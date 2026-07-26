(ns hyperopen.views.trading-chart.utils.chart-interop.chart-creation-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.schema.chart-interop-contracts :as chart-contracts]
            [hyperopen.views.trading-chart.utils.chart-interop :as chart-interop]
            [hyperopen.views.trading-chart.test-support.fake-dom :as fake-dom]))

(deftest create-chart-with-volume-and-series-skips-volume-pane-when-hidden-test
  (let [chart #js {:addSeries (fn [& _]
                                (throw (js/Error. "volume series should not be created when hidden")))}
        set-main-data-calls (atom 0)
        main-series #js {:id "main"
                         :applyOptions (fn [_] nil)
                         :setData (fn [_]
                                    (swap! set-main-data-calls inc))}
        set-volume-data-calls (atom 0)
        fit-content-calls (atom 0)
        candles [{:time 1 :open 10 :high 11 :low 9 :close 10.5 :volume 100}]
        chart-obj (with-redefs [chart-contracts/assert-candles! (fn
                                                                   ([value _context]
                                                                    value)
                                                                   ([value _context _opts]
                                                                    value))
                                chart-interop/create-chart! (fn [_]
                                                              chart)
                                chart-interop/add-series! (fn [_ _]
                                                            main-series)
                                chart-interop/set-volume-data! (fn [_ _]
                                                                 (swap! set-volume-data-calls inc))
                                chart-interop/fit-content! (fn [_]
                                                             (swap! fit-content-calls inc))]
                    (chart-interop/create-chart-with-volume-and-series!
                     (fake-dom/make-fake-element "div")
                     :candlestick
                     candles
                     {:series-options {:price-decimals 2}
                      :volume-visible? false}))]
    (is (identical? main-series (.-mainSeries ^js chart-obj)))
    (is (nil? (.-volumeSeries ^js chart-obj)))
    (is (nil? (.-volumePaneIndex ^js chart-obj)))
    (is (= 1 @set-main-data-calls))
    (is (zero? @set-volume-data-calls))
    (is (= 1 @fit-content-calls))))

(deftest create-chart-with-indicators-skips-volume-pane-when-hidden-test
  (let [chart #js {:addSeries (fn [& _]
                                (throw (js/Error. "volume series should not be created when hidden")))}
        set-main-data-calls (atom 0)
        main-series #js {:id "main"
                         :applyOptions (fn [_] nil)
                         :setData (fn [_]
                                    (swap! set-main-data-calls inc))}
        set-volume-data-calls (atom 0)
        fit-content-calls (atom 0)
        candles [{:time 1 :open 10 :high 11 :low 9 :close 10.5 :volume 100}]
        indicators []
        chart-obj (with-redefs [chart-contracts/assert-candles! (fn
                                                                   ([value _context]
                                                                    value)
                                                                   ([value _context _opts]
                                                                    value))
                                chart-contracts/assert-indicators! (fn [value _context]
                                                                      value)
                                chart-interop/create-chart! (fn [_]
                                                              chart)
                                chart-interop/add-series! (fn [_ _]
                                                            main-series)
                                chart-interop/set-volume-data! (fn [_ _]
                                                                 (swap! set-volume-data-calls inc))
                                chart-interop/fit-content! (fn [_]
                                                             (swap! fit-content-calls inc))]
                    (chart-interop/create-chart-with-indicators!
                     (fake-dom/make-fake-element "div")
                     :candlestick
                     candles
                     indicators
                     {:series-options {:price-decimals 2}
                      :volume-visible? false}))]
    (is (identical? main-series (.-mainSeries ^js chart-obj)))
    (is (nil? (.-volumeSeries ^js chart-obj)))
    (is (nil? (.-volumePaneIndex ^js chart-obj)))
    (is (zero? (alength (.-indicatorSeries ^js chart-obj))))
    (is (= 1 @set-main-data-calls))
    (is (zero? @set-volume-data-calls))
    (is (= 1 @fit-content-calls))))

(deftest legacy-candlestick-wrappers-delegate-test
  (let [created-chart #js {:id "chart"}
        applied-data (atom nil)
        series* #js {:setData (fn [data]
                                (reset! applied-data (js->clj data :keywordize-keys true)))}
        candles [{:time 1 :open 10 :high 12 :low 9 :close 11}]]
    (with-redefs [chart-interop/create-chart! (fn [_] created-chart)]
      (is (identical? created-chart (chart-interop/create-candlestick-chart! #js {:id "container"}))))
    (chart-interop/set-candlestick-data! series* candles)
    (is (= candles @applied-data))))

