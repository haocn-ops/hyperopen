(ns hyperopen.portfolio.optimizer.application.run-bridge-workflow-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.progress :as progress]
            [hyperopen.portfolio.optimizer.application.run-bridge-workflow :as workflow]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]))

(deftest start-run-plans-worker-commands-and-running-state-test
  (let [state {:portfolio {:optimizer {:run-state {:status :idle}}}}
        result (workflow/start-run {:state state
                                    :last-run-request nil
                                    :request {:scenario-id "scenario-1"}
                                    :request-signature {:seed 1}
                                    :run-id "run-1"
                                    :computed-at-ms 100})]
    (is (= {:status :running
            :run-id "run-1"
            :scenario-id "scenario-1"
            :request-signature {:seed 1}
            :started-at-ms 100
            :error nil}
           (get-in result [:state :portfolio :optimizer :run-state])))
    (is (= {:request-signature {:seed 1}
            :run-id "run-1"}
           (:last-run-request result)))
    (is (= [:optimizer.workflow/install-worker-handler
            :optimizer.workflow/post-worker-run]
           (mapv :command/type (:commands result))))))

(deftest start-run-allows-retry-after-failed-run-with-identical-signature-test
  (let [state {:portfolio {:optimizer {:run-state {:status :failed
                                                   :run-id "run-1"
                                                   :request-signature {:seed 1}
                                                   :error {:code :boom}}}}}
        result (workflow/start-run {:state state
                                    :last-run-request {:request-signature {:seed 1}
                                                       :run-id "run-1"}
                                    :request {:scenario-id "scenario-1"}
                                    :request-signature {:seed 1}
                                    :run-id "run-2"
                                    :computed-at-ms 200})]
    (is (= "run-2" (:run-id result)))
    (is (= :running
           (get-in result [:state :portfolio :optimizer :run-state :status])))
    (is (= :optimizer.workflow/post-worker-run
           (get-in result [:commands 1 :command/type])))))

(deftest start-run-dedupes-identical-in-flight-signature-test
  (let [state {:portfolio {:optimizer {:run-state {:status :running
                                                   :run-id "run-1"
                                                   :request-signature {:seed 1}}}}}
        result (workflow/start-run {:state state
                                    :last-run-request {:request-signature {:seed 1}
                                                       :run-id "run-1"}
                                    :request {:scenario-id "scenario-1"}
                                    :request-signature {:seed 1}
                                    :computed-at-ms 200})]
    (is (nil? (:run-id result)))
    (is (= state (:state result)))
    (is (empty? (:commands result)))))

(deftest handle-worker-message-ignores-stale-progress-and-error-after-scenario-switch-test
  (let [state {:portfolio {:optimizer {:active-scenario {:loaded-id "scenario-2"}
                                       :optimization-progress {:run-id "run-1"
                                                               :status :running
                                                               :steps []}
                                       :run-state {:status :running
                                                   :run-id "run-1"
                                                   :scenario-id "scenario-1"
                                                   :request-signature {:seed 1}}
                                       :last-successful-run {:result {:old? true}}}}}]
    (is (= state
           (:state (workflow/handle-worker-message
                    {:state state
                     :message {:id "run-1"
                               :type "optimizer-progress"
                               :payload {:percent 50}}
                     :computed-at-ms 300}))))
    (is (= state
           (:state (workflow/handle-worker-message
                    {:state state
                     :message {:id "run-1"
                               :type "optimizer-error"
                               :payload {:code :boom}}
                     :computed-at-ms 300}))))))

(deftest handle-worker-message-applies-current-solved-result-test
  (let [state {:portfolio {:optimizer {:draft {:metadata {:dirty? true}}
                                       :optimization-progress
                                       (progress/begin-progress
                                        {:run-id "run-1"
                                         :scenario-id "scenario-1"
                                         :request {:scenario-id "scenario-1"}
                                         :started-at-ms 100})
                                       :run-state {:status :running
                                                   :run-id "run-1"
                                                   :scenario-id "scenario-1"
                                                   :request-signature {:seed 1}}}}}
        result (workflow/handle-worker-message
                {:state state
                 :message {:id "run-1"
                           :type "optimizer-result"
                           :payload {:status :solved
                                     :scenario-id "scenario-1"}}
                 :computed-at-ms 400})]
    (is (= :succeeded
           (get-in result [:state :portfolio :optimizer :run-state :status])))
    (is (= {:request-signature {:seed 1}
            :result (fixtures/sample-minimal-solved-result
                     {:scenario-id "scenario-1"})
            :computed-at-ms 400}
           (get-in result [:state :portfolio :optimizer :last-successful-run])))
    (is (false?
         (get-in result [:state :portfolio :optimizer :draft :metadata :dirty?])))))

(deftest handle-worker-message-on-new-route-reveals-result-in-place-without-navigating-test
  ;; Single-workspace Phase 1: a run on /optimize/new reveals the result in-place
  ;; rather than force-navigating to the results page, so the user keeps their
  ;; inputs in view. The only post-run command is the slippage-snapshot refresh.
  (let [state {:router {:path "/portfolio/optimize/new"}
               :portfolio {:optimizer {:draft {:metadata {:dirty? true}}
                                        :optimization-progress
                                        (progress/begin-progress
                                         {:run-id "run-1"
                                          :scenario-id nil
                                          :request {:scenario-id nil}
                                          :started-at-ms 100})
                                        :run-state {:status :running
                                                    :run-id "run-1"
                                                    :scenario-id nil
                                                    :request-signature {:seed 1}}}}}
        result (workflow/handle-worker-message
                {:state state
                 :message {:id "run-1"
                           :type "optimizer-result"
                           :payload {:status :solved}}
                 :computed-at-ms 400})]
    (is (= [{:command/type
             :optimizer.workflow/refresh-portfolio-optimizer-rebalance-slippage-snapshots}]
           (:commands result))
        "No navigate command — the result reveals in-place on the workspace.")))
