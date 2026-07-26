(ns hyperopen.portfolio.optimizer.application.view-model-frontier-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.view-model.frontier :as frontier]))

(deftest visible-points-normalizes-mode-and-filters-invalid-points-test
  (let [result {:frontier-overlays
                {:standalone [{:instrument-id "perp:BTC"
                               :expected-return 0.12
                               :volatility 0.4}
                              {:instrument-id "perp:ETH"
                               :expected-return nil
                               :volatility 0.32}]
                 :contribution [{:instrument-id "spot:PURR"
                                 :expected-return 0.03
                                 :volatility 0.08}
                                {:instrument-id "spot:BAD"
                                 :expected-return 0.02
                                 :volatility js/NaN}]}}]
    (is (= [{:instrument-id "perp:BTC"
             :expected-return 0.12
             :volatility 0.4}]
           (frontier/visible-points result :unexpected)))
    (is (= [{:instrument-id "perp:BTC"
             :expected-return 0.12
             :volatility 0.4}
            {:instrument-id "spot:PURR"
             :expected-return 0.03
             :volatility 0.08}]
           (frontier/all-points result)))
    (is (= []
           (frontier/visible-points result :none)))))

(deftest point-market-derives-asset-icon-identity-test
  (is (= {:key "perp:BTC"
          :coin "BTC"
          :symbol "BTC"
          :base "BTC"
          :market-type :perp}
         (frontier/point-market {:instrument-id "perp:BTC"
                                 :label "Bitcoin Perp"})))
  (is (= "Risk vs return — annualized"
         (:subtitle (frontier/copy :none)))))
