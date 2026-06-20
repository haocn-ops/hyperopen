(ns hyperopen.views.portfolio.optimize.frontier-chart-toolbar
  (:require [hyperopen.portfolio.optimizer.application.view-model.frontier :as overlay-model]))

(defn- overlay-mode-button
  [current-mode mode]
  (let [selected? (= current-mode mode)]
    [:button {:type "button"
              :class (cond-> ["optimizer-frontier-overlay-mode"
                              "bg-transparent"
                              "text-center"
                              "whitespace-nowrap"
                              "px-3"
                              "py-1.5"
                              "text-[0.62rem]"
                              "font-semibold"
                              "uppercase"
                              "tracking-[0.06em]"
                              "text-trading-muted"
                              "transition-colors"
                              "hover:text-trading-text"]
                       selected? (conj "bg-base-200/60" "text-trading-text"))
              :data-role (str "portfolio-optimizer-frontier-overlay-mode-" (name mode))
              :data-selected (str selected?)
              :aria-pressed (str selected?)
              :on {:click [[:actions/set-portfolio-optimizer-frontier-overlay-mode mode]]}}
     (case mode
       :standalone "Standalone"
       :contribution "Contribution"
       :none "None")]))

(defn- constrain-frontier-control
  [constrain-frontier?]
  [:label {:class ["optimizer-constrain-frontier-control"
                   "flex" "items-center" "gap-2" "border" "border-base-300"
                   "bg-base-100/90" "px-2.5" "py-1.5" "text-[0.68rem]"
                   "font-medium" "text-trading-muted" "transition-colors"
                   "hover:text-trading-text"]
           :data-role "portfolio-optimizer-constrain-frontier-control"}
   [:input {:type "checkbox"
            :class ["optimizer-frontier-checkbox" "h-3.5" "w-3.5" "accent-warning" "outline-none"]
            :data-role "portfolio-optimizer-constrain-frontier-checkbox"
            :checked (true? constrain-frontier?)
            :on {:change [[:actions/set-portfolio-optimizer-constrain-frontier
                           [:event.target/checked]]]}}]
   [:span "Constrain Frontier"]])

(defn toolbar
  [{:keys [overlay-mode constrain-frontier? subtitle point-count]}]
  [:div {:class ["grid" "items-start" "gap-3" "lg:grid-cols-[minmax(0,1fr)_auto]"]
         :data-role "portfolio-optimizer-frontier-toolbar"}
   [:div {:class ["min-w-0"]}
    [:p {:class ["font-mono"
                 "text-[0.62rem]"
                 "uppercase"
                 "tracking-[0.08em]"
                 "text-trading-muted/70"]}
     "Efficient Frontier"]
    [:p {:class ["mt-1" "text-xs" "text-trading-muted"]}
     subtitle]]
   [:div {:class ["optimizer-frontier-controls"
                  "flex" "items-start" "justify-start" "gap-3" "lg:justify-end"]
          :data-role "portfolio-optimizer-frontier-controls"}
    (constrain-frontier-control constrain-frontier?)
    [:div {:class ["min-w-[19.25rem]" "border" "border-base-300" "bg-base-100/90" "p-0.5"]
           :data-role "portfolio-optimizer-frontier-overlay-mode-group"}
     (into [:div {:class ["grid"
                          "grid-cols-[minmax(0,1fr)_minmax(0,1.28fr)_minmax(0,0.78fr)]"
                          "items-stretch"
                          "gap-1"]}]
           (map #(overlay-mode-button overlay-mode %) overlay-model/modes))]
    [:p {:class ["font-mono" "text-[0.62rem]" "text-trading-muted/70"]}
     (str point-count " points")]]])
