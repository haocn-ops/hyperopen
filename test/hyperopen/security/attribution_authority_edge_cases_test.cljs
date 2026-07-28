(ns hyperopen.security.attribution-authority-edge-cases-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.effect-adapters.attribution :as runtime]
            [hyperopen.service.attribution :as attribution]))

(defn- event [event-id market]
  {:event/id event-id
   :event/type :trade-submit-requested
   :tenant/id "tenant"
   :affiliate/id "affiliate"
   :venue/id :hyperliquid
   :session/id "session"
   :wallet/address-hash "sha256-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
   :occurred-at-ms 1700000000000
   :market market
   :outcome :submitted})

(deftest event-identifiers-ignore-map-insertion-order-test
  (let [left {:tenant/id "tenant" :venue/id :hyperliquid :outcome :submitted}
        right (into {} (reverse (seq left)))
        changed (assoc left :outcome :rejected)]
    (is (= (attribution/idempotency-key left)
           (attribution/idempotency-key right)))
    (is (not= (attribution/idempotency-key left)
              (attribution/idempotency-key changed)))
    (is (re-matches #"[0-9a-f]{64}" (attribution/sha256-hex "changed-input")))))

(deftest legacy-event-identifiers-remain-restorable-test
  (let [legacy (event "evt-legacy-compatible" "BTC")
        stored (js/JSON.stringify
                #js [#js {"event" #js {"event/id" (:event/id legacy)
                                        "event/type" "trade-submit-requested"
                                        "tenant/id" (:tenant/id legacy)
                                        "affiliate/id" (:affiliate/id legacy)
                                        "venue/id" "hyperliquid"
                                        "session/id" (:session/id legacy)
                                        "wallet/address-hash" (:wallet/address-hash legacy)
                                        "occurred-at-ms" (:occurred-at-ms legacy)
                                        "market" (:market legacy)
                                        "outcome" "submitted"}
                          "delivery/status" "pending"
                          "delivery/attempt-count" 1}])
        store (atom {})
        restored (runtime/restore-queue! store (constantly stored))]
    (is (= "evt-legacy-compatible" (get-in restored [0 :event :event/id])))))
