(ns hyperopen.views.portfolio.optimize.risk-diversification-summary
  "Portfolio-level diversification comparison for the Equal Risk
  DIVERSIFICATION tab. Geometry and economic direction are supplied by the
  pure structure read model; this namespace only formats and renders them."
  (:require [hyperopen.portfolio.optimizer.application.view-model.equal-risk-structure
             :as structure-model]))

(defn- direction-word
  [direction]
  (case direction
    :falls "falls"
    :rises "rises"
    :unchanged "is unchanged"
    "is unavailable"))

(defn- correlation-direction-copy
  [{:keys [current-polarity recommended-polarity direction]}]
  (case [current-polarity recommended-polarity]
    [:amplifying :offsetting] "turns offsetting"
    [:amplifying :neutral] "becomes neutral"
    [:offsetting :amplifying] "turns amplifying"
    [:offsetting :neutral] "becomes neutral"
    [:neutral :amplifying] "turns amplifying; more amplifying than current"
    [:neutral :offsetting] "turns offsetting"
    [:neutral :neutral] "remains neutral"
    [:amplifying :amplifying]
    (case direction
      :falls "less amplifying"
      :rises "more amplifying"
      "unchanged amplification")
    [:offsetting :offsetting]
    (case direction
      :falls "more offsetting"
      :rises "less offsetting"
      "unchanged offset")
    "comparison unavailable"))

(defn- outcome-direction-copy
  [{:keys [key direction] :as row}]
  (case key
    :diversification-benefit
    (case direction
      :rises "larger diversification benefit"
      :falls "smaller diversification benefit"
      :unchanged "unchanged diversification benefit"
      "comparison unavailable")

    :correlation-effect
    (correlation-direction-copy row)

    (direction-word direction)))

(defn- comparison-value
  [value]
  [:span {:class ["optimizer-risk-diversification-cell-value" "font-mono"
                  "tabular-nums"]
          :role "cell"}
   (structure-model/format-pct value)])

(defn- format-signed-points
  [points]
  (if (and (number? points) (js/isFinite points))
    (str (when (pos? points) "+") (.toFixed points 1) " pts")
    "—"))

(defn- change-cell
  [{:keys [change change-points]} direction-copy]
  [:div {:class ["optimizer-risk-diversification-change"]
         :role "cell"}
   [:span {:class ["optimizer-risk-diversification-cell-value" "font-mono"
                   "tabular-nums"]}
    (format-signed-points change-points)]
   (when (some? change)
     [:span {:class ["optimizer-risk-diversification-direction-copy"]}
      direction-copy])])

(defn- benchmark-row
  [{:keys [key label current-value recommended-value current-position
           recommended-position connector marker-overlap? direction tone]
    :as row}]
  [:div {:class ["optimizer-risk-diversification-row"
                 "optimizer-risk-diversification-row--benchmark"]
         :role "row"
         :data-role "portfolio-optimizer-risk-diversification-benchmark-row"
         :data-benchmark (name key)
         :data-overlap (str marker-overlap?)
         :data-direction (name direction)
         :data-tone (name tone)}
   [:div {:class ["optimizer-risk-diversification-row-label"]
          :role "rowheader"}
    [:span label]
    [:span {:class ["optimizer-risk-diversification-row-kind"]}
     "Annualized volatility"]]
   [:div {:class ["optimizer-risk-diversification-lane"
                  "optimizer-risk-diversification-lane-cell"]
          :role "cell"}
    [:div {:class ["optimizer-risk-diversification-lane-graphic"]
           :role "img"
           :aria-label (str label ": Current "
                            (structure-model/format-pct current-value)
                            ", Recommended "
                            (structure-model/format-pct recommended-value))}
     [:div {:class ["optimizer-risk-diversification-rail"]
            :aria-hidden true}]
     (when connector
       (let [{:keys [start end]} connector]
         [:span {:class ["optimizer-risk-diversification-connector"]
                 :aria-hidden true
                 :data-start start
                 :data-end end
                 :style {:left (str (min start end) "%")
                         :width (str (js/Math.abs (- end start)) "%")}}]))
     (when (some? current-position)
       [:span {:class ["optimizer-risk-diversification-marker"
                       "optimizer-risk-diversification-marker--current"]
               :data-role "portfolio-optimizer-risk-diversification-current-marker"
               :aria-hidden true
               :data-tone "neutral"
               :data-position current-position
               :style {:left (str current-position "%")}}])
     [:span {:class ["optimizer-risk-diversification-marker"
                     "optimizer-risk-diversification-marker--recommended"]
             :data-role "portfolio-optimizer-risk-diversification-recommended-marker"
             :aria-hidden true
             :data-tone "recommended"
             :data-position recommended-position
             :style {:left (str recommended-position "%")}}]]]
   (comparison-value current-value)
   (comparison-value recommended-value)
   (change-cell row (direction-word direction))])

(defn- outcome-row
  [{:keys [key label current-value recommended-value direction tone] :as row}]
  [:div {:class ["optimizer-risk-diversification-row"
                 "optimizer-risk-diversification-row--outcome"]
         :role "row"
         :data-role "portfolio-optimizer-risk-diversification-outcome-row"
         :data-outcome (name key)
         :data-direction (name direction)
         :data-tone (name tone)}
   [:div {:class ["optimizer-risk-diversification-row-label"]
          :role "rowheader"}
    [:span label]
    [:span {:class ["optimizer-risk-diversification-row-kind"]}
     (case key
       :diversification-benefit "Reduction from all-move-together"
       :correlation-effect "Modeled minus zero correlation"
       "Portfolio outcome")]]
   [:span {:class ["optimizer-risk-diversification-outcome-spacer"]
           :role "cell"}]
   (comparison-value current-value)
   (comparison-value recommended-value)
   (change-cell row (outcome-direction-copy row))])

(defn- render-decision-summary
  [{:keys [status modeled-direction stress-direction
           modeled-current-value modeled-recommended-value
           stress-current-value stress-recommended-value]}]
  [:p {:class ["optimizer-risk-diversification-decision"]
       :data-role "portfolio-optimizer-risk-diversification-decision-summary"}
   (if (= :comparison status)
     (str "Modeled volatility " (direction-word modeled-direction)
          " from " (structure-model/format-pct modeled-current-value)
          " to " (structure-model/format-pct modeled-recommended-value)
          "; all-move-together stress " (direction-word stress-direction)
          " from " (structure-model/format-pct stress-current-value)
          " to " (structure-model/format-pct stress-recommended-value) ".")
     (str "Current portfolio benchmarks are unavailable. Recommended modeled "
          "volatility is "
          (structure-model/format-pct modeled-recommended-value)
          " and all-move-together stress is "
          (structure-model/format-pct stress-recommended-value) "."))])

(defn- comparison-matrix
  [{:keys [benchmark-rows outcome-rows decision-summary]}]
  [:div {:class ["optimizer-risk-diversification-matrix"]
         :data-role "portfolio-optimizer-risk-diversification-matrix"
         :role "table"
         :aria-label "Portfolio diversification comparison"}
   [:div {:class ["optimizer-risk-diversification-header-group"]
          :role "rowgroup"}
    [:div {:class ["optimizer-risk-diversification-header"]
           :role "row"}
     [:span {:role "columnheader"} "Benchmark"]
     [:span {:class ["optimizer-risk-diversification-rail-header"]
             :role "columnheader"}
      "Shared scale"]
     [:span {:role "columnheader"} "Current"]
     [:span {:role "columnheader"} "Recommended"]
     [:span {:role "columnheader"} "Change"]]]
   (into [:div {:class ["optimizer-risk-diversification-row-group"]
                :role "rowgroup"}]
         (map benchmark-row)
         benchmark-rows)
   (into [:div {:class ["optimizer-risk-diversification-row-group"
                        "optimizer-risk-diversification-row-group--outcomes"]
                :role "rowgroup"}]
         (map outcome-row)
         outcome-rows)
   (render-decision-summary decision-summary)])

(defn diversification-summary
  "Renders one current/recommended comparison matrix when a valid target
  summary exists. Legacy results get an explicit re-run note while their
  existing final-weight attribution remains available below."
  [result]
  (if-let [model (structure-model/diversification-comparison-model result)]
    [:section {:class ["optimizer-risk-diversification-summary"]
               :data-role "portfolio-optimizer-risk-diversification-comparison"}
     [:div {:class ["optimizer-risk-diversification-intro"]}
      [:div
       [:p {:class ["optimizer-risk-corr-title"]}
        "Portfolio diversification"]
       [:p {:class ["optimizer-risk-diversification-explainer"]}
        (str "Equal Risk balances risk ownership; it does not minimize total "
             "volatility. These benchmarks compare both books on one "
             "absolute annualized-volatility scale.")]]
      [:div {:class ["optimizer-risk-diversification-legend"]
             :aria-label "Portfolio marker legend"}
       [:span {:class ["optimizer-risk-diversification-legend-item"]}
       [:span {:class ["optimizer-risk-diversification-legend-marker"
                        "optimizer-risk-diversification-legend-marker--current"]
                :aria-hidden true}]
        "Current"]
       [:span {:class ["optimizer-risk-diversification-legend-item"]}
       [:span {:class ["optimizer-risk-diversification-legend-marker"
                        "optimizer-risk-diversification-legend-marker--recommended"]
                :aria-hidden true}]
        "Recommended"]]]
     (comparison-matrix model)]
    [:div {:class ["optimizer-risk-diversification-unavailable"]
           :data-role "portfolio-optimizer-risk-diversification-unavailable"}
     [:p {:class ["optimizer-risk-corr-title"]}
      "Portfolio diversification unavailable"]
     [:p
      "Re-run this saved Equal Risk scenario to add the portfolio benchmarks. Final-weight attribution remains available below."]]))
