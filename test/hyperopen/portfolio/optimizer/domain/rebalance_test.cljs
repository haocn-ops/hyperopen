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

(deftest build-rebalance-preview-generates-ready-perp-and-spot-rows-test
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
    ;; Spot legs are now executable (routed on Hyperliquid like perps), so both rows
    ;; are ready and the preview status is no longer partially-blocked.
    (is (= :ready (:status preview)))
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
      ;; spot:PURR: target 0.02 vs current 0.10 -> sell $800, qty 400 @ $2, fee 7 bp
      (is (= :ready (:status spot-row)))
      (is (= :sell (:side spot-row)))
      (is (near? -800 (:delta-notional-usd spot-row)))
      (is (near? 400 (:quantity spot-row)))
      (is (= 7 (get-in spot-row [:cost :fee-bps])))
      (is (near? 0.56 (get-in spot-row [:cost :estimated-fee-usd]))))))

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
  ;; perp:BTC and spot:PURR are both executable (spot routes on Hyperliquid too);
  ;; perp:ETH is blocked on a missing price and must be excluded from the summary.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  :rebalance-tolerance 0.0
                  :instrument-ids ["perp:BTC" "spot:PURR" "perp:ETH"]
                  :current-weights [0.0 0.0 0.0]
                  :target-weights [0.1 0.1 0.1]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"}
                                      "spot:PURR" {:instrument-type :spot
                                                   :coin "PURR"}
                                      "perp:ETH" {:instrument-type :perp
                                                  :coin "ETH"}}
                  :prices-by-id {"perp:BTC" 10000
                                 "spot:PURR" 1}})]
    (is (= :partially-blocked (:status preview)))
    (is (= 2 (get-in preview [:summary :ready-count])))
    (is (= 1 (get-in preview [:summary :blocked-count])))
    (is (= :missing-price (get-in preview [:rows 2 :reason])))
    ;; gross-trade-notional counts only the two ready rows ($1000 + $1000).
    (is (= 2000 (get-in preview [:summary :gross-trade-notional-usd])))))

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
    ;; Explicit per-instrument leverage (5x) still drives the margin impact (2000/5 = 400);
    ;; the new keys add the account-leverage projection + free-margin headroom. No
    ;; :current-gross-exposure-usdc here, so before-gross falls back to the row's own
    ;; current gross (0 — the position is opened from flat), after-gross is the Δ (0.2x).
    (is (= {:capital-usd 10000
            :current-used-usd 1000
            :estimated-impact-usd 400
            :after-used-usd 1400
            :free-margin-usd 8600
            :before-utilization 0.1
            :after-utilization 0.14
            :before-gross-leverage 0
            :after-gross-leverage 0.2
            :before-venue-leverage nil ;; no venue inputs -> bridge stays nil
            :after-venue-leverage nil
            :warning nil}
           (get-in preview [:summary :margin])))))

(deftest build-rebalance-preview-projects-account-leverage-and-effective-margin-impact-test
  ;; The commit-moment figure is account leverage (gross notional / equity), projected
  ;; with the ready trade applied; and with no explicit per-instrument leverage cap, the
  ;; margin impact must charge at the account's REAL effective leverage (gross/margin-used),
  ;; not the misleading 1x (full-collateral) default.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  ;; Account-wide current book: 2.0x gross leverage, running at 5x effective
                  ;; (20000 gross / 4000 margin used).
                  :current-gross-exposure-usdc 20000
                  :current-margin-used-usdc 4000
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [0.3]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :fee-bps-by-id {"perp:BTC" 5}}) ;; NB: no :leverage-by-id -> effective fallback
        margin (get-in preview [:summary :margin])]
    (is (= :ready (:status preview)))
    ;; Impact = 3000 notional / 5x effective = 600 (the old 1x default would charge 3000).
    (is (= 600 (:estimated-impact-usd margin)))
    (is (= 4600 (:after-used-usd margin)))
    (is (= 5400 (:free-margin-usd margin)))
    ;; Leverage projection: 2.0x before, +0.3x from the opened position -> 2.3x after.
    (is (near? 2.0 (:before-gross-leverage margin)))
    (is (near? 2.3 (:after-gross-leverage margin)))))

(deftest build-rebalance-preview-projects-venue-leverage-test
  ;; Venue-lens bridge (trade page's Unified Account Leverage, perp notional ÷
  ;; collateral). Before: 6000/5000 = 1.2x. After: only the perp row's +0.3 weight
  ;; (3000 notional) counts, not the spot row's +0.1 -> 9000/5000 = 1.8x.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  :current-gross-exposure-usdc 20000
                  :current-margin-used-usdc 4000
                  :current-perp-gross-usdc 6000
                  :venue-collateral-usd 5000
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:BTC" "spot:HYPE"]
                  :current-weights [0.0 0.1] :target-weights [0.3 0.2]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp :coin "BTC"}
                                      "spot:HYPE" {:instrument-type :spot :coin "HYPE"}}
                  :prices-by-id {"perp:BTC" 100 "spot:HYPE" 10}})
        margin (get-in preview [:summary :margin])]
    (is (= :ready (:status preview)))
    (is (near? 1.2 (:before-venue-leverage margin)))
    (is (near? 1.8 (:after-venue-leverage margin)))))

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
  ;; Order (qty 3) exceeds the single visible ask level (sz 1). Slippage is the real
  ;; VWAP on the visible portion blended with a remainder charged from the book-EDGE
  ;; marginal price scaled by the overrun, never below the flat fallback and never
  ;; above the extrapolation cap. visible-vwap=101 -> visible-slip=100 bps;
  ;; edge-slip=100 bps (worst level 101); overrun=3 -> remainder=min(100*3,1000)=300;
  ;; blended=(1/3*100)+(2/3*300)=233.333 bps; slip-usd=300*233.333/10000=7.0.
  ;; The estimate is an explicit FLOOR (part of the order is beyond the visible book).
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
    (is (near? 233.33333333333334 (:slippage-bps cost)))
    (is (near? 7.0 (:estimated-slippage-usd cost)))
    (is (= :insufficient-visible-depth (:depth-status cost)))
    (is (= :snapshot-depth-limited (:fallback-reason cost)))
    (is (= 5000 (:age-ms cost)))
    ;; floor semantics + coverage disclosure for the surfaces
    (is (true? (:estimate-floor? cost)))
    (is (near? 3 (:depth-overrun cost)))
    (is (near? (/ 1 3) (:depth-coverage cost)))
    (is (near? 101 (:visible-notional-usd cost)))
    ;; never under-charges relative to the old flat fallback
    (is (>= (:slippage-bps cost) 25))))

(deftest depth-extrapolation-is-capped-not-unbounded-test
  ;; The motivating bug: a $16.8M sell against ~$41k of visible bids used to read
  ;; 10,225 bp (a fill below zero -- impossible). An order 1000x the visible book must
  ;; now blend toward the extrapolation cap (1,000 bp), not fallback*overrun=25,000 bp.
  ;; Here: visible-slip=100, edge=100, overrun=1000 -> remainder=min(100*1000,1000)=1000;
  ;; depth-ratio=0.001 -> blended=0.1+0.999*1000=999.1 bp.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 100000
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:THIN"]
                  :current-weights [1.0]
                  :target-weights [0.0]
                  :instruments-by-id {"perp:THIN" {:instrument-type :perp
                                                   :coin "THIN"}}
                  :prices-by-id {"perp:THIN" 100}
                  :cost-contexts-by-id {"perp:THIN" {:source :snapshot
                                                     :bids [{:px "99" :sz "1"}]
                                                     :stale? false
                                                     :age-ms 0}}})
        cost (get-in preview [:rows 0 :cost])]
    (is (= :sell (get-in preview [:rows 0 :side])))
    (is (near? 999.1 (:slippage-bps cost)))
    (is (<= (:slippage-bps cost) (+ rebalance/max-extrapolated-slippage-bps 1)))
    ;; a sell's average slippage can never reach 10,000 bp (a fill at/below zero)
    (is (< (:slippage-bps cost) 10000))
    (is (true? (:estimate-floor? cost)))
    (is (near? 1000 (:depth-overrun cost)))))

(deftest twap-suborder-count-mirrors-venue-cadence-test
  ;; One suborder each 30s plus the initial clip, runtime floored at the venue's
  ;; 5-minute minimum — must agree with hyperopen.domain.trading.core.
  (is (= 21 (rebalance/twap-suborder-count 10)))
  (is (= 41 (rebalance/twap-suborder-count 20)))
  (is (= 11 (rebalance/twap-suborder-count 5)))
  (is (= 11 (rebalance/twap-suborder-count 3)))
  (is (= 11 (rebalance/twap-suborder-count nil)))
  (is (= (rebalance/twap-suborder-count 20)
         (trading-core/twap-suborder-count 20))))

(def ^:private one-shot-cost
  ;; A splittable one-shot cost map as cost-estimate stamps it: spread 2 bp + impact
  ;; 100 bp = 102 bp on $10,000 notional.
  {:spread-bps 2 :impact-bps 100
   :slippage-bps 102 :estimated-slippage-usd 102
   :notional-usd 10000})

(deftest twap-cost-slices-impact-below-the-one-shot-walk-test
  ;; 10 minutes = 21 suborders. Temporary impact 100/21 = 4.7619; permanent residue
  ;; 0.3*100*20/42 = 14.2857; twap impact 19.0476 vs 100 one-shot. Spread is still
  ;; paid in full (every clip crosses): total 21.0476 bp vs 102 one-shot.
  (let [twap (rebalance/twap-cost one-shot-cost 10)]
    (is (true? (:twap-adjusted? twap)))
    (is (= 21 (:suborders twap)))
    (is (near? 19.047619047619047 (:impact-bps twap)))
    (is (near? 21.047619047619047 (:slippage-bps twap)))
    (is (near? 21.047619047619047 (:estimated-slippage-usd twap)))
    (is (near? 2 (:spread-bps twap)))
    (is (near? 2 (:spread-usd twap)))
    (is (near? 19.047619047619047 (:impact-usd twap)))
    (is (< (:slippage-bps twap) (:slippage-bps one-shot-cost)))))

(deftest twap-cost-monotone-in-duration-with-permanent-floor-test
  ;; Longer working time -> smaller estimate, but never below the permanent-impact
  ;; asymptote: spread + phi/2 * impact = 2 + 15 = 17 bp.
  (let [at (fn [minutes] (:slippage-bps (rebalance/twap-cost one-shot-cost minutes)))]
    (is (> (at 5) (at 10)))
    (is (> (at 10) (at 20)))
    (is (> (at 20) (at 1440)))
    (is (> (at 1440) 17))
    (is (< (at 5) 102))))

(deftest twap-cost-passes-flat-estimates-through-unadjusted-test
  ;; A flat fallback / prebaked estimate has no impact component to slice — slicing a
  ;; flat 25 bp assumption into 21 clips of 25 bp would fabricate precision.
  (let [twap (rebalance/twap-cost {:slippage-bps 25 :estimated-slippage-usd 0.75} 10)]
    (is (false? (:twap-adjusted? twap)))
    (is (= 25 (:slippage-bps twap)))
    (is (= 0.75 (:estimated-slippage-usd twap)))
    (is (nil? (:spread-bps twap)))))

(deftest twap-cost-flags-clips-that-still-exceed-visible-depth-test
  ;; $1M worked over 5 minutes = 11 clips of ~$90.9k, against a $500 visible book:
  ;; even sliced, every clip overruns the book — flag it and inherit floor semantics.
  (let [twap (rebalance/twap-cost {:spread-bps 5 :impact-bps 500
                                   :slippage-bps 505 :estimated-slippage-usd 50500
                                   :notional-usd 1000000
                                   :visible-notional-usd 500
                                   :estimate-floor? true}
                                  5)]
    (is (true? (:twap-adjusted? twap)))
    (is (true? (:slice-exceeds-visible-depth? twap)))
    (is (true? (:estimate-floor? twap)))
    (is (< (:slippage-bps twap) 505)))
  ;; ...and stays quiet when a clip fits the visible book.
  (let [twap (rebalance/twap-cost (assoc one-shot-cost :visible-notional-usd 5000) 10)]
    (is (false? (:slice-exceeds-visible-depth? twap)))))

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

;; ── 0-bp cost-model guard (M7) ─────────────────────────────────────────────

(deftest implausibly-favorable-buy-snapshot-fill-is-flagged-untrusted-not-zero-test
  ;; A stale/coin-mismatched snapshot whose ask sits far BELOW the reference implies a
  ;; non-physical "free" buy fill; the favorable delta would clamp to 0 bp and read as
  ;; zero cost. The guard refuses that, reporting the configured flat fallback against the
  ;; reference under an explicit untrusted source.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 300
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 30
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [1.0]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :cost-contexts-by-id {"perp:BTC" {:source :snapshot
                                                    :asks [{:px "90" :sz "10"}]
                                                    :stale? false
                                                    :age-ms 1000}}})
        cost (get-in preview [:rows 0 :cost])]
    (is (= :ready (get-in preview [:rows 0 :status])))
    (is (= :untrusted-snapshot-fill (:source cost)))
    (is (= :implausible-favorable-fill (:fallback-reason cost)))
    (is (near? 30 (:slippage-bps cost)))           ;; the configured fallback, not 25
    (is (near? 100 (:estimated-fill-price cost)))   ;; the reference, not the bogus 90
    (is (near? 0.9 (:estimated-slippage-usd cost)))));; 300 * 30/10000

(deftest implausibly-favorable-sell-snapshot-fill-is-flagged-untrusted-test
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 300
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:BTC"]
                  :current-weights [1.0]
                  :target-weights [0.0]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :cost-contexts-by-id {"perp:BTC" {:source :snapshot
                                                    :bids [{:px "115" :sz "10"}]
                                                    :stale? false
                                                    :age-ms 1000}}})
        cost (get-in preview [:rows 0 :cost])]
    (is (= :sell (get-in preview [:rows 0 :side])))
    (is (= :untrusted-snapshot-fill (:source cost)))
    (is (near? 25 (:slippage-bps cost)))
    (is (near? 100 (:estimated-fill-price cost)))))

(deftest small-favorable-snapshot-fill-still-floors-to-zero-test
  ;; Genuine sub-threshold improvement (a buy filling just below the reference) is real
  ;; and still clamps to 0 bp — the guard only fires beyond the 50 bp band.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 300
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [1.0]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :cost-contexts-by-id {"perp:BTC" {:source :snapshot
                                                    :asks [{:px "99.9" :sz "10"}]
                                                    :stale? false
                                                    :age-ms 1000}}})
        cost (get-in preview [:rows 0 :cost])]
    (is (= :snapshot (:source cost)))
    (is (near? 99.9 (:estimated-fill-price cost)))
    (is (near? 0 (:slippage-bps cost)))))

(deftest build-rebalance-preview-exposes-maker-fee-for-resting-orders-test
  ;; The cost carries both the taker fee (estimated-fee-usd) and the maker fee, so the
  ;; execution tab can show the lower fee when a row is routed as a resting (maker) order.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  :rebalance-tolerance 0.0
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [0.1]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :fee-bps-by-id {"perp:BTC" 4.5}
                  :maker-fee-bps 1.5})
        cost (get-in preview [:rows 0 :cost])]
    ;; notional 1000: taker 4.5 bp -> $0.45, maker 1.5 bp -> $0.15
    (is (near? 0.45 (:estimated-fee-usd cost)))
    (is (= 1.5 (:maker-fee-bps cost)))
    (is (near? 0.15 (:maker-fee-usd cost)))))

;; ── price-cost decomposition (spread + impact) ─────────────────────────────

(deftest build-rebalance-preview-decomposes-price-cost-into-spread-and-impact-test
  ;; :slippage-bps (price cost) already bundles crossing the spread + walking the book. With
  ;; asks [101,102] vs mark 100 for a 3-unit buy, the touch (101) is the spread crossing
  ;; (100 bp) and the residual to the VWAP (101.667) is the book impact (66.67 bp); the two
  ;; sum back to the total price cost.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 300
                  :rebalance-tolerance 0.0
                  :fallback-slippage-bps 25
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [1.0]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :cost-contexts-by-id {"perp:BTC" {:source :snapshot
                                                    :asks [{:px "101" :sz "1"}
                                                           {:px "102" :sz "2"}]
                                                    :stale? false
                                                    :age-ms 0}}})
        cost (get-in preview [:rows 0 :cost])]
    (is (near? 166.66666666666669 (:slippage-bps cost)))   ; total price cost, unchanged
    (is (near? 100 (:spread-bps cost)))
    (is (near? 66.66666666666669 (:impact-bps cost)))
    (is (near? (:slippage-bps cost) (+ (:spread-bps cost) (:impact-bps cost))))
    ;; notional 300: spread 300*100/10000 = 3, impact 300*66.667/10000 = 2 (sum = total 5)
    (is (near? 3 (:spread-usd cost)))
    (is (near? 2 (:impact-usd cost)))))

(deftest top-of-book-price-cost-is-all-spread-no-impact-test
  ;; A fill that only reaches the touch (best ask) has spread crossing but no book impact.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  :rebalance-tolerance 0.0
                  :instrument-ids ["perp:BTC"]
                  :current-weights [0.0]
                  :target-weights [0.2]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp :coin "BTC"}}
                  :prices-by-id {"perp:BTC" 100}
                  :cost-contexts-by-id {"perp:BTC" {:source :live-orderbook
                                                    :best-bid {:px-num 99}
                                                    :best-ask {:px-num 101}}}})
        cost (get-in preview [:rows 0 :cost])]
    (is (near? 100 (:slippage-bps cost)))
    (is (near? 100 (:spread-bps cost)))
    (is (near? 0 (:impact-bps cost)))))

;; ── realized slippage helper ───────────────────────────────────────────────

(deftest realized-slippage-bps-matches-estimate-convention-test
  ;; buy filling 1% above the reference -> 100 bp; a favorable fill floors to 0 like the
  ;; estimate so realized and estimated are directly comparable.
  (is (near? 100 (rebalance/realized-slippage-bps :buy 100 101)))
  (is (near? 100 (rebalance/realized-slippage-bps :sell 100 99)))
  (is (near? 0 (rebalance/realized-slippage-bps :buy 100 99)))
  (is (nil? (rebalance/realized-slippage-bps :buy 100 nil))))

;; ── excluded (no optimizer target) rows ────────────────────────────────────

(deftest build-rebalance-preview-holds-rows-with-no-target-weight-test
  ;; A nil target means the optimizer expressed NO opinion (out-of-universe holding,
  ;; asset dropped by the allocator). The row must be held — never staged as a
  ;; sell-to-zero — and must not count as ready or blocked.
  (let [preview (rebalance/build-rebalance-preview
                 {:capital-usd 10000
                  :rebalance-tolerance 0.005
                  :instrument-ids ["perp:BTC" "spot:@107"]
                  :current-weights [0.2 0.01]
                  :target-weights [0.35 nil]
                  :instruments-by-id {"perp:BTC" {:instrument-type :perp
                                                  :coin "BTC"}
                                      "spot:@107" {:instrument-type :spot
                                                   :coin "HYPE"}}
                  :prices-by-id {"perp:BTC" 30000
                                 "spot:@107" 40}})
        [perp-row spot-row] (:rows preview)]
    (is (= :ready (:status perp-row)))
    (is (= :excluded (:status spot-row)))
    (is (= :excluded-from-optimization (:reason spot-row)))
    (is (= :none (:side spot-row)))
    (is (nil? (:target-weight spot-row)))
    (is (zero? (:delta-notional-usd spot-row)))
    (is (= :ready (:status preview)))
    (is (= 1 (get-in preview [:summary :ready-count])))
    (is (= 0 (get-in preview [:summary :blocked-count])))
    (is (= 1 (get-in preview [:summary :excluded-count])))
    ;; The held row contributes nothing to the trade notional.
    (is (near? 1500 (get-in preview [:summary :gross-trade-notional-usd])))))
