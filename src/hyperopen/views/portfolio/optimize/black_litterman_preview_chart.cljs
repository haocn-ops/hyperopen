(ns hyperopen.views.portfolio.optimize.black-litterman-preview-chart
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(def ^:private eyebrow-class
  ["font-mono" "text-[0.625rem]" "font-semibold" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"])

(def ^:private chart-width 760)
(def ^:private chart-height 326)
(def ^:private chart-plot-left 48)
(def ^:private chart-plot-right 736)
(def ^:private chart-plot-top 26)
(def ^:private chart-plot-bottom 224)
(def ^:private chart-legend-y 296)
(def ^:private chart-legend-column-gap 320)

(def ^:private chart-grid-stroke "rgb(90 95 104 / 0.22)")
(def ^:private chart-axis-stroke "rgb(90 95 104 / 0.38)")
(def ^:private prior-fill "#6b8db5")
(def ^:private posterior-fill "#d4b558")

(def ^:private finite-number? opt-format/finite-number?)

(defn- finite-preview-values
  [rows]
  (filter finite-number?
          (mapcat (juxt :prior-return :posterior-return) rows)))

(defn- chart-domain
  [rows]
  (if-let [values (seq (finite-preview-values rows))]
    (let [domain-min (min 0 (apply min values))
          domain-max (max 0 (apply max values))
          span (max 0.0001 (- domain-max domain-min))
          padding (* span 0.08)]
      [(if (neg? domain-min) (- domain-min padding) 0)
       (if (pos? domain-max) (+ domain-max padding) 0.01)])
    [0 0.01]))

(defn- chart-y
  [[domain-min domain-max] value]
  (if (and (finite-number? value)
           (not= domain-min domain-max))
    (+ chart-plot-top
       (* (/ (- domain-max value)
             (- domain-max domain-min))
          (- chart-plot-bottom chart-plot-top)))
    chart-plot-bottom))

(defn- chart-ticks
  [[domain-min domain-max]]
  (let [step (/ (- domain-max domain-min) 4)]
    (mapv #(+ domain-min (* step %)) (range 5))))

(defn- group-step
  [row-count]
  (/ (- chart-plot-right chart-plot-left) (max 1 row-count)))

(defn- bar-width
  [row-count]
  (let [step (group-step row-count)]
    (max 8 (min 24 (/ step 3)))))

(defn- group-center-x
  [row-count idx]
  (+ chart-plot-left
     (* (group-step row-count) idx)
     (/ (group-step row-count) 2)))

(defn- bar-x
  [row-count idx series-index]
  (let [width (bar-width row-count)
        gap 5
        group-start (- (group-center-x row-count idx)
                       width
                       (/ gap 2))]
    (+ group-start
       (* series-index (+ width gap)))))

(defn- bar-rect
  [domain row-count idx row series-key value]
  (when (finite-number? value)
    (let [zero-y (chart-y domain 0)
          value-y (chart-y domain value)
          height (max 1 (js/Math.abs (- zero-y value-y)))
          y (min zero-y value-y)
          series-index (case series-key
                         :prior 0
                         :posterior 1)]
      [:rect {:key (str (name series-key) "-" idx)
              :x (bar-x row-count idx series-index)
              :y y
              :width (bar-width row-count)
              :height height
              :fill (case series-key
                      :prior prior-fill
                      :posterior posterior-fill)
              :rx 0
              :data-role (str "portfolio-optimizer-black-litterman-preview-bar-"
                              (name series-key)
                              "-"
                              (:instrument-id row))
              :aria-label (str (or (:label row) (:instrument-id row))
                               " "
                               (case series-key
                                 :prior "market reference "
                                 :posterior "combined output ")
                               (opt-format/format-pct value)
                               " annualized")}])))

(defn- delta-label
  [domain row-count idx row]
  (let [prior (:prior-return row)
        posterior (:posterior-return row)
        delta (when (and (finite-number? prior)
                         (finite-number? posterior))
                (- posterior prior))]
    (when (and (finite-number? delta)
               (>= (js/Math.abs delta) 0.005))
      (let [value-y (min (chart-y domain prior)
                         (chart-y domain posterior))]
        [:text {:key (str "delta-" idx)
                :x (group-center-x row-count idx)
                :y (max 12 (- value-y 9))
                :fill "currentColor"
                :font-size 9
                :opacity 0.66
                :text-anchor "middle"
                :data-role (str "portfolio-optimizer-black-litterman-preview-view-delta-"
                                (:instrument-id row))}
         (str "view " (opt-format/format-pct-delta delta {:suffix ""}))]))))

(defn- tick-label
  [domain idx value]
  (let [y (chart-y domain value)]
    [:text {:key (str "y-label-" idx)
            :x (- chart-plot-left 10)
            :y (+ y 4)
            :fill "currentColor"
            :font-size 9
            :opacity 0.58
            :text-anchor "end"
            :data-role (str "portfolio-optimizer-black-litterman-preview-y-tick-" idx)}
     (opt-format/format-pct value {:minimum-fraction-digits 0
                                   :maximum-fraction-digits 0})]))

(defn- label-text
  [row]
  (or (:label row)
      (:instrument-id row)))

(defn- asset-label
  [row-count idx row]
  [:text {:key (str "x-label-" idx)
          :x (group-center-x row-count idx)
          :y (+ chart-plot-bottom 24)
          :fill "currentColor"
          :font-size 10
          :opacity 0.72
          :text-anchor "middle"
          :data-role (str "portfolio-optimizer-black-litterman-preview-x-label-"
                          (:instrument-id row))}
   (label-text row)])

(defn- chart-grid
  [domain]
  [:g {:data-role "portfolio-optimizer-black-litterman-preview-grid"}
   (map-indexed
    (fn [idx value]
      (let [y (chart-y domain value)]
        [:line {:key (str "grid-" idx)
                :x1 chart-plot-left
                :x2 chart-plot-right
                :y1 y
                :y2 y
                :stroke chart-grid-stroke}]))
    (chart-ticks domain))])

(defn- legend-item
  [center-x fill label qualifier]
  [:g {:transform (str "translate(" center-x " 0)")}
   [:rect {:x -84
           :y -8
           :width 12
           :height 12
           :fill fill}]
   [:text {:x -60
           :y -4
           :fill "currentColor"
           :font-size 12
           :letter-spacing 2.4
           :opacity 0.72
           :text-anchor "start"}
    label]
   [:text {:x -60
           :y 18
           :fill "currentColor"
           :font-size 12
           :letter-spacing 2.4
           :opacity 0.72
           :text-anchor "start"}
    qualifier]])

(defn- chart-legend
  []
  (let [center-x (/ (+ chart-plot-left chart-plot-right) 2)
        prior-x (- center-x (/ chart-legend-column-gap 2))
        posterior-x (+ center-x (/ chart-legend-column-gap 2))]
    [:g {:transform (str "translate(0 " chart-legend-y ")")
         :data-role "portfolio-optimizer-black-litterman-preview-legend"}
     (legend-item prior-x prior-fill "Market reference" "(prior)")
     (legend-item posterior-x posterior-fill "Combined output" "(posterior)")]))

(defn- preview-chart
  ([rows]
   (preview-chart rows nil))
  ([rows {:keys [legend-layout]}]
   (let [row-count (count rows)
         domain (chart-domain rows)
         zero-y (chart-y domain 0)]
    [:div {:class ["mt-4" "overflow-hidden" "border" "border-base-300" "bg-base-200/20" "p-3"]
           :data-role "portfolio-optimizer-black-litterman-preview-chart-box"}
     [:svg {:viewBox (str "0 0 " chart-width " " chart-height)
            :class ["h-[20.375rem]" "w-full" "overflow-visible" "text-trading-text"]
            :data-role "portfolio-optimizer-black-litterman-preview-svg"
            :aria-label "Expected return per asset chart. Bars compare market reference prior returns against combined posterior output."}
      (chart-grid domain)
      [:line {:x1 chart-plot-left
              :x2 chart-plot-right
              :y1 zero-y
              :y2 zero-y
              :stroke chart-axis-stroke}]
      [:line {:x1 chart-plot-left
              :x2 chart-plot-left
              :y1 chart-plot-top
              :y2 chart-plot-bottom
              :stroke chart-axis-stroke}]
      [:g {:data-role "portfolio-optimizer-black-litterman-preview-y-axis"}
       (map-indexed (partial tick-label domain) (chart-ticks domain))]
      [:g {:data-role "portfolio-optimizer-black-litterman-preview-bars"}
       (map-indexed
        (fn [idx row]
          [:g {:key (str "asset-" idx)
               :data-role (str "portfolio-optimizer-black-litterman-preview-asset-"
                               (:instrument-id row))}
           (bar-rect domain row-count idx row :prior (:prior-return row))
           (bar-rect domain row-count idx row :posterior (:posterior-return row))
           (delta-label domain row-count idx row)])
        rows)]
      [:g {:data-role "portfolio-optimizer-black-litterman-preview-x-axis"}
       (map-indexed (partial asset-label row-count) rows)]
      (when (not= :external legend-layout)
        (chart-legend))]])))

(defn black-litterman-preview-panel
  ([readiness]
   (black-litterman-preview-panel readiness nil))
  ([readiness opts]
   (let [preview (or (:preview opts)
                     (optimizer-view-model/black-litterman-preview-model readiness))]
     [:section {:class ["border" "border-base-300" "bg-base-100/90" "p-4"]
                :data-role "portfolio-optimizer-black-litterman-preview-panel"}
      [:div {:class ["flex" "items-start" "justify-between" "gap-3"]}
       [:div
        [:p {:class eyebrow-class} "Expected return per asset - annualized"]
        [:h3 {:class ["mt-2" "text-[0.875rem]" "font-medium"]}
         "Market reference vs combined output"]]]
      (case (:status preview)
        :ready
        (preview-chart (:rows preview) opts)

        :empty
        [:p {:class ["mt-4" "border" "border-base-300" "bg-base-200/30" "p-3"
                     "text-[0.6875rem]" "text-trading-muted"]}
         "No active views yet. Add a view to compare posterior expected returns against the market reference."]

        [:p {:class ["mt-4" "border" "border-base-300" "bg-base-200/30" "p-3"
                     "text-[0.6875rem]" "text-trading-muted"]}
         "Posterior preview will appear once the universe has eligible history. Views still save with the scenario."])])))
