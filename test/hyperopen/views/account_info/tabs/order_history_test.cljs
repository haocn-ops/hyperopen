(ns hyperopen.views.account-info.tabs.order-history-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.account-info.tabs.order-history :as order-history-tab]))

(defn- reset-order-history-sort-cache-fixture
  [f]
  (order-history-tab/reset-order-history-sort-cache!)
  (f)
  (order-history-tab/reset-order-history-sort-cache!))

(use-fixtures :each reset-order-history-sort-cache-fixture)

(deftest order-history-sortable-header-uses-secondary-text-hover-and-action-test
  (let [header-node (order-history-tab/sortable-order-history-header "Time" {:column "Time" :direction :asc})
        sort-icon-node (second (vec (hiccup/node-children header-node)))]
    (is (contains? (hiccup/node-class-set header-node) "text-trading-text-secondary"))
    (is (contains? (hiccup/node-class-set header-node) "hover:text-trading-text"))
    (is (= [[:actions/sort-order-history "Time"]]
           (get-in header-node [1 :on :click])))
    (is (= "↑" (last sort-icon-node)))))

(deftest order-history-content-renders-hyperliquid-columns-and-values-test
  (let [rows [{:order {:coin "xyz:NVDA"
                       :oid 307891000622
                       :side "B"
                       :origSz "0.500"
                       :remainingSz "0.000"
                       :limitPx "0"
                       :orderType "Market"
                       :reduceOnly false
                       :isTrigger false
                       :timestamp 1700000000000}
               :status "filled"
               :statusTimestamp 1700000000500}
              {:order {:coin "PUMP"
                       :oid 275043415805
                       :side "B"
                       :origSz "11386"
                       :remainingSz "11386"
                       :limitPx "0.001000"
                       :orderType "Limit"
                       :reduceOnly true
                       :isTrigger false
                       :timestamp 1700000000000}
               :status "canceled"
               :statusTimestamp 1699999999000}]
        content (order-history-tab/order-history-tab-content rows {:sort {:column "Time" :direction :desc}
                                                                   :status-filter :all
                                                                   :loading? false})
        strings (set (hiccup/collect-strings content))]
    (is (some? (hiccup/find-first-node content #(contains? (hiccup/direct-texts %) "Filled Size"))))
    (is (some? (hiccup/find-first-node content #(contains? (hiccup/direct-texts %) "Trigger Conditions"))))
    (is (some? (hiccup/find-first-node content #(contains? (hiccup/direct-texts %) "Order ID"))))
    (is (contains? strings "NVDA"))
    (is (contains? strings "xyz"))
    (is (not (contains? strings "xyz:NVDA")))
    (is (contains? strings "Market"))
    (is (contains? strings "N/A"))
    (is (contains? strings "No"))
    (is (contains? strings "Yes"))
    (is (contains? strings "Long"))
    (is (contains? strings "Close Short"))
    (is (contains? strings "Filled"))
    (is (contains? strings "Canceled"))))

(deftest order-history-status-and-order-id-cells-use-overflow-safe-classes-test
  (let [rows [{:order {:coin "PUMP"
                       :oid 3300074759
                       :side "A"
                       :origSz "11386"
                       :remainingSz "11386"
                       :limitPx "0.001000"
                       :orderType "Take Profit Market"
                       :reduceOnly true
                       :isTrigger true
                       :triggerCondition "Above"
                       :triggerPx "0.001949"
                       :timestamp 1700000000000}
               :status "reduceonlycanceled"
               :statusTimestamp 1699999999000}]
        content (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                             :status-filter :all
                                                             :loading? false})
        row-node (hiccup/first-viewport-row content)
        cells (vec (hiccup/node-children row-node))
        direction-cell (nth cells 3)
        status-cell (nth cells 11)
        order-id-cell (nth cells 12)
        direction-strings (set (hiccup/collect-strings direction-cell))
        status-classes (hiccup/node-class-set status-cell)
        order-id-classes (hiccup/node-class-set order-id-cell)
        status-strings (set (hiccup/collect-strings status-cell))
        underlined-status (hiccup/find-first-node status-cell
                                                  #(contains? (hiccup/node-class-set %)
                                                              "underline"))
        status-tooltip-container (hiccup/find-first-node status-cell
                                                         #(contains? (hiccup/node-class-set %)
                                                                     "pointer-events-none"))
        status-tooltip-panel (hiccup/find-first-node status-cell
                                                     #(contains? (hiccup/node-class-set %)
                                                                 "bg-gray-800"))]
    (is (contains? status-classes "break-words"))
    (is (contains? status-classes "leading-4"))
    (is (contains? direction-strings "Close Long"))
    (is (contains? status-strings "Canceled"))
    (is (contains? status-strings "Canceled due to reduce only."))
    (is (some? underlined-status))
    (is (some? status-tooltip-container))
    (is (contains? (hiccup/node-class-set status-tooltip-container) "left-1/2"))
    (is (contains? (hiccup/node-class-set status-tooltip-container) "-translate-x-1/2"))
    (is (some? status-tooltip-panel))
    (is (contains? (hiccup/node-class-set status-tooltip-panel) "w-max"))
    (is (= :div (first status-tooltip-panel)))
    (is (contains? order-id-classes "order-history-order-id-text"))
    (is (contains? order-id-classes "tracking-tight"))
    (is (contains? order-id-classes "whitespace-nowrap"))))

(deftest order-history-tab-content-dedupes-open-and-filled-rows-with-the-same-order-id-test
  (let [rows [{:order {:coin "PUMP"
                       :oid 330007475448
                       :side "A"
                       :origSz "11273"
                       :remainingSz "11273"
                       :limitPx "0.001772"
                       :orderType "Limit"
                       :timestamp 1700000000000}
               :status "open"
               :statusTimestamp 1700000000000}
              {:order {:coin "PUMP"
                       :oid 330007475448
                       :side "A"
                       :origSz "11273"
                       :remainingSz "0.0"
                       :limitPx "0.001772"
                       :orderType "Limit"
                       :timestamp 1700000000000}
               :status "filled"
               :statusTimestamp 1700000000000}]
        content (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                             :status-filter :all
                                                             :loading? false})
        viewport (hiccup/tab-rows-viewport-node content)
        rendered-rows (vec (hiccup/node-children viewport))
        row-strings (set (hiccup/collect-strings (first rendered-rows)))
        all-strings (hiccup/collect-strings content)]
    (is (= 1 (count rendered-rows)))
    (is (contains? row-strings "Filled"))
    (is (not (contains? row-strings "Open")))
    (is (= 1 (count (filter #(= "330007475448" %) all-strings))))))

(deftest order-history-coin-labels-are-bold-and-side-colored-test
  (let [rows [{:order {:coin "xyz:NVDA"
                       :oid 307891000622
                       :side "B"
                       :origSz "0.500"
                       :remainingSz "0.000"
                       :limitPx "0"
                       :orderType "Market"
                       :reduceOnly false
                       :isTrigger false
                       :timestamp 1700000000000}
               :status "filled"
               :statusTimestamp 1700000000500}
              {:order {:coin "PUMP"
                       :oid 275043415805
                       :side "A"
                       :origSz "11386"
                       :remainingSz "11386"
                       :limitPx "0.001000"
                       :orderType "Limit"
                       :reduceOnly true
                       :isTrigger false
                       :timestamp 1700000000000}
               :status "canceled"
               :statusTimestamp 1699999999000}]
        content (order-history-tab/order-history-tab-content rows {:sort {:column "Time" :direction :desc}
                                                                   :status-filter :all
                                                                   :loading? false})
        long-coin-base (hiccup/find-first-node content #(and (= :span (first %))
                                                             (contains? (hiccup/node-class-set %) "whitespace-nowrap")
                                                             (contains? (hiccup/direct-texts %) "NVDA")))
        sell-coin-base (hiccup/find-first-node content #(and (= :span (first %))
                                                             (contains? (hiccup/node-class-set %) "whitespace-nowrap")
                                                             (contains? (hiccup/direct-texts %) "PUMP")))]
    (is (some? long-coin-base))
    (is (some? sell-coin-base))
    (is (contains? (hiccup/node-class-set long-coin-base) "font-semibold"))
    (is (contains? (hiccup/node-class-set sell-coin-base) "font-semibold"))
    (is (= "rgb(151, 252, 228)"
           (get-in long-coin-base [1 :style :color])))
    (is (= "rgb(234, 175, 184)"
           (get-in sell-coin-base [1 :style :color])))))

(deftest order-history-desktop-grid-and-coin-label-avoid-unnecessary-truncation-test
  (let [rows [{:order {:coin "xyz:SILVER"
                       :oid 307891000622
                       :side "B"
                       :origSz "0.500"
                       :remainingSz "0.000"
                       :limitPx "95.242"
                       :orderType "Limit"
                       :reduceOnly false
                       :isTrigger false
                       :timestamp 1700000000000}
               :status "filled"
               :statusTimestamp 1700000000500}]
        content (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                             :status-filter :all
                                                             :loading? false})
        header-node (hiccup/tab-header-node content)
        row-node (hiccup/first-viewport-row content)
        coin-cell (nth (vec (hiccup/node-children row-node)) 2)
        coin-base (hiccup/find-first-node coin-cell #(and (= :span (first %))
                                                          (contains? (hiccup/direct-texts %) "SILVER")))
        header-classes (hiccup/node-class-set header-node)
        row-classes (hiccup/node-class-set row-node)
        flexible-grid-class "grid-cols-[minmax(124px,1.4fr)_minmax(72px,0.72fr)_minmax(84px,1.2fr)_minmax(72px,1fr)_minmax(76px,0.82fr)_minmax(76px,0.82fr)_minmax(96px,1fr)_minmax(72px,0.78fr)_minmax(74px,0.74fr)_minmax(112px,1.08fr)_minmax(52px,0.58fr)_minmax(84px,0.82fr)_minmax(96px,0.92fr)]"
        old-grid-class "grid-cols-[minmax(130px,1.45fr)_minmax(110px,1.25fr)_minmax(84px,0.9fr)_minmax(64px,0.7fr)_minmax(82px,0.9fr)_minmax(72px,0.75fr)_minmax(100px,1.05fr)_minmax(72px,0.8fr)_minmax(74px,0.72fr)_minmax(140px,1.55fr)_minmax(60px,0.65fr)_minmax(120px,1.25fr)_minmax(106px,1.2fr)]"]
    (is (contains? header-classes flexible-grid-class))
    (is (contains? row-classes flexible-grid-class))
    (is (not (contains? header-classes old-grid-class)))
    (is (not (contains? row-classes old-grid-class)))
    (is (some? coin-base))
    (is (contains? (hiccup/node-class-set coin-base) "whitespace-nowrap"))
    (is (not (contains? (hiccup/node-class-set coin-base) "truncate")))))

(deftest order-history-desktop-coin-and-direction-columns-keep-readable-separation-test
  (let [rows [{:order {:coin "xyz:SILVER"
                       :oid 307891000622
                       :side "A"
                       :origSz "1.13"
                       :remainingSz "0.000"
                       :limitPx "88.30"
                       :orderType "Limit"
                       :reduceOnly true
                       :isTrigger false
                       :timestamp 1700000000000}
               :status "filled"
               :statusTimestamp 1700000000500}]
        content (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                             :status-filter :all
                                                             :loading? false})
        header-node (hiccup/tab-header-node content)
        row-node (hiccup/first-viewport-row content)
        header-cells (vec (hiccup/node-children header-node))
        row-cells (vec (hiccup/node-children row-node))
        coin-header (nth header-cells 2)
        direction-header (nth header-cells 3)
        coin-cell (nth row-cells 2)
        direction-cell (nth row-cells 3)]
    (is (contains? (hiccup/node-class-set coin-header) "pr-4"))
    (is (contains? (hiccup/node-class-set direction-header) "pl-2"))
    (is (contains? (hiccup/node-class-set coin-cell) "pr-4"))
    (is (contains? (hiccup/node-class-set direction-cell) "pl-2"))))

(deftest order-history-coin-cell-dispatches-select-asset-action-test
  (let [rows [{:order {:coin "xyz:NVDA"
                       :oid 307891000622
                       :side "B"
                       :origSz "0.500"
                       :remainingSz "0.000"
                       :limitPx "0"
                       :orderType "Market"
                       :reduceOnly false
                       :isTrigger false
                       :timestamp 1700000000000}
               :status "filled"
               :statusTimestamp 1700000000500}]
        content (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                             :status-filter :all
                                                             :loading? false})
        row-node (hiccup/first-viewport-row content)
        coin-cell (nth (vec (hiccup/node-children row-node)) 2)
        coin-button (hiccup/find-first-node coin-cell #(= :button (first %)))]
    (is (some? coin-button))
    (is (= [[:actions/select-asset "xyz:NVDA"]]
           (get-in coin-button [1 :on :click])))))

(deftest order-history-coin-label-prefers-market-base-for-spot-id-test
  (let [rows [{:order {:coin "@230"
                       :oid 307891000622
                       :side "B"
                       :origSz "0.500"
                       :remainingSz "0.000"
                       :limitPx "0.000"
                       :orderType "Market"
                       :reduceOnly false
                       :isTrigger false
                       :isPositionTpsl false
                       :timestamp 1700000000000}
               :status "filled"
               :statusTimestamp 1700000005000}]
        content (order-history-tab/order-history-tab-content rows {:sort {:column "Time" :direction :desc}
                                                                   :status-filter :all
                                                                   :loading? false
                                                                   :market-by-key {"spot:@230" {:coin "@230"
                                                                                                :symbol "SOL/USDC"
                                                                                                :base "SOL"
                                                                                                :market-type :spot}}})
        strings (set (hiccup/collect-strings content))]
    (is (contains? strings "SOL"))
    (is (not (contains? strings "@230")))))

(deftest order-history-formatting-distinguishes-market-price-and-filled-size-placeholder-test
  (let [market-row (order-history-tab/normalize-order-history-row
                    {:order {:coin "NVDA"
                             :oid 1
                             :side "B"
                             :origSz "2.0"
                             :remainingSz "1.0"
                             :limitPx "0"
                             :orderType "Market"}
                     :status "filled"
                     :statusTimestamp 1700000000000})
        unfilled-limit-row (order-history-tab/normalize-order-history-row
                            {:order {:coin "PUMP"
                                     :oid 2
                                     :side "A"
                                     :origSz "3.0"
                                     :remainingSz "3.0"
                                     :limitPx "0.0012"
                                     :orderType "Limit"}
                             :status "open"
                             :statusTimestamp 1700000000100})
        filled-row-with-sz-fallback (order-history-tab/normalize-order-history-row
                                     {:order {:coin "PUMP"
                                              :oid 3
                                              :side "A"
                                              :origSz "11273"
                                              :sz "0.0"
                                              :limitPx "0.001772"
                                              :orderType "Limit"}
                                      :status "filled"
                                      :statusTimestamp 1700000000100})]
    (is (= "Market" (order-history-tab/format-order-history-price market-row)))
    (is (= "--" (order-history-tab/format-order-history-filled-size (:filled-size unfilled-limit-row))))
    (is (= "11,273" (order-history-tab/format-order-history-filled-size (:filled-size filled-row-with-sz-fallback))))
    (is (= "No" (order-history-tab/format-order-history-reduce-only (assoc market-row :reduce-only false))))
    (is (= "N/A" (order-history-tab/format-order-history-trigger market-row)))))

(deftest order-history-normalize-row-supports-top-level-history-shapes-test
  (let [row (order-history-tab/normalize-order-history-row
             {:coin "ETH"
              :orderId "abc-123"
              :side "A"
              :direction "close long"
              :origSz "4.0"
              :remainingSz "1.5"
              :limitPx "2500.5"
              :orderStatus "triggered"
              :statusTime 1700000000100
              :reduceOnly true})]
    (is (= "ETH" (:coin row)))
    (is (= "abc-123" (:oid row)))
    (is (= "close long" (:direction row)))
    (is (= "Yes" (order-history-tab/format-order-history-reduce-only row)))
    (is (= "2.5" (order-history-tab/format-order-history-filled-size (:filled-size row))))))
