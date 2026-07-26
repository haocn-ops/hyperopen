(ns hyperopen.views.account-info.tabs.trade-history.sorting-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.account-info.tabs.trade-history :as trade-history-tab]
            [hyperopen.views.account-info.tabs.trade-history.shared :as trade-shared]))

(defn- reset-trade-history-sort-cache-fixture
  [f]
  (trade-shared/reset-trade-history-sort-cache!)
  (f)
  (trade-shared/reset-trade-history-sort-cache!))

(use-fixtures :each reset-trade-history-sort-cache-fixture)

(deftest sort-trade-history-by-column-is-deterministic-on-ties-and-formats-derived-values-test
  (let [rows [{:tid 2
               :coin "xyz:NVDA"
               :side "B"
               :sz "2"
               :px "10"
               :fee "0.1"
               :time 1700000000000}
              {:tid 1
               :coin "BTC"
               :side "A"
               :sz "1"
               :px "15"
               :fee "0.2"
               :time 1700000000000}
              {:tid 3
               :coin "ETH"
               :dir "Open Long (Price Improved)"
               :sz "3"
               :px "8"
               :tradeValue "24"
               :closedPnl "-0.3"
               :fee "0.05"
               :time 1700000001000}]
        time-asc (trade-shared/sort-trade-history-by-column rows "Time" :asc {})
        value-desc (trade-shared/sort-trade-history-by-column rows "Trade Value" :desc {})
        direction-asc (trade-shared/sort-trade-history-by-column rows "Direction" :asc {})]
    (is (= [1 2 3] (mapv :tid time-asc)))
    (is (= [3 2 1] (mapv :tid value-desc)))
    (is (= [2 3 1] (mapv :tid direction-asc)))))

(deftest trade-history-tab-content-memoizes-by-input-signatures-and-rebuilds-only-market-index-on-market-change-test
  (let [fills [{:tid 1
                :coin "ETH"
                :side "B"
                :sz "1.0"
                :px "100.0"
                :fee "0.1"
                :time 1700000000000}]
        trade-history-state {:sort {:column "Time" :direction :desc}
                             :direction-filter :all
                             :coin-search ""
                             :market-by-key {}}
        equivalent-market (into {} (:market-by-key trade-history-state))
        changed-market {"spot:ETH/USDC" {:coin "spot:ETH/USDC"
                                         :symbol "ETH/USDC"}}
        sort-calls (atom 0)
        index-calls (atom 0)
        original-index-builder @#'trade-shared/*build-trade-history-coin-search-index*]
    (trade-shared/reset-trade-history-sort-cache!)
    (with-redefs [trade-shared/sort-trade-history-by-column
                  (fn
                    ([rows _column _direction]
                     (swap! sort-calls inc)
                     rows)
                    ([rows _column _direction _market-by-key]
                     (swap! sort-calls inc)
                     rows))
                  trade-shared/*build-trade-history-coin-search-index*
                  (fn [rows market-by-key]
                    (swap! index-calls inc)
                    (original-index-builder rows market-by-key))]
      (trade-history-tab/trade-history-tab-content fills trade-history-state)
      (trade-history-tab/trade-history-tab-content fills trade-history-state)
      (is (= 1 @sort-calls))
      (is (= 1 @index-calls))

      (let [churned-fills (into [] fills)]
        (trade-history-tab/trade-history-tab-content churned-fills trade-history-state)
        (trade-history-tab/trade-history-tab-content churned-fills trade-history-state)
        (is (= 1 @sort-calls))
        (is (= 1 @index-calls)))

      (trade-history-tab/trade-history-tab-content fills (assoc trade-history-state :market-by-key equivalent-market))
      (is (= 1 @sort-calls))
      (is (= 1 @index-calls))

      (trade-history-tab/trade-history-tab-content fills (assoc trade-history-state :market-by-key changed-market))
      (is (= 1 @sort-calls))
      (is (= 2 @index-calls))

      (trade-history-tab/trade-history-tab-content fills
                                                   (assoc trade-history-state
                                                          :market-by-key changed-market
                                                          :coin-search "et"))
      (trade-history-tab/trade-history-tab-content fills
                                                   (assoc trade-history-state
                                                          :market-by-key changed-market
                                                          :coin-search "et"))
      (is (= 1 @sort-calls))
      (is (= 2 @index-calls))

      (trade-history-tab/trade-history-tab-content fills
                                                   (assoc trade-history-state
                                                          :market-by-key changed-market
                                                          :direction-filter :short))
      (trade-history-tab/trade-history-tab-content fills
                                                   (assoc trade-history-state
                                                          :market-by-key changed-market
                                                          :direction-filter :short))
      (is (= 2 @sort-calls))
      (is (= 3 @index-calls))

      (let [changed-fills (assoc-in (into [] fills) [0 :px] "101.0")]
        (trade-history-tab/trade-history-tab-content changed-fills trade-history-state)
        (is (= 3 @sort-calls))
        (is (= 4 @index-calls))))))

(deftest trade-history-tab-content-filters-rows-by-direction-filter-test
  (let [fills [{:tid 1
                :coin "LONGCOIN"
                :side "B"
                :sz "1.0"
                :px "100.0"
                :fee "0.1"
                :time 1700000002000}
               {:tid 2
                :coin "SHORTA"
                :side "A"
                :sz "2.0"
                :px "99.0"
                :fee "0.1"
                :time 1700000001000}
               {:tid 3
                :coin "SHORTS"
                :side "S"
                :sz "3.0"
                :px "98.0"
                :fee "0.1"
                :time 1700000000000}]
        all-content (trade-history-tab/trade-history-tab-content fills {:direction-filter :all})
        long-content (trade-history-tab/trade-history-tab-content fills {:direction-filter :long})
        short-content (trade-history-tab/trade-history-tab-content fills {:direction-filter :short})
        all-row-count (count (vec (hiccup/node-children (hiccup/tab-rows-viewport-node all-content))))
        long-row-count (count (vec (hiccup/node-children (hiccup/tab-rows-viewport-node long-content))))
        short-row-count (count (vec (hiccup/node-children (hiccup/tab-rows-viewport-node short-content))))
        long-text (set (hiccup/collect-strings long-content))
        short-text (set (hiccup/collect-strings short-content))]
    (is (= 3 all-row-count))
    (is (= 1 long-row-count))
    (is (= 2 short-row-count))
    (is (contains? long-text "LONGCOIN"))
    (is (not (contains? long-text "SHORTA")))
    (is (not (contains? long-text "SHORTS")))
    (is (contains? short-text "SHORTA"))
    (is (contains? short-text "SHORTS"))
    (is (not (contains? short-text "LONGCOIN")))))

(deftest trade-history-tab-content-filters-rows-by-coin-search-test
  (let [fills [{:tid 1
                :coin "xyz:NVDA"
                :side "B"
                :sz "1.0"
                :px "100.0"
                :fee "0.1"
                :time 1700000002000}
               {:tid 2
                :coin "HYPE"
                :side "A"
                :sz "2.0"
                :px "99.0"
                :fee "0.1"
                :time 1700000001000}]
        all-content (trade-history-tab/trade-history-tab-content fills {:coin-search ""})
        symbol-search-content (trade-history-tab/trade-history-tab-content fills {:coin-search "nv"})
        prefix-search-content (trade-history-tab/trade-history-tab-content fills {:coin-search "xyz"})
        all-row-count (count (vec (hiccup/node-children (hiccup/tab-rows-viewport-node all-content))))
        symbol-row-count (count (vec (hiccup/node-children (hiccup/tab-rows-viewport-node symbol-search-content))))
        prefix-row-count (count (vec (hiccup/node-children (hiccup/tab-rows-viewport-node prefix-search-content))))
        symbol-text (set (hiccup/collect-strings symbol-search-content))
        prefix-text (set (hiccup/collect-strings prefix-search-content))]
    (is (= 2 all-row-count))
    (is (= 1 symbol-row-count))
    (is (= 1 prefix-row-count))
    (is (contains? symbol-text "NVDA"))
    (is (not (contains? symbol-text "HYPE")))
    (is (contains? prefix-text "NVDA"))
    (is (contains? prefix-text "xyz"))))
