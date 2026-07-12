(ns hyperopen.portfolio.optimizer.application.rebalance-preview-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.rebalance-preview
             :as rebalance-preview]))

(defn- sample-request
  []
  {:current-portfolio
   {:capital {:nav-usdc 1000}
    :by-instrument {"perp:BTC" {:instrument-id "perp:BTC"
                               :weight 0.5
                               :mark-price 100
                               :close 100}}}
   :universe [{:instrument-id "perp:BTC"
               :instrument-type :perp
               :coin "BTC"}]
   ;; :default-fee-bps is what request_builder derives from :fee-mode; the frontend
   ;; preview reads it straight from execution-assumptions.
   :execution-assumptions {:fee-mode :taker
                           :default-fee-bps 4.5
                           :fallback-slippage-bps 25}})

(defn- sample-result
  "A solved result whose :current-portfolio-weights-by-instrument carries a KEYWORD
   instrument-id key — the exact shape that survives the worker boundary when a map
   is missing from the instrument-keyed codec allow-list. The preview must not spawn
   a phantom \":perp:BTC\" leg from it."
  []
  {:status :solved
   :instrument-ids ["perp:BTC"]
   :target-weights [0.8]
   :current-weights [0.5]
   :current-portfolio-instrument-ids ["perp:BTC"]
   :current-portfolio-weights [0.5]
   :current-portfolio-weights-by-instrument {(keyword "perp:BTC") 0.5}
   :labels-by-instrument {"perp:BTC" "BTC"}})

(deftest preview-does-not-spawn-phantom-leg-from-keyword-keyed-current-portfolio-weights-test
  (let [result (rebalance-preview/result-with-rebalance-preview (sample-request)
                                                                (sample-result))
        preview (:rebalance-preview result)
        rows (:rows preview)
        ids (mapv :instrument-id rows)]
    ;; Exactly one row for the single held/targeted asset — no duplicate leg.
    (is (= 1 (count rows)))
    (is (= ["perp:BTC"] ids))
    ;; No leading-colon phantom id leaked through.
    (is (not-any? #(str/starts-with? (str %) ":") ids))
    ;; The single row resolves to a real instrument (not market-metadata-missing).
    (is (= :ready (:status (first rows))))
    (is (not= :market-metadata-missing (:reason (first rows))))
    ;; Buys reflect the genuine 0.5 -> 0.8 move on $1000 NAV = $300, once.
    (is (= 1 (get-in preview [:summary :ready-count])))
    (is (= 0 (get-in preview [:summary :blocked-count])))
    ;; The refresh-path preview applies the schedule-derived fee: $300 * 4.5bps.
    (is (= 4.5 (get-in (first rows) [:cost :fee-bps])))
    (is (pos? (get-in (first rows) [:cost :estimated-fee-usd])))
    (is (pos? (get-in preview [:summary :estimated-fees-usd])))))

(deftest preview-holds-portfolio-assets-the-allocator-excluded-test
  ;; A held spot asset absent from the result's targets (allocator excluded it from
  ;; the optimization) must surface as a held :excluded row, never a sell-to-zero.
  (let [request (-> (sample-request)
                    (assoc-in [:current-portfolio :by-instrument "spot:@107"]
                              {:instrument-id "spot:@107"
                               :market-type :spot
                               :coin "HYPE"
                               :weight 0.01
                               :mark-price 40
                               :close 40}))
        result (rebalance-preview/result-with-rebalance-preview request
                                                                (sample-result))
        rows (get-in result [:rebalance-preview :rows])
        spot-row (first (filter #(= "spot:@107" (:instrument-id %)) rows))]
    (is (some? spot-row))
    (is (= :excluded (:status spot-row)))
    (is (= :excluded-from-optimization (:reason spot-row)))
    (is (= :none (:side spot-row)))
    (is (zero? (:delta-notional-usd spot-row)))
    ;; The optimized asset still trades normally.
    (is (= 1 (get-in result [:rebalance-preview :summary :ready-count])))))

(deftest preview-still-exits-blocklisted-holdings-test
  ;; Blocklisting is an explicit user "exit this position" — those holdings keep the
  ;; sell-to-zero behavior even though the result carries no target for them.
  (let [request (-> (sample-request)
                    (assoc-in [:current-portfolio :by-instrument "perp:ETH"]
                              {:instrument-id "perp:ETH"
                               :market-type :perp
                               :weight 0.2
                               :mark-price 100
                               :close 100})
                    (update :universe conj {:instrument-id "perp:ETH"
                                            :instrument-type :perp
                                            :coin "ETH"})
                    (assoc-in [:constraints :blocklist] ["perp:ETH"]))
        result (rebalance-preview/result-with-rebalance-preview request
                                                                (sample-result))
        rows (get-in result [:rebalance-preview :rows])
        eth-row (first (filter #(= "perp:ETH" (:instrument-id %)) rows))]
    (is (some? eth-row))
    (is (= :ready (:status eth-row)))
    (is (= :sell (:side eth-row)))
    (is (= 0 (:target-weight eth-row)))))
