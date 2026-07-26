(ns hyperopen.portfolio.optimizer.application.history-loader-vaults-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.history-loader :as history-loader]
            [hyperopen.portfolio.optimizer.application.history-loader-fixtures :as fixtures]))

(deftest align-history-inputs-aligns-vault-detail-return-history-test
  (let [vault-address "0x1111111111111111111111111111111111111111"
        vault-instrument-id (str "vault:" vault-address)
        aligned (history-loader/align-history-inputs
                 {:universe [{:instrument-id "perp:BTC"
                              :market-type :perp
                              :coin "BTC"}
                             {:instrument-id vault-instrument-id
                              :market-type :vault
                              :coin vault-instrument-id
                              :vault-address vault-address}]
                  :candle-history-by-coin {"BTC" [{:time 1000 :close "100"}
                                                  {:time 2000 :close "110"}
                                                  {:time 3000 :close "121"}]}
                  :vault-details-by-address {vault-address
                                             {:portfolio
                                              {:all-time
                                               {:accountValueHistory [[1000 100]
                                                                      [2000 110]
                                                                      [3000 121]]
                                                :pnlHistory [[1000 0]
                                                             [2000 10]
                                                             [3000 21]]}}}}
                  :as-of-ms 4000
                  :stale-after-ms 5000
                  :funding-periods-per-year 100})]
    (is (= [1000 2000 3000] (:calendar aligned)))
    (is (= ["perp:BTC" vault-instrument-id]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (fixtures/near? 0.1 (get-in aligned [:return-series-by-instrument vault-instrument-id 0])))
    (is (fixtures/near? 0.1 (get-in aligned [:return-series-by-instrument vault-instrument-id 1])))
    (is (= :not-applicable
           (get-in aligned [:funding-by-instrument vault-instrument-id :source])))
    (is (= [] (:excluded-instruments aligned)))
    (is (= [] (:warnings aligned)))))

(deftest align-history-inputs-preserves-native-raw-history-for-mixed-frequency-risk-test
  (let [d0 (fixtures/day-start-ms "2025-05-28")
        vault-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        vault-instrument-id (str "vault:" vault-address)
        market-history (mapv (fn [idx]
                               [(+ d0 (* idx fixtures/day-ms))
                                (+ 100 idx)])
                             (range 29))
        aligned (history-loader/align-history-inputs
                 {:universe [{:instrument-id "perp:BTC"
                              :market-type :perp
                              :coin "BTC"}
                             {:instrument-id vault-instrument-id
                              :market-type :vault
                              :coin vault-instrument-id
                              :vault-address vault-address}]
                  :candle-history-by-coin {"BTC" (fixtures/candle-rows market-history)}
                  :vault-details-by-address
                  {vault-address
                   {:portfolio
                    {:one-year
                     (fixtures/summary-from-points
                      [[d0 100 0]
                       [(+ d0 (* 14 fixtures/day-ms)) 102 2]
                       [(+ d0 (* 28 fixtures/day-ms)) 105 5]])}}}
                  :as-of-ms (+ d0 (* 29 fixtures/day-ms))
                  :stale-after-ms (* 2 fixtures/day-ms)})]
    (is (= 3 (count (:calendar aligned))))
    (is (= 2 (count (:return-calendar aligned))))
    (is (= 29
           (count (get-in aligned [:raw-price-series-by-instrument "perp:BTC"]))))
    (is (= 3
           (count (get-in aligned [:raw-price-series-by-instrument vault-instrument-id]))))
    (is (= :dense
           (get-in aligned [:cadence-by-instrument "perp:BTC" :kind])))
    (is (= :sparse
           (get-in aligned [:cadence-by-instrument vault-instrument-id :kind])))
    (is (= 28
           (count (get-in aligned [:expected-return-series-by-instrument "perp:BTC"]))))
    (is (= 2
           (count (get-in aligned [:expected-return-series-by-instrument vault-instrument-id]))))
    (is (= 28
           (count (get-in aligned [:expected-return-intervals-by-instrument "perp:BTC"]))))
    (is (= 2
           (count (get-in aligned [:expected-return-intervals-by-instrument vault-instrument-id]))))
    (is (= {:kind :mixed-frequency
            :dense-block-calendar :daily
            :sparse-policy :pairwise-interval-aggregation
            :sparse-correlation-shrinkage true}
           (:risk-estimation aligned)))))

(deftest align-history-inputs-aligns-vault-and-market-history-by-utc-day-test
  (let [d0 (fixtures/day-start-ms "2026-04-27")
        d1 (+ d0 fixtures/day-ms)
        d2 (+ d1 fixtures/day-ms)
        vault-address "0x1111111111111111111111111111111111111111"
        vault-instrument-id (str "vault:" vault-address)
        aligned (history-loader/align-history-inputs
                 {:universe [{:instrument-id "perp:BTC"
                              :market-type :perp
                              :coin "BTC"}
                             {:instrument-id vault-instrument-id
                              :market-type :vault
                              :coin vault-instrument-id
                              :vault-address vault-address}]
                  :candle-history-by-coin {"BTC" [{:time d0 :close "100"}
                                                  {:time d1 :close "110"}
                                                  {:time d2 :close "121"}]}
                  :vault-details-by-address {vault-address
                                             {:portfolio
                                              {:all-time
                                               {:accountValueHistory [[(+ d0 (* 23 60 60 1000)) 100]
                                                                      [(+ d1 (* 23 60 60 1000)) 110]
                                                                      [(+ d2 (* 23 60 60 1000)) 121]]
                                                :pnlHistory [[(+ d0 (* 23 60 60 1000)) 0]
                                                             [(+ d1 (* 23 60 60 1000)) 10]
                                                             [(+ d2 (* 23 60 60 1000)) 21]]}}}}
                  :as-of-ms (+ d2 fixtures/day-ms)
                  :stale-after-ms (* 2 fixtures/day-ms)})]
    (is (= [d0 d1 d2] (:calendar aligned)))
    (is (= ["perp:BTC" vault-instrument-id]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (fixtures/near? 0.1 (get-in aligned [:return-series-by-instrument vault-instrument-id 0])))
    (is (fixtures/near? 0.1 (get-in aligned [:return-series-by-instrument vault-instrument-id 1])))
    (is (= [] (:warnings aligned)))))

(deftest align-history-inputs-prefers-derived-one-year-vault-history-over-direct-month-test
  (let [prior-all-time-start (fixtures/day-start-ms "2024-04-30")
        derived-start (fixtures/day-start-ms "2025-04-30")
        derived-mid (fixtures/day-start-ms "2025-10-30")
        direct-month-start (fixtures/day-start-ms "2026-02-28")
        direct-month-second (fixtures/day-start-ms "2026-03-14")
        direct-month-third (fixtures/day-start-ms "2026-03-28")
        direct-month-fourth (fixtures/day-start-ms "2026-04-14")
        end (fixtures/day-start-ms "2026-04-30")
        vault-address "0x1111111111111111111111111111111111111111"
        vault-instrument-id (str "vault:" vault-address)
        aligned (fixtures/align-market-and-vault-history
                 vault-address
                 {:all-time (fixtures/summary-from-points [[prior-all-time-start 80 -20]
                                                  [derived-start 100 0]
                                                  [derived-mid 110 10]
                                                  [end 121 21]])
                  :month (fixtures/summary-from-points [[direct-month-start 100 0]
                                               [direct-month-second 98 -2]
                                               [direct-month-third 96 -4]
                                               [direct-month-fourth 94 -6]
                                               [end 92 -8]])}
                 [[derived-start 200]
                  [derived-mid 220]
                  [direct-month-start 230]
                  [direct-month-second 228]
                  [direct-month-third 226]
                  [direct-month-fourth 224]
                  [end 242]])]
    (is (= [derived-start derived-mid end] (:calendar aligned)))
    (is (= ["perp:BTC" vault-instrument-id]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (fixtures/near? 0.1 (get-in aligned [:return-series-by-instrument vault-instrument-id 0])))
    (is (fixtures/near? 0.1 (get-in aligned [:return-series-by-instrument vault-instrument-id 1])))
    (is (= [{:start-ms derived-start
             :end-ms derived-mid
             :dt-days 183
             :dt-years (/ 183 365.2425)}
            {:start-ms derived-mid
             :end-ms end
             :dt-days 182
             :dt-years (/ 182 365.2425)}]
           (:return-intervals aligned)))
    (is (= [] (:warnings aligned)))))

(deftest align-history-inputs-prefers-direct-one-year-vault-summary-over-derived-all-time-test
  (let [prior-all-time-start (fixtures/day-start-ms "2024-04-30")
        direct-start (fixtures/day-start-ms "2025-04-30")
        direct-mid (fixtures/day-start-ms "2025-10-30")
        end (fixtures/day-start-ms "2026-04-30")
        vault-address "0x1111111111111111111111111111111111111111"
        vault-instrument-id (str "vault:" vault-address)
        aligned (fixtures/align-market-and-vault-history
                 vault-address
                 {:all-time (fixtures/summary-from-points [[prior-all-time-start 80 -20]
                                                  [direct-start 100 0]
                                                  [direct-mid 110 10]
                                                  [end 121 21]])
                  :one-year (fixtures/summary-from-points [[direct-start 100 0]
                                                  [direct-mid 120 20]
                                                  [end 144 44]])
                  :month (fixtures/summary-from-points [[(fixtures/day-start-ms "2026-02-28") 100 0]
                                               [(fixtures/day-start-ms "2026-03-31") 95 -5]
                                               [end 90 -10]])}
                 [[direct-start 200]
                  [direct-mid 220]
                  [(fixtures/day-start-ms "2026-02-28") 230]
                  [(fixtures/day-start-ms "2026-03-31") 225]
                  [end 242]])]
    (is (= [direct-start direct-mid end] (:calendar aligned)))
    (is (fixtures/near? 0.2 (get-in aligned [:return-series-by-instrument vault-instrument-id 0])))
    (is (fixtures/near? 0.2 (get-in aligned [:return-series-by-instrument vault-instrument-id 1])))
    (is (= [] (:warnings aligned)))))

(deftest align-history-inputs-falls-back-to-direct-month-vault-summary-when-one-year-cannot-be-derived-test
  (let [month-start (fixtures/day-start-ms "2026-02-28")
        month-mid (fixtures/day-start-ms "2026-03-31")
        end (fixtures/day-start-ms "2026-04-30")
        vault-address "0x1111111111111111111111111111111111111111"
        vault-instrument-id (str "vault:" vault-address)
        aligned (fixtures/align-market-and-vault-history
                 vault-address
                 {:all-time (fixtures/summary-from-points [[end 130 30]])
                  :month (fixtures/summary-from-points [[month-start 100 0]
                                               [month-mid 105 5]
                                               [end 110 10]])}
                 [[month-start 200]
                  [month-mid 210]
                  [end 220]])]
    (is (= [month-start month-mid end] (:calendar aligned)))
    (is (= ["perp:BTC" vault-instrument-id]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (fixtures/near? 0.05 (get-in aligned [:return-series-by-instrument vault-instrument-id 0])))
    (is (fixtures/near? (/ 5 105) (get-in aligned [:return-series-by-instrument vault-instrument-id 1])))
    (is (= [] (:warnings aligned)))))

(deftest align-history-inputs-falls-back-to-common-direct-vault-window-when-preferred-windows-do-not-overlap-test
  (let [hlp-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        growi-address "0x1e37a337ed460039d1b15bd3bc489de789768d5e"
        systemic-address "0xd6e56265890b76413d1d527eb9b75e334c0c5b42"
        hlp-id (fixtures/vault-instrument-id hlp-address)
        growi-id (fixtures/vault-instrument-id growi-address)
        systemic-id (fixtures/vault-instrument-id systemic-address)
        h0 (fixtures/day-start-ms "2025-05-03")
        h1 (fixtures/day-start-ms "2025-10-30")
        m0 (fixtures/day-start-ms "2026-04-02")
        m1 (fixtures/day-start-ms "2026-04-12")
        m2 (fixtures/day-start-ms "2026-04-23")
        m3 (fixtures/day-start-ms "2026-05-03")
        month-summary (fixtures/summary-from-points [[m0 100 0]
                                            [m1 105 5]
                                            [m2 110 10]
                                            [m3 115 15]])
        sparse-derived-summary (fixtures/summary-from-points [[h0 90 -10]
                                                     [h1 100 0]
                                                     [m3 115 15]])
        aligned (history-loader/align-history-inputs
                 {:universe [{:instrument-id hlp-id
                              :market-type :vault
                              :coin hlp-id
                              :vault-address hlp-address
                              :name "Hyperliquidity Provider (HLP)"}
                             {:instrument-id growi-id
                              :market-type :vault
                              :coin growi-id
                              :vault-address growi-address
                              :name "Growi HF"}
                             {:instrument-id systemic-id
                              :market-type :vault
                              :coin systemic-id
                              :vault-address systemic-address
                              :name "[ Systemic Strategies ] HyperGrowth"}]
                  :vault-details-by-address
                  {hlp-address {:portfolio {:all-time sparse-derived-summary
                                             :month month-summary}}
                   growi-address {:portfolio {:all-time sparse-derived-summary
                                               :month month-summary}}
                   systemic-address {:portfolio {:all-time (fixtures/summary-from-points [[m3 115 15]])
                                                  :month month-summary}}}
                  :as-of-ms (+ m3 fixtures/day-ms)
                  :stale-after-ms (* 2 fixtures/day-ms)})]
    (is (= [m0 m1 m2 m3] (:calendar aligned)))
    (is (= [m1 m2 m3] (:return-calendar aligned)))
    (is (= [hlp-id growi-id systemic-id]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (= {:kind :common-vault-window
            :window :month
            :observations 4}
           (:alignment-source aligned)))
    (is (not-any? #(= :insufficient-common-history (:code %))
                  (:warnings aligned)))))

(deftest align-history-inputs-keeps-preferred-vault-return-series-for-expected-returns-test
  (let [hlp-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        growi-address "0x1e37a337ed460039d1b15bd3bc489de789768d5e"
        systemic-address "0xd6e56265890b76413d1d527eb9b75e334c0c5b42"
        hlp-id (fixtures/vault-instrument-id hlp-address)
        growi-id (fixtures/vault-instrument-id growi-address)
        systemic-id (fixtures/vault-instrument-id systemic-address)
        h0 (fixtures/day-start-ms "2025-05-03")
        h1 (fixtures/day-start-ms "2025-10-30")
        m0 (fixtures/day-start-ms "2026-04-02")
        m1 (fixtures/day-start-ms "2026-04-12")
        m2 (fixtures/day-start-ms "2026-04-23")
        m3 (fixtures/day-start-ms "2026-05-03")
        hlp-month (fixtures/summary-from-points [[m0 100 0]
                                        [m1 99 -1]
                                        [m2 98 -2]
                                        [m3 97 -3]])
        growi-month (fixtures/summary-from-points [[m0 100 0]
                                          [m1 102 2]
                                          [m2 104 4]
                                          [m3 106 6]])
        systemic-month (fixtures/summary-from-points [[m0 100 0]
                                             [m1 103 3]
                                             [m2 106 6]
                                             [m3 109 9]])
        positive-derived (fixtures/summary-from-points [[h0 100 0]
                                               [h1 110 10]
                                               [m3 121 21]])
        aligned (history-loader/align-history-inputs
                 {:universe [{:instrument-id hlp-id
                              :market-type :vault
                              :coin hlp-id
                              :vault-address hlp-address}
                             {:instrument-id growi-id
                              :market-type :vault
                              :coin growi-id
                              :vault-address growi-address}
                             {:instrument-id systemic-id
                              :market-type :vault
                              :coin systemic-id
                              :vault-address systemic-address}]
                  :vault-details-by-address
                  {hlp-address {:portfolio {:all-time positive-derived
                                             :month hlp-month}}
                   growi-address {:portfolio {:all-time positive-derived
                                               :month growi-month}}
                   systemic-address {:portfolio {:all-time (fixtures/summary-from-points [[m3 109 9]])
                                                  :month systemic-month}}}
                  :as-of-ms (+ m3 fixtures/day-ms)
                  :stale-after-ms (* 2 fixtures/day-ms)})]
    (is (= [m0 m1 m2 m3] (:calendar aligned)))
    (is (= {:kind :common-vault-window
            :window :month
            :observations 4}
           (:alignment-source aligned)))
    (is (neg? (get-in aligned [:return-series-by-instrument hlp-id 0])))
    (is (= 2
           (count (get-in aligned [:expected-return-series-by-instrument hlp-id]))))
    (is (every? #(fixtures/near? 0.1 %)
                (get-in aligned [:expected-return-series-by-instrument hlp-id])))
    (is (= [{:start-ms h0
             :end-ms h1
             :dt-days 180
             :dt-years (/ 180 365.2425)}
            {:start-ms h1
             :end-ms m3
             :dt-days 185
             :dt-years (/ 185 365.2425)}]
           (get-in aligned [:expected-return-intervals-by-instrument hlp-id])))
    (is (= [h0 h1 m3]
           (mapv :time-ms
                 (get-in aligned [:raw-price-series-by-instrument hlp-id]))))
    (is (every? true?
                (map fixtures/near?
                     [100 110 121]
                     (mapv :close
                           (get-in aligned [:raw-price-series-by-instrument hlp-id])))))))

(deftest align-history-inputs-keeps-common-history-warning-when-no-vault-window-overlaps-test
  (let [vault-a "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        vault-b "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        vault-c "0xcccccccccccccccccccccccccccccccccccccccc"
        vault-a-id (fixtures/vault-instrument-id vault-a)
        vault-b-id (fixtures/vault-instrument-id vault-b)
        vault-c-id (fixtures/vault-instrument-id vault-c)
        a0 (fixtures/day-start-ms "2026-04-01")
        a1 (fixtures/day-start-ms "2026-04-02")
        b0 (fixtures/day-start-ms "2026-04-10")
        b1 (fixtures/day-start-ms "2026-04-11")
        c0 (fixtures/day-start-ms "2026-04-20")
        c1 (fixtures/day-start-ms "2026-04-21")
        aligned (history-loader/align-history-inputs
                 {:universe [{:instrument-id vault-a-id
                              :market-type :vault
                              :coin vault-a-id
                              :vault-address vault-a}
                             {:instrument-id vault-b-id
                              :market-type :vault
                              :coin vault-b-id
                              :vault-address vault-b}
                             {:instrument-id vault-c-id
                              :market-type :vault
                              :coin vault-c-id
                              :vault-address vault-c}]
                  :vault-details-by-address
                  {vault-a {:portfolio {:month (fixtures/summary-from-points [[a0 100 0]
                                                                      [a1 101 1]])}}
                   vault-b {:portfolio {:month (fixtures/summary-from-points [[b0 100 0]
                                                                      [b1 101 1]])}}
                   vault-c {:portfolio {:month (fixtures/summary-from-points [[c0 100 0]
                                                                      [c1 101 1]])}}}
                  :as-of-ms (+ c1 fixtures/day-ms)
                  :stale-after-ms (* 2 fixtures/day-ms)})]
    (is (= [] (:calendar aligned)))
    (is (= [] (:eligible-instruments aligned)))
    (is (= [vault-a-id vault-b-id vault-c-id]
           (mapv :instrument-id (:excluded-instruments aligned))))
    (is (= {:code :insufficient-common-history
            :observations 0
            :required 2}
           (last (:warnings aligned))))))

(deftest align-history-inputs-tries-derived-one-year-when-direct-one-year-window-does-not-overlap-test
  (let [vault-a "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        vault-b "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        vault-a-id (fixtures/vault-instrument-id vault-a)
        vault-b-id (fixtures/vault-instrument-id vault-b)
        prior (fixtures/day-start-ms "2025-05-02")
        cutoff (fixtures/day-start-ms "2025-05-03")
        a-direct (fixtures/day-start-ms "2025-06-01")
        b-direct (fixtures/day-start-ms "2025-07-01")
        mid (fixtures/day-start-ms "2025-11-03")
        end (fixtures/day-start-ms "2026-05-03")
        derived-source (fixtures/summary-from-points [[prior 90 -10]
                                             [mid 100 0]
                                             [end 110 10]])
        aligned (history-loader/align-history-inputs
                 {:universe [{:instrument-id vault-a-id
                              :market-type :vault
                              :coin vault-a-id
                              :vault-address vault-a}
                             {:instrument-id vault-b-id
                              :market-type :vault
                              :coin vault-b-id
                              :vault-address vault-b}]
                  :vault-details-by-address
                  {vault-a {:portfolio {:one-year (fixtures/summary-from-points [[a-direct 100 0]
                                                                         [end 110 10]])
                                         :all-time derived-source}}
                   vault-b {:portfolio {:one-year (fixtures/summary-from-points [[b-direct 100 0]
                                                                         [end 110 10]])
                                         :all-time derived-source}}}
                  :as-of-ms (+ end fixtures/day-ms)
                  :stale-after-ms (* 2 fixtures/day-ms)})]
    (is (= [cutoff mid end] (:calendar aligned)))
    (is (= [vault-a-id vault-b-id]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (= {:kind :common-vault-window
            :window :one-year
            :observations 3}
           (:alignment-source aligned)))
    (is (not-any? #(= :insufficient-common-history (:code %))
                  (:warnings aligned)))))

(deftest align-history-inputs-reports-missing-and-insufficient-history-without-silent-drops-test
  (let [aligned (history-loader/align-history-inputs
                 {:universe [{:instrument-id "perp:BTC"
                              :market-type :perp
                              :coin "BTC"}
                             {:instrument-id "spot:MISSING"
                              :market-type :spot
                              :coin "MISSING"}]
                  :candle-history-by-coin {"BTC" [{:time 1000 :close "100"}]}
                  :min-observations 2
                  :as-of-ms 20000
                  :stale-after-ms 1000})]
    (is (= [] (:eligible-instruments aligned)))
    (is (= ["perp:BTC" "spot:MISSING"]
           (mapv :instrument-id (:excluded-instruments aligned))))
    (is (= [{:code :insufficient-candle-history
             :instrument-id "perp:BTC"
             :coin "BTC"
             :observations 1
             :required 2}
            {:code :missing-candle-history
             :instrument-id "spot:MISSING"
             :coin "MISSING"}]
           (:warnings aligned)))
    (is (= {:as-of-ms 20000
            :latest-common-ms nil
            :oldest-common-ms nil
            :age-ms nil
            :stale? true}
           (:freshness aligned)))))
