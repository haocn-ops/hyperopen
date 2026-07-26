(ns hyperopen.portfolio.application.analytics-state
  "Pure portfolio-store adapter for the account analytics service."
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.portfolio.actions :as portfolio-actions]
            [hyperopen.portfolio.metrics.parsing :as parsing]
            [hyperopen.service.portfolio-analytics :as portfolio-analytics]
            [hyperopen.service.tenant-config :as tenant-config]))

(def ^:private portfolio-max-age-ms 8000)

(defn- selected-summary-key
  [scope time-range]
  (if (= :perps scope)
    (keyword (str "perp-" (name time-range)))
    time-range))

(defn- selected-summary
  [state]
  (let [scope (portfolio-actions/normalize-summary-scope
               (get-in state [:portfolio-ui :summary-scope]
                       portfolio-actions/default-summary-scope))
        time-range (portfolio-actions/normalize-summary-time-range
                    (get-in state [:portfolio-ui :summary-time-range]
                            portfolio-actions/default-summary-time-range))]
    {:range time-range
     :summary (get-in state [:portfolio :summary-by-key
                             (selected-summary-key scope time-range)])}))

(defn- summary-address
  [summary]
  (when (map? summary)
    (account-context/normalize-address
     (or (:account summary)
         (:address summary)
         (:user summary)))))

(defn- address-matches?
  [account address]
  (and account
       (= account (account-context/normalize-address address))))

(defn- summary-for-account
  [summary account observed-account? loaded-for-address]
  (let [summary-address* (summary-address summary)]
    (when (and (map? summary)
               (if observed-account?
                 (or (address-matches? account summary-address*)
                     (and (nil? summary-address*)
                          (address-matches? account loaded-for-address)))
                 (or (nil? summary-address*)
                     (address-matches? account summary-address*))))
      summary)))

(defn- matching-user-fees
  [portfolio account]
  (when (address-matches? account (:user-fees-loaded-for-address portfolio))
    (:user-fees portfolio)))

(defn- matching-portfolio-error
  [portfolio account observed-account? summary]
  (let [error (:error portfolio)
        error-for-address (:error-for-address portfolio)]
    (when (and error
               (if observed-account?
                 (or (address-matches? account error-for-address)
                     (address-matches? account (summary-address summary)))
                 (or (nil? error-for-address)
                     (address-matches? account error-for-address))))
      error)))

(defn- matching-user-fees-error?
  [portfolio account]
  (and (:user-fees-error portfolio)
       (address-matches? account (:user-fees-error-for-address portfolio))))

(defn- first-present
  [row keys]
  (some (fn [key]
          (let [value (get row key)]
            (when (some? value)
              value)))
        keys))

(defn- daily-user-vlm-row-volume
  [row]
  (cond
    (map? row)
    (let [exchange (parsing/optional-number
                    (first-present row [:exchange :exchange-volume]))
          user-cross (parsing/optional-number
                      (first-present row [:userCross :user-cross :user_cross]))
          user-add (parsing/optional-number
                    (first-present row [:userAdd :user-add :user_add]))]
      (if (or (some? user-cross) (some? user-add))
        (+ (or user-cross 0) (or user-add 0))
        exchange))

    (and (sequential? row) (>= (count row) 2))
    (parsing/optional-number (second row))

    :else nil))

(defn- user-fee-volume
  [user-fees]
  (let [rows (or (:dailyUserVlm user-fees)
                 (:daily-user-vlm user-fees))]
    (when (and (sequential? rows) (seq rows))
      (let [volumes (mapv daily-user-vlm-row-volume rows)]
        (when (every? some? volumes)
          (reduce + 0 volumes))))))

(defn- round-fee-rate
  [rate]
  (/ (js/Math.round (* rate 1000000000000)) 1000000000000))

(defn- current-fee-rates
  [user-fees]
  (let [referral-discount (or (parsing/optional-number
                               (:activeReferralDiscount user-fees))
                              0)
        taker (parsing/optional-number (:userCrossRate user-fees))
        maker (parsing/optional-number (:userAddRate user-fees))
        discount-factor (- 1 referral-discount)]
    (when (and (some? taker) (some? maker))
      {:taker (round-fee-rate (* taker discount-factor))
       :maker (if (pos? maker)
                (round-fee-rate (* maker discount-factor))
                maker)})))

(defn- fresh-at?
  [loaded-at-ms now-ms]
  (let [loaded-at-ms* (parsing/optional-number loaded-at-ms)
        now-ms* (parsing/optional-number now-ms)]
    (and (some? loaded-at-ms*)
         (some? now-ms*)
         (<= loaded-at-ms* now-ms*)
         (<= (- now-ms* loaded-at-ms*) portfolio-max-age-ms))))

(defn- provider-status
  [portfolio summary portfolio-error]
  (cond
    (and (nil? summary) (:loading? portfolio)) :loading
    portfolio-error :error
    (:loading? portfolio) :loading
    (or summary (some? (:loaded-at-ms portfolio))) :ready
    :else :ready))

(defn build-analytics-state
  "Build the sanitized account analytics model for the active portfolio route.

  `now-ms` is injected by the caller so freshness remains deterministic and this
  application boundary stays free of browser clocks and request side effects."
  [state now-ms]
  (let [account (account-context/effective-account-address state)
        observed-account? (or (account-context/trader-portfolio-route-active? state)
                              (account-context/spectate-mode-active? state))
        {:keys [range summary]} (selected-summary state)
        portfolio (or (:portfolio state) {})
        summary* (summary-for-account summary
                                      account
                                      observed-account?
                                      (:loaded-for-address portfolio))
        user-fees (matching-user-fees portfolio account)
        summary-present? (some? summary*)
        portfolio-error (matching-portfolio-error portfolio account observed-account? summary*)
        user-fees-error? (matching-user-fees-error? portfolio account)
        fee-loaded-at-ms (when user-fees
                           (:user-fees-loaded-at-ms portfolio))
        fee-rates (when (map? user-fees)
                    (current-fee-rates user-fees))
        fee-rates-fresh? (and (not user-fees-error?)
                               (fresh-at? fee-loaded-at-ms now-ms))
        summary-volume (when summary-present?
                         (or (:vlm summary*) (:volume summary*)))
        history (cond-> (or summary* {})
                  true (assoc :status (provider-status portfolio
                                                        summary*
                                                        portfolio-error)
                              :freshness {:fetched-at-ms
                                          (when summary-present?
                                            (:loaded-at-ms portfolio))
                                          :now-ms now-ms
                                          :max-age-ms portfolio-max-age-ms}
                              :error portfolio-error
                              :fee-rates-state {:loaded? (some? user-fees)
                                                :as-of-ms fee-loaded-at-ms
                                                :error? user-fees-error?
                                                :fresh? fee-rates-fresh?})
                  (some? summary-volume) (assoc :vlm summary-volume)
                  (and (nil? summary-volume) user-fees)
                  (assoc :vlm (user-fee-volume user-fees))
                  fee-rates (assoc :fee-rates fee-rates))]
    (portfolio-analytics/build-portfolio-view-model
     (tenant-config/active-tenant-config state)
     history
     {:account account
      :range range})))
