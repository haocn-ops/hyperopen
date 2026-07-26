(ns hyperopen.views.portfolio.optimize.frontier-current
  (:require [hyperopen.portfolio.optimizer.application.view-model.volatility-intuition
             :as volatility-intuition-vm]
            [hyperopen.views.portfolio.optimize.frontier-callout :as frontier-callout]))

(def current-color "#2f9bff")
(def current-fill "rgba(47, 155, 255, 0.22)")
(def current-stroke "rgba(117, 211, 255, 0.92)")

(defn- current-model
  [{:keys [point-position x-domain y-domain result]}]
  (let [point {:expected-return (:current-expected-return result)
               :volatility (:current-volatility result)
               :sharpe (get-in result [:current-performance :in-sample-sharpe])}
        position (point-position x-domain y-domain point)
        {:keys [x y]} position
        label-x (+ x 24)
        label-y (+ y 14)
        label "Current"
        rows (frontier-callout/point-rows
              point
              {:exposure (frontier-callout/exposure-summary result :current)
               :horizons (volatility-intuition-vm/horizons (:volatility point))})
        allocations (frontier-callout/allocation-summary
                     (or (:current-portfolio-instrument-ids result)
                         (:instrument-ids result))
                     (or (:current-portfolio-weights result)
                         (:current-weights result))
                     (:labels-by-instrument result))]
    {:position position
     :x x
     :y y
     :label-x label-x
     :label-y label-y
     :label label
     :rows rows
     :allocations allocations}))

(defn callout
  [{:keys [bounds] :as opts}]
  (let [{:keys [position label rows allocations]} (current-model opts)]
    (frontier-callout/callout
     {:bounds bounds
      :data-role "portfolio-optimizer-frontier-callout-current"
      :variant :blended
      :label label
      :point position
      :rows rows
      :allocations allocations})))

(defn marker
  [{:keys [render-callout?] :as opts}]
  (let [{:keys [x y label-x label-y label rows]} (current-model opts)]
    [:g {:class ["portfolio-frontier-marker" "outline-none"]
         :style {:color current-color}
         :data-role "portfolio-optimizer-frontier-current-marker"
         :role "img"
         :tabIndex 0
         :tabindex 0
         :focusable "true"
         :aria-label (frontier-callout/aria-label label rows)}
     [:line {:x1 (+ x 8)
             :y1 (+ y 6)
             :x2 label-x
             :y2 (+ label-y 10)
             :stroke "rgba(47, 155, 255, 0.50)"
             :stroke-width 1
             :stroke-dasharray "2 3"
             :data-role "portfolio-optimizer-frontier-current-leader-line"}]
     [:circle {:cx x
               :cy y
               :r 13
               :fill current-fill
               :opacity 0.62
               :data-role "portfolio-optimizer-frontier-current-halo"}]
     [:circle {:cx x
               :cy y
               :r 8.5
               :fill "rgba(7, 18, 21, 0.94)"
               :stroke current-stroke
               :stroke-width 1.2
               :data-role "portfolio-optimizer-frontier-current-ring"}]
     [:circle {:cx x
               :cy y
               :r 3.8
               :fill current-color
               :data-role "portfolio-optimizer-frontier-current-core"}]
     (frontier-callout/focus-ring x y 18)
     [:g {:data-role "portfolio-optimizer-frontier-current-label"
          :transform (str "translate(" label-x " " label-y ")")}
      [:rect {:x 0
              :y 0
              :width 56
              :height 22
              :rx 3
              :fill "rgba(7, 30, 56, 0.94)"
              :stroke "rgba(117, 211, 255, 0.54)"}]
      [:text {:x 28
              :y 11
              :fill "#e3f5ff"
              :font-size 11
              :font-weight 650
              :text-anchor "middle"
              :dominant-baseline "middle"}
       "Current"]]
     (frontier-callout/hitbox
      "portfolio-optimizer-frontier-current-marker-hitbox"
      x
      y
      16)
     (when-not (false? render-callout?)
       (callout opts))]))
