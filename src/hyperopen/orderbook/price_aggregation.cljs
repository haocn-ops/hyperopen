(ns hyperopen.orderbook.price-aggregation)

(def mode-order
  [:full :sf4 :sf3 :sf2])

(def valid-modes
  (set mode-order))

(def mode->sig-figs
  {:full 5
   :sf4 4
   :sf3 3
   :sf2 2})

(defn normalize-mode [mode]
  (if (contains? valid-modes mode) mode :full))

(defn- parse-number [value]
  (cond
    (number? value) value
    (string? value) (let [n (js/parseFloat value)]
                      (when-not (js/isNaN n) n))
    :else nil))

(defn- finite-positive? [n]
  (and (number? n)
       (not (js/isNaN n))
       (js/isFinite n)
       (> n 0)))

(defn- clamp [value min-value max-value]
  (max min-value (min max-value value)))

(defn safe-reference-price [reference-price]
  (let [n (parse-number reference-price)]
    (if (finite-positive? n) n 1)))

(defn max-price-decimals [market-type sz-decimals]
  (let [sz (int (or (parse-number sz-decimals) 0))
        raw (if (= market-type :spot)
              (- 8 sz)
              (- 6 sz))]
    (clamp raw 0 12)))

(defn- pow10 [exp]
  (js/Math.pow 10 exp))

(defn- log10 [n]
  (/ (js/Math.log n) (js/Math.log 10)))

(defn sig-step [reference-price sig-figs]
  (let [p (safe-reference-price reference-price)
        exponent (- (js/Math.floor (log10 p)) (dec sig-figs))]
    (pow10 exponent)))

(defn decimal-step [max-decimals]
  (pow10 (- (int max-decimals))))

(defn mode-step [reference-price mode max-decimals]
  (let [sig-figs (get mode->sig-figs (normalize-mode mode) 5)]
    (max (sig-step reference-price sig-figs)
         (decimal-step max-decimals))))

(defn- same-step? [a b]
  (let [scale (max 1 (js/Math.abs a) (js/Math.abs b))]
    (<= (js/Math.abs (- a b)) (* scale 1e-12))))

(defn step->label [step]
  (let [safe-step (if (finite-positive? step) step 1)
        exponent (js/Math.round (log10 safe-step))]
    (if (>= exponent 0)
      (.toFixed safe-step 0)
      (.toFixed safe-step (- exponent)))))

(defn mode->subscription-config [mode]
  (let [normalized (normalize-mode mode)]
    (case normalized
      :full {}
      :sf4 {:nSigFigs 4}
      :sf3 {:nSigFigs 3}
      :sf2 {:nSigFigs 2}
      {})))

(defn- dedupe-by-step [options]
  (reduce (fn [acc option]
            (if (some #(same-step? (:step %) (:step option)) acc)
              acc
              (conj acc option)))
          []
          options))

(defn build-options
  [{:keys [market-type sz-decimals reference-price]}]
  (let [max-decimals (max-price-decimals market-type sz-decimals)
        ref (safe-reference-price reference-price)]
    (->> mode-order
         (map (fn [mode]
                (let [step (mode-step ref mode max-decimals)]
                  {:mode mode
                   :step step
                   :label (step->label step)
                   :subscription-config (mode->subscription-config mode)})))
         dedupe-by-step
         vec)))

(defn resolve-selected-mode [options mode]
  (let [normalized (normalize-mode mode)]
    (if (some #(= (:mode %) normalized) options)
      normalized
      (or (:mode (first options)) :full))))

(defn option-for-mode [options mode]
  (let [resolved (resolve-selected-mode options mode)]
    (or (some #(when (= (:mode %) resolved) %) options)
        (first options))))
