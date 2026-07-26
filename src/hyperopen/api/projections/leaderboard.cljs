(ns hyperopen.api.projections.leaderboard
  (:require [hyperopen.api.errors :as api-errors]))

(defn- normalized-error
  [err]
  (api-errors/normalize-error err))

(defn begin-leaderboard-load
  [state]
  (-> state
      (assoc-in [:leaderboard :loading?] true)
      (assoc-in [:leaderboard :error] nil)
      (assoc-in [:leaderboard :error-category] nil)))

(defn apply-leaderboard-success
  [state {:keys [rows excluded-addresses]}]
  (-> state
      (assoc-in [:leaderboard :rows]
                (if (sequential? rows) (vec rows) []))
      (assoc-in [:leaderboard :excluded-addresses]
                (if (set? excluded-addresses)
                  excluded-addresses
                  (set (or excluded-addresses []))))
      (assoc-in [:leaderboard :loading?] false)
      (assoc-in [:leaderboard :error] nil)
      (assoc-in [:leaderboard :error-category] nil)
      (assoc-in [:leaderboard :loaded-at-ms] (.now js/Date))))

(defn apply-leaderboard-cache-hydration
  [state cache-record]
  (let [rows (if (sequential? (:rows cache-record))
               (vec (:rows cache-record))
               [])
        excluded-addresses (or (:excluded-addresses cache-record) [])
        saved-at-ms (:saved-at-ms cache-record)]
    (-> state
        (assoc-in [:leaderboard :rows] rows)
        (assoc-in [:leaderboard :excluded-addresses]
                  (if (set? excluded-addresses)
                    excluded-addresses
                    (set excluded-addresses)))
        (assoc-in [:leaderboard :loading?] false)
        (assoc-in [:leaderboard :error] nil)
        (assoc-in [:leaderboard :error-category] nil)
        (assoc-in [:leaderboard :loaded-at-ms]
                  (when (number? saved-at-ms)
                    saved-at-ms)))))

(defn apply-leaderboard-error
  [state err]
  (let [{:keys [message category]} (normalized-error err)]
    (-> state
        (assoc-in [:leaderboard :loading?] false)
        (assoc-in [:leaderboard :error] message)
        (assoc-in [:leaderboard :error-category] category))))
