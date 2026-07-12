(ns hyperopen.views.portfolio.optimize.risk-breakdown-panel
  "The ALL ASSETS sub-view of the Equal Risk BREAKDOWN tab: one lane per
  balance-chart row (same cap, same signed-share display order) splitting the
  signed net contribution into its standalone (own-variance, always
  positive, purple) and diversification (cross-covariance, signed,
  green/red) components, both drawn from zero as paired sub-bars with a
  purple net marker at their sum and the shared dashed equal-target line
  behind. Numeric Div / Net columns mirror the contribution tab's
  Target / Deviation columns. The tab's default per-asset sub-view and the
  toggle between the two live in risk-asset-breakdown-panel, which composes
  this chart as the second view.

  Also home of the small plot primitives (backdrop, axis, lane scale ticks)
  the per-asset panel reuses — both views draw the same decomposition
  language, just grouped differently."
  (:require [hyperopen.portfolio.optimizer.application.view-model.equal-risk-structure
             :as structure-model]))

(defn format-signed-pct
  [value]
  (if (and (number? value) (js/isFinite value))
    (str (when (>= value 0) "+") (.toFixed (* 100 value) 1) "%")
    "—"))

(defn- sign-token
  [value]
  (cond
    (not (and (number? value) (js/isFinite value))) nil
    (neg? value) "negative"
    :else "positive"))

;; --- Shared plot primitives (used by the correlation panel too) ---------------

(defn plot-backdrop
  "Gridlines + zero axis + the continuous dashed equal-target line, drawn once
  behind a stack of lane rows. `row-class` picks the grid template (the
  4-column balance grid here, the 3-column decomposition grid in the
  correlation panel); `lead-cells`/`tail-cells` pad the non-lane columns."
  [{:keys [x] :as scale} target-share
   {:keys [row-class lead-cells tail-cells max-labels]
    :or {max-labels 8}}]
  (into [:div {:class [row-class "optimizer-risk-balance-backdrop"
                       "pointer-events-none"]}]
        (concat
         (repeat lead-cells [:span])
         [(into [:div {:class ["relative" "h-full"]}]
                (concat
                 (map (fn [tick]
                        [:div {:class ["optimizer-risk-balance-gridline"]
                               :style {:left (str (x tick) "%")}}])
                      (remove zero? (structure-model/scale-ticks scale
                                                                 max-labels)))
                 [[:div {:class ["optimizer-risk-balance-zeroline"]
                         :style {:left (str (x 0) "%")}}]]
                 (when (number? target-share)
                   [[:div {:class ["optimizer-risk-balance-targetline"]
                           :style {:left (str (x target-share) "%")}}]])))]
         (repeat tail-cells [:span]))))

(defn axis-rows
  "% tick row (+ optional title row when `title` is non-nil) padded into the
  same grid template as the lanes above it."
  [{:keys [x] :as scale}
   {:keys [title row-class lead-cells tail-cells max-labels]
    :or {max-labels 8}}]
  (let [pad (fn [content]
              (into [:div {:class [row-class]}]
                    (concat (repeat lead-cells [:span])
                            [content]
                            (repeat tail-cells [:span]))))]
    [:div
     (pad (into [:div {:class ["optimizer-risk-balance-axis" "relative"]}]
                (map (fn [tick]
                       [:span {:class ["optimizer-risk-balance-axis-label"
                                       "absolute" "-translate-x-1/2"
                                       "font-mono"]
                               :style {:left (str (x tick) "%")}}
                        (structure-model/format-pct tick 0)])
                     (structure-model/scale-ticks scale max-labels))))
     (when title
       (pad [:p {:class ["optimizer-risk-balance-axis-title"]} title]))]))

;; --- Lanes ---------------------------------------------------------------------

(defn- component-bar
  [{:keys [x]} value kind]
  (when (and (number? value) (js/isFinite value))
    (let [x0 (x 0)
          xv (x value)]
      [:div {:class ["optimizer-risk-decomp-bar"]
             :data-kind kind
             :data-sign (sign-token value)
             :style {:left (str (min x0 xv) "%")
                     :width (str (max 0.4 (js/Math.abs (- xv x0))) "%")}}])))

(defn- net-marker
  [{:keys [x]} value]
  (when (and (number? value) (js/isFinite value))
    [:div {:class ["optimizer-risk-decomp-net" "optimizer-risk-balance-marker"]
           :data-role "portfolio-optimizer-risk-breakdown-net"
           :style {:left (str (x value) "%")}}]))

(defn- breakdown-lane
  "Paired sub-bars from zero — standalone on the upper half, diversification
  on the lower — with the net marker at their sum. Drawing both from zero
  (not stacked) keeps the two components directly comparable; the net marker
  carries the sum."
  [scale {:keys [standalone diversification share]}]
  [:div {:class ["optimizer-risk-balance-lane" "relative" "min-w-0"]}
   (component-bar scale standalone "standalone")
   (component-bar scale diversification "diversification")
   (net-marker scale share)])

(defn- breakdown-row
  [scale {:keys [instrument-id label standalone diversification share]
          :as row}]
  [:div {:class ["optimizer-risk-balance-row"]
         :data-role "portfolio-optimizer-risk-breakdown-row"
         :data-instrument-id instrument-id
         :title (str label
                     " · standalone " (structure-model/format-pct standalone)
                     " · diversification " (format-signed-pct diversification)
                     " · net " (structure-model/format-pct share))}
   [:span {:class ["optimizer-risk-balance-label" "truncate"]} label]
   (breakdown-lane scale row)
   [:span {:class ["optimizer-risk-decomp-div-cell" "font-mono" "tabular-nums"]
           :data-role "portfolio-optimizer-risk-breakdown-diversification"
           :data-sign (sign-token diversification)}
    (format-signed-pct diversification)]
   [:span {:class ["optimizer-risk-decomp-net-cell" "font-mono" "tabular-nums"]
           :data-role "portfolio-optimizer-risk-breakdown-net-cell"}
    (structure-model/format-pct share)]])

(defn- legend-row
  [target-share]
  [:div {:class ["optimizer-risk-balance-row" "optimizer-risk-balance-legend-row"]}
   [:span]
   [:div {:class ["optimizer-risk-balance-legend"]}
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-decomp-swatch"]
             :data-kind "standalone"}]
     "Standalone risk"]
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-decomp-swatch"]
             :data-kind "diversification"
             :data-sign "negative"}]
     "Reduces risk (diversifier)"]
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-decomp-swatch"]
             :data-kind "diversification"
             :data-sign "positive"}]
     "Adds risk (concentration)"]
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-decomp-net"]}]
     "Net contribution"]
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-balance-legend-dash"]}]
     (str "Target (" (structure-model/format-pct target-share) ")")]]
   [:span {:class ["optimizer-risk-balance-col-head"]} "Div"]
   [:span {:class ["optimizer-risk-balance-col-head"]} "Net"]])

(defn- reading-note
  []
  [:p {:class ["optimizer-risk-balance-reading"]
       :data-role "portfolio-optimizer-risk-breakdown-reading"}
   [:span {:class ["optimizer-risk-balance-reading-label"]} "Reading this"]
   [:span {:class ["optimizer-risk-balance-reading-sep"]} "·"]
   (str "Standalone is the position's own-variance share; diversification is "
        "what its correlations with the rest of the book add or remove — "
        "green removes risk (a diversifier), red adds it (moves with the "
        "book). They sum to the net contribution — the bar on the Risk "
        "contribution tab.")])

(defn breakdown-panel
  "The all-assets chart body. `rows` come pre-joined from the structure
  view-model (balance rows + standalone/diversification); the composing
  per-asset panel passes the remainder line (and owns the KPI strip, so
  `kpi-strip` is normally nil here)."
  [{:keys [rows target-share kpi-strip overflow-note]}]
  (when (seq rows)
    (let [scale (structure-model/fit-scale
                 (concat [(or target-share 0)]
                         (keep :standalone rows)
                         (keep :diversification rows)
                         (keep :share rows)))]
      [:div
       kpi-strip
       [:div {:class ["optimizer-risk-balance-plot-frame"]
              :data-role "portfolio-optimizer-risk-breakdown-chart"}
        (legend-row target-share)
        [:div {:class ["optimizer-risk-balance-rows" "relative"]}
         (plot-backdrop scale target-share
                        {:row-class "optimizer-risk-balance-row"
                         :lead-cells 1
                         :tail-cells 2})
         (into [:div {:class ["relative" "space-y-1"]}]
               (map (partial breakdown-row scale))
               rows)]
        (axis-rows scale
                   {:title "Contribution Components (% of Total Volatility)"
                    :row-class "optimizer-risk-balance-row"
                    :lead-cells 1
                    :tail-cells 2})]
       (reading-note)
       overflow-note])))
