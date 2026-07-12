(ns hyperopen.margin-rec-modules
  "Loader for the lazy :margin_rec shadow module (same pattern as
  hyperopen.trading-crypto-modules): the recommendation engine's exported
  entry points resolve from goog globals after shadow.loader fetches the
  module, keeping the simulation code out of the :main bundle."
  (:require [goog.object :as gobj]
            [shadow.loader :as loader]))

(def ^:private margin-rec-module-name
  "margin_rec")

(def ^:private exported-function-paths
  {:compute-recommendation-async!
   ["hyperopen" "margin_rec" "engine_module" "computeRecommendationAsync"]})

(defonce ^:private resolved-engine* (atom nil))
(defonce ^:private inflight-load* (atom nil))

(defn reset-margin-rec-module-state!
  []
  (reset! resolved-engine* nil)
  (reset! inflight-load* nil))

(defn- resolve-exported-function
  [path-segments]
  (let [root (or (some-> js/goog .-global)
                 js/globalThis)]
    (reduce (fn [acc segment]
              (when acc
                (gobj/get acc segment)))
            root
            path-segments)))

(defn- engine-ready?
  [resolved]
  (and (map? resolved)
       (every? fn? (vals resolved))))

(defn- resolve-exported-engine
  []
  (reduce-kv (fn [resolved key path-segments]
               (assoc resolved key (resolve-exported-function path-segments)))
             {}
             exported-function-paths))

(defn resolved-margin-rec-engine
  []
  (let [cached @resolved-engine*]
    (cond
      (engine-ready? cached)
      cached

      (some? cached)
      (do
        (reset-margin-rec-module-state!)
        nil)

      :else
      (let [resolved (resolve-exported-engine)]
        (when (engine-ready? resolved)
          (reset! resolved-engine* resolved)
          resolved)))))

(defn load-margin-rec-engine-module!
  []
  (if-let [existing (resolved-margin-rec-engine)]
    (js/Promise.resolve existing)
    (if-let [existing-load @inflight-load*]
      existing-load
      (let [resolve-loaded!
            (fn []
              (let [resolved (resolve-exported-engine)]
                (when-not (engine-ready? resolved)
                  (throw (js/Error.
                          "Loaded margin-rec module without exported engine.")))
                (reset! resolved-engine* resolved)
                resolved))
            load-promise
            (-> (loader/load margin-rec-module-name)
                (.then (fn [_] (resolve-loaded!)))
                (.catch (fn [error]
                          (reset! inflight-load* nil)
                          (throw error))))]
        (reset! inflight-load* load-promise)
        load-promise))))
