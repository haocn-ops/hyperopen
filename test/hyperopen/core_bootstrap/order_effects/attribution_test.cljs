(ns hyperopen.core-bootstrap.order-effects.attribution-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.core-bootstrap.order-effects.test-support :as support]
            [hyperopen.order.effects :as order-effects]
            [hyperopen.runtime.effect-adapters.attribution :as attribution-runtime]
            [hyperopen.service.fixtures :as fixtures]))

(defn- submit-deps
  [record-attribution-event!]
  {:dispatch! (fn [_store _evt _actions] nil)
   :exchange-response-error support/test-exchange-response-error
   :record-attribution-event! record-attribution-event!
   :runtime-error-message support/test-runtime-error-message
   :show-toast! support/test-show-toast!})

(deftest order-rejection-records-request-and-result-attribution-test
  (async done
    (let [store (atom (support/base-submit-order-store {:active-asset "BTC"}))
          events (atom [])
          original-submit-order trading-api/submit-order!]
      (set! trading-api/submit-order!
            (fn [_store _address _action]
              (js/Promise.resolve {:status "err" :error "venue rejected"})))
      (order-effects/api-submit-order
       (submit-deps (fn [_store event-type attrs]
                      (swap! events conj [event-type attrs])))
       nil
       store
       {:action {:type "order" :orders [] :grouping "na"}})
      (js/setTimeout
       (fn []
         (try
           (is (= [[:trade-submit-requested {:outcome :submitted :market "BTC"}]
                   [:trade-submit-result {:outcome :rejected :market "BTC"}]]
                  @events))
           (is (= "venue rejected"
                  (get-in @store [:order-form-runtime :error])))
           (finally
             (set! trading-api/submit-order! original-submit-order)
             (done))))
       0))))

(deftest attribution-adapter-exception-does-not-block-order-result-test
  (async done
    (let [store (atom (support/base-submit-order-store {:active-asset "ETH"}))
          submit-count (atom 0)
          original-submit-order trading-api/submit-order!]
      (set! trading-api/submit-order!
            (fn [_store _address _action]
              (swap! submit-count inc)
              (js/Promise.resolve {:status "err" :error "venue rejected"})))
      (order-effects/api-submit-order
       (submit-deps (fn [& _]
                      (throw (js/Error. "attribution offline"))))
       nil
       store
       {:action {:type "order" :orders [] :grouping "na"}})
      (js/setTimeout
       (fn []
         (try
           (is (= 1 @submit-count))
           (is (false? (get-in @store [:order-form-runtime :submitting?])))
           (is (= "venue rejected"
                  (get-in @store [:order-form-runtime :error])))
           (finally
             (set! trading-api/submit-order! original-submit-order)
             (done))))
       0))))

(deftest provider-json-parse-retries-cannot-change-a-venue-rejection-test
  (async done
    (let [storage (atom {})
          attribution-calls (atom 0)
          submit-count (atom 0)
          tenant (assoc-in fixtures/default-tenant-raw
                           [:affiliate :event-endpoint]
                           "https://events.example.test/attribution")
          store (atom (merge (support/base-submit-order-store {:active-asset "BTC"})
                             {:tenant/override tenant
                              :wallet {:address fixtures/wallet-address
                                       :agent {:status :ready}}}))
          attribution-deps
          {:fetch-fn (fn [_endpoint _request]
                       (swap! attribution-calls inc)
                       (js/Promise.resolve
                        #js {:ok true
                             :json (fn []
                                     (js/Promise.reject
                                      (js/Error. "provider JSON rejected")))}))
           :local-storage-get (fn [key] (get @storage key))
           :local-storage-set! (fn [key value] (swap! storage assoc key value))
           :now-ms-fn (constantly 1700000000000)
           :random-value-fn (constantly 0.25)
           :schedule-retry! (fn [retry-fn _delay-ms] (retry-fn))}
          record! (fn [attribution-store event-type attrs]
                    (attribution-runtime/record-attribution-event!
                     attribution-deps attribution-store event-type attrs))
          original-submit-order trading-api/submit-order!]
      (set! trading-api/submit-order!
            (fn [_store _address _action]
              (swap! submit-count inc)
              (js/Promise.resolve {:status "err" :error "venue rejected"})))
      (order-effects/api-submit-order
       (submit-deps record!)
       nil
       store
       {:action {:type "order" :orders [] :grouping "na"}})
      (js/setTimeout
       (fn []
         (try
           (is (= 1 @submit-count))
           (is (= (* 2 attribution-runtime/max-delivery-attempts)
                  @attribution-calls))
           (is (false? (get-in @store [:order-form-runtime :submitting?])))
           (is (= "venue rejected"
                  (get-in @store [:order-form-runtime :error])))
           (is (= #{:unavailable}
                  (set (map :delivery/status
                            (get-in @store [:attribution :queue])))))
           (finally
             (set! trading-api/submit-order! original-submit-order)
             (done))))
       0))))
