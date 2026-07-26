(ns hyperopen.funding.history-cache-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.funding.history-cache :as funding-cache]
            [hyperopen.test-support.async :as async-support]))

(deftest merge-and-trim-market-funding-history-rows-test
  (let [coin "BTC"
        now-ms 1700000000000
        rows (funding-cache/merge-market-funding-history-rows
              coin
              [{:coin coin :time 1699990000000 :fundingRate "0.0001"}
               {:coin coin :time 1699993600000 :fundingRate "0.0002"}]
              [{:coin coin :time 1699993600000 :fundingRate "0.0003"}
               {:coin coin :time (- now-ms (* 40 24 60 60 1000)) :fundingRate "0.0009"}])
        trimmed (funding-cache/trim-market-funding-history-rows
                 rows
                 now-ms
                 funding-cache/cache-retention-window-ms)]
    (is (= [1699990000000 1699993600000]
           (mapv :time-ms trimmed)))
    (is (= [0.0001 0.0003]
           (mapv :fundingRate trimmed)))))

(deftest normalize-coin-canonicalizes-case-for-default-and-dex-prefixed-markets-test
  (is (= "BTC" (funding-cache/normalize-coin "btc")))
  (is (= "ETH" (funding-cache/normalize-coin " Eth ")))
  (is (= "xyz:GOLD" (funding-cache/normalize-coin "XYZ:GOLD")))
  (is (= "xyz:GOLD" (funding-cache/normalize-coin "xyz:GOLD"))))

(deftest sync-market-funding-history-cache-cold-then-warm-test
  (async done
    (let [now-ms (atom 1700007200000)
          cache-state (atom nil)
          request-calls (atom [])
          request-market-funding-history!
          (fn [_coin opts]
            (swap! request-calls conj opts)
            (js/Promise.resolve [{:coin "BTC"
                                  :time 1700000000000
                                  :fundingRate "0.0001"
                                  :premium "0.0002"}
                                 {:coin "BTC"
                                  :time 1700003600000
                                  :fundingRate "0.0002"
                                  :premium "0.0003"}]))]
      (-> (funding-cache/sync-market-funding-history-cache!
           "BTC"
           {:now-ms-fn (fn [] @now-ms)
            :load-cache-fn (fn [_coin _opts] @cache-state)
            :persist-cache-fn (fn [_coin snapshot]
                                (reset! cache-state snapshot))
            :request-market-funding-history! request-market-funding-history!
            :min-refresh-interval-ms (* 5 60 1000)})
          (.then (fn [first-result]
                   (is (= :network (:source first-result)))
                   (is (= 2 (:fetched-count first-result)))
                   (is (= 2 (count (:rows first-result))))
                   (is (= 1 (count @request-calls)))
                   (-> (funding-cache/sync-market-funding-history-cache!
                        "BTC"
                        {:now-ms-fn (fn [] @now-ms)
                         :load-cache-fn (fn [_coin _opts] @cache-state)
                         :persist-cache-fn (fn [_coin snapshot]
                                             (reset! cache-state snapshot))
                         :request-market-funding-history! request-market-funding-history!
                         :min-refresh-interval-ms (* 5 60 1000)})
                       (.then (fn [second-result]
                                (is (= :cache (:source second-result)))
                                (is (= :recent-sync (:reason second-result)))
                                (is (= 1 (count @request-calls)))
                                (done)))
                       (.catch (async-support/unexpected-error done)))))
          (.catch (async-support/unexpected-error done))))))

(deftest sync-market-funding-history-cache-fetches-delta-only-test
  (async done
    (let [now-ms (atom 1700007200000)
          cache-state (atom nil)
          request-calls (atom [])
          request-market-funding-history!
          (fn [_coin opts]
            (swap! request-calls conj opts)
            (let [start-ms (:start-time-ms opts)]
              (if (= 1700003600001 start-ms)
                (js/Promise.resolve [{:coin "BTC"
                                      :time 1700007200000
                                      :fundingRate "0.0003"
                                      :premium "0.0004"}])
                (js/Promise.resolve [{:coin "BTC"
                                      :time 1700000000000
                                      :fundingRate "0.0001"
                                      :premium "0.0002"}
                                     {:coin "BTC"
                                      :time 1700003600000
                                      :fundingRate "0.0002"
                                      :premium "0.0003"}]))))]
      (-> (funding-cache/sync-market-funding-history-cache!
           "BTC"
           {:now-ms-fn (fn [] @now-ms)
            :load-cache-fn (fn [_coin _opts] @cache-state)
            :persist-cache-fn (fn [_coin snapshot]
                                (reset! cache-state snapshot))
            :request-market-funding-history! request-market-funding-history!
            :min-refresh-interval-ms 0})
          (.then (fn [_]
                   (reset! now-ms 1700007200000)
                   (-> (funding-cache/sync-market-funding-history-cache!
                        "BTC"
                        {:now-ms-fn (fn [] @now-ms)
                         :load-cache-fn (fn [_coin _opts] @cache-state)
                         :persist-cache-fn (fn [_coin snapshot]
                                             (reset! cache-state snapshot))
                         :request-market-funding-history! request-market-funding-history!
                         :min-refresh-interval-ms 0
                         :force? true})
                       (.then (fn [result]
                                (is (= :network (:source result)))
                                (is (= 1 (:fetched-count result)))
                                (is (= [1700000000000 1700003600000 1700007200000]
                                       (mapv :time-ms (:rows result))))
                                (is (= 2 (count @request-calls)))
                                (is (= 1700003600001
                                       (:start-time-ms (second @request-calls))))
                                (done)))
                       (.catch (async-support/unexpected-error done)))))
          (.catch (async-support/unexpected-error done))))))

(deftest sync-market-funding-history-cache-empty-cache-does-not-skip-network-fetch-test
  (async done
    (let [now-ms 1700007200000
          request-calls (atom [])
          requested-coins (atom [])
          request-market-funding-history!
          (fn [coin opts]
            (swap! requested-coins conj coin)
            (swap! request-calls conj opts)
            (js/Promise.resolve [{:coin "xyz:GOLD"
                                  :time 1700007200000
                                  :fundingRate "0.0003"
                                  :premium "0.0004"}]))]
      (-> (funding-cache/sync-market-funding-history-cache!
           "XYZ:GOLD"
           {:now-ms-fn (constantly now-ms)
            :load-cache-fn (fn [_coin _opts]
                             {:version 1
                              :coin "XYZ:GOLD"
                              :rows []
                              :last-row-time-ms nil
                              :last-sync-ms now-ms})
            :persist-cache-fn (fn [_coin _snapshot] nil)
            :request-market-funding-history! request-market-funding-history!
            :min-refresh-interval-ms (* 5 60 1000)})
          (.then (fn [result]
                   (is (= :network (:source result)))
                   (is (= 1 (:fetched-count result)))
                   (is (= ["xyz:GOLD"] @requested-coins))
                   (is (= 1 (count @request-calls)))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest sync-market-funding-history-cache-supports-promise-based-cache-adapters-test
  (async done
    (let [now-ms 1700007200000
          cache-state (atom nil)
          persisted-snapshots (atom [])
          request-market-funding-history!
          (fn [_coin _opts]
            (js/Promise.resolve [{:coin "BTC"
                                  :time 1700000000000
                                  :fundingRate "0.0001"
                                  :premium "0.0002"}]))]
      (-> (funding-cache/sync-market-funding-history-cache!
           "BTC"
           {:now-ms-fn (constantly now-ms)
            :load-cache-fn (fn [_coin _opts]
                             (js/Promise.resolve @cache-state))
            :persist-cache-fn (fn [_coin snapshot]
                                (reset! cache-state snapshot)
                                (swap! persisted-snapshots conj snapshot)
                                (js/Promise.resolve true))
            :request-market-funding-history! request-market-funding-history!
            :min-refresh-interval-ms 0})
          (.then (fn [result]
                   (is (= :network (:source result)))
                   (is (= 1 (count (:rows result))))
                   (is (= 1 (count @persisted-snapshots)))
                   (is (= 1700000000000
                          (get-in (first @persisted-snapshots) [:rows 0 :time-ms])))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest normalize-market-funding-history-row-and-cache-handle-mixed-inputs-test
  (is (= {:coin "xyz:GOLD"
          :time-ms 1700000000000
          :time 1700000000000
          :funding-rate-raw 1.2E-4
          :fundingRate 1.2E-4
          :premium 0.5}
         (funding-cache/normalize-market-funding-history-row
          nil
          {:coin "XYZ:GOLD"
           :time "1700000000000.9"
           :funding-rate-raw "0.00012"
           :premium "0.5"})))
  (is (nil? (funding-cache/normalize-market-funding-history-row
             "BTC"
             {:time "1700000000000"
              :fundingRate "NaN"})))
  (let [snapshot (funding-cache/normalize-market-funding-history-cache
                  " btc "
                  {:rows [{:time 1700000000000
                           :fundingRate "0.0001"}
                          {:time 1690000000000
                           :fundingRate "0.0002"}
                          {:time 1700000500000
                           :fundingRate "0.0003"}]
                   :last-sync-ms "invalid"}
                  1700000600000
                  700000)]
    (is (= "BTC" (:coin snapshot)))
    (is (= [1700000000000 1700000500000]
           (mapv :time-ms (:rows snapshot))))
    (is (= 1700000500000 (:last-row-time-ms snapshot)))
    (is (= 0 (:last-sync-ms snapshot)))))

(deftest rows-for-window-sorts-and-filters-to-requested-range-test
  (let [rows [{:time-ms 1700000600000}
              {:time-ms "1700000000000"}
              {:time-ms "not-a-number"}
              {:time-ms 1700000300000}]
        windowed (funding-cache/rows-for-window rows 1700000600000 400000)]
    (is (= [1700000300000 1700000600000]
           (mapv :time-ms windowed)))))

(deftest load-market-funding-history-cache-prefers-newer-local-record-and-backfills-indexeddb-test
  (async done
    (let [persisted-records (atom [])]
      (-> (funding-cache/load-market-funding-history-cache
           "btc"
           {:now-ms-fn (constantly 1700007200000)
            :local-storage-get-fn
            (fn [_key]
              (js/JSON.stringify
               (clj->js
                {:saved-at-ms 1700007100000
                 :snapshot {:rows [{:coin "BTC"
                                    :time 1700000000000
                                    :fundingRate "0.0001"}]
                            :last-sync-ms 1700007000000}})))
            :load-indexed-db-fn
            (fn [_coin _now-ms _retention-window-ms]
              (js/Promise.resolve
               {:saved-at-ms 1700006000000
                :snapshot {:rows []
                           :last-sync-ms 1700006000000}}))
            :persist-indexed-db-fn
            (fn [coin record]
              (swap! persisted-records conj [coin record])
              (js/Promise.resolve true))})
          (.then (fn [snapshot]
                   (is (= "BTC" (:coin snapshot)))
                   (is (= [1700000000000]
                          (mapv :time-ms (:rows snapshot))))
                   (is (= 1700000000000 (:last-row-time-ms snapshot)))
                   (is (= 1700007000000 (:last-sync-ms snapshot)))
                   (is (= [["BTC" {:coin "BTC"
                                   :saved-at-ms 1700007100000
                                   :snapshot {:version 1
                                              :coin "BTC"
                                              :rows [{:coin "BTC"
                                                      :time-ms 1700000000000
                                                      :time 1700000000000
                                                      :funding-rate-raw 1.0E-4
                                                      :fundingRate 1.0E-4
                                                      :premium nil}]
                                              :last-row-time-ms 1700000000000
                                              :last-sync-ms 1700007000000}}]]
                          @persisted-records))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest persist-and-clear-market-funding-history-cache-fall-back-to-local-storage-test
  (async done
    (let [persisted-local (atom [])
          removed-keys (atom [])
          original-warn (.-warn js/console)
          fail (fn [err]
                 (set! (.-warn js/console) original-warn)
                 ((async-support/unexpected-error done) err))
          complete (fn []
                     (set! (.-warn js/console) original-warn)
                     (done))]
      (set! (.-warn js/console) (fn [& _args] nil))
      (-> (funding-cache/persist-market-funding-history-cache!
           "btc"
           {:rows [{:time 1700000000000
                    :fundingRate "0.0001"}]}
           {:now-ms-fn (constantly 1700007200000)
            :persist-indexed-db-fn
            (fn [_coin _record]
              (js/Promise.reject (js/Error. "idb unavailable")))
            :local-storage-set-fn
            (fn [key value]
              (swap! persisted-local conj [key (js->clj (js/JSON.parse value) :keywordize-keys true)]))})
          (.then (fn [persisted?]
                   (is (= false persisted?))
                   (is (= "market-funding-history-cache:BTC"
                          (ffirst @persisted-local)))
                   (is (= 1700000000000
                          (get-in (second (first @persisted-local))
                                  [:snapshot :rows 0 :time-ms])))
                   (-> (funding-cache/clear-market-funding-history-cache!
                        "btc"
                       {:delete-indexed-db-fn
                         (fn [_store _coin]
                           (js/Promise.resolve false))
                         :local-storage-remove-fn
                         (fn [key]
                           (swap! removed-keys conj key))})
                       (.then (fn [deleted?]
                                (is (= false deleted?))
                                (is (= ["market-funding-history-cache:BTC"]
                                       @removed-keys))
                                (complete)))
                       (.catch fail))))
          (.catch fail)))))

(deftest sync-market-funding-history-cache-covers-invalid-coin-and-up-to-date-branches-test
  (async done
    (let [persisted-snapshots (atom [])
          request-calls (atom 0)]
      (-> (funding-cache/sync-market-funding-history-cache!
           "   "
           {:now-ms-fn (constantly 1700007200000)})
          (.then (fn [invalid-result]
                   (is (= :invalid-coin (:source invalid-result)))
                   (is (= [] (:rows invalid-result)))
                   (-> (funding-cache/sync-market-funding-history-cache!
                        "BTC"
                        {:now-ms-fn (constantly 1700007200000)
                         :load-cache-fn
                         (fn [_coin _opts]
                           {:version 1
                            :coin "BTC"
                            :rows [{:coin "BTC"
                                    :time-ms 1700007200000
                                    :time 1700007200000
                                    :funding-rate-raw 1.0E-4
                                    :fundingRate 1.0E-4}]
                            :last-row-time-ms 1700007200000
                            :last-sync-ms 1700000000000})
                         :persist-cache-fn
                         (fn [_coin snapshot]
                           (swap! persisted-snapshots conj snapshot)
                           true)
                         :request-market-funding-history!
                         (fn [& _args]
                           (swap! request-calls inc)
                           (js/Promise.resolve []))
                         :min-refresh-interval-ms 0})
                       (.then (fn [result]
                                (is (= :cache (:source result)))
                                (is (= :up-to-date (:reason result)))
                                (is (= 0 @request-calls))
                                (is (= 1700007200000
                                       (get-in (first @persisted-snapshots)
                                               [:last-sync-ms])))
                                (done)))
                       (.catch (async-support/unexpected-error done)))))
          (.catch (async-support/unexpected-error done))))))
