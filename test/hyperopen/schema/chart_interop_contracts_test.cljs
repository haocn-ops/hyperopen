(ns hyperopen.schema.chart-interop-contracts-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.schema.chart-interop-contracts :as chart-contracts]
            [hyperopen.views.trading-chart.utils.indicators :as indicators]))

(deftest assert-indicators-accepts-view-indicator-series-data-shape-test
  (let [candles [{:time 1 :open 100 :high 101 :low 99 :close 100 :volume 10}
                 {:time 2 :open 100 :high 102 :low 98 :close 101 :volume 11}
                 {:time 3 :open 101 :high 103 :low 100 :close 102 :volume 12}
                 {:time 4 :open 102 :high 104 :low 101 :close 103 :volume 13}]
        indicator (indicators/calculate-indicator :sma candles {:period 3})
        indicators-data [indicator]]
    (is (some? indicator))
    (is (= indicators-data
           (chart-contracts/assert-indicators! indicators-data {:boundary :test/contracts})))))

(deftest assert-legend-meta-accepts-market-open-flag-test
  (let [legend-meta {:symbol "BTC"
                     :timeframe-label "1D"
                     :venue "Hyperopen"
                     :market-open? false
                     :candle-data [{:time 1 :open 100 :high 101 :low 99 :close 100}]}]
    (is (= legend-meta
           (chart-contracts/assert-legend-meta! legend-meta {:boundary :test/contracts})))))
