(ns hyperopen.portfolio.optimizer.application.pipeline-workflow-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.progress :as progress]
            [hyperopen.portfolio.optimizer.application.pipeline-workflow :as workflow]))

(def btc-instrument
  {:instrument-id "perp:BTC"
   :market-type :perp
   :coin "BTC"})

(def eth-instrument
  {:instrument-id "perp:ETH"
   :market-type :perp
   :coin "ETH"})

(def hype-instrument
  {:instrument-id "perp:HYPE"
   :market-type :perp
   :coin "HYPE"})

(defn- runnable-state
  []
  {:portfolio {:optimizer
               {:draft {:id "draft-pipeline"
                        :universe [btc-instrument]
                        :objective {:kind :minimum-variance}
                        :return-model {:kind :historical-mean}
                        :risk-model {:kind :diagonal-shrink}
                        :constraints {:long-only? true
                                      :max-asset-weight 1.0}}
                :history-data {:candle-history-by-coin
                               {"BTC" [{:time 1000 :close "100"}
                                       {:time 2000 :close "110"}]}
                               :funding-history-by-coin
                               {"BTC" [{:time-ms 1000
                                        :funding-rate-raw 0}]}
                               :loaded-at-ms 2100}
                :runtime {:as-of-ms 3000
                          :stale-after-ms 60000}}}
   :webdata2 {:clearinghouseState
              {:marginSummary {:accountValue "1000"}
               :assetPositions []}}})

(defn- selected-ready-current-outside-state
  []
  (-> (runnable-state)
      (assoc-in [:portfolio :optimizer :draft :universe]
                [btc-instrument eth-instrument])
      (assoc-in [:portfolio :optimizer :history-data :candle-history-by-coin]
                {"BTC" [{:time 1000 :close "100"}
                        {:time 2000 :close "110"}]
                 "ETH" [{:time 1000 :close "200"}
                        {:time 2000 :close "220"}]})
      (assoc-in [:portfolio :optimizer :history-data :funding-history-by-coin]
                {})
      (assoc-in [:webdata2 :clearinghouseState]
                {:marginSummary {:accountValue "1000"}
                 :assetPositions [{:position {:coin "HYPE"
                                              :szi "1"
                                              :positionValue "250"}}]})
      (assoc-in [:asset-selector :market-by-key]
                {"perp:HYPE" hype-instrument})))

(deftest begin-run-loads-current-history-before-ready-selected-worker-run-test
  (let [result (workflow/begin-run
                {:state (selected-ready-current-outside-state)
                 :run-id "pipeline-run-current-history"
                 :started-at-ms 1000})]
    (is (= [{:command/type :optimizer.workflow/load-history
             :run-id "pipeline-run-current-history"}]
           (:commands result)))
    (is (= :running
           (get-in result [:state :portfolio :optimizer :optimization-progress :status])))))

(deftest begin-run-plans-history-idle-wait-while-selection-prefetch-active-test
  (let [state (-> (runnable-state)
                  (assoc-in [:portfolio :optimizer :history-load-state]
                            {:status :loading
                             :request-signature {:source :selection-prefetch}})
                  (assoc-in [:portfolio :optimizer :history-prefetch]
                            {:queue []
                             :active-instrument-id "perp:BTC"
                             :by-instrument-id
                             {"perp:BTC" {:status :loading
                                          :started-at-ms 900
                                          :completed-at-ms nil
                                          :error nil
                                          :warnings []}}}))
        result (workflow/begin-run {:state state
                                    :run-id "pipeline-run-1"
                                    :started-at-ms 1000
                                    :history-idle-poll-ms 7
                                    :history-idle-timeout-ms 99})]
    (is (= [{:command/type :optimizer.workflow/wait-for-history-idle
             :run-id "pipeline-run-1"
             :poll-ms 7
             :timeout-ms 99}]
           (:commands result)))
    (is (= :running
           (get-in result [:state :portfolio :optimizer :optimization-progress :status])))
    (is (= "pipeline-run-1"
           (get-in result [:state :portfolio :optimizer :optimization-progress :run-id])))))

(deftest after-history-idle-plans-worker-run-when-request-is-ready-test
  (let [state (assoc-in (runnable-state)
                        [:portfolio :optimizer :optimization-progress]
                        (progress/begin-progress
                         {:run-id "pipeline-run-1"
                          :scenario-id "draft-pipeline"
                          :request {:scenario-id "draft-pipeline"
                                    :universe [btc-instrument]
                                    :requested-universe [btc-instrument]
                                    :return-model {:kind :historical-mean}
                                    :risk-model {:kind :diagonal-shrink}
                                    :objective {:kind :minimum-variance}}
                          :started-at-ms 1000}))
        result (workflow/after-history-idle {:state state
                                             :run-id "pipeline-run-1"})]
    (is (= :optimizer.workflow/request-worker-run
           (get-in result [:commands 0 :command/type])))
    (is (= "pipeline-run-1"
           (get-in result [:commands 0 :run-id])))
    (is (= ["perp:BTC"]
           (mapv :instrument-id (get-in result [:commands 0 :request :universe]))))
    (is (map? (get-in result [:commands 0 :request-signature])))
    (is (= :running
           (get-in result
                   [:state
                    :portfolio
                    :optimizer
                    :optimization-progress
                    :steps
                    1
                    :status])))))
