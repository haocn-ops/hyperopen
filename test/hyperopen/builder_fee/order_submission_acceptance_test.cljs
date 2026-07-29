(ns hyperopen.builder-fee.order-submission-acceptance-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.account.history.actions :as history-actions]
            [hyperopen.account.history.position-reduce :as position-reduce]
            [hyperopen.account.history.position-tpsl :as position-tpsl]
            [hyperopen.builder-fee.policy :as policy]
            [hyperopen.portfolio.optimizer.application.execution :as optimizer-execution]
            [hyperopen.views.account-info.test-support.fixtures :as fixtures]))

(def owner "0x1111111111111111111111111111111111111111")
(def builder "0x36a47878219fb346e031f6cf82cbfc8c77e35932")
(def config {:status :configured
             :builder-address builder
             :fee-tenths-bp 10
             :disclosure "A disclosed 0.01% builder fee."})
(def approval {:status :ready
               :owner-address owner
               :builder-address builder
               :network :mainnet
               :max-builder-fee 10})

(defn configured-state [state]
  (assoc state
         :tenant/override {:tenant/id "dexhelm"
                           :builder-fee config}
         :builder-fee {:approval approval}
         :wallet {:address owner}))

(defn order-action
  [grouping]
  (array-map :type "order"
             :orders [(array-map :a 5 :b false :p "100" :s "1" :r true
                                 :t {:limit {:tif "Gtc"}}
                                 :c "0x00000000000000000000000000000001")]
             :grouping grouping
             :pre-actions [{:type "cancel" :cancels []}]
             :cancellations []))

(deftest every-main-account-perp-and-spot-sell-order-shares-one-non-mutating-builder-decoration-test
  (doseq [[caller market side grouping]
          [[:trade-ticket :perp :buy "na"]
           [:position-reduction :perp :sell "na"]
           [:position-tpsl :perp :sell "normalTpsl"]
           [:optimizer :spot :sell "na"]]]
    (testing (name caller)
      (let [before (order-action grouping)
            after (:action (policy/policy-decision config approval owner owner market before side))]
        (is (= (:orders before) (:orders after)))
        (is (= (:grouping before) (:grouping after)))
        (is (= (:pre-actions before) (:pre-actions after)))
        (is (= (:cancellations before) (:cancellations after)))
        (is (= (array-map :b builder :f 10) (:builder after)))))))

(deftest ineligible-spot-buys-and-non-main-targets-retain-the-original-signed-action-test
  (let [before (order-action "na")]
    (is (identical? before
                    (:action (policy/policy-decision config approval owner owner :spot before :buy))))
    (is (identical? before
                    (:action (policy/policy-decision config approval owner "0x2222222222222222222222222222222222222222" :perp before :sell))))))

(deftest position-reduction-position-tpsl-and-optimizer-use-the-shared-builder-fee-policy-on-real-order-paths-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        reduce-popover (-> (position-reduce/from-position-row row)
                           (assoc :close-type :limit :limit-price "11"))
        reduce-effects (history-actions/submit-position-reduce-close
                        (configured-state
                         {:trading-settings {:confirm-close-position? false}
                          :positions-ui {:reduce-popover reduce-popover}
                          :asset-selector {:market-by-key {"perp:xyz:NVDA"
                                                           {:coin "xyz:NVDA"
                                                            :market-type :perp
                                                            :asset-id 123
                                                            :mark 10}}}}))
        tpsl-modal (-> (position-tpsl/from-position-row row)
                       (assoc :tp-price "11"))
        tpsl-effects (history-actions/submit-position-tpsl
                      (configured-state
                       {:positions-ui {:tpsl-modal tpsl-modal}
                        :asset-selector {:market-by-key {"perp:xyz:NVDA"
                                                         {:coin "xyz:NVDA"
                                                          :market-type :perp
                                                          :asset-id 123
                                                          :szDecimals 4}}}}))
        optimizer-plan (optimizer-execution/build-execution-plan
                        {:scenario-id "builder-fee-optimizer"
                         :rebalance-preview {:rows [{:instrument-id "spot:PURR"
                                                     :instrument-type :spot
                                                     :coin "PURR"
                                                     :status :ready
                                                     :side :sell
                                                     :price 100
                                                     :quantity 1
                                                     :delta-notional-usd -100}]}
                         :execution-assumptions {:default-order-type :market}})
        optimizer-attempt (optimizer-execution/build-execution-attempt
                           {:plan optimizer-plan
                            :market-by-key {"spot:PURR" {:coin "PURR"
                                                         :market-type :spot
                                                         :asset-id 10000
                                                         :szDecimals 4}}
                            :builder-fee-context {:config config
                                                  :approval approval
                                                  :owner-address owner
                                                  :target-address owner
                                                  :network :mainnet}})
        actions [(get-in reduce-effects [1 1 :action])
                 (get-in tpsl-effects [1 1 :action])
                 (get-in optimizer-attempt [:rows 0 :request :action])]]
    (is (every? #(= (array-map :b builder :f 10) (:builder %)) actions))
    (is (= "positionTpsl" (:grouping (second actions))))
    (is (= false (get-in (nth actions 2) [:orders 0 :r])))))
