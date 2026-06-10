(ns hyperopen.views.account-info.tabs.trade-history.table-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup-selectors :as selectors]
            [hyperopen.views.account-info.test-support.fixtures :as fixtures]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.account-info.tabs.trade-history :as trade-history-tab]
            [hyperopen.views.account-info.tabs.trade-history.shared :as trade-shared]
            [hyperopen.views.account-info-view :as view]))

(deftest trade-history-pagination-renders-only-current-page-rows-test
  (let [rows (mapv fixtures/trade-history-row (range 55))
        content (trade-history-tab/trade-history-table rows {:page-size 25
                                                             :page 2
                                                             :page-input "2"})
        viewport (hiccup/tab-rows-viewport-node content)
        rendered-rows (vec (hiccup/node-children viewport))
        all-strings (set (hiccup/collect-strings content))]
    (is (= 25 (count rendered-rows)))
    (is (contains? all-strings "Page 2 of 3"))
    (is (contains? all-strings "Total: 55"))))

(deftest trade-history-pagination-controls-disable-prev-next-at-edges-test
  (let [rows (mapv fixtures/trade-history-row (range 51))
        first-page (trade-history-tab/trade-history-table rows {:page-size 25
                                                                :page 1
                                                                :page-input "1"})
        first-prev (hiccup/find-first-node first-page selectors/prev-button-predicate)
        first-next (hiccup/find-first-node first-page selectors/next-button-predicate)
        last-page (trade-history-tab/trade-history-table rows {:page-size 25
                                                               :page 3
                                                               :page-input "3"})
        last-prev (hiccup/find-first-node last-page selectors/prev-button-predicate)
        last-next (hiccup/find-first-node last-page selectors/next-button-predicate)]
    (is (= true (get-in first-prev [1 :disabled])))
    (is (not= true (get-in first-next [1 :disabled])))
    (is (not= true (get-in last-prev [1 :disabled])))
    (is (= true (get-in last-next [1 :disabled])))))

(deftest trade-history-pagination-controls-wire-actions-test
  (let [rows (mapv fixtures/trade-history-row (range 12))
        content (trade-history-tab/trade-history-table rows {:page-size 25
                                                             :page 1
                                                             :page-input "4"})
        page-size-select (hiccup/find-first-node content (selectors/select-id-predicate "trade-history-page-size"))
        jump-input (hiccup/find-first-node content (selectors/input-id-predicate "trade-history-page-input"))
        go-button (hiccup/find-first-node content selectors/go-button-predicate)]
    (is (= [[:actions/set-trade-history-page-size [:event.target/value]]]
           (get-in page-size-select [1 :on :change])))
    (is (= [[:actions/set-trade-history-page-input [:event.target/value]]]
           (get-in jump-input [1 :on :input])))
    (is (= [[:actions/set-trade-history-page-input [:event.target/value]]]
           (get-in jump-input [1 :on :change])))
    (is (= [[:actions/handle-trade-history-page-input-keydown [:event/key] 1]]
           (get-in jump-input [1 :on :keydown])))
    (is (= [[:actions/apply-trade-history-page-input 1]]
           (get-in go-button [1 :on :click])))))

(deftest trade-history-direction-filter-controls-wire-actions-test
  (let [rows [{:tid 1
               :coin "LONGCOIN"
               :side "B"
               :sz "1.0"
               :px "100.0"
               :fee "0.1"
               :time 1700000001000}
              {:tid 2
               :coin "SHORTCOIN"
               :side "A"
               :sz "1.0"
               :px "99.0"
               :fee "0.1"
               :time 1700000000000}]
        panel-state (-> fixtures/sample-account-info-state
                        (assoc-in [:account-info :selected-tab] :trade-history)
                        (assoc-in [:account-info :trade-history]
                                  {:sort {:column "Time" :direction :desc}
                                   :direction-filter :short
                                   :coin-search "nv"
                                   :filter-open? true
                                   :page-size 25
                                   :page 1
                                   :page-input "1"})
                        (assoc-in [:orders :fills] rows))
        panel (view/account-info-panel panel-state)
        filter-button (hiccup/find-first-node panel #(and (contains? (hiccup/direct-texts %) "Short")
                                                          (= [[:actions/toggle-trade-history-direction-filter-open]]
                                                             (get-in % [1 :on :click]))))
        search-input (hiccup/find-first-node panel #(= [[:actions/set-account-info-coin-search :trade-history [:event.target/value]]]
                                                        (get-in % [1 :on :input])))
        short-option (hiccup/find-first-node panel #(and (contains? (hiccup/direct-texts %) "Short")
                                                         (= [[:actions/set-trade-history-direction-filter :short]]
                                                            (get-in % [1 :on :click]))))
        long-option (hiccup/find-first-node panel #(and (contains? (hiccup/direct-texts %) "Long")
                                                        (= [[:actions/set-trade-history-direction-filter :long]]
                                                           (get-in % [1 :on :click]))))]
    (is (some? filter-button))
    (is (some? search-input))
    (is (= "nv" (get-in search-input [1 :value])))
    (is (some? short-option))
    (is (some? long-option))
    (is (= [[:actions/toggle-trade-history-direction-filter-open]]
           (get-in filter-button [1 :on :click])))
    (is (= [[:actions/set-trade-history-direction-filter :short]]
           (get-in short-option [1 :on :click])))
    (is (= [[:actions/set-trade-history-direction-filter :long]]
           (get-in long-option [1 :on :click])))))

(deftest trade-history-pagination-clamps-page-when-data-shrinks-test
  (let [rows (mapv fixtures/trade-history-row (range 10))
        content (trade-history-tab/trade-history-table rows {:page-size 25
                                                             :page 4
                                                             :page-input "4"})
        viewport (hiccup/tab-rows-viewport-node content)
        jump-input (hiccup/find-first-node content (selectors/input-id-predicate "trade-history-page-input"))
        all-strings (set (hiccup/collect-strings content))]
    (is (= 10 (count (vec (hiccup/node-children viewport)))))
    (is (contains? all-strings "Page 1 of 1"))
    (is (= "1" (get-in jump-input [1 :value])))))

(deftest trade-history-table-renders-mobile-summary-cards-with-inline-expansion-test
  (let [fills [{:tid 7
                :coin "xyz:SILVER"
                :side "A"
                :sz "0.52"
                :px "95.242"
                :tradeValue "49.53"
                :fee "0.00"
                :closedPnl "0.19"
                :time 1700000000000
                :hash "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"}
               {:tid 8
                :coin "BTC"
                :side "B"
                :sz "1.25"
                :px "100.5"
                :fee "0.05"
                :time 1700000001000}]
        expanded-row-id (trade-shared/trade-history-row-id (first fills))
        collapsed-row-id (trade-shared/trade-history-row-id (second fills))
        content (trade-history-tab/trade-history-table fills {:page-size 25
                                                              :page 1
                                                              :page-input "1"
                                                              :mobile-expanded-card {:trade-history expanded-row-id}})
        mobile-viewport (hiccup/find-by-data-role content "trade-history-mobile-cards-viewport")
        mobile-cards (->> (hiccup/node-children mobile-viewport)
                          (filter vector?)
                          vec)
        expanded-card (hiccup/find-by-data-role content (str "mobile-trade-history-card-" expanded-row-id))
        collapsed-card (hiccup/find-by-data-role content (str "mobile-trade-history-card-" collapsed-row-id))
        expanded-button (first (vec (hiccup/node-children expanded-card)))
        collapsed-button (first (vec (hiccup/node-children collapsed-card)))
        expanded-button-classes (hiccup/node-class-set expanded-button)
        summary-grid (hiccup/find-first-node expanded-button #(contains? (hiccup/node-class-set %) "grid-cols-[minmax(0,1fr)_minmax(0,0.95fr)_minmax(0,0.72fr)_auto]"))
        expanded-button-strings (set (hiccup/collect-strings expanded-button))
        expanded-strings (set (hiccup/collect-strings expanded-card))
        collapsed-strings (set (hiccup/collect-strings collapsed-card))
        namespace-chip (hiccup/find-first-node expanded-card #(and (= :span (first %))
                                                                   (contains? (hiccup/direct-texts %) "xyz")))
        links (hiccup/find-all-nodes expanded-card #(= :a (first %)))
        time-link (first links)
        pnl-link (second links)]
    (is (some? mobile-viewport))
    (is (= 3 (count mobile-cards)))
    (is (= true (get-in expanded-button [1 :aria-expanded])))
    (is (= [[:actions/toggle-account-info-mobile-card :trade-history expanded-row-id]]
           (get-in expanded-button [1 :on :click])))
    (is (contains? expanded-button-classes "px-3.5"))
    (is (contains? expanded-button-classes "hover:bg-[#0c1b24]"))
    (is (contains? (hiccup/node-class-set expanded-card) "bg-[#08161f]"))
    (is (contains? (hiccup/node-class-set expanded-card) "border-ho-border-accent-muted"))
    (is (not (contains? (hiccup/node-class-set expanded-card) "bg-[#0f1920]")))
    (is (some? summary-grid))
    (is (some? namespace-chip))
    (is (contains? (hiccup/node-class-set namespace-chip) "bg-[#242924]"))
    (is (contains? (hiccup/node-class-set namespace-chip) "border"))
    (is (contains? (hiccup/node-class-set namespace-chip) "rounded-lg"))
    (is (contains? expanded-button-strings "Coin"))
    (is (contains? expanded-button-strings "Direction"))
    (is (contains? expanded-button-strings "Price"))
    (is (not (contains? expanded-button-strings "Time")))
    (is (not (contains? expanded-button-strings "Size")))
    (is (contains? expanded-strings "Coin"))
    (is (contains? expanded-strings "Time"))
    (is (contains? expanded-strings "Direction"))
    (is (contains? expanded-strings "Price"))
    (is (contains? expanded-strings "Size"))
    (is (contains? expanded-strings "Trade Value"))
    (is (contains? expanded-strings "Fee"))
    (is (contains? expanded-strings "Closed PNL"))
    (is (some? time-link))
    (is (some? pnl-link))
    (is (= "https://app.hyperliquid.xyz/explorer/tx/0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
           (get-in time-link [1 :href])))
    (is (= "https://app.hyperliquid.xyz/explorer/tx/0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
           (get-in pnl-link [1 :href])))
    (is (= false (get-in collapsed-button [1 :aria-expanded])))
    (is (contains? collapsed-strings "BTC"))
    (is (not (contains? collapsed-strings "Trade Value")))))
