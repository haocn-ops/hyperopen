(ns hyperopen.views.trading-chart.utils.chart-interop.markers
  (:require ["lightweight-charts" :refer [createSeriesMarkers]]))

(defonce ^:private markers-sidecar (js/WeakMap.))

(defn- marker-state
  [chart-obj]
  (if chart-obj
    (or (.get markers-sidecar chart-obj) {})
    {}))

(defn- set-marker-state!
  [chart-obj state]
  (when chart-obj
    (.set markers-sidecar chart-obj state)))

(defn set-main-series-markers!
  "Attach/update markers on the main price series."
  ([chart-obj markers]
   (set-main-series-markers! chart-obj markers {}))
  ([chart-obj markers {:keys [create-markers]
                       :or {create-markers createSeriesMarkers}}]
   (when-let [main-series (when chart-obj (.-mainSeries ^js chart-obj))]
     (let [{:keys [plugin series markers-ref]} (marker-state chart-obj)
           active-plugin (if (and plugin (identical? series main-series))
                           plugin
                           (let [created (create-markers main-series #js [])]
                             (set-marker-state! chart-obj {:plugin created
                                                           :series main-series})
                             created))
           marker-data (if (sequential? markers) markers [])
           stale-markers? (or (not (identical? active-plugin plugin))
                              (not (identical? marker-data markers-ref)))]
       (when stale-markers?
         (.setMarkers ^js active-plugin (clj->js marker-data))
         (set-marker-state! chart-obj {:plugin active-plugin
                                       :series main-series
                                       :markers-ref marker-data}))))))
