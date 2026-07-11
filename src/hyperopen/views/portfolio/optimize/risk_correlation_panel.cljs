(ns hyperopen.views.portfolio.optimize.risk-correlation-panel
  "CORRELATION tab of the Equal Risk risk-contribution card (designer spec
  2026-07-11): left, the correlation heatmap with a POSITION P&L /
  UNDERLYING RETURNS toggle — position-P&L correlations are the underlying
  correlations sign-flipped by position sides, the actual correlation between
  the trades as held; right, the CONTRIBUTION BREAKDOWN block decomposing the
  selected asset's net contribution into standalone risk plus diversification
  effect over a shared axis with the dashed equal-target line.

  The mode toggle is DOM state (label-wrapped sr-only radios + scoped :has()
  CSS, exactly like the card's tabs): BOTH heatmap grids and captions are
  pre-rendered and CSS swaps them, so toggling never re-renders. Every cell
  carries a native multi-line title with both correlations and the
  portfolio-risk verdict — hover answers 'economically correlated but held
  opposite?' without a chart change. Which ASSET the breakdown explains is
  real app state (Allocation-row clicks); the panel just renders the model."
  (:require [hyperopen.portfolio.optimizer.application.view-model.equal-risk-structure
             :as structure-model]
            [hyperopen.views.portfolio.optimize.risk-breakdown-panel
             :as breakdown-panel]))

(def ^:private decomp-row-class "optimizer-risk-decomp-row")

;; --- Heatmap -------------------------------------------------------------------

(defn- mode-tab
  [radio-name mode text]
  [:label {:class ["optimizer-risk-corr-mode-tab"]
           :data-role (str "portfolio-optimizer-risk-corr-mode-" mode)}
   [:input {:type "radio"
            :name radio-name
            :class ["sr-only"]
            :data-corr-mode mode}]
   text])

(defn- corr-cell
  [row-entry col-entry {:keys [diagonal?] :as cell} mode]
  (let [value (get cell mode)
        strength (when (number? value)
                   (js/Math.round (* 72 (js/Math.abs value))))]
    [:span {:class ["optimizer-risk-corr-cell" "font-mono" "tabular-nums"]
            :data-role "portfolio-optimizer-risk-corr-cell"
            :data-row (:instrument-id row-entry)
            :data-col (:instrument-id col-entry)
            :data-sign (cond
                         (nil? value) "missing"
                         (neg? value) "negative"
                         :else "positive")
            :data-diagonal (when diagonal? "true")
            :style {:--corr-strength (str (or strength 0) "%")}
            :title (structure-model/cell-title row-entry col-entry cell)}
     (structure-model/format-correlation value)]))

(defn- matrix-grid
  "One heatmap for one mode. Column count is dynamic, so the tracks are an
  inline style (static-CSS track rules only cover fixed layouts; Tailwind
  arbitrary grid-cols are unreliable under JIT watch)."
  [{:keys [entries cells]} mode]
  (let [template (str "minmax(2.75rem, auto) repeat("
                      (count entries)
                      ", minmax(0, 1fr))")]
    [:div {:class ["optimizer-risk-corr-grid"]
           :data-role (str "portfolio-optimizer-risk-corr-grid-" (name mode))
           :style {:grid-template-columns template}}
     (into [:div {:class ["contents"]}]
           (cons [:span {:class ["optimizer-risk-corr-corner"]}]
                 (map (fn [{:keys [label]}]
                        [:span {:class ["optimizer-risk-corr-col-label"
                                        "truncate" "font-mono"]}
                         label])
                      entries)))
     (into [:div {:class ["contents"]}]
           (mapcat (fn [row-entry row-cells]
                     (cons [:span {:class ["optimizer-risk-corr-row-label"
                                           "truncate" "font-mono"]}
                            (:label row-entry)]
                           (map (fn [col-entry cell]
                                  (corr-cell row-entry col-entry cell mode))
                                entries
                                row-cells)))
                   entries
                   cells))]))

(defn- legend
  [mode]
  [:div {:class ["optimizer-risk-corr-legend"]
         :data-role "portfolio-optimizer-risk-corr-legend"}
   [:div {:class ["optimizer-risk-corr-legend-bar"]}]
   (into [:div {:class ["optimizer-risk-corr-legend-labels" "font-mono"]}]
         (map (fn [value] [:span value]))
         ["-1.0" "-0.5" "0.0" "0.5" "1.0"])
   [:p {:class ["optimizer-risk-corr-caption"]
        :title (if (= :position mode)
                 "Corr(position i P&L, position j P&L) = side i × side j × underlying correlation. A short flips the sign of its P&L correlations."
                 "The plain correlation of the assets' returns, ignoring which side you hold.")}
    (if (= :position mode)
      "Correlation between held position P&Ls"
      "Correlation between underlying asset returns")
    [:span {:class ["optimizer-risk-corr-info"]} "ⓘ"]]])

(defn- heatmap-block
  [{:keys [hidden-count] :as corr-model} radio-name]
  [:div {:class ["optimizer-risk-corr-block"]
         :data-role "portfolio-optimizer-risk-correlation-heatmap"}
   [:div {:class ["optimizer-risk-corr-head"]}
    [:div {:class ["min-w-0"]}
     [:p {:class ["optimizer-risk-corr-title"]
          :data-corr-only "position"}
      "Position P&L correlation"]
     [:p {:class ["optimizer-risk-corr-title"]
          :data-corr-only "underlying"}
      "Underlying return correlation"]]
    [:div {:class ["optimizer-risk-corr-mode-tabs"]
           :data-role "portfolio-optimizer-risk-corr-mode-tabs"}
     (mode-tab radio-name "position" "Position P&L")
     (mode-tab radio-name "underlying" "Underlying returns")]]
   [:div {:data-corr-only "position"}
    (matrix-grid corr-model :position)
    (legend :position)]
   [:div {:data-corr-only "underlying"}
    (matrix-grid corr-model :underlying)
    (legend :underlying)]
   (when (pos? (or hidden-count 0))
     [:p {:class ["mt-2" "text-xs" "text-trading-muted"]
          :data-role "portfolio-optimizer-risk-corr-overflow"}
      (str "+ " hidden-count " more held asset"
           (when (not= 1 hidden-count) "s")
           " not shown (the largest risk shares are kept).")])])

;; --- Selected-asset contribution breakdown --------------------------------------

(defn- decomp-row
  [scale {:keys [label sub value bar-value kind role]}]
  [:div {:class [decomp-row-class]
         :data-role role}
   [:div {:class ["min-w-0"]}
    [:p {:class ["optimizer-risk-decomp-label"]} label]
    [:p {:class ["optimizer-risk-decomp-sub"]} sub]]
   [:span {:class ["optimizer-risk-decomp-value" "font-mono" "tabular-nums"]
           :data-kind kind
           :data-sign (cond
                        (not (number? bar-value)) nil
                        (neg? bar-value) "negative"
                        :else "positive")}
    value]
   [:div {:class ["optimizer-risk-balance-lane" "relative" "min-w-0"]}
    (when (number? bar-value)
      (let [x0 ((:x scale) 0)
            xv ((:x scale) bar-value)]
        [:div {:class ["optimizer-risk-decomp-bar"
                       "optimizer-risk-decomp-bar--full"]
               :data-kind kind
               :data-sign (if (neg? bar-value) "negative" "positive")
               :style {:left (str (min x0 xv) "%")
                       :width (str (max 0.4 (js/Math.abs (- xv x0))) "%")}}]))]])

(defn- breakdown-block
  [{:keys [label side standalone diversification net target-share]}]
  (let [scale (structure-model/fit-scale
               [(or target-share 0) standalone diversification net])
        side-word (structure-model/side-label side)]
    [:div {:class ["optimizer-risk-corr-block"]
           :data-role "portfolio-optimizer-risk-selected-breakdown"}
     [:div {:class ["optimizer-risk-corr-head"]}
      [:div {:class ["min-w-0"]}
       [:p {:class ["optimizer-risk-corr-title"]} "Contribution breakdown"]
       [:p {:class ["optimizer-risk-decomp-selected"]}
        "Selected asset: "
        [:span {:class ["optimizer-risk-decomp-selected-asset" "font-mono"]
                :data-role "portfolio-optimizer-risk-selected-asset"
                :data-side (some-> side name)}
         (str label (when side-word (str " " side-word)))]]]]
     [:div {:class ["optimizer-risk-balance-rows" "relative" "mt-2"]}
      (breakdown-panel/plot-backdrop scale target-share
                                     {:row-class decomp-row-class
                                      :lead-cells 2
                                      :tail-cells 0
                                      :max-labels 4})
      [:div {:class ["relative" "space-y-2"]}
       (decomp-row scale
                   {:label "Standalone risk"
                    :sub "Risk if held in isolation"
                    :value (structure-model/format-pct standalone)
                    :bar-value standalone
                    :kind "standalone"
                    :role "portfolio-optimizer-risk-selected-standalone"})
       (decomp-row scale
                   {:label "Diversification effect"
                    :sub "From correlations with other positions"
                    :value (breakdown-panel/format-signed-pct diversification)
                    :bar-value diversification
                    :kind "diversification"
                    :role "portfolio-optimizer-risk-selected-diversification"})
       (decomp-row scale
                   {:label "Net risk contribution"
                    :sub "To total portfolio volatility"
                    :value (structure-model/format-pct net)
                    :bar-value net
                    :kind "net"
                    :role "portfolio-optimizer-risk-selected-net"})]]
     (breakdown-panel/axis-rows scale
                                {:row-class decomp-row-class
                                 :lead-cells 2
                                 :tail-cells 0
                                 :max-labels 4})
     [:p {:class ["optimizer-risk-decomp-identity"]
          :data-role "portfolio-optimizer-risk-selected-identity"
          :title "The signed Euler contribution splits exactly into the position's own-variance term plus its cross-covariance term."}
      "Net contribution = standalone risk + diversification effect"
      [:span {:class ["optimizer-risk-corr-info"]} "ⓘ"]]]))

;; --- Panel ----------------------------------------------------------------------

(defn correlation-panel
  "Panel body for the CORRELATION tab; nil when the result predates
  :risk-structure or holds no positions (the card then drops the tab).
  `selected-instrument-id` is the app-state selection; fallback rules live in
  the view-model."
  [{:keys [result kpi-strip selected-instrument-id]}]
  (when-let [corr-model (structure-model/correlation-model result)]
    (let [selected (structure-model/selected-breakdown result
                                                       selected-instrument-id)
          radio-name (str (structure-model/risk-view-radio-name result)
                          "-corr-mode")]
      [:div
       kpi-strip
       [:div {:class ["optimizer-risk-corr-layout"]}
        (heatmap-block corr-model radio-name)
        (when selected
          (breakdown-block selected))]])))
