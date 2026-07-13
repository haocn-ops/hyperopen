(ns hyperopen.views.account-info.margin-rec-curve
  "SVG chart for the margin recommendation panel: modeled probability of
  liquidation as a function of isolated collateral, with Current and
  Recommended markers sitting on the sampled curve the engine emits
  (`:curve` on the recommendation result)."
  (:require [clojure.string :as str]
            [hyperopen.views.account-info.shared :as shared]))

(defn- fmt-usd
  [value]
  (if (number? value)
    (str "$" (shared/format-currency value))
    "--"))

(defn- fmt-probability
  [p]
  (cond
    (not (number? p)) "--"
    (< p 0.0005) "<0.1%"
    :else (str (.toFixed (* 100 p) 1) "%")))

(def ^:private chart-width 440)
(def ^:private chart-height 214)
(def ^:private plot-left 38)
(def ^:private plot-right 430)
(def ^:private plot-top 22)
(def ^:private plot-bottom 168)
(def ^:private grid-stroke "rgb(var(--ho-text-muted) / 0.25)")
;; The curve is stroked with a horizontal gradient: amber where collateral is
;; low (liquidation likely) on the left, green where it is high (safe) on the
;; right, blending across the band between the current and recommended markers.
;; `curve-stroke` doubles as the solid fallback when a marker is missing.
(def ^:private curve-stroke "rgb(var(--ho-warn))")
(def ^:private curve-stroke-safe "rgb(var(--ho-buy))")
(def ^:private curve-gradient-id "margin-rec-curve-gradient")
(def ^:private label-box-fill "rgb(var(--ho-bg-deep) / 0.92)")
(def ^:private label-title-fill "rgb(var(--ho-text) / 0.92)")
(def ^:private label-box-top 26)
(def ^:private label-box-height 44)
(def ^:private label-box-width 66)

(defn- chart-x
  [x-max e]
  (+ plot-left
     (* (/ (-> e (max 0) (min x-max)) x-max)
        (- plot-right plot-left))))

(defn- chart-y
  [p]
  (+ plot-top (* (- 1 (-> p (max 0) (min 1))) (- plot-bottom plot-top))))

(defn- axis-usd
  [value]
  (str "$" (js/parseFloat (.toFixed value 2))))

(defn- marker
  "Dashed drop line, dot on the curve, and a small labeled box:
  name / collateral / probability."
  [role color-class label-x marker-x e p title]
  (let [y (chart-y p)
        box-left (- label-x (/ label-box-width 2))]
    [:g {:class [color-class]
         :data-role role}
     [:line {:x1 marker-x
             :x2 marker-x
             :y1 (+ label-box-top label-box-height)
             :y2 plot-bottom
             :stroke "currentColor"
             :stroke-dasharray "3 3"
             :opacity 0.55}]
     [:circle {:cx marker-x
               :cy y
               :r 4.5
               :fill "currentColor"}]
     [:rect {:x box-left
             :y label-box-top
             :width label-box-width
             :height label-box-height
             :rx 5
             :fill label-box-fill
             :stroke "currentColor"
             :stroke-opacity 0.45}]
     [:text {:x label-x
             :y (+ label-box-top 12)
             :fill label-title-fill
             :font-size 9
             :text-anchor "middle"}
      title]
     [:text {:x label-x
             :y (+ label-box-top 25)
             :fill "currentColor"
             :font-size 10
             :font-weight 600
             :text-anchor "middle"}
      (fmt-usd e)]
     [:text {:x label-x
             :y (+ label-box-top 38)
             :fill "currentColor"
             :font-size 10
             :font-weight 600
             :text-anchor "middle"}
      (fmt-probability p)]]))

(defn- curve-chart
  [{:keys [x-max points]} current-e p-now rec-e p-after]
  (let [polyline (str/join " " (map (fn [{:keys [e p]}]
                                      (str (chart-x x-max e) ","
                                           (chart-y p)))
                                    points))
        cur-x (when (number? current-e) (chart-x x-max current-e))
        rec-x (when (number? rec-e) (chart-x x-max rec-e))
        plot-width (- plot-right plot-left)
        marker-frac (fn [x] (-> (/ (- x plot-left) plot-width) (max 0) (min 1)))
        cur-frac (when cur-x (marker-frac cur-x))
        ;; Recommended sits at or right of current in normal cases; clamp so the
        ;; gradient stops stay ordered even in the within-target edge case.
        rec-frac (when rec-x (max (marker-frac rec-x) (or cur-frac 0)))
        gradient? (and cur-frac rec-frac)
        curve-paint (if gradient?
                      (str "url(#" curve-gradient-id ")")
                      curve-stroke)
        half-box (/ label-box-width 2)
        cur-label-x (when cur-x
                      (-> cur-x
                          (max (+ plot-left half-box))
                          (min (- plot-right label-box-width half-box 8))))
        rec-label-x (when rec-x
                      (-> rec-x
                          (max (if cur-label-x
                                 (+ cur-label-x label-box-width 8)
                                 (+ plot-left half-box)))
                          (min (- plot-right half-box))))]
    (-> [:svg {:viewBox (str "0 0 " chart-width " " chart-height)
               :class ["w-full" "overflow-visible" "text-trading-text"]
               :data-role "margin-rec-curve"
               :aria-label "Modeled probability of liquidation versus isolated margin. The curve falls as collateral increases; markers show the current and recommended margin."}]
        ;; horizontal grid + y labels at 0 / 50 / 100%
        (into (map (fn [p]
                     (let [y (chart-y p)]
                       [:g {:key (str "y-" p)}
                        [:line {:x1 plot-left :x2 plot-right :y1 y :y2 y
                                :stroke grid-stroke}]
                        [:text {:x (- plot-left 6)
                                :y (+ y 3)
                                :fill "currentColor"
                                :font-size 9
                                :opacity 0.6
                                :text-anchor "end"}
                         (str (js/Math.round (* 100 p)) "%")]]))
                   [0 0.5 1]))
        ;; x ticks
        (into (map (fn [i]
                     (let [value (* (/ x-max 4) i)
                           x (chart-x x-max value)]
                       [:text {:key (str "x-" i)
                               :x x
                               :y (+ plot-bottom 14)
                               :fill "currentColor"
                               :font-size 9
                               :opacity 0.6
                               :text-anchor "middle"}
                        (axis-usd value)]))
                   (range 5)))
        (conj (when gradient?
                [:defs
                 [:linearGradient {:id curve-gradient-id
                                   :x1 "0%" :y1 "0%" :x2 "100%" :y2 "0%"}
                  [:stop {:offset "0%" :stop-color curve-stroke}]
                  [:stop {:offset (str (.toFixed (* 100 cur-frac) 2) "%")
                          :stop-color curve-stroke}]
                  [:stop {:offset (str (.toFixed (* 100 rec-frac) 2) "%")
                          :stop-color curve-stroke-safe}]
                  [:stop {:offset "100%" :stop-color curve-stroke-safe}]]])
              [:text {:x (/ (+ plot-left plot-right) 2)
                      :y (+ plot-bottom 30)
                      :fill "currentColor"
                      :font-size 9
                      :opacity 0.72
                      :text-anchor "middle"}
               "Isolated margin (USDC)"]
              [:polyline {:points polyline
                          :fill "none"
                          :stroke curve-paint
                          :stroke-width 2
                          :stroke-linejoin "round"}]
              (when (and cur-x (number? p-now))
                (marker "margin-rec-curve-current" "text-amber-400"
                        cur-label-x cur-x current-e p-now "Current"))
              (when (and rec-x (number? p-after))
                (marker "margin-rec-curve-recommended" "text-trading-green"
                        rec-label-x rec-x rec-e p-after "Recommended"))))))

(defn curve-card
  [curve current-e p-now rec-e p-after]
  (when (seq (:points curve))
    [:div {:class ["rounded-lg" "bg-base-300/40" "p-3"]
           :data-role "margin-rec-curve-card"}
     [:div {:class ["mb-1" "flex" "items-center" "gap-1.5" "text-xs" "font-semibold"
                    "text-trading-text"]}
      "Modeled probability of liquidation vs. collateral"
      [:span {:class ["cursor-help" "font-normal" "text-trading-text-secondary"]
              :title (str "Each point is the modeled probability that this position"
                          " would be liquidated before your next likely intervention,"
                          " at that level of isolated margin. The markers show where"
                          " your current and recommended margin sit on the curve.")}
       "ⓘ"]]
     (curve-chart curve current-e p-now rec-e p-after)]))
