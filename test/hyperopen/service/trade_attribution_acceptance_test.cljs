(ns hyperopen.service.trade-attribution-acceptance-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.service.attribution :as attribution]
            [hyperopen.service.fixtures :as fixtures]
            [hyperopen.service.tenant-config :as tenant-config]))

(def event-types
  [:tenant-loaded
   :affiliate-attribution-seen
   :wallet-connected
   :trade-submit-requested
   :trade-submit-result])

(deftest attribution-events-have-stable-redacted-identities-and-ids-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        context (attribution/build-attribution-context tenant fixtures/attribution-context)]
    (doseq [event-type event-types]
      (let [attrs {:market "BTC"
                   :outcome :observed
                   :provider-event-id "provider-fixture-1"}
            event-a (attribution/build-attribution-event context event-type attrs)
            event-b (attribution/build-attribution-event
                     (into {} (reverse (seq context))) event-type attrs)]
        (is (= event-a event-b))
        (is (string? (:event/id event-a)))
        (is (= (attribution/idempotency-key event-a)
               (attribution/idempotency-key event-b)))
        (is (= :observed (:outcome event-a)))
        (is (not= fixtures/wallet-address (:wallet/address event-a)))
        (is (string? (:wallet/address-hash event-a)))
        (is (not (attribution/contains-secret? event-a)))))))

(deftest attribution-redaction-omits-secrets-and-local-settlement-claims-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        context (attribution/build-attribution-context tenant fixtures/attribution-context)
        event (attribution/build-attribution-event
               context
               :trade-submit-result
               {:outcome :accepted
                :volume 1000000000
                :fees 500000
                :rebate-amount 999999
                :private-key "0xdeadbeef"
                :raw-signature "signed-secret"})
        safe (attribution/redact-attribution-event event)]
    (is (not (contains? safe :private-key)))
    (is (not (contains? safe :raw-signature)))
    (is (not (contains? safe :rebate-amount)))
    (is (not (contains? safe :settled-at-ms)))
    (is (not (attribution/contains-secret? safe)))
    (is (not= :settled (:outcome safe)))
    (is (= :accepted (:outcome safe)))))

(deftest provider-confirmation-is-required-before-settlement-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        context (attribution/build-attribution-context tenant fixtures/attribution-context)
        observed (attribution/normalize-provider-result
                  context
                  {:outcome :accepted
                   :rebate-amount 12.34})
        confirmed (attribution/normalize-provider-result
                   context
                   {:outcome :settled
                    :provider-event-id "provider-fixture-2"
                    :occurred-at-ms 1700000001000
                    :settled-at-ms 1700000002000
                    :tenant/id "hyperopen-default"
                    :affiliate/id "hyperopen-official"
                    :venue/id :hyperliquid
                    :provider/evidence {:verified? true
                                        :verification-id "verification-fixture-2"
                                        :response-digest "digest-fixture-2"}})]
    (is (not= :settled (:outcome observed)))
    (is (nil? (:rebate-amount observed)))
    (is (= :settled (:outcome confirmed)))
    (is (= "provider-fixture-2" (:provider-event-id confirmed)))))

(deftest verified-provider-settlement-retains-only-authoritative-public-fields-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        context (attribution/build-attribution-context tenant fixtures/attribution-context)
        accepted (attribution/normalize-provider-result
                  context
                  {:outcome :accepted
                   :rebate-amount 12.5
                   :settled-at-ms 1700000002000})
        settled (attribution/normalize-provider-result
                 context
                 {:outcome :settled
                  :provider-event-id "provider-public-settlement"
                  :occurred-at-ms 1700000001000
                  :settled-at-ms 1700000002000
                  :tenant/id "hyperopen-default"
                  :affiliate/id "hyperopen-official"
                  :venue/id :hyperliquid
                  :rebate-amount 12.5
                  :provider/evidence {:verified? true
                                      :verification-id "verification-public-settlement"
                                      :adapter/provenance "approved-provider-relay"}
                  :private-key "must-not-normalize"
                  :raw-signature "must-not-normalize"})]
    (is (= :accepted (:outcome accepted)))
    (is (nil? (:settlement/verified? accepted)))
    (is (nil? (:rebate-amount accepted)))
    (is (nil? (:settled-at-ms accepted)))
    (is (= :settled (:outcome settled)))
    (is (true? (:settlement/verified? settled)))
    (is (= "provider-public-settlement" (:provider-event-id settled)))
    (is (= 12.5 (:rebate-amount settled)))
    (is (not (contains? settled :private-key)))
    (is (not (contains? settled :raw-signature)))))

(deftest event-attrs-cannot-override-authoritative-context-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        context (attribution/build-attribution-context tenant fixtures/attribution-context)
        expected-wallet-hash (:wallet/address-hash context)
        event (attribution/build-attribution-event
               context
               :wallet-connected
               {:tenant/id "attacker-tenant"
                :affiliate/id "attacker-affiliate"
                :venue/id :attacker-venue
                :event/type :trade-submit-result
                :wallet/address-hash "attacker-wallet"
                :wallet/address fixtures/wallet-address
                :outcome :not-a-contract-outcome})]
    (is (= "hyperopen-default" (:tenant/id event)))
    (is (= "hyperopen-official" (:affiliate/id event)))
    (is (= :hyperliquid (:venue/id event)))
    (is (= :wallet-connected (:event/type event)))
    (is (= expected-wallet-hash (:wallet/address-hash event)))
    (is (= :unknown (:outcome event)))
    (is (not (contains? event :wallet/address)))))

(deftest nested-and-string-key-secrets-are-removed-by-event-whitelist-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        context (attribution/build-attribution-context tenant fixtures/attribution-context)
        event (attribution/build-attribution-event
               context
               :trade-submit-requested
               {:outcome :submitted
                "api-secret" "sk_live_nested_leak"
                "metadata" {"nested" {"private-key" "0xfeedface"
                                        "raw-signature" "signed-secret"}}
                :provider/raw-response {:access-token "token-leak"}
                :market "BTC"})
        serialized (attribution/canonical-serialize event)]
    (is (= :submitted (:outcome event)))
    (is (= "BTC" (:market event)))
    (is (not (contains? event "api-secret")))
    (is (not (contains? event "metadata")))
    (is (not (contains? event :provider/raw-response)))
    (is (not (re-find #"sk_live_nested_leak|feedface|signed-secret|token-leak"
                      serialized)))
    (is (not (attribution/contains-secret? event)))))

(deftest attribution-outcome-is-a-closed-contract-test
  (let [allowed #{:observed :submitted :accepted :rejected
                  :unavailable :unknown :settled}
        event (attribution/build-attribution-event
               fixtures/attribution-context
               :tenant-loaded
               {:outcome "accepted"})]
    (is (contains? allowed (:outcome event)))
    (is (= :unknown (:outcome event)))))

(deftest allowed-event-fields-still-reject-secret-shaped-values-test
  (let [event (attribution/build-attribution-event
               fixtures/attribution-context
               :affiliate-attribution-seen
               {:market "sk_live_market-value"
                :provider-event-id "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                :outcome :observed})
        serialized (attribution/canonical-serialize event)]
    (is (not (re-find #"sk_live_market-value|0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                      serialized)))
    (is (not (attribution/contains-secret? event)))))

(deftest normalized-verified-settlement-remains-settled-after-redaction-test
  (let [context {:tenant/id "hyperopen-default"
                 :affiliate/id "hyperopen-official"
                 :venue/id :hyperliquid
                 :session/id "settlement-session"
                 :wallet/address-hash "wallet-hash-fixture"}
        normalized (attribution/normalize-provider-result
                    context
                    {:outcome :settled
                     :provider-event-id "provider-settlement-2"
                     :occurred-at-ms 1700000001000
                     :settled-at-ms 1700000002000
                     :tenant/id "hyperopen-default"
                     :affiliate/id "hyperopen-official"
                     :venue/id :hyperliquid
                     :provider/evidence {:verified? true
                                         :verification-id "verified-fixture-2"
                                         :response-digest "digest-fixture-2"}})
        safe (attribution/redact-attribution-event
              (assoc normalized
                     :event/id "event-settlement-2"
                     :event/type :trade-submit-result))]
    (is (= :settled (:outcome normalized)))
    (is (= :settled (:outcome safe)))
    (is (= "provider-settlement-2" (:provider-event-id safe)))
    (is (= 1700000002000 (:settled-at-ms safe)))
    (is (not (attribution/contains-secret? safe)))))

(deftest settlement-looking-event-without-complete-evidence-is-unknown-test
  (let [forged {:event/id "event-forged-settlement"
                :event/type :trade-submit-result
                :tenant/id "hyperopen-default"
                :affiliate/id "hyperopen-official"
                :venue/id :hyperliquid
                :occurred-at-ms 1700000001000
                :outcome :settled
                :provider-event-id "fake-provider-event"
                :settlement/verified? true
                :authenticated? true
                :rebate-amount 999
                :settled-at-ms 1700000002000}
        safe (attribution/redact-attribution-event forged)]
    (is (= :unknown (:outcome safe)))
    (is (nil? (:rebate-amount safe)))
    (is (nil? (:settled-at-ms safe)))
    (is (= "fake-provider-event" (:provider-event-id safe)))))

(deftest legacy-authenticated-flag-without-provider-proof-cannot-retain-settlement-test
  (let [legacy {:event/id "event-legacy-authenticated-settlement"
                :event/type :trade-submit-result
                :tenant/id "hyperopen-default"
                :affiliate/id "hyperopen-official"
                :venue/id :hyperliquid
                :occurred-at-ms 1700000001000
                :outcome :settled
                :provider-event-id "provider-event-legacy"
                :authenticated? true
                :provider/evidence {:verified? true
                                    :verification-id "verification-legacy"}
                :rebate-amount 12.5
                :settled-at-ms 1700000002000}
        safe (attribution/redact-attribution-event legacy)]
    (is (= :unknown (:outcome safe)))
    (is (nil? (:rebate-amount safe)))
    (is (nil? (:settled-at-ms safe)))
    (is (not (contains? safe :authenticated?)))
    (is (= "provider-event-legacy" (:provider-event-id safe)))))

(deftest incomplete-attribution-context-cannot-create-a-sendable-event-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        contexts [{:session/id "missing-wallet"
                   :occurred-at-ms 1700000000000}
                  {:session/id "blank-wallet"
                   :wallet/address "   "
                   :occurred-at-ms 1700000000000}
                  {:session/id "missing-time"
                   :wallet/address fixtures/wallet-address}
                  {:session/id "nan-time"
                   :wallet/address fixtures/wallet-address
                   :occurred-at-ms js/NaN}
                  {:session/id "infinite-time"
                   :wallet/address fixtures/wallet-address
                   :occurred-at-ms js/Infinity}]]
    (doseq [raw-context contexts]
      (let [context (attribution/build-attribution-context tenant raw-context)
            event (attribution/build-attribution-event context
                                                       :wallet-connected
                                                       {:outcome :observed})]
        (is (nil? (:wallet/address-hash context)))
        (is (= :unknown (:outcome event)))
        (is (nil? (:event/id event)))))))
