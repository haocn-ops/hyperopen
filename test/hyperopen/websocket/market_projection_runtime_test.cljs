(ns hyperopen.websocket.market-projection-runtime-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is]]
            [hyperopen.websocket.market-projection-runtime :as market-runtime]))

(defn- apply-store-with-count
  [write-count]
  (fn [store apply-update-fn]
    (swap! write-count inc)
    (swap! store apply-update-fn)))

(defn- now-ms-seq-fn
  [values-atom]
  (fn []
    (let [values @values-atom
          next-value (first values)]
      (swap! values-atom
             (fn [current-values]
               (vec (rest current-values))))
      (if (some? next-value)
        next-value
        (throw (js/Error. "now-ms sequence exhausted"))))))

(defn- telemetry-for-store-id
  [store-id]
  (some (fn [store-summary]
          (when (= store-id (:store-id store-summary))
            store-summary))
        (:stores (market-runtime/market-projection-telemetry-snapshot))))

(defn- expected-p95
  [samples]
  (when (seq samples)
    (let [sorted (vec (sort samples))
          idx (-> (js/Math.ceil (* 0.95 (count sorted)))
                  dec
                  int)]
      (nth sorted idx))))

(deftest queue-market-projection-coalesces-multiple-keys-into-one-frame-write-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}
                       :active-assets {:contexts {}
                                       :loading true}})
          scheduled-callback (atom nil)
          schedule-count (atom 0)
          write-count (atom 0)
          apply-store! (apply-store-with-count write-count)
          schedule-animation-frame! (fn [f]
                                      (swap! schedule-count inc)
                                      (reset! scheduled-callback f)
                                      :raf-id)]
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state
                                     [:orderbooks "BTC"]
                                     {:bids [{:px "100"}]
                                      :asks [{:px "101"}]
                                      :timestamp 1}))})
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:active-asset-ctx "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (-> state
                               (assoc-in [:active-assets :contexts "BTC"] {:mark 100.5})
                               (assoc-in [:active-assets :loading] false)))})
      (is (= 1 @schedule-count))
      (is (= 0 @write-count))
      (@scheduled-callback 16)
      (is (= 1 @write-count))
      (is (= {:bids [{:px "100"}]
              :asks [{:px "101"}]
              :timestamp 1}
             (get-in @store [:orderbooks "BTC"])))
      (is (= {:mark 100.5}
             (get-in @store [:active-assets :contexts "BTC"])))
      (is (false? (get-in @store [:active-assets :loading]))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

(deftest queue-market-projection-keeps-latest-update-for-same-key-in-frame-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          scheduled-callback (atom nil)
          schedule-count (atom 0)
          write-count (atom 0)
          apply-store! (apply-store-with-count write-count)
          schedule-animation-frame! (fn [f]
                                      (swap! schedule-count inc)
                                      (reset! scheduled-callback f)
                                      :raf-id)]
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:timestamp 1 :mark 100}))})
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:timestamp 2 :mark 101}))})
      (is (= 1 @schedule-count))
      (is (= 0 @write-count))
      (@scheduled-callback 16)
      (is (= 1 @write-count))
      (is (= {:timestamp 2 :mark 101}
             (get-in @store [:orderbooks "BTC"]))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

(deftest queue-market-projection-schedules-next-frame-after-prior-flush-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          scheduled-callbacks (atom [])
          write-count (atom 0)
          apply-store! (apply-store-with-count write-count)
          schedule-animation-frame! (fn [f]
                                      (swap! scheduled-callbacks conj f)
                                      (keyword (str "raf-" (count @scheduled-callbacks))))]
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:timestamp 1}))})
      ((first @scheduled-callbacks) 16)
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "ETH"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "ETH"] {:timestamp 2}))})
      (is (= 2 (count @scheduled-callbacks)))
      ((second @scheduled-callbacks) 32)
      (is (= 2 @write-count))
      (is (= {:timestamp 1}
             (get-in @store [:orderbooks "BTC"])))
      (is (= {:timestamp 2}
             (get-in @store [:orderbooks "ETH"]))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

(deftest queue-market-projection-telemetry-tracks-pending-depth-and-overwrites-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          now-values (atom [100 101 102 110 115])
          now-ms-fn (now-ms-seq-fn now-values)
          scheduled-callback (atom nil)
          schedule-animation-frame! (fn [f]
                                      (reset! scheduled-callback f)
                                      :raf-id)
          emitted-events (atom [])
          emit-fn (fn [event payload]
                    (swap! emitted-events conj [event payload]))]
      (market-runtime/queue-market-projection!
       {:store store
        :store-id "test-store"
        :coalesce-key [:orderbook "BTC"]
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:mark 100}))
        :schedule-animation-frame! schedule-animation-frame!
        :now-ms-fn now-ms-fn
        :emit-fn emit-fn})
      (market-runtime/queue-market-projection!
       {:store store
        :store-id "test-store"
        :coalesce-key [:orderbook "BTC"]
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:mark 101}))
        :schedule-animation-frame! schedule-animation-frame!
        :now-ms-fn now-ms-fn
        :emit-fn emit-fn})
      (market-runtime/queue-market-projection!
       {:store store
        :store-id "test-store"
        :coalesce-key [:orderbook "ETH"]
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "ETH"] {:mark 200}))
        :schedule-animation-frame! schedule-animation-frame!
        :now-ms-fn now-ms-fn
        :emit-fn emit-fn})
      (let [summary-before-flush (telemetry-for-store-id "test-store")]
        (is (= 2 (:pending-count summary-before-flush)))
        (is (= 3 (:queued-total summary-before-flush)))
        (is (= 1 (:overwrite-total summary-before-flush)))
        (is (= 1 (:pending-overwrite-count summary-before-flush)))
        (is (= 2 (:max-pending-depth summary-before-flush)))
        (is (= 0 (:flush-count summary-before-flush)))
        (is (:frame-scheduled? summary-before-flush)))
      (@scheduled-callback 16)
      (let [summary-after-flush (telemetry-for-store-id "test-store")]
        (is (= 0 (:pending-count summary-after-flush)))
        (is (= 3 (:queued-total summary-after-flush)))
        (is (= 1 (:overwrite-total summary-after-flush)))
        (is (= 0 (:pending-overwrite-count summary-after-flush)))
        (is (= 1 (:flush-count summary-after-flush)))
        (is (= 5 (:last-flush-duration-ms summary-after-flush)))
        (is (= 10 (:last-queue-wait-ms summary-after-flush)))
        (is (= 5 (:p95-flush-duration-ms summary-after-flush)))
        (is (false? (:frame-scheduled? summary-after-flush))))
      (is (= 1 (count @emitted-events))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

(deftest flush-telemetry-event-payload-includes-required-fields-and-reset-overwrite-window-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          now-values (atom [10 11 12 20 27 30 36 39])
          now-ms-fn (now-ms-seq-fn now-values)
          scheduled-callbacks (atom [])
          schedule-animation-frame! (fn [f]
                                      (swap! scheduled-callbacks conj f)
                                      (keyword (str "raf-" (count @scheduled-callbacks))))
          emitted-events (atom [])
          emit-fn (fn [event payload]
                    (swap! emitted-events conj [event payload]))]
      (market-runtime/queue-market-projection!
       {:store store
        :store-id "emit-store"
        :coalesce-key [:orderbook "BTC"]
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:mark 100}))
        :schedule-animation-frame! schedule-animation-frame!
        :now-ms-fn now-ms-fn
        :emit-fn emit-fn})
      (market-runtime/queue-market-projection!
       {:store store
        :store-id "emit-store"
        :coalesce-key [:orderbook "BTC"]
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:mark 101}))
        :schedule-animation-frame! schedule-animation-frame!
        :now-ms-fn now-ms-fn
        :emit-fn emit-fn})
      (market-runtime/queue-market-projection!
       {:store store
        :store-id "emit-store"
        :coalesce-key [:orderbook "ETH"]
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "ETH"] {:mark 200}))
        :schedule-animation-frame! schedule-animation-frame!
        :now-ms-fn now-ms-fn
        :emit-fn emit-fn})
      ((first @scheduled-callbacks) 16)

      (market-runtime/queue-market-projection!
       {:store store
        :store-id "emit-store"
        :coalesce-key [:orderbook "SOL"]
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "SOL"] {:mark 300}))
        :schedule-animation-frame! schedule-animation-frame!
        :now-ms-fn now-ms-fn
        :emit-fn emit-fn})
      ((second @scheduled-callbacks) 32)

      (is (= 2 (count @emitted-events)))
      (let [[event-1 payload-1] (first @emitted-events)
            [event-2 payload-2] (second @emitted-events)]
        (is (= :websocket/market-projection-flush event-1))
        (is (= "emit-store" (:store-id payload-1)))
        (is (= 2 (:pending-count payload-1)))
        (is (= 1 (:overwrite-count payload-1)))
        (is (= 7 (:flush-duration-ms payload-1)))
        (is (= 10 (:queue-wait-ms payload-1)))
        (is (= 1 (:flush-count payload-1)))
        (is (= 2 (:max-pending-depth payload-1)))
        (is (= 7 (:p95-flush-duration-ms payload-1)))

        (is (= :websocket/market-projection-flush event-2))
        (is (= "emit-store" (:store-id payload-2)))
        (is (= 1 (:pending-count payload-2)))
        (is (= 0 (:overwrite-count payload-2)))
        (is (= 3 (:flush-duration-ms payload-2)))
        (is (= 6 (:queue-wait-ms payload-2)))
        (is (= 2 (:flush-count payload-2)))
        (is (= 2 (:max-pending-depth payload-2)))
        (is (= 7 (:p95-flush-duration-ms payload-2)))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

(deftest flush-telemetry-bounds-samples-and-computes-p95-from-window-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          durations (vec (range 1 66))
          now-values (atom (reduce (fn [timeline [idx duration]]
                                     (let [base (* 100 idx)
                                           enqueue-ms base
                                           flush-start-ms (+ base 10)
                                           flush-end-ms (+ flush-start-ms duration)]
                                       (-> timeline
                                           (conj enqueue-ms)
                                           (conj flush-start-ms)
                                           (conj flush-end-ms))))
                                   []
                                   (map-indexed vector durations)))
          now-ms-fn (now-ms-seq-fn now-values)
          scheduled-callback (atom nil)
          schedule-animation-frame! (fn [f]
                                      (reset! scheduled-callback f)
                                      :raf-id)]
      (doseq [idx (range (count durations))]
        (market-runtime/queue-market-projection!
         {:store store
          :store-id "window-store"
          :coalesce-key [:orderbook idx]
          :apply-update-fn (fn [state]
                             (assoc-in state [:orderbooks idx] {:mark idx}))
          :schedule-animation-frame! schedule-animation-frame!
          :now-ms-fn now-ms-fn
          :emit-fn (fn [_ _] nil)})
        (@scheduled-callback 16))
      (let [summary (telemetry-for-store-id "window-store")
            window-size (:flush-duration-window-size summary)
            expected-window (vec (take-last window-size durations))]
        (is (= (count durations) (:flush-count summary)))
        (is (= (count durations) (:queued-total summary)))
        (is (= 0 (:overwrite-total summary)))
        (is (= 0 (:pending-count summary)))
        (is (= (min (count durations) window-size)
               (:flush-duration-sample-count summary)))
        (is (= (min (count durations) window-size)
               (:queue-wait-sample-count summary)))
        (is (= (expected-p95 expected-window)
               (:p95-flush-duration-ms summary)))
        (is (= 10 (:p95-queue-wait-ms summary)))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

(deftest queue-market-projection-default-store-id-is-compact-and-readable-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          scheduled-callback (atom nil)
          schedule-animation-frame! (fn [f]
                                      (reset! scheduled-callback f)
                                      :raf-id)]
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:mark 101}))
        :schedule-animation-frame! schedule-animation-frame!})
      (let [stores (:stores (market-runtime/market-projection-telemetry-snapshot))
            summary (first stores)
            store-id (:store-id summary)]
        (is (= 1 (count stores)))
        (is (string? store-id))
        (is (str/starts-with? store-id "market-projection/store-"))
        (is (not (str/starts-with? store-id "#object[")))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

(deftest flush-store-updates-skips-default-store-write-for-net-noop-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          watch-count (atom 0)
          watch-key ::noop-watch
          scheduled-callback (atom nil)
          schedule-animation-frame! (fn [f]
                                      (reset! scheduled-callback f)
                                      :raf-id)]
      (add-watch store watch-key
                 (fn [_ _ _ _]
                   (swap! watch-count inc)))
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :apply-update-fn identity
        :schedule-animation-frame! schedule-animation-frame!
        :emit-fn (fn [_ _] nil)})
      (@scheduled-callback 16)
      (is (= {:orderbooks {}} @store))
      (is (= 0 @watch-count))
      (remove-watch store watch-key))
    (finally
      (market-runtime/reset-market-projection-runtime!))))
