(ns hyperopen.security.affiliate-consent-persistence-edge-cases-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.runtime.effect-adapters.attribution :as runtime]
            [hyperopen.service.fixtures :as fixtures]))

(deftest consent-revocation-wins-when-storage-persistence-fails-test
  (async done
   (let [tenant (-> fixtures/default-tenant-raw
                   (assoc-in [:features :affiliate] true)
                   (assoc-in [:affiliate :status] :enabled)
                   (assoc-in [:affiliate :event-endpoint] "https://events.example/collect"))
        event {:event/id "evt-pending"
               :event/type :trade-submit-requested
               :tenant/id (:tenant/id tenant)
               :affiliate/id (get-in tenant [:affiliate :id])
               :venue/id (get-in tenant [:venue :id])
               :session/id "session"
               :wallet/address-hash "sha256-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
               :occurred-at-ms 1700000000000
               :market "BTC"
               :outcome :submitted}
        store (atom {:tenant/override tenant
                     :wallet {:address fixtures/wallet-address}
                     :attribution {:affiliate-consent? true
                                   :queue [{:event event
                                            :delivery/status :pending
                                            :delivery/attempt-count 1}]}})
        retry (atom nil)
        fetch-count (atom 0)
        deps {:fetch-fn (fn [& _]
                          (swap! fetch-count inc)
                          (js/Promise.reject (js/Error. "offline")))
              :local-storage-get (constantly nil)
              :local-storage-set! (fn [& _] (throw (js/Error. "unavailable")))
              :now-ms-fn (constantly 1700000000000)
              :random-value-fn (constantly 0.25)
              :schedule-retry! (fn [retry-fn _delay-ms]
                                 (reset! retry retry-fn))
              :affiliate-consent? runtime/affiliate-consent?}]
    (runtime/record-attribution-event!
     deps store :trade-submit-requested {:market "BTC" :outcome :submitted})
    (js/setTimeout
     (fn []
       (let [result (runtime/set-affiliate-consent-with-deps! deps store false)]
         (is (false? result))
         (is (false? (get-in @store [:attribution :affiliate-consent?])))
         (is (empty? (get-in @store [:attribution :queue])))
         (is (fn? @retry))
         (@retry)
         (js/setTimeout
          (fn []
            (try
              (is (= 1 @fetch-count))
              (finally
                (done))))
          0)))
     0))))
