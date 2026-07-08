(ns hyperopen.runtime.effect-adapters.portfolio-optimizer-history-cache-restore-test
  "Restore-funnel half of the history-bundle stale-while-revalidate cache: the
  draft restore effect hydrates the wallet's persisted bundle in parallel with
  the draft record, so assumption cards/readiness settle from last session's
  data while the normal background reload refreshes it. (Own namespace:
  portfolio-optimizer-scenarios-test sits at its size cap.)"
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer-scenarios :as scenario-effects]
            [hyperopen.test-support.async :as async-support]))

(def ^:private address
  "0x1111111111111111111111111111111111111111")

(def ^:private cached-record
  {:version 1
   :address address
   :saved-at-ms 1700000000000
   :history-data {:api-v2-history
                  {:status :ok
                   :series-by-instrument
                   {"perp:BTC" {:instrument-id "hl:perp:BTC"
                                :points [{:time-ms 1000 :close 100}]}}}
                  :warnings []
                  :loaded-at-ms 1700000000000}})

(defn- restore!
  [store {:keys [record load-draft!]}]
  (scenario-effects/restore-portfolio-optimizer-draft-effect
   {:load-draft! (or load-draft! (fn [_address] (js/Promise.resolve nil)))
    :load-history-cache! (fn [_address] (js/Promise.resolve record))
    :now-ms (constantly 1700000001000)
    :dispatch! (fn [_store _ctx _actions] nil)}
   store
   "/portfolio/optimize/new"))

(deftest restore-effect-hydrates-cached-history-bundle-test
  (async done
    (let [store (atom {:wallet {:address address}})]
      (-> (restore! store {:record cached-record})
          (.then (fn [_]
                   (let [history-data (get-in @store
                                              [:portfolio :optimizer :history-data])]
                     (is (= #{"perp:BTC"}
                            (set (keys (get-in history-data
                                               [:api-v2-history
                                                :series-by-instrument]))))
                         "The cached bundle hydrates alongside the draft restore.")
                     (is (true? (:restored-from-cache? history-data)))
                     (is (nil? (get-in @store
                                       [:portfolio :optimizer :history-load-state]))
                         "Hydration never fakes a load-state - the revalidate stamps its own."))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest restore-effect-ignores-unusable-cache-records-test
  (async done
    (let [store (atom {:wallet {:address address}})]
      (-> (restore! store {:record (assoc cached-record
                                          :address "0x2222222222222222222222222222222222222222")})
          (.then (fn [_]
                   (is (nil? (get-in @store [:portfolio :optimizer :history-data]))
                       "Another wallet's record never hydrates.")
                   (done)))
          (.catch (async-support/unexpected-error done))))))
