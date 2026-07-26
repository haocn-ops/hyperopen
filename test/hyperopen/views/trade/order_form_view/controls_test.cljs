(ns hyperopen.views.trade.order-form-view.controls-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trade.order-form.test-support :refer [base-state
                                                                   button-node-by-click-action
                                                                   collect-strings
                                                                   find-all-nodes
                                                                   find-first-node]]
            [hyperopen.views.trade.order-form-controls :as controls]
            [hyperopen.views.trade.order-form-handlers :as handlers]
            [hyperopen.views.trade.order-form-vm :as order-form-vm]))

(deftest price-row-renders-mid-action-and-focus-handlers-test
  (let [state (base-state {:type :limit :price ""})
        vm (order-form-vm/order-form-vm state)
        price-row (controls/price-row (:price vm)
                                      (:quote-symbol vm)
                                      (:price (handlers/build-handlers)))
        price-input (find-first-node price-row
                                     (fn [node]
                                       (let [attrs (when (map? (second node)) (second node))]
                                         (and (= :input (first node))
                                              (= "Price (USDC)" (:placeholder attrs))))))
        mid-button (find-first-node price-row
                                    (fn [node]
                                      (and (= :button (first node))
                                           (= [[:actions/set-order-price-to-mid]]
                                              (get-in node [1 :on :click])))))
        price-attrs (second price-input)]
    (is (some? price-input))
    (is (seq (:value price-attrs)))
    (is (= [[:actions/focus-order-price-input]] (get-in price-attrs [:on :focus])))
    (is (= [[:actions/blur-order-price-input]] (get-in price-attrs [:on :blur])))
    (is (some? mid-button))))

(deftest size-row-renders-slider-notches-and-size-unit-actions-test
  (let [state (base-state {:type :limit :size-input-mode :quote}
                          {:size-unit-dropdown-open? true})
        vm (order-form-vm/order-form-vm state)
        size-row (controls/size-row {:size-display (:size-display vm)
                                     :size-input-mode (:size-input-mode vm)
                                     :quote-symbol (:quote-symbol vm)
                                     :base-symbol (:base-symbol vm)
                                     :size-unit-dropdown-open? true
                                     :display-size-percent (:display-size-percent vm)
                                     :size-percent (:size-percent vm)
                                     :notch-overlap-threshold (:notch-overlap-threshold vm)}
                                    (:size (handlers/build-handlers)))
        trigger (find-first-node size-row
                                 (fn [node]
                                   (re-find #"^Size unit:" (or (get-in node [1 :aria-label]) ""))))
        option-buttons (find-all-nodes size-row
                                       (fn [node]
                                         (= :actions/set-order-size-input-mode
                                            (ffirst (get-in node [1 :on :click])))))
        notches (find-all-nodes size-row
                                (fn [node]
                                  (contains? (set (get-in node [1 :class]))
                                             "order-size-slider-notch")))]
    (is (some? trigger))
    (is (some #{"USDC"} (collect-strings trigger)))
    (is (= 2 (count option-buttons)))
    (is (= #{[[:actions/set-order-size-input-mode :quote]]
             [[:actions/set-order-size-input-mode :base]]}
           (set (map #(get-in % [1 :on :click]) option-buttons))))
    (is (= 5 (count notches)))))

(deftest leverage-row-renders-margin-mode-and-popover-actions-test
  (let [state (base-state {:type :limit}
                          {:margin-mode-dropdown-open? true
                           :leverage-popover-open? true
                           :leverage-draft 18})
        vm (order-form-vm/order-form-vm state)
        ui-state (:order-form-ui state)
        leverage-row (controls/leverage-row state
                                            (get-in state [:order-form :margin-mode])
                                            true
                                            (:margin-mode-dropdown-open? ui-state)
                                            (:leverage-popover-open? ui-state)
                                            (:ui-leverage vm)
                                            (:leverage-draft ui-state)
                                            40
                                            (:leverage (handlers/build-handlers)))
        strings (set (collect-strings leverage-row))
        margin-overlay (find-first-node leverage-row
                                        (fn [node]
                                          (= "Close margin mode menu"
                                             (get-in node [1 :aria-label]))))
        leverage-overlay (find-first-node leverage-row
                                          (fn [node]
                                            (= "Close leverage menu"
                                               (get-in node [1 :aria-label]))))
        confirm-button (button-node-by-click-action leverage-row :actions/confirm-order-ui-leverage)]
    (is (contains? strings "Cross"))
    (is (contains? strings "Adjust Leverage"))
    (is (contains? strings "Maximum leverage"))
    (is (some? margin-overlay))
    (is (some? leverage-overlay))
    (is (some? confirm-button))))
