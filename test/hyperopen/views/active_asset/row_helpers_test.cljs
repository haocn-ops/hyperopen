(ns hyperopen.views.active-asset.row-helpers-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.active-asset.row :as row]
            [hyperopen.views.active-asset.test-support :as support]))

(deftest change-indicator-uses-directional-color-and-fallback-copy-test
  (let [change-indicator @#'row/change-indicator
        positive-node (change-indicator 1.25 0.5)
        negative-node (change-indicator -1.25 -0.5)
        missing-node (change-indicator nil nil)
        missing-strings (support/collect-strings missing-node)]
    (is (support/contains-class? positive-node "text-success"))
    (is (support/contains-class? negative-node "text-error"))
    (is (support/contains-class? missing-node "text-error"))
    (is (= ["-- / --"] missing-strings))))

(deftest data-column-applies-numeric-underline-and-change-options-test
  (let [data-column @#'row/data-column
        numeric-node (data-column "Mark" "87.0" {:numeric? true
                                                 :underlined true})
        change-node (data-column "24h Change"
                                 nil
                                 {:change? true
                                  :change-value 1.25
                                  :change-pct 0.5})]
    (is (support/contains-class? numeric-node "num"))
    (is (support/contains-class? numeric-node "border-dashed"))
    (is (support/contains-class? change-node "text-success"))
    (is (some #{"24h Change"} (support/collect-strings change-node)))))
