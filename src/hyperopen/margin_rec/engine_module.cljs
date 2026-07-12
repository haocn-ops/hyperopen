(ns hyperopen.margin-rec.engine-module
  "Lazy-module entry for the isolated-margin recommendation engine.

  Loaded on demand through hyperopen.margin-rec-modules; nothing in here may
  be required from :main. The exported compute runs the simulation in
  idle-scheduled batches so a recommendation never occupies the main thread
  for more than one small slice at a time."
  (:require [hyperopen.margin-rec.paths :as paths]
            [hyperopen.margin-rec.recommend :as recommend]
            [hyperopen.platform :as platform]))

(def ^:private batch-idle-timeout-ms 250)

(defn- run-batches!
  [ctx resolve]
  (let [total (:paths-count ctx)
        bars (:bars ctx)
        params (:params ctx)
        batches (atom [])
        offset (atom 0)
        step (fn step []
               (if (>= @offset total)
                 (resolve (recommend/finalize ctx (paths/sort-required @batches)))
                 (let [n (min recommend/batch-size (- total @offset))]
                   (swap! batches conj (paths/simulate-batch bars params @offset n))
                   (swap! offset + n)
                   (platform/schedule-idle-or-timeout! step batch-idle-timeout-ms))))]
    (step)))

(defn ^:export computeRecommendationAsync
  "Inputs map (see hyperopen.margin-rec.recommend/prepare) -> js/Promise of
  the recommendation result map. Terminal prepare statuses resolve
  immediately."
  [inputs]
  (js/Promise.
   (fn [resolve _reject]
     (let [ctx (recommend/prepare inputs)]
       (if (= :ready (:status ctx))
         (run-batches! ctx resolve)
         (resolve ctx))))))
