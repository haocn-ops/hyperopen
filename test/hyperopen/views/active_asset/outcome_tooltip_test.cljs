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
        summary-scroll (support/find-node-by-role tooltip "outcome-tooltip-summary-scroll")
        strings (set (support/collect-strings summary-scroll))]
    (is (support/contains-class? tooltip "left-0"))
    (is (= {:width "min(44rem, calc(100vw - 2rem))"
            :max-width "calc(100vw - 2rem)"}
           (get-in tooltip [1 :style])))
    (is (not (support/contains-class? tooltip "right-0")))
    (is (= {:max-height "min(30rem, calc(100vh - 16rem))"}
           (get-in summary-scroll [1 :style])))
    (is (support/contains-class? summary-scroll "overflow-y-auto"))
    (is (contains? strings long-details))))
