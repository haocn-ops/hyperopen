(ns hyperopen.portfolio.optimizer.actions.execution
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.portfolio.optimizer.actions.common :as common]
            [hyperopen.portfolio.optimizer.application.execution :as execution]
            [hyperopen.portfolio.optimizer.application.rebalance-preview :as rebalance-preview]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]))

(def ^:private phase-path (conj contracts/execution-modal-path :phase))
(def ^:private default-order-type-path
  (conj contracts/execution-modal-path :default-order-type))
(def ^:private overrides-path (conj contracts/execution-modal-path :overrides))
(def ^:private params-path (conj contracts/execution-modal-path :params))
(def ^:private open-row-path (conj contracts/execution-modal-path :open-row))
(def ^:private order-filter-path (conj contracts/execution-modal-path :order-filter))

(defn- staged-plan
  "Builds the execution plan from the last successful run, or nil when there is no
  solved run with a rebalance preview to stage."
  [state]
  (let [readiness (setup-readiness/build-readiness state)
        last-successful-run
        (rebalance-preview/last-successful-run-with-rebalance-preview
         (:request readiness)
         (get-in state contracts/last-successful-run-path))
        result (:result last-successful-run)
        preview (:rebalance-preview result)]
    (when (and (= :solved (:status result))
               (map? preview))
      (execution/build-execution-plan
       {:scenario-id (common/current-scenario-id state)
        :rebalance-preview preview
        :execution-assumptions (get-in state
                                        contracts/draft-execution-assumptions-path)
        :mutations-blocked-message
        (account-context/mutations-blocked-message state)}))))

(defn open-portfolio-optimizer-execution
  "Stages the current rebalance into an execution plan and switches the scenario
  surface to the Execution tab. Always switches the tab (so the entry point is
  discoverable even with no solved run, which renders an empty state); the plan is
  rebuilt as a fresh snapshot on every entry."
  [state]
  [[:effects/save
    contracts/execution-modal-path
    (assoc (optimizer-defaults/default-execution-modal-state)
           :open? true
           :plan (staged-plan state))]
   ;; Reset run-state so re-staging shows a clean staged surface rather than a stale
   ;; terminal status from a previous execution attempt.
   [:effects/save contracts/execution-path (optimizer-defaults/default-execution-state)]
   [:effects/save contracts/ui-results-tab-path :execution]
   [:effects/replace-shareable-route-query]])

(defn set-portfolio-optimizer-execution-phase
  "Pre-submit phase toggle: :armed asks for a second confirm, :staged returns to the
  default order-type selector. Clears any stale confirm-time error."
  [_state phase]
  [[:effects/save phase-path (if (= :armed (keyword phase)) :armed :staged)]
   [:effects/save contracts/execution-modal-error-path nil]])

(defn set-portfolio-optimizer-execution-default-order-type
  [_state order-type]
  [[:effects/save default-order-type-path (keyword order-type)]])

(defn set-portfolio-optimizer-execution-row-order-type
  "Overrides one order's type. The :recommended sentinel clears the override so the
  row falls back to the algorithm-recommended type."
  [state row-id order-type]
  (let [overrides (or (get-in state overrides-path) {})
        order-type* (keyword order-type)]
    [[:effects/save
      overrides-path
      (if (= :recommended order-type*)
        (dissoc overrides row-id)
        (assoc overrides row-id order-type*))]]))

(defn toggle-portfolio-optimizer-execution-row
  "Expands/collapses one order's inline type editor (single-open accordion)."
  [state row-id]
  (let [open-row (get-in state open-row-path)]
    [[:effects/save open-row-path (when (not= open-row row-id) row-id)]]))

(defn set-portfolio-optimizer-execution-row-param
  [state row-id param-key value]
  (let [params (or (get-in state params-path) {})]
    [[:effects/save
      params-path
      (assoc-in params [row-id (keyword param-key)] value)]]))

(defn set-portfolio-optimizer-execution-order-filter
  "Sets the order-list filter (:all / :working / :filled)."
  [_state order-filter]
  [[:effects/save order-filter-path (keyword order-filter)]])

(defn confirm-portfolio-optimizer-execution
  [state]
  (let [modal (get-in state contracts/execution-modal-path)
        base-plan (:plan modal)
        plan (when (map? base-plan)
               (execution/apply-order-type-selections
                base-plan
                {:default-order-type (:default-order-type modal)
                 :overrides (:overrides modal)
                 :params (:params modal)}))
        ready-count (get-in plan [:summary :ready-count])
        agent-status (get-in state [:wallet :agent :status])]
    (cond
      (not (map? plan))
      []

      (:submitting? modal)
      []

      (:execution-disabled? plan)
      [[:effects/save
        contracts/execution-modal-error-path
        (or (:disabled-message plan)
            "Execution is disabled for this scenario.")]]

      (not (pos? (or ready-count 0)))
      [[:effects/save
        contracts/execution-modal-error-path
        "No executable rows are ready."]]

      ;; Trading must be ready before any order is sent — mirror manual order entry
      ;; (order/effects.cljs api-submit-order), which routes every non-ready agent status
      ;; instead of submitting. Without this the orders dispatch and each row dead-ends on a
      ;; "Trading is locked" / "Enable trading first" rejection with no recovery affordance.
      ;;
      ;; A locked agent prompts the passkey unlock and replays confirm on success. An action
      ;; can only EMIT effects (`effects/*`) — it cannot return `[:actions/...]` (that fails
      ;; the effect-id schema). So inline what :actions/unlock-agent-trading itself emits:
      ;; flip the agent status to :unlocking (so a second confirm-click can't double-prompt
      ;; the passkey) and run the unlock effect with confirm queued as the replay.
      (= :locked agent-status)
      [[:effects/save contracts/execution-modal-error-path nil]
       [:effects/save-many [[[:wallet :agent :status] :unlocking]
                            [[:wallet :agent :error] nil]]]
       [:effects/unlock-agent-trading
        {:after-success-actions [[:actions/confirm-portfolio-optimizer-execution]]}]]

      ;; Unlock already in flight (awaiting passkey): hold without submitting.
      (= :unlocking agent-status)
      [[:effects/save
        contracts/execution-modal-error-path
        "Awaiting passkey before executing."]]

      ;; Trading not enabled yet (the default :not-ready, plus :approving / :error): open the
      ;; enable-trading recovery modal — the same prompt manual order entry shows — instead of
      ;; submitting orders that would each be rejected with "Enable trading first".
      (not= :ready agent-status)
      [[:effects/save [:wallet :agent :recovery-modal-open?] true]
       [:effects/save
        contracts/execution-modal-error-path
        "Enable trading before executing."]]

      :else
      [[:effects/save contracts/execution-modal-submitting-path true]
       [:effects/save contracts/execution-modal-error-path nil]
       [:effects/execute-portfolio-optimizer-plan plan]])))

(defn resume-portfolio-optimizer-execution
  "Resumes a halted run by retrying ONLY the still-recoverable rows. Unlike a plain
  re-confirm (which replays the stale modal :plan whose rows stay :ready forever and
  would re-submit already-filled orders), this derives the retry set from the latest
  execution ledger: failed rows are re-armed, already-filled rows are demoted to
  :skipped :already-filled, and the corrected plan is executed via the existing effect
  (which appends a fresh ledger so the audit trail is preserved)."
  [state]
  (let [modal (get-in state contracts/execution-modal-path)
        plan (:plan modal)
        ledger (last (get-in state contracts/execution-history-path))]
    (cond
      (not (map? plan))
      []

      (:submitting? modal)
      []

      (not (map? ledger))
      []

      (:execution-disabled? plan)
      [[:effects/save
        contracts/execution-modal-error-path
        (or (:disabled-message plan)
            "Execution is disabled for this scenario.")]]

      :else
      (let [resume-plan (execution/build-resume-plan plan ledger)
            ready-count (get-in resume-plan [:summary :ready-count])]
        (if-not (pos? (or ready-count 0))
          [[:effects/save
            contracts/execution-modal-error-path
            "No orders are eligible to resume."]]
          [[:effects/save contracts/execution-modal-submitting-path true]
           [:effects/save contracts/execution-modal-error-path nil]
           [:effects/execute-portfolio-optimizer-plan resume-plan]])))))

(defn revert-portfolio-optimizer-execution-filled
  "Unwinds the filled orders from the latest execution attempt by submitting reversing
  (reduce-only, opposite-side) market orders. Reuses the execute effect, which appends a
  fresh ledger so the revert is auditable."
  [state]
  (let [modal (get-in state contracts/execution-modal-path)
        plan (:plan modal)
        ledger (last (get-in state contracts/execution-history-path))]
    (cond
      (not (map? plan))
      []

      (:submitting? modal)
      []

      (not (map? ledger))
      []

      (:execution-disabled? plan)
      [[:effects/save
        contracts/execution-modal-error-path
        (or (:disabled-message plan)
            "Execution is disabled for this scenario.")]]

      :else
      (let [revert-plan (execution/build-revert-plan plan ledger)]
        (if-not (pos? (or (get-in revert-plan [:summary :ready-count]) 0))
          [[:effects/save
            contracts/execution-modal-error-path
            "No filled orders to revert."]]
          [[:effects/save contracts/execution-modal-submitting-path true]
           [:effects/save contracts/execution-modal-error-path nil]
           [:effects/execute-portfolio-optimizer-plan revert-plan]])))))

(defn restage-portfolio-optimizer-execution-smaller
  "Re-stages the unfilled rows at half size and returns the surface to :staged for a
  fresh arm/confirm. Pure state rewrite — no orders are sent. The prior ledger history
  is preserved so the halt remains auditable."
  [state]
  (let [modal (get-in state contracts/execution-modal-path)
        plan (:plan modal)
        ledger (last (get-in state contracts/execution-history-path))]
    (cond
      (not (map? plan))
      []

      (:submitting? modal)
      []

      :else
      (let [restaged (execution/build-restaged-plan plan ledger 0.5)]
        [[:effects/save (conj contracts/execution-modal-path :plan) restaged]
         [:effects/save phase-path :staged]
         [:effects/save contracts/execution-modal-error-path nil]
         ;; Reset run-state out of the terminal :halted status but keep the prior
         ;; ledger history for the audit trail.
         [:effects/save contracts/execution-path
          (assoc (optimizer-defaults/default-execution-state)
                 :history (vec (get-in state contracts/execution-history-path)))]]))))

(defn pause-portfolio-optimizer-execution
  "Requests a pause/abort of an in-flight run. The submit loop checks this flag before
  releasing each remaining order: in-flight orders still settle, but nothing new is
  sent, and the run lands in the halted state for resume / re-stage / revert."
  [_state]
  [[:effects/save contracts/execution-abort-requested-path true]])

(defn discard-portfolio-optimizer-execution
  "Abort & discard: clears the staged plan and run state and returns to the Rebalance
  preview. No-op while a run is in flight (mirrors confirm's submitting guard)."
  [state]
  (if (:submitting? (get-in state contracts/execution-modal-path))
    []
    [[:effects/save contracts/execution-modal-path
      (optimizer-defaults/default-execution-modal-state)]
     [:effects/save contracts/execution-path
      (optimizer-defaults/default-execution-state)]
     [:effects/save contracts/ui-results-tab-path :rebalance]
     [:effects/replace-shareable-route-query]]))

(defn open-portfolio-optimizer-execution-in-ticket
  "Opens the trade ticket seeded with the first ready order's market, so a trader can
  place or adjust that leg manually."
  [state]
  (let [plan (get-in state (conj contracts/execution-modal-path :plan))
        ready (first (filter #(= :ready (:status %)) (:rows plan)))
        coin (or (:coin ready) (:instrument-id ready))]
    (if coin
      [[:actions/navigate (str "/trade?market=" coin)]]
      [])))
