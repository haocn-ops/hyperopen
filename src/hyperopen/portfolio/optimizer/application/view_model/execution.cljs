(ns hyperopen.portfolio.optimizer.application.view-model.execution
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.execution :as execution]
            [hyperopen.portfolio.optimizer.application.execution-amend :as execution-amend]
            [hyperopen.portfolio.optimizer.application.execution-carryover :as carryover]
            [hyperopen.portfolio.optimizer.application.execution-cloid :as cloid]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.application.spot-token-labels :as spot-token-labels]
            [hyperopen.portfolio.optimizer.application.view-model.execution-reconcile :as reconcile]
            [hyperopen.portfolio.optimizer.application.view-model.workspace :as workspace]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

;; Keep in sync with hyperopen.portfolio.optimizer.actions.execution/stale-recommendation-message
;; (the action emits the same sentence as the modal error when a stale arm/confirm is refused).
(def ^:private stale-recommendation-message
  "Inputs changed since this recommendation was computed — re-run the optimizer before executing.")

;; Appended to the read-only (spectate / trader-portfolio / unavailable-subaccount) commit-block
;; banner. Changing a strategy or an order's type only re-projects estimated costs (the same pure
;; recompute the KPI strip uses) — nothing is sent — so a spectator can model execution here even
;; though arming/sending stays disabled. Not appended to the stale-recommendation block, whose only
;; honest resolution is a re-run.
(def ^:private simulation-allowed-note
  "You can still model execution strategies and per-order types here to compare costs — arming and sending stay disabled.")

(defn- labels-by-instrument
  [state]
  (or (get-in state (conj contracts/last-successful-run-result-path
                          :labels-by-instrument))
      {}))

(defn- display-label
  "Human display symbol for an instrument id, mirroring the other optimizer surfaces:
  prefer the engine label, fall back to the trailing id segment (e.g.
  \"perp:xyz:xyz:SILVER\" -> \"SILVER\"), and resolve spot \"@N\" dust references
  through spotMeta (the asset-selector pattern) so users never see a bare pair index."
  [spot-resolver labels instrument-id]
  (let [value (str instrument-id)
        from-labels (get labels instrument-id)
        tail (last (str/split value #":"))
        base (some-> tail (str/split #"[/-]") first)
        named (cond
                (and from-labels (not (spot-token-labels/at-reference? from-labels)))
                from-labels

                (and base (not= base value) (not (spot-token-labels/at-reference? base)))
                base

                :else nil)]
    (or named
        (when (str/starts-with? value "spot:")
          (:base (spot-token-labels/display-fields spot-resolver tail tail)))
        from-labels
        base
        value)))

(defn- label-resolver
  "Closure resolving an instrument id to its display label from app state (engine
  labels + spotMeta)."
  [state]
  (let [labels (labels-by-instrument state)
        spot-resolver (spot-token-labels/resolver (get-in state [:spot :meta]))]
    (partial display-label spot-resolver labels)))

(defn- enrich-instrument-rows
  [resolve-label rows]
  (mapv (fn [row]
          (assoc row :instrument-label (resolve-label (:instrument-id row))))
        (or rows [])))

(defn- order-numbered-and-sorted
  "Stamp each display row with its 1-based ledger order number (the submission order) then
  sort the list by absolute notional, largest first — so the riskiest trade always leads the
  pre-commit order table while the displayed `#` stays tied to ledger order (the reference
  used by Resume / audit). Ties keep ledger order."
  [rows]
  (->> rows
       (map-indexed (fn [i row] (assoc row :order-no (inc i))))
       (sort-by (fn [row] [(- (js/Math.abs (or (:delta-notional-usd row) 0))) (:order-no row)]))
       vec))

(defn- latest-record
  [records]
  (last (vec records)))

(defn- enrich-execution-attempt
  [resolve-label attempt]
  (when attempt
    (assoc attempt :rows (enrich-instrument-rows resolve-label (:rows attempt)))))

(defn execution-modal-model
  [state]
  (let [modal (or (get-in state contracts/execution-modal-path) {})
        plan (or (:plan modal) {})
        summary (:summary plan)
        labels-by-instrument* (labels-by-instrument state)
        resolve-label (label-resolver state)
        latest-attempt (enrich-execution-attempt
                        resolve-label
                        (latest-record (get-in state contracts/execution-history-path)))
        submitting? (boolean (:submitting? modal))
        ready? (pos? (or (:ready-count summary) 0))
        confirm-disabled? (or submitting?
                              (:execution-disabled? plan)
                              (not ready?))
        plan* (assoc plan
                     :rows (enrich-instrument-rows resolve-label
                                                   (:rows plan)))]
    {:modal modal
     :open? (boolean (:open? modal))
     :plan plan*
     :summary summary
     :latest-attempt latest-attempt
     :labels-by-instrument labels-by-instrument*
     :submitting? submitting?
     :ready? ready?
     :confirm-disabled? (boolean confirm-disabled?)
     :disabled-message (or (:disabled-message plan)
                           "Order submission wiring is not enabled in this slice.")}))

(defn- stamp-amend-affordances
  "Stamps each still-working (reconciled :resting) row with its live amend context —
  {:amendable? true :oid :remaining-size :limit-px :live-mark :order-type :limit-bps} —
  so the order table can render the amend editor without touching state. A row whose
  oid can no longer be seen live on the book gets no :amend (there is nothing safe to
  cancel-and-replace); the selection keys come from the same `amend-selections` the
  amend action resolves, so what the editor shows is what a commit would submit."
  [state modal writable? rows]
  (if-not writable?
    rows
    (let [market-by-key (get-in state [:asset-selector :market-by-key])
          selections {:overrides (:overrides modal)
                      :params (:params modal)}]
      (mapv (fn [row]
              (if-let [target (when (= :resting (:status row))
                                (execution-amend/amend-target state market-by-key row))]
                (assoc row :amend
                       (merge (select-keys target
                                           [:oid :remaining-size :limit-px :live-mark])
                              {:amendable? true}
                              (execution-amend/amend-selections selections row)))
                row))
            rows))))

(def ^:private terminal-run-statuses
  #{:executed :resting :partially-executed :failed :blocked})

(defn- run-status
  [state]
  (get-in state (conj contracts/execution-path :status)))

(defn- derive-phase
  "Folds the thin engine state (submitting? + run :status) and the cosmetic pre-submit
  UI phase into the six v4 display phases."
  [{:keys [submitting? ui-phase status live-resting?]}]
  (cond
    submitting? :running
    (= :executed status) :done
    ;; Orders accepted and live (open) on the book, none rejected — terminal, but not a fill.
    (= :resting status) :resting
    ;; A rejection stopped the release loop, but earlier passive orders are still live
    ;; (open) on the book and keep filling — the run is partial, not halted. It falls back
    ;; to :halted once the resting rows resolve (fill or leave the book).
    (and (terminal-run-statuses status) live-resting?) :partial
    (terminal-run-statuses status) :halted
    (= :armed ui-phase) :armed
    :else :staged))

(defn execution-tab-model
  "View-model for the v4 Execution tab. Reuses the modal plan/summary/latest-attempt
  derivation and layers the staging interaction state + derived display phase on top.
  Pre-submit the table shows the plan rows; after a run it shows the latest ledger
  attempt rows (which carry :submitted/:failed/:blocked + :error)."
  [state]
  (let [modal (or (get-in state contracts/execution-modal-path) {})
        plan (or (:plan modal) {})
        summary (:summary plan)
        labels (labels-by-instrument state)
        resolve-label (label-resolver state)
        ;; Reconcile the frozen ledger rows against the live order/fill feeds so a resting passive
        ;; order that has since filled reads as filled (the persisted ledger stays untouched — it
        ;; is the audit record). A no-op for any non-resting row. The latest-attempt's own :status
        ;; chip is re-derived from the reconciled rows so it flips :resting -> :executed too.
        latest-attempt (when-let [attempt (enrich-execution-attempt
                                           resolve-label
                                           (latest-record
                                            (get-in state contracts/execution-history-path)))]
                         (let [rrows (reconcile/reconcile-rows state (:rows attempt))]
                           (assoc attempt
                                  :rows rrows
                                  :status (execution/final-ledger-status rrows))))
        run-attempt (enrich-execution-attempt
                     resolve-label
                     (get-in state contracts/execution-run-attempt-path))
        submitting? (boolean (:submitting? modal))
        status (run-status state)
        terminal? (boolean (terminal-run-statuses status))
        ui-phase (or (:phase modal) :staged)
        plan-rows (enrich-instrument-rows resolve-label (:rows plan))
        ;; During a run, show the live in-flight rows (queued -> working ->
        ;; submitted/failed); after a run, the latest ledger attempt; otherwise the
        ;; static staged plan.
        reconciled-rows (reconcile/reconcile-rows
                         state
                         (cond
                           (and submitting? (seq (:rows run-attempt)))
                           (:rows run-attempt)

                           (and terminal? (seq (:rows latest-attempt)))
                           (:rows latest-attempt)

                           :else plan-rows))
        display-rows (stamp-amend-affordances
                      state modal
                      (and (not (:execution-disabled? plan))
                           (not submitting?))
                      (order-numbered-and-sorted reconciled-rows))
        ;; When the run settled as :resting, re-derive its status from the reconciled rows so the
        ;; phase advances :resting -> :done as the open orders fill on the book.
        reconciled-status (if (= :resting status)
                            (execution/final-ledger-status reconciled-rows)
                            status)
        ;; Rows still :resting AFTER reconciliation are live (open) on the book right now,
        ;; so a rejected-alongside-resting run reads :partial (still working), not :halted.
        live-resting? (and terminal?
                           (boolean (some #(= :resting (:status %)) reconciled-rows)))
        phase (derive-phase {:submitting? submitting? :ui-phase ui-phase
                             :status reconciled-status :live-resting? live-resting?})
        ready? (pos? (or (:ready-count summary) 0))
        execution-disabled? (boolean (:execution-disabled? plan))
        ;; Inputs edited since the solve => the staged plan no longer reflects intent. The entry
        ;; already stages a stale plan as :execution-disabled? (its read-only banner carries the
        ;; re-run message), and the action gates refuse a stale arm/confirm. This recomputes
        ;; staleness live so post-staging drift (a plan that was current when staged) still
        ;; disables Arm reactively instead of only erroring on click.
        scenario-stale? (boolean (workspace/scenario-stale?
                                  state (setup-readiness/build-readiness state)))
        ;; Show the standalone notice only when the disabled banner isn't already carrying the
        ;; stale message — avoids a duplicate banner for a stale-at-entry plan.
        stale-notice? (and scenario-stale? (not execution-disabled?))
        disabled-message (when execution-disabled?
                           (or (:disabled-message plan)
                               "Execution is disabled for this scenario."))
        ;; Why sending is blocked: :read-only (spectate / trader-portfolio / unavailable
        ;; subaccount) or :stale-recommendation. Only the former is a *simulate-anyway* context;
        ;; the banner tells a spectator they can model execution even though sending is disabled.
        commit-blocked-reason (when execution-disabled? (:disabled-reason plan))
        commit-blocked-message (when execution-disabled?
                                 (if (= :read-only commit-blocked-reason)
                                   (str disabled-message " " simulation-allowed-note)
                                   disabled-message))
        confirm-disabled? (or submitting?
                              execution-disabled?
                              scenario-stale?
                              (not ready?))
        ;; Side-split notional of the rows that will actually send (status :ready), so the
        ;; armed/confirm band can restate "how much money moves" — buys vs sells — alongside
        ;; the order count. Derived from the staged plan rows (pre-run).
        ready-rows (filter #(= :ready (:status %)) plan-rows)
        side-notional (fn [side]
                        (->> ready-rows
                             (filter #(= side (:side %)))
                             (map #(js/Math.abs (or (:delta-notional-usd %) 0)))
                             (reduce + 0)))
        ;; Open orders overlapping this rebalance, classified against the live cloid-bearing
        ;; frontendOpenOrders snapshot: our OWN tagged orders auto-cancel (counted alongside
        ;; the in-memory carryover); UNTAGGED overlaps (manual / pre-tag) surface for an
        ;; explicit per-order decision.
        {owned-open :optimizer-owned untagged-open :untagged-overlap}
        (cloid/classify-overlap (cloid/live-open-orders state) ready-rows)
        session-carryover (carryover/live-resting-carryover
                           state
                           (get-in state contracts/execution-resting-carryover-path))
        auto-cancel-oids (into #{}
                               (map #(str (:oid %)))
                               (concat session-carryover owned-open))
        overlap-selections (or (get-in state (conj contracts/execution-modal-path
                                                   :overlap-cancels)) {})
        overlap-orders (mapv (fn [o]
                               (assoc o :cancel? (boolean
                                                  (get overlap-selections (str (:oid o))))))
                             untagged-open)]
    {:plan (assoc plan :rows plan-rows)
     :summary summary
     ;; Orders (ours) that confirm will cancel automatically before sending new orders —
     ;; in-session carryover PLUS reload-surviving cloid-recognized orders, deduped by oid.
     :carryover-count (count auto-cancel-oids)
     ;; Untagged open orders overlapping the rebalance the user must resolve (each carries
     ;; its live :cancel? choice); drives the decision surface.
     :overlap-orders overlap-orders
     :rows display-rows
     :phase phase
     ;; The live-run pause/abort flag the submit loop checks; surfaced so the running band can
     ;; acknowledge the request instead of leaving the button looking inert.
     :abort-requested? (boolean (get-in state (conj contracts/execution-path :abort-requested?)))
     :ready-buys-usd (side-notional :buy)
     :ready-sells-usd (side-notional :sell)
     :run-status status
     :terminal? terminal?
     :latest-attempt latest-attempt
     :labels-by-instrument labels
     :default-order-type (or (:default-order-type modal) :recommended)
     :overrides (or (:overrides modal) {})
     :params (or (:params modal) {})
     :open-row (:open-row modal)
     :order-filter (or (:order-filter modal) :all)
     :submitting? submitting?
     :error (:error modal)
     :ready? ready?
     ;; Commit gate — cannot Arm / Confirm / send (spectate, trader-portfolio, unavailable
     ;; subaccount, or stale). Distinct from simulation editability: the strategy tiles and
     ;; per-order type/param editors stay live so a read-only viewer can compare execution costs.
     :commit-blocked? execution-disabled?
     :commit-blocked-reason commit-blocked-reason
     :commit-blocked-message commit-blocked-message
     :stale? stale-notice?
     :stale-message (when stale-notice? stale-recommendation-message)
     ;; Arming is blocked by either a read-only/disabled plan (spectate or stale-at-entry) or
     ;; live post-staging staleness; the header reads the combined flag + reason so the button
     ;; explains itself on hover.
     :arm-disabled? (or execution-disabled? scenario-stale?)
     :arm-disabled-message (cond
                             execution-disabled? disabled-message
                             scenario-stale? stale-recommendation-message
                             :else nil)
     :confirm-disabled? (boolean confirm-disabled?)
     :disabled-message disabled-message
     :has-plan? (boolean (seq (:rows plan)))}))
