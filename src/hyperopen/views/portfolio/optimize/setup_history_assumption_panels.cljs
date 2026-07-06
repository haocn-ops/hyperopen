(ns hyperopen.views.portfolio.optimize.setup-history-assumption-panels
  "Read-only exposure-story panels for the proxy workflow card: the
  source-labeled prior basket, the regression's own estimate, the
  confidence-shrunk final modeled basket, the how-this-works explainer, and the
  diagnostics strip. Pure card -> hiccup projections with no dispatches (split
  from setup-history-assumptions when the risk-guardrails drawer pushed it past
  the namespace size gate, 2026-07-06)."
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]))

(def ^:private how-this-works-copy
  ["Qualitative prior chooses the proxy set."
   "Quantitative regression adjusts the split."
   "Confidence governs how much the regression can move the prior."
   "Specific risk and the cap keep the optimizer honest."])

(defn- basket-weight-rows
  "One label/percent line per basket member. `role-prefix` disambiguates the
  prior / regression / final panels for tests."
  [card-id role-prefix rows emphasized?]
  (into [:div {:class ["mt-2" "space-y-1"]}]
        (map (fn [{:keys [instrument-id label weight-percent]}]
               [:div {:class ["flex" "items-center" "justify-between" "gap-2"
                              "font-mono" "text-[0.75rem]"]
                      :data-role (str role-prefix card-id "-" instrument-id)}
                [:span {:class [(when emphasized? "font-semibold")]} label]
                [:span {:class [(if emphasized? "text-trading-text" "text-trading-muted")
                                (when emphasized? "font-semibold")]}
                 (str (.toFixed weight-percent 0) "%")]]))
        rows))

(defn prior-basket-panel
  "Panel A: the source-labeled prior. Never presented as the model's output -
  the final modeled basket below is what drives covariance."
  [card]
  (let [id (:instrument-id card)]
    (when (seq (:prior-basket card))
      [:div {:class ["border" "border-base-300" "bg-base-200/20" "p-2"]
             :data-role (str "portfolio-optimizer-history-assumption-prior-basket-" id)}
       [:span {:class controls/eyebrow-class} "Prior basket"]
       (when (:prior-source-label card)
         [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]
              :data-role (str "portfolio-optimizer-history-assumption-prior-source-" id)}
          (str "Source: " (:prior-source-label card))])
       (basket-weight-rows id
                           "portfolio-optimizer-history-assumption-prior-weight-"
                           (:prior-basket card)
                           false)])))

(defn regression-estimate-panel
  "Panel B: the joint regression's own estimate over the available overlap, or
  the reason it was skipped. Its weights never reach the optimizer directly -
  confidence shrinkage blends them with the prior first."
  [card]
  (let [id (:instrument-id card)
        estimate (:regression-estimate card)
        estimated? (= :estimated (:status estimate))]
    (when estimate
      [:div {:class ["border" "border-base-300" "bg-base-200/20" "p-2"]
             :data-role (str "portfolio-optimizer-history-assumption-regression-" id)
             :data-status (some-> (:status estimate) name)}
       [:span {:class controls/eyebrow-class} "Regression estimate"]
       (when estimated?
         [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]}
          "Regression estimates exposure over the available overlap."])
       (when estimated?
         (basket-weight-rows id
                             "portfolio-optimizer-history-assumption-regression-weight-"
                             (:rows estimate)
                             false))
       (when estimated?
         [:p {:class ["mt-1.5" "font-mono" "text-[0.6875rem]" "text-trading-muted"]}
          (:summary estimate)])
       (when-not estimated?
         [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]}
          (:message estimate)])])))

(defn final-basket-panel
  "Panel C: the confidence-shrunk final basket - the split covariance synthesis
  actually uses. Visually the loudest of the three."
  [card]
  (let [id (:instrument-id card)
        final (:final-basket card)]
    (when (seq (:rows final))
      [:div {:class ["border" "border-warning/50" "bg-warning/10" "p-2"]
             :data-role (str "portfolio-optimizer-history-assumption-final-basket-" id)}
       [:span {:class controls/eyebrow-class} "Final modeled basket"]
       [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]}
        "Final modeled basket after confidence-weighted shrinkage."]
       (basket-weight-rows id
                           "portfolio-optimizer-history-assumption-final-weight-"
                           (:rows final)
                           true)
       (when (:confidence-label final)
         [:p {:class ["mt-1.5" "font-mono" "text-[0.6875rem]" "text-trading-muted"]
              :data-role (str "portfolio-optimizer-history-assumption-confidence-" id)}
          (str "Confidence q " (:confidence-label final)
               " — controls how much the regression can move the prior")])])))

(defn diagnostics-strip
  [card]
  (let [id (:instrument-id card)]
    (when (seq (:diagnostics card))
      [:div {:data-role (str "portfolio-optimizer-history-assumption-diagnostics-" id)}
       (into [:div {:class ["grid" "gap-1" "sm:grid-cols-3"]}]
             (map (fn [{:keys [label value detail]}]
                    [:div {:class ["border" "border-base-300" "bg-base-200/20" "p-2"]}
                     [:p {:class ["text-[0.625rem]" "uppercase" "tracking-[0.08em]"
                                  "text-trading-muted/70"]}
                      label]
                     [:p {:class ["mt-0.5" "font-mono" "text-[0.75rem]" "font-semibold"]}
                      value]
                     (when detail
                       [:p {:class ["mt-0.5" "text-[0.625rem]" "leading-[1.4]"
                                    "text-trading-muted"]}
                        detail])]))
             (:diagnostics card))
       (when (:final-model-line card)
         [:p {:class ["mt-1.5" "border" "border-base-300" "bg-base-200/40" "px-2" "py-1"
                      "font-mono" "text-[0.6875rem]" "text-trading-muted"]}
          (:final-model-line card)])])))

(defn how-this-works
  []
  [:div {:class ["border" "border-primary/30" "bg-primary/5" "p-2"]}
   [:p {:class ["text-[0.625rem]" "font-semibold" "uppercase" "tracking-[0.08em]"
                "text-primary/80"]}
    "How this works"]
   (into [:div {:class ["mt-1" "space-y-0.5" "text-[0.6875rem]" "leading-[1.4]"
                        "text-trading-muted"]}]
         (map (fn [line] [:p line]))
         how-this-works-copy)])
