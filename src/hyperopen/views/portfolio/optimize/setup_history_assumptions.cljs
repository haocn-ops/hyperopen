(ns hyperopen.views.portfolio.optimize.setup-history-assumptions
  "Inline setup cards for assets that lack optimizer-safe history. Views render the
  view-model cards and dispatch the carried action ids only."
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.views.portfolio.optimize.setup-controls :as controls]))

(def ^:private intro-copy
  (str "Return and volatility are not enough. HyperOpen also needs to know how this "
       "asset moves relative to the rest of the portfolio."))

(defn- percent-input
  [{:keys [label field role action]}]
  [:label {:class ["block" "border" "border-base-300" "bg-base-200/20" "p-2"]}
   [:span {:class controls/eyebrow-class} label]
   [:div {:class ["mt-2" "flex" "items-center" "gap-1"]}
    [:input {:type "text"
             :inputmode "decimal"
             :class controls/input-class
             :data-role role
             :value (or (:input-text field) "")
             :on {:input [action]}}]
    [:span {:class ["font-mono" "text-[0.6875rem]" "text-trading-muted"]} "%"]]])

(defn- mode-selector
  [card]
  (let [id (:instrument-id card)]
    (into [:div {:class ["mt-3" "grid" "grid-cols-2" "border" "border-base-300"]
                 :data-role (str "portfolio-optimizer-history-assumption-mode-" id)}]
          (map (fn [{:keys [value label]}]
                 (controls/segmented-button
                  label
                  (= value (:mode card))
                  (str "portfolio-optimizer-history-assumption-mode-" id "-" (name value))
                  [(get-in card [:actions :set-mode]) id value]))
               (:mode-options card)))))

(defn- relationship-selector
  [card]
  (let [id (:instrument-id card)
        selected (get-in card [:relationship :value])]
    [:div {:class ["mt-2"]}
     [:p {:class controls/eyebrow-class} "Relationship strength"]
     (into [:div {:class ["mt-1" "grid" "grid-cols-3" "border" "border-base-300"]
                  :data-role (str "portfolio-optimizer-history-assumption-relationship-" id)}]
           (map (fn [{:keys [value label]}]
                  (controls/segmented-button
                   label
                   (= value selected)
                   (str "portfolio-optimizer-history-assumption-relationship-" id "-" (name value))
                   [(get-in card [:actions :set-proxy-relationship]) id value]))
                (get-in card [:relationship :options])))]))

(defn- proxy-selector
  [card]
  (let [id (:instrument-id card)]
    [:label {:class ["block" "mt-2" "border" "border-base-300" "bg-base-200/20" "p-2"]}
     [:span {:class controls/eyebrow-class} "Proxy asset"]
     (into [:select {:class (conj controls/input-class "mt-2")
                     :data-role (str "portfolio-optimizer-history-assumption-proxy-" id)
                     :value (or (get-in card [:proxy :selected-id]) "")
                     :on {:change [[(get-in card [:actions :set-proxy-instrument])
                                    id
                                    [:event.target/value]]]}}
            [:option {:value ""} "Select a proxy asset"]]
           (map (fn [{:keys [value label]}]
                  [:option {:value value} label])
                (get-in card [:proxy :options])))]))

(defn- card-fields
  [card]
  (let [id (:instrument-id card)
        proxy? (= :proxy (:mode card))]
    (cond-> [:div {:class ["space-y-2"]}
             (percent-input
              {:label "Expected annual return"
               :field (:expected-return card)
               :role (str "portfolio-optimizer-history-assumption-return-" id)
               :action [(get-in card [:actions :set-expected-return]) id
                        [:event.target/value]]})
             (percent-input
              {:label "Expected annual volatility"
               :field (:volatility card)
               :role (str "portfolio-optimizer-history-assumption-volatility-" id)
               :action [(get-in card [:actions :set-expected-volatility]) id
                        [:event.target/value]]})]
      proxy? (conj (proxy-selector card))
      proxy? (conj (relationship-selector card))
      :always (conj (percent-input
                     {:label "Max weight cap"
                      :field (:max-weight card)
                      :role (str "portfolio-optimizer-history-assumption-max-weight-" id)
                      :action [(get-in card [:actions :set-max-weight-cap]) id
                               [:event.target/value]]})))))

(defn- card-errors
  [card]
  (when (seq (:errors card))
    (into [:ul {:class ["space-y-1" "text-[0.65rem]" "text-warning"]
                :data-role (str "portfolio-optimizer-history-assumption-errors-"
                                (:instrument-id card))}]
          (map (fn [message] [:li message]) (:errors card)))))

(defn- assumption-card
  [card]
  (let [id (:instrument-id card)]
    [:section {:class ["border" "border-base-300" "bg-base-100/70" "p-3" "space-y-2"]
               :data-role (:role card)}
     [:div {:class ["flex" "items-start" "justify-between" "gap-2"]}
      [:div
       [:p {:class controls/section-title-class}
        (str "Assumptions needed for " (:label card))]
       [:p {:class ["mt-1" "font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.08em]"
                    "text-trading-muted/70"]}
        (:status-label card)]]
      [:button {:type "button"
                :class ["text-[0.6rem]" "uppercase" "tracking-[0.08em]"
                        "text-trading-muted" "hover:text-warning"]
                :data-role (str "portfolio-optimizer-history-assumption-clear-" id)
                :on {:click [[(get-in card [:actions :clear]) id]]}}
       "Clear"]]
     [:p {:class ["text-[0.65rem]" "leading-[1.5]" "text-trading-muted"]} intro-copy]
     (mode-selector card)
     (when (:mode card)
       (card-fields card))
     (when (:note card)
       [:p {:class ["border" "border-warning/40" "bg-warning/10" "p-2"
                    "text-[0.65rem]" "text-warning"]
            :data-role (str "portfolio-optimizer-history-assumption-note-" id)}
        (:note card)])
     (card-errors card)
     (when (:summary card)
       [:p {:class ["text-[0.65rem]" "text-trading-text"]
            :data-role (str "portfolio-optimizer-history-assumption-summary-" id)}
        (str (:label card) ": " (:summary card))])]))

(defn history-assumptions-section
  [{:keys [state draft readiness history-load-state]}]
  (let [{:keys [cards applicable?]}
        (optimizer-view-model/history-assumption-cards
         state draft readiness history-load-state
         {:percent-label controls/percent-label})]
    (when applicable?
      (into [:section {:class ["optimizer-setup-panel" "border" "border-base-300"
                               "bg-base-100/90" "p-3" "space-y-3"]
                       :data-role "portfolio-optimizer-history-assumptions-section"}
             [:div
              [:p {:class controls/eyebrow-class} "Assets needing assumptions"]
              [:p {:class ["mt-1" "text-[0.65rem]" "leading-[1.45]" "text-trading-muted"]}
               "Some selected assets don't have enough history for optimizer-safe risk estimates."]]]
            (map assumption-card cards)))))
