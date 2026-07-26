(ns hyperopen.portfolio.optimizer.application.view-model-facade-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.view-model :as view-model]
            [hyperopen.portfolio.optimizer.application.view-model.execution :as view-model-execution]
            [hyperopen.portfolio.optimizer.application.view-model.scenario :as view-model-scenario]
            [hyperopen.portfolio.optimizer.application.view-model.tracking :as view-model-tracking]
            [hyperopen.portfolio.optimizer.application.view-model.universe :as view-model-universe]
            [hyperopen.portfolio.optimizer.application.view-model.workspace :as view-model-workspace]))

(def ^:private btc-instrument
  {:instrument-id "perp:BTC"
   :market-type :perp
   :coin "BTC"
   :symbol "BTC-USDC"
   :name "Bitcoin"})

(deftest compatibility-facade-delegates-to-focused-view-model-namespaces-test
  (let [state {:router {:path "/portfolio/optimize/new"}
               :portfolio {:optimizer {:active-scenario {:loaded-id "scn_current"
                                                          :status :computed}
                                        :draft {:id "draft-current"
                                                :name "Current Draft"
                                                :universe [btc-instrument]}
                                        :runtime {:as-of-ms 5000
                                                  :stale-after-ms 60000}
                                        :tracking {:snapshots []}
                                        :execution-modal {:open? true
                                                          :plan {:summary {:ready-count 0}}}}}}
        route {:scenario-id "scn_current"}
        universe-state {:portfolio-ui {:optimizer {:universe-search-query ""}}}
        draft {:universe [btc-instrument]}]
    (is (= (view-model-workspace/workspace-model state route)
           (view-model/workspace-model state route)))
    (is (= (view-model-scenario/scenario-detail-model state route)
           (view-model/scenario-detail-model state route)))
    (is (= (view-model-universe/universe-section-model universe-state draft)
           (view-model/universe-section-model universe-state draft)))
    (is (= (view-model-tracking/tracking-model state)
           (view-model/tracking-model state)))
    (is (= (view-model-execution/execution-modal-model state)
           (view-model/execution-modal-model state)))
    (is (= (view-model-execution/execution-tab-model state)
           (view-model/execution-tab-model state)))
    (is (= (view-model-scenario/inputs-audit-model state)
           (view-model/inputs-audit-model state)))))

(deftest selected-history-status-keeps-universe-logic-separate-from-label-copy-test
  (is (= :sufficient
         (view-model-universe/selected-history-status
          {}
          {:request {:universe [btc-instrument]}}
          {}
          {}
          btc-instrument)))
  (is (= :sufficient
         (view-model/selected-history-status
          {}
          {:request {:universe [btc-instrument]}}
          {}
          {}
          btc-instrument)))
  (is (= "sufficient"
         (view-model/selected-history-label
          {}
          {:request {:universe [btc-instrument]}}
          {}
          {}
          btc-instrument))))
