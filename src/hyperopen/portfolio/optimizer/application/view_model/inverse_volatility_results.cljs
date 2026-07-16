(ns hyperopen.portfolio.optimizer.application.view-model.inverse-volatility-results
  "Read models for the Risk-weighted sizing (:inverse-volatility) results
  page: the sizing-fidelity card rows (each asset's |weight| × σ against the
  shared equal ideal, with the rows a constraint moved off the 1/σ seed
  flagged), the KPI sizing-deviation tile, and the recommendation verdict
  copy. Pure functions over the solved result payload's :inverse-volatility
  section; every view branch for this objective reads from here so the page
  can never disagree with itself (same contract as equal-risk-results)."
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def ^:private finite-number? coercion/finite-number?)

(defn inverse-volatility-result?
  [result]
  (some? (:inverse-volatility result)))

(defn- format-multiple
  ;; Deliberately local: hyperopen.views.portfolio.optimize.format has the
  ;; sibling helper, but the namespace-boundary lint forbids a non-view
  ;; namespace importing hyperopen.views.* (and this fallback is "—", not its
  ;; "N/A").
  [value]
  (if (finite-number? value)
    (str (.toFixed value 2) "x")
    "—"))

(defn max-sizing-deviation
  "Largest relative spread of |w|·σ among the assets the projection left free
  — the objective's solve-quality number (0 on a clean run)."
  [result]
  (get-in result [:inverse-volatility :max-sizing-deviation]))

(defn sizing-model
  "Card model from the payload's :inverse-volatility section. Rows are
  displayed in |w|·σ descending order so a capped row's shortfall reads as a
  visible step off the shared ideal; the ideal itself is the mean risk-weight
  of the FREE rows (the value the σ-weighted projection equalizes them to),
  read from the payload section — the fallback recompute only covers payloads
  written before the section carried :ideal-risk-weight."
  [result]
  (when-let [section (:inverse-volatility result)]
    (let [labels (:labels-by-instrument result)
          rows (mapv (fn [{:keys [instrument-id weight sigma risk-weight
                                  moved-off-seed? turnover-limited?]}]
                       {:instrument-id instrument-id
                        :label (or (get labels instrument-id) instrument-id)
                        :weight weight
                        :sigma sigma
                        :risk-weight risk-weight
                        :short? (and (finite-number? weight) (neg? weight))
                        :moved-off-seed? (boolean moved-off-seed?)
                        :turnover-limited? (boolean turnover-limited?)})
                     (:sizing-rows section))
          all-risk-weights (into [] (filter finite-number?)
                                 (map :risk-weight rows))
          ideal (or (:ideal-risk-weight section)
                    (let [free (into []
                                     (comp (remove :moved-off-seed?)
                                           (map :risk-weight)
                                           (filter finite-number?))
                                     rows)]
                      (when (seq free)
                        (/ (reduce + 0 free) (count free)))))]
      {:rows (vec (sort-by (fn [{:keys [risk-weight]}]
                             (- (if (finite-number? risk-weight)
                                  risk-weight
                                  ##-Inf)))
                           rows))
       :ideal-risk-weight ideal
       :max-risk-weight (when (seq all-risk-weights)
                          (reduce max (or ideal 0) all-risk-weights))
       :moved-count (count (filter :moved-off-seed? rows))
       :max-sizing-deviation (:max-sizing-deviation section)})))

(defn verdict-body
  "The recommendation sentence for the verdict bar: what the sizing rule did,
  in the objective's own success metric — never vol/return framing that would
  imply a frontier choice was made."
  [result]
  (when-let [section (:inverse-volatility result)]
    (let [rows (:sizing-rows section)
          n (count rows)
          moved (count (filter :moved-off-seed? rows))
          gross (get-in result [:diagnostics :gross-exposure])
          net (get-in result [:diagnostics :net-exposure])]
      (str "Sizes " n " selected positions by the inverse of their own "
           "volatility on their chosen sides at selected "
           (format-multiple gross) " gross with resulting "
           (format-multiple net) " net exposure. Correlations and return "
           "forecasts never move these weights."
           (when (pos? moved)
             (str " " moved
                  (if (= 1 moved)
                    " position sits on a limit instead of its ideal 1/σ size."
                    " positions sit on limits instead of their ideal 1/σ sizes.")))))))
