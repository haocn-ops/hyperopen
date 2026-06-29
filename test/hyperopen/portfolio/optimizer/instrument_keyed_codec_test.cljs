(ns hyperopen.portfolio.optimizer.instrument-keyed-codec-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.instrument-keyed-codec :as codec]))

(deftest normalize-worker-boundary-keywordizes-wire-enums-test
  (let [normalized (codec/normalize-worker-boundary
                    {:type "optimizer-result"
                     :payload {:status "solved"
                               :diagnostics {:reason "binding-constraint"}}
                     :history {:funding-by-instrument
                               {(keyword "perp:BTC")
                                {:source "market-funding-history"}}}})]
    (is (= :optimizer-result (:type normalized)))
    (is (= :solved (get-in normalized [:payload :status])))
    (is (= :binding-constraint
           (get-in normalized [:payload :diagnostics :reason])))
    (is (= :market-funding-history
           (get-in normalized
                   [:history :funding-by-instrument "perp:BTC" :source])))))

(deftest normalize-worker-boundary-stringifies-instrument-keyed-maps-by-key-test
  (let [btc (keyword "perp:BTC")
        purr (keyword "spot:PURR/USDC")
        normalized (codec/normalize-worker-boundary
                    {:current-portfolio {:by-instrument {btc {:weight 0.4}}}
                     :payload {:future-result
                               {:weights-by-instrument {btc 0.4
                                                       purr 0.6}
                                :labels-by-instrument {btc "BTC"
                                                       purr "PURR"}
                                :nested [{:target-weights-by-instrument
                                          {btc 0.5}}]}}
                     :diagnostics {:custom
                                   {:weight-sensitivity-by-instrument
                                    {purr {:max-delta 0.01}}}}})]
    (is (= {"perp:BTC" {:weight 0.4}}
           (get-in normalized [:current-portfolio :by-instrument])))
    (is (= {"perp:BTC" 0.4
            "spot:PURR/USDC" 0.6}
           (get-in normalized [:payload :future-result :weights-by-instrument])))
    (is (= {"perp:BTC" "BTC"
            "spot:PURR/USDC" "PURR"}
           (get-in normalized [:payload :future-result :labels-by-instrument])))
    (is (= {"perp:BTC" 0.5}
           (get-in normalized
                   [:payload :future-result :nested 0 :target-weights-by-instrument])))
    (is (= {"spot:PURR/USDC" {:max-delta 0.01}}
           (get-in normalized
                   [:diagnostics :custom :weight-sensitivity-by-instrument])))))

(deftest normalize-worker-boundary-stringifies-history-assumptions-and-keywordizes-enums-test
  (let [new-perp (keyword "perp:NEW")
        normalized (codec/normalize-worker-boundary
                    {:history-assumptions
                     {new-perp {:behavior "conservative"
                                :volatility 0.9
                                :max-weight 0.03
                                :correlation-floor 0.75}}})
        entry (get-in normalized [:history-assumptions "perp:NEW"])]
    (is (= {"perp:NEW" {:behavior :conservative
                        :volatility 0.9
                        :max-weight 0.03
                        :correlation-floor 0.75}}
           (:history-assumptions normalized))
        "instrument-id key is stringified and the :behavior value keywordized")
    (is (= :conservative (:behavior entry)))))

(deftest normalize-worker-boundary-preserves-unrelated-nested-map-keys-test
  (let [btc (keyword "perp:BTC")
        raw-weights {btc 1
                     :cash-buffer 0.05}
        normalized (codec/normalize-worker-boundary
                    {:payload {:metadata {:weights raw-weights}
                               :custom-by-id raw-weights}})]
    (is (= raw-weights
           (get-in normalized [:payload :metadata :weights])))
    (is (= raw-weights
           (get-in normalized [:payload :custom-by-id])))))

(deftest normalize-worker-boundary-covers-legacy-instrument-keyed-paths-test
  (let [btc (keyword "perp:BTC")]
    (doseq [path codec/instrument-keyed-map-paths]
      (let [normalized (codec/normalize-worker-boundary
                        (assoc-in {} path {btc {:weight 0.5}}))]
        (is (= {"perp:BTC" {:weight 0.5}}
               (get-in normalized path))
            (str "legacy path should still normalize: " (pr-str path)))))))

(deftest normalize-worker-boundary-stringifies-black-litterman-view-weights-test
  (let [btc (keyword "perp:BTC")
        normalized (codec/normalize-worker-boundary
                    {:return-model {:kind "black-litterman"
                                    :views [{:id "view-1"
                                             :kind "absolute"
                                             :instrument-id "perp:BTC"
                                             :weights {btc 1}}]}})]
    (is (= :black-litterman
           (get-in normalized [:return-model :kind])))
    (is (= {"perp:BTC" 1}
           (get-in normalized [:return-model :views 0 :weights])))))
