(ns hyperopen.views.agent-trading-recovery-modal
  (:require [clojure.string :as str]))

(def ^:private idle-description
  "Hyperliquid no longer recognizes this trading setup. Re-enable trading to continue placing orders.")

(def ^:private approving-description
  "Approve the wallet signature request to restore trading.")

(defn- display-error-text
  [error]
  (let [text (some-> error str str/trim not-empty)]
    (when (and text
               (not= "nil" (str/lower-case text)))
      text)))

(defn- close-icon []
  [:svg {:viewBox "0 0 20 20"
         :class ["h-4" "w-4"]
         :fill "none"
         :stroke "currentColor"
         :stroke-width "1.8"
         :stroke-linecap "round"
         :aria-hidden "true"}
   [:path {:d "M5 5 15 15"}]
   [:path {:d "M15 5 5 15"}]])

(defn agent-trading-recovery-modal-view
  [state]
  (let [agent-state (get-in state [:wallet :agent] {})
        open? (true? (:recovery-modal-open? agent-state))
        approving? (= :approving (:status agent-state))
        error-text (display-error-text (:error agent-state))]
    (when open?
      [:div {:class ["fixed"
                     "inset-0"
                     "z-[295]"
                     "flex"
                     "items-center"
                     "justify-center"
                     "bg-[#041016]/80"
                     "p-4"
                     "sm:p-6"]
             :data-role "agent-trading-recovery-modal-overlay"}
       [:div {:class ["w-full"
                      "max-w-4xl"
                      "rounded-[1.75rem]"
                      "border"
                      "border-base-300"
                      "bg-ho-bg-deep"
                      "p-5"
                      "sm:p-8"
                      "shadow-2xl"
                      "space-y-6"
                      "sm:space-y-8"]
              :role "dialog"
              :aria-modal true
              :aria-label "Enable Trading Again"
              :data-role "agent-trading-recovery-modal"}
        [:div {:class ["flex" "items-start" "justify-between" "gap-4"]}
         [:div {:class ["space-y-2"]}
          [:p {:class ["text-sm"
                       "sm:text-xl"
                       "font-semibold"
                       "uppercase"
                       "tracking-[0.12em]"
                       "text-[#8fd8cb]"]}
           "Trading Recovery"]
          [:h2 {:class ["text-3xl"
                        "sm:text-4xl"
                        "font-semibold"
                        "leading-tight"
                        "text-white"]}
           "Enable Trading Again"]]
         [:button {:type "button"
                   :class ["inline-flex"
                           "h-10"
                           "w-10"
                           "shrink-0"
                           "items-center"
                           "justify-center"
                           "rounded-md"
                           "text-gray-400"
                           "hover:bg-base-200"
                           "hover:text-gray-100"
                           "focus:outline-none"
                           "focus:ring-0"
                           "focus:ring-offset-0"]
                   :on {:click [[:actions/close-agent-recovery-modal]]}
                   :aria-label "Close enable trading dialog"
                   :data-role "agent-trading-recovery-modal-close"}
          (close-icon)]]
        [:div {:class ["space-y-3"]}
         [:p {:class ["text-base"
                      "leading-7"
                      "sm:text-2xl"
                      "sm:leading-[1.9]"
                      "text-[#d7e7e8]"]}
          (if approving?
            approving-description
            idle-description)]
         (when error-text
           [:div {:class ["rounded-lg"
                          "sm:rounded-xl"
                          "border"
                          "border-[#23505a]"
                          "bg-[#0b2630]"
                          "px-4"
                          "py-3"
                          "sm:px-6"
                          "sm:py-4"
                          "text-base"
                          "sm:text-xl"
                          "leading-7"
                          "text-[#b7d7dd]"]
                  :data-role "agent-trading-recovery-modal-message"}
            error-text])]
        [:div {:class ["flex" "justify-end" "gap-2"]}
         [:button {:type "button"
                   :class ["rounded-lg"
                           "border"
                           "border-[#2c4b50]"
                           "px-4"
                           "py-2.5"
                           "sm:px-6"
                           "sm:py-3"
                           "text-base"
                           "sm:text-xl"
                           "text-[#b7c8cc]"
                           "hover:border-[#3d666b]"
                           "hover:text-[#e5eef1]"
                           "focus:outline-none"
                           "focus:ring-0"
                           "focus:ring-offset-0"]
                   :on {:click [[:actions/close-agent-recovery-modal]]}
                   :data-role "agent-trading-recovery-modal-dismiss"}
          "Not now"]
         [:button {:type "button"
                   :disabled approving?
                   :class (into ["rounded-lg"
                                 "border"
                                 "px-4"
                                 "py-2.5"
                                 "sm:px-6"
                                 "sm:py-3"
                                 "text-base"
                                 "sm:text-xl"
                                 "font-medium"
                                 "focus:outline-none"
                                 "focus:ring-0"
                                 "focus:ring-offset-0"]
                                (if approving?
                                  ["border-[#2a4b4b]"
                                   "bg-[#08202a]/55"
                                   "text-[#6c8e93]"
                                   "cursor-not-allowed"]
                                  ["border-[#2f625a]"
                                   "bg-ho-accent-soft"
                                   "text-[#daf3ef]"
                                   "hover:border-[#3f7f75]"
                                   "hover:bg-ho-accent-soft-hi"]))
                   :on {:click [[:actions/enable-agent-trading]]}
                   :data-role "agent-trading-recovery-modal-confirm"}
          (if approving? "Awaiting signature..." "Enable Trading")]]]])))
