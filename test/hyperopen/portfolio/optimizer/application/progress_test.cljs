(ns hyperopen.portfolio.optimizer.application.progress-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.progress :as progress]))

(def ^:private sample-progress
  (progress/begin-progress
   {:run-id "run-1"
    :scenario-id "scenario-1"
    :request {:universe [{:instrument-id "perp:BTC"}
                         {:instrument-id "perp:ETH"}]
              :risk-model {:kind :diagonal-shrink}
              :return-model {:kind :historical-mean}}
    :started-at-ms 100}))

(deftest worker-progress-applies-string-step-ids-from-the-wire-test
  ;; Worker payloads cross the postMessage boundary with :step serialized
  ;; to a string; they must still match the keyword step ids.
  (let [updated (progress/worker-progress sample-progress
                                          {:step "risk-model"
                                           :status :running
                                           :percent 25
                                           :detail "estimating covariance"})
        step (first (filter #(= :risk-model (:id %)) (:steps updated)))]
    (is (= 25 (:percent step)))
    (is (= :running (:status step)))
    (is (= "estimating covariance" (:detail step)))
    (is (= :risk-model (:active-step updated)))))

(deftest worker-progress-applies-keyword-step-ids-test
  (let [updated (progress/worker-progress sample-progress
                                          {:step :frontier
                                           :status :running
                                           :percent 37.5
                                           :detail "30/80 points"})
        step (first (filter #(= :frontier (:id %)) (:steps updated)))]
    (is (= 37.5 (:percent step)))
    (is (= :running (:status step)))))

(deftest worker-progress-ignores-unknown-steps-test
  (is (= sample-progress
         (progress/worker-progress sample-progress
                                   {:step "not-a-step"
                                    :status :running
                                    :percent 10}))))
