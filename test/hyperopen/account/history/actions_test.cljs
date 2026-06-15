(ns hyperopen.account.history.actions-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.account.history.actions :as history-actions]
            [hyperopen.account.history.test-support.fixtures :as fixtures]
            [hyperopen.domain.funding-history :as funding-history]
            [hyperopen.platform :as platform]))

(deftest normalize-order-history-page-covers-single-arity-and-clamping-test
  (is (= 1 (history-actions/normalize-order-history-page nil)))
  (is (= 1 (history-actions/normalize-order-history-page "0")))
  (is (= 7 (history-actions/normalize-order-history-page "7")))
  (is (= 3 (history-actions/normalize-order-history-page "7" 3)))
  (is (= 1 (history-actions/normalize-order-history-page "9" 0)))
  (is (= 1 (history-actions/normalize-order-history-page "abc" "nope"))))

(deftest normalize-order-history-page-supports-localized-integer-inputs-test
  (is (= 1234
         (history-actions/normalize-order-history-page (str "1\u202F234") nil "fr-FR")))
  (is (= 100
         (history-actions/normalize-order-history-page-size "100,0" "fr-FR")))
  (is (= 1
         (history-actions/normalize-order-history-page "abc" 2000 "fr-FR"))))

(deftest apply-order-history-page-input-parses-localized-page-input-test
  (let [state {:ui {:locale "fr-FR"}
               :account-info {:order-history {:page-input (str "1\u202F234")}}}]
    (is (= [[:effects/save-many [[[:account-info :order-history :page] 1234]
                                 [[:account-info :order-history :page-input] "1234"]]]]
           (history-actions/apply-order-history-page-input state 2000)))))

(deftest restore-open-orders-sort-settings-covers-valid-and-fallback-values-test
  (with-redefs [platform/local-storage-get (fn [key]
                                             (case key
                                               "open-orders-sort-by" "Price"
                                               "open-orders-sort-direction" "asc"
                                               nil))]
    (let [store (atom {:account-info {}})]
      (history-actions/restore-open-orders-sort-settings! store)
      (is (= {:column "Price" :direction :asc}
             (get-in @store [:account-info :open-orders-sort])))))
  (with-redefs [platform/local-storage-get (fn [key]
                                             (case key
                                               "open-orders-sort-by" "Unsupported"
                                               "open-orders-sort-direction" "sideways"
                                               nil))]
    (let [store (atom {:account-info {}})]
      (history-actions/restore-open-orders-sort-settings! store)
      (is (= {:column "Time" :direction :desc}
             (get-in @store [:account-info :open-orders-sort]))))))

(deftest set-funding-history-filters-normalizes-paths-and-datetime-values-test
  (let [datetime-text "2026-01-02T03:04:05"
        datetime-ms (js/Math.floor (.parse js/Date datetime-text))]
    (is (= [[:effects/save [:account-info :funding-history :draft-filters :start-time-ms]
             datetime-ms]]
           (history-actions/set-funding-history-filters {} [:draft-filters :start-time-ms] datetime-text)))
    (is (= [[:effects/save [:account-info :funding-history :filters :end-time-ms] nil]]
           (history-actions/set-funding-history-filters {} [:filters :end-time-ms] "  ")))
    (is (= [[:effects/save [:account-info :funding-history :filter-open?] true]]
           (history-actions/set-funding-history-filters {} :filter-open? true)))
    (is (= [[:effects/save [:account-info :funding-history :coin-search] "42"]]
           (history-actions/set-funding-history-filters {} :coin-search 42)))
    (is (= [[:effects/save [:account-info :funding-history :coin-suggestions-open?] true]]
           (history-actions/set-funding-history-filters {} :coin-suggestions-open? "yes")))))

(deftest toggle-funding-history-filter-open-covers-open-and-closed-branches-test
  (with-redefs [platform/now-ms (constantly 2000)]
    (let [filters {:coin-set #{"" "BTC"}
                   :start-time-ms 10
                   :end-time-ms 20}
          normalized-filters (funding-history/normalize-funding-history-filters filters 2000)
          draft-filters {:coin-set #{"ETH" ""}
                         :start-time-ms 30
                         :end-time-ms 40}
          normalized-draft (funding-history/normalize-funding-history-filters draft-filters 2000)]
      (is (= [[:effects/save-many [[[:account-info :funding-history :filter-open?] true]
                                   [[:account-info :funding-history :draft-filters] normalized-filters]
                                   [[:account-info :funding-history :coin-search] ""]
                                   [[:account-info :funding-history :coin-suggestions-open?] false]]]]
             (history-actions/toggle-funding-history-filter-open
              {:account-info {:funding-history {:filter-open? false
                                                :filters filters}}})))
      (is (= [[:effects/save-many [[[:account-info :funding-history :filter-open?] false]
                                   [[:account-info :funding-history :draft-filters] normalized-draft]
                                   [[:account-info :funding-history :coin-search] ""]
                                   [[:account-info :funding-history :coin-suggestions-open?] false]]]]
             (history-actions/toggle-funding-history-filter-open
              {:account-info {:funding-history {:filter-open? true
                                                :filters filters
                                                :draft-filters draft-filters}}})))
      (is (= [[:effects/save-many [[[:account-info :funding-history :filter-open?] false]
                                   [[:account-info :funding-history :draft-filters] normalized-filters]
                                   [[:account-info :funding-history :coin-search] ""]
                                   [[:account-info :funding-history :coin-suggestions-open?] false]]]]
             (history-actions/toggle-funding-history-filter-open
              {:account-info {:funding-history {:filter-open? true
                                                :filters filters}}}))))))

(deftest toggle-and-reset-funding-history-filter-draft-covers-coin-and-reset-branches-test
  (let [draft-filters {:coin-set #{"BTC"}
                       :start-time-ms 10
                       :end-time-ms 20}
        state {:account-info {:funding-history {:draft-filters draft-filters}}}]
    (is (= [[:effects/save [:account-info :funding-history :draft-filters]
             {:coin-set #{"BTC" "ETH"}
              :start-time-ms 10
              :end-time-ms 20}]]
           (history-actions/toggle-funding-history-filter-coin state "ETH")))
    (is (= [[:effects/save [:account-info :funding-history :draft-filters]
             {:coin-set #{}
              :start-time-ms 10
              :end-time-ms 20}]]
           (history-actions/toggle-funding-history-filter-coin state "BTC")))
    (is (= [[:effects/save [:account-info :funding-history :draft-filters]
             {:coin-set #{"SOL"}
              :start-time-ms 10
              :end-time-ms 20}]]
           (history-actions/toggle-funding-history-filter-coin
            {:account-info {:funding-history {:draft-filters {:start-time-ms 10
                                                              :end-time-ms 20}}}}
            "SOL")))
    (is (= []
           (history-actions/toggle-funding-history-filter-coin state "   "))))
  (with-redefs [platform/now-ms (constantly 2000)]
    (let [filters {:coin-set #{"BTC" ""}
                   :start-time-ms 50
                   :end-time-ms 75}
          normalized-filters (funding-history/normalize-funding-history-filters filters 2000)]
      (is (= [[:effects/save-many [[[:account-info :funding-history :draft-filters] normalized-filters]
                                   [[:account-info :funding-history :filter-open?] false]
                                   [[:account-info :funding-history :coin-search] ""]
                                   [[:account-info :funding-history :coin-suggestions-open?] false]]]]
             (history-actions/reset-funding-history-filter-draft
              {:account-info {:funding-history {:filter-open? true
                                                :filters filters}}}))))))

(deftest add-funding-history-filter-coin-and-keydown-handler-cover-enter-and-escape-test
  (let [draft-filters {:coin-set #{"BTC"}
                       :start-time-ms 10
                       :end-time-ms 20}
        state {:account-info {:funding-history {:draft-filters draft-filters}}}]
    (is (= [[:effects/save-many [[[:account-info :funding-history :draft-filters]
                                   {:coin-set #{"BTC" "ETH"}
                                    :start-time-ms 10
                                    :end-time-ms 20}]
                                  [[:account-info :funding-history :coin-search] ""]
                                  [[:account-info :funding-history :coin-suggestions-open?] true]]]]
           (history-actions/add-funding-history-filter-coin state "ETH")))
    (is (= []
           (history-actions/add-funding-history-filter-coin state "  ")))
    (is (= [[:effects/save-many [[[:account-info :funding-history :draft-filters]
                                   {:coin-set #{"BTC" "SOL"}
                                    :start-time-ms 10
                                    :end-time-ms 20}]
                                  [[:account-info :funding-history :coin-search] ""]
                                  [[:account-info :funding-history :coin-suggestions-open?] true]]]]
           (history-actions/handle-funding-history-coin-search-keydown state "Enter" "SOL")))
    (is (= [[:effects/save [:account-info :funding-history :coin-suggestions-open?] false]]
           (history-actions/handle-funding-history-coin-search-keydown state "Escape" nil)))
    (is (= []
           (history-actions/handle-funding-history-coin-search-keydown state "Tab" "SOL")))))

(deftest select-account-info-tab-and-export-funding-history-csv-cover-default-and-filtered-projection-test
  (let [btc-row (fixtures/info-funding-row 1700003600000 "BTC" "0.1000" "10" "0.0001")
        eth-row (fixtures/info-funding-row 1700000000000 "ETH" "0.0500" "5" "0.0002")
        state {:account-info {:funding-history {:filters {:coin-set #{"BTC"}
                                                          :start-time-ms 0
                                                          :end-time-ms 2000000000000}}}
               :orders {:fundings-raw [eth-row btc-row]}}]
    (is (= [[:effects/save [:account-info :selected-tab] :balances]]
           (history-actions/select-account-info-tab state :balances)))
    (is (= [[:effects/export-funding-history-csv [btc-row]]]
           (history-actions/export-funding-history-csv state)))))

(deftest select-account-info-tab-order-history-uses-effective-address-for-freshness-test
  (with-redefs [platform/now-ms (constantly 100000)]
    (let [spectate-address "0xdddddddddddddddddddddddddddddddddddddddd"
          state {:wallet {:address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                 :account-context {:spectate-mode {:active? true
                                                :address spectate-address}}
                 :account-info {:order-history {:loading? false
                                                :loaded-at-ms 99000
                                                :loaded-for-address spectate-address
                                                :error nil}}}]
      (is (= [[:effects/save [:account-info :selected-tab] :order-history]]
             (take 1 (history-actions/select-account-info-tab state :order-history))))
      (is (= [[:effects/load-account-tab-module :order-history]]
             (subvec (history-actions/select-account-info-tab state :order-history) 1 2))))))

(deftest select-account-info-tab-syncs-trade-url-with-market-and-tab-query-test
  (let [state {:router {:path "/trade"}
               :active-asset "ETH"
               :account-info {:selected-tab :balances}}
        effects (history-actions/select-account-info-tab state :outcomes)]
    (is (= [[:effects/save [:account-info :selected-tab] :outcomes]
            [:effects/load-account-tab-module :outcomes]
            [:effects/push-state "/trade?market=ETH&tab=outcomes"]]
           effects))))

(deftest select-account-info-tab-sync-preserves-spectate-query-when-active-test
  (let [spectate-address "0xdddddddddddddddddddddddddddddddddddddddd"
        state {:router {:path "/trade"}
               :active-asset "ETH"
               :account-info {:selected-tab :balances}
               :account-context {:spectate-mode {:active? true
                                                 :address spectate-address}}}
        effects (history-actions/select-account-info-tab state :positions)]
    (is (= [[:effects/save [:account-info :selected-tab] :positions]
            [:effects/load-account-tab-module :positions]
            [:effects/push-state
             "/trade?market=ETH&tab=positions&spectate=0xdddddddddddddddddddddddddddddddddddddddd"]]
           effects))))

(deftest sort-positions-balances-and-open-orders-cover-direction-branches-test
  (testing "positions and balances toggle to desc only for same-column asc"
    (is (= [[:effects/save [:account-info :positions-sort] {:column "Coin" :direction :desc}]]
           (history-actions/sort-positions
            {:account-info {:positions-sort {:column "Coin" :direction :asc}}}
            "Coin")))
    (is (= [[:effects/save [:account-info :balances-sort] {:column "Coin" :direction :asc}]]
           (history-actions/sort-balances
            {:account-info {:balances-sort {:column "Value" :direction :asc}}}
            "Coin"))))
  (testing "open orders uses mixed default direction for new columns and persists to storage"
    (is (= [[:effects/save [:account-info :open-orders-sort] {:column "Time" :direction :desc}]
            [:effects/local-storage-set "open-orders-sort-by" "Time"]
            [:effects/local-storage-set "open-orders-sort-direction" "desc"]]
           (history-actions/sort-open-orders
            {:account-info {:open-orders-sort {:column "Time" :direction :asc}}}
            "Time")))
    (is (= [[:effects/save [:account-info :open-orders-sort] {:column "Price" :direction :desc}]
            [:effects/local-storage-set "open-orders-sort-by" "Price"]
            [:effects/local-storage-set "open-orders-sort-direction" "desc"]]
           (history-actions/sort-open-orders
            {:account-info {:open-orders-sort {:column "Coin" :direction :asc}}}
            "Price")))
    (is (= [[:effects/save [:account-info :open-orders-sort] {:column "Coin" :direction :asc}]
            [:effects/local-storage-set "open-orders-sort-by" "Coin"]
            [:effects/local-storage-set "open-orders-sort-direction" "asc"]]
           (history-actions/sort-open-orders
            {:account-info {:open-orders-sort {:column "Price" :direction :desc}}}
            "Coin")))))

(deftest history-page-input-setters-stringify-non-strings-and-preserve-strings-test
  (is (= [[:effects/save [:account-info :funding-history :page-input] "12"]]
         (history-actions/set-funding-history-page-input nil 12)))
  (is (= [[:effects/save [:account-info :funding-history :page-input] "03"]]
         (history-actions/set-funding-history-page-input nil "03")))
  (is (= [[:effects/save [:account-info :trade-history :page-input] ""]]
         (history-actions/set-trade-history-page-input nil nil)))
  (is (= [[:effects/save [:account-info :order-history :page-input] "8"]]
         (history-actions/set-order-history-page-input nil 8))))

(deftest set-order-history-status-filter-parses-strings-and-falls-back-to-all-test
  (is (= [[:effects/save-many [[[:account-info :order-history :status-filter] :short]
                               [[:account-info :order-history :filter-open?] false]
                               [[:account-info :order-history :page] 1]
                               [[:account-info :order-history :page-input] "1"]]]]
         (history-actions/set-order-history-status-filter nil "ShOrT")))
  (is (= [[:effects/save-many [[[:account-info :order-history :status-filter] :all]
                               [[:account-info :order-history :filter-open?] false]
                               [[:account-info :order-history :page] 1]
                               [[:account-info :order-history :page-input] "1"]]]]
         (history-actions/set-order-history-status-filter nil "invalid-status"))))

(deftest open-orders-direction-filter-actions-normalize-and-close-dropdown-test
  (is (= [[:effects/save [:account-info :open-orders :filter-open?] true]]
         (history-actions/toggle-open-orders-direction-filter-open
          {:account-info {:open-orders {:filter-open? false}}})))
  (is (= [[:effects/save [:account-info :open-orders :filter-open?] false]]
         (history-actions/toggle-open-orders-direction-filter-open
          {:account-info {:open-orders {:filter-open? true}}})))
  (is (= [[:effects/save-many [[[:account-info :open-orders :direction-filter] :short]
                               [[:account-info :open-orders :filter-open?] false]]]]
         (history-actions/set-open-orders-direction-filter nil "ShOrT")))
  (is (= [[:effects/save-many [[[:account-info :open-orders :direction-filter] :all]
                               [[:account-info :open-orders :filter-open?] false]]]]
         (history-actions/set-open-orders-direction-filter nil "invalid-filter"))))

(deftest positions-direction-filter-actions-normalize-and-close-dropdown-test
  (is (= [[:effects/save [:account-info :positions :filter-open?] true]]
         (history-actions/toggle-positions-direction-filter-open
          {:account-info {:positions {:filter-open? false}}})))
  (is (= [[:effects/save [:account-info :positions :filter-open?] false]]
         (history-actions/toggle-positions-direction-filter-open
          {:account-info {:positions {:filter-open? true}}})))
  (is (= [[:effects/save-many [[[:account-info :positions :direction-filter] :short]
                               [[:account-info :positions :filter-open?] false]]]]
         (history-actions/set-positions-direction-filter nil "ShOrT")))
  (is (= [[:effects/save-many [[[:account-info :positions :direction-filter] :all]
                               [[:account-info :positions :filter-open?] false]]]]
         (history-actions/set-positions-direction-filter nil "invalid-filter"))))

(deftest trade-history-direction-filter-actions-normalize-close-dropdown-and-reset-pagination-test
  (is (= [[:effects/save [:account-info :trade-history :filter-open?] true]]
         (history-actions/toggle-trade-history-direction-filter-open
          {:account-info {:trade-history {:filter-open? false}}})))
  (is (= [[:effects/save [:account-info :trade-history :filter-open?] false]]
         (history-actions/toggle-trade-history-direction-filter-open
          {:account-info {:trade-history {:filter-open? true}}})))
  (is (= [[:effects/save-many [[[:account-info :trade-history :direction-filter] :short]
                               [[:account-info :trade-history :filter-open?] false]
                               [[:account-info :trade-history :page] 1]
                               [[:account-info :trade-history :page-input] "1"]]]]
         (history-actions/set-trade-history-direction-filter nil "ShOrT")))
  (is (= [[:effects/save-many [[[:account-info :trade-history :direction-filter] :all]
                               [[:account-info :trade-history :filter-open?] false]
                               [[:account-info :trade-history :page] 1]
                               [[:account-info :trade-history :page-input] "1"]]]]
         (history-actions/set-trade-history-direction-filter nil "invalid-filter"))))

(deftest set-hide-small-balances-updates-flag-test
  (is (= [[:effects/save [:account-info :hide-small-balances?] true]]
         (history-actions/set-hide-small-balances nil true))))

(deftest set-account-info-coin-search-updates-tab-specific-state-test
  (is (= [[:effects/save [:account-info :balances-coin-search] "ETH"]]
         (history-actions/set-account-info-coin-search nil :balances "ETH")))
  (is (= [[:effects/save [:account-info :positions :coin-search] "nv"]]
         (history-actions/set-account-info-coin-search nil "PoSiTiOnS" "nv")))
  (is (= [[:effects/save [:account-info :open-orders :coin-search] "sol"]]
         (history-actions/set-account-info-coin-search nil :open-orders "sol")))
  (is (= [[:effects/save-many [[[:account-info :trade-history :coin-search] "123"]
                               [[:account-info :trade-history :page] 1]
                               [[:account-info :trade-history :page-input] "1"]]]]
         (history-actions/set-account-info-coin-search nil :trade-history 123)))
  (is (= [[:effects/save-many [[[:account-info :order-history :coin-search] "42"]
                               [[:account-info :order-history :page] 1]
                               [[:account-info :order-history :page-input] "1"]]]]
         (history-actions/set-account-info-coin-search nil :order-history 42)))
  (is (= [[:effects/save [:account-info :balances-coin-search] "fallback"]]
         (history-actions/set-account-info-coin-search nil :unknown "fallback"))))

(deftest toggle-account-info-mobile-card-saves-collapses-and-ignores-invalid-inputs-test
  (let [state {:account-info {:mobile-expanded-card {:balances "btc"
                                                     :positions nil
                                                     :trade-history nil}}}]
    (is (= [[:effects/save [:account-info :mobile-expanded-card :balances] "eth"]]
           (history-actions/toggle-account-info-mobile-card state :balances "eth")))
    (is (= [[:effects/save [:account-info :mobile-expanded-card :balances] nil]]
           (history-actions/toggle-account-info-mobile-card state :balances "btc")))
    (is (= [[:effects/save [:account-info :mobile-expanded-card :positions] "nvda"]]
           (history-actions/toggle-account-info-mobile-card state :positions "nvda")))
    (is (= [[:effects/save [:account-info :mobile-expanded-card :trade-history] "42"]]
           (history-actions/toggle-account-info-mobile-card state "trade-history" 42)))
    (is (= []
           (history-actions/toggle-account-info-mobile-card state :open-orders "btc")))
    (is (= []
           (history-actions/toggle-account-info-mobile-card state :balances "   ")))))
