(ns hyperopen.websocket.acl.subscription-errors-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.websocket.acl.subscription-errors :as sub-errors]))

(def ^:private address
  "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

(deftest classify-error-text-schema-rejection-test
  (testing "The provider's full-request parse error classifies as rejected with the subscription"
    (let [text (str "Error parsing JSON into valid websocket request: "
                    "{\"method\":\"subscribe\",\"subscription\":{\"type\":\"webData2\",\"user\":\"" address "\"}}")]
      (is (= {:kind :rejected
              :subscription {:type "webData2" :user address}}
             (sub-errors/classify-error-text text)))))
  (testing "A parse error echoing an unsubscribe never downgrades a stream"
    (let [text (str "Error parsing JSON into valid websocket request: "
                    "{\"method\":\"unsubscribe\",\"subscription\":{\"type\":\"webData2\",\"user\":\"" address "\"}}")]
      (is (= :unknown (:kind (sub-errors/classify-error-text text))))))
  (testing "A parse error without a recoverable subscription is unknown"
    (is (= :unknown
           (:kind (sub-errors/classify-error-text
                   "Error parsing JSON into valid websocket request: not-json"))))))

(deftest classify-error-text-benign-duplicates-test
  (testing "Already subscribed echoes classify as benign with the subscription"
    (let [text (str "Already subscribed: "
                    "{\"type\":\"clearinghouseState\",\"dex\":\"xyz\",\"user\":\"" address "\"}")]
      (is (= {:kind :benign
              :subscription {:type "clearinghouseState" :dex "xyz" :user address}}
             (sub-errors/classify-error-text text)))))
  (testing "Already unsubscribed echoes classify as benign"
    (is (= :benign
           (:kind (sub-errors/classify-error-text
                   (str "Already unsubscribed: {\"type\":\"openOrders\",\"user\":\"" address "\"}")))))))

(deftest classify-error-text-unknown-shapes-test
  (is (= {:kind :unknown :subscription nil}
         (sub-errors/classify-error-text "Rate limited")))
  (is (= {:kind :unknown :subscription nil}
         (sub-errors/classify-error-text nil)))
  (is (= {:kind :unknown :subscription nil}
         (sub-errors/classify-error-text 42))))
