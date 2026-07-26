(ns hyperopen.referrals.actions
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.router :as router]))

(def canonical-route
  "/referrals")

(def selected-subaccount-referral-blocked-message
  "Switch to the master account before changing referral settings.")

(def ^:private join-route-prefix
  "/join/")

(def ^:private invalid-referral-code-message
  "Enter a valid referral code.")

(def ^:private missing-wallet-message
  "Connect your wallet before entering a referral code.")

(def ^:private valid-form-fields
  #{:code
    :new-code})

(def ^:private valid-modals
  #{:enter-code
    :create-code
    :share-code
    :claim-rewards})

(def ^:private valid-tabs
  #{:referrals
    :legacy-reward-history})

(def valid-sort-columns
  #{:address
    :date-joined
    :total-volume
    :fees-paid
    :your-rewards})

(def default-sort
  {:column :total-volume
   :direction :desc})

(defn normalize-referral-code
  [value]
  (-> (or value "")
      str
      str/trim
      str/upper-case))

(defn valid-referral-code?
  [value]
  (let [code (normalize-referral-code value)]
    (boolean
     (and (seq code)
          (<= (count code) 20)
          (re-matches #"[A-Z0-9]+" code)))))

(defn referrals-route?
  [path]
  (let [path* (router/normalize-path path)]
    (or (= canonical-route path*)
        (str/starts-with? path* join-route-prefix))))

(defn- decode-join-code
  [raw-code]
  (try
    (js/decodeURIComponent raw-code)
    (catch :default _
      raw-code)))

(defn join-code-from-path
  [path]
  (let [path* (router/normalize-path path)]
    (when (str/starts-with? path* join-route-prefix)
      (let [raw-code (subs path* (count join-route-prefix))
            code (normalize-referral-code (decode-join-code raw-code))]
        (when (seq code)
          code)))))

(defn load-referrals-route
  [state path]
  (if-not (referrals-route? path)
    []
    (let [join-code (join-code-from-path path)
          referral-address (account-context/effective-account-address state)]
      (cond-> [[:effects/save-many
                [[[:referrals-ui :pending-code] join-code]
                 [[:referrals-ui :active-modal] (when join-code :enter-code)]
                 [[:referrals-ui :form :code]
                  (or join-code
                      (get-in state [:referrals-ui :form :code] ""))]
                 [[:referrals-ui :last-error] nil]
                 [[:referrals-ui :submitting?] false]]]]
        referral-address (conj [:effects/api-fetch-referral referral-address])))))

(defn set-form-field
  [_state field value]
  (when (contains? valid-form-fields field)
    [[:effects/save [:referrals-ui :form field] value]]))

(defn set-active-tab
  [_state tab]
  (let [tab* (if (contains? valid-tabs tab)
               tab
               :referrals)]
    [[:effects/save [:referrals-ui :active-tab] tab*]]))

(defn- normalize-sort-column
  [column]
  (let [column* (cond
                  (keyword? column) column
                  (string? column) (keyword column)
                  :else nil)]
    (if (contains? valid-sort-columns column*)
      column*
      (:column default-sort))))

(defn- normalize-sort-direction
  [direction]
  (case direction
    :asc :asc
    "asc" :asc
    :desc :desc
    "desc" :desc
    (:direction default-sort)))

(defn set-referrals-sort
  [state column]
  (let [column* (normalize-sort-column column)
        current-sort (get-in state [:referrals-ui :sort])
        current-column (normalize-sort-column (:column current-sort))
        current-direction (normalize-sort-direction (:direction current-sort))
        next-direction (if (= column* current-column)
                         (if (= :asc current-direction) :desc :asc)
                         (if (= :address column*) :asc :desc))]
    [[:effects/save [:referrals-ui :sort] {:column column*
                                           :direction next-direction}]]))

(defn open-referrals-modal
  [_state modal]
  (let [modal* (if (contains? valid-modals modal)
                 modal
                 :create-code)]
    [[:effects/save-many [[[:referrals-ui :active-modal] modal*]
                          [[:referrals-ui :last-error] nil]]]]))

(defn close-referrals-modal
  [_state]
  [[:effects/save-many [[[:referrals-ui :active-modal] nil]
                        [[:referrals-ui :last-error] nil]]]])

(defn- referral-mutation-blocked-message
  [state]
  (or (account-context/mutations-blocked-message state)
      (when (account-context/selected-subaccount-owned-by-owner? state)
        selected-subaccount-referral-blocked-message)))

(defn- save-error-effect
  [message]
  [[:effects/save [:referrals-ui :last-error] message]])

(defn- submit-code-mutation
  [state {:keys [form-path submitting-kind effect-key]}]
  (if-let [blocked-message (referral-mutation-blocked-message state)]
    (save-error-effect blocked-message)
    (if-let [owner (account-context/owner-address state)]
      (let [code (normalize-referral-code (get-in state form-path))]
        (if (valid-referral-code? code)
          [[:effects/save-many [[[:referrals-ui :last-error] nil]
                                [[:referrals-ui :submitting?] submitting-kind]]]
           [effect-key {:owner owner
                        :code code}]]
          (save-error-effect invalid-referral-code-message)))
      (save-error-effect missing-wallet-message))))

(defn submit-set-referrer
  [state]
  (submit-code-mutation state
                        {:form-path [:referrals-ui :form :code]
                         :submitting-kind :set-referrer
                         :effect-key :effects/api-set-referrer}))

(defn submit-register-referrer
  [state]
  (submit-code-mutation state
                        {:form-path [:referrals-ui :form :new-code]
                         :submitting-kind :register-referrer
                         :effect-key :effects/api-register-referrer}))

(defn submit-claim-rewards
  [state]
  (if-let [blocked-message (referral-mutation-blocked-message state)]
    (save-error-effect blocked-message)
    (if-let [owner (account-context/owner-address state)]
      [[:effects/save-many [[[:referrals-ui :last-error] nil]
                            [[:referrals-ui :submitting?] :claim-rewards]]]
       [:effects/api-claim-referral-rewards {:owner owner}]]
      (save-error-effect missing-wallet-message))))
