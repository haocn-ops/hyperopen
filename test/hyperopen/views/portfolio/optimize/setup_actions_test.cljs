(ns hyperopen.views.portfolio.optimize.setup-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.optimize.setup-actions :as setup-actions]
            [hyperopen.views.portfolio.optimize.test-support :as ts]))

(def ^:private sample-draft
  {:universe [{:instrument-id "perp:BTC"}]
   :objective {:kind :max-sharpe}
   :return-model {:kind :historical-mean}
   :risk-model {:kind :diagonal-shrink}})

(deftest run-status-is-ready-when-triggerable-and-readiness-runnable-test
  (let [status (setup-actions/run-status {:run-triggerable? true
                                          :running? false
                                          :readiness {:runnable? true}
                                          :asset-count 4})]
    (is (true? (:ready? status)))
    (is (= :ready (:tone status)))
    (is (= "Ready to run" (:label status)))))

(deftest run-status-blocks-and-names-reason-when-not-runnable-test
  (let [incomplete (setup-actions/run-status {:run-triggerable? true
                                              :running? false
                                              :readiness {:runnable? false
                                                          :reason :incomplete-history}
                                              :asset-count 4})
        no-history (setup-actions/run-status {:run-triggerable? true
                                              :running? false
                                              :readiness {:runnable? false
                                                          :reason :no-eligible-history}
                                              :asset-count 4})
        loading (setup-actions/run-status {:run-triggerable? true
                                           :running? false
                                           :readiness {:runnable? false
                                                       :reason :history-loading}
                                           :asset-count 4})
        bl (setup-actions/run-status {:run-triggerable? true
                                      :running? false
                                      :readiness {:runnable? false
                                                  :reason :missing-black-litterman-views}
                                      :asset-count 4})]
    (is (false? (:ready? incomplete)))
    (is (= :blocked (:tone incomplete)))
    (is (= "History incomplete" (:label incomplete)))
    (is (= "No usable history" (:label no-history)))
    (is (= "Loading history" (:label loading)))
    (is (= "Add a view" (:label bl)))))

(deftest run-status-prefers-empty-message-when-no-assets-test
  (let [status (setup-actions/run-status {:run-triggerable? false
                                          :running? false
                                          :readiness {:runnable? false
                                                      :reason :missing-universe}
                                          :asset-count 0})]
    (is (false? (:ready? status)))
    (is (= :empty (:tone status)))
    (is (= "Add assets to run" (:label status)))))

(deftest run-status-reports-busy-while-running-test
  (let [status (setup-actions/run-status {:run-triggerable? false
                                          :running? true
                                          :readiness {:runnable? true}
                                          :asset-count 4})]
    (is (false? (:ready? status)))
    (is (= :busy (:tone status)))))

(deftest setup-bottom-actions-keeps-run-enabled-but-flags-blocked-status-when-not-runnable-test
  ;; The Run button stays clickable (the intentional "run retries anything still
  ;; missing" affordance), but the status pill must NOT read green "Ready to run";
  ;; it names the blocking reason instead, removing the QA P1 contradiction.
  (let [node (setup-actions/setup-bottom-actions
              {:draft sample-draft
               :readiness {:runnable? false :reason :no-eligible-history}
               :running? false
               :run-triggerable? true
               :saving-scenario? false
               :solved-run? false})
        run-button (ts/node-by-role node "portfolio-optimizer-run-draft")
        status-meta (ts/node-by-role node "portfolio-optimizer-setup-bottom-actions-status-meta")]
    (is (not (true? (ts/node-attr run-button :disabled))))
    (is (= "blocked" (ts/node-attr status-meta :data-run-status)))
    (is (not (some #{"Ready to run"} (ts/collect-strings status-meta))))
    (is (some #{"No usable history"} (ts/collect-strings status-meta)))))

(deftest setup-bottom-actions-enables-run-button-when-runnable-test
  (let [node (setup-actions/setup-bottom-actions
              {:draft sample-draft
               :readiness {:runnable? true}
               :running? false
               :run-triggerable? true
               :saving-scenario? false
               :solved-run? false})
        run-button (ts/node-by-role node "portfolio-optimizer-run-draft")
        status-meta (ts/node-by-role node "portfolio-optimizer-setup-bottom-actions-status-meta")]
    (is (false? (ts/node-attr run-button :disabled)))
    (is (= "ready" (ts/node-attr status-meta :data-run-status)))
    (is (some #{"Ready to run"} (ts/collect-strings status-meta)))))
