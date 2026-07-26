(ns hyperopen.api.endpoints.leaderboard
  (:require [hyperopen.leaderboard.normalization :as normalization]))

(def default-leaderboard-url
  "https://stats-data.hyperliquid.xyz/Mainnet/leaderboard")

(defn normalize-window-performance
  [payload]
  (normalization/normalize-window-performance payload))

(defn normalize-window-performances
  [payload]
  (normalization/normalize-window-performances payload))

(defn normalize-leaderboard-row
  [row]
  (when (map? row)
    (when-let [eth-address (normalization/normalize-address (:ethAddress row))]
      {:eth-address eth-address
       :account-value (or (normalization/parse-optional-num (:accountValue row)) 0)
       :display-name (normalization/non-blank-text (:displayName row))
       :prize (or (normalization/parse-optional-num (:prize row)) 0)
       :window-performances (normalize-window-performances (:windowPerformances row))})))

(defn normalize-leaderboard-rows
  [payload]
  (let [rows (cond
               (map? payload) (:leaderboardRows payload)
               (sequential? payload) payload
               :else [])]
    (if (sequential? rows)
      (->> rows
           (keep normalize-leaderboard-row)
           vec)
      [])))

(defn request-leaderboard!
  ([fetch-fn opts]
   (request-leaderboard! fetch-fn default-leaderboard-url opts))
  ([fetch-fn url opts]
   (let [fetch-fn* (or fetch-fn js/fetch)
         init (clj->js (merge {:method "GET"}
                              (:fetch-opts (or opts {}))))]
     (-> (fetch-fn* url init)
         (.then (fn [response]
                  (cond
                    (or (map? response)
                        (sequential? response))
                    (normalize-leaderboard-rows response)

                    (and (some? response)
                         (false? (.-ok response)))
                    (let [status (.-status response)
                          error (js/Error. (str "Leaderboard request failed with HTTP " status))]
                      (aset error "status" status)
                      (js/Promise.reject error))

                    (fn? (some-> response .-json))
                    (-> (.json response)
                        (.then (fn [payload]
                                 (normalize-leaderboard-rows
                                  (js->clj payload :keywordize-keys true)))))

                    :else
                    (js/Promise.resolve []))))))))
