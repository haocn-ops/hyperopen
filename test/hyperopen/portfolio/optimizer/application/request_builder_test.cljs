(ns hyperopen.portfolio.optimizer.application.request-builder-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.defaults :as defaults]
            [hyperopen.portfolio.optimizer.application.request-builder :as request-builder]))

(defn- near?
  [expected actual]
  (< (js/Math.abs (- expected actual)) 0.0000001))

(def day-ms
  (* 24 60 60 1000))

(defn- day-start-ms
  [day]
  (.getTime (js/Date. (str day "T00:00:00.000Z"))))

(defn- summary-from-points
  [points]
  {:accountValueHistory (mapv (fn [[time-ms account-value _pnl-value]]
                                [time-ms account-value])
                              points)
   :pnlHistory (mapv (fn [[time-ms _account-value pnl-value]]
                       [time-ms pnl-value])
                     points)})

(deftest build-engine-request-keeps-model-layers-separate-and-attaches-bl-prior-test
  (let [request (request-builder/build-engine-request
                 {:draft {:id "draft-1"
                          :universe [{:instrument-id "perp:BTC"
                                      :market-type :perp
                                      :coin "BTC"}
                                     {:instrument-id "spot:PURR"
                                      :market-type :spot
                                      :coin "PURR"}]
                          :objective {:kind :target-return
                                      :target-return 0.2}
                          :return-model {:kind :black-litterman
                                         :views [{:id "view-1"
                                                  :kind :relative
                                                  :long-instrument-id "perp:BTC"
                                                  :short-instrument-id "spot:PURR"
                                                  :return 0.04
                                                  :confidence 0.8}]}
                          :risk-model {:kind :diagonal-shrink
                                       :shrinkage 0.3}
                          :constraints {:long-only? true
                                        :max-asset-weight 0.4}
                          :execution-assumptions {:slippage-bps 25}}
                  :current-portfolio {:capital {:nav-usdc 1000}
                                      :by-instrument {"perp:BTC" {:weight 0.6}
                                                      "spot:PURR" {:weight 0.4}}}
                  :history-data {:candle-history-by-coin {"BTC" [{:time 1000 :close "100"}
                                                                 {:time 2000 :close "110"}]
                                                          "PURR" [{:time 1000 :close "10"}
                                                                  {:time 2000 :close "12"}]}
                                 :funding-history-by-coin {"BTC" [{:time-ms 1000
                                                                   :funding-rate-raw 0.001}]}}
                  :market-cap-by-coin {"BTC" 900
                                       "PURR" 100}
                  :as-of-ms 2500})]
    (is (= :target-return (get-in request [:objective :kind])))
    (is (= :black-litterman (get-in request [:return-model :kind])))
    (is (= :diagonal-shrink (get-in request [:risk-model :kind])))
    (is (= {:id "view-1"
            :kind :relative
            :long-instrument-id "perp:BTC"
            :short-instrument-id "spot:PURR"
            :return 0.04
            :confidence 0.8
            :weights {"perp:BTC" 1
                      "spot:PURR" -1}}
           (dissoc (first (get-in request [:return-model :views]))
                   :confidence-variance)))
    (is (near? 0.2 (get-in request [:return-model :views 0 :confidence-variance])))
    (is (= :market-cap (get-in request [:black-litterman-prior :source])))
    (is (= ["perp:BTC" "spot:PURR"]
           (mapv :instrument-id (:universe request))))
    (is (= ["perp:BTC" "spot:PURR"]
           (mapv :instrument-id (get-in request [:history :eligible-instruments]))))
    (is (= [] (:warnings request)))))

(deftest build-engine-request-surfaces-excluded-history-rows-and-fallback-prior-test
  (let [request (request-builder/build-engine-request
                 {:draft {:universe [{:instrument-id "perp:BTC"
                                      :market-type :perp
                                      :coin "BTC"}
                                     {:instrument-id "spot:MISSING"
                                      :market-type :spot
                                      :coin "MISSING"}]
                          :objective {:kind :minimum-variance}
                          :return-model {:kind :black-litterman}
                          :risk-model {:kind :sample-covariance}
                          :constraints {}}
                  :current-portfolio {:by-instrument {"perp:BTC" {:weight 1}}}
                  :history-data {:candle-history-by-coin {"BTC" [{:time 1000 :close "100"}]}}
                  :market-cap-by-coin {}
                  :as-of-ms 2000})]
    (is (= [] (:universe request)))
    (is (= :fallback-current-portfolio
           (get-in request [:black-litterman-prior :source])))
    (is (= #{:insufficient-candle-history
             :missing-candle-history
             :missing-market-cap-prior}
           (set (map :code (:warnings request)))))
    (is (= ["perp:BTC" "spot:MISSING"]
           (mapv :instrument-id (get-in request [:history :excluded-instruments]))))))

(deftest build-engine-request-uses-vault-details-for-vault-history-test
  (let [vault-address "0x1111111111111111111111111111111111111111"
        vault-instrument-id (str "vault:" vault-address)
        request (request-builder/build-engine-request
                 {:draft {:universe [{:instrument-id "perp:BTC"
                                      :market-type :perp
                                      :coin "BTC"}
                                     {:instrument-id vault-instrument-id
                                      :market-type :vault
                                      :coin vault-instrument-id
                                      :vault-address vault-address}]
                          :objective {:kind :minimum-variance}
                          :return-model {:kind :historical-mean}
                          :risk-model {:kind :diagonal-shrink}
                          :constraints {}}
                  :current-portfolio {:by-instrument {"perp:BTC" {:weight 1}}}
                  :history-data {:candle-history-by-coin
                                 {"BTC" [{:time 1000 :close "100"}
                                         {:time 2000 :close "110"}
                                         {:time 3000 :close "121"}]}
                                 :funding-history-by-coin {}
                                 :vault-details-by-address
                                 {vault-address
                                  {:portfolio
                                   {:all-time
                                    {:accountValueHistory [[1000 100]
                                                           [2000 110]
                                                           [3000 121]]
                                     :pnlHistory [[1000 0]
                                                  [2000 10]
                                                  [3000 21]]}}}}}
                  :market-cap-by-coin {}
                  :as-of-ms 4000})]
    (is (= ["perp:BTC" vault-instrument-id]
           (mapv :instrument-id (:universe request))))
    (is (= ["perp:BTC" vault-instrument-id]
           (mapv :instrument-id (get-in request [:history :eligible-instruments]))))
    (is (near? 0.1 (get-in request [:history :return-series-by-instrument vault-instrument-id 0])))
    (is (= [] (:warnings request)))))

(deftest build-engine-request-carries-derived-one-year-vault-history-test
  (let [prior-all-time-start (day-start-ms "2024-04-30")
        derived-start (day-start-ms "2025-04-30")
        derived-mid (day-start-ms "2025-10-30")
        direct-month-start (day-start-ms "2026-02-28")
        direct-month-mid (day-start-ms "2026-03-31")
        end (day-start-ms "2026-04-30")
        vault-address "0x1111111111111111111111111111111111111111"
        vault-instrument-id (str "vault:" vault-address)
        request (request-builder/build-engine-request
                 {:draft {:universe [{:instrument-id "perp:BTC"
                                      :market-type :perp
                                      :coin "BTC"}
                                     {:instrument-id vault-instrument-id
                                      :market-type :vault
                                      :coin vault-instrument-id
                                      :vault-address vault-address}]
                          :objective {:kind :minimum-variance}
                          :return-model {:kind :historical-mean}
                          :risk-model {:kind :diagonal-shrink}
                          :constraints {}}
                  :current-portfolio {:by-instrument {"perp:BTC" {:weight 1}}}
                  :history-data {:candle-history-by-coin
                                 {"BTC" [{:time derived-start :close "200"}
                                         {:time derived-mid :close "220"}
                                         {:time direct-month-start :close "230"}
                                         {:time direct-month-mid :close "225"}
                                         {:time end :close "242"}]}
                                 :funding-history-by-coin {}
                                 :vault-details-by-address
                                 {vault-address
                                  {:portfolio
                                   {:all-time (summary-from-points
                                               [[prior-all-time-start 80 -20]
                                                [derived-start 100 0]
                                                [derived-mid 110 10]
                                                [end 121 21]])
                                    :month (summary-from-points
                                            [[direct-month-start 100 0]
                                             [direct-month-mid 95 -5]
                                             [end 90 -10]])}}}}
                  :market-cap-by-coin {}
                  :as-of-ms (+ end day-ms)})]
    (is (= [derived-start derived-mid end]
           (get-in request [:history :calendar])))
    (is (= ["perp:BTC" vault-instrument-id]
           (mapv :instrument-id (get-in request [:history :eligible-instruments]))))
    (is (near? 0.1 (get-in request [:history :return-series-by-instrument vault-instrument-id 0])))
    (is (near? 0.1 (get-in request [:history :return-series-by-instrument vault-instrument-id 1])))
    (is (= :not-applicable
           (get-in request [:history :funding-by-instrument vault-instrument-id :source])))
    (is (= [] (:warnings request)))))

(deftest build-engine-request-normalizes-setup-constraint-keys-test
  (let [draft (assoc (defaults/default-draft)
                     :id "draft-constraints"
                     :universe [{:instrument-id "perp:BTC"
                                 :market-type :perp
                                 :coin "BTC"}]
                     :constraints {:long-only? false
                                   :gross-max 1.3
                                   :net-min -0.2
                                   :net-max 0.8
                                   :max-asset-weight 0.6
                                   :allowlist ["perp:BTC"]
                                   :blocklist ["spot:PURR"]
                                   :max-long-weight 0.7
                                   :max-short-weight 0.3
                                   :asset-overrides {"perp:BTC" {:max-weight 0.5
                                                                 :max-long-weight 0.45
                                                                 :max-short-weight 0.2
                                                                 :shortable? true}}
                                   :held-locks ["perp:BTC"]
                                   :perp-leverage {"perp:BTC" {:max-weight 0.4}}
                                   :max-turnover 0.25
                                   :rebalance-tolerance 0.01})
        request (request-builder/build-engine-request
                 {:draft draft
                  :current-portfolio {:by-instrument {"perp:BTC" {:weight 1}}}
                  :history-data {:candle-history-by-coin
                                 {"BTC" [{:time 1000 :close "100"}
                                         {:time 2000 :close "110"}]}
                                 :funding-history-by-coin {}}
                  :market-cap-by-coin {}
                  :as-of-ms 2500})
        constraints (:constraints request)]
    (is (= 1.3 (:gross-leverage constraints)))
    (is (= {:min -0.2 :max 0.8} (:net-exposure constraints)))
    (is (= ["perp:BTC"] (:allowlist constraints)))
    (is (= ["spot:PURR"] (:blocklist constraints)))
    (is (= 0.7 (:max-long-weight constraints)))
    (is (= 0.3 (:max-short-weight constraints)))
    (is (= {"perp:BTC" {:max-weight 0.5
                        :max-long-weight 0.45
                        :max-short-weight 0.2
                        :shortable? true}}
           (:per-asset-overrides constraints)))
    (is (= ["perp:BTC"] (:held-position-locks constraints)))
    (is (= {"perp:BTC" {:max-weight 0.4}}
           (:per-perp-leverage-caps constraints)))
    (is (= 0.25 (:max-turnover constraints)))
    (is (not (contains? constraints :gross-max)))
    (is (not (contains? constraints :net-min)))
    (is (not (contains? constraints :asset-overrides)))))

(deftest build-engine-request-preserves-disabled-turnover-cap-test
  (let [draft (-> (defaults/default-draft)
                  (assoc :id "draft-no-turnover-cap"
                         :universe [{:instrument-id "perp:BTC"
                                     :market-type :perp
                                     :coin "BTC"}])
                  (assoc-in [:constraints :max-turnover] nil))
        request (request-builder/build-engine-request
                 {:draft draft
                  :current-portfolio {:by-instrument {"perp:BTC" {:weight 1}}}
                  :history-data {:candle-history-by-coin
                                 {"BTC" [{:time 1000 :close "100"}
                                         {:time 2000 :close "110"}]}
                                 :funding-history-by-coin {}}
                  :market-cap-by-coin {}
                  :as-of-ms 2500})
        constraints (:constraints request)]
    (is (contains? constraints :max-turnover))
    (is (nil? (:max-turnover constraints)))))

(deftest build-engine-request-folds-in-conservative-history-assumptions-test
  (let [draft (-> (defaults/default-draft)
                  (assoc :id "draft-history-assumptions"
                         :universe [{:instrument-id "perp:BTC"
                                     :market-type :perp
                                     :coin "BTC"}
                                    {:instrument-id "perp:NEW"
                                     :market-type :perp
                                     :coin "NEW"}
                                    {:instrument-id "perp:PXY"
                                     :market-type :perp
                                     :coin "PXY"}]
                         :history-assumptions
                         {"perp:NEW" {:behavior :conservative
                                      :expected-return nil
                                      :volatility 0.9
                                      :max-weight 0.03
                                      :correlation-floor 0.75}
                          "perp:PXY" {:behavior :proxy
                                      :expected-return 0.25
                                      :volatility 0.8
                                      :proxy-instrument-id "perp:BTC"
                                      :relationship :medium
                                      :max-weight 0.05}})
                  (assoc-in [:constraints :asset-overrides]
                            {"perp:NEW" {:max-weight 0.5}}))
        request (request-builder/build-engine-request
                 {:draft draft
                  ;; Only BTC has candle history; NEW and PXY are dropped by alignment.
                  :history-data {:candle-history-by-coin
                                 {"BTC" [{:time 1000 :close "100"}
                                         {:time 2000 :close "110"}]}
                                 :funding-history-by-coin {}}
                  :market-cap-by-coin {}
                  :as-of-ms 2500})
        universe-ids (set (map :instrument-id (:universe request)))
        constraints (:constraints request)]
    (is (contains? universe-ids "perp:NEW")
        "A complete conservative assumption re-admits the no-history asset to the engine universe.")
    (is (not (contains? universe-ids "perp:PXY"))
        "A proxy assumption is collected but not yet engine-backed, so its asset stays dropped.")
    (is (= 0.03 (get-in constraints [:per-asset-overrides "perp:NEW" :max-weight]))
        "The conservative cap min-merges into the existing per-asset override (0.5 vs 0.03 -> 0.03).")
    (is (= 0.9 (get-in request [:history-assumptions "perp:NEW" :volatility])))
    (is (= 0.75 (get-in request [:history-assumptions "perp:NEW" :correlation-floor])))
    (is (= 0.65 (get-in request [:history-assumptions "perp:PXY" :implied-correlation]))
        "A proxy relationship resolves to its implied correlation on the request.")))

(deftest build-engine-request-treats-empty-allowlist-as-unbounded-test
  (let [draft (assoc (defaults/default-draft)
                     :id "draft-default-constraints"
                     :universe [{:instrument-id "perp:BTC"
                                 :market-type :perp
                                 :coin "BTC"}])
        request (request-builder/build-engine-request
                 {:draft draft
                  :current-portfolio {:by-instrument {"perp:BTC" {:weight 1}}}
                  :history-data {:candle-history-by-coin
                                 {"BTC" [{:time 1000 :close "100"}
                                         {:time 2000 :close "110"}]}
                                 :funding-history-by-coin {}}
                  :market-cap-by-coin {}
                  :as-of-ms 2500})
        constraints (:constraints request)]
    (is (nil? (:allowlist constraints)))
    (is (= [] (:blocklist constraints)))
    (is (= false (:include-spot? constraints)))
    (is (= 2.0 (:gross-leverage constraints)))
    (is (= {:min 1.0 :max 1.0} (:net-exposure constraints)))
    (is (= 1.0 (:max-turnover constraints)))))

(deftest build-engine-request-normalizes-execution-assumptions-test
  (let [draft (assoc (defaults/default-draft)
                     :id "draft-execution-assumptions"
                     :universe [{:instrument-id "perp:BTC"
                                 :market-type :perp
                                 :coin "BTC"}]
                     :execution-assumptions {:default-order-type :market
                                             :slippage-fallback-bps 25
                                             :fee-mode :taker})
        request (request-builder/build-engine-request
                 {:draft draft
                  :current-portfolio {:by-instrument {"perp:BTC" {:weight 1}}}
                  :history-data {:candle-history-by-coin
                                 {"BTC" [{:time 1000 :close "100"}
                                         {:time 2000 :close "110"}]}
                                 :funding-history-by-coin {}}
                  :market-cap-by-coin {}
                  :as-of-ms 2500})
        assumptions (:execution-assumptions request)]
    (is (= :market (:default-order-type assumptions)))
    (is (= 25 (:fallback-slippage-bps assumptions)))
    (is (= :taker (:fee-mode assumptions)))
    (is (not (contains? assumptions :slippage-fallback-bps)))))

(deftest build-engine-request-does-not-use-selected-cache-for-outside-current-history-test
  (let [request (request-builder/build-engine-request
                 {:draft {:id "draft-outside-current-cache"
                          :universe [{:instrument-id "perp:BTC"
                                      :market-type :perp
                                      :coin "BTC"}
                                     {:instrument-id "perp:ETH"
                                      :market-type :perp
                                      :coin "ETH"}]
                          :objective {:kind :minimum-variance}
                          :return-model {:kind :historical-mean}
                          :risk-model {:kind :diagonal-shrink}
                          :constraints {:long-only? true}}
                  :current-portfolio {:capital {:nav-usdc 10000}
                                      :by-instrument {"perp:HYPE"
                                                      {:instrument-id "perp:HYPE"
                                                       :market-type :perp
                                                       :coin "HYPE"
                                                       :weight 0.25}}}
                  :history-data {:candle-history-by-coin
                                 {"BTC" [{:time 1000 :close "100"}
                                         {:time 2000 :close "110"}]
                                  "ETH" [{:time 1000 :close "200"}
                                         {:time 2000 :close "220"}]
                                  "HYPE" [{:time 1000 :close "10"}
                                          {:time 2000 :close "12"}]}
                                 :funding-history-by-coin {}}
                  :market-cap-by-coin {}
                  :as-of-ms 2500})]
    (is (= ["perp:HYPE"]
           (mapv :instrument-id (:current-portfolio-universe request))))
    (is (nil? (:current-portfolio-history request))
        "Outside-universe current history must come from the separate current bundle, not stale selected-cache data.")))

(defn- black-litterman-request
  [views]
  (request-builder/build-engine-request
   {:draft {:id "draft-bl-views"
            :universe [{:instrument-id "perp:ETH"
                        :market-type :perp
                        :coin "ETH"}
                       {:instrument-id "perp:SOL"
                        :market-type :perp
                        :coin "SOL"}
                       {:instrument-id "perp:HYPE"
                        :market-type :perp
                        :coin "HYPE"}]
            :objective {:kind :max-sharpe}
            :return-model {:kind :black-litterman
                           :views views}
            :risk-model {:kind :sample-covariance}
            :constraints {}}
    :current-portfolio {:by-instrument {"perp:ETH" {:weight 0.5}
                                        "perp:SOL" {:weight 0.3}
                                        "perp:HYPE" {:weight 0.2}}}
    :history-data {:candle-history-by-coin
                   {"ETH" [{:time 1000 :close "100"}
                           {:time 2000 :close "105"}
                           {:time 3000 :close "110"}]
                    "SOL" [{:time 1000 :close "50"}
                           {:time 2000 :close "52"}
                           {:time 3000 :close "55"}]
                    "HYPE" [{:time 1000 :close "10"}
                            {:time 2000 :close "12"}
                            {:time 3000 :close "14"}]}
                   :funding-history-by-coin {}}
    :market-cap-by-coin {"ETH" 600
                         "SOL" 300
                         "HYPE" 100}
    :as-of-ms 4000}))

(deftest build-engine-request-normalizes-new-black-litterman-view-shapes-test
  (let [request (black-litterman-request
                 [{:id "view-abs"
                   :kind :absolute
                   :instrument-id "perp:HYPE"
                   :return 0.45
                   :confidence 0.75
                   :horizon :1y
                   :notes "Momentum conviction"}
                  {:id "view-rel-out"
                   :kind :relative
                   :instrument-id "perp:ETH"
                   :comparator-instrument-id "perp:SOL"
                   :direction :outperform
                   :return 0.05
                   :confidence 0.5
                   :horizon :6m}
                  {:id "view-rel-under"
                   :kind :relative
                   :instrument-id "perp:ETH"
                   :comparator-instrument-id "perp:SOL"
                   :direction :underperform
                   :return 0.03
                   :confidence 0.25
                   :horizon :3m}])
        [absolute-view outperform-view underperform-view]
        (get-in request [:return-model :views])]
    (is (= {:id "view-abs"
            :kind :absolute
            :instrument-id "perp:HYPE"
            :return 0.45
            :confidence 0.75
            :weights {"perp:HYPE" 1}}
           (select-keys absolute-view
                        [:id :kind :instrument-id :return :confidence :weights])))
    (is (near? 0.25 (:confidence-variance absolute-view)))
    (is (= {:id "view-rel-out"
            :kind :relative
            :instrument-id "perp:ETH"
            :comparator-instrument-id "perp:SOL"
            :direction :outperform
            :return 0.05
            :confidence 0.5
            :weights {"perp:ETH" 1
                      "perp:SOL" -1}}
           (select-keys outperform-view
                        [:id :kind :instrument-id :comparator-instrument-id :direction
                         :return :confidence :weights])))
    (is (near? 0.5 (:confidence-variance outperform-view)))
    (is (= {:id "view-rel-under"
            :kind :relative
            :instrument-id "perp:ETH"
            :comparator-instrument-id "perp:SOL"
            :direction :underperform
            :return 0.03
            :confidence 0.25
            :weights {"perp:ETH" -1
                      "perp:SOL" 1}}
           (select-keys underperform-view
                        [:id :kind :instrument-id :comparator-instrument-id :direction
                         :return :confidence :weights])))
    (is (near? 0.75 (:confidence-variance underperform-view)))
    (is (= [] (:warnings request)))))

(deftest build-engine-request-drops-malformed-legacy-black-litterman-views-with-warning-test
  (let [request (black-litterman-request
                 [{:id "view-good"
                   :kind :absolute
                   :instrument-id "perp:HYPE"
                   :return 0.2
                   :confidence 0.75}
                  {:id "legacy-bad"
                   :kind :relative
                   :long-instrument-id "perp:ETH"
                   :short-instrument-id "perp:ETH"
                   :return 0.04
                   :confidence 0.8}])
        warnings (filterv #(= :invalid-black-litterman-view (:code %))
                          (:warnings request))]
    (is (= ["view-good"]
           (mapv :id (get-in request [:return-model :views]))))
    (is (= [{:code :invalid-black-litterman-view
             :view-id "legacy-bad"}]
           (mapv #(select-keys % [:code :view-id]) warnings)))))

(deftest build-engine-request-drops-black-litterman-views-outside-eligible-universe-test
  (let [request (black-litterman-request
                 [{:id "stale-btc"
                   :kind :absolute
                   :instrument-id "BTC"
                   :return 0.2
                   :confidence 0.75}
                  {:id "valid-hype"
                   :kind :absolute
                   :instrument-id "perp:HYPE"
                   :return 0.35
                   :confidence 0.75}])
        warnings (filterv #(= :black-litterman-view-outside-universe (:code %))
                          (:warnings request))]
    (is (= ["valid-hype"]
           (mapv :id (get-in request [:return-model :views]))))
    (is (= [{:code :black-litterman-view-outside-universe
             :view-id "stale-btc"
             :instrument-ids ["BTC"]}]
           (mapv #(select-keys % [:code :view-id :instrument-ids]) warnings)))))
