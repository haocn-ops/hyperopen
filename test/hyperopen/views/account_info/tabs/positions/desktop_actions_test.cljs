(ns hyperopen.views.account-info.tabs.positions.desktop-actions-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.account.history.position-margin :as position-margin]
            [hyperopen.account.history.position-reduce :as position-reduce]
            [hyperopen.account.history.position-tpsl :as position-tpsl]
            [hyperopen.views.account-info.test-support.fixtures :as fixtures]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.account-info.tabs.positions :as positions-tab]
            [hyperopen.views.account-info.tabs.positions.test-support :as test-support]))

(use-fixtures :each test-support/reset-positions-sort-cache-fixture)

(deftest position-table-header-close-all-dispatches-placeholder-action-test
  (let [header-node (positions-tab/position-table-header fixtures/default-sort-state)
        close-all-button (hiccup/find-first-node
                          header-node
                          #(and (= :button (first %))
                                (contains? (hiccup/direct-texts %) "Close All")))]
    (is (some? close-all-button))
    (is (= [[:actions/trigger-close-all-positions]]
           (get-in close-all-button [1 :on :click])))))

(deftest position-row-coin-cell-dispatches-select-asset-action-test
  (let [row-node (positions-tab/position-row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500"))
        coin-cell (first (vec (hiccup/node-children row-node)))
        coin-button (hiccup/find-first-node coin-cell #(= :button (first %)))]
    (is (some? coin-button))
    (is (= [[:actions/select-asset "xyz:NVDA"]]
           (get-in coin-button [1 :on :click])))))

(deftest position-row-tpsl-cell-dispatches-open-modal-action-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        row-node (positions-tab/position-row row-data)
        row-cells (vec (hiccup/node-children row-node))
        tpsl-cell (nth row-cells 10)
        action-button (hiccup/find-first-node tpsl-cell #(= :button (first %)))
        click-actions (get-in action-button [1 :on :click])]
    (is (vector? click-actions))
    (is (= :actions/open-position-tpsl-modal
           (first (first click-actions))))
    (is (= row-data
           (second (first click-actions))))
    (is (= :event.currentTarget/bounds
           (nth (first click-actions) 2)))
    (is (= "true" (get-in action-button [1 :data-position-tpsl-trigger])))))

(deftest position-row-reduce-cell-dispatches-open-popover-action-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        row-node (positions-tab/position-row row-data)
        row-cells (vec (hiccup/node-children row-node))
        reduce-cell (nth row-cells 9)
        action-button (hiccup/find-first-node reduce-cell #(= :button (first %)))
        click-actions (get-in action-button [1 :on :click])]
    (is (= #{"Reduce"} (set (hiccup/collect-strings reduce-cell))))
    (is (vector? click-actions))
    (is (= :actions/open-position-reduce-popover
           (first (first click-actions))))
    (is (= row-data
           (second (first click-actions))))
    (is (= :event.currentTarget/bounds
           (nth (first click-actions) 2)))
    (is (= "true" (get-in action-button [1 :data-position-reduce-trigger])))))

(deftest position-row-reduce-cell-renders-text-button-without-btn-chrome-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        row-node (positions-tab/position-row row-data)
        row-cells (vec (hiccup/node-children row-node))
        reduce-cell (nth row-cells 9)
        action-button (hiccup/find-first-node reduce-cell #(= :button (first %)))
        button-classes (hiccup/node-class-set action-button)]
    (is (some? action-button))
    (is (contains? (set (hiccup/collect-strings reduce-cell)) "Reduce"))
    (is (contains? button-classes "inline-flex"))
    (is (not (contains? button-classes "btn")))
    (is (not (contains? button-classes "btn-spectate")))))

(deftest position-row-margin-cell-dispatches-open-modal-action-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        row-node (positions-tab/position-row row-data)
        row-cells (vec (hiccup/node-children row-node))
        margin-cell (nth row-cells 7)
        action-button (hiccup/find-first-node margin-cell #(= :button (first %)))
        edit-icon-node (hiccup/find-first-node margin-cell #(= :svg (first %)))
        value-node (hiccup/find-first-node margin-cell #(and (= :span (first %))
                                                              (contains? (hiccup/node-class-set %) "select-text")))
        click-actions (get-in action-button [1 :on :click])
        margin-strings (set (hiccup/collect-strings margin-cell))]
    (is (contains? margin-strings "$12.00"))
    (is (some? value-node))
    (is (contains? (hiccup/node-class-set action-button) "h-6"))
    (is (contains? (hiccup/node-class-set action-button) "w-6"))
    (is (contains? (hiccup/node-class-set edit-icon-node) "h-4"))
    (is (contains? (hiccup/node-class-set edit-icon-node) "w-4"))
    (is (vector? click-actions))
    (is (= :actions/open-position-margin-modal
           (first (first click-actions))))
    (is (= row-data
           (second (first click-actions))))
    (is (= :event.currentTarget/bounds
           (nth (first click-actions) 2)))
    (is (= "true" (get-in action-button [1 :data-position-margin-trigger])))
    (is (= "Edit Margin" (get-in action-button [1 :aria-label])))))

(deftest positions-tab-content-read-only-mode-omits-desktop-mutation-affordances-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        content (test-support/render-positions-tab-from-rows [row-data]
                                                             fixtures/default-sort-state
                                                             nil
                                                             nil
                                                             nil
                                                             {:direction-filter :all
                                                              :read-only? true})
        header-strings (set (hiccup/collect-strings (hiccup/tab-header-node content)))
        row-node (hiccup/first-viewport-row content)
        row-cells (vec (hiccup/node-children row-node))
        reduce-button (hiccup/find-first-node row-node #(and (= :button (first %))
                                                             (contains? (hiccup/direct-texts %) "Reduce")))
        margin-edit-button (hiccup/find-first-node row-node #(= "Edit Margin" (get-in % [1 :aria-label])))
        tpsl-edit-button (hiccup/find-first-node row-node #(= "Edit TP/SL" (get-in % [1 :aria-label])))
        row-buttons (hiccup/find-all-nodes row-node #(= :button (first %)))]
    (is (not (contains? header-strings "Close All")))
    (is (= 10 (count row-cells)))
    (is (nil? reduce-button))
    (is (nil? margin-edit-button))
    (is (nil? tpsl-edit-button))
    (is (= 1 (count row-buttons)))))

(deftest position-row-renders-inline-margin-modal-for-active-row-key-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        margin-modal (position-margin/from-position-row {} row-data)
        row-node (positions-tab/position-row row-data nil nil margin-modal)
        panel-node (hiccup/find-first-node
                    row-node
                    #(= "true" (get-in % [1 :data-position-margin-surface])))]
    (is (some? panel-node))
    (is (contains? (hiccup/node-class-set panel-node) "fixed"))
    (is (contains? (hiccup/node-class-set panel-node) "space-y-3"))))

(deftest position-row-margin-slider-hides-overlapping-notch-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        margin-modal (assoc (position-margin/from-position-row {} row-data)
                            :amount-percent-input "25")
        row-node (positions-tab/position-row row-data nil nil margin-modal)
        notch-nodes (hiccup/find-all-nodes
                     row-node
                     #(contains? (hiccup/node-class-set %) "order-size-slider-notch"))
        hidden-notch-count (count (filter #(contains? (hiccup/node-class-set %) "opacity-0")
                                          notch-nodes))]
    (is (seq notch-nodes))
    (is (= 1 hidden-notch-count))))

(deftest position-row-renders-inline-reduce-popover-for-active-row-key-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        popover (position-reduce/from-position-row row-data)
        row-node (positions-tab/position-row row-data nil popover)
        panel-node (hiccup/find-first-node
                    row-node
                    #(= "true" (get-in % [1 :data-position-reduce-surface])))]
    (is (some? panel-node))
    (is (contains? (hiccup/node-class-set panel-node) "fixed"))
    (is (contains? (hiccup/node-class-set panel-node) "space-y-3"))))

(deftest position-row-reduce-popover-mid-button-dispatches-mid-price-action-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        popover (assoc (position-reduce/from-position-row row-data)
                       :close-type :limit
                       :mid-price "10")
        row-node (positions-tab/position-row row-data nil popover)
        mid-button (hiccup/find-first-node
                    row-node
                    #(and (= :button (first %))
                          (contains? (hiccup/direct-texts %) "MID")))]
    (is (some? mid-button))
    (is (= [[:actions/set-position-reduce-limit-price-to-mid]]
           (get-in mid-button [1 :on :click])))
    (is (false? (boolean (get-in mid-button [1 :disabled]))))))

(deftest position-row-reduce-popover-mid-button-disabled-without-mid-price-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        popover (assoc (position-reduce/from-position-row row-data)
                       :close-type :limit
                       :mid-price nil)
        row-node (positions-tab/position-row row-data nil popover)
        mid-button (hiccup/find-first-node
                    row-node
                    #(and (= :button (first %))
                          (contains? (hiccup/direct-texts %) "MID")))]
    (is (some? mid-button))
    (is (nil? (get-in mid-button [1 :on :click])))
    (is (true? (boolean (get-in mid-button [1 :disabled]))))))

(deftest position-row-renders-inline-position-tpsl-panel-for-active-row-key-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        modal (position-tpsl/from-position-row row-data)
        row-node (positions-tab/position-row row-data modal)
        panel-node (hiccup/find-first-node
                    row-node
                    #(= "true" (get-in % [1 :data-position-tpsl-surface])))]
    (is (some? panel-node))
    (is (contains? (hiccup/node-class-set panel-node) "fixed"))
    (is (not (contains? (hiccup/node-class-set panel-node) "absolute")))
    (is (contains? (hiccup/node-class-set panel-node) "overflow-y-auto"))
    (is (not (contains? (hiccup/node-class-set panel-node) "inset-0")))))

(deftest position-row-does-not-render-inline-position-tpsl-panel-for-different-row-key-test
  (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        other-row (fixtures/sample-position-row "xyz:TSLA" 10 "0.500")
        modal (position-tpsl/from-position-row other-row)
        row-node (positions-tab/position-row row-data modal)
        panel-node (hiccup/find-first-node
                    row-node
                    #(= "true" (get-in % [1 :data-position-tpsl-surface])))]
    (is (nil? panel-node))))
