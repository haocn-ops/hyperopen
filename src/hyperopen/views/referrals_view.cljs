(ns hyperopen.views.referrals-view
  (:require [hyperopen.referrals.vm :as referrals-vm]
            [hyperopen.utils.formatting :as formatting]
            [hyperopen.views.referrals.modals :as referrals-modals]
            [hyperopen.wallet.core :as wallet]))

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

(defn- stat-card
  [label value role]
  [:div {:class ["rounded-lg"
                 "border"
                 "border-ho-surface"
                 "bg-ho-bg"
                 "p-4"]
         :data-role role}
   [:div {:class ["text-xs" "uppercase" "tracking-[0.08em]" "text-ho-text-secondary"]}
    label]
   [:div {:class ["mt-2" "text-2xl" "text-ho-text" "num"]}
    value]])

(defn- status-banner
  [{:keys [connected?
           loading?
           error
           last-error
           mutation-blocked-message
           owner
           owner-label]}]
  (cond
    error
    [:div {:class ["rounded-lg" "border" "border-[#7a2836]" "bg-ho-sell-soft-deep" "px-3" "py-2" "text-sm" "text-[#ff9db2]"]
           :data-role "referrals-error"}
     [:div error]
     (when owner
       (button {:label "Retry"
                :role "referrals-retry"
                :action [:actions/load-referrals-route "/referrals"]}))]

    last-error
    [:div {:class ["rounded-lg" "border" "border-[#7a2836]" "bg-ho-sell-soft-deep" "px-3" "py-2" "text-sm" "text-[#ff9db2]"]
           :data-role "referrals-form-error"}
     last-error]

    mutation-blocked-message
    [:div {:class ["rounded-lg" "border" "border-[#725f1d]" "bg-[#241f0b]" "px-3" "py-2" "text-sm" "text-[#ffe08a]"]
           :data-role "referrals-read-only"}
     mutation-blocked-message
     (when owner
       [:span
        " Showing referral state for "
        [:span {:class ["num" "text-ho-text"]} owner-label]])]

    loading?
    [:div {:class ["rounded-lg" "border" "border-[#224247]" "bg-[#0b2025]" "px-3" "py-2" "text-sm" "text-[#b7d3d0]"]
           :data-role "referrals-loading"}
     "Loading referral state..."]

    connected?
    [:div {:class ["rounded-lg" "border" "border-[#1b3d40]" "bg-[#071d22]" "px-3" "py-2" "text-sm" "text-[#b7d3d0]"]
           :data-role "referrals-owner"}
     "Showing master account referral state for "
     [:span {:class ["num" "text-ho-text"]} owner-label]]

    :else
    [:div {:class ["rounded-lg" "border" "border-[#224247]" "bg-[#0b2025]" "px-3" "py-2" "text-sm" "text-[#b7d3d0]"]
           :data-role "referrals-disconnected"}
     "Connect your wallet to load referral status."]))

(defn- hero
  [view-state]
  (let [{:keys [connected?
                mutation-blocked-message
                submitting?
                stage
                stage-label
                referral-code
                join-link
                rewards]} view-state
        mutation-disabled? (or (not connected?)
                               (seq mutation-blocked-message))
        create-disabled? (or mutation-disabled?
                             (= :need-to-trade stage))
        claim-disabled? (or mutation-disabled?
                            (zero? (:claimable rewards))
                            (= :claim-rewards submitting?))]
    [:div {:class ["bg-[#04251f]" "px-4" "py-4" "space-y-4" "rounded-lg"]
           :data-role "referrals-hero"}
     [:div {:class ["flex" "flex-wrap" "items-start" "justify-between" "gap-3"]}
      [:div {:class ["space-y-2" "max-w-[900px]"]}
       [:h1 {:class ["text-[24px]" "md:text-[34px]" "font-normal" "leading-[1.08]" "text-ho-text-hi"]}
        "Referrals"]
       [:p {:class ["text-sm" "leading-5" "font-semibold" "text-[#d3f5ef]" "max-w-[880px]"]}
        "Refer users to earn rewards. "
        [:a {:href "https://hyperliquid.gitbook.io/hyperliquid-docs/referrals"
             :target "_blank"
             :rel "noreferrer"
             :class ["text-ho-accent-bright" "hover:text-[#d2fff7]"]
             :data-role "referrals-learn-more"}
         "Learn more"]]]
      [:div {:class ["flex" "flex-wrap" "items-center" "justify-end" "gap-2"]}
       (button {:label "Enter Code"
                :role "referrals-open-enter-code"
                :disabled? (or mutation-disabled?
                               (= :set-referrer submitting?))
                :action [:actions/open-referrals-modal :enter-code]})
       (if (= :ready stage)
         (button {:label "Share Code"
                  :role "referrals-open-share-code"
                  :disabled? mutation-disabled?
                  :action [:actions/open-referrals-modal :share-code]})
         (button {:label (if (= :register-referrer submitting?)
                           "Creating..."
                           "Create Code")
                  :role "referrals-open-create-code"
                  :disabled? (or create-disabled?
                                 (= :register-referrer submitting?))
                  :action [:actions/open-referrals-modal :create-code]}))
       (button {:label (if (= :claim-rewards submitting?) "Claiming..." "Claim Rewards")
                :role "referrals-open-claim-rewards"
                :primary? true
                :disabled? claim-disabled?
                :action [:actions/open-referrals-modal :claim-rewards]})]]
     [:div {:class ["flex" "flex-wrap" "items-center" "gap-3" "text-sm"]}
      [:div {:class ["rounded-lg" "border" "border-[#1b3d40]" "bg-[#071d22]" "px-3" "py-2" "text-sm" "text-[#d3f5ef]"]
             :data-role "referrals-stage"}
       stage-label]
      (when (= :ready stage)
        [:div {:class ["flex" "min-w-0" "flex-wrap" "items-center" "gap-2" "text-[#b7d3d0]"]}
         [:span "Code"]
         [:span {:class ["num" "text-ho-text"]
                 :data-role "referrals-own-code"}
          (or referral-code "Unavailable")]
         [:span {:class ["hidden" "break-all"]
                 :data-role "referrals-join-link"}
          join-link]])]]))

(defn- tab-button
  [active? label tab]
  [:button {:type "button"
            :class (into ["border-b"
                          "px-0"
                          "mr-4"
                          "text-xs"
                          "leading-[34px]"
                          "transition-colors"]
                         (if active?
                           ["border-ho-accent" "text-ho-text"]
                           ["border-ho-border" "text-ho-text-secondary" "hover:text-[#c5d0ce]"]))
            :data-role (str "referrals-tab-" (name tab))
            :on {:click [[:actions/set-referrals-active-tab tab]]}}
   label])

(defn- format-referral-date
  [value]
  (let [time-ms (js/parseFloat (str value))]
    (if (js/isFinite time-ms)
      (let [date (js/Date. time-ms)]
        (str (inc (.getMonth date))
             "/"
             (.getDate date)
             "/"
             (.getFullYear date)
             " - "
             (formatting/pad2 (.getHours date))
             ":"
             (formatting/pad2 (.getMinutes date))
             ":"
             (formatting/pad2 (.getSeconds date))))
      (str value))))

(defn- format-referral-volume
  [value]
  (or (formatting/format-intl-number value
                                     {:style "currency"
                                      :currency "USD"
                                      :maximumFractionDigits 2})
      (str value)))

(defn- format-referral-money
  [value]
  (or (formatting/format-intl-number value
                                     {:style "currency"
                                      :currency "USD"
                                      :minimumFractionDigits 2
                                      :maximumFractionDigits 2})
      (str value)))

(def referral-grid-cols
  "grid-cols-[minmax(160px,1.25fr)_minmax(190px,0.9fr)_minmax(140px,0.75fr)_minmax(120px,0.7fr)_minmax(130px,0.75fr)]")

(defn- sort-direction-icon
  [direction]
  [:svg {:class (into ["h-3" "w-3" "shrink-0" "opacity-70" "transition-transform"]
                      (if (= :asc direction)
                        ["rotate-180"]
                        ["rotate-0"]))
         :viewBox "0 0 12 12"
         :aria-hidden true}
   [:path {:d "M3 4.5L6 7.5L9 4.5"
           :fill "none"
           :stroke "currentColor"
           :stroke-width "1.5"
           :stroke-linecap "round"
           :stroke-linejoin "round"}]])

(defn- sort-aria
  [column sort-state]
  (if (= column (:column sort-state))
    (if (= :asc (:direction sort-state)) "ascending" "descending")
    "none"))

(defn- sortable-referral-header
  [label column sort-state align]
  (let [active? (= column (:column sort-state))
        direction (:direction sort-state)]
    [:button {:type "button"
              :class (into ["inline-flex"
                            "items-center"
                            "gap-1"
                            "font-normal"
                            "text-ho-text-secondary"
                            "transition-colors"
                            "hover:text-ho-text"
                            "focus:outline-none"
                            "focus:ring-0"
                            "focus:ring-offset-0"]
                           (case align
                             :right ["justify-end" "text-right"]
                             ["justify-start" "text-left"]))
              :data-role (str "referrals-sort-" (name column))
              :aria-sort (sort-aria column sort-state)
              :on {:click [[:actions/set-referrals-sort column]]}}
     [:span label]
     (when active?
       (sort-direction-icon direction))]))

(defn- referral-row
  [idx row]
  (let [address (or (:user row) (get row "user") (:address row) (get row "address"))
        date-joined (or (:time row) (:timestamp row) (:joinedAt row)
                        (:timeJoined row) (:date-joined row)
                        (get row "time")
                        (get row "timestamp") (get row "joinedAt")
                        (get row "timeJoined")
                        (get row "date-joined") "-")
        volume (or (:cumVlm row) (:cum-vlm row) (get row "cumVlm") (get row "cum-vlm") "0")
        fees (or (:cumRewardedFeesSinceReferred row)
                 (:cum-rewarded-fees-since-referred row)
                 (:cumFees row)
                 (:cum-fees row)
                 (get row "cumRewardedFeesSinceReferred")
                 (get row "cum-rewarded-fees-since-referred")
                 (get row "cumFees")
                 (get row "cum-fees")
                 "0")
        rewards (or (:cumFeesRewardedToReferrer row)
                    (:cum-fees-rewarded-to-referrer row)
                    (:cumReward row)
                    (:cum-reward row)
                    (get row "cumFeesRewardedToReferrer")
                    (get row "cum-fees-rewarded-to-referrer")
                    (get row "cumReward")
                    (get row "cum-reward")
                    "0")]
    [:div {:class ["grid" "min-w-[860px]" referral-grid-cols "gap-3" "px-3" "py-2" "text-sm"]
           :data-role "referrals-row"}
     [:span {:class ["truncate" "num"]} (or (wallet/short-addr address) address (str "Trader " (inc idx)))]
     [:span {:class ["num" "text-right"]} (format-referral-date date-joined)]
     [:span {:class ["num" "text-right"]} (format-referral-volume volume)]
     [:span {:class ["num" "text-right"]} (format-referral-money fees)]
     [:span {:class ["num" "text-right"]} (format-referral-money rewards)]]))

(defn- legacy-row
  [idx row]
  (let [time (or (:time row) (:timestamp row) (get row "time") (get row "timestamp"))
        user-volume (or (:userVlm row) (:user-vlm row) (get row "userVlm") (get row "user-vlm") "0")
        referral-volume (or (:referralVlm row) (:referral-vlm row)
                            (get row "referralVlm") (get row "referral-vlm") "0")
        rewards (or (:totalRewards row) (:total-rewards row) (:amount row)
                    (get row "totalRewards") (get row "total-rewards")
                    (get row "amount") "0")]
    [:div {:class ["grid" "min-w-[680px]" "grid-cols-[minmax(0,1fr)_140px_160px_160px]" "gap-3" "px-3" "py-2" "text-sm"]
           :data-role "referrals-legacy-row"}
     [:span {:class ["truncate" "num"]} (str (or time (str "Reward " (inc idx))))]
     [:span {:class ["num" "text-right"]} (str user-volume)]
     [:span {:class ["num" "text-right"]} (str referral-volume)]
     [:span {:class ["num" "text-right"]} (str rewards)]]))

(defn- table-panel
  [{:keys [active-tab referral-rows legacy-rows referrals-sort]}]
  [:div {:class ["rounded-lg" "border" "border-ho-surface" "bg-ho-bg" "overflow-hidden"]
         :data-role "referrals-table-panel"}
   [:div {:class ["px-3" "pt-2"]}
    (tab-button (= :referrals active-tab) "Referrals" :referrals)
    (tab-button (= :legacy-reward-history active-tab) "Legacy Reward History" :legacy-reward-history)]
   (if (= :legacy-reward-history active-tab)
     (if (seq legacy-rows)
       [:div {:class ["overflow-x-auto"]
              :data-role "referrals-legacy-table"}
        [:div {:class ["grid" "min-w-[680px]" "grid-cols-[minmax(0,1fr)_140px_160px_160px]" "gap-3" "px-3" "py-2" "text-xs" "uppercase" "tracking-[0.08em]" "text-ho-text-secondary"]}
         [:span "Date"]
         [:span {:class ["text-right"]} "Your Volume"]
         [:span {:class ["text-right"]} "Your Referral Volume"]
         [:span {:class ["text-right"]} "Total Rewards Earned"]]
        (map-indexed legacy-row legacy-rows)]
       [:div {:class ["px-3" "py-8" "text-center" "text-sm" "text-ho-text-secondary"]
              :data-role "referrals-legacy-empty"}
        "No rewards earned"])
     (if (seq referral-rows)
       [:div {:class ["overflow-x-auto"]
              :data-role "referrals-table"}
        [:div {:class ["grid" "min-w-[860px]" referral-grid-cols "gap-3" "px-3" "py-2" "text-xs" "uppercase" "tracking-[0.08em]" "text-ho-text-secondary"]
               :data-role "referrals-table-header"}
         (sortable-referral-header "Address" :address referrals-sort :left)
         (sortable-referral-header "Date Joined" :date-joined referrals-sort :right)
         (sortable-referral-header "Total Volume" :total-volume referrals-sort :right)
         (sortable-referral-header "Fees Paid" :fees-paid referrals-sort :right)
         (sortable-referral-header "Your Rewards" :your-rewards referrals-sort :right)]
        (map-indexed referral-row referral-rows)]
       [:div {:class ["px-3" "py-8" "text-center" "text-sm" "text-ho-text-secondary"]
              :data-role "referrals-empty"}
        "No referrals yet"]))])

(defn referrals-view
  [state]
  (let [view-state (referrals-vm/referrals-vm state)
        {:keys [traders-referred-label
                rewards]} view-state]
    [:div {:class ["flex"
                   "flex-1"
                   "min-h-0"
                   "w-full"
                   "overflow-hidden"
                   "flex-col"
                   "app-shell-gutter"
                   "pt-3"]
           :data-parity-id "referrals-root"}
     [:div {:class ["w-full"
                    "h-full"
                    "min-h-0"
                    "scrollbar-hide"
                    "overflow-y-auto"
                    "flex"
                    "flex-col"
                    "gap-3"
                    "pb-16"]}
      (hero view-state)
      (status-banner view-state)
      [:div {:class ["grid" "gap-2" "md:grid-cols-3"]}
       (stat-card "Traders Referred" traders-referred-label "referrals-stat-traders")
       (stat-card "Rewards Earned" (:earned-label rewards) "referrals-stat-rewards")
       (stat-card "Claimable Rewards" (:claimable-label rewards) "referrals-stat-claimable")]
      (table-panel view-state)]
     (referrals-modals/referrals-modal view-state)]))

(defn ^:export route-view
  [state]
  (referrals-view state))

(goog/exportSymbol "hyperopen.views.referrals_view.route_view" route-view)
