(ns hyperopen.websocket.acl.hyperliquid-test
  (:require [hyperopen.runtime.validation :as validation]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.schema.contracts]
            [hyperopen.websocket.acl.hyperliquid :as acl]
            [hyperopen.websocket.domain.model :as model]))

(deftest parse-raw-envelope-success-test
  (let [result (acl/parse-raw-envelope {:raw "{\"channel\":\"trades\",\"data\":[{\"coin\":\"BTC\"}]}"
                                        :socket-id 42
                                        :source :hyperliquid/ws
                                        :now-ms (constantly 1234567890)
                                        :topic->tier (fn [topic]
                                                       (if (= topic "trades")
                                                         :market
                                                         :lossless))})]
    (testing "ACL maps provider JSON into a domain envelope"
      (is (contains? result :ok))
      (is (model/domain-message-envelope? (:ok result)))
      (is (= "trades" (get-in result [:ok :topic])))
      (is (= :market (get-in result [:ok :tier])))
      (is (= 42 (get-in result [:ok :socket-id]))))))

(deftest parse-raw-envelope-market-fast-path-defers-full-conversion-test
  (with-redefs [validation/validation-enabled? (constantly false)]
    (let [result (acl/parse-raw-envelope {:raw "{\"channel\":\"trades\",\"seq\":2,\"data\":[{\"coin\":\"BTC\"}]}"
                                          :socket-id 5
                                          :now-ms (constantly 200)
                                          :topic->tier (constantly :market)})
          envelope (:ok result)
          payload (:payload envelope)
          hydrated (acl/hydrate-envelope envelope)]
      (is (contains? result :ok))
      (is (= :market (:tier envelope)))
      (is (= "trades" (:channel payload)))
      (is (= "BTC" (:coin payload)))
      (is (nil? (:seq payload)))
      (is (acl/deferred-market-payload? payload))
      (is (not (acl/deferred-market-payload? (:payload hydrated))))
      (is (= 2 (get-in hydrated [:payload :seq])))
      (is (= "BTC" (get-in hydrated [:payload :data 0 :coin]))))))

(deftest parse-raw-envelope-lossless-path-stays-eager-when-validation-disabled-test
  (with-redefs [validation/validation-enabled? (constantly false)]
    (let [result (acl/parse-raw-envelope {:raw "{\"channel\":\"openOrders\",\"seq\":7,\"data\":{\"user\":\"0xabc\"}}"
                                          :socket-id 3
                                          :now-ms (constantly 50)
                                          :topic->tier (constantly :lossless)})
          payload (get-in result [:ok :payload])]
      (is (contains? result :ok))
      (is (= :lossless (get-in result [:ok :tier])))
      (is (= 7 (:seq payload)))
      (is (= "0xabc" (get-in payload [:data :user])))
      (is (not (acl/deferred-market-payload? payload))))))

(deftest parse-raw-envelope-default-source-and-validation-enabled-branches-test
  (let [assert-calls (atom [])]
    (with-redefs [validation/validation-enabled? (constantly true)
                  validation/assert-provider-message! (fn [provider-message context]
                                                       (swap! assert-calls conj [provider-message context]))]
      (let [result (acl/parse-raw-envelope {:raw "{\"channel\":\"trades\",\"data\":[]}"
                                            :socket-id 7
                                            :now-ms (constantly 111)
                                            :topic->tier (constantly :market)})]
        (is (= 1 (count @assert-calls)))
        (is (= :ws-acl/parse-raw-envelope
               (get-in @assert-calls [0 1 :boundary])))
        (is (= :hyperliquid/ws (get-in result [:ok :source])))
        (is (= "trades" (get-in result [:ok :topic])))))))

(deftest parse-raw-envelope-channel-shape-errors-with-validation-disabled-test
  (with-redefs [validation/validation-enabled? (constantly false)]
    (doseq [raw ["{\"data\":[]}"
                 "{\"channel\":42,\"data\":[]}"
                 "{\"channel\":null,\"data\":[]}"]]
      (let [result (acl/parse-raw-envelope {:raw raw
                                            :socket-id 0
                                            :now-ms (constantly 0)
                                            :topic->tier (constantly :lossless)})]
        (is (contains? result :error))
        (is (not (contains? result :ok)))))))

(deftest parse-raw-envelope-invalid-json-error-test
  (let [result (acl/parse-raw-envelope {:raw "{invalid-json"
                                        :socket-id 0
                                        :now-ms (constantly 0)
                                        :topic->tier (constantly :lossless)})]
    (testing "Invalid provider payload returns structured error result"
      (is (contains? result :error))
      (is (not (contains? result :ok))))))
