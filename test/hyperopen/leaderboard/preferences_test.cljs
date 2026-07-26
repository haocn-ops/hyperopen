(ns hyperopen.leaderboard.preferences-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.leaderboard.preferences :as preferences]
            [hyperopen.test-support.async :as async-support]))

(deftest normalize-leaderboard-preferences-record-normalizes-supported-fields-test
  (is (= {:id "leaderboard-ui-preferences:v1"
          :version 1
          :saved-at-ms 42
          :timeframe :all-time
          :sort {:column :volume
                 :direction :asc}
          :page-size 25}
         (preferences/normalize-leaderboard-preferences-record
          {:saved-at-ms "42"
           :timeframe "all time"
           :sort {:column "volume"
                  :direction "asc"}
           :page-size "25"})))
  (is (= {:id "leaderboard-ui-preferences:v1"
          :version 1
          :saved-at-ms 0
          :timeframe :month
          :sort {:column :pnl
                 :direction :desc}
          :page-size 10}
         (preferences/normalize-leaderboard-preferences-record
          {:timeframe :unsupported
           :sort {:column :unsupported
                  :direction :sideways}
           :page-size 999}))))

(deftest persist-and-load-leaderboard-preferences-roundtrip-test
  (async done
    (let [persisted-record (atom nil)]
      (-> (preferences/persist-leaderboard-preferences!
           {:leaderboard-ui {:timeframe :all-time
                             :sort {:column :volume
                                    :direction :desc}
                             :page-size 25}}
           {:now-ms-fn (fn [] 1700000000000)
            :persist-indexed-db-fn (fn [record]
                                     (reset! persisted-record record)
                                     true)})
          (.then (fn [persisted?]
                   (is (true? persisted?))
                   (-> (preferences/load-leaderboard-preferences!
                        {:load-indexed-db-fn (fn []
                                              @persisted-record)})
                       (.then (fn [record]
                                (is (= {:id "leaderboard-ui-preferences:v1"
                                        :version 1
                                        :saved-at-ms 1700000000000
                                        :timeframe :all-time
                                        :sort {:column :volume
                                               :direction :desc}
                                        :page-size 25}
                                       record))
                                (done)))
                       (.catch (async-support/unexpected-error done)))))
          (.catch (async-support/unexpected-error done))))))

(deftest restore-leaderboard-preferences-applies-loaded-values-when-state-is-unchanged-test
  (async done
    (let [store (atom {:leaderboard-ui {:timeframe :month
                                        :sort {:column :pnl
                                               :direction :desc}
                                        :page-size 10}})]
      (-> (preferences/restore-leaderboard-preferences!
           store
           {:load-preferences-fn (fn []
                                   {:timeframe :all-time
                                    :sort {:column :volume
                                           :direction :desc}
                                    :page-size 25})})
          (.then (fn [_]
                   (is (= {:timeframe :all-time
                           :sort {:column :volume
                                  :direction :desc}
                           :page-size 25}
                          (select-keys (get @store :leaderboard-ui)
                                       [:timeframe :sort :page-size])))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest restore-leaderboard-preferences-does-not-overwrite-local-changes-after-load-starts-test
  (async done
    (let [store (atom {:leaderboard-ui {:timeframe :month
                                        :sort {:column :pnl
                                               :direction :desc}
                                        :page-size 10}})
          resolve-load (atom nil)]
      (-> (preferences/restore-leaderboard-preferences!
           store
           {:load-preferences-fn (fn []
                                   (js/Promise.
                                    (fn [resolve _reject]
                                      (reset! resolve-load resolve))))})
          (.then (fn [_]
                   (is (= {:timeframe :week
                           :sort {:column :pnl
                                  :direction :desc}
                           :page-size 10}
                          (select-keys (get @store :leaderboard-ui)
                                       [:timeframe :sort :page-size])))
                   (done)))
          (.catch (async-support/unexpected-error done)))
      (swap! store assoc-in [:leaderboard-ui :timeframe] :week)
      (@resolve-load {:timeframe :all-time
                      :sort {:column :volume
                             :direction :desc}
                      :page-size 25}))))
