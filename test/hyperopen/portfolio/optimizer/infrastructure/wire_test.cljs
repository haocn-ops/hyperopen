(ns hyperopen.portfolio.optimizer.infrastructure.wire-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.infrastructure.wire :as wire]))

(deftest normalize-worker-boundary-stringifies-black-litterman-view-weights-test
  (let [decoded-id (keyword "perp:BTC")
        normalized (wire/normalize-worker-boundary
                    {:return-model {:kind "black-litterman"
                                    :views [{:id "view-1"
                                             :kind "absolute"
                                             :instrument-id "perp:BTC"
                                             :return 0.2
                                             :confidence 0.75
                                             :weights {decoded-id 1}}]}})]
    (is (= {"perp:BTC" 1}
           (get-in normalized [:return-model :views 0 :weights])))
    (is (= :black-litterman
           (get-in normalized [:return-model :kind])))))

(deftest normalize-worker-boundary-stringifies-known-instrument-keyed-maps-test
  (let [perp-id (keyword "perp:BTC")
        spot-id (keyword "spot:PURR/USDC")
        normalized (wire/normalize-worker-boundary
                    {:current-portfolio {:by-instrument {spot-id {:weight 0.2}}}
                     :history {:return-series-by-instrument {perp-id [0.01 0.02]}
                               :funding-by-instrument {perp-id {:source "market-funding-history"}}}
                     :payload {:status "solved"
                               :return-decomposition-by-instrument
                               {perp-id {:return-component 0.12
                                         :funding-component 0.04
                                         :funding-source "market-funding-history"}
                                spot-id {:return-component 0.08
                                         :funding-component 0
                                         :funding-source "missing"}}
                               :expected-returns-by-instrument {perp-id 0.12
                                                                spot-id 0.08}
                               :current-weights-by-instrument {spot-id 0.2}
                               :target-weights-by-instrument {perp-id 0.35}
                               :diagnostics {:weight-sensitivity-by-instrument
                                             {perp-id {:max-delta 0.01}}}}})]
    (is (= {"spot:PURR/USDC" {:weight 0.2}}
           (get-in normalized [:current-portfolio :by-instrument])))
    (is (= {"perp:BTC" [0.01 0.02]}
           (get-in normalized [:history :return-series-by-instrument])))
    (is (= :market-funding-history
           (get-in normalized [:history :funding-by-instrument "perp:BTC" :source])))
    (is (= :solved (get-in normalized [:payload :status])))
    (is (= #{ "perp:BTC" "spot:PURR/USDC" }
           (set (keys (get-in normalized [:payload :return-decomposition-by-instrument])))))
    (is (= :market-funding-history
           (get-in normalized
                   [:payload :return-decomposition-by-instrument
                    "perp:BTC"
                    :funding-source])))
    (is (= {"perp:BTC" 0.12
            "spot:PURR/USDC" 0.08}
           (get-in normalized [:payload :expected-returns-by-instrument])))
    (is (= {"spot:PURR/USDC" 0.2}
           (get-in normalized [:payload :current-weights-by-instrument])))
    (is (= {"perp:BTC" 0.35}
           (get-in normalized [:payload :target-weights-by-instrument])))
    (is (= {"perp:BTC" {:max-delta 0.01}}
           (get-in normalized
                   [:payload :diagnostics :weight-sensitivity-by-instrument])))))
