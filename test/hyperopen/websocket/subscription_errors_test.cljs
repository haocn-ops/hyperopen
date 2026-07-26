(ns hyperopen.websocket.subscription-errors-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.websocket.subscription-errors :as subscription-errors]))

(def ^:private address
  "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

(defn- make-handler
  [published logs]
  (subscription-errors/create-error-handler
   {:publish-control! (fn [msg] (swap! published conj msg))
    :now-ms-fn (constantly 12345)
    :log-fn (fn [& args] (swap! logs conj (vec args)))}))

(deftest error-handler-publishes-subscription-rejected-for-schema-errors-test
  (let [published (atom [])
        logs (atom [])
        handle! (make-handler published logs)]
    (handle! {:channel "error"
              :data (str "Error parsing JSON into valid websocket request: "
                         "{\"method\":\"subscribe\",\"subscription\":{\"type\":\"webData2\",\"user\":\"" address "\"}}")})
    (is (= [{:msg/type :evt/subscription-rejected
             :ts 12345
             :subscription {:type "webData2" :user address}}]
           @published))
    (is (= 1 (count @logs)))))

(deftest error-handler-only-logs-benign-and-unknown-errors-test
  (let [published (atom [])
        logs (atom [])
        handle! (make-handler published logs)]
    (handle! {:channel "error"
              :data (str "Already subscribed: {\"type\":\"clearinghouseState\",\"dex\":\"xyz\",\"user\":\"" address "\"}")})
    (handle! {:channel "error"
              :data "Rate limited"})
    (is (empty? @published))
    (is (= 2 (count @logs)))))

(deftest error-handler-ignores-other-channels-test
  (let [published (atom [])
        logs (atom [])
        handle! (make-handler published logs)]
    (handle! {:channel "subscriptionResponse"
              :data {:method "subscribe"}})
    (is (empty? @published))
    (is (empty? @logs))))
