(ns hyperopen.runtime.effect-adapters.websocket-health-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.platform :as platform]
            [hyperopen.runtime.effect-adapters.websocket :as ws-adapters]
            [hyperopen.runtime.state :as runtime-state]
            [hyperopen.telemetry :as telemetry]
            [hyperopen.websocket.client :as ws-client]
            [hyperopen.websocket.diagnostics-effects :as diagnostics-effects]
            [hyperopen.websocket.diagnostics-runtime :as diagnostics-runtime]
            [hyperopen.websocket.health-projection :as health-projection]
            [hyperopen.websocket.health-runtime :as health-runtime]
            [hyperopen.websocket.market-projection-runtime :as market-projection-runtime]))

(deftest sync-websocket-health-sources-market-projection-flush-events-from-telemetry-ring-test
  (let [store (atom {})
        captured-health (atom nil)
        ring-entry {:seq 42
                    :event :websocket/market-projection-flush
                    :at-ms 777
                    :store-id "emit-store"
                    :pending-count 3
                    :overwrite-count 1
                    :flush-duration-ms 9
                    :queue-wait-ms 4
                    :flush-count 12
                    :max-pending-depth 8
                    :p95-flush-duration-ms 11
                    :queued-total 30
                    :overwrite-total 5
                    :ignored "not-copied"}
        expected-entry (select-keys ring-entry
                                    [:seq
                                     :event
                                     :at-ms
                                     :store-id
                                     :pending-count
                                     :overwrite-count
                                     :flush-duration-ms
                                     :queue-wait-ms
                                     :flush-count
                                     :max-pending-depth
                                     :p95-flush-duration-ms
                                     :queued-total
                                     :overwrite-total])]
    (with-redefs [telemetry/events (fn []
                                     (throw (js/Error. "global telemetry log should not be scanned")))
                  telemetry/market-projection-flush-events (fn []
                                                            (throw (js/Error. "raw flush events should not be remapped")))
                  telemetry/market-projection-flush-diagnostics-events (constantly [expected-entry])
                  market-projection-runtime/market-projection-telemetry-snapshot
                  (constantly {:stores [{:store-id "emit-store"
                                         :pending-count 0
                                         :frame-scheduled? false}]})
                  ws-client/get-health-snapshot
                  (constantly {:generated-at-ms 1000
                               :transport {:state :connected
                                           :freshness :live}})
                  health-runtime/sync-websocket-health!
                  (fn [{:keys [get-health-snapshot]}]
                    (reset! captured-health (get-health-snapshot)))]
      (ws-adapters/sync-websocket-health-with-runtime! nil store)
      (let [diagnostics (get @captured-health :market-projection)]
        (is (= 1 (count (:stores diagnostics))))
        (is (= [expected-entry] (:flush-events diagnostics)))
        (is (= telemetry/market-projection-flush-event-limit
               (:flush-event-limit diagnostics)))
        (is (= 1 (:flush-event-count diagnostics)))
        (is (= 42 (:latest-flush-event-seq diagnostics)))
        (is (= 777 (:latest-flush-at-ms diagnostics)))))))

(deftest append-diagnostics-event-adapter-passes-runtime-limit-to-projection-test
  (let [store (atom {})
        calls (atom [])]
    (with-redefs [health-projection/append-diagnostics-event
                  (fn [state event at-ms details limit]
                    (swap! calls conj [state event at-ms details limit])
                    (assoc state :last-event [event at-ms details limit]))]
      (ws-adapters/append-diagnostics-event! store :health/check 111 {:detail true}))
    (let [[[state event at-ms details limit]] @calls]
      (is (= {} state))
      (is (= :health/check event))
      (is (= 111 at-ms))
      (is (= {:detail true} details))
      (is (= runtime-state/diagnostics-timeline-limit limit))
      (is (= [:health/check 111 {:detail true} runtime-state/diagnostics-timeline-limit]
             (:last-event @store))))))

(deftest sync-websocket-health-delegates-to-runtime-aware-helper-test
  (let [store (atom {})
        calls (atom [])]
    (with-redefs [ws-adapters/sync-websocket-health-with-runtime!
                  (fn [runtime store* & {:keys [force? projected-fingerprint]}]
                    (swap! calls conj {:runtime runtime
                                       :store store*
                                       :force? force?
                                       :projected-fingerprint projected-fingerprint})
                    :sync-result)]
      (is (= :sync-result
             (ws-adapters/sync-websocket-health! store :force? true :projected-fingerprint "fp"))))
    (is (= [{:runtime nil
             :store store
             :force? true
             :projected-fingerprint "fp"}]
           @calls))))

(deftest refresh-websocket-health-and-factory-bind-runtime-and-force-flag-test
  (let [store (atom {})
        runtime (atom {})
        calls (atom [])]
    (with-redefs [ws-adapters/sync-websocket-health-with-runtime!
                  (fn [runtime* store* & {:keys [force?]}]
                    (swap! calls conj {:runtime runtime*
                                       :store store*
                                       :force? force?})
                    :refresh-result)]
      (is (= :refresh-result
             (ws-adapters/refresh-websocket-health nil store)))
      (is (= :refresh-result
             (ws-adapters/refresh-websocket-health runtime :ctx store)))
      (is (= :refresh-result
             ((ws-adapters/make-refresh-websocket-health runtime) :ctx store))))
    (is (= [{:runtime nil :store store :force? true}
            {:runtime runtime :store store :force? true}
            {:runtime runtime :store store :force? true}]
           @calls))))

(deftest ws-reset-subscriptions-uses-default-and-custom-diagnostics-options-test
  (let [store (atom {})
        calls (atom [])]
    (with-redefs [diagnostics-runtime/ws-reset-subscriptions!
                  (fn [deps]
                    (swap! calls conj deps)
                    :reset-result)]
      (is (= :reset-result
             (ws-adapters/ws-reset-subscriptions nil store {})))
      (is (= :reset-result
             (ws-adapters/ws-reset-subscriptions nil store {:group :user :source :auto}))))
    (let [[default-call custom-call] @calls]
      (is (= store (:store default-call)))
      (is (= :all (:group default-call)))
      (is (= :manual (:source default-call)))
      (is (identical? ws-client/get-health-snapshot
                      (:get-health-snapshot default-call)))
      (is (= (health-runtime/effective-now-ms 123)
             ((:effective-now-ms default-call) 123)))
      (is (= runtime-state/reset-subscriptions-cooldown-ms
             (:reset-subscriptions-cooldown-ms default-call)))
      (is (identical? ws-client/send-message! (:send-message! default-call)))
      (is (identical? ws-adapters/append-diagnostics-event!
                      (:append-diagnostics-event! default-call)))

      (is (= :user (:group custom-call)))
      (is (= :auto (:source custom-call))))))

(deftest diagnostics-effect-adapters-wire-platform-and-copy-status-seams-test
  (let [store (atom {})
        confirm-call (atom nil)
        copy-call (atom nil)]
    (with-redefs [diagnostics-effects/confirm-ws-diagnostics-reveal!
                  (fn [deps]
                    (reset! confirm-call deps)
                    :confirm-result)
                  diagnostics-effects/copy-websocket-diagnostics!
                  (fn [deps]
                    (reset! copy-call deps)
                    ((:set-copy-status! deps) store :copied)
                    :copy-result)]
      (is (= :confirm-result
             (ws-adapters/confirm-ws-diagnostics-reveal nil store)))
      (is (= :copy-result
             (ws-adapters/copy-websocket-diagnostics nil store))))
    (is (= store (:store @confirm-call)))
    (is (identical? platform/confirm! (:confirm-fn @confirm-call)))
    (is (= store (:store @copy-call)))
    (is (= runtime-state/app-version (:app-version @copy-call)))
    (is (identical? telemetry/log! (:log-fn @copy-call)))
    (is (= :copied (get-in @store [:websocket-ui :copy-status])))))
