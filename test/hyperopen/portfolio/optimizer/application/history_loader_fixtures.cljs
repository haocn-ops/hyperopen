(ns hyperopen.portfolio.optimizer.application.history-loader-fixtures
  (:require [hyperopen.portfolio.optimizer.application.history-loader :as history-loader]))

(defn near?
  ([expected actual]
   (near? expected actual 0.0000001))
  ([expected actual tolerance]
   (< (js/Math.abs (- expected actual)) tolerance)))

(def day-ms
  (* 24 60 60 1000))

(defn day-start-ms
  [day]
  (.getTime (js/Date. (str day "T00:00:00.000Z"))))

(defn summary-from-points
  [points]
  {:accountValueHistory (mapv (fn [[time-ms account-value _pnl-value]]
                                [time-ms account-value])
                              points)
   :pnlHistory (mapv (fn [[time-ms _account-value pnl-value]]
                       [time-ms pnl-value])
                     points)})

(defn candle-rows
  [time-and-close-pairs]
  (mapv (fn [[time-ms close]]
          {:time time-ms
           :close (str close)})
        time-and-close-pairs))

(defn vault-instrument-id
  [vault-address]
  (str "vault:" vault-address))

(defn align-market-and-vault-history
  [vault-address portfolio candle-history]
  (history-loader/align-history-inputs
   {:universe [{:instrument-id "perp:BTC"
                :market-type :perp
                :coin "BTC"}
               {:instrument-id (vault-instrument-id vault-address)
                :market-type :vault
                :coin (vault-instrument-id vault-address)
                :vault-address vault-address}]
    :candle-history-by-coin {"BTC" (candle-rows candle-history)}
    :vault-details-by-address {vault-address
                               {:portfolio portfolio}}
    :as-of-ms (+ (apply max (map first candle-history)) day-ms)
    :stale-after-ms (* 2 day-ms)}))
