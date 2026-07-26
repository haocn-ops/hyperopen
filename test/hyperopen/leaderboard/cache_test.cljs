(ns hyperopen.leaderboard.cache-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.leaderboard.cache :as cache]
            [hyperopen.test-support.async :as async-support]))

(deftest normalize-leaderboard-cache-record-normalizes-supported-fields-test
  (is (= {:id "leaderboard-cache:v1"
          :version 1
          :saved-at-ms 42
          :rows [{:eth-address "0xabc"
                  :account-value 15
                  :display-name "Desk"
                  :prize 0
                  :window-performances {:day {:pnl 0 :roi 0 :volume 0}
                                        :week {:pnl 0 :roi 0 :volume 0}
                                        :month {:pnl 2 :roi 0.1 :volume 9}
                                        :all-time {:pnl 0 :roi 0 :volume 0}}}]
          :excluded-addresses ["0xdead" "0xbeef"]}
         (cache/normalize-leaderboard-cache-record
          {:saved-at-ms "42"
           :rows [{:eth-address "0xABC"
                   :account-value "15"
                   :display-name "Desk"
                   :window-performances {:month {:pnl "2"
                                                 :roi "0.1"
                                                 :vlm "9"}}}]
           :excluded-addresses ["0xDEAD" "" "0xdead" "0xbeef"]})))
  (is (nil? (cache/normalize-leaderboard-cache-record
             {:saved-at-ms "nope"
              :rows []}))))

(deftest fresh-leaderboard-snapshot-respects-one-hour-ttl-test
  (is (true? (cache/fresh-leaderboard-snapshot?
              1700000000000
              {:now-ms-fn (fn [] (+ 1700000000000 (* 59 60 1000)))})))
  (is (false? (cache/fresh-leaderboard-snapshot?
               1700000000000
               {:now-ms-fn (fn [] (+ 1700000000000 (* 61 60 1000)))}))))

(deftest persist-and-load-leaderboard-cache-roundtrip-test
  (async done
    (let [stored-record* (atom nil)]
      (-> (cache/persist-leaderboard-cache-record!
           {:rows [{:eth-address "0x111"
                    :account-value 25
                    :display-name "Alpha"
                    :window-performances {:month {:pnl 5
                                                  :roi 0.2
                                                  :volume 7}}}]
            :excluded-addresses #{"0x222"}}
           {:now-ms-fn (fn [] 1700000000000)
            :persist-indexed-db-fn (fn [record]
                                     (reset! stored-record* record)
                                     true)})
          (.then (fn [persisted?]
                   (is (true? persisted?))
                   (-> (cache/load-leaderboard-cache-record!
                        {:load-indexed-db-fn (fn []
                                              @stored-record*)})
                       (.then (fn [record]
                                (is (= {:id "leaderboard-cache:v1"
                                        :version 1
                                        :saved-at-ms 1700000000000
                                        :rows [{:eth-address "0x111"
                                                :account-value 25
                                                :display-name "Alpha"
                                                :prize 0
                                                :window-performances {:day {:pnl 0 :roi 0 :volume 0}
                                                                      :week {:pnl 0 :roi 0 :volume 0}
                                                                      :month {:pnl 5 :roi 0.2 :volume 7}
                                                                      :all-time {:pnl 0 :roi 0 :volume 0}}}]
                                        :excluded-addresses ["0x222"]}
                                       record))
                                (done)))
                       (.catch (async-support/unexpected-error done)))))
          (.catch (async-support/unexpected-error done))))))
