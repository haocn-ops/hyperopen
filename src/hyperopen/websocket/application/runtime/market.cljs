(ns hyperopen.websocket.application.runtime.market
  (:require [hyperopen.websocket.domain.model :as model]))

(defn maybe-clear-market-flush
  [state effects]
  (let [state* (-> state
                   (assoc :market-flush-active? false)
                   (assoc-in [:market-coalesce :pending] {}))]
    (if (:market-flush-active? state)
      [state*
       (conj effects (model/make-runtime-effect :fx/timer-clear-timeout {:timer-key :market-flush}))]
      [state* effects])))

(defn flush-market-pending
  [state effects]
  (let [pending (vals (get-in state [:market-coalesce :pending] {}))
        sorted (sort-by :ts pending)
        effects* (into effects
                       (map (fn [envelope]
                              (model/make-runtime-effect :fx/router-dispatch-envelope {:envelope envelope})))
                       sorted)
        dispatched (count sorted)]
    [(-> state
         (assoc-in [:market-coalesce :pending] {})
         (assoc :market-flush-active? false)
         (update-in [:metrics :market-dispatched] (fnil + 0) dispatched))
     effects*]))
