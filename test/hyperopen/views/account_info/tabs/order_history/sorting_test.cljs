(ns hyperopen.views.account-info.tabs.order-history.sorting-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.account-info.tabs.order-history :as order-history-tab]))

(defn- reset-order-history-sort-cache-fixture
  [f]
  (order-history-tab/reset-order-history-sort-cache!)
  (f)
  (order-history-tab/reset-order-history-sort-cache!))

(use-fixtures :each reset-order-history-sort-cache-fixture)

(deftest order-history-tab-content-memoizes-normalize-sort-and-index-by-input-signatures-test
  (let [raw-rows [{:order {:coin "ETH"
                           :oid 1
                           :side "B"
                           :origSz "1.0"
                           :remainingSz "0.0"
                           :limitPx "100"
                           :orderType "Limit"
                           :isTrigger false
                           :isPositionTpsl false
                           :timestamp 1700000000000}
                   :status "filled"
                   :statusTimestamp 1700000000000}]
        normalized-row {:time-ms 1700000000000
                        :type "Limit"
                        :coin "ETH"
                        :side "B"
                        :size 1
                        :filled-size 1
                        :order-value 100
                        :px "100"
                        :status-key :filled
                        :status-label "Filled"
                        :oid "1"}
        table-state {:sort {:column "Time" :direction :desc}
                     :status-filter :all
                     :loading? false
                     :market-by-key {}}
        equivalent-market (into {} (:market-by-key table-state))
        changed-market {"spot:ETH/USDC" {:coin "spot:ETH/USDC"
                                         :symbol "ETH/USDC"}}
        normalize-calls (atom 0)
        sort-calls (atom 0)
        index-calls (atom 0)
        original-index-builder @#'order-history-tab/*build-order-history-coin-search-index*]
    (order-history-tab/reset-order-history-sort-cache!)
    (with-redefs [order-history-tab/normalized-order-history
                  (fn [_rows]
                    (swap! normalize-calls inc)
                    [normalized-row])
                  order-history-tab/sort-order-history-by-column
                  (fn [rows _column _direction]
                    (swap! sort-calls inc)
                    rows)
                  order-history-tab/*build-order-history-coin-search-index*
                  (fn [rows market-by-key]
                    (swap! index-calls inc)
                    (original-index-builder rows market-by-key))]
      (order-history-tab/order-history-tab-content raw-rows table-state)
      (order-history-tab/order-history-tab-content raw-rows table-state)
      (is (= 1 @normalize-calls))
      (is (= 1 @sort-calls))
      (is (= 1 @index-calls))

      (let [asc-state (assoc-in table-state [:sort :direction] :asc)]
        (order-history-tab/order-history-tab-content raw-rows asc-state)
        (order-history-tab/order-history-tab-content raw-rows asc-state)
        (is (= 2 @normalize-calls))
        (is (= 2 @sort-calls))
        (is (= 2 @index-calls))

        (order-history-tab/order-history-tab-content raw-rows (assoc asc-state :coin-search "et"))
        (order-history-tab/order-history-tab-content raw-rows (assoc asc-state :coin-search "et"))
        (is (= 2 @normalize-calls))
        (is (= 2 @sort-calls))
        (is (= 2 @index-calls))

        (let [churned-rows (into [] raw-rows)]
          (order-history-tab/order-history-tab-content churned-rows asc-state)
          (order-history-tab/order-history-tab-content churned-rows asc-state)
          (is (= 2 @normalize-calls))
          (is (= 2 @sort-calls))
          (is (= 2 @index-calls)))

        (order-history-tab/order-history-tab-content raw-rows (assoc asc-state :market-by-key equivalent-market))
        (is (= 2 @normalize-calls))
        (is (= 2 @sort-calls))
        (is (= 2 @index-calls))

        (order-history-tab/order-history-tab-content raw-rows (assoc asc-state :market-by-key changed-market))
        (is (= 2 @normalize-calls))
        (is (= 2 @sort-calls))
        (is (= 3 @index-calls))

        (let [changed-rows (assoc-in (into [] raw-rows) [0 :order :limitPx] "101")]
          (order-history-tab/order-history-tab-content changed-rows asc-state)
          (is (= 3 @normalize-calls))
          (is (= 3 @sort-calls))
          (is (= 4 @index-calls)))))))

(deftest order-history-direction-label-prefers-explicit-text-and-reduce-only-fallbacks-test
  (is (= "Open Short"
         (@#'order-history-tab/order-history-direction-label
          {:direction "open short"
           :side "B"
           :reduce-only true})))
  (is (= "Close Short"
         (@#'order-history-tab/order-history-direction-label
          {:side "B"
           :reduce-only true})))
  (is (= "Close Long"
         (@#'order-history-tab/order-history-direction-label
          {:side "A"
           :reduce-only true})))
  (is (= "Long"
         (@#'order-history-tab/order-history-direction-label
          {:side "B"
           :reduce-only false}))))

(deftest order-history-direction-class-infers-buy-sell-and-neutral-tones-test
  (is (= "text-success"
         (@#'order-history-tab/order-history-direction-class {:side "B"})))
  (is (= "text-error"
         (@#'order-history-tab/order-history-direction-class {:side "S"})))
  (is (= "text-success"
         (@#'order-history-tab/order-history-direction-class
          {:direction "buy"})))
  (is (= "text-error"
         (@#'order-history-tab/order-history-direction-class
          {:direction "close long"})))
  (is (= "text-success"
         (@#'order-history-tab/order-history-direction-class
          {:direction "Market Order Liquidation: Close Short"})))
  (is (= "text-base-content"
         (@#'order-history-tab/order-history-direction-class
          {:direction "hold"}))))

(deftest sort-order-history-by-column-is-deterministic-on-ties-test
  (let [rows (order-history-tab/normalized-order-history
              [{:order {:coin "BTC" :oid "2" :side "B" :origSz "1.0" :remainingSz "0.0" :limitPx "1.0"}
                :status "filled"
                :statusTimestamp 2000}
               {:order {:coin "BTC" :oid "1" :side "B" :origSz "1.0" :remainingSz "0.0" :limitPx "1.0"}
                :status "filled"
                :statusTimestamp 2000}])
        time-asc (order-history-tab/sort-order-history-by-column rows "Time" :asc)
        oid-desc (order-history-tab/sort-order-history-by-column rows "Order ID" :desc)]
    (is (= ["1" "2"] (mapv (comp str :oid) time-asc)))
    (is (= ["2" "1"] (mapv (comp str :oid) oid-desc)))))

(deftest sort-order-history-by-column-supports-derived-order-history-columns-test
  (let [rows [{:id :alpha
               :time-ms 200
               :type "Market"
               :coin "BTC"
               :direction "close long"
               :size-num 1
               :filled-size 0
               :order-value 0
               :market? true
               :reduce-only nil
               :is-trigger false
               :is-position-tpsl false
               :status-label "Rejected"
               :oid "beta"}
              {:id :beta
               :time-ms 100
               :type "Limit"
               :coin "ETH"
               :direction "buy"
               :size-num 2
               :filled-size 1
               :order-value 50
               :market? false
               :px "25"
               :reduce-only false
               :is-trigger true
               :trigger-condition "Above"
               :trigger-px "30"
               :is-position-tpsl true
               :status-label "Filled"
               :oid "12"}
              {:id :gamma
               :time-ms 150
               :type "Stop Market"
               :coin "ADA"
               :side "B"
               :size-num 3
               :filled-size 2
               :order-value 75
               :market? false
               :px "24"
               :reduce-only true
               :is-trigger true
               :trigger-condition "Below"
               :trigger-px "10"
               :is-position-tpsl false
               :status-label "Canceled"
               :oid "3"}]
        column-cases [["Direction" :asc [:beta :alpha :gamma]]
                      ["Price" :asc [:alpha :gamma :beta]]
                      ["Reduce Only" :asc [:alpha :beta :gamma]]
                      ["Trigger Conditions" :asc [:alpha :gamma :beta]]
                      ["TP/SL" :asc [:gamma :alpha :beta]]
                      ["Status" :asc [:gamma :beta :alpha]]
                      ["Order ID" :asc [:gamma :beta :alpha]]]]
    (doseq [[column direction expected-order] column-cases]
      (is (= expected-order
             (mapv :id (order-history-tab/sort-order-history-by-column rows column direction)))
          (str "Unexpected order for column " column)))))

(deftest order-history-filters-by-fuzzy-coin-search-test
  (let [rows [{:order {:coin "xyz:NVDA"
                       :oid 1
                       :side "B"
                       :origSz "1.0"
                       :remainingSz "0.0"
                       :limitPx "0"
                       :orderType "Market"
                       :timestamp 1700000000000}
               :status "filled"
               :statusTimestamp 1700000000000}
              {:order {:coin "@230"
                       :oid 2
                       :side "A"
                       :origSz "1.0"
                       :remainingSz "0.0"
                       :limitPx "0.001"
                       :orderType "Limit"
                       :timestamp 1699999999000}
               :status "filled"
               :statusTimestamp 1699999999000}]
        market-by-key {"spot:@230" {:coin "@230"
                                    :symbol "SOL/USDC"
                                    :base "SOL"
                                    :market-type :spot}}
        nv-content (order-history-tab/order-history-tab-content rows {:sort {:column "Time" :direction :desc}
                                                                      :status-filter :all
                                                                      :coin-search "nd"
                                                                      :loading? false
                                                                      :market-by-key market-by-key})
        sol-content (order-history-tab/order-history-tab-content rows {:sort {:column "Time" :direction :desc}
                                                                       :status-filter :all
                                                                       :coin-search "sl"
                                                                       :loading? false
                                                                       :market-by-key market-by-key})
        nv-strings (set (hiccup/collect-strings nv-content))
        sol-strings (set (hiccup/collect-strings sol-content))]
    (is (contains? nv-strings "NVDA"))
    (is (not (contains? nv-strings "SOL")))
    (is (contains? sol-strings "SOL"))
    (is (not (contains? sol-strings "NVDA")))))
