(ns hyperopen.views.trading-chart.runtime-visible-range-restore-test
  "Covers the symbol-load race: a partial seed dataset (live candle from
   the websocket/trades stream) mounts the chart before the REST snapshot
   lands. The visible-range restore must re-run when the snapshot prepends
   history over a seed, must not re-run on live tail appends or on
   user-driven backfills of full datasets, and seed-era visible ranges
   must never be persisted."
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trading-chart.runtime :as runtime]
            [hyperopen.views.trading-chart.runtime-state :as chart-runtime]
            [hyperopen.views.trading-chart.utils.chart-interop :as chart-interop]))

(defn- expose-arity!
  [f arity]
  (aset f (str "cljs$core$IFn$_invoke$arity$" arity) f)
  f)

(def noop-2
  (expose-arity! (fn [_ _] nil) 2))

(def noop-4
  (expose-arity! (fn [_ _ _ _] nil) 4))

(defn- fake-chart
  []
  (let [time-scale (doto #js {}
                     (aset "getVisibleLogicalRange" (fn [] nil)))]
    (doto #js {}
      (aset "timeScale" (fn [] time-scale))
      (aset "removeSeries" (fn [_] nil))
      (aset "remove" (fn [] nil)))))

(defn- fake-chart-obj
  [chart]
  (doto #js {}
    (aset "chart" chart)
    (aset "mainSeries" #js {:id "main"})
    (aset "volumeSeries" #js {:id "volume"})
    (aset "indicatorSeries" #js [])))

(defn- daily-candles
  [start-time count*]
  (vec
   (for [idx (range count*)]
     {:time (+ start-time (* idx 86400))
      :open (+ 100 idx)
      :high (+ 101 idx)
      :low (+ 99 idx)
      :close (+ 100 idx)
      :volume (+ 1000 idx)})))

(defn- base-context
  [candles overrides]
  (merge {:candle-data candles
          :chart-type :candlestick
          :indicators-data []
          :indicator-series-data []
          :legend-meta {:symbol "BTC"
                        :timeframe-label "1D"
                        :venue "Hyperopen"
                        :candle-data candles}
          :legend-deps {}
          :series-options {:price-decimals 2}
          :selected-timeframe :1d
          :persistence-deps {:asset "BTC"
                             :candles candles}
          :volume-visible? true
          :main-series-markers []
          :position-overlay nil
          :position-overlay-deps {}
          :open-order-overlays []
          :overlay-deps {}
          :volume-indicator-deps {}
          :context-menu-deps {}
          :schedule-decoration-frame! (fn [_] nil)
          :cancel-decoration-frame! (fn [_] nil)}
         overrides))

(defn- render!
  [context lifecycle node]
  ((runtime/chart-canvas-on-render context)
   {:replicant/life-cycle lifecycle
    :replicant/node node}))

(defn- with-chart-runtime
  [chart-obj default-range-calls subscribe-opts f]
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
                chart-interop/apply-default-visible-range!
                (expose-arity! (fn [_ candles]
                                 (swap! default-range-calls conj (count candles)))
                               2)
                chart-interop/apply-persisted-visible-range!
                (expose-arity! (fn [_ _ _] (js/Promise.resolve nil)) 3)
                chart-interop/subscribe-visible-range-persistence!
                (expose-arity! (fn [_ _ opts]
                                 (reset! subscribe-opts opts)
                                 (fn [] nil))
                               3)]
    (f)))

(deftest chart-runtime-restores-visible-range-after-snapshot-replaces-seed-test
  (let [node #js {}
        default-range-calls (atom [])
        subscribe-opts (atom nil)
        chart-obj (fake-chart-obj (fake-chart))
        seed-candles (daily-candles 1700000000 1)
        snapshot-candles (daily-candles (- 1700000000 (* 5 86400)) 6)]
    (with-chart-runtime
     chart-obj
     default-range-calls
     subscribe-opts
     (fn []
       (render! (base-context seed-candles {})
                :replicant.life-cycle/mount
                node)
       (is (= [1] @default-range-calls)
           "mount restores against the seed dataset")
       (render! (base-context snapshot-candles {})
                :replicant.life-cycle/update
                node)
       (is (= [1 6] @default-range-calls)
           "snapshot prepending history over a seed re-runs the restore")
       (is (= 6 (:visible-range-restore-candle-count (chart-runtime/get-state node))))
       (chart-runtime/clear-state! node)))))

(deftest chart-runtime-keeps-visible-range-on-live-tail-append-test
  (let [node #js {}
        default-range-calls (atom [])
        subscribe-opts (atom nil)
        chart-obj (fake-chart-obj (fake-chart))
        seed-candles (daily-candles 1700000000 2)
        appended-candles (daily-candles 1700000000 3)]
    (with-chart-runtime
     chart-obj
     default-range-calls
     subscribe-opts
     (fn []
       (render! (base-context seed-candles {})
                :replicant.life-cycle/mount
                node)
       (render! (base-context appended-candles {})
                :replicant.life-cycle/update
                node)
       (is (= [2] @default-range-calls)
           "live tail appends never re-run the restore")
       (chart-runtime/clear-state! node)))))

(deftest chart-runtime-keeps-visible-range-on-backfill-of-full-dataset-test
  (let [node #js {}
        default-range-calls (atom [])
        subscribe-opts (atom nil)
        chart-obj (fake-chart-obj (fake-chart))
        full-candles (daily-candles 1700000000 120)
        backfilled-candles (daily-candles (- 1700000000 (* 330 86400)) 450)]
    (with-chart-runtime
     chart-obj
     default-range-calls
     subscribe-opts
     (fn []
       (render! (base-context full-candles {})
                :replicant.life-cycle/mount
                node)
       (render! (base-context backfilled-candles {})
                :replicant.life-cycle/update
                node)
       (is (= [120] @default-range-calls)
           "history backfill onto a full dataset keeps the user's view")
       (chart-runtime/clear-state! node)))))

(deftest chart-runtime-blocks-visible-range-persistence-for-seed-datasets-test
  (let [node #js {}
        default-range-calls (atom [])
        subscribe-opts (atom nil)
        chart-obj (fake-chart-obj (fake-chart))
        seed-candles (daily-candles 1700000000 1)
        snapshot-candles (daily-candles (- 1700000000 (* 39 86400)) 40)]
    (with-chart-runtime
     chart-obj
     default-range-calls
     subscribe-opts
     (fn []
       (render! (base-context seed-candles {})
                :replicant.life-cycle/mount
                node)
       (let [allow-persist? (:allow-persist-fn @subscribe-opts)]
         (is (fn? allow-persist?))
         (is (false? (allow-persist?))
             "seed-era visible ranges must not be persisted")
         (render! (base-context snapshot-candles {})
                  :replicant.life-cycle/update
                  node)
         (is (true? (allow-persist?))
             "full datasets persist visible ranges again"))
       (chart-runtime/clear-state! node)))))
