(ns hyperopen.websocket.candles-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.websocket.candles :as candles]
            [hyperopen.websocket.client :as ws-client]))

(defn- reset-candle-state! []
  (reset! candles/candle-state
          {:subscriptions #{}
           :owners-by-sub {}
           :sub-by-owner {}}))

(deftest sync-candle-subscription-switches-active-chart-target-test
  (let [messages (atom [])]
    (reset-candle-state!)
    (with-redefs [ws-client/send-message! (fn [payload]
                                            (swap! messages conj payload)
                                            true)]
      (candles/sync-candle-subscription! "BTC" :1m)
      (candles/sync-candle-subscription! "BTC" :5m)
      (candles/clear-owner-subscription!))
    (is (= [{:method "subscribe"
             :subscription {:type "candle"
                            :coin "BTC"
                            :interval "1m"}}
            {:method "unsubscribe"
             :subscription {:type "candle"
                            :coin "BTC"
                            :interval "1m"}}
            {:method "subscribe"
             :subscription {:type "candle"
                            :coin "BTC"
                            :interval "5m"}}
            {:method "unsubscribe"
             :subscription {:type "candle"
                            :coin "BTC"
                            :interval "5m"}}]
           @messages))
    (is (= #{} (candles/get-subscriptions)))))

(deftest sync-candle-subscription-shares-wire-subscription-across-owners-test
  (let [messages (atom [])]
    (reset-candle-state!)
    (with-redefs [ws-client/send-message! (fn [payload]
                                            (swap! messages conj payload)
                                            true)]
      (candles/sync-candle-subscription! "ETH" :15m :owner-a)
      (candles/sync-candle-subscription! "ETH" "15m" :owner-b)
      (candles/clear-owner-subscription! :owner-a)
      (candles/clear-owner-subscription! :owner-b))
    (is (= [{:method "subscribe"
             :subscription {:type "candle"
                            :coin "ETH"
                            :interval "15m"}}
            {:method "unsubscribe"
             :subscription {:type "candle"
                            :coin "ETH"
                            :interval "15m"}}]
           @messages))))

(deftest sync-candle-subscription-does-not-emit-duplicate-wire-traffic-for-noop-resubscribe-test
  (let [messages (atom [])]
    (reset-candle-state!)
    (with-redefs [ws-client/send-message! (fn [payload]
                                            (swap! messages conj payload)
                                            true)]
      (candles/sync-candle-subscription! "BTC" :1m :owner-a)
      (candles/sync-candle-subscription! "BTC" "1m" :owner-a)
      (candles/clear-owner-subscription! :owner-a))
    (is (= [{:method "subscribe"
             :subscription {:type "candle"
                            :coin "BTC"
                            :interval "1m"}}
            {:method "unsubscribe"
             :subscription {:type "candle"
                            :coin "BTC"
                            :interval "1m"}}]
           @messages))))

(deftest sync-candle-subscription-keeps-shared-subscription-until-last-owner-leaves-test
  (let [messages (atom [])]
    (reset-candle-state!)
    (with-redefs [ws-client/send-message! (fn [payload]
                                            (swap! messages conj payload)
                                            true)]
      (candles/sync-candle-subscription! "ETH" :15m :owner-a)
      (candles/sync-candle-subscription! "ETH" :15m :owner-b)
      (candles/sync-candle-subscription! "ETH" :1h :owner-a)
      (candles/clear-owner-subscription! :owner-b)
      (candles/clear-owner-subscription! :owner-a))
    (is (= [{:method "subscribe"
             :subscription {:type "candle"
                            :coin "ETH"
                            :interval "15m"}}
            {:method "subscribe"
             :subscription {:type "candle"
                            :coin "ETH"
                            :interval "1h"}}
            {:method "unsubscribe"
             :subscription {:type "candle"
                            :coin "ETH"
                            :interval "15m"}}
            {:method "unsubscribe"
             :subscription {:type "candle"
                            :coin "ETH"
                            :interval "1h"}}]
           @messages))))

(deftest candle-handler-upserts-rows-by-coin-interval-and-time-test
  (let [store (atom {:candles {"BTC" {:1m [{:t 1000
                                             :o 10
                                             :h 12
                                             :l 9
                                             :c 11
                                             :v 1}]}}})
        handler (candles/create-candles-handler store)]
    (handler {:channel "candle"
              :data [{:s "BTC"
                      :i "1m"
                      :t 1000
                      :o "10"
                      :h "13"
                      :l "9"
                      :c "12"
                      :v "2"}
                     {:s "BTC"
                      :i "1m"
                      :t 1060
                      :o "12"
                      :h "14"
                      :l "11"
                      :c "13"
                      :v "1.5"}]})
    (is (= [{:t 1000
             :o 10
             :h 13
             :l 9
             :c 12
             :v 2}
            {:t 1060
             :o 12
             :h 14
             :l 11
             :c 13
             :v 1.5}]
           (get-in @store [:candles "BTC" :1m])))))

(deftest candle-handler-preserves-map-shaped-entry-with-candles-key-test
  (let [store (atom {:candles {"SOL" {:5m {:meta :keep
                                             :candles [{:t 2000
                                                        :o 20
                                                        :h 21
                                                        :l 19
                                                        :c 20
                                                        :v 3}]}}}})
        handler (candles/create-candles-handler store)]
    (handler {:channel "candle"
              :data [{:s "SOL"
                      :i "5m"
                      :t 2300
                      :o 20
                      :h 22
                      :l 19
                      :c 21
                      :v 2}]})
    (let [entry (get-in @store [:candles "SOL" :5m])]
      (is (= :keep (:meta entry)))
      (is (= [{:t 2000
               :o 20
               :h 21
               :l 19
               :c 20
               :v 3}
              {:t 2300
               :o 20
               :h 22
               :l 19
               :c 21
               :v 2}]
             (:candles entry))))))

(deftest candle-handler-normalizes-single-map-payloads-with-payload-level-metadata-test
  (let [store (atom {:candles {}})
        handler (candles/create-candles-handler store)]
    (handler {:channel "candle"
              :coin "BTC"
              :interval "1h"
              :data {:time "3000"
                     :open "10"
                     :high "12"
                     :low "9"
                     :close "11"
                     :volume "1.5"
                     :T "3600"
                     :n "7"}})
    (is (= [{:t 3000
             :o 10
             :h 12
             :l 9
             :c 11
             :v 1.5
             :T 3600
             :n 7}]
           (get-in @store [:candles "BTC" :1h])))))

(deftest candle-handler-preserves-rows-and-data-entry-shapes-and-clears-errors-test
  (let [store (atom {:candles {"BTC" {:1m {:meta :rows
                                           :error "rows-error"
                                           :error-category :unexpected
                                           :rows [{:t 1000
                                                   :o 10
                                                   :h 11
                                                   :l 9
                                                   :c 10}]}
                                     :5m {:meta :data
                                          :error "data-error"
                                          :error-category :network
                                          :data [{:t 2000
                                                  :o 20
                                                  :h 21
                                                  :l 19
                                                  :c 20}]}}}})
        handler (candles/create-candles-handler store)]
    (handler {:channel "candle"
              :data [{:coin "BTC"
                      :interval "1m"
                      :time "1060"
                      :open "10"
                      :high "12"
                      :low "9"
                      :close "11"}
                     {:coin "BTC"
                      :interval "5m"
                      :time "2300"
                      :open "20"
                      :high "22"
                      :low "19"
                      :close "21"}]})
    (let [rows-entry (get-in @store [:candles "BTC" :1m])
          data-entry (get-in @store [:candles "BTC" :5m])]
      (is (= :rows (:meta rows-entry)))
      (is (nil? (:error rows-entry)))
      (is (nil? (:error-category rows-entry)))
      (is (= [{:t 1000
               :o 10
               :h 11
               :l 9
               :c 10}
              {:t 1060
               :o 10
               :h 12
               :l 9
               :c 11}]
             (:rows rows-entry)))
      (is (= :data (:meta data-entry)))
      (is (nil? (:error data-entry)))
      (is (nil? (:error-category data-entry)))
      (is (= [{:t 2000
               :o 20
               :h 21
               :l 19
               :c 20}
              {:t 2300
               :o 20
               :h 22
               :l 19
               :c 21}]
             (:data data-entry))))))
