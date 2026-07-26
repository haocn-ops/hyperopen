(ns hyperopen.views.account-info.tabs.order-history.table-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.test-support.hiccup-selectors :as selectors]
            [hyperopen.views.account-info.test-support.fixtures :as fixtures]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.account-info.tabs.order-history :as order-history-tab]
            [hyperopen.views.account-info-view :as view]))

(defn- reset-order-history-sort-cache-fixture
  [f]
  (order-history-tab/reset-order-history-sort-cache!)
  (f)
  (order-history-tab/reset-order-history-sort-cache!))

(use-fixtures :each reset-order-history-sort-cache-fixture)

(deftest order-history-direction-filter-controls-and-filtering-test
  (let [rows [{:order {:coin "NVDA"
                       :oid 1
                       :side "B"
                       :origSz "1.0"
                       :remainingSz "0.0"
                       :limitPx "0"
                       :orderType "Market"}
               :status "filled"
               :statusTimestamp 1700000000000}
              {:order {:coin "PUMP"
                       :oid 2
                       :side "A"
                       :origSz "1.0"
                       :remainingSz "0.0"
                       :limitPx "0.001"
                       :orderType "Limit"}
               :status "canceled"
               :statusTimestamp 1699999999000}]
        filtered-content (order-history-tab/order-history-tab-content rows {:sort {:column "Time" :direction :desc}
                                                                            :status-filter :long
                                                                            :loading? false})
        filtered-strings (set (hiccup/collect-strings filtered-content))
        panel-state (-> fixtures/sample-account-info-state
                        (assoc-in [:account-info :selected-tab] :order-history)
                        (assoc-in [:account-info :order-history]
                                  {:sort {:column "Time" :direction :desc}
                                   :status-filter :short
                                   :filter-open? true
                                   :loading? false
                                   :error nil
                                   :request-id 1})
                        (assoc-in [:orders :order-history] rows))
        panel (view/account-info-panel panel-state)
        filter-button (hiccup/find-first-node panel #(and (contains? (hiccup/direct-texts %) "Short")
                                                          (= [[:actions/toggle-order-history-filter-open]]
                                                             (get-in % [1 :on :click]))))
        short-option (hiccup/find-first-node panel #(and (contains? (hiccup/direct-texts %) "Short")
                                                         (= [[:actions/set-order-history-status-filter :short]]
                                                            (get-in % [1 :on :click]))))]
    (is (contains? filtered-strings "NVDA"))
    (is (not (contains? filtered-strings "PUMP")))
    (is (some? filter-button))
    (is (some? short-option))
    (is (= [[:actions/toggle-order-history-filter-open]]
           (get-in filter-button [1 :on :click])))
    (is (= [[:actions/set-order-history-status-filter :short]]
           (get-in short-option [1 :on :click])))))

(deftest order-history-pagination-renders-only-current-page-rows-test
  (let [rows (mapv fixtures/order-history-row (range 55))
        content (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                             :status-filter :all
                                                             :page-size 25
                                                             :page 2
                                                             :page-input "2"
                                                             :loading? false})
        viewport (hiccup/tab-rows-viewport-node content)
        rendered-rows (vec (hiccup/node-children viewport))
        all-strings (set (hiccup/collect-strings content))]
    (is (= 25 (count rendered-rows)))
    (is (contains? all-strings "Page 2 of 3"))
    (is (contains? all-strings "Total: 55"))))

(deftest order-history-pagination-controls-disable-prev-next-at-edges-test
  (let [rows (mapv fixtures/order-history-row (range 51))
        first-page (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                                :status-filter :all
                                                                :page-size 25
                                                                :page 1
                                                                :page-input "1"
                                                                :loading? false})
        first-prev (hiccup/find-first-node first-page selectors/prev-button-predicate)
        first-next (hiccup/find-first-node first-page selectors/next-button-predicate)
        last-page (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                               :status-filter :all
                                                               :page-size 25
                                                               :page 3
                                                               :page-input "3"
                                                               :loading? false})
        last-prev (hiccup/find-first-node last-page selectors/prev-button-predicate)
        last-next (hiccup/find-first-node last-page selectors/next-button-predicate)]
    (is (= true (get-in first-prev [1 :disabled])))
    (is (not= true (get-in first-next [1 :disabled])))
    (is (not= true (get-in last-prev [1 :disabled])))
    (is (= true (get-in last-next [1 :disabled])))))

(deftest order-history-pagination-controls-wire-actions-test
  (let [rows (mapv fixtures/order-history-row (range 12))
        content (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                             :status-filter :all
                                                             :page-size 25
                                                             :page 1
                                                             :page-input "4"
                                                             :loading? false})
        page-size-select (hiccup/find-first-node content (selectors/select-id-predicate "order-history-page-size"))
        jump-input (hiccup/find-first-node content (selectors/input-id-predicate "order-history-page-input"))
        go-button (hiccup/find-first-node content selectors/go-button-predicate)]
    (is (= [[:actions/set-order-history-page-size [:event.target/value]]]
           (get-in page-size-select [1 :on :change])))
    (is (= [[:actions/set-order-history-page-input [:event.target/value]]]
           (get-in jump-input [1 :on :input])))
    (is (= [[:actions/set-order-history-page-input [:event.target/value]]]
           (get-in jump-input [1 :on :change])))
    (is (= [[:actions/handle-order-history-page-input-keydown [:event/key] 1]]
           (get-in jump-input [1 :on :keydown])))
    (is (= [[:actions/apply-order-history-page-input 1]]
           (get-in go-button [1 :on :click])))))

(deftest order-history-pagination-clamps-page-when-data-shrinks-test
  (let [rows (mapv fixtures/order-history-row (range 10))
        content (order-history-tab/order-history-table rows {:sort {:column "Time" :direction :desc}
                                                             :status-filter :all
                                                             :page-size 25
                                                             :page 4
                                                             :page-input "4"
                                                             :loading? false})
        viewport (hiccup/tab-rows-viewport-node content)
        jump-input (hiccup/find-first-node content (selectors/input-id-predicate "order-history-page-input"))
        all-strings (set (hiccup/collect-strings content))]
    (is (= 10 (count (vec (hiccup/node-children viewport)))))
    (is (contains? all-strings "Page 1 of 1"))
    (is (= "1" (get-in jump-input [1 :value])))))
