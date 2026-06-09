(ns hyperopen.views.active-asset.outcome-tooltip-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.active-asset.outcome-tooltip :as outcome-tooltip]
            [hyperopen.views.active-asset.test-support :as support]))

(deftest outcome-tooltip-bounds-long-rule-copy-in-scrollable-panel-test
  (let [long-details (str "Each associated outcome corresponds to a team confirmed to be participating in the 2026 FIFA World Cup. "
                          "An outcome resolves to Yes if FIFA officially declares the corresponding team the champion of the 2026 FIFA World Cup. "
                          "An outcome resolves to No once it becomes impossible under FIFA tournament rules for the corresponding team to win the 2026 FIFA World Cup, "
                          "including but not limited to upon elimination from the tournament.")
        tooltip (outcome-tooltip/outcome-tooltip-panel
                 {:title "Outcome Details"
                  :summary long-details
                  :settlement-label long-details
                  :settlement-time-label "on settlement time"
                  :yes-payout-label "$1.00"
                  :no-payout-label "$0.00"
                  :footer-label "Payouts are in USDC."})
        scroll-container (support/find-node-by-role tooltip "outcome-tooltip-scroll-container")
        summary-scroll (support/find-node-by-role tooltip "outcome-tooltip-summary-scroll")
        settlement-label (support/find-node-by-role tooltip "outcome-tooltip-settlement-label")
        strings (set (support/collect-strings tooltip))]
    (is (support/contains-class? tooltip "left-0"))
    (is (= {:width "min(44rem, calc(100vw - 2rem))"
            :max-width "calc(100vw - 2rem)"}
           (get-in tooltip [1 :style])))
    (is (not (support/contains-class? tooltip "right-0")))
    (is (= {:max-height "min(40rem, calc(100vh - 5rem))"}
           (get-in scroll-container [1 :style])))
    (is (support/contains-class? scroll-container "overflow-y-auto"))
    (is (not (support/contains-class? summary-scroll "overflow-y-auto")))
    (is (nil? settlement-label))
    (is (not (contains? strings "Settlement Condition")))
    (is (contains? strings "Payout Rule"))
    (is (contains? strings "Payouts are in USDC."))
    (is (contains? strings long-details))))

(deftest outcome-tooltip-keeps-distinct-structured-settlement-row-test
  (let [tooltip (outcome-tooltip/outcome-tooltip-panel
                 {:title "Outcome Details"
                  :summary "This market resolves to YES or NO based on the following settlement condition at the specified time."
                  :settlement-label "BTC mark price is above 78,213"
                  :settlement-time-label "on May 03, 2026 02:00 AM UTC"
                  :yes-payout-label "$1.00"
                  :no-payout-label "$0.00"
                  :footer-label "Payouts are in USDH."})
        settlement-label (support/find-node-by-role tooltip "outcome-tooltip-settlement-label")
        strings (set (support/collect-strings tooltip))]
    (is (contains? strings "Settlement Condition"))
    (is (contains? strings "on May 03, 2026 02:00 AM UTC"))
    (is (support/contains-class? settlement-label "whitespace-normal"))
    (is (support/contains-class? settlement-label "break-words"))
    (is (not (support/contains-class? settlement-label "whitespace-nowrap")))))
