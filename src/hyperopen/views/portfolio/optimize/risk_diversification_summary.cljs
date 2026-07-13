(ns hyperopen.views.portfolio.optimize.risk-diversification-summary
  "Portfolio-level diversification comparison for the Equal Risk
  DIVERSIFICATION tab. Geometry is supplied by the pure structure read model;
  this namespace only renders persisted scalar benchmarks."
  (:require [hyperopen.portfolio.optimizer.application.view-model.equal-risk-structure
             :as structure-model]
            [hyperopen.views.portfolio.optimize.risk-breakdown-panel
             :as breakdown-panel]))

(defn- benchmark-row
  [{:keys [positions]} {:keys [key label value]}]
  (let [position (get positions key)]
    [:div {:class ["optimizer-risk-diversification-benchmark"]}
     [:span {:class ["optimizer-risk-diversification-benchmark-label"]}
      label]
     [:div {:class ["optimizer-risk-diversification-lane"]}
      [:div {:class ["optimizer-risk-diversification-rail"]}]
      [:span {:class ["optimizer-risk-diversification-marker"]
              :data-role (str "portfolio-optimizer-risk-diversification-"
                              (name key))
              :data-benchmark (name key)
              :data-position position
              :style {:left (str position "%")}}]]
     [:span {:class ["optimizer-risk-diversification-value" "font-mono"
                     "tabular-nums"]}
      (structure-model/format-pct value)]]))

(defn- comparison-card
  [{:keys [key label benchmarks benefit-copy correlation-direction
           correlation-effect correlation-copy] :as card}]
  [:article {:class ["optimizer-risk-diversification-card"]
             :data-role (str "portfolio-optimizer-risk-diversification-"
                             (name key))}
   [:div {:class ["optimizer-risk-diversification-card-head"]}
    [:p {:class ["optimizer-risk-diversification-card-label"]} label]
    [:p {:class ["optimizer-risk-diversification-benefit" "font-mono"
                "tabular-nums"]}
     benefit-copy]]
   (into [:div {:class ["optimizer-risk-diversification-benchmarks"]}]
         (map (partial benchmark-row card))
         benchmarks)
   [:p {:class ["optimizer-risk-diversification-correlation"]
        :data-effect (name correlation-direction)}
    (str correlation-copy " ("
         (breakdown-panel/format-signed-pct correlation-effect) ").")]])

(defn diversification-summary
  "Renders the current/recommended benchmark comparison when at least one
  valid persisted summary exists. Legacy results get an explicit re-run note
  while their existing final-weight attribution remains available below."
  [result]
  (if-let [{:keys [cards]} (structure-model/diversification-comparison-model
                            result)]
    [:section {:class ["optimizer-risk-diversification-summary"]
               :data-role "portfolio-optimizer-risk-diversification-comparison"}
     [:div {:class ["optimizer-risk-diversification-intro"]}
      [:div
       [:p {:class ["optimizer-risk-corr-title"]}
        "Portfolio diversification"]
       [:p {:class ["optimizer-risk-diversification-explainer"]}
        (str "Equal Risk balances risk ownership; it does not minimize total "
             "volatility. These benchmarks show how much modeled volatility "
             "is below all held position P&L streams moving together.")]]
      [:p {:class ["optimizer-risk-diversification-scale-note"]}
       "Shared absolute volatility scale"]]
     (into [:div {:class ["optimizer-risk-diversification-cards"]}]
           (map comparison-card)
           cards)]
    [:div {:class ["optimizer-risk-diversification-unavailable"]
           :data-role "portfolio-optimizer-risk-diversification-unavailable"}
     [:p {:class ["optimizer-risk-corr-title"]}
      "Portfolio diversification unavailable"]
     [:p
      "Re-run this saved Equal Risk scenario to add the portfolio benchmarks. Final-weight attribution remains available below."]]))
