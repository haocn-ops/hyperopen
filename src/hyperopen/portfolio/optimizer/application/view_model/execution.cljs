(ns hyperopen.portfolio.optimizer.application.view-model.execution
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.spot-token-labels :as spot-token-labels]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

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

(def ^:private terminal-run-statuses
  #{:executed :resting :partially-executed :failed :blocked})

(defn- run-status
  [state]
  (get-in state (conj contracts/execution-path :status)))

(defn- derive-phase
  "Folds the thin engine state (submitting? + run :status) and the cosmetic pre-submit
  UI phase into the five v4 display phases."
  [{:keys [submitting? ui-phase status]}]
  (cond
    submitting? :running
    (= :executed status) :done
    ;; Orders accepted and live (open) on the book, none rejected — terminal, but not a fill.
    (= :resting status) :resting
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
        latest-attempt (enrich-execution-attempt
                        resolve-label
                        (latest-record (get-in state contracts/execution-history-path)))
        run-attempt (enrich-execution-attempt
                     resolve-label
                     (get-in state contracts/execution-run-attempt-path))
        submitting? (boolean (:submitting? modal))
        status (run-status state)
        terminal? (boolean (terminal-run-statuses status))
        ui-phase (or (:phase modal) :staged)
        phase (derive-phase {:submitting? submitting? :ui-phase ui-phase :status status})
        plan-rows (enrich-instrument-rows resolve-label (:rows plan))
        ;; During a run, show the live in-flight rows (queued -> working ->
        ;; submitted/failed); after a run, the latest ledger attempt; otherwise the
        ;; static staged plan.
        display-rows (order-numbered-and-sorted
                      (cond
                        (and submitting? (seq (:rows run-attempt)))
                        (:rows run-attempt)

                        (and terminal? (seq (:rows latest-attempt)))
                        (:rows latest-attempt)

                        :else plan-rows))
        ready? (pos? (or (:ready-count summary) 0))
        execution-disabled? (boolean (:execution-disabled? plan))
        confirm-disabled? (or submitting?
                              execution-disabled?
                              (not ready?))
        ;; Side-split notional of the rows that will actually send (status :ready), so the
        ;; armed/confirm band can restate "how much money moves" — buys vs sells — alongside
        ;; the order count. Derived from the staged plan rows (pre-run).
        ready-rows (filter #(= :ready (:status %)) plan-rows)
        side-notional (fn [side]
                        (->> ready-rows
                             (filter #(= side (:side %)))
                             (map #(js/Math.abs (or (:delta-notional-usd %) 0)))
                             (reduce + 0)))]
    {:plan (assoc plan :rows plan-rows)
     :summary summary
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
     :read-only? execution-disabled?
     :confirm-disabled? (boolean confirm-disabled?)
     :disabled-message (when execution-disabled?
                         (or (:disabled-message plan)
                             "Execution is disabled for this scenario."))
     :has-plan? (boolean (seq (:rows plan)))}))
