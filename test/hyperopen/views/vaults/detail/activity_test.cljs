(ns hyperopen.views.vaults.detail.activity-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.vaults.detail.activity :as activity]))

(def base-activity-panel-props
  {:activity-tabs [{:value :performance-metrics
                    :label "Performance Metrics"
                    :count 1}
                   {:value :positions
                    :label "Positions"
                    :count 2}
                   {:value :open-orders
                    :label "Open Orders"
                    :count 100}
                   {:value :balances
                    :label "Balances"
                    :count 0}]
   :activity-table-config {:positions {:supports-direction-filter? true
                                       :columns [{:id :coin :label "Coin"}
                                                 {:id :size :label "Size"}]}
                           :balances {:supports-direction-filter? false
                                      :columns [{:id :coin :label "Coin"}]}
                           :trade-history {:columns [{:id :time-ms :label "Time"}]}
                           :funding-history {:columns [{:id :time-ms :label "Time"}]}
                           :order-history {:columns [{:id :time-ms :label "Time"}]}
                           :deposits-withdrawals {:columns [{:id :time-ms :label "Time"}]}}
   :activity-direction-filter :all
   :activity-filter-open? false
   :activity-filter-options [{:value :all :label "All"}
                             {:value :long :label "Long"}
                             {:value :short :label "Short"}]
   :activity-sort-state-by-tab {}
   :activity-loading {}
   :activity-errors {}
   :activity-balances []
   :activity-positions []
   :activity-open-orders []
   :activity-twaps []
   :activity-fills []
   :activity-funding-history []
   :activity-order-history []
   :activity-deposits-withdrawals []
   :activity-depositors []})

(defn- find-button-by-click
  [view action]
  (hiccup/find-first-node view
                          #(and (= :button (first %))
                                (= action (get-in % [1 :on :click])))))

(deftest activity-panel-routes-selected-tab-and-preserves-tab-button-contracts-test
  (let [positions-view (activity/activity-panel
                        (assoc base-activity-panel-props
                               :selected-activity-tab :positions))
        performance-tab (find-button-by-click positions-view
                                              [[:actions/set-vault-detail-activity-tab :performance-metrics]])
        positions-tab (find-button-by-click positions-view
                                            [[:actions/set-vault-detail-activity-tab :positions]])
        open-orders-tab (find-button-by-click positions-view
                                              [[:actions/set-vault-detail-activity-tab :open-orders]])
        balances-tab (find-button-by-click positions-view
                                           [[:actions/set-vault-detail-activity-tab :balances]])
        positions-text (set (hiccup/collect-strings positions-view))
        performance-card-in-positions (hiccup/find-by-data-role positions-view
                                                                "vault-detail-performance-metrics-card")
        metrics-view (activity/activity-panel
                      (assoc base-activity-panel-props
                             :selected-activity-tab :performance-metrics
                             :performance-metrics {:benchmark-selected? false
                                                   :groups []}))
        performance-card (hiccup/find-by-data-role metrics-view
                                                   "vault-detail-performance-metrics-card")
        activity-panel (hiccup/find-by-data-role metrics-view
                                                 "vault-detail-activity-panel")]
    (is (= ["Performance Metrics (1)"] (hiccup/collect-strings performance-tab)))
    (is (= ["Positions (2)"] (hiccup/collect-strings positions-tab)))
    (is (= ["Open Orders (100+)"] (hiccup/collect-strings open-orders-tab)))
    (is (= ["Balances"] (hiccup/collect-strings balances-tab)))
    (is (contains? (hiccup/node-class-set positions-tab) "text-ho-text"))
    (is (contains? (hiccup/node-class-set performance-tab) "text-[#949e9c]"))
    (is (contains? (hiccup/node-class-set performance-tab) "hover:text-ho-text"))
    (is (contains? positions-text "No active positions."))
    (is (nil? performance-card-in-positions))
    (is (some? performance-card))
    (is (contains? (hiccup/node-class-set activity-panel) "max-h-[75vh]"))))

(deftest activity-panel-renders-filter-states-and-fallback-tab-message-test
  (let [positions-view (activity/activity-panel
                        (-> base-activity-panel-props
                            (assoc :selected-activity-tab :positions
                                   :activity-filter-open? true
                                   :activity-direction-filter :long)))
        filter-toggle (find-button-by-click positions-view
                                            [[:actions/toggle-vault-detail-activity-filter-open]])
        short-option (find-button-by-click positions-view
                                           [[:actions/set-vault-detail-activity-direction-filter :short]])
        balances-view (activity/activity-panel
                       (-> base-activity-panel-props
                           (assoc :selected-activity-tab :balances)))
        disabled-filter-toggle (find-button-by-click balances-view
                                                     [[:actions/toggle-vault-detail-activity-filter-open]])
        fallback-view (activity/activity-panel
                       (-> base-activity-panel-props
                           (assoc :selected-activity-tab :not-supported
                                  :activity-filter-open? true
                                  :activity-loading {:trade-history true}
                                  :activity-errors {:trade-history "Should not render."}
                                  :activity-fills [{:coin "ETH"}])))
        fallback-text (set (hiccup/collect-strings fallback-view))]
    (is (some? filter-toggle))
    (is (not (true? (get-in filter-toggle [1 :disabled]))))
    (is (some? short-option))
    (is (some? disabled-filter-toggle))
    (is (true? (get-in disabled-filter-toggle [1 :disabled])))
    (is (contains? fallback-text "This activity stream is not available yet for vaults."))
    (is (not (contains? fallback-text "Should not render.")))
    (is (nil? (hiccup/find-first-node fallback-view #(= :table (first %)))))))

(deftest activity-panel-position-coin-cell-navigates-to-trade-market-test
  (let [view (activity/activity-panel
              (-> base-activity-panel-props
                  (assoc :selected-activity-tab :positions
                         :activity-positions [{:coin "xyz:GOLD"
                                               :leverage 20
                                               :size 1.25
                                               :side-key :long
                                               :position-value 2500
                                               :entry-price 50000
                                               :mark-price 50125
                                               :pnl 125
                                               :roe 0.05
                                               :liq-price nil
                                               :margin nil
                                               :funding -4.2}])))
        coin-button (hiccup/find-by-data-role view "vault-detail-position-coin-select")
        wrapping-cell (hiccup/find-first-node view
                                              #(and (= :td (first %))
                                                    (some #{"xyz:GOLD"} (hiccup/collect-strings %))))]
    (is (some? coin-button))
    (is (= "button" (get-in coin-button [1 :type])))
    (is (= [[:actions/select-asset "xyz:GOLD"]
            [:actions/navigate "/trade/xyz:GOLD"]]
           (get-in coin-button [1 :on :click])))
    (is (contains? (hiccup/node-class-set coin-button) "bg-transparent"))
    (is (contains? (hiccup/node-class-set coin-button) "p-0"))
    (is (contains? (hiccup/node-class-set coin-button) "text-left"))
    (is (contains? (hiccup/node-class-set coin-button) "focus:outline-none"))
    (is (contains? (hiccup/node-class-set coin-button) "focus:ring-0"))
    (is (contains? (hiccup/node-class-set coin-button) "focus:ring-offset-0"))
    (is (contains? (set (hiccup/collect-strings coin-button)) "20x"))
    (is (contains? (hiccup/node-class-set wrapping-cell) "whitespace-nowrap"))
    (is (= "12px" (get-in wrapping-cell [1 :style :padding-left])))
    (is (str/includes? (get-in wrapping-cell [1 :style :background])
                       "linear-gradient(90deg,rgb(31,166,125)"))))
