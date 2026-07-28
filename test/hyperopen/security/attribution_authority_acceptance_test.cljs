(ns hyperopen.security.attribution-authority-acceptance-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.runtime.effect-adapters.attribution :as runtime]
            [hyperopen.service.attribution :as attribution]
            [hyperopen.service.fixtures :as fixtures]
            [hyperopen.service.tenant-config :as tenant-config]))

(deftest canonical-affiliate-authority-and-sha256-vectors-test
  (is (= "https://events.example.com/collect?campaign=one"
         (tenant-config/normalize-affiliate-event-endpoint
          "HTTPS://EVENTS.Example.COM:443/a/../collect?campaign=one")))
  (is (= "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
         (attribution/sha256-hex "")))
  (is (= "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
         (attribution/sha256-hex "abc")))
  (is (= "670d9743542cae3ea7ebe36af56bd53648b0a1126162e78d81a32934a711302e"
         (attribution/sha256-hex "你好"))))

(deftest new-attribution-identifiers-use-prefixed-sha256-test
  (let [context {:tenant/id "tenant"
                 :affiliate/id "affiliate"
                 :venue/id :hyperliquid
                 :session/id "session"
                 :wallet/address "0x1234567890abcdef1234567890abcdef12345678"
                 :occurred-at-ms 1700000000000}
        event (attribution/build-attribution-event
               context :trade-submit-requested {:market "BTC" :outcome :submitted})]
    (is (re-matches #"sha256-[0-9a-f]{64}" (:event/id event)))
    (is (re-matches #"sha256-[0-9a-f]{64}" (:wallet/address-hash event)))
    (is (re-matches #"sha256-[0-9a-f]{64}" (attribution/idempotency-key event)))))

(deftest opted-in-delivery-uses-only-the-canonical-endpoint-test
  (async done
    (let [endpoint "https://events.example.com/collect?campaign=one"
          tenant (-> fixtures/default-tenant-raw
                     (assoc-in [:features :affiliate] true)
                     (assoc-in [:affiliate :status] :enabled)
                     (assoc-in [:affiliate :event-endpoint]
                               "HTTPS://EVENTS.Example.COM:443/a/../collect?campaign=one"))
          calls (atom [])
          store (atom {:tenant/override tenant
                       :wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}})
          deps {:fetch-fn (fn [url request]
                            (swap! calls conj [url request])
                            (js/Promise.resolve
                             #js {:ok true
                                  :json (fn []
                                          (js/Promise.resolve #js {:outcome "accepted"}))}))
                :local-storage-get (constantly nil)
                :local-storage-set! (fn [& _] true)
                :now-ms-fn (constantly 1700000000000)
                :random-value-fn (constantly 0.25)
                :schedule-retry! (fn [& _] nil)
                :affiliate-consent? (constantly true)}]
      (runtime/record-attribution-event!
       deps store :trade-submit-requested {:market "BTC" :outcome :submitted})
      (js/setTimeout
       (fn []
         (try
           (is (= 1 (count @calls)))
           (is (= endpoint (ffirst @calls)))
           (let [[_ request] (first @calls)
                 payload (js->clj (js/JSON.parse (.-body request)) :keywordize-keys true)]
             (is (re-matches #"sha256-[0-9a-f]{64}" (:event/id payload)))
             (is (= (:event/id payload) (aget (.-headers request) "Idempotency-Key"))))
           (finally
             (done))))
       0))))
