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

(defn confirm-portfolio-optimizer-execution
  [state]
  (let [modal (get-in state contracts/execution-modal-path)
        plan (:plan modal)
        ready-count (get-in plan [:summary :ready-count])]
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

      :else
      [[:effects/save contracts/execution-modal-submitting-path true]
       [:effects/save contracts/execution-modal-error-path nil]
       [:effects/execute-portfolio-optimizer-plan plan]])))
