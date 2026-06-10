(ns hyperopen.views.account-info.tabs.positions.mobile-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.account.history.position-margin :as position-margin]
            [hyperopen.views.account-info.test-support.fixtures :as fixtures]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.account-info.tabs.positions :as positions-tab]
            [hyperopen.views.account-info.tabs.positions.test-support :as test-support]))

(use-fixtures :each test-support/reset-positions-sort-cache-fixture)

(deftest positions-tab-content-renders-mobile-summary-cards-with-inline-expansion-test
  (test-support/with-phone-viewport
    (fn []
      (let [expanded-row (-> (fixtures/sample-position-row "xyz:GOLD" 20 "0.0185" "xyz")
                             (assoc-in [:position :positionValue] "95.55")
                             (assoc-in [:position :entryPx] "5382.4")
                             (assoc-in [:position :markPx] "5164.6")
                             (assoc-in [:position :liquidationPx] "4407.1")
                             (assoc-in [:position :marginUsed] "15.64")
                             (assoc-in [:position :returnOnEquity] "-0.809")
                             (assoc-in [:position :unrealizedPnl] "-4.03")
                             (assoc-in [:position :cumFunding :allTime] "-0.05")
                             (assoc-in [:position :leverage :type] "isolated"))
            collapsed-row (fixtures/sample-position-row "SOL" 10 "0.61")
            expanded-row-id (positions-tab/position-unique-key expanded-row)
            content (test-support/render-positions-tab-from-rows [expanded-row collapsed-row]
                                                                 fixtures/default-sort-state
                                                                 nil
                                                                 nil
                                                                 nil
                                                                 {:direction-filter :all
                                                                  :mobile-expanded-card {:positions expanded-row-id}})
            mobile-viewport (hiccup/find-by-data-role content "positions-mobile-cards-viewport")
            mobile-cards (vec (hiccup/node-children mobile-viewport))
            expanded-card (hiccup/find-by-data-role content (str "mobile-position-card-" expanded-row-id))
            collapsed-card (hiccup/find-by-data-role content (str "mobile-position-card-" (positions-tab/position-unique-key collapsed-row)))
            expanded-button (first (vec (hiccup/node-children expanded-card)))
            collapsed-button (first (vec (hiccup/node-children collapsed-card)))
            expanded-card-classes (hiccup/node-class-set expanded-card)
            expanded-button-classes (hiccup/node-class-set expanded-button)
            expanded-strings (set (hiccup/collect-strings expanded-card))
            collapsed-strings (set (hiccup/collect-strings collapsed-card))
            margin-edit-button (hiccup/find-first-node expanded-card #(= "Edit Margin" (get-in % [1 :aria-label])))
            tpsl-edit-button (hiccup/find-first-node expanded-card #(= "Edit TP/SL" (get-in % [1 :aria-label])))
            close-button (hiccup/find-first-node expanded-card #(and (= :button (first %))
                                                                     (contains? (hiccup/direct-texts %) "Close")))
            margin-button (hiccup/find-first-node expanded-card #(and (= :button (first %))
                                                                      (contains? (hiccup/direct-texts %) "Margin")))
            tpsl-button (hiccup/find-first-node expanded-card #(and (= :button (first %))
                                                                    (contains? (hiccup/direct-texts %) "TP/SL")))
            margin-action (first (get-in margin-edit-button [1 :on :click]))
            tpsl-action (first (get-in tpsl-edit-button [1 :on :click]))
            close-action (first (get-in close-button [1 :on :click]))
            margin-footer-action (first (get-in margin-button [1 :on :click]))
            tpsl-footer-action (first (get-in tpsl-button [1 :on :click]))
            margin-anchor (nth margin-action 2)
            tpsl-anchor (nth tpsl-action 2)
            close-anchor (nth close-action 2)
            margin-footer-anchor (nth margin-footer-action 2)
            tpsl-footer-anchor (nth tpsl-footer-action 2)]
        (is (some? mobile-viewport))
        (is (= 2 (count mobile-cards)))
        (is (= true (get-in expanded-button [1 :aria-expanded])))
        (is (= [[:actions/toggle-account-info-mobile-card :positions expanded-row-id]]
               (get-in expanded-button [1 :on :click])))
        (is (contains? expanded-card-classes "bg-[#08161f]"))
        (is (contains? expanded-card-classes "border-ho-border-accent-muted"))
        (is (not (contains? expanded-card-classes "bg-base-200/70")))
        (is (contains? expanded-button-classes "px-3.5"))
        (is (contains? expanded-button-classes "hover:bg-[#0c1b24]"))
        (is (contains? expanded-strings "Coin"))
        (is (contains? expanded-strings "Size"))
        (is (contains? expanded-strings "PNL (ROE %)"))
        (is (contains? expanded-strings "Entry Price"))
        (is (contains? expanded-strings "Mark Price"))
        (is (contains? expanded-strings "Liq. Price"))
        (is (contains? expanded-strings "Position Value"))
        (is (contains? expanded-strings "Margin"))
        (is (contains? expanded-strings "Funding"))
        (is (contains? expanded-strings "TP/SL"))
        (is (not (contains? expanded-strings "Actions")))
        (is (contains? expanded-strings "Close"))
        (is (contains? expanded-strings "GOLD"))
        (is (contains? expanded-strings "20x"))
        (is (contains? expanded-strings "xyz"))
        (is (contains? (hiccup/node-class-set close-button) "text-trading-green"))
        (is (not (contains? (hiccup/node-class-set close-button) "border")))
        (is (not (contains? (hiccup/node-class-set close-button) "rounded-full")))
        (is (not (contains? (hiccup/node-class-set close-button) "bg-base-100/70")))
        (is (contains? (hiccup/node-class-set margin-edit-button) "h-6"))
        (is (contains? (hiccup/node-class-set margin-edit-button) "w-6"))
        (is (= :actions/open-position-margin-modal
               (first margin-action)))
        (is (= expanded-row
               (second margin-action)))
        (is (map? margin-anchor))
        (is (= 430 (:viewport-width margin-anchor)))
        (is (= 932 (:viewport-height margin-anchor)))
        (is (= "true" (get-in margin-edit-button [1 :data-position-margin-trigger])))
        (is (= :actions/open-position-tpsl-modal
               (first tpsl-action)))
        (is (= expanded-row
               (second tpsl-action)))
        (is (map? tpsl-anchor))
        (is (= 430 (:viewport-width tpsl-anchor)))
        (is (= 932 (:viewport-height tpsl-anchor)))
        (is (= "true" (get-in tpsl-edit-button [1 :data-position-tpsl-trigger])))
        (is (= :actions/open-position-reduce-popover
               (first close-action)))
        (is (map? close-anchor))
        (is (= 430 (:viewport-width close-anchor)))
        (is (= 932 (:viewport-height close-anchor)))
        (is (= :actions/open-position-margin-modal
               (first margin-footer-action)))
        (is (map? margin-footer-anchor))
        (is (= 430 (:viewport-width margin-footer-anchor)))
        (is (= 932 (:viewport-height margin-footer-anchor)))
        (is (= :actions/open-position-tpsl-modal
               (first tpsl-footer-action)))
        (is (map? tpsl-footer-anchor))
        (is (= 430 (:viewport-width tpsl-footer-anchor)))
        (is (= 932 (:viewport-height tpsl-footer-anchor)))
        (is (contains? (hiccup/node-class-set margin-button) "text-trading-green"))
        (is (contains? (hiccup/node-class-set tpsl-button) "text-trading-green"))
        (is (= false (get-in collapsed-button [1 :aria-expanded])))
        (is (contains? collapsed-strings "SOL"))
        (is (not (contains? collapsed-strings "Entry Price")))))))

(deftest positions-tab-content-read-only-mode-omits-mobile-mutation-affordances-test
  (test-support/with-phone-viewport
    (fn []
      (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
            row-id (positions-tab/position-unique-key row-data)
            content (test-support/render-positions-tab-from-rows [row-data]
                                                                 fixtures/default-sort-state
                                                                 nil
                                                                 nil
                                                                 nil
                                                                 {:direction-filter :all
                                                                  :read-only? true
                                                                  :mobile-expanded-card {:positions row-id}})
            expanded-card (hiccup/find-by-data-role content (str "mobile-position-card-" row-id))
            margin-edit-button (hiccup/find-first-node expanded-card #(= "Edit Margin" (get-in % [1 :aria-label])))
            tpsl-edit-button (hiccup/find-first-node expanded-card #(= "Edit TP/SL" (get-in % [1 :aria-label])))
            close-button (hiccup/find-first-node expanded-card #(and (= :button (first %))
                                                                     (contains? (hiccup/direct-texts %) "Close")))
            margin-button (hiccup/find-first-node expanded-card #(and (= :button (first %))
                                                                      (contains? (hiccup/direct-texts %) "Margin")))
            tpsl-button (hiccup/find-first-node expanded-card #(and (= :button (first %))
                                                                    (contains? (hiccup/direct-texts %) "TP/SL")))]
        (is (contains? (set (hiccup/collect-strings expanded-card)) "TP/SL"))
        (is (nil? margin-edit-button))
        (is (nil? tpsl-edit-button))
        (is (nil? close-button))
        (is (nil? margin-button))
        (is (nil? tpsl-button))))))

(deftest positions-tab-content-mobile-cross-margin-card-omits-margin-actions-test
  (test-support/with-phone-viewport
    (fn []
      (let [cross-row (assoc-in (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
                                [:position :leverage :type]
                                "cross")
            row-id (positions-tab/position-unique-key cross-row)
            content (test-support/render-positions-tab-from-rows [cross-row]
                                                                 fixtures/default-sort-state
                                                                 nil
                                                                 nil
                                                                 nil
                                                                 {:direction-filter :all
                                                                  :mobile-expanded-card {:positions row-id}})
            expanded-card (hiccup/find-by-data-role content (str "mobile-position-card-" row-id))
            margin-edit-button (hiccup/find-first-node expanded-card #(= "Edit Margin" (get-in % [1 :aria-label])))
            margin-footer-button (hiccup/find-first-node expanded-card #(and (= :button (first %))
                                                                             (contains? (hiccup/direct-texts %) "Margin")))
            expanded-strings (set (hiccup/collect-strings expanded-card))]
        (is (contains? expanded-strings "Margin"))
        (is (contains? expanded-strings "(Cross)"))
        (is (nil? margin-edit-button))
        (is (nil? margin-footer-button))))))

(deftest positions-tab-content-mobile-collapsed-row-still-renders-hoisted-margin-overlay-test
  (test-support/with-phone-viewport
    (fn []
      (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
            content (test-support/render-positions-tab-from-rows [row-data]
                                                                 fixtures/default-sort-state
                                                                 nil
                                                                 nil
                                                                 (position-margin/from-position-row {} row-data)
                                                                 {:direction-filter :all})
            collapsed-card (hiccup/find-by-data-role content
                                                     (str "mobile-position-card-"
                                                          (positions-tab/position-unique-key row-data)))
            card-strings (set (hiccup/collect-strings collapsed-card))
            overlay-layer (hiccup/find-by-data-role content "position-margin-mobile-sheet-layer")
            overlay-surface (hiccup/find-first-node content
                                                    #(= "true" (get-in % [1 :data-position-margin-surface])))]
        (is (some? collapsed-card))
        (is (contains? card-strings "NVDA"))
        (is (not (contains? card-strings "Adjust Margin")))
        (is (some? overlay-layer))
        (is (some? overlay-surface))))))

(deftest positions-tab-content-mobile-renders-one-active-margin-overlay-test
  (test-support/with-phone-viewport
    (fn []
      (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
            row-id (positions-tab/position-unique-key row-data)
            content (test-support/render-positions-tab-from-rows [row-data]
                                                                 fixtures/default-sort-state
                                                                 nil
                                                                 nil
                                                                 (position-margin/from-position-row {} row-data)
                                                                 {:direction-filter :all
                                                                  :mobile-expanded-card {:positions row-id}})
            overlay-surfaces (hiccup/find-all-nodes content
                                                    #(= "true" (get-in % [1 :data-position-margin-surface])))
            overlay-layers (hiccup/find-all-nodes content
                                                  #(= "position-margin-mobile-sheet-layer"
                                                      (get-in % [1 :data-role])))]
        (is (= 1 (count overlay-surfaces)))
        (is (= 1 (count overlay-layers)))))))

(deftest positions-tab-content-card-layout-renders-hoisted-margin-overlay-at-tablet-width-test
  (test-support/with-viewport
    768
    1024
    (fn []
      (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
            content (test-support/render-positions-tab-from-rows [row-data]
                                                                 fixtures/default-sort-state
                                                                 nil
                                                                 nil
                                                                 (position-margin/from-position-row {} row-data)
                                                                 {:direction-filter :all})
            overlay-layer (hiccup/find-by-data-role content "position-margin-mobile-sheet-layer")
            overlay-surface (hiccup/find-first-node content
                                                    #(= "true" (get-in % [1 :data-position-margin-surface])))]
        (is (nil? overlay-layer))
        (is (some? overlay-surface))
        (is (contains? (hiccup/node-class-set overlay-surface) "fixed"))))))

(deftest positions-tab-content-filters-out-hoisted-overlay-for-hidden-row-test
  (test-support/with-phone-viewport
    (fn []
      (let [long-row (fixtures/sample-position-row "LONGCOIN" 5 "1.0")
            short-row (fixtures/sample-position-row "SHORTCOIN" 5 "-1.0")
            content (test-support/render-positions-tab-from-rows [long-row short-row]
                                                                 fixtures/default-sort-state
                                                                 nil
                                                                 nil
                                                                 (position-margin/from-position-row {} short-row)
                                                                 {:direction-filter :long})
            overlay-layer (hiccup/find-by-data-role content "position-margin-mobile-sheet-layer")
            overlay-surface (hiccup/find-first-node content
                                                    #(= "true" (get-in % [1 :data-position-margin-surface])))]
        (is (nil? overlay-layer))
        (is (nil? overlay-surface))))))

(deftest positions-tab-content-read-only-mode-suppresses-hoisted-mobile-overlay-test
  (test-support/with-phone-viewport
    (fn []
      (let [row-data (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
            content (test-support/render-positions-tab-from-rows [row-data]
                                                                 fixtures/default-sort-state
                                                                 nil
                                                                 nil
                                                                 (position-margin/from-position-row {} row-data)
                                                                 {:direction-filter :all
                                                                  :read-only? true})
            overlay-layer (hiccup/find-by-data-role content "position-margin-mobile-sheet-layer")
            overlay-surface (hiccup/find-first-node content
                                                    #(= "true" (get-in % [1 :data-position-margin-surface])))]
        (is (nil? overlay-layer))
        (is (nil? overlay-surface))))))
