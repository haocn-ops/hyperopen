(ns hyperopen.leaderboard.preferences
  (:require [hyperopen.leaderboard.actions :as leaderboard-actions]
            [hyperopen.platform :as platform]
            [hyperopen.platform.indexed-db :as indexed-db]))

(def ^:private leaderboard-preferences-id
  "leaderboard-ui-preferences:v1")

(def ^:private leaderboard-preferences-version
  1)

(defn- ->promise
  [result]
  (if (instance? js/Promise result)
    result
    (js/Promise.resolve result)))

(defn- normalize-saved-at-ms
  [value]
  (let [candidate (cond
                    (number? value) value
                    (string? value) (js/parseFloat value)
                    :else js/NaN)]
    (if (js/isFinite candidate)
      (js/Math.floor candidate)
      0)))

(defn leaderboard-preferences-fingerprint
  [state]
  {:timeframe (leaderboard-actions/normalize-leaderboard-timeframe
               (get-in state [:leaderboard-ui :timeframe]))
   :sort {:column (leaderboard-actions/normalize-leaderboard-sort-column
                   (get-in state [:leaderboard-ui :sort :column]))
          :direction (leaderboard-actions/normalize-leaderboard-sort-direction
                      (get-in state [:leaderboard-ui :sort :direction]))}
   :page-size (leaderboard-actions/normalize-leaderboard-page-size
               (get-in state [:leaderboard-ui :page-size]))})

(defn normalize-leaderboard-preferences-record
  [raw]
  (when (map? raw)
    (let [normalized (leaderboard-preferences-fingerprint
                      {:leaderboard-ui {:timeframe (:timeframe raw)
                                        :sort (:sort raw)
                                        :page-size (:page-size raw)}})]
      {:id (or (:id raw) leaderboard-preferences-id)
       :version leaderboard-preferences-version
       :saved-at-ms (normalize-saved-at-ms (:saved-at-ms raw))
       :timeframe (:timeframe normalized)
       :sort (:sort normalized)
       :page-size (:page-size normalized)})))

(defn- build-leaderboard-preferences-record
  [state now-ms-fn]
  (let [{:keys [timeframe sort page-size]}
        (leaderboard-preferences-fingerprint state)]
    {:id leaderboard-preferences-id
     :version leaderboard-preferences-version
     :saved-at-ms (now-ms-fn)
     :timeframe timeframe
     :sort sort
     :page-size page-size}))

(defn load-leaderboard-preferences!
  ([] (load-leaderboard-preferences! {}))
  ([{:keys [load-indexed-db-fn]
     :or {load-indexed-db-fn (fn []
                               (indexed-db/get-json! indexed-db/leaderboard-preferences-store
                                                     leaderboard-preferences-id))}}]
   (-> (->promise (load-indexed-db-fn))
       (.then (fn [record]
                (when record
                  (normalize-leaderboard-preferences-record record))))
       (.catch (fn [error]
                 (js/console.warn "Failed to load leaderboard preferences from IndexedDB:" error)
                 nil)))))

(defn persist-leaderboard-preferences!
  ([state]
   (persist-leaderboard-preferences! state {}))
  ([state {:keys [now-ms-fn
                  persist-indexed-db-fn]
           :or {now-ms-fn platform/now-ms
                persist-indexed-db-fn (fn [record]
                                        (indexed-db/put-json! indexed-db/leaderboard-preferences-store
                                                              leaderboard-preferences-id
                                                              record))}}]
   (let [record (build-leaderboard-preferences-record state now-ms-fn)]
     (-> (->promise (persist-indexed-db-fn record))
         (.catch (fn [error]
                   (js/console.warn "Failed to persist leaderboard preferences to IndexedDB:" error)
                   false))))))

(defn- apply-restored-leaderboard-preferences
  [state record]
  (-> state
      (assoc-in [:leaderboard-ui :timeframe] (:timeframe record))
      (assoc-in [:leaderboard-ui :sort] (:sort record))
      (assoc-in [:leaderboard-ui :page-size] (:page-size record))))

(defn restore-leaderboard-preferences!
  ([store]
   (restore-leaderboard-preferences! store {}))
  ([store {:keys [load-preferences-fn]
           :or {load-preferences-fn load-leaderboard-preferences!}}]
   (let [initial-fingerprint (leaderboard-preferences-fingerprint @store)]
     (-> (->promise (load-preferences-fn))
         (.then (fn [record]
                  (when (and record
                             (= initial-fingerprint
                                (leaderboard-preferences-fingerprint @store)))
                    (swap! store apply-restored-leaderboard-preferences record))
                  record))
         (.catch (fn [error]
                   (js/console.warn "Failed to restore leaderboard preferences:" error)
                   nil))))))
