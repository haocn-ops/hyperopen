(ns hyperopen.views.portfolio.optimize.frontier-chart-model
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model.frontier :as overlay-model]
            [hyperopen.views.portfolio.optimize.format :as opt-format]
            [hyperopen.views.portfolio.optimize.frontier-chart-axes :as chart-axes]))

(def chart-width 680)
(def chart-height 380)
(def chart-plot-left 64)
(def chart-plot-right 42)
(def chart-plot-top 34)
(def chart-plot-bottom 58)
(def chart-tick-count 6)
(def chart-grid-stroke "#1d2025")
(def chart-axis-stroke "#292d33")
(def frontier-color "#e2b84f")
(def chart-bounds {:width chart-width
                   :height chart-height})

(def plot-right (- chart-width chart-plot-right))
(def plot-bottom (- chart-height chart-plot-bottom))
(def plot-width (- plot-right chart-plot-left))
(def plot-height (- plot-bottom chart-plot-top))
(def plot-center-x (+ chart-plot-left (/ plot-width 2)))
(def plot-center-y (+ chart-plot-top (/ plot-height 2)))
(def plot-geometry {:left chart-plot-left
                    :right plot-right
                    :top chart-plot-top
                    :bottom plot-bottom})

(defn objective-target
  [draft point]
  (if (= :target-volatility (get-in draft [:objective :kind]))
    {:kind :target-volatility
     :parameter-key :target-volatility
     :parameter-value (:volatility point)
     :label "Target Volatility"}
    {:kind :target-return
     :parameter-key :target-return
     :parameter-value (:expected-return point)
     :label "Target Return"}))

(defn point-actions
  [target]
  [[:actions/set-portfolio-optimizer-objective-kind (:kind target)]
   [:actions/set-portfolio-optimizer-objective-parameter
    (:parameter-key target)
    (:parameter-value target)]])

(defn numeric-values
  [points key]
  (keep (fn [point]
          (let [value (get point key)]
            (when (opt-format/finite-number? value) value)))
        points))

(defn domain
  ([values fallback-min fallback-max]
   (domain values fallback-min fallback-max nil))
  ([values fallback-min fallback-max {:keys [floor-zero? include-zero?]}]
   (let [values* (seq values)
         min* (if values* (apply min values*) fallback-min)
         max* (if values* (apply max values*) fallback-max)]
     (if (= min* max*)
       [(if floor-zero? 0 (- min* 0.01)) (+ max* 0.01)]
       (let [span (- max* min*)
             lower (- min* (* span 0.08))
             upper (+ max* (* span 0.08))
             lower* (cond
                      floor-zero? 0
                      (and include-zero? (pos? lower)) 0
                      :else lower)
             upper* (if (and include-zero? (neg? upper)) 0 upper)]
         [lower* upper*])))))

(defn scale-value
  [domain-min domain-max range-min range-max value]
  (if (and (opt-format/finite-number? value)
           (not= domain-min domain-max))
    (+ range-min
       (* (/ (- value domain-min)
             (- domain-max domain-min))
          (- range-max range-min)))
    (/ (+ range-min range-max) 2)))

(defn point-position
  [x-domain y-domain point]
  {:x (scale-value (first x-domain)
                   (second x-domain)
                   chart-plot-left
                   plot-right
                   (:volatility point))
   :y (scale-value (first y-domain)
                   (second y-domain)
                   plot-bottom
                   chart-plot-top
                   (:expected-return point))})

(defn path-data
  [positions]
  (when (seq positions)
    (str/join " "
              (map-indexed
               (fn [idx {:keys [x y]}]
                 (str (if (zero? idx) "M" "L")
                      " "
                      (opt-format/format-decimal x)
                      " "
                      (opt-format/format-decimal y)))
               positions))))

(defn frontier-points
  [result constrain-frontier?]
  (or (get-in result [:frontiers (if (true? constrain-frontier?)
                                   :constrained
                                   :unconstrained)])
      (:frontier result)))

(defn current-allocation?
  [result]
  (let [weights (filter opt-format/finite-number?
                        (or (:current-portfolio-weights result)
                            (:current-weights result)))]
    (pos? (reduce + 0 (map js/Math.abs weights)))))

(defn current-point
  [result]
  (when (and (opt-format/finite-number? (:current-volatility result))
             (opt-format/finite-number? (:current-expected-return result))
             (current-allocation? result))
    {:expected-return (:current-expected-return result)
     :volatility (:current-volatility result)
     :sharpe (get-in result [:current-performance :in-sample-sharpe])}))

(defn chart-model
  [draft result overlay-mode constrain-frontier?]
  (let [overlay-mode* (overlay-model/normalize-mode overlay-mode)
        overlay-points (overlay-model/visible-points result overlay-mode*)
        domain-overlay-points (overlay-model/all-points result)
        current-point* (current-point result)
        {:keys [subtitle
                x-axis-prefix
                y-axis-prefix
                reading-text]} (overlay-model/copy overlay-mode*)
        points (->> (frontier-points result constrain-frontier?)
                    (filter #(and (opt-format/finite-number? (:volatility %))
                                  (opt-format/finite-number? (:expected-return %))))
                    (sort-by :volatility)
                    vec)
        x-domain (domain (concat (numeric-values points :volatility)
                                 (numeric-values domain-overlay-points :volatility)
                                 (numeric-values (when current-point*
                                                   [current-point*])
                                                 :volatility)
                                 (when (opt-format/finite-number? (:volatility result))
                                   [(:volatility result)]))
                         0
                         1
                         {:floor-zero? true})
        y-domain (domain (concat (numeric-values points :expected-return)
                                 (numeric-values domain-overlay-points :expected-return)
                                 (numeric-values (when current-point*
                                                   [current-point*])
                                                 :expected-return)
                                 (when (opt-format/finite-number? (:expected-return result))
                                   [(:expected-return result)]))
                         0
                         1
                         {:include-zero? true})
        x-ticks (chart-axes/axis-ticks x-domain chart-tick-count)
        y-ticks (chart-axes/axis-ticks y-domain chart-tick-count)
        x-domain* (chart-axes/tick-domain x-ticks x-domain)
        y-domain* (chart-axes/tick-domain y-ticks y-domain)]
    {:overlay-mode overlay-mode*
     :overlay-points overlay-points
     :subtitle subtitle
     :x-axis-prefix x-axis-prefix
     :y-axis-prefix y-axis-prefix
     :reading-text reading-text
     :points points
     :current-point current-point*
     :x-domain x-domain*
     :y-domain y-domain*
     :x-ticks x-ticks
     :y-ticks y-ticks
     :positions (map #(point-position x-domain* y-domain* %) points)
     :target (objective-target draft (first points))}))
