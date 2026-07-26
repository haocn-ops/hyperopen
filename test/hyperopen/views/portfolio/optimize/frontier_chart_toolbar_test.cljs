(ns hyperopen.views.portfolio.optimize.frontier-chart-toolbar-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.optimize.frontier-chart-toolbar :as toolbar]
            [hyperopen.views.portfolio.optimize.test-support :as ts]))

(deftest constrain-frontier-checkbox-dispatches-vector-placeholder-test
  ;; The event-value placeholder MUST be the vector form `[:event.target/checked]`
  ;; so nexus interpolates the real boolean. The bare-keyword form dispatches a
  ;; literal keyword, which `(true? ...)` always reads as false, making the
  ;; control silently dead.
  (let [node (toolbar/toolbar {:overlay-mode :standalone
                               :constrain-frontier? false
                               :subtitle "Efficient frontier"
                               :point-count 40})
        checkbox (ts/node-by-role node
                                  "portfolio-optimizer-constrain-frontier-checkbox")]
    (is (= [[:actions/set-portfolio-optimizer-constrain-frontier
             [:event.target/checked]]]
           (ts/change-actions checkbox)))))
