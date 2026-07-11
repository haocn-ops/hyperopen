(ns hyperopen.portfolio.optimizer.application.view-model.equal-risk-structure
  "Read models for the Equal Risk correlation/decomposition views (the
  CORRELATION and BREAKDOWN tabs of the risk-contribution card, plus the
  Allocation table's P&L-correlation line and row selection). Pure functions
  over the solved result's :risk-structure payload section — persisted
  pre-structure results simply return nil models and the views degrade to the
  original two tabs.

  The position-P&L correlation matrix is DERIVED here from the persisted
  underlying matrix: Corr(s_i r_i, s_j r_j) = s_i * s_j * Corr(r_i, r_j),
  with s the sign of the published target weight. Direction, correlation and
  risk contribution stay three separate concepts: nothing here infers a
  contribution sign from a position side."
  (:require [hyperopen.portfolio.optimizer.application.view-model.equal-risk-results
             :as equal-risk-results]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def ^:private finite-number? coercion/finite-number?)

(def neutral-correlation-threshold
  "|position correlation| below this displays as 0.00, so the tooltip verdict
  says Neutral instead of over-reading float dust as a hedge."
  0.005)

;; --- Shared identity/formatting -----------------------------------------------

(defn risk-view-radio-name
  "The DOM-state tab radio group for a result's risk card. Suffixed with the
  result's :as-of-ms so co-rendered fixtures don't share a group."
  [result]
  (str "optimizer-risk-view-" (or (:as-of-ms result) "result")))

(defn risk-view-radio-id
  "Deterministic id for one tab's radio input, so out-of-card labels (the
  why-card's Correlation view card) can activate a tab with plain label/for."
  [result view]
  (str (risk-view-radio-name result) "-" view))

(defn format-pct
  ([value] (format-pct value 1))
  ([value decimals]
   (if (finite-number? value)
     (str (.toFixed (* 100 value) decimals) "%")
     "—")))

(defn format-correlation
  [value]
  (if (finite-number? value)
    (.toFixed value 2)
    "—"))

(defn format-signed-correlation
  [value]
  (if (finite-number? value)
    (str (when (>= value 0) "+") (.toFixed value 2))
    "—"))

(defn side-label
  [side]
  (case side
    :long "Long"
    :short "Short"
    nil))

;; --- Sides and selection --------------------------------------------------------

(defn instrument-side
  "Position side implied by the published target weight: :long, :short, or
  nil for an unheld (zero-weight) instrument."
  [result instrument-id]
  (let [weight (get-in result [:target-weights-by-instrument instrument-id])]
    (cond
      (and (finite-number? weight) (pos? weight)) :long
      (and (finite-number? weight) (neg? weight)) :short
      :else nil)))

(defn- label-for
  [result instrument-id]
  (or (get-in result [:labels-by-instrument instrument-id]) instrument-id))

(defn- net-shares-by-instrument
  [result]
  (into {}
        (filter (comp finite-number? val))
        (get-in result [:risk-contributions
                        :relative-contributions-by-instrument])))

(defn default-selected-instrument
  "The asset the breakdown panel explains before any click: the most negative
  net contributor when one exists (the page's headline anomaly — it has its
  own KPI cell), else the largest |net| contributor."
  [result]
  (let [nets (net-shares-by-instrument result)]
    (when (seq nets)
      (let [negatives (filter (comp neg? val) nets)]
        (if (seq negatives)
          (key (apply min-key val negatives))
          (key (apply max-key (comp js/Math.abs val) nets)))))))

(defn selected-instrument
  "The explicitly selected instrument when it exists on this result, else the
  default. A stale selection (re-solve dropped the asset) never renders."
  [result explicit-id]
  (let [nets (net-shares-by-instrument result)]
    (if (and explicit-id (contains? nets explicit-id))
      explicit-id
      (default-selected-instrument result))))

(defn pnl-portfolio-correlation
  "Corr(s_i r_i, r_p) for the Allocation table's per-row line; nil when the
  result predates :risk-structure or the instrument holds no position."
  [result instrument-id]
  (get-in result [:risk-structure
                  :pnl-portfolio-correlation-by-instrument
                  instrument-id]))

;; --- Correlation heatmap ---------------------------------------------------------

(defn- pair-effect
  [position diagonal?]
  (cond
    diagonal? nil
    (not (finite-number? position)) nil
    (< (js/Math.abs position) neutral-correlation-threshold) :neutral
    (neg? position) :diversifying
    :else :amplifying))

(defn cell-title
  "Native-tooltip copy for one heatmap cell: both correlations plus the
  portfolio-risk verdict, so a user never has to infer one matrix from the
  other. `row` and `col` are entries of the correlation model."
  [row col {:keys [underlying position effect diagonal?]}]
  (let [pair (str (:label row)
                  (some->> (side-label (:side row)) (str " "))
                  " × "
                  (:label col)
                  (some->> (side-label (:side col)) (str " ")))]
    (if diagonal?
      (str (:label row) (some->> (side-label (:side row)) (str " "))
           " · correlation with itself is 1.00")
      (str pair
           "\nUnderlying-return correlation " (format-signed-correlation underlying)
           "\nPosition-P&L correlation " (format-signed-correlation position)
           "\nEffect on portfolio risk: "
           (case effect
             :diversifying "Diversifying"
             :amplifying "Amplifying"
             :neutral "Neutral"
             "—")))))

(defn correlation-model
  "The heatmap model: entries (id/label/side, display order persisted by the
  payload — signed net share descending, matching the balance chart), a cell
  matrix carrying BOTH the underlying and the derived position-P&L
  correlation, and the honest hidden-count when the payload cap bit. Nil when
  the result predates :risk-structure or nothing is held."
  [result]
  (let [correlation (get-in result [:risk-structure :correlation])
        ids (:instrument-ids correlation)]
    (when (seq ids)
      (let [entries (mapv (fn [instrument-id]
                            {:instrument-id instrument-id
                             :label (label-for result instrument-id)
                             :side (instrument-side result instrument-id)})
                          ids)
            signs (mapv (fn [{:keys [side]}]
                          (case side :long 1 :short -1 nil))
                        entries)
            matrix (:matrix correlation)
            cells (vec
                   (map-indexed
                    (fn [row-idx row]
                      (vec
                       (map-indexed
                        (fn [col-idx underlying]
                          (let [diagonal? (= row-idx col-idx)
                                sign-row (nth signs row-idx)
                                sign-col (nth signs col-idx)
                                position (when (and (finite-number? underlying)
                                                    sign-row
                                                    sign-col)
                                           (* sign-row sign-col underlying))]
                            {:underlying (when (finite-number? underlying)
                                           underlying)
                             :position position
                             :diagonal? diagonal?
                             :effect (pair-effect position diagonal?)}))
                        row)))
                    matrix))]
        {:entries entries
         :cells cells
         :hidden-count (or (:hidden-count correlation) 0)}))))

;; --- Decomposition ---------------------------------------------------------------

(defn breakdown-rows
  "Balance-chart rows (same cap, same signed-share display order) enriched
  with the standalone/diversification split, for the BREAKDOWN tab. Rows
  without structure data (should not happen on same-run payloads) carry nils
  and render as em-dashes."
  [result balance-rows]
  (let [structure (:risk-structure result)]
    (when structure
      (mapv (fn [{:keys [instrument-id] :as row}]
              (assoc row
                     :side (instrument-side result instrument-id)
                     :standalone (get-in structure
                                         [:standalone-share-by-instrument
                                          instrument-id])
                     :diversification (get-in structure
                                              [:diversification-share-by-instrument
                                               instrument-id])))
            balance-rows))))

(defn selected-breakdown
  "The CONTRIBUTION BREAKDOWN panel model for the selected (or defaulted)
  instrument: label, side, and the standalone + diversification = net story.
  Nil when the result carries no structure section or no contributions."
  [result explicit-id]
  (when-let [structure (:risk-structure result)]
    (when-let [instrument-id (selected-instrument result explicit-id)]
      (let [net (get (net-shares-by-instrument result) instrument-id)]
        {:instrument-id instrument-id
         :label (label-for result instrument-id)
         :side (instrument-side result instrument-id)
         :standalone (get-in structure [:standalone-share-by-instrument
                                        instrument-id])
         :diversification (get-in structure [:diversification-share-by-instrument
                                             instrument-id])
         :net net
         :negative? (and (finite-number? net) (neg? net))
         :target-share (first (get-in result [:risk-contributions
                                              :target-relative-contributions]))}))))

;; --- Plot scale ------------------------------------------------------------------

(defn fit-scale
  "One shared axis for decomposition lanes: fits zero plus every finite value,
  pads 8% (at least 2 pts), and rounds outward to clean 5% ticks. Returns
  {:lo :hi :x (fraction -> 0..100 CSS percent)} — the same contract as the
  balance chart's lane scale, without its current-marker special cases."
  [values]
  (let [finite (cons 0 (filter finite-number? values))
        lo0 (reduce min 0 finite)
        hi0 (reduce max 0 finite)
        pad (max 0.02 (* 0.08 (- hi0 lo0)))
        lo (* 0.05 (js/Math.floor (/ (- lo0 pad) 0.05)))
        hi (* 0.05 (js/Math.ceil (/ (+ hi0 pad) 0.05)))
        span (max 1e-9 (- hi lo))]
    {:lo lo
     :hi hi
     :x (fn [value]
          (-> (* 100 (/ (- value lo) span))
              (max 0)
              (min 100)))}))

(defn scale-ticks
  "Clean tick values for a fit-scale, capped at `max-labels` (default 8 —
  narrow lanes like the correlation tab's breakdown block pass fewer)."
  ([scale] (scale-ticks scale 8))
  ([{:keys [lo hi]} max-labels]
   (let [span (- hi lo)
         step (or (first (filter #(<= (/ span %) max-labels)
                                 [0.05 0.1 0.2 0.25 0.5 1 2 5]))
                  5)]
     (->> (range (js/Math.ceil (/ lo step)) (inc (js/Math.floor (/ hi step))))
          (map #(* step %))))))
