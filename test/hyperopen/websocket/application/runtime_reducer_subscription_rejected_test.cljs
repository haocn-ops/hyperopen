(ns hyperopen.websocket.application.runtime-reducer-subscription-rejected-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.websocket.application.runtime-reducer :as reducer]
            [hyperopen.websocket.domain.model :as model]
            [hyperopen.websocket.health :as health]))

(def ^:private test-config
  {:max-queue-size 3
   :watchdog-interval-ms 10000
   :health-tick-interval-ms 1000
   :transport-live-threshold-ms 10000
   :stale-threshold-ms {"l2Book" 5000
                        "trades" 10000}
   :stale-visible-ms 45000
   :stale-hidden-ms 180000
   :market-coalesce-window-ms 5})

(defn- step [state msg]
  (reducer/step {:calculate-retry-delay-ms (fn [_ _ _ _] 500)} state msg))

(deftest subscription-rejected-downgrades-stream-but-keeps-desired-replay-test
  (let [base (-> (reducer/initial-runtime-state test-config)
                 (assoc :status :connected
                        :active-socket-id 1
                        :online? true)
                 (assoc-in [:transport :connected-at-ms] 10))
        sub {:type "clearinghouseState"
             :user "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
             :dex ""}
        sub-key (model/subscription-key sub)
        subscribed (:state (step base
                                 (model/make-runtime-msg :cmd/send-message
                                                         100
                                                         {:data {:method "subscribe"
                                                                 :subscription sub}})))
        rejected (:state (step subscribed
                               (model/make-runtime-msg :evt/subscription-rejected
                                                       200
                                                       {:subscription sub})))
        rejected-stream (get-in rejected [:streams sub-key])]
    (testing "Rejection downgrades the stream's health bookkeeping"
      (is (false? (:subscribed? rejected-stream)))
      (is (= :rejected (:status rejected-stream)))
      (is (= 200 (:rejected-at-ms rejected-stream)))
      (is (= :rejected
             (health/derive-stream-status 1000 rejected-stream))))
    (testing "Desired subscriptions keep the entry so reconnect replay self-heals"
      (is (= {sub-key sub} (:desired-subscriptions rejected))))
    (testing "A fresh subscribe intent re-arms the stream and clears the rejection stamp"
      (let [resubscribed (:state (step rejected
                                       (model/make-runtime-msg :cmd/send-message
                                                               300
                                                               {:data {:method "subscribe"
                                                                       :subscription sub}})))
            stream (get-in resubscribed [:streams sub-key])]
        (is (true? (:subscribed? stream)))
        (is (nil? (:rejected-at-ms stream)))
        (is (= :idle (:status stream)))))
    (testing "Rejection for an untracked subscription leaves state unchanged"
      (let [untouched (:state (step subscribed
                                    (model/make-runtime-msg :evt/subscription-rejected
                                                            210
                                                            {:subscription {:type "webData2"
                                                                            :user "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}})))]
        (is (= (dissoc (get-in subscribed [:streams sub-key]) :status)
               (dissoc (get-in untouched [:streams sub-key]) :status)))))))
