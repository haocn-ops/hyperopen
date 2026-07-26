(ns hyperopen.productization.edge-cases-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.productization.test-support :as support]
            [hyperopen.service.attribution :as attribution]
            [hyperopen.service.fixtures :as fixtures]
            [hyperopen.service.tenant-config :as tenant-config]))

(deftest malformed-tenant-config-falls-back-atomically-test
  (let [default (tenant-config/normalize-tenant-config tenant-config/default-tenant-raw)
        malformed (tenant-config/normalize-tenant-config fixtures/malformed-tenant-raw)]
    (is (= default malformed))
    (is (tenant-config/valid-tenant-config? malformed))
    (is (= malformed
           (tenant-config/normalize-tenant-config fixtures/malformed-tenant-raw)))))

(deftest nested-secret-shaped-tenant-values-are-rejected-test
  (let [normalized (tenant-config/normalize-tenant-config fixtures/secret-bearing-tenant-raw)
        serialized (tenant-config/canonical-serialize normalized)]
    (is (not (tenant-config/contains-secret? normalized)))
    (is (not (re-find #"sk_live_do_not_store|deadbeef|alpha beta gamma|token-do-not-store"
                      serialized)))
    (is (not (support/contains-secret-shaped-value? normalized)))))

(deftest unknown-theme-and-feature-values-do-not-enable-behavior-test
  (let [normalized (tenant-config/normalize-tenant-config
                    (assoc fixtures/default-tenant-raw
                           :theme/id :future-theme
                           :features {:terminal true
                                      :unknown-route true}))]
    (is (= "dark" (:theme/id normalized)))
    (is (not (contains? (:features normalized) :unknown-route)))
    (is (= #{:terminal :analytics :affiliate}
           (set (keys (:features normalized)))))))

(deftest attribution-idempotency-and-canonical-serialization-are-stable-test
  (let [context fixtures/attribution-context
        event-a (attribution/build-attribution-event
                 context :wallet-connected {:outcome :observed})
        event-b (attribution/build-attribution-event
                 (into {} (reverse (seq context)))
                 :wallet-connected
                 {:outcome :observed})]
    (is (= (:event/id event-a) (:event/id event-b)))
    (is (= (attribution/idempotency-key event-a)
           (attribution/idempotency-key event-b)))
    (is (= (attribution/canonical-serialize event-a)
           (attribution/canonical-serialize event-b)))
    (is (not= (attribution/idempotency-key event-a)
              (attribution/idempotency-key
               (assoc event-a :session/id "different-session"))))))

(deftest canonical-codec-preserves-key-type-and-namespace-test
  (let [keyword-encoded (attribution/canonical-serialize {:tenant/id "keyword"})
        string-encoded (attribution/canonical-serialize {"tenant/id" "string"})
        mixed-encoded (attribution/canonical-serialize
                       {:tenant/id "keyword"
                        "tenant/id" "string"})]
    (is (not= keyword-encoded string-encoded))
    (is (re-find #":tenant/id" keyword-encoded))
    (is (re-find #"\"tenant/id\"" string-encoded))
    (is (re-find #"keyword" mixed-encoded))
    (is (re-find #"string" mixed-encoded))))

(deftest provider-settlement-requires-complete-verified-matching-evidence-test
  (let [context {:tenant/id "hyperopen-default"
                 :affiliate/id "hyperopen-official"
                 :venue/id :hyperliquid}
        valid-result {:outcome :settled
                      :provider-event-id "provider-settlement-1"
                      :occurred-at-ms 1700000001000
                      :settled-at-ms 1700000002000
                      :tenant/id "hyperopen-default"
                      :affiliate/id "hyperopen-official"
                      :venue/id :hyperliquid
                      :provider/evidence {:verified? true
                                          :verification-id "verified-fixture-1"
                                          :response-digest "digest-fixture-1"}}
        invalid-results [(dissoc valid-result :provider-event-id)
                         (assoc valid-result :occurred-at-ms "not-a-timestamp")
                         (assoc valid-result :settled-at-ms 1700000000000)
                         (assoc valid-result :venue/id :wrong-venue)
                         (dissoc valid-result :provider/evidence)
                         (-> valid-result
                             (dissoc :provider/evidence)
                             (assoc :authenticated? true))]]
    (is (= :settled
           (:outcome (attribution/normalize-provider-result context valid-result))))
    (doseq [result invalid-results]
      (is (not= :settled
                (:outcome (attribution/normalize-provider-result context result)))))))

(deftest verification-status-only-cannot-settle-a-provider-result-test
  (let [context {:tenant/id "hyperopen-default"
                 :affiliate/id "hyperopen-official"
                 :venue/id :hyperliquid}
        status-only {:outcome :settled
                     :provider-event-id "provider-status-only"
                     :occurred-at-ms 1700000001000
                     :settled-at-ms 1700000002000
                     :tenant/id "hyperopen-default"
                     :affiliate/id "hyperopen-official"
                     :venue/id :hyperliquid
                     :verification/status :verified}
        authenticated-only (assoc status-only :authenticated? true)]
    (is (not= :settled
              (:outcome (attribution/normalize-provider-result context status-only))))
    (is (not= :settled
              (:outcome (attribution/normalize-provider-result context authenticated-only))))))

(deftest local-volume-never-creates-rebate-settlement-test
  (let [result (attribution/normalize-provider-result
                fixtures/attribution-context
                {:outcome :accepted
                 :volume 999999999999
                 :fees 999999999
                 :provider-event-id nil})]
    (is (not= :settled (:outcome result)))
    (is (nil? (:rebate-amount result)))
    (is (nil? (:settled-at-ms result)))))

(deftest attribution-outage-is-independent-from-venue-order-result-test
  (let [order-result {:status :accepted :order-id "fixture-order-1"}
        attribution-result (attribution/normalize-send-result
                            {:status :network-error})]
    (is (= order-result
           (support/merge-optional-attribution-result
            order-result attribution-result)))
    (is (= :unavailable (:outcome attribution-result)))
    (is (nil? (:rebate-amount attribution-result)))))
