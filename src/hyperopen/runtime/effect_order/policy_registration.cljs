(ns hyperopen.runtime.effect-order.policy-registration
  (:require [clojure.set :as set]
            [hyperopen.schema.runtime-registration-catalog :as registration-catalog]))

(defn- sorted-vec
  [xs]
  (->> xs sort vec))

(defn validate-policy-by-action-id!
  [policy-by-action-id]
  (let [policy-action-ids (set (keys policy-by-action-id))
        registered-action-ids (registration-catalog/action-ids)
        required-action-ids (registration-catalog/effect-order-policy-required-action-ids)
        stale-policy-action-ids (set/difference policy-action-ids registered-action-ids)
        missing-policy-action-ids (set/difference required-action-ids policy-action-ids)
        unmarked-policy-action-ids (set/difference policy-action-ids required-action-ids)]
    (when (or (seq stale-policy-action-ids)
              (seq missing-policy-action-ids)
              (seq unmarked-policy-action-ids))
      (throw (js/Error.
              (str "Effect-order policy registration drift detected. "
                   "stale-policy=" (pr-str (sorted-vec stale-policy-action-ids))
                   " missing-policy=" (pr-str (sorted-vec missing-policy-action-ids))
                   " unmarked-policy=" (pr-str (sorted-vec unmarked-policy-action-ids))))))
    policy-by-action-id))
