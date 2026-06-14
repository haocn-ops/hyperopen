(ns hyperopen.views.portfolio.optimize.optimization-progress-panel-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.optimization-progress-panel :as panel]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [collect-strings node-by-role]]))

(defn- running-progress
  [overrides]
  (merge {:status :running
          :run-id "run-1"
          :scenario-id "draft-1"
          :started-at-ms 1000
          :active-step :fetch-returns
          :overall-percent 25
          :steps [{:id :fetch-returns
                   :label "fetch returns matrix"
                   :detail "1/2 requests"
                   :status :running
                   :percent 50}
                  {:id :solve
                   :label "QP solve"
                   :detail "OSQP"
                   :status :pending
                   :percent 0}]
          :error nil}
         overrides))

(deftest progress-panel-shows-a-single-compact-summary-test
  (let [node (panel/progress-panel (running-progress {:now-ms 16000}))
        strings (set (collect-strings node))
        footer (node-by-role node "portfolio-optimizer-progress-footer")
        footer-text (str/join " " (collect-strings footer))]
    ;; Single quiet headline + overall percent instead of a stack of bars.
    (is (contains? strings "Optimizing portfolio…"))
    (is (contains? strings "25%"))
    ;; Per-step breakdown is preserved behind a 'details' disclosure.
    (is (some? (node-by-role node "portfolio-optimizer-progress-details")))
    ;; Fine-print footer: elapsed and the current sub-step (no remaining estimate).
    (is (str/includes? footer-text "elapsed"))
    (is (not (str/includes? footer-text "remaining")))
    (is (str/includes? footer-text "fetch returns matrix"))
    (is (str/includes? footer-text "details"))))

(deftest progress-panel-renders-the-trickled-display-percent-and-ticker-clock-test
  ;; The ticker writes a smoothed :display-percent ahead of the sparse true
  ;; :overall-percent and a fresh :now-ms; the headline + bar follow the smoothed
  ;; value and the elapsed clock follows :now-ms (started 1000, now 9000 -> 8.0s).
  (let [node (panel/progress-panel (running-progress {:overall-percent 25
                                                      :display-percent 33
                                                      :now-ms 9000}))
        strings (set (collect-strings node))
        footer (node-by-role node "portfolio-optimizer-progress-footer")
        footer-text (str/join " " (collect-strings footer))]
    (is (contains? strings "33%"))
    (is (not (contains? strings "25%")))
    (is (str/includes? footer-text "8.0s elapsed"))))

(deftest progress-panel-failed-state-omits-the-stale-percent-test
  (let [node (panel/progress-panel
              {:status :failed
               :run-id "run-1"
               :started-at-ms 1000
               :completed-at-ms 2000
               :active-step nil
               :overall-percent 63
               :error {:message "solver diverged"}
               :steps [{:id :fetch-returns
                        :label "fetch returns matrix"
                        :status :succeeded
                        :percent 100}
                       {:id :solve
                        :label "QP solve"
                        :status :failed
                        :percent 0}]})
        strings (set (collect-strings node))]
    (is (some? (node-by-role node "portfolio-optimizer-progress-panel")))
    (is (contains? strings "Optimization failed"))
    (is (contains? strings "solver diverged"))
    ;; A partial completion fraction is not shown next to a failure.
    (is (not (contains? strings "63%")))))
