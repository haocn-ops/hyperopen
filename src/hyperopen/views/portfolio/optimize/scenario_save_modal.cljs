(ns hyperopen.views.portfolio.optimize.scenario-save-modal
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn- blank-name?
  [value]
  (not (seq (str/trim (or value "")))))

(defn scenario-save-modal
  [state]
  (let [modal (get-in state contracts/scenario-save-modal-path)
        save-state (get-in state contracts/scenario-save-state-path)
        open? (true? (:open? modal))
        name-value (or (:name modal) "")
        saving? (= :saving (:status save-state))
        error (or (:error modal)
                  (get-in save-state [:error :message]))]
    (when open?
      [:div {:class ["fixed"
                     "inset-0"
                     "z-50"
                     "flex"
                     "items-center"
                     "justify-center"
                     "bg-black/60"
                     "p-6"]
             :data-role "portfolio-optimizer-scenario-save-modal"}
       [:section {:class ["w-full"
                          "max-w-md"
                          "rounded-lg"
                          "border"
                          "border-base-300"
                          "bg-base-100"
                          "p-5"
                          "shadow-2xl"]}
        [:div {:class ["flex" "items-start" "justify-between" "gap-4"]}
         [:div
          [:p {:class ["text-[0.65rem]"
                       "font-semibold"
                       "uppercase"
                       "tracking-[0.24em]"
                       "text-trading-muted"]}
           "Scenario"]
          [:h2 {:class ["mt-2" "text-lg" "font-semibold" "tracking-tight"]}
           "Save scenario as"]]
         [:button {:type "button"
                   :class ["rounded-lg"
                           "border"
                           "border-base-300"
                           "px-3"
                           "py-2"
                           "text-xs"
                           "font-semibold"
                           "uppercase"
                           "tracking-[0.16em]"
                           "text-trading-muted"]
                   :data-role "portfolio-optimizer-scenario-save-cancel"
                   :on {:click [[:actions/close-portfolio-optimizer-scenario-save-modal]]}}
          "Cancel"]]
        [:label {:class ["mt-4"
                         "block"
                         "text-[0.65rem]"
                         "font-semibold"
                         "uppercase"
                         "tracking-[0.18em]"
                         "text-trading-muted"]
                 :for "portfolio-optimizer-scenario-save-name"}
         "Name"]
        [:input {:id "portfolio-optimizer-scenario-save-name"
                 :type "text"
                 :class ["mt-2"
                         "w-full"
                         "rounded-md"
                         "border"
                         "border-base-300"
                         "bg-base-200/40"
                         "px-3"
                         "py-2"
                         "text-sm"
                         "font-medium"
                         "text-trading-text"
                         "outline-none"
                         "focus:border-primary"]
                 :data-role "portfolio-optimizer-scenario-save-name"
                 :value name-value
                 :aria-invalid (when (and error (blank-name? name-value)) "true")
                 :on {:input [[:actions/set-portfolio-optimizer-scenario-save-name
                               [:event.target/value]]]}}]
        (when error
          [:p {:class ["mt-3"
                       "rounded-md"
                       "border"
                       "border-error/40"
                       "bg-error/10"
                       "px-3"
                       "py-2"
                       "text-sm"
                       "font-semibold"
                       "text-error"]
               :data-role "portfolio-optimizer-scenario-save-error"}
           error])
        [:div {:class ["mt-5" "flex" "items-center" "justify-end" "gap-3"]}
         [:button {:type "button"
                   :class ["rounded-lg"
                           "border"
                           "border-base-300"
                           "px-4"
                           "py-2"
                           "text-sm"
                           "font-semibold"
                           "text-trading-muted"]
                   :on {:click [[:actions/close-portfolio-optimizer-scenario-save-modal]]}}
          "Cancel"]
         [:button {:type "button"
                   :class ["rounded-lg"
                           "border"
                           "border-primary/50"
                           "bg-primary/10"
                           "px-4"
                           "py-2"
                           "text-sm"
                           "font-semibold"
                           "text-primary"
                           "disabled:cursor-not-allowed"
                           "disabled:border-base-300"
                           "disabled:bg-base-200/40"
                           "disabled:text-trading-muted"]
                   :data-role "portfolio-optimizer-scenario-save-confirm"
                   :disabled saving?
                   :on {:click [[:actions/confirm-portfolio-optimizer-scenario-save]]}}
          (if saving? "Saving..." "Save as")]]]])))
