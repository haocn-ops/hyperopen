(ns hyperopen.portfolio.optimizer.application.history-loader-api-v2-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.set :as set]
            [hyperopen.portfolio.optimizer.application.history-loader :as history-loader]
            [hyperopen.portfolio.optimizer.application.history-loader.api-v2 :as api-v2]))

(defn near?
  [expected actual]
  (< (js/Math.abs (- expected actual)) 0.000001))

(def day-ms
  (* 24 60 60 1000))

(defn- day-start-ms
  [day]
  (.getTime (js/Date. (str day "T00:00:00.000Z"))))

(deftest normalize-discovery-preserves-backend-and-local-identities-test
  (let [discovery (api-v2/normalize-discovery
                   {:contract_version "optimizer-history-api-v2"
                    :request_id "rid-discovery"
                    :dataset_version "dv-1"
                    :status "partial"
                    :instruments
                    [{:instrument_id "hl:perp:BTC"
                      :display_symbol "BTC"
                      :instrument_kind "hl_perp"
                      :funding_enabled true
                      :aliases {:hyperopen_market_key "perp:BTC"}
                      :proxy {:available true
                              :proxy_mapping_id "proxy-review:btc"}
                      :history {:status "available"
                                :quality_status "passed"
                                :observation_count 0
                                :max_native_lookback_days 0
                                :native_only {:status "available"
                                              :observation_count 0
                                              :max_lookback_days 0}}}]
                    :warnings [{:code "validation-failed"
                                :message "discovery warning"}]})
        candidate (api-v2/with-discovery-metadata
                    {:key "perp:BTC"
                     :market-type :perp
                     :coin "BTC"}
                    discovery)]
    (is (= :partial (:status discovery)))
    (is (= "hl:perp:BTC"
           (get-in discovery [:backend-id-by-local-id "perp:BTC"])))
    (is (= "perp:BTC"
           (get-in discovery
                   [:instruments-by-backend-id
                    "hl:perp:BTC"
                    :aliases
                    :hyperopen-market-key])))
    (is (= 0
           (get-in discovery
                   [:instruments-by-backend-id
                    "hl:perp:BTC"
                    :history
                    :observation-count])))
    (is (= [{:code :validation-failed
             :message "discovery warning"}]
           (:warnings discovery)))
    (is (= {:key "perp:BTC"
            :market-type :perp
            :coin "BTC"
            :optimizer-history/instrument-id "hl:perp:BTC"
            :optimizer-history/display-symbol "BTC"
            :optimizer-history/instrument-kind :hl-perp
            :optimizer-history/history-status :available
            :optimizer-history/quality-status :passed
            :optimizer-history/proxy {:available true
                                      :proxy-mapping-id "proxy-review:btc"}}
           candidate))))

(deftest with-discovery-metadata-resolves-hip3-alias-keys-test
  (let [discovery (api-v2/normalize-discovery
                   {:contract_version "optimizer-history-api-v2"
                    :status "ok"
                    :instruments
                    [{:instrument_id "hl:hip3:xyz:GOLD"
                      :display_symbol "xyz:GOLD"
                      :kind "hip3"
                      :instrument_kind "hl_hip3"
                      :aliases {:hyperopen_market_key "hip3:xyz:GOLD"}
                      :history {:status "stale"
                                :quality_status "passed"}}]
                    :warnings []})
        candidate (api-v2/with-discovery-metadata
                    {:key "perp:xyz:GOLD"
                     :instrument-id "perp:xyz:GOLD"
                     :market-type :perp
                     :coin "xyz:GOLD" :base "GOLD"
                     :dex "xyz"
                     :hip3? true}
                    discovery)]
    (is (= {:optimizer-history/instrument-id "hl:hip3:xyz:GOLD"
            :optimizer-history/display-symbol "xyz:GOLD"
            :optimizer-history/instrument-kind :hl-hip3
            :optimizer-history/history-status :stale}
           (select-keys candidate [:optimizer-history/instrument-id :optimizer-history/display-symbol :optimizer-history/instrument-kind :optimizer-history/history-status])))))

(deftest align-api-v2-history-uses-api-aligned-returns-and-funding-policy-test
  (let [universe [{:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}
                  {:instrument-id "spot:PURR/USDC"
                   :market-type :spot
                   :coin "PURR/USDC"
                   :optimizer-history/instrument-id "hl:spot:PURR/USDC"}]
        api-body {:contract_version "optimizer-history-api-v2"
                  :request_id "rid-bundle"
                  :dataset_version "dv-2"
                  :status "partial"
                  :common_calendar [1000 2000 3000]
                  :return_calendar [2000 3000]
                  :aligned_returns_by_instrument
                  {"perp:BTC" {:instrument_id "hl:perp:BTC"
                               :returns [0.05 0.02]}
                   "spot:PURR/USDC" {:instrument_id "hl:spot:PURR/USDC"
                                     :returns [0.03 0.04]}}
                  :series_by_instrument
                  {"perp:BTC" {:instrument_id "hl:perp:BTC"
                               :lineage_kind "stitched_native_proxy"
                               :series_kind "market_price"
                               :points [{:time_ms 1000 :close 100 :return nil}
                                        {:time_ms 2000 :close 120 :return 0.05}
                                        {:time_ms 3000 :close 122.4 :return 0.02}]
                               :funding {:status "available"
                                         :annualized_carry 0.0123}
                               :warnings [{:code "proxy-history-used"}]}
                   "spot:PURR/USDC" {:instrument_id "hl:spot:PURR/USDC"
                                     :lineage_kind "native"
                                     :series_kind "market_price"
                                     :points [{:time_ms 1000 :close 10 :return nil}
                                              {:time_ms 2000 :close 10.3 :return 0.03}
                                              {:time_ms 3000 :close 10.712 :return 0.04}]
                                     :funding {:status "missing"}
                                     :warnings []}}
                  :warnings [{:code "stale-history"
                              :instrument_id "perp:BTC"}]}
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    api-body)
        aligned (history-loader/align-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :as-of-ms 4000
                  :stale-after-ms 10000})]
    (is (= [1000 2000 3000] (:calendar aligned)))
    (is (= [2000 3000] (:return-calendar aligned)))
    (is (near? 0.05
               (get-in aligned [:return-series-by-instrument "perp:BTC" 0])))
    (is (near? 0.02
               (get-in aligned [:return-series-by-instrument "perp:BTC" 1])))
    (is (near? 0.03
               (get-in aligned [:return-series-by-instrument "spot:PURR/USDC" 0])))
    (is (= :api-v2-aligned-returns
           (get-in aligned [:alignment-source :kind])))
    (is (= :history-api-v2
           (get-in aligned [:funding-by-instrument "perp:BTC" :source])))
    (is (= 0.0123
           (get-in aligned [:funding-by-instrument "perp:BTC" :annualized-carry])))
    (is (= :not-applicable
           (get-in aligned [:funding-by-instrument "spot:PURR/USDC" :source])))
    (is (= #{:proxy-history-used :stale-history}
           (set (map :code (:warnings aligned)))))))

(deftest align-api-v2-history-keeps-selected-id-for-proxy-requested-equity-calendar-test
  (let [friday (day-start-ms "2026-05-08")
        saturday (+ friday day-ms)
        sunday (+ friday (* 2 day-ms))
        monday (+ friday (* 3 day-ms))
        universe [{:instrument-id "perp:xyz:SP500"
                   :market-type :perp
                   :coin "xyz:SP500"
                   :optimizer-history/instrument-id "hl:hip3:xyz:SP500"
                   :optimizer-history/proxy
                   {:mapping-kind :stitched-native-proxy
                    :proxy-instrument-id "external:tiingo:SPY"
                    :provider "tiingo"
                    :optimizer-proxy-policy "default_allowed"}}
                  {:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}]
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-spy-aligned"
                     :dataset_version "dv-spy"
                     :status "partial"
                     :common_calendar [friday monday]
                     :return_calendar [monday]
                     :aligned_returns_by_instrument
                     {"perp:xyz:SP500" {:instrument_id "external:tiingo:SPY"
                                        :returns [0.005]}
                      "perp:BTC" {:instrument_id "hl:perp:BTC"
                                  :returns [0.01]}}
                     :series_by_instrument
                     {"perp:xyz:SP500" {:instrument_id "external:tiingo:SPY"
                                        :lineage_kind "external"
                                        :series_kind "external_proxy_price"
                                        :points [{:time_ms friday
                                                  :close 600
                                                  :return nil
                                                  :component "external"}
                                                 {:time_ms monday
                                                  :close 603
                                                  :return 0.005
                                                  :component "external"}]
                                        :funding {:status "not_applicable"}
                                        :warnings [{:code "insufficient-candle-history"
                                                    :instrument_id "external:tiingo:SPY"}]}
                      "perp:BTC" {:instrument_id "hl:perp:BTC"
                                  :lineage_kind "native"
                                  :series_kind "market_price"
                                  :points [{:time_ms friday
                                            :close 100
                                            :return nil}
                                           {:time_ms saturday
                                            :close 101
                                            :return 0.01}
                                           {:time_ms sunday
                                            :close 102
                                            :return (/ 1 101)}
                                           {:time_ms monday
                                            :close 103
                                            :return 0.01}]
                                  :funding {:status "available"
                                            :annualized_carry 0.02}
                                  :warnings []}}
                     :warnings [{:code "insufficient-candle-history"
                                 :instrument_id "external:tiingo:SPY"}]})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= :api-v2-aligned-returns
           (get-in aligned [:alignment-source :kind])))
    (is (= ["perp:xyz:SP500" "perp:BTC"]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (= [friday monday] (:calendar aligned)))
    (is (= [monday] (:return-calendar aligned)))
    (is (= {"perp:xyz:SP500" [0.005]
            "perp:BTC" [0.01]}
           (:return-series-by-instrument aligned)))
    (is (= []
           (filterv #(= :insufficient-candle-history (:code %))
                    (:warnings aligned))))))

(deftest align-api-v2-history-preserves-native-raw-history-for-mixed-frequency-risk-test
  (let [d0 (day-start-ms "2025-05-28")
        vault-id "vault:0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        daily-points (mapv (fn [idx]
                             {:time_ms (+ d0 (* idx day-ms))
                              :close (+ 100 idx)
                              :return (when (pos? idx)
                                        (/ 1 (+ 99 idx)))})
                           (range 29))
        universe [{:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}
                  {:instrument-id vault-id
                   :market-type :vault
                   :coin vault-id
                   :vault-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
                   :optimizer-history/instrument-id "hl:vault:0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"}]
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-mixed-frequency"
                     :dataset_version "dv-mixed-frequency"
                     :status "partial"
                     :common_calendar [d0
                                       (+ d0 (* 14 day-ms))
                                       (+ d0 (* 28 day-ms))]
                     :return_calendar [(+ d0 (* 14 day-ms))
                                       (+ d0 (* 28 day-ms))]
                     :aligned_returns_by_instrument
                     {"hl:perp:BTC" {:instrument_id "hl:perp:BTC"
                                     :returns [0.14 0.1228070175438596]}
                      "hl:vault:0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
                      {:instrument_id "hl:vault:0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
                       :returns [0.02 0.02941176470588225]}}
                     :series_by_instrument
                     {"hl:perp:BTC" {:instrument_id "hl:perp:BTC"
                                     :lineage_kind "native"
                                     :series_kind "market_price"
                                     :points daily-points
                                     :funding {:status "available"
                                               :annualized_carry 0.01}
                                     :warnings []}
                      "hl:vault:0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
                      {:instrument_id "hl:vault:0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
                       :lineage_kind "native"
                       :series_kind "vault_return_index"
                       :points [{:time_ms d0 :close 100 :return nil}
                                {:time_ms (+ d0 (* 14 day-ms)) :close 102 :return 0.02}
                                {:time_ms (+ d0 (* 28 day-ms)) :close 105 :return 0.02941176470588225}]
                       :funding {:status "not_applicable"}
                       :warnings []}}
                     :warnings []})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= :api-v2-aligned-returns
           (get-in aligned [:alignment-source :kind])))
    (is (= 29
           (count (get-in aligned [:raw-price-series-by-instrument "perp:BTC"]))))
    (is (= 3
           (count (get-in aligned [:raw-price-series-by-instrument vault-id]))))
    (is (= :dense
           (get-in aligned [:cadence-by-instrument "perp:BTC" :kind])))
    (is (= :sparse
           (get-in aligned [:cadence-by-instrument vault-id :kind])))
    (is (= 28
           (count (get-in aligned [:expected-return-series-by-instrument "perp:BTC"]))))
    (is (= 2
           (count (get-in aligned [:expected-return-series-by-instrument vault-id]))))
    (is (= 28
           (count (get-in aligned [:expected-return-intervals-by-instrument "perp:BTC"]))))
    (is (= 2
           (count (get-in aligned [:expected-return-intervals-by-instrument vault-id]))))
    (is (= :mixed-frequency
           (get-in aligned [:risk-estimation :kind])))))

(deftest align-api-v2-history-does-not-recompute-nil-boundary-returns-test
  (let [universe [{:instrument-id "perp:NEW"
                   :market-type :perp
                   :coin "NEW"
                   :optimizer-history/instrument-id "hl:perp:NEW"}]
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-no-aligned"
                     :dataset_version "dv-3"
                     :status "partial"
                     :series_by_instrument
                     {"perp:NEW" {:instrument_id "hl:perp:NEW"
                                  :lineage_kind "stitched_native_proxy"
                                  :series_kind "market_price"
                                  :points [{:time_ms 1000 :close 10 :return nil}
                                           {:time_ms 2000 :close 100 :return nil}
                                           {:time_ms 3000 :close 110 :return 0.1}]
                                  :funding {:status "missing"}
                                  :warnings [{:code "funding-history-missing"}]}}
                     :warnings []})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= [1000 2000 3000] (:calendar aligned)))
    (is (= [3000] (:return-calendar aligned)))
    (is (= [0.1]
           (get-in aligned [:return-series-by-instrument "perp:NEW"])))
    (is (= :missing-market-funding-history
           (get-in aligned [:funding-by-instrument "perp:NEW" :source])))
    (is (= #{:funding-history-missing}
           (set (map :code (:warnings aligned)))))))

(deftest align-api-v2-history-accepts-aligned-returns-with-sparse-or-missing-points-test
  (let [universe [{:instrument-id "perp:ETH"
                   :market-type :perp
                   :coin "ETH"
                   :optimizer-history/instrument-id "hl:perp:ETH"}
                  {:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}]
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-aligned-sparse"
                     :dataset_version "dv-return-first"
                     :status "partial"
                     :common_calendar [1000 2000 3000]
                     :return_calendar [2000 3000]
                     :aligned_returns_by_instrument
                     {"perp:ETH" {:instrument_id "hl:perp:ETH"
                                  :returns [0.02 0.03]}
                      "perp:BTC" {:instrument_id "hl:perp:BTC"
                                  :returns [-0.01 0.04]}}
                     :series_by_instrument
                     {"perp:ETH" {:instrument_id "hl:perp:ETH"
                                  :lineage_kind "native"
                                  :series_kind "market_price"
                                  :points [{:time_ms 3000
                                            :close 2200
                                            :return 0.03}]
                                  :funding {:status "available"
                                            :annualized_carry 0.01}
                                  :warnings [{:code "insufficient-candle-history"
                                              :instrument_id "hl:perp:ETH"}]}
                      "perp:BTC" {:instrument_id "hl:perp:BTC"
                                  :lineage_kind "stitched_native_proxy"
                                  :series_kind "market_price"
                                  :points []
                                  :funding {:status "available"
                                            :annualized_carry 0.002}
                                  :warnings [{:code "missing-candle-history"
                                              :instrument_id "hl:perp:BTC"}]}}
                     :warnings []})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= ["perp:ETH" "perp:BTC"]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (empty? (:excluded-instruments aligned)))
    (is (= [2000 3000] (:return-calendar aligned)))
    (is (= [0.02 0.03]
           (get-in aligned [:return-series-by-instrument "perp:ETH"])))
    (is (= [-0.01 0.04]
           (get-in aligned [:return-series-by-instrument "perp:BTC"])))
    (is (= :api-v2-aligned-returns
           (get-in aligned [:alignment-source :kind])))
    (is (empty? (set/intersection
                 #{:missing-candle-history :insufficient-candle-history}
                 (set (map :code (:warnings aligned))))))))

(deftest normalize-api-v2-history-canonicalizes-backend-keyed-series-test
  (let [universe [{:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}
                  {:instrument-id "perp:ETH"
                   :market-type :perp
                   :coin "ETH"
                   :optimizer-history/instrument-id "hl:perp:ETH"}]
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-backend-keyed"
                     :dataset_version "dv-backend-keyed"
                     :status "partial"
                     :common_calendar [1000 2000 3000]
                     :return_calendar [2000 3000]
                     :aligned_returns_by_instrument
                     {"hl:perp:BTC" {:instrument_id "hl:perp:BTC"
                                     :returns [-0.01 0.04]}
                      "perp:ETH" {:instrument_id "hl:perp:ETH"
                                  :returns [0.02 0.03]}}
                     :series_by_instrument
                     {"hl:perp:BTC" {:instrument_id "hl:perp:BTC"
                                     :lineage_kind "stitched_native_proxy"
                                     :series_kind "market_price"
                                     :points []
                                     :funding {:status "available"
                                               :annualized_carry 0.002}
                                     :warnings [{:code "missing-candle-history"
                                                 :instrument_id "hl:perp:BTC"}]}
                      "perp:ETH" {:instrument_id "hl:perp:ETH"
                                  :lineage_kind "native"
                                  :series_kind "market_price"
                                  :points [{:time_ms 3000
                                            :close 2200
                                            :return 0.03}]
                                  :funding {:status "available"
                                            :annualized_carry 0.01}
                                  :warnings []}}
                     :warnings []})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= ["perp:BTC" "perp:ETH"]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (empty? (:excluded-instruments aligned)))
    (is (= [-0.01 0.04]
           (get-in aligned [:return-series-by-instrument "perp:BTC"])))
    (is (empty? (filter #(= :missing-return-history (:code %))
                        (:warnings aligned))))))

(deftest align-api-v2-history-reports-return-history-warning-when-aligned-returns-are-missing-test
  (let [universe [{:instrument-id "perp:ETH"
                   :market-type :perp
                   :coin "ETH"
                   :optimizer-history/instrument-id "hl:perp:ETH"}
                  {:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}]
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-missing-return"
                     :dataset_version "dv-return-first"
                     :status "partial"
                     :common_calendar [1000 2000 3000]
                     :return_calendar [2000 3000]
                     :aligned_returns_by_instrument
                     {"perp:ETH" {:instrument_id "hl:perp:ETH"
                                  :returns [0.02 0.03]}}
                     :series_by_instrument
                     {"perp:ETH" {:instrument_id "hl:perp:ETH"
                                  :lineage_kind "native"
                                  :series_kind "market_price"
                                  :points [{:time_ms 1000
                                            :close 2100
                                            :return nil}
                                           {:time_ms 2000
                                            :close 2142
                                            :return 0.02}
                                           {:time_ms 3000
                                            :close 2206.26
                                            :return 0.03}]
                                  :funding {:status "available"
                                            :annualized_carry 0.01}
                                  :warnings []}
                      "perp:BTC" {:instrument_id "hl:perp:BTC"
                                  :lineage_kind "native"
                                  :series_kind "market_price"
                                  :points []
                                  :funding {:status "available"
                                            :annualized_carry 0.002}
                                  :warnings [{:code "missing-candle-history"
                                              :instrument_id "hl:perp:BTC"}]}}
                     :warnings []})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= ["perp:ETH"]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (= ["perp:BTC"]
           (mapv :instrument-id (:excluded-instruments aligned))))
    (is (= #{:missing-return-history}
           (set (map :code (:warnings aligned)))))))

(deftest align-api-v2-history-blocks-missing-and-ambiguous-series-test
  (let [universe [{:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}
                  {:instrument-id "perp:MISSING"
                   :market-type :perp
                   :coin "MISSING"
                   :optimizer-history/instrument-id "hl:perp:MISSING"}
                  {:instrument-id "perp:UNKNOWN"
                   :market-type :perp
                   :coin "UNKNOWN"}]
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-partial"
                     :dataset_version "dv-4"
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
                                  :points [{:time_ms 1000 :close 100 :return nil}
                                           {:time_ms 2000 :close 110 :return 0.1}]
                                  :funding {:status "available"
                                            :annualized_carry 0}}
                      "perp:MISSING" {:instrument_id "hl:perp:MISSING"
                                      :lineage_kind "missing"
                                      :series_kind "market_price"
                                      :points []
                                      :warnings [{:code "missing-candle-history"}]}}
                     :warnings []})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= ["perp:BTC"]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (= ["perp:MISSING" "perp:UNKNOWN"]
           (mapv :instrument-id (:excluded-instruments aligned))))
    (is (= #{:missing-return-history :identity-ambiguous}
           (set (map :code (:warnings aligned)))))))
