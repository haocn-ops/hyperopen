(ns hyperopen.referrals.vm
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.utils.formatting :as formatting]
            [hyperopen.wallet.core :as wallet]))

(defn- field
  [m k]
  (case k
    :cum-vlm (or (:cumVlm m) (:cum-vlm m) (get m "cumVlm") (get m "cum-vlm"))
    :referrer-state (or (:referrerState m) (:referrer-state m) (get m "referrerState") (get m "referrer-state"))
    :reward-history (or (:rewardHistory m) (:reward-history m) (get m "rewardHistory") (get m "reward-history"))
    :token-to-state (or (:tokenToState m) (:token-to-state m) (get m "tokenToState") (get m "token-to-state"))
    :stage (or (:stage m) (get m "stage"))
    :data (or (:data m) (get m "data"))
    :code (or (:code m) (get m "code"))
    :n-referrals (or (:nReferrals m) (:n-referrals m) (get m "nReferrals") (get m "n-referrals"))
    :referral-states (or (:referralStates m) (:referral-states m) (get m "referralStates") (get m "referral-states"))
    :unclaimed-rewards (or (:unclaimedRewards m) (:unclaimed-rewards m) (get m "unclaimedRewards") (get m "unclaimed-rewards"))
    :claimed-rewards (or (:claimedRewards m) (:claimed-rewards m) (get m "claimedRewards") (get m "claimed-rewards"))
    :builder-rewards (or (:builderRewards m) (:builder-rewards m) (get m "builderRewards") (get m "builder-rewards"))
    :user (or (:user m) (:address m) (get m "user") (get m "address"))
    :time-joined (or (:timeJoined m) (:time-joined m) (:joinedAt m) (:joined-at m)
                     (:time m) (:timestamp m)
                     (get m "timeJoined") (get m "time-joined")
                     (get m "joinedAt") (get m "joined-at")
                     (get m "time") (get m "timestamp"))
    :rewarded-fees (or (:cumRewardedFeesSinceReferred m)
                       (:cum-rewarded-fees-since-referred m)
                       (:cumFees m)
                       (:cum-fees m)
                       (get m "cumRewardedFeesSinceReferred")
                       (get m "cum-rewarded-fees-since-referred")
                       (get m "cumFees")
                       (get m "cum-fees"))
    :referrer-rewards (or (:cumFeesRewardedToReferrer m)
                          (:cum-fees-rewarded-to-referrer m)
                          (:cumReward m)
                          (:cum-reward m)
                          (get m "cumFeesRewardedToReferrer")
                          (get m "cum-fees-rewarded-to-referrer")
                          (get m "cumReward")
                          (get m "cum-reward"))
    :time (or (:time m) (:timestamp m) (get m "time") (get m "timestamp"))
    :amount (or (:amount m) (get m "amount"))
    :token (or (:token m) (get m "token"))
    :description (or (:description m) (:type m) (get m "description") (get m "type"))
    nil))

(defn- numberish
  [value]
  (cond
    (number? value) (when (js/isFinite value) value)
    (string? value) (let [parsed (js/parseFloat value)]
                      (when (js/isFinite parsed)
                        parsed))
    :else nil))

(defn- format-number
  [value]
  (or (formatting/format-intl-number value {:maximumFractionDigits 2})
      "0"))

(defn- format-usd
  [value]
  (or (formatting/format-intl-number value
                                     {:style "currency"
                                      :currency "USD"
                                      :maximumFractionDigits 2})
      "$0"))

(defn- normalize-stage
  [value]
  (let [stage (some-> value str str/trim str/lower-case)]
    (case stage
      "ready" :ready
      "needtocreatecode" :need-to-create-code
      "need-to-create-code" :need-to-create-code
      "need_to_create_code" :need-to-create-code
      "needtotrade" :need-to-trade
      "need-to-trade" :need-to-trade
      "need_to_trade" :need-to-trade
      :unknown)))

(defn- token-label
  [token]
  (let [token* (some-> token str)]
    (case token*
      nil "USDC"
      "0" "USDC"
      token*)))

(defn- token-state-rows
  [payload]
  (let [token-to-state (field payload :token-to-state)]
    (cond
      (map? token-to-state)
      (mapv (fn [[token state]]
              {:token (token-label token)
               :unclaimed (or (numberish (field state :unclaimed-rewards)) 0)
               :claimed (or (numberish (field state :claimed-rewards)) 0)
               :builder (or (numberish (field state :builder-rewards)) 0)})
            token-to-state)

      (sequential? token-to-state)
      (mapv (fn [entry]
              (let [[token state] (if (and (sequential? entry)
                                           (map? (second entry)))
                                    [(first entry) (second entry)]
                                    [(field entry :token) entry])]
                {:token (token-label token)
                 :unclaimed (or (numberish (field state :unclaimed-rewards)) 0)
                 :claimed (or (numberish (field state :claimed-rewards)) 0)
                 :builder (or (numberish (field state :builder-rewards)) 0)}))
            token-to-state)

      :else
      (if (or (some? (field payload :unclaimed-rewards))
              (some? (field payload :claimed-rewards))
              (some? (field payload :builder-rewards)))
        [{:token "USDC"
          :unclaimed (or (numberish (field payload :unclaimed-rewards)) 0)
          :claimed (or (numberish (field payload :claimed-rewards)) 0)
          :builder (or (numberish (field payload :builder-rewards)) 0)}]
        []))))

(defn- sum-rewards
  [rows k]
  (reduce + 0 (map #(or (numberish (get % k)) 0) rows)))

(defn- reward-summary
  [payload referrer-state referrer-data]
  (let [rows (or (some (fn [source]
                         (let [rows* (token-state-rows source)]
                           (when (seq rows*) rows*)))
                       [referrer-data referrer-state payload])
                 [])
        unclaimed (sum-rewards rows :unclaimed)
        claimed (sum-rewards rows :claimed)]
    {:rows rows
     :claimable unclaimed
     :earned (+ claimed unclaimed)
     :claimable-label (format-usd unclaimed)
     :earned-label (format-usd (+ claimed unclaimed))}))

(defn- referral-rows
  [referrer-state]
  (let [data (field referrer-state :data)]
    (vec (or (field data :referral-states) []))))

(defn- legacy-rows
  [payload]
  (vec (or (field payload :reward-history) [])))

(def default-referrals-sort
  {:column :total-volume
   :direction :desc})

(defn- normalize-sort-column
  [column]
  (let [column* (cond
                  (keyword? column) column
                  (string? column) (keyword column)
                  :else nil)]
    (if (contains? #{:address
                     :date-joined
                     :total-volume
                     :fees-paid
                     :your-rewards}
                   column*)
      column*
      (:column default-referrals-sort))))

(defn- normalize-sort-direction
  [direction]
  (case direction
    :asc :asc
    "asc" :asc
    :desc :desc
    "desc" :desc
    (:direction default-referrals-sort)))

(defn- referral-row-sort-value
  [column row]
  (case column
    :address (str/lower-case (str (or (field row :user) "")))
    :date-joined (or (numberish (field row :time-joined)) 0)
    :total-volume (or (numberish (field row :cum-vlm)) 0)
    :fees-paid (or (numberish (field row :rewarded-fees)) 0)
    :your-rewards (or (numberish (field row :referrer-rewards)) 0)
    0))

(defn- compare-referral-rows
  [column direction a b]
  (let [result (compare (referral-row-sort-value column a)
                        (referral-row-sort-value column b))]
    (if (= :desc direction)
      (- result)
      result)))

(defn- sort-state
  [state]
  (let [raw-sort (get-in state [:referrals-ui :sort])]
    {:column (normalize-sort-column (:column raw-sort))
     :direction (normalize-sort-direction (:direction raw-sort))}))

(defn- sorted-referral-rows
  [rows sort-state*]
  (vec (sort #(compare-referral-rows (:column sort-state*) (:direction sort-state*) %1 %2)
             rows)))

(defn referrals-vm
  [state]
  (let [payload (or (get-in state [:referrals :raw]) {})
        referrer-state (or (field payload :referrer-state) {})
        referrer-data (or (field referrer-state :data) {})
        rewards (reward-summary payload referrer-state referrer-data)
        sort (sort-state state)
        owner (account-context/effective-account-address state)
        blocked-message (or (account-context/mutations-blocked-message state)
                            (when (account-context/selected-subaccount-owned-by-owner? state)
                              "Switch to the master account before changing referral settings."))
        stage (normalize-stage (field referrer-state :stage))
        code (field referrer-data :code)]
    {:connected? (some? owner)
     :owner owner
     :owner-label (or (wallet/short-addr owner) "Not connected")
     :loading? (true? (get-in state [:referrals :loading?]))
     :error (get-in state [:referrals :error])
     :last-error (get-in state [:referrals-ui :last-error])
     :submitting? (get-in state [:referrals-ui :submitting?])
     :active-modal (get-in state [:referrals-ui :active-modal])
     :active-tab (or (get-in state [:referrals-ui :active-tab]) :referrals)
     :form {:code (or (get-in state [:referrals-ui :form :code]) "")
            :new-code (or (get-in state [:referrals-ui :form :new-code]) "")}
     :pending-code (get-in state [:referrals-ui :pending-code])
     :mutation-blocked-message blocked-message
     :stage stage
     :stage-label (case stage
                    :ready "Ready"
                    :need-to-create-code "Eligible to create code"
                    :need-to-trade "Trade more volume to create a code"
                    "Unavailable")
     :referral-code code
     :join-link (when (seq code)
                  (str "/join/" code))
     :traders-referred (or (field referrer-data :n-referrals) 0)
     :traders-referred-label (format-number (or (field referrer-data :n-referrals) 0))
     :cum-vlm (or (numberish (field payload :cum-vlm)) 0)
     :cum-vlm-label (format-usd (or (numberish (field payload :cum-vlm)) 0))
     :rewards rewards
     :referrals-sort sort
     :referral-rows (sorted-referral-rows (referral-rows referrer-state) sort)
     :legacy-rows (legacy-rows payload)}))
