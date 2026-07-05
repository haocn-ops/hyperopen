(ns hyperopen.portfolio.optimizer.actions.execution
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.portfolio.optimizer.actions.common :as common]
            [hyperopen.portfolio.optimizer.application.execution :as execution]
            [hyperopen.portfolio.optimizer.application.execution-amend :as execution-amend]
            [hyperopen.portfolio.optimizer.application.execution-carryover :as carryover]
            [hyperopen.portfolio.optimizer.application.execution-cloid :as cloid]
            [hyperopen.portfolio.optimizer.application.rebalance-preview :as rebalance-preview]
            [hyperopen.portfolio.optimizer.application.run-identity :as run-identity]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]))

(def stale-recommendation-message
  "Inputs changed since this recommendation was computed — re-run the optimizer before executing.")

(defn- optimizer-running?
  [state]
  (or (= :running (get-in state contracts/run-state-status-path))
      (= :running (get-in state contracts/optimization-progress-status-path))))

(defn stale-recommendation?
  "True when there IS a solved run but the current draft/readiness no longer matches it
  (universe / constraints / objective / model edited since the solve), so the staged plan
  is computed from inputs the trader has since moved off. Mirrors the Recommendation tab's
  `:stale?` signal but enforced in the execution gates, where a stale plan would otherwise
  release live orders. Short-circuits on `solved-run?` so a hand-built plan with no retained
  run (and the cheap pre-submit toggles) never pays for `build-readiness`."
  [state]
  (let [last-successful-run (get-in state contracts/last-successful-run-path)]
    (and (run-identity/solved-run? last-successful-run)
         (run-identity/stale-run?
          {:draft (get-in state contracts/draft-path)
           :readiness (setup-readiness/build-readiness state)
           :run-state (get-in state contracts/run-state-path)
           :running? (optimizer-running? state)
           :last-successful-run last-successful-run}))))

(def ^:private phase-path (conj contracts/execution-modal-path :phase))
(def ^:private default-order-type-path
  (conj contracts/execution-modal-path :default-order-type))
(def ^:private overrides-path (conj contracts/execution-modal-path :overrides))
(def ^:private params-path (conj contracts/execution-modal-path :params))
(def ^:private open-row-path (conj contracts/execution-modal-path :open-row))
(def ^:private order-filter-path (conj contracts/execution-modal-path :order-filter))

(defn- staged-plan
  "Builds the execution plan from the last successful run, or nil when there is no
  solved run with a rebalance preview to stage.

  The entry gates on currency, not merely `solved?`: a stale recommendation (inputs edited
  since the solve) stages a plan flagged `:execution-disabled? :stale-recommendation`, so the
  trader sees what WOULD trade but can't arm or commit until the optimizer is re-run. A
  read-only (spectate) block takes precedence — it is the harder restriction and its message
  wins."
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
      (let [plan (execution/build-execution-plan
                  {:scenario-id (common/current-scenario-id state)
                   :rebalance-preview preview
                   :execution-assumptions (get-in state
                                                  contracts/draft-execution-assumptions-path)
                   :mutations-blocked-message
                   (account-context/mutations-blocked-message state)})]
        (if (and (not (:execution-disabled? plan))
                 (stale-recommendation? state))
          (assoc plan
                 :execution-disabled? true
                 :disabled-reason :stale-recommendation
                 :disabled-message stale-recommendation-message)
          plan)))))

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
   ;; Refresh every open-order surface (base + per-dex frontendOpenOrders, the only
   ;; cloid-bearing source — the generic openOrders stream never hydrates named-dex
   ;; rows). By confirm time the run can then recognize and cancel its own resting
   ;; orders from previous sessions, and surface untagged overlaps for review.
   [:effects/refresh-portfolio-optimizer-open-orders]
   [:effects/save contracts/ui-results-tab-path :execution]
   [:effects/replace-shareable-route-query]])

(defn set-portfolio-optimizer-execution-phase
  "Pre-submit phase toggle: :armed asks for a second confirm, :staged returns to the
  default order-type selector. Clears any stale confirm-time error. Arming a stale
  recommendation is refused (the surface stays :staged with a re-run prompt) so a plan
  computed from since-edited inputs can never reach the live-commit button."
  [state phase]
  (let [arm? (= :armed (keyword phase))]
    (if (and arm? (stale-recommendation? state))
      [[:effects/save phase-path :staged]
       [:effects/save contracts/execution-modal-error-path stale-recommendation-message]]
      [[:effects/save phase-path (if arm? :armed :staged)]
       [:effects/save contracts/execution-modal-error-path nil]])))

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

(def ^:private overlap-cancels-path
  (conj contracts/execution-modal-path :overlap-cancels))

(defn- dedup-by-oid
  [entries]
  (:out (reduce (fn [{:keys [seen out]} e]
                  (let [k (str (:oid e))]
                    (if (contains? seen k)
                      {:seen seen :out out}
                      {:seen (conj seen k) :out (conj out e)})))
                {:seen #{} :out []}
                entries)))

(defn- attach-carryover-cancels
  "Stamps every open order this run should cancel BEFORE releasing new orders onto the
  plan as :cancel-orders. Three sources, deduped by oid (first wins):

    1. In-memory carryover — resting orders from previous runs THIS session (they carry
       a frozen wire asset index).
    2. Live-book recognition — orders on the frontendOpenOrders snapshot bearing the
       optimizer cloid tag, so a run AFTER A PAGE RELOAD still cleans up its own resting
       orders automatically (no user action).
    3. User-selected overlaps — untagged orders on a traded instrument the user ticked to
       cancel on the decision surface (manual orders / pre-tag optimizer orders).

  Read fresh at confirm time so anything that filled in between is not needlessly
  cancelled. Without this a stale order fills on top of the new run and over-allocates."
  [state plan]
  (let [carryover (carryover/live-resting-carryover
                   state
                   (get-in state contracts/execution-resting-carryover-path))
        snapshot (cloid/live-open-orders state)
        ready-rows (filter #(= :ready (:status %)) (:rows plan))
        {:keys [optimizer-owned untagged-overlap]} (cloid/classify-overlap snapshot ready-rows)
        selections (get-in state overlap-cancels-path)
        chosen-untagged (filter #(get selections (str (:oid %))) untagged-overlap)
        merged (dedup-by-oid (concat carryover optimizer-owned chosen-untagged))]
    (cond-> plan
      (seq merged) (assoc :cancel-orders merged))))

(defn set-portfolio-optimizer-execution-overlap-cancel
  "Records the user's cancel/keep choice for one untagged overlapping open order (keyed
  by oid string) on the decision surface. Checked oids are merged into :cancel-orders at
  confirm by attach-carryover-cancels."
  [state oid cancel?]
  (let [selections (or (get-in state overlap-cancels-path) {})
        oid-key (str oid)]
    [[:effects/save
      overlap-cancels-path
      (if cancel?
        (assoc selections oid-key true)
        (dissoc selections oid-key))]]))

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

      ;; Stale recommendation: the staged plan was solved against inputs the trader has since
      ;; changed (the Recommendation tab is already showing the same `:stale?` banner). Refuse
      ;; to send — a re-run is the only honest way to bring the orders back in line with intent.
      ;; This backstops the Arm gate in case the surface goes stale between arming and confirming.
      (stale-recommendation? state)
      [[:effects/save
        contracts/execution-modal-error-path
        stale-recommendation-message]]

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
       [:effects/execute-portfolio-optimizer-plan (attach-carryover-cancels state plan)]])))

(defn- amend-refusal-message
  [error]
  (case error
    :not-amendable
    "This order is no longer on the book — it filled or was cancelled."
    :market-unavailable
    "Market metadata is unavailable for this order — try again shortly."
    :remaining-below-lot
    "The unfilled remainder is below one lot — manage it from the trade ticket."
    "This order can't be updated right now."))

(defn amend-portfolio-optimizer-execution-order
  "Replaces ONE working (resting/open) order from the latest execution attempt: cancel
  it on the exchange, then submit a replacement priced off the LIVE mark — a new bps
  offset (or post-only at the touch), or a market order that crosses immediately for
  the REMAINING unfilled size. Reuses the execute effect, whose cancel-first /
  halt-on-failure contract guarantees the replacement can never be live alongside the
  original; the amend plan carries ONLY the amended order's cancel entry (attaching the
  session carryover here would cancel the run's OTHER live orders).

  Deliberately NOT gated on stale-recommendation? (mirrors Revert's reasoning): the
  order is already live — repricing or crossing its remaining size never creates
  exposure beyond what was already confirmed, and gating it would strand a working
  order behind a re-run."
  [state row-id]
  (let [modal (get-in state contracts/execution-modal-path)
        plan (:plan modal)
        ledger (last (get-in state contracts/execution-history-path))
        agent-status (get-in state [:wallet :agent :status])]
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
      (let [row (some #(when (= row-id (:row-id %)) %) (:rows ledger))
            target (when row
                     (execution-amend/amend-target
                      state
                      (get-in state [:asset-selector :market-by-key])
                      row))
            {amend-plan :plan error :error}
            (execution-amend/build-amend-plan
             {:plan plan
              :ledger ledger
              :row-id row-id
              :selections {:overrides (:overrides modal)
                           :params (:params modal)}
              :target target})]
        (cond
          (some? error)
          [[:effects/save contracts/execution-modal-error-path
            (amend-refusal-message error)]]

          ;; Trading must be ready before the cancel+replace is sent — the same gate
          ;; stack as confirm (see its comment for the emit-effects-only constraint).
          ;; Checked AFTER amendability so a passkey prompt is never raised for an
          ;; order that can no longer be amended; the unlock replays this action
          ;; (with its row-id), which re-resolves the live target.
          (= :locked agent-status)
          [[:effects/save contracts/execution-modal-error-path nil]
           [:effects/save-many [[[:wallet :agent :status] :unlocking]
                                [[:wallet :agent :error] nil]]]
           [:effects/unlock-agent-trading
            {:after-success-actions
             [[:actions/amend-portfolio-optimizer-execution-order row-id]]}]]

          (= :unlocking agent-status)
          [[:effects/save
            contracts/execution-modal-error-path
            "Awaiting passkey before updating the order."]]

          (not= :ready agent-status)
          [[:effects/save [:wallet :agent :recovery-modal-open?] true]
           [:effects/save
            contracts/execution-modal-error-path
            "Enable trading before updating orders."]]

          :else
          [[:effects/save contracts/execution-modal-submitting-path true]
           [:effects/save contracts/execution-modal-error-path nil]
           [:effects/execute-portfolio-optimizer-plan amend-plan]])))))

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
           ;; A halted run may have failed BEFORE its pre-run cancellation succeeded, so
           ;; resume re-attaches the still-live carryover the same way confirm does.
           [:effects/execute-portfolio-optimizer-plan
            (attach-carryover-cancels state resume-plan)]])))))

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
  "Abort & discard: clears the staged plan and run state and returns to the
  Recommendation tab. No-op while a run is in flight (mirrors confirm's submitting
  guard)."
  [state]
  (if (:submitting? (get-in state contracts/execution-modal-path))
    []
    [[:effects/save contracts/execution-modal-path
      (optimizer-defaults/default-execution-modal-state)]
     [:effects/save contracts/execution-path
      (optimizer-defaults/default-execution-state)]
     [:effects/save contracts/ui-results-tab-path :recommendation]
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
