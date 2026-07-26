(ns hyperopen.views.trading-chart.runtime-history-backfill-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.views.trading-chart.runtime :as runtime]
            [hyperopen.views.trading-chart.runtime-state :as chart-runtime]
            [hyperopen.views.trading-chart.utils.chart-interop :as chart-interop]))

(defn- expose-arity!
  [f arity]
  (aset f (str "cljs$core$IFn$_invoke$arity$" arity) f)
  f)

(def noop-2
  (expose-arity! (fn [_ _] nil) 2))

(def noop-3
  (expose-arity! (fn [_ _ _] nil) 3))

(def noop-4
  (expose-arity! (fn [_ _ _ _] nil) 4))

(defn- fake-time-scale
  [visible-range]
  (doto #js {}
    (aset "getVisibleLogicalRange" (fn [] visible-range))))

(defn- fake-chart
  [time-scale]
  (doto #js {}
    (aset "timeScale" (fn [] time-scale))
    (aset "removeSeries" (fn [_] nil))
    (aset "remove" (fn [] nil))))

(defn- fake-chart-obj
  [chart]
  (doto #js {}
    (aset "chart" chart)
    (aset "mainSeries" #js {:id "main"})
    (aset "volumeSeries" #js {:id "volume"})
    (aset "indicatorSeries" #js [])))

(defn- base-context
  [overrides]
  (let [candle-data (or (:candle-data overrides)
                        [{:time 1700000000 :open 100 :high 101 :low 99 :close 100 :volume 10}])]
    (merge {:candle-data candle-data
            :chart-type :candlestick
            :indicators-data []
            :indicator-series-data []
            :legend-meta {:symbol "BTC"
                          :timeframe-label "1D"
                          :venue "Hyperopen"
                          :candle-data candle-data}
            :legend-deps {}
            :series-options {:price-decimals 2}
            :selected-timeframe :1d
            :persistence-deps {:asset "BTC"
                               :candles candle-data}
            :volume-visible? true
            :main-series-markers []
            :position-overlay nil
            :position-overlay-deps {}
            :open-order-overlays []
            :overlay-deps {}
            :volume-indicator-deps {}
            :context-menu-deps {}}
           overrides)))

(defn- render!
  [context lifecycle node]
  ((runtime/chart-canvas-on-render context)
   {:replicant/life-cycle lifecycle
    :replicant/node node}))

(defn- with-chart-runtime
  [chart-obj subscribe-opts f]
  (with-redefs [chart-interop/create-chart-with-volume-and-series!
                (expose-arity! (fn [_ _ _ _] chart-obj) 4)
                chart-interop/create-chart-with-indicators!
                (expose-arity! (fn [_ _ _ _ _] chart-obj) 5)
                chart-interop/create-legend!
                (expose-arity! (fn [_ _ _ _]
                                 #js {:update (fn [_] nil)
                                      :destroy (fn [] nil)})
                               4)
                chart-interop/sync-baseline-base-value-subscription! noop-2
                chart-interop/set-series-data! noop-4
                chart-interop/set-volume-data! noop-2
                chart-interop/set-indicator-data! noop-2
                chart-interop/set-main-series-markers! noop-2
                chart-interop/sync-position-overlays! noop-4
                chart-interop/sync-open-order-overlays! noop-4
                chart-interop/sync-volume-indicator-overlay! noop-4
                chart-interop/sync-chart-context-menu-overlay! noop-4
                chart-interop/sync-chart-navigation-overlay! noop-4
                chart-interop/apply-default-visible-range! noop-2
                chart-interop/apply-persisted-visible-range!
                (expose-arity! (fn [_ _ _] (js/Promise.resolve nil)) 3)
                chart-interop/subscribe-visible-range-persistence!
                (expose-arity! (fn [_ _ opts]
                                 (reset! subscribe-opts opts)
                                 (fn [] nil))
                               3)]
    (f)))

(deftest chart-runtime-requests-history-backfill-near-loaded-left-edge-test
  (let [node #js {}
        backfill-calls (atom [])
        subscribe-opts (atom nil)
        chart (fake-chart (fake-time-scale #js {:from -4 :to 90}))
        chart-obj (fake-chart-obj chart)
        candles [{:time 1700000000 :open 100 :high 101 :low 99 :close 100 :volume 10}
                 {:time 1700086400 :open 101 :high 102 :low 100 :close 101 :volume 12}]]
    (with-chart-runtime
     chart-obj
     subscribe-opts
     (fn []
       (render! (base-context {:candle-data candles
                               :persistence-deps {:asset "BTC"
                                                  :candles candles}
                               :on-history-backfill-request #(swap! backfill-calls conj %)
                               :schedule-decoration-frame! (fn [_] nil)
                               :cancel-decoration-frame! (fn [_] nil)})
                :replicant.life-cycle/mount
                node)
       (is (fn? (:on-visible-range-change! @subscribe-opts)))
       (is (fn? (:on-visible-range-event! @subscribe-opts)))
       ((:on-visible-range-event! @subscribe-opts))
       ((:on-visible-range-event! @subscribe-opts))
       (is (= [{:interval :1d
                :bars 330
                :end-time-ms 1699999999999}]
              @backfill-calls))
       (chart-runtime/clear-state! node)))))

(deftest chart-runtime-requests-history-backfill-from-restored-left-edge-range-test
  (async done
    (let [node #js {}
          backfill-calls (atom [])
          visible-range (atom #js {:from 210 :to 337})
          time-scale (doto #js {}
                       (aset "getVisibleLogicalRange" (fn [] @visible-range)))
          chart (fake-chart time-scale)
          chart-obj (fake-chart-obj chart)
          candles [{:time 1700000000 :open 100 :high 101 :low 99 :close 100 :volume 10}
                   {:time 1700086400 :open 101 :high 102 :low 100 :close 101 :volume 12}]]
      (with-redefs [chart-interop/create-chart-with-volume-and-series!
                    (expose-arity! (fn [_ _ _ _] chart-obj) 4)
                    chart-interop/create-chart-with-indicators!
                    (expose-arity! (fn [_ _ _ _ _] chart-obj) 5)
                    chart-interop/create-legend!
                    (expose-arity! (fn [_ _ _ _]
                                     #js {:update (fn [_] nil)
                                          :destroy (fn [] nil)})
                                   4)
                    chart-interop/sync-baseline-base-value-subscription! noop-2
                    chart-interop/set-main-series-markers! noop-2
                    chart-interop/sync-position-overlays! noop-4
                    chart-interop/sync-open-order-overlays! noop-4
                    chart-interop/sync-volume-indicator-overlay! noop-4
                    chart-interop/sync-chart-context-menu-overlay! noop-4
                    chart-interop/sync-chart-navigation-overlay! noop-4
                    chart-interop/apply-default-visible-range! noop-2
                    chart-interop/apply-persisted-visible-range!
                    (expose-arity! (fn [_ _ _]
                                     (reset! visible-range #js {:from -520 :to 90})
                                     (js/Promise.resolve true))
                                   3)
                    chart-interop/subscribe-visible-range-persistence!
                    (expose-arity! (fn [_ _ _] (fn [] nil)) 3)]
        (render! (base-context {:candle-data candles
                                :persistence-deps {:asset "BTC"
                                                   :candles candles}
                                :on-history-backfill-request #(swap! backfill-calls conj %)
                                :schedule-decoration-frame! (fn [_] nil)
                                :cancel-decoration-frame! (fn [_] nil)})
                 :replicant.life-cycle/mount
                 node)
        (-> (js/Promise.resolve nil)
            (.then (fn []
                     (is (= [{:interval :1d
                              :bars 552
                              :end-time-ms 1699999999999}]
                            @backfill-calls))
                     (chart-runtime/clear-state! node)
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (chart-runtime/clear-state! node)
                      (done))))))))

(deftest chart-runtime-sizes-history-backfill-to-visible-empty-left-range-test
  (let [node #js {}
        backfill-calls (atom [])
        subscribe-opts (atom nil)
        chart (fake-chart (fake-time-scale #js {:from -520 :to 90}))
        chart-obj (fake-chart-obj chart)
        candles [{:time 1700000000 :open 100 :high 101 :low 99 :close 100 :volume 10}
                 {:time 1700086400 :open 101 :high 102 :low 100 :close 101 :volume 12}]]
    (with-chart-runtime
     chart-obj
     subscribe-opts
     (fn []
       (render! (base-context {:candle-data candles
                               :persistence-deps {:asset "BTC"
                                                  :candles candles}
                               :on-history-backfill-request #(swap! backfill-calls conj %)
                               :schedule-decoration-frame! (fn [_] nil)
                               :cancel-decoration-frame! (fn [_] nil)})
                :replicant.life-cycle/mount
                node)
       ((:on-visible-range-event! @subscribe-opts))
       (is (= [{:interval :1d
                :bars 552
                :end-time-ms 1699999999999}]
              @backfill-calls))
       (chart-runtime/clear-state! node)))))

(deftest chart-runtime-samples-history-backfill-on-every-visible-range-event-test
  (let [node #js {}
        backfill-calls (atom [])
        subscribe-opts (atom nil)
        visible-range (atom #js {:from 64 :to 180})
        time-scale (doto #js {}
                     (aset "getVisibleLogicalRange" (fn [] @visible-range)))
        chart (fake-chart time-scale)
        chart-obj (fake-chart-obj chart)
        candles [{:time 1700000000 :open 100 :high 101 :low 99 :close 100 :volume 10}
                 {:time 1700086400 :open 101 :high 102 :low 100 :close 101 :volume 12}]]
    (with-chart-runtime
     chart-obj
     subscribe-opts
     (fn []
       (render! (base-context {:candle-data candles
                               :persistence-deps {:asset "BTC"
                                                  :candles candles}
                               :on-history-backfill-request #(swap! backfill-calls conj %)
                               :schedule-decoration-frame! (fn [_] nil)
                               :cancel-decoration-frame! (fn [_] nil)})
                :replicant.life-cycle/mount
                node)
       (is (fn? (:on-visible-range-change! @subscribe-opts)))
       (is (fn? (:on-visible-range-event! @subscribe-opts)))
       ((:on-visible-range-change! @subscribe-opts))
       (is (empty? @backfill-calls))
       (reset! visible-range #js {:from -520 :to 90})
       ((:on-visible-range-event! @subscribe-opts))
       (is (= [{:interval :1d
                :bars 552
                :end-time-ms 1699999999999}]
              @backfill-calls))
       (chart-runtime/clear-state! node)))))

(deftest chart-runtime-continues-history-backfill-after-prepending-data-test
  (let [node #js {}
        backfill-calls (atom [])
        subscribe-opts (atom nil)
        chart (fake-chart (fake-time-scale #js {:from -520 :to 90}))
        chart-obj (fake-chart-obj chart)
        first-candles [{:time 1700000000 :open 100 :high 101 :low 99 :close 100 :volume 10}
                       {:time 1700086400 :open 101 :high 102 :low 100 :close 101 :volume 12}]
        prepended-candles [{:time 1699913600 :open 99 :high 100 :low 98 :close 99 :volume 8}
                           {:time 1700000000 :open 100 :high 101 :low 99 :close 100 :volume 10}
                           {:time 1700086400 :open 101 :high 102 :low 100 :close 101 :volume 12}]]
    (with-chart-runtime
     chart-obj
     subscribe-opts
     (fn []
       (render! (base-context {:candle-data first-candles
                               :persistence-deps {:asset "BTC"
                                                  :candles first-candles}
                               :on-history-backfill-request #(swap! backfill-calls conj %)
                               :schedule-decoration-frame! (fn [_] nil)
                               :cancel-decoration-frame! (fn [_] nil)})
                :replicant.life-cycle/mount
                node)
       ((:on-visible-range-event! @subscribe-opts))
       (render! (base-context {:candle-data prepended-candles
                               :persistence-deps {:asset "BTC"
                                                  :candles prepended-candles}
                               :on-history-backfill-request #(swap! backfill-calls conj %)
                               :schedule-decoration-frame! (fn [_] nil)
                               :cancel-decoration-frame! (fn [_] nil)})
                :replicant.life-cycle/update
                node)
       (is (= [{:interval :1d
                :bars 552
                :end-time-ms 1699999999999}
               {:interval :1d
                :bars 552
                :end-time-ms 1699913599999}]
              @backfill-calls))
       (chart-runtime/clear-state! node)))))
