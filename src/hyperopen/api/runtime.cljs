(ns hyperopen.api.runtime)

(defn make-runtime
  [{:keys [info-client]}]
  {:info-client info-client
   :public-webdata2-cache (atom nil)
   :ensure-perp-dexs-flight (atom nil)})

(defn info-client
  [runtime]
  (:info-client runtime))

(defn public-webdata2-cache
  [runtime]
  @(get runtime :public-webdata2-cache))

(defn set-public-webdata2-cache!
  [runtime snapshot]
  (reset! (get runtime :public-webdata2-cache) snapshot))

(defn ensure-perp-dexs-flight
  [runtime]
  @(get runtime :ensure-perp-dexs-flight))

(defn set-ensure-perp-dexs-flight!
  [runtime promise]
  (reset! (get runtime :ensure-perp-dexs-flight) promise))

(defn clear-ensure-perp-dexs-flight-if-tracked!
  [runtime tracked]
  (let [flight* (get runtime :ensure-perp-dexs-flight)]
    (when (identical? @flight* tracked)
      (reset! flight* nil))))

(defn reset-runtime!
  [runtime]
  (set-public-webdata2-cache! runtime nil)
  (set-ensure-perp-dexs-flight! runtime nil)
  nil)
