(ns hyperopen.portfolio.optimizer.domain.rebalance-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.trading.core :as trading-core]
            [hyperopen.portfolio.optimizer.domain.rebalance :as rebalance]))

(defn- near?
  [expected actual]
  (< (js/Math.abs (- expected actual)) 0.0000001))

(defn- near-vec?
  [expected actual]
  (and (= (count expected) (count actual))
       (every? true? (map near? expected actual))))

(deftest build-rebalance-preview-generates-ready-perp-and-blocked-spot-rows-test
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  :rebalance-tolerance 0.005
                  :fallback-slippage-bps 15
                  :instrument-ids ["perp:BTC" "spot:PURR"]
                  :current-weights [0.2 0.1]
                  :target-weights [0.35 0.02]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"}
                                      "spot:PURR" {:instrument-type :spot
                                                   :coin "PURR"}}
                  :prices-by-id {"perp:BTC" 30000
                                 "spot:PURR" 2}
                  :cost-contexts-by-id {"perp:BTC" {:source :live-book
                                                    :slippage-bps 4}}
                  :fee-bps-by-id {"perp:BTC" 4.5
                                  "spot:PURR" 7}})]
    (is (= :partially-blocked (:status preview)))
    (is (= 2 (count (:rows preview))))
    (let [perp-row (first (:rows preview))
          spot-row (second (:rows preview))]
      (is (= :ready (:status perp-row)))
      (is (= :buy (:side perp-row)))
      (is (near? 1500 (:delta-notional-usd perp-row)))
      (is (near? 0.05 (:quantity perp-row)))
      (is (= :live-book (get-in perp-row [:cost :source])))
      (is (= 4 (get-in perp-row [:cost :slippage-bps])))
      (is (near? 0.6 (get-in perp-row [:cost :estimated-slippage-usd])))
      (is (= 4.5 (get-in perp-row [:cost :fee-bps])))
      (is (near? 0.675 (get-in perp-row [:cost :estimated-fee-usd])))
      (is (= :blocked (:status spot-row)))
      (is (= :spot-submit-unsupported (:reason spot-row)))
      (is (near? -800 (:delta-notional-usd spot-row))))))

(deftest build-rebalance-preview-skips-tolerance-and-blocks-missing-prices-test
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 1000
                  :rebalance-tolerance 0.01
                  :instrument-ids ["perp:ETH" "perp:SOL"]
                  :current-weights [0.2 0.2]
                  :target-weights [0.205 0.1]
                  :instruments-by-id {"perp:ETH" {:instrument-type :perp}
                                      "perp:SOL" {:instrument-type :perp}}
                  :prices-by-id {"perp:ETH" 2000}})]
    (is (= :blocked (:status preview)))
    (is (= :within-tolerance (get-in preview [:rows 0 :status])))
    (is (= :blocked (get-in preview [:rows 1 :status])))
    (is (= :missing-price (get-in preview [:rows 1 :reason])))
    (is (near? 0 (get-in preview [:summary :gross-trade-notional-usd])))))

(deftest build-rebalance-preview-blocks-zero-capital-without-ready-rows-test
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 0
                  :rebalance-tolerance 0.005
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [0.5]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 30000}})]
    (is (= :blocked (:status preview)))
    (is (= :blocked (get-in preview [:rows 0 :status])))
    (is (= :missing-capital-base (get-in preview [:rows 0 :reason])))
    (is (= :buy (get-in preview [:rows 0 :side])))
    (is (= 0 (get-in preview [:summary :ready-count])))
    (is (= 0 (get-in preview [:summary :gross-trade-notional-usd])))))

(deftest build-rebalance-preview-blocks-quantity-rounded-below-lot-test
  ;; Notional is $12 (clears the $10 minimum) but the quantity rounds below the
  ;; lot at szDecimals 4, so this exercises the lot gate specifically.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 12000
                  :rebalance-tolerance 0.0
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [0.001]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"
                                                  :szDecimals 4}}
                  :prices-by-id {"perp:BTC" 200000}})]
    (is (= :blocked (:status preview)))
    (is (= :quantity-below-lot (get-in preview [:rows 0 :reason])))
    (is (= 0 (get-in preview [:rows 0 :quantity])))
    (is (= 0 (get-in preview [:summary :ready-count])))))

(deftest build-rebalance-preview-keeps-summary-to-executable-rows-test
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  :rebalance-tolerance 0.0
                  :instrument-ids ["perp:BTC" "spot:PURR"]
                  :current-weights [0.0 0.0]
                  :target-weights [0.1 0.1]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"}
                                      "spot:PURR" {:instrument-type :spot
                                                   :coin "PURR"}}
                  :prices-by-id {"perp:BTC" 10000
                                 "spot:PURR" 1}})]
    (is (= :partially-blocked (:status preview)))
    (is (= 1 (get-in preview [:summary :ready-count])))
    (is (= 1 (get-in preview [:summary :blocked-count])))
    (is (= 1000 (get-in preview [:summary :gross-trade-notional-usd])))))

(deftest build-rebalance-preview-derives-live-orderbook-costs-and-margin-impact-test
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  :current-margin-used-usdc 1000
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [0.2]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :cost-contexts-by-id {"perp:BTC" {:source :live-orderbook
                                                    :best-bid {:px-num 99}
                                                    :best-ask {:px-num 101}}}
                  :fee-bps-by-id {"perp:BTC" 5}
                  :leverage-by-id {"perp:BTC" 5}})]
    (is (= :ready (:status preview)))
    (is (= :live-orderbook (get-in preview [:rows 0 :cost :source])))
    (is (= 101 (get-in preview [:rows 0 :cost :estimated-fill-price])))
    (is (near? 100 (get-in preview [:rows 0 :cost :slippage-bps])))
    (is (near? 20 (get-in preview [:rows 0 :cost :estimated-slippage-usd])))
    (is (near? 1 (get-in preview [:rows 0 :cost :estimated-fee-usd])))
    (is (= {:capital-usd 10000
            :current-used-usd 1000
            :estimated-impact-usd 400
            :after-used-usd 1400
            :before-utilization 0.1
            :after-utilization 0.14
            :warning nil}
           (get-in preview [:summary :margin])))))

(deftest build-rebalance-preview-uses-signed-deltas-across-zero-test
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  :rebalance-tolerance 0.0
                  :instrument-ids ["increase-long"
                                   "reduce-long"
                                   "long-to-short"
                                   "cover-short"
                                   "short-to-long"
                                   "increase-short"]
                  :current-weights [0.20 0.50 0.20 -0.30 -0.10 -0.10]
                  :target-weights [0.50 0.20 -0.10 -0.10 0.20 -0.40]
                  :instruments-by-id {"increase-long" {:instrument-type :perp}
                                      "reduce-long" {:instrument-type :perp}
                                      "long-to-short" {:instrument-type :perp}
                                      "cover-short" {:instrument-type :perp}
                                      "short-to-long" {:instrument-type :perp}
                                      "increase-short" {:instrument-type :perp}}
                  :prices-by-id {"increase-long" 100
                                 "reduce-long" 100
                                 "long-to-short" 100
                                 "cover-short" 100
                                 "short-to-long" 100
                                 "increase-short" 100}})
        rows (:rows preview)]
    (is (= [:buy :sell :sell :buy :buy :sell]
           (mapv :side rows)))
    (is (near-vec? [3000 -3000 -3000 2000 3000 -3000]
                   (mapv :delta-notional-usd rows)))
    (is (= 17000 (get-in preview [:summary :gross-trade-notional-usd])))))

(deftest build-rebalance-preview-uses-snapshot-visible-depth-for-buy-slippage-test
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 300
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [1.0]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :cost-contexts-by-id {"perp:BTC" {:source :snapshot
                                                    :asks [{:px "101" :sz "1"}
                                                           {:px "102" :sz "2"}]
                                                    :stale? false
                                                    :age-ms 7000}}})
        cost (get-in preview [:rows 0 :cost])]
    (is (= :ready (get-in preview [:rows 0 :status])))
    (is (= :snapshot (:source cost)))
    (is (near? 101.66666666666667 (:estimated-fill-price cost)))
    (is (near? 166.66666666666669 (:slippage-bps cost)))
    (is (near? 5 (:estimated-slippage-usd cost)))
    (is (= :full-visible-depth (:depth-status cost)))
    (is (= false (:stale? cost)))
    (is (= 7000 (:age-ms cost)))))

(deftest build-rebalance-preview-uses-snapshot-visible-depth-for-sell-slippage-test
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 300
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:BTC"]
                  :current-weights [1.0]
                  :target-weights [0.0]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :cost-contexts-by-id {"perp:BTC" {:source :snapshot
                                                    :bids [{:px "99" :sz "1"}
                                                           {:px "98" :sz "2"}]
                                                    :stale? false
                                                    :age-ms 9000}}})
        cost (get-in preview [:rows 0 :cost])]
    (is (= :ready (get-in preview [:rows 0 :status])))
    (is (= :sell (get-in preview [:rows 0 :side])))
    (is (= :snapshot (:source cost)))
    (is (near? 98.33333333333333 (:estimated-fill-price cost)))
    (is (near? 166.66666666666674 (:slippage-bps cost)))
    (is (near? 5 (:estimated-slippage-usd cost)))
    (is (= :full-visible-depth (:depth-status cost)))))

(deftest build-rebalance-preview-extrapolates-when-snapshot-depth-is-insufficient-test
  ;; Order (qty 3) exceeds the single visible ask level (sz 1). Slippage is the
  ;; real VWAP on the visible portion blended with a shortfall-scaled penalty on
  ;; the unfilled remainder, never below the flat fallback. visible-vwap=101 ->
  ;; visible-slip=100 bps; depth-ratio=1/3; shortfall=3 -> remainder=25*3=75;
  ;; blended=(1/3*100)+(2/3*75)=83.333 bps; slip-usd=300*83.333/10000=2.5.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 300
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [1.0]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :cost-contexts-by-id {"perp:BTC" {:source :snapshot
                                                    :asks [{:px "101" :sz "1"}]
                                                    :stale? false
                                                    :age-ms 5000}}})
        cost (get-in preview [:rows 0 :cost])]
    (is (= :ready (get-in preview [:rows 0 :status])))
    (is (= :depth-extrapolated (:source cost)))
    (is (near? 83.33333333333333 (:slippage-bps cost)))
    (is (near? 2.5 (:estimated-slippage-usd cost)))
    (is (= :insufficient-visible-depth (:depth-status cost)))
    (is (= :snapshot-depth-limited (:fallback-reason cost)))
    (is (= 5000 (:age-ms cost)))
    ;; never under-charges relative to the old flat fallback
    (is (>= (:slippage-bps cost) 25))))

(deftest depth-limited-slippage-grows-with-order-size-test
  ;; Monotonicity: a larger order that overruns the same visible book by more
  ;; must be charged strictly more slippage than a smaller over-book order.
  (let [slip (fn [capital]
               (-> (rebalance/build-rebalance-preview
                    {:capital-usd capital
                     :rebalance-tolerance 0.0
                     :fallback-slippage-bps 25
                     :instrument-ids ["perp:BTC"]
                     :current-weights [0.0]
                     :target-weights [1.0]
                     :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                     :coin "BTC"}}
                     :prices-by-id {"perp:BTC" 100}
                     :cost-contexts-by-id {"perp:BTC"
                                           {:source :snapshot
                                            :asks [{:px "101" :sz "1"}]
                                            :stale? false
                                            :age-ms 0}}})
                   (get-in [:rows 0 :cost :slippage-bps])))
        small (slip 200)
        medium (slip 300)
        large (slip 1000)]
    (is (< small medium))
    (is (< medium large))))

(deftest taker-rebalance-includes-nonzero-fees-and-per-id-overrides-test
  ;; A taker rebalance with no per-instrument fee but a schedule-derived
  ;; :default-fee-bps must charge a non-zero fee (the bug was a permanent $0 fee).
  ;; The default bps is the canonical taker rate (domain.trading.core/default-fees,
  ;; percent -> bps); a per-id :fee-bps-by-id entry still wins.
  (let [taker-bps (* 100 (:taker trading-core/default-fees))
        base {:capital-usd 300
              :rebalance-tolerance 0.0
              :instrument-ids ["perp:BTC"]
              :current-weights [0.0]
              :target-weights [1.0]
              :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                              :coin "BTC"}}
              :prices-by-id {"perp:BTC" 100}
              :cost-contexts-by-id {"perp:BTC" {:source :live-book
                                                :slippage-bps 4}}}
        default-cost (-> (rebalance/build-rebalance-preview
                          (assoc base :default-fee-bps taker-bps))
                         (get-in [:rows 0 :cost]))
        override-cost (-> (rebalance/build-rebalance-preview
                           (assoc base
                                  :default-fee-bps taker-bps
                                  :fee-bps-by-id {"perp:BTC" 2}))
                          (get-in [:rows 0 :cost]))]
    (is (near? taker-bps (:fee-bps default-cost)))
    ;; notional 300 * 4.5 bps / 10000 = 0.135, and crucially > 0.
    (is (pos? (:estimated-fee-usd default-cost)))
    (is (near? (* 300 (/ taker-bps 10000)) (:estimated-fee-usd default-cost)))
    (is (= 2 (:fee-bps override-cost)))
    (is (near? 0.06 (:estimated-fee-usd override-cost)))))

(deftest sub-ten-dollar-leg-is-blocked-below-min-notional-test
  ;; Hyperliquid rejects orders below a $10 notional. A small reweight on a small
  ;; portfolio must not be offered as a ready row that fails at submit.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 1000
                  :rebalance-tolerance 0.001
                  :instrument-ids ["perp:SMALL" "perp:BIG"]
                  ;; deltas: 0.005 -> $5 (below $10), 0.05 -> $50 (above $10)
                  :current-weights [0.10 0.0]
                  :target-weights [0.105 0.05]
                  :instruments-by-id {"perp:SMALL" {:instrument-type :perp
                                                    :coin "SMALL"}
                                      "perp:BIG" {:instrument-type :perp
                                                  :coin "BIG"}}
                  :prices-by-id {"perp:SMALL" 50000
                                 "perp:BIG" 50000}})
        rows-by-id (into {} (map (juxt :instrument-id identity) (:rows preview)))
        small (get rows-by-id "perp:SMALL")
        big (get rows-by-id "perp:BIG")]
    (is (= :blocked (:status small)))
    (is (= :below-min-notional (:reason small)))
    (is (= :ready (:status big)))))
