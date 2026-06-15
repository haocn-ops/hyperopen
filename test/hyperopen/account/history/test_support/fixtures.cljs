(ns hyperopen.account.history.test-support.fixtures
  "Shared store/state builders for account-history action and effect suites."
  (:require [hyperopen.domain.funding-history :as funding-history]))

(defn info-funding-row
  [time-ms coin usdc signed-size funding-rate]
  (funding-history/normalize-info-funding-row
   {:time time-ms
    :delta {:type "funding"
            :coin coin
            :usdc usdc
            :szi signed-size
            :fundingRate funding-rate}}))

(defn history-filters
  []
  {:coin-set #{}
   :start-time-ms 0
   :end-time-ms 2000000000000})

(defn base-history-state
  ([]
   (base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
  ([address]
   (let [filters (history-filters)]
     {:wallet {:address address}
      :account-context {:spectate-mode {:active? false
                                        :address nil}}
      :account-info {:selected-tab :balances
                     :funding-history {:filters filters
                                       :draft-filters filters
                                       :sort {:column "Time"
                                              :direction :desc}
                                       :filter-open? false
                                       :page-size 50
                                       :page 1
                                       :page-input "1"
                                       :loading? true
                                       :error "stale-funding-error"
                                       :request-id 0}
                     :order-history {:sort {:column "Time"
                                            :direction :desc}
                                     :status-filter :all
                                     :filter-open? false
                                     :page-size 50
                                     :page 1
                                     :page-input "1"
                                     :loading? true
                                     :error "stale-order-error"
                                     :request-id 0}}
      :orders {:fundings-raw []
               :fundings []
               :order-history []}})))
