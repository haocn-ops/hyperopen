(ns hyperopen.views.referrals.modals
  (:require [hyperopen.referrals.actions :as referrals-actions]))

(defn- button
  [{:keys [label role action primary? disabled?]}]
  [:button {:type "button"
            :class (into ["h-9"
                          "rounded-lg"
                          "border"
                          "px-3"
                          "text-sm"
                          "transition-colors"
                          "disabled:cursor-not-allowed"
                          "disabled:opacity-50"]
                         (if primary?
                           ["border-ho-accent"
                            "bg-ho-accent"
                            "text-[#041914]"
                            "hover:bg-[#6de3d5]"]
                           ["border-[#2f7f73]"
                            "bg-[#061b1f]"
                            "text-ho-accent-bright"
                            "hover:bg-[#0b262c]"]))
            :disabled disabled?
            :data-role role
            :on {:click [action]}}
   label])

(defn- modal-code-field
  [{:keys [id label value field placeholder disabled?]}]
  [:div {:class ["space-y-2"]}
   [:label {:class ["block" "text-xs" "uppercase" "tracking-[0.08em]" "text-[#878c8f]"]
            :for id}
    label]
   [:input {:id id
            :type "text"
            :value value
            :placeholder placeholder
            :disabled disabled?
            :class ["h-10"
                    "w-full"
                    "rounded-lg"
                    "border"
                    "border-[#263338]"
                    "bg-[#071317]"
                    "px-3"
                    "text-sm"
                    "text-ho-text"
                    "outline-none"
                    "placeholder:text-[#596568]"
                    "focus:border-ho-accent"]
            :data-role id
            :on {:input [[:actions/set-referrals-form-field field [:event.target/value]]]}}]])

(defn- modal-shell
  [title body]
  [:div {:class ["fixed"
                 "inset-0"
                 "z-50"
                 "flex"
                 "items-center"
                 "justify-center"
                 "bg-black/60"
                 "px-4"]
         :data-role "referrals-modal-backdrop"}
   [:div {:role "dialog"
          :aria-modal "true"
          :class ["w-full"
                  "max-w-[440px]"
                  "rounded-lg"
                  "border"
                  "border-[#1b3d40]"
                  "bg-[#071317]"
                  "p-4"
                  "text-ho-text"
                  "shadow-2xl"]
          :data-role "referrals-modal"}
    [:div {:class ["mb-4" "flex" "items-start" "justify-between" "gap-3"]}
     [:h2 {:class ["text-lg" "font-normal" "leading-6"]
           :data-role "referrals-modal-title"}
      title]
     [:button {:type "button"
               :class ["h-8"
                       "w-8"
                       "rounded-lg"
                       "border"
                       "border-[#263338]"
                       "bg-ho-bg"
                       "text-sm"
                       "text-[#b7d3d0]"
                       "hover:text-ho-text-hi"]
               :aria-label "Close referrals modal"
               :data-role "referrals-modal-close"
               :on {:click [[:actions/close-referrals-modal]]}}
      "x"]]
    body]])

(defn- modal-error
  [last-error]
  (when last-error
    [:div {:class ["rounded-lg" "border" "border-[#7a2836]" "bg-ho-sell-soft-deep" "px-3" "py-2" "text-sm" "text-[#ff9db2]"]
           :data-role "referrals-modal-error"}
     last-error]))

(defn- disabled-reason
  [mutation-blocked-message]
  (when mutation-blocked-message
    [:div {:class ["text-sm" "text-[#ffe08a]"]
           :data-role "referrals-modal-disabled-reason"}
     mutation-blocked-message]))

(defn- enter-code-modal
  [{:keys [form pending-code mutation-blocked-message connected? submitting? last-error]}]
  (let [code (referrals-actions/normalize-referral-code (:code form))
        join? (seq pending-code)
        disabled? (or (not connected?)
                      (seq mutation-blocked-message)
                      (= :set-referrer submitting?))]
    (modal-shell
     (if join? "Confirm Referral Code" "Enter Referral Code")
     [:div {:class ["space-y-4"]}
      [:p {:class ["text-sm" "leading-5" "text-[#b7d3d0]"]}
       (if join?
         "Confirm the normalized code below before signing. This updates referral settings for your master account only."
         "Enter the referral code exactly as you want it submitted for this master account.")]
      (when (seq code)
        [:div {:class ["rounded-lg" "border" "border-ho-surface" "bg-ho-bg" "p-3"]}
         [:div {:class ["text-xs" "uppercase" "tracking-[0.08em]" "text-[#878c8f]"]}
          "Normalized Code"]
         [:div {:class ["mt-1" "text-xl" "num" "text-ho-text-hi"]
                :data-role "referrals-modal-normalized-code"}
          code]])
      (modal-code-field {:id "referrals-modal-code-input"
                         :label "Referral Code"
                         :value (:code form)
                         :field :code
                         :placeholder "CODE"
                         :disabled? disabled?})
      (modal-error last-error)
      (disabled-reason mutation-blocked-message)
      [:div {:class ["flex" "justify-end" "gap-2"]}
       (button {:label "Cancel"
                :role "referrals-modal-cancel"
                :action [:actions/close-referrals-modal]})
       (button {:label (if (= :set-referrer submitting?) "Entering..." "Enter")
                :role "referrals-modal-submit"
                :primary? true
                :disabled? disabled?
                :action [:actions/submit-set-referrer]})]])))

(defn- create-code-modal
  [{:keys [form stage stage-label mutation-blocked-message connected? submitting? last-error]}]
  (let [disabled? (or (not connected?)
                      (seq mutation-blocked-message)
                      (= :need-to-trade stage)
                      (= :register-referrer submitting?))]
    (modal-shell
     "Create Referral Code"
     [:div {:class ["space-y-4"]}
      [:p {:class ["text-sm" "leading-5" "text-[#b7d3d0]"]}
       (if (= :need-to-trade stage)
         stage-label
         "Create the referral code that new traders can use when joining.")]
      (modal-code-field {:id "referrals-modal-new-code-input"
                         :label "New Code"
                         :value (:new-code form)
                         :field :new-code
                         :placeholder "MYCODE"
                         :disabled? disabled?})
      (modal-error last-error)
      (disabled-reason mutation-blocked-message)
      [:div {:class ["flex" "justify-end" "gap-2"]}
       (button {:label "Cancel"
                :role "referrals-modal-cancel"
                :action [:actions/close-referrals-modal]})
       (button {:label (if (= :register-referrer submitting?) "Creating..." "Create")
                :role "referrals-modal-submit"
                :primary? true
                :disabled? disabled?
                :action [:actions/submit-register-referrer]})]])))

(defn- share-code-modal
  [{:keys [referral-code join-link]}]
  (modal-shell
   "Share Referral Code"
   [:div {:class ["space-y-4"]}
    [:p {:class ["text-sm" "leading-5" "text-[#b7d3d0]"]}
     "Share this Hyperopen join link with traders you refer."]
    [:div {:class ["rounded-lg" "border" "border-ho-surface" "bg-ho-bg" "p-3"]}
     [:div {:class ["text-xs" "uppercase" "tracking-[0.08em]" "text-[#878c8f]"]}
      "Referral Code"]
     [:div {:class ["mt-1" "text-xl" "num" "text-ho-text-hi"]
            :data-role "referrals-modal-own-code"}
      (or referral-code "Unavailable")]]
    [:div {:class ["rounded-lg" "border" "border-ho-surface" "bg-ho-bg" "p-3"]}
     [:div {:class ["text-xs" "uppercase" "tracking-[0.08em]" "text-[#878c8f]"]}
      "Join Link"]
     [:div {:class ["mt-1" "break-all" "text-sm" "text-[#d3f5ef]"]
            :data-role "referrals-modal-join-link"}
      (or join-link "Unavailable")]]
    [:div {:class ["flex" "justify-end"]}
     (button {:label "Done"
              :role "referrals-modal-submit"
              :primary? true
              :action [:actions/close-referrals-modal]})]]))

(defn- claim-rewards-modal
  [{:keys [rewards mutation-blocked-message connected? submitting? last-error]}]
  (let [disabled? (or (not connected?)
                      (seq mutation-blocked-message)
                      (zero? (:claimable rewards))
                      (= :claim-rewards submitting?))]
    (modal-shell
     "Claim Rewards"
     [:div {:class ["space-y-4"]}
      [:p {:class ["text-sm" "leading-5" "text-[#b7d3d0]"]}
       "Claimable rewards are transferred to your spot balance after the exchange accepts the signed action."]
      [:div {:class ["rounded-lg" "border" "border-ho-surface" "bg-ho-bg" "p-3"]}
       [:div {:class ["text-xs" "uppercase" "tracking-[0.08em]" "text-[#878c8f]"]}
        "Claimable Rewards"]
       [:div {:class ["mt-1" "text-2xl" "num" "text-ho-text-hi"]
              :data-role "referrals-modal-claim-total"}
        (:claimable-label rewards)]]
      (when (seq (:rows rewards))
        [:div {:class ["space-y-2"]}
         (for [{:keys [token unclaimed claimed]} (:rows rewards)]
           ^{:key token}
           [:div {:class ["flex" "items-center" "justify-between" "gap-3" "text-sm"]
                  :data-role "referrals-modal-claim-row"}
            [:span {:class ["text-[#b7d3d0]"]} token]
            [:span {:class ["num" "text-ho-text"]}
             "Claimable " unclaimed " | Claimed " claimed]])])
      (modal-error last-error)
      (disabled-reason mutation-blocked-message)
      [:div {:class ["flex" "justify-end" "gap-2"]}
       (button {:label "Cancel"
                :role "referrals-modal-cancel"
                :action [:actions/close-referrals-modal]})
       (button {:label (if (= :claim-rewards submitting?) "Claiming..." "Claim")
                :role "referrals-modal-submit"
                :primary? true
                :disabled? disabled?
                :action [:actions/submit-claim-referral-rewards]})]])))

(defn referrals-modal
  [{:keys [active-modal] :as view-state}]
  (case active-modal
    :enter-code (enter-code-modal view-state)
    :create-code (create-code-modal view-state)
    :share-code (share-code-modal view-state)
    :claim-rewards (claim-rewards-modal view-state)
    nil))
