(ns hyperopen.portfolio.optimizer.execution-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]))

(deftest open-execution-stages-plan-and-switches-to-execution-tab-test
  (let [state {:portfolio {:optimizer
                           {:draft {:id "draft-1"
                                    :execution-assumptions {:default-order-type :market}}
                            :last-successful-run
                            (fixtures/sample-last-successful-run
                             {:result {:rebalance-preview
                                       {:summary {:estimated-fees-usd nil
                                                  :estimated-slippage-usd nil}
                                        :rows [{:instrument-id "perp:BTC"
                                                :instrument-type :perp
                                                :status :ready
                                                :side :buy
                                                :quantity 0.25
                                                :delta-notional-usd 1000}]}}})}}}
        effects (actions/open-portfolio-optimizer-execution state)]
    (is (= [:effects/save [:portfolio :optimizer :execution-modal]
            {:open? true
             :submitting? false
             :error nil
             :phase :staged
             :default-order-type :recommended
             :overrides {}
             :params {}
             :open-row nil
             :plan {:scenario-id "draft-1"
                    :status :ready
                    :execution-disabled? false
                    :disabled-reason nil
                    :disabled-message nil
                    :summary {:ready-count 1
                              :blocked-count 0
                              :skipped-count 0
                              :gross-ready-notional-usd 1000
                              :estimated-fees-usd nil
                              :estimated-slippage-usd nil
                              :margin nil}
                    :rows [{:row-id "perp:BTC"
                            :instrument-id "perp:BTC"
                            :instrument-type :perp
                            :status :ready
                            :side :buy
                            :quantity 0.25
                            :order-type :market
                            :delta-notional-usd 1000
                            :cost nil
                            :intent {:kind :perp-order
                                     :instrument-id "perp:BTC"
                                     :side :buy
                                     :quantity 0.25
                                     :order-type :market
                                     :reduce-only? false}}]}}]
           (first effects)))
    ;; run-state is reset and the surface switches to the Execution tab.
    (is (= [:effects/save [:portfolio :optimizer :execution]
            {:status :idle :attempt nil :history [] :error nil}]
           (nth effects 1)))
    (is (= [:effects/save [:portfolio-ui :optimizer :results-tab] :execution]
           (nth effects 2)))
    (is (= [:effects/replace-shareable-route-query] (nth effects 3)))))

(deftest open-execution-derives-preview-when-solved-run-lacks-preview-test
  (let [state {:asset-selector
               {:market-by-key
                {"perp:BTC" {:key "perp:BTC"
                             :market-type :perp
                             :coin "BTC"
                             :markPx "100"}
                 "perp:ETH" {:key "perp:ETH"
                             :market-type :perp
                             :coin "ETH"
                             :markPx "50"}}}
               :webdata2
               {:clearinghouseState
                {:marginSummary {:accountValue "10000"
                                 :totalMarginUsed "500"}
                 :assetPositions [{:position {:coin "BTC"
                                              :szi "25"
                                              :positionValue "2500"
                                              :markPx "100"
                                              :leverage {:value 5
                                                         :type "cross"}}}]}}
               :portfolio {:optimizer
                           {:active-scenario {:loaded-id nil
                                              :status :computed}
                            :draft {:id "draft-1"
                                    :universe [{:instrument-id "perp:BTC"
                                                :market-type :perp
                                                :coin "BTC"}
                                               {:instrument-id "perp:ETH"
                                                :market-type :perp
                                                :coin "ETH"}]
                                    :objective {:kind :minimum-variance}
                                    :return-model {:kind :historical-mean}
                                    :risk-model {:kind :diagonal-shrink}
                                    :constraints {:long-only? true
                                                  :max-asset-weight 0.75
                                                  :rebalance-tolerance 0.001}
                                    :execution-assumptions
                                    {:default-order-type :market
                                     :fallback-slippage-bps 20
                                     :prices-by-id {"perp:BTC" 100
                                                    "perp:ETH" 50}
                                     :fee-bps-by-id {"perp:BTC" 4
                                                    "perp:ETH" 5}}}
                            :history-data {:candle-history-by-coin
                                           {"BTC" [{:time-ms 1000 :close "100"}
                                                   {:time-ms 2000 :close "101"}
                                                   {:time-ms 3000 :close "102"}]
                                            "ETH" [{:time-ms 1000 :close "50"}
                                                   {:time-ms 2000 :close "51"}
                                                   {:time-ms 3000 :close "52"}]}
                                           :funding-history-by-coin
                                           {"BTC" [{:time-ms 1000
                                                    :funding-rate-raw 0}]
                                            "ETH" [{:time-ms 1000
                                                    :funding-rate-raw 0}]}}
                            :runtime {:as-of-ms 3000
                                      :stale-after-ms 60000}
                            :last-successful-run
                            (fixtures/sample-minimal-last-successful-run
                             {:result {:status :solved
                                       :scenario-id "draft-1"
                                       :instrument-ids ["perp:BTC" "perp:ETH"]
                                       :current-weights [0.25 0.0]
                                       :target-weights [0.5 0.5]
                                       :target-weights-by-instrument {"perp:BTC" 0.5
                                                                      "perp:ETH" 0.5}
                                       :current-weights-by-instrument {"perp:BTC" 0.25
                                                                       "perp:ETH" 0.0}
                                       :diagnostics {:turnover 0.75}}})}}}
        effects (actions/open-portfolio-optimizer-execution state)
        plan (get-in effects [0 2 :plan])]
    (is (= :effects/save (get-in effects [0 0])))
    (is (= [:portfolio :optimizer :execution-modal] (get-in effects [0 1])))
    (is (= true (get-in effects [0 2 :open?])))
    (is (= :ready (:status plan)))
    (is (= 2 (get-in plan [:summary :ready-count])))
    (is (= #{"perp:BTC" "perp:ETH"} (set (map :instrument-id (:rows plan)))))
    (is (= #{:perp-order} (set (map #(get-in % [:intent :kind]) (:rows plan)))))))

(deftest open-execution-without-solved-run-still-switches-tab-with-nil-plan-test
  (let [effects (actions/open-portfolio-optimizer-execution
                 {:portfolio {:optimizer {:last-successful-run
                                          (fixtures/sample-last-successful-run
                                           {:result {:status :infeasible}})}}})]
    (is (nil? (get-in effects [0 2 :plan])))
    (is (= [:effects/save [:portfolio-ui :optimizer :results-tab] :execution]
           (nth effects 2)))))

(deftest execution-staging-mutators-update-interaction-state-test
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :phase] :armed]
          [:effects/save [:portfolio :optimizer :execution-modal :error] nil]]
         (actions/set-portfolio-optimizer-execution-phase {} :armed)))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :phase] :staged]
          [:effects/save [:portfolio :optimizer :execution-modal :error] nil]]
         (actions/set-portfolio-optimizer-execution-phase {} :staged)))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :default-order-type] :twap]]
         (actions/set-portfolio-optimizer-execution-default-order-type {} :twap)))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :overrides] {"perp:BTC" :limit}]]
         (actions/set-portfolio-optimizer-execution-row-order-type {} "perp:BTC" :limit)))
  ;; the :recommended sentinel clears an existing override
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :overrides] {}]]
         (actions/set-portfolio-optimizer-execution-row-order-type
          {:portfolio {:optimizer {:execution-modal {:overrides {"perp:BTC" :limit}}}}}
          "perp:BTC" :recommended)))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :open-row] "perp:BTC"]]
         (actions/toggle-portfolio-optimizer-execution-row {} "perp:BTC")))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :open-row] nil]]
         (actions/toggle-portfolio-optimizer-execution-row
          {:portfolio {:optimizer {:execution-modal {:open-row "perp:BTC"}}}}
          "perp:BTC")))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :params] {"perp:BTC" {:limit-bps -5}}]]
         (actions/set-portfolio-optimizer-execution-row-param {} "perp:BTC" :limit-bps -5))))

(deftest confirm-execution-dispatches-execution-effect-test
  (let [plan {:scenario-id "draft-1"
              :status :ready
              :execution-disabled? false
              :summary {:ready-count 1}
              :rows [{:row-id "perp:BTC"
                      :status :ready
                      :intent {:kind :perp-order}}]}
        state {:portfolio {:optimizer {:execution-modal {:open? true
                                                         :plan plan}}}}]
    (is (= [[:effects/save [:portfolio :optimizer :execution-modal :submitting?] true]
            [:effects/save [:portfolio :optimizer :execution-modal :error] nil]
            [:effects/execute-portfolio-optimizer-plan plan]]
           (actions/confirm-portfolio-optimizer-execution state)))))

(deftest confirm-execution-blocks-read-only-plan-test
  (let [state {:portfolio {:optimizer {:execution-modal
                                       {:open? true
                                        :plan {:scenario-id "draft-1"
                                               :status :ready
                                               :execution-disabled? true
                                               :disabled-message "Spectate Mode is read-only."}}}}}]
    (is (= [[:effects/save
             [:portfolio :optimizer :execution-modal :error]
             "Spectate Mode is read-only."]]
           (actions/confirm-portfolio-optimizer-execution state)))))
