(ns hyperopen.portfolio.optimizer.infrastructure.history-client-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.portfolio.optimizer.infrastructure.history-client :as history-client]
            [hyperopen.test-support.async :as async-support]))

(defn- json-response
  [status payload]
  #js {:ok (<= 200 status 299)
       :status status
       :json (fn []
               (js/Promise.resolve (clj->js payload)))})

(deftest request-history-bundle-fetches-arbitrary-candles-and-perp-funding-test
  (async done
    (let [calls (atom [])
          deps {:request-candle-snapshot!
                (fn [coin opts]
                  (swap! calls conj [:candle coin opts])
                  (js/Promise.resolve [{:time 1000 :close (case coin
                                                            "BTC" "100"
                                                            "PURR" "10")}]))
                :request-market-funding-history!
                (fn [coin opts]
                  (swap! calls conj [:funding coin opts])
                  (js/Promise.resolve [{:time-ms 1000
                                        :funding-rate-raw "0.001"}]))}
          request {:universe [{:instrument-id "perp:BTC"
                               :market-type :perp
                               :coin "BTC"}
                              {:instrument-id "spot:PURR"
                               :market-type :spot
                               :coin "PURR"}]
                   :interval :1d
                   :bars 30
                   :priority :low
                   :now-ms 10000
                   :funding-window-ms 5000}]
      (-> (history-client/request-history-bundle! deps request)
          (.then
           (fn [bundle]
             (is (= [[:candle "BTC" {:interval :1d
                                     :bars 30
                                     :priority :low
                                     :cache-key [:portfolio-optimizer :candles "BTC" :1d 30]
                                     :dedupe-key [:portfolio-optimizer :candles "BTC" :1d 30]}]
                     [:candle "PURR" {:interval :1d
                                      :bars 30
                                      :priority :low
                                      :cache-key [:portfolio-optimizer :candles "PURR" :1d 30]
                                      :dedupe-key [:portfolio-optimizer :candles "PURR" :1d 30]}]
                     [:funding "BTC" {:priority :low
                                      :start-time-ms 5000
                                      :end-time-ms 10000
                                      :cache-key [:portfolio-optimizer :funding "BTC" 5000 10000]
                                      :dedupe-key [:portfolio-optimizer :funding "BTC" 5000 10000]}]]
                    @calls))
             (is (= {"BTC" [{:time 1000 :close "100"}]
                     "PURR" [{:time 1000 :close "10"}]}
                    (:candle-history-by-coin bundle)))
             (is (= {"BTC" [{:time-ms 1000
                             :funding-rate-raw "0.001"}]}
                    (:funding-history-by-coin bundle)))
             (is (= [] (:warnings bundle)))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-history-bundle-fetches-vault-details-for-vault-instruments-test
  (async done
    (let [vault-address "0x1111111111111111111111111111111111111111"
          calls (atom [])
          vault-details {:portfolio {:all-time {:accountValueHistory [[1000 100]
                                                                       [2000 110]]
                                                :pnlHistory [[1000 0]
                                                             [2000 10]]}}}
          deps {:request-candle-snapshot!
                (fn [coin opts]
                  (swap! calls conj [:candle coin opts])
                  (js/Promise.resolve [{:time 1000 :close "100"}
                                       {:time 2000 :close "110"}]))
                :request-market-funding-history!
                (fn [coin opts]
                  (swap! calls conj [:funding coin opts])
                  (js/Promise.resolve [{:time-ms 1000
                                        :funding-rate-raw "0.001"}]))
                :request-vault-details!
                (fn [address opts]
                  (swap! calls conj [:vault-details address opts])
                  (js/Promise.resolve vault-details))}
          request {:universe [{:instrument-id "perp:BTC"
                               :market-type :perp
                               :coin "BTC"}
                              {:instrument-id (str "vault:" vault-address)
                               :market-type :vault
                               :coin (str "vault:" vault-address)
                               :vault-address vault-address}]
                   :interval :1d
                   :bars 30
                   :priority :low
                   :now-ms 10000
                   :funding-window-ms 5000}]
      (-> (history-client/request-history-bundle! deps request)
          (.then
           (fn [bundle]
             (is (= [[:candle "BTC" {:interval :1d
                                     :bars 30
                                     :priority :low
                                     :cache-key [:portfolio-optimizer :candles "BTC" :1d 30]
                                     :dedupe-key [:portfolio-optimizer :candles "BTC" :1d 30]}]
                     [:funding "BTC" {:priority :low
                                      :start-time-ms 5000
                                      :end-time-ms 10000
                                      :cache-key [:portfolio-optimizer :funding "BTC" 5000 10000]
                                      :dedupe-key [:portfolio-optimizer :funding "BTC" 5000 10000]}]
                     [:vault-details vault-address {:priority :low
                                                    :cache-key [:portfolio-optimizer :vault-details vault-address]
                                                    :dedupe-key [:portfolio-optimizer :vault-details vault-address]}]]
                    @calls))
             (is (= {vault-address vault-details}
                    (:vault-details-by-address bundle)))
             (is (= [vault-address]
                    (mapv :vault-address (get-in bundle [:request-plan :vault-detail-requests]))))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-history-bundle-keeps-request-plan-warnings-test
  (async done
    (let [deps {:request-candle-snapshot! (fn [_coin _opts]
                                            (js/Promise.resolve []))
                :request-market-funding-history! (fn [_coin _opts]
                                                   (js/Promise.resolve []))}]
      (-> (history-client/request-history-bundle!
           deps
           {:universe [{:instrument-id "missing"
                        :market-type :perp}]
            :now-ms 1000})
          (.then
           (fn [bundle]
             (is (= {} (:candle-history-by-coin bundle)))
             (is (= {} (:funding-history-by-coin bundle)))
             (is (= [{:code :missing-history-coin
                      :instrument-id "missing"
                      :market-type :perp}]
                    (:warnings bundle)))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-history-bundle-uses-api-v2-when-feature-flag-enabled-test
  (async done
    (let [calls (atom [])
          fetch-fn (fn [url init]
                     (swap! calls conj [url (js->clj init)])
                     (js/Promise.resolve
                      (json-response
                       200
                       {:contract_version "optimizer-history-api-v2"
                        :request_id "rid-v2"
                        :dataset_version "dv-v2"
                        :status "partial"
                        :common_calendar [1000 2000]
                        :return_calendar [2000]
                        :aligned_returns_by_instrument
                        {"perp:BTC" {:instrument_id "hl:perp:BTC"
                                     :returns [0.1]}}
                        :series_by_instrument
                        {"perp:BTC" {:instrument_id "hl:perp:BTC"
                                     :lineage_kind "native"
                                     :series_kind "market_price"
                                     :points [{:time_ms 1000
                                               :close 100
                                               :return nil}
                                              {:time_ms 2000
                                               :close 110
                                               :return 0.1}]
                                     :funding {:status "available"
                                               :annualized_carry 0.02}
                                     :warnings []}}
                        :warnings [{:code "stale-history"
                                    :instrument_id "perp:BTC"}]})))
          deps {:optimizer-history-api {:enabled? true
                                        :base-url "https://history.test"
                                        :proxy-policy :native-only
                                        :include-aligned-returns? true
                                        :fallback-to-legacy? true}
                :fetch-fn fetch-fn
                :request-id (fn [] "rid-v2")
                :request-candle-snapshot! (fn [& _]
                                            (is false "legacy candles should not be called")
                                            (js/Promise.resolve []))
                :request-market-funding-history! (fn [& _]
                                                   (is false "legacy funding should not be called")
                                                   (js/Promise.resolve []))}
          request {:universe [{:instrument-id "perp:BTC"
                               :market-type :perp
                               :coin "BTC"
                               :optimizer-history/instrument-id "hl:perp:BTC"}]
                   :bars 30
                   :interval :1d}]
      (-> (history-client/request-history-bundle! deps request)
          (.then
           (fn [bundle]
             (is (= 1 (count @calls)))
             (is (= :partial (get-in bundle [:api-v2-history :status])))
             (is (= :stale-history
                    (get-in bundle [:api-v2-history :warnings 0 :code])))
             (is (= [{:code :stale-history
                      :instrument-id "perp:BTC"}]
                    (:warnings bundle)))
             (is (nil? (:candle-history-by-coin bundle)))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-history-bundle-falls-back-to-legacy-on-api-v2-transport-failure-test
  (async done
    (let [legacy-calls (atom [])
          deps {:optimizer-history-api {:enabled? true
                                        :base-url "https://history.test"
                                        :fallback-to-legacy? true}
                :fetch-fn (fn [_url _init]
                            (js/Promise.reject (js/Error. "network down")))
                :request-id (fn [] "rid-fail")
                :request-candle-snapshot!
                (fn [coin opts]
                  (swap! legacy-calls conj [:candle coin opts])
                  (js/Promise.resolve [{:time 1000 :close "100"}
                                       {:time 2000 :close "110"}]))
                :request-market-funding-history!
                (fn [coin opts]
                  (swap! legacy-calls conj [:funding coin opts])
                  (js/Promise.resolve [{:time-ms 1000
                                        :funding-rate-raw "0.001"}]))}]
      (-> (history-client/request-history-bundle!
           deps
           {:universe [{:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :optimizer-history/instrument-id "hl:perp:BTC"}]
            :now-ms 2000})
          (.then
           (fn [bundle]
             (is (= [[:candle "BTC" (:opts (first (get-in bundle [:request-plan :candle-requests])))]
                     [:funding "BTC" (:opts (first (get-in bundle [:request-plan :funding-requests])))]]
                    @legacy-calls))
             (is (= {"BTC" [{:time 1000 :close "100"}
                             {:time 2000 :close "110"}]}
                    (:candle-history-by-coin bundle)))
             (is (= :optimizer-history-api-fallback
                    (get-in bundle [:warnings 0 :code])))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-history-bundle-does-not-fallback-on-usable-api-v2-partial-success-test
  (async done
    (let [legacy-called? (atom false)
          deps {:optimizer-history-api {:enabled? true
                                        :base-url "https://history.test"
                                        :fallback-to-legacy? true}
                :fetch-fn (fn [_url _init]
                            (js/Promise.resolve
                             (json-response
                              200
                              {:contract_version "optimizer-history-api-v2"
                               :request_id "rid-partial"
                               :dataset_version "dv-partial"
                               :status "partial"
                               :series_by_instrument
                               {"perp:BTC" {:instrument_id "hl:perp:BTC"
                                            :lineage_kind "native"
                                            :points [{:time_ms 1000
                                                      :close 100
                                                      :return nil}
                                                     {:time_ms 2000
                                                      :close 110
                                                      :return 0.1}]
                                            :warnings []}}
                               :warnings [{:code "stale-history"
                                           :instrument_id "hl:perp:BTC"}]})))
                :request-id (fn [] "rid-partial")
                :request-candle-snapshot! (fn [& _]
                                            (reset! legacy-called? true)
                                            (js/Promise.resolve []))
                :request-market-funding-history! (fn [& _]
                                                   (reset! legacy-called? true)
                                                   (js/Promise.resolve []))}]
      (-> (history-client/request-history-bundle!
           deps
           {:universe [{:instrument-id "perp:BTC"
                        :market-type :perp
                        :coin "BTC"
                        :optimizer-history/instrument-id "hl:perp:BTC"}]})
          (.then
           (fn [bundle]
             (is (false? @legacy-called?))
             (is (= :partial (get-in bundle [:api-v2-history :status])))
             (is (= :stale-history
                    (get-in bundle [:warnings 0 :code])))
             (done)))
          (.catch (async-support/unexpected-error done))))))
