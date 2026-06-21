(ns hyperopen.workbench.scenes.optimize.rebalance-scenes
  "Workbench scenes for the optimizer rebalance preview tab
  (views.portfolio.optimize.rebalance-tab). Renders the pure tab against a
  seeded `last-successful-run` so the v4 dense-table / flush-KPI / outcome-rail
  layout can be eyeballed in isolation, inside the .portfolio-optimizer scope."
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.views.portfolio.optimize.rebalance-tab :as rebalance-tab]))

(portfolio/configure-scenes
  {:title "Rebalance preview"
   :collection :optimize})

(def ^:private sample-run
  {:result
   {:status :solved
    :labels-by-instrument {"perp:BTC" "BTC"
                           "spot:SOL" "SOL"
                           "perp:SOL" "SOL"
                           "perp:ETH" "ETH"
                           "spot:HYPE" "HYPE"
                           "perp:ARB" "ARB"}
    :current-volatility 0.148
    :volatility 0.120
    :current-expected-return 0.112
    :expected-return 0.146
    :current-performance {:in-sample-sharpe 0.76}
    :performance {:in-sample-sharpe 1.22}
    :diagnostics {:turnover 0.184}
    :rebalance-preview
    {:status :partially-blocked
     :capital-usd 950000
     :summary {:ready-count 4
               :blocked-count 1
               :gross-trade-notional-usd 428102
               :estimated-fees-usd 150
               :estimated-slippage-usd 280
               :margin {:after-utilization 0.42 :warning :none}}
     :rows [{:instrument-id "perp:BTC" :side :sell
             :delta-weight -0.080 :delta-notional-usd -174720 :quantity 8.0
             :status :ready
             :cost {:source :snapshot :age-ms 3000 :depth-status :full-visible-depth
                    :slippage-bps 5.2 :estimated-slippage-usd 91 :estimated-fee-usd 52}}
            {:instrument-id "spot:SOL" :side :buy
             :delta-weight 0.050 :delta-notional-usd 78395 :quantity 410.0
             :status :ready
             :cost {:source :snapshot :age-ms 1000 :depth-status :full-visible-depth
                    :slippage-bps 4.1 :estimated-slippage-usd 32 :estimated-fee-usd 23}}
            {:instrument-id "perp:SOL" :side :buy
             :delta-weight 0.030 :delta-notional-usd 98535 :quantity 515.0
             :status :ready
             :cost {:source :orderbook :age-ms 500 :depth-status :full-visible-depth
                    :slippage-bps 6.4 :estimated-slippage-usd 63 :estimated-fee-usd 30}}
            {:instrument-id "perp:ETH" :side :buy
             :delta-weight 0.040 :delta-notional-usd 76452 :quantity 21.5
             :status :ready
             :cost {:source :snapshot :age-ms 8000 :depth-status :insufficient-visible-depth
                    :slippage-bps 9.1 :estimated-slippage-usd 70 :estimated-fee-usd 23}}
            {:instrument-id "perp:ARB" :side :buy
             :delta-weight 0.001 :delta-notional-usd 8 :quantity 4.2
             :status :blocked :reason :below-min-notional}]}}})

(defn- shell
  [content]
  (layout/page-shell
   (layout/desktop-shell
    [:div {:class ["portfolio-optimizer" "w-full" "p-6"]}
     content])))

(portfolio/defscene default
  []
  (shell (rebalance-tab/rebalance-tab sample-run)))

(portfolio/defscene empty-state
  []
  (shell (rebalance-tab/rebalance-tab {:result {:status :failed}})))
