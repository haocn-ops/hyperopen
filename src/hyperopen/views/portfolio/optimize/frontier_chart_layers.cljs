(ns hyperopen.views.portfolio.optimize.frontier-chart-layers
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model.frontier :as overlay-model]
            [hyperopen.views.portfolio.optimize.frontier-chart-axes :as chart-axes]
            [hyperopen.views.portfolio.optimize.frontier-chart-model :as model]
            [hyperopen.views.portfolio.optimize.frontier-callout :as frontier-callout]
            [hyperopen.views.portfolio.optimize.frontier-current :as frontier-current]
            [hyperopen.views.portfolio.optimize.frontier-overlay-markers :as frontier-overlays]
            [hyperopen.views.portfolio.optimize.frontier-target :as frontier-target]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- grid-line
  [orientation idx position]
  (case orientation
    :vertical
    [:line {:key (str "v-" idx)
            :x1 position
            :x2 position
            :y1 model/chart-plot-top
            :y2 model/plot-bottom
            :stroke model/chart-grid-stroke}]
    [:line {:key (str "h-" idx)
            :x1 model/chart-plot-left
            :x2 model/plot-right
            :y1 position
            :y2 position
            :stroke model/chart-grid-stroke}]))

(defn- frontier-callout-id
  [idx]
  (str "frontier-" idx))

(defn- callout-visibility-rule
  [trigger-selector callout-selector]
  (let [svg-selector "[data-role=\"portfolio-optimizer-frontier-svg\"]"]
    (str svg-selector ":has(" trigger-selector ":hover) " callout-selector ",\n"
         svg-selector ":has(" trigger-selector ":focus) " callout-selector ",\n"
         svg-selector ":has(" trigger-selector ":focus-within) " callout-selector
         " { display: inline; opacity: 1; }")))

(defn- frontier-callout-visibility-rule
  [idx]
  (let [callout-id (frontier-callout-id idx)
        trigger-selector (str "[data-frontier-callout-trigger=\"" callout-id "\"]")
        callout-selector (str "[data-frontier-callout-id=\"" callout-id "\"]")]
    (callout-visibility-rule trigger-selector callout-selector)))

(defn- overlay-role-prefix
  [overlay-mode]
  (case (overlay-model/normalize-mode overlay-mode)
    :contribution "portfolio-optimizer-frontier-overlay-contribution-"
    :standalone "portfolio-optimizer-frontier-overlay-standalone-"
    nil))

(defn- overlay-callout-prefix
  [overlay-mode]
  (case (overlay-model/normalize-mode overlay-mode)
    :contribution "portfolio-optimizer-frontier-callout-contribution-"
    :standalone "portfolio-optimizer-frontier-callout-standalone-"
    nil))

(defn- overlay-callout-visibility-rule
  [overlay-mode point]
  (when-let [role-prefix (overlay-role-prefix overlay-mode)]
    (let [callout-prefix (overlay-callout-prefix overlay-mode)
          instrument-id (:instrument-id point)]
      (callout-visibility-rule
       (str "[data-role=\"" role-prefix instrument-id "\"]")
       (str "[data-role=\"" callout-prefix instrument-id "\"]")))))

(defn- target-callout-visibility-rule
  []
  (callout-visibility-rule
   "[data-role=\"portfolio-optimizer-frontier-target-marker\"]"
   "[data-role=\"portfolio-optimizer-frontier-callout-target\"]"))

(defn- current-callout-visibility-rule
  []
  (callout-visibility-rule
   "[data-role=\"portfolio-optimizer-frontier-current-marker\"]"
   "[data-role=\"portfolio-optimizer-frontier-callout-current\"]"))

(defn- frontier-callout-style
  [points overlay-mode overlay-points current-point]
  (when (or (seq points) (seq overlay-points) current-point)
    [:style {:type "text/css"}
     (str/join "\n" (concat
                     [(target-callout-visibility-rule)
                      (current-callout-visibility-rule)]
                     (map-indexed (fn [idx _]
                                    (frontier-callout-visibility-rule idx))
                                  points)
                     (keep #(overlay-callout-visibility-rule overlay-mode %)
                           overlay-points)))]))

(defn- frontier-point
  [draft idx point x-domain y-domain]
  (let [target (model/objective-target draft point)
        position (model/point-position x-domain y-domain point)
        {:keys [x y]} position
        label (str "Frontier Point " (inc idx))
        rows (frontier-callout/point-rows point)
        callout-id (frontier-callout-id idx)]
    [:g {:role "button"
         :tabIndex 0
         :tabindex 0
         :focusable "true"
         :class ["portfolio-frontier-marker" "cursor-pointer" "outline-none"]
         :style {:color model/frontier-color}
         :data-role (str "portfolio-optimizer-frontier-point-" idx)
         :data-frontier-drag-target "true"
         :data-return (opt-format/format-pct (:expected-return point))
         :data-volatility (opt-format/format-pct (:volatility point))
         :data-sharpe (opt-format/format-decimal (:sharpe point))
         :data-frontier-callout-trigger callout-id
         :aria-label (frontier-callout/aria-label label rows)
         :draggable true
         :on {:click (model/point-actions target)
              :drag-start (model/point-actions target)
              :drag-enter (model/point-actions target)}}
     [:circle {:cx x
               :cy y
               :r 4
               :fill model/frontier-color}]
     [:circle {:cx x
               :cy y
               :r 11
               :fill "transparent"
               :stroke "rgba(212, 181, 88, 0.16)"}]
     (frontier-callout/focus-ring x y 15)
     (frontier-callout/hitbox
      (str "portfolio-optimizer-frontier-point-" idx "-hitbox")
      x
      y
      14)]))

(defn- frontier-point-callout
  [result idx point x-domain y-domain]
  (let [position (model/point-position x-domain y-domain point)
        label (str "Frontier Point " (inc idx))
        rows (frontier-callout/point-rows point)
        allocations (frontier-callout/allocation-summary
                     (:instrument-ids result)
                     (:weights point)
                     (:labels-by-instrument result))]
    (frontier-callout/callout
     {:bounds model/chart-bounds
      :data-role (str "portfolio-optimizer-frontier-callout-frontier-" idx)
      :data-frontier-callout-id (frontier-callout-id idx)
      :variant :blended
      :label label
      :point position
      :rows rows
      :allocations allocations})))

(defn chart-svg
  [draft result {:keys [points overlay-mode overlay-points current-point x-domain y-domain
                        x-ticks y-ticks positions x-axis-prefix y-axis-prefix]}]
  [:svg {:viewBox (str "0 0 " model/chart-width " " model/chart-height)
         :class ["h-[23.75rem]" "w-full" "overflow-visible" "text-trading-text"]
         :data-role "portfolio-optimizer-frontier-svg"
         :aria-label "Efficient frontier chart. X axis is annualized volatility. Y axis is annualized expected return."}
   (frontier-callout-style points overlay-mode overlay-points current-point)
   (frontier-target/gradient-defs)
   [:g {:data-role "portfolio-optimizer-frontier-grid"}
    (map-indexed (fn [idx value]
                   (grid-line :vertical idx (chart-axes/x-tick-position model/plot-geometry x-domain value)))
                 x-ticks)
    (map-indexed (fn [idx value]
                   (grid-line :horizontal idx (chart-axes/y-tick-position model/plot-geometry y-domain value)))
                 y-ticks)]
   [:line {:x1 model/chart-plot-left
           :x2 model/plot-right
           :y1 model/plot-bottom
           :y2 model/plot-bottom
           :stroke model/chart-axis-stroke}]
   [:line {:x1 model/chart-plot-left
           :x2 model/chart-plot-left
           :y1 model/chart-plot-top
           :y2 model/plot-bottom
           :stroke model/chart-axis-stroke}]
   [:g {:data-role "portfolio-optimizer-frontier-x-axis-ticks"}
    (map-indexed (fn [idx value]
                   (chart-axes/tick-label
                    model/plot-geometry
                    :x
                    idx
                    (chart-axes/x-tick-position model/plot-geometry x-domain value)
                    value))
                 x-ticks)]
   [:g {:data-role "portfolio-optimizer-frontier-y-axis-ticks"}
    (map-indexed (fn [idx value]
                   (chart-axes/tick-label
                    model/plot-geometry
                    :y
                    idx
                    (chart-axes/y-tick-position model/plot-geometry y-domain value)
                    value))
                 y-ticks)]
   [:text {:x model/plot-center-x
           :y (- model/chart-height 10)
           :fill "currentColor"
           :font-size 11
           :opacity 0.78
           :text-anchor "middle"
           :dominant-baseline "middle"
           :data-role "portfolio-optimizer-frontier-x-axis-label"}
    "Volatility (Annualized)"]
   [:text {:x 14
           :y model/plot-center-y
           :fill "currentColor"
           :font-size 11
           :opacity 0.78
           :text-anchor "middle"
           :dominant-baseline "middle"
           :transform (str "rotate(-90 14 " model/plot-center-y ")")
           :data-role "portfolio-optimizer-frontier-y-axis-label"}
    "Expected Return (Annualized)"]
   [:path {:d (model/path-data positions)
           :fill "none"
           :stroke model/frontier-color
           :stroke-width 2.5
           :stroke-linecap "round"
           :stroke-linejoin "round"
           :data-role "portfolio-optimizer-frontier-path"}]
   (map-indexed (fn [idx point]
                  (frontier-point draft idx point x-domain y-domain))
                points)
   (when current-point
     (frontier-current/marker
      {:bounds model/chart-bounds
       :point-position model/point-position
       :x-domain x-domain
       :y-domain y-domain
       :result result
       :render-callout? false}))
   (frontier-target/marker
    {:bounds model/chart-bounds
     :point-position model/point-position
     :x-domain x-domain
     :y-domain y-domain
     :result result
     :render-callout? false})
   (map #(frontier-overlays/marker
          {:bounds model/chart-bounds
           :overlay-mode overlay-mode
           :point-position model/point-position
           :x-domain x-domain
           :y-domain y-domain
           :point %
           :render-callout? false})
        overlay-points)
   [:text {:x model/plot-right
           :y (- model/chart-plot-top 12)
           :fill "currentColor"
           :font-size 10
           :opacity 0.58
           :text-anchor "end"}
    (str x-axis-prefix " / " y-axis-prefix)]
   [:g {:data-role "portfolio-optimizer-frontier-callout-layer"}
    (map-indexed (fn [idx point]
                   (frontier-point-callout result idx point x-domain y-domain))
                 points)
    (frontier-target/callout
     {:bounds model/chart-bounds
      :point-position model/point-position
      :x-domain x-domain
      :y-domain y-domain
      :result result})
    (when current-point
      (frontier-current/callout
       {:bounds model/chart-bounds
        :point-position model/point-position
        :x-domain x-domain
        :y-domain y-domain
        :result result}))
    (map #(frontier-overlays/callout
           {:bounds model/chart-bounds
            :overlay-mode overlay-mode
            :point-position model/point-position
            :x-domain x-domain
            :y-domain y-domain
            :point %})
         overlay-points)]])
