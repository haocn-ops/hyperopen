(ns hyperopen.leaderboard.cache
  (:require [hyperopen.leaderboard.normalization :as normalization]
            [hyperopen.platform :as platform]
            [hyperopen.platform.indexed-db :as indexed-db]))

(def leaderboard-cache-id
  "leaderboard-cache:v1")

(def leaderboard-cache-version
  1)

(def leaderboard-cache-ttl-ms
  (* 60 60 1000))

(defn- ->promise
  [result]
  (if (instance? js/Promise result)
    result
    (js/Promise.resolve result)))

(defn- parse-saved-at-ms
  [value]
  (let [candidate (cond
                    (number? value) value
                    (string? value) (js/parseFloat value)
                    :else js/NaN)]
    (when (and (number? candidate)
               (js/isFinite candidate))
      (max 0 (js/Math.floor candidate)))))

(defn- normalize-leaderboard-cache-row
  [row]
  (when (map? row)
    (when-let [eth-address (or (normalization/normalize-address (:eth-address row))
                               (normalization/normalize-address (:ethAddress row)))]
      {:eth-address eth-address
       :account-value (or (normalization/parse-optional-num (or (:account-value row)
                                                                (:accountValue row)))
                          0)
       :display-name (or (normalization/non-blank-text (:display-name row))
                         (normalization/non-blank-text (:displayName row)))
       :prize (or (normalization/parse-optional-num (:prize row)) 0)
       :window-performances (normalization/normalize-window-performances
                             (or (:window-performances row)
                                 (:windowPerformances row)))})))

(defn- normalize-leaderboard-cache-rows
  [rows]
  (if (sequential? rows)
    (->> rows
         (keep normalize-leaderboard-cache-row)
         vec)
    []))

(defn- normalize-excluded-addresses
  [addresses]
  (let [entries (cond
                  (set? addresses) (seq addresses)
                  (sequential? addresses) addresses
                  :else [])]
    (reduce (fn [acc address]
              (if-let [address* (normalization/normalize-address address)]
                (if (some #(= % address*) acc)
                  acc
                  (conj acc address*))
                acc))
            []
            entries)))

(defn normalize-leaderboard-cache-record
  [raw]
  (when (map? raw)
    (let [saved-at-ms (parse-saved-at-ms (:saved-at-ms raw))
          raw-rows (:rows raw)]
      (when (and (some? saved-at-ms)
                 (sequential? raw-rows))
        {:id (or (normalization/non-blank-text (:id raw))
                 leaderboard-cache-id)
         :version (or (parse-saved-at-ms (:version raw))
                      leaderboard-cache-version)
         :saved-at-ms saved-at-ms
         :rows (normalize-leaderboard-cache-rows raw-rows)
         :excluded-addresses (normalize-excluded-addresses
                              (:excluded-addresses raw))}))))

(defn build-leaderboard-cache-record
  ([payload]
   (build-leaderboard-cache-record payload {}))
  ([{:keys [rows excluded-addresses]} {:keys [now-ms-fn]
                                       :or {now-ms-fn platform/now-ms}}]
   {:id leaderboard-cache-id
    :version leaderboard-cache-version
    :saved-at-ms (now-ms-fn)
    :rows (normalize-leaderboard-cache-rows rows)
    :excluded-addresses (normalize-excluded-addresses excluded-addresses)}))

(defn fresh-leaderboard-snapshot?
  ([saved-at-ms]
   (fresh-leaderboard-snapshot? saved-at-ms {}))
  ([saved-at-ms {:keys [now-ms-fn ttl-ms]
                 :or {now-ms-fn platform/now-ms
                      ttl-ms leaderboard-cache-ttl-ms}}]
   (when-let [saved-at-ms* (parse-saved-at-ms saved-at-ms)]
     (<= 0
         (- (now-ms-fn) saved-at-ms*)
         ttl-ms))))

(defn load-leaderboard-cache-record!
  ([] (load-leaderboard-cache-record! {}))
  ([{:keys [load-indexed-db-fn]
     :or {load-indexed-db-fn (fn []
                               (indexed-db/get-json! indexed-db/leaderboard-cache-store
                                                     leaderboard-cache-id))}}]
   (-> (->promise (load-indexed-db-fn))
       (.then normalize-leaderboard-cache-record)
       (.catch (fn [error]
                 (js/console.warn "Failed to load leaderboard cache from IndexedDB:" error)
                 nil)))))

(defn persist-leaderboard-cache-record!
  ([payload]
   (persist-leaderboard-cache-record! payload {}))
  ([payload {:keys [now-ms-fn
                    persist-indexed-db-fn]
             :or {now-ms-fn platform/now-ms
                  persist-indexed-db-fn (fn [record]
                                          (indexed-db/put-json! indexed-db/leaderboard-cache-store
                                                                leaderboard-cache-id
                                                                record))}}]
   (let [record (build-leaderboard-cache-record payload
                                                {:now-ms-fn now-ms-fn})]
     (-> (->promise (persist-indexed-db-fn record))
         (.catch (fn [error]
                   (js/console.warn "Failed to persist leaderboard cache to IndexedDB:" error)
                   false))))))
