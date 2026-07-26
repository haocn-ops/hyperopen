(ns hyperopen.websocket.application.runtime-tla-conformance-test
  (:require [cljs.test :refer-macros [async deftest is testing]]
            [hyperopen.websocket.application.runtime-reducer :as reducer]
            [hyperopen.websocket.application.runtime-tla-trace-fixtures :as fixtures]
            [hyperopen.websocket.client :as ws-client]
            [hyperopen.websocket.domain.model :as model]
            [hyperopen.websocket.flight-recorder :as flight-recorder]))

(defn- reducer-step
  [state msg]
  (reducer/step {:calculate-retry-delay-ms (fn [_ _ _ _] 500)} state msg))

(defn- replay-trace
  [trace]
  (let [recording (if (contains? trace :events)
                    trace
                    (fixtures/recording trace))]
    (flight-recorder/replay-runtime-messages {:recording recording
                                              :reducer reducer-step})))

(defn- make-fake-socket
  [sent-payloads close-events]
  (let [socket (js-obj)]
    (aset socket "readyState" 0)
    (aset socket "send"
          (fn [payload]
            (swap! sent-payloads conj payload)))
    (aset socket "close"
          (fn [& [code reason]]
            (let [code* (or code 1000)
                  reason* (or reason "")]
              (swap! close-events conj {:code code* :reason reason*})
              (aset socket "readyState" 3)
              (when-let [onclose (aget socket "onclose")]
                (onclose (js-obj "code" code*
                                 "reason" reason*
                                 "wasClean" true))))))
    socket))

(defn- recording-message-types
  [recording]
  (->> (get recording :events [])
       (filter #(= :runtime/msg (:kind %)))
       (mapv #(get-in % [:payload :msg-type]))))

(defn- finish-live-recorder-test!
  [original-config done]
  (reset! ws-client/connection-config original-config)
  (ws-client/reset-manager-state!)
  (done))

(deftest stale-socket-trace-keeps-runtime-state-observationally-stable-test
  (let [{:keys [final-state trace]} (replay-trace fixtures/stale-socket-trace)]
    (testing "Stale decoded and parse-error events leave reducer-owned buffers and parse metrics unchanged"
      (is (empty? (get-in final-state [:market-coalesce :pending])))
      (is (= 0 (get-in final-state [:metrics :market-coalesced])))
      (is (= 0 (get-in final-state [:metrics :market-dispatched])))
      (is (= 0 (get-in final-state [:metrics :ingress-parse-errors]))))
    (testing "Stale events do not emit new market-flush or dead-letter effects"
      (is (not-any? #(= :fx/timer-set-timeout %)
                    (mapcat :effect-types trace)))
      (is (not-any? #(= :fx/dead-letter %)
                    (mapcat :effect-types trace))))))

(deftest replay-order-trace-preserves-sorted-subscription-then-queue-order-test
  (let [{:keys [trace final-state]} (replay-trace fixtures/replay-order-trace)
        sent-payloads (->> trace
                           first
                           :effects
                           (filter #(= :fx/socket-send (:fx/type %)))
                           (mapv :data))]
    (testing "Socket open replays subscriptions by domain key order before queued payloads"
      (is (= fixtures/expected-replay-order sent-payloads)))
    (testing "Replay drains the queue once the active socket opens"
      (is (empty? (:queue final-state)))
      (is (= :connected (:status final-state))))))

(deftest browser-startup-recording-preserves-replay-order-from-real-flight-capture-test
  (let [{:keys [trace final-state]} (replay-trace fixtures/browser-startup-recording)
        socket-open-step (some #(when (= :evt/socket-open (:msg-type %)) %) trace)
        sent-payloads (->> (:effects socket-open-step)
                           (filter #(= :fx/socket-send (:fx/type %)))
                           (mapv :data))]
    (testing "A reduced real browser capture replays sorted desired subscriptions before queued startup commands"
      (is (= fixtures/expected-browser-startup-replay-order sent-payloads)))
    (testing "The reduced browser capture reaches the same connected and drained queue state as the model"
      (is (= :connected (:status final-state)))
      (is (empty? (:queue final-state)))
      (is (= 3 (count (:desired-subscriptions final-state)))))))

(deftest force-reconnect-trace-clears-market-pending-before-requesting-new-connect-test
  (let [{:keys [trace final-state]} (replay-trace fixtures/force-reconnect-trace)
        effect-types (mapcat :effect-types trace)]
    (testing "Force reconnect drops stale market pending data before the next socket generation"
      (is (empty? (get-in final-state [:market-coalesce :pending])))
      (is (false? (:market-flush-active? final-state))))
    (testing "Force reconnect tears down the old socket and requests a new connect"
      (is (some #{:fx/socket-detach-handlers} effect-types))
      (is (some #{:fx/socket-close} effect-types))
      (is (some #{:fx/socket-connect} effect-types)))))

(deftest seq-gap-resubscribe-trace-clears-gap-state-after-resubscribe-test
  (let [{:keys [final-state]} (replay-trace fixtures/seq-gap-resubscribe-trace)
        sub-key (model/subscription-key {:type "trades" :coin "BTC"})
        stream (get-in final-state [:streams sub-key])]
    (testing "Resubscribe clears reducer seq-gap metadata back to the modeled baseline"
      (is (nil? (:last-seq stream)))
      (is (false? (:seq-gap-detected? stream)))
      (is (= 0 (:seq-gap-count stream)))
      (is (nil? (:last-gap stream))))))

(deftest intentional-close-trace-suppresses-retry-after-active-socket-close-test
  (let [{:keys [final-state]} (replay-trace fixtures/intentional-close-trace)]
    (testing "Intentional close leaves runtime disconnected without scheduling retry"
      (is (= :disconnected (:status final-state)))
      (is (false? (:retry-timer-active? final-state)))
      (is (nil? (:next-retry-at-ms final-state))))))

(deftest recorder-derived-connect-open-trace-replays-to-a-connected-runtime-test
  (async done
    (let [created (atom [])
          sent (atom [])
          closes (atom [])
          original-config @ws-client/connection-config]
      (ws-client/reset-manager-state!)
      (swap! ws-client/connection-config assoc :flight-recorder {:enabled? true
                                                                 :capacity 64})
      (with-redefs [hyperopen.websocket.client/create-websocket
                    (fn [ws-url]
                      (let [socket (make-fake-socket sent closes)]
                        (swap! created conj {:url ws-url :socket socket})
                        socket))
                    hyperopen.websocket.client/add-event-listener! (fn [& _] nil)
                    hyperopen.websocket.client/schedule-interval! (fn [& _] :watchdog)]
        (ws-client/init-connection! "wss://example.test/ws")
        (js/setTimeout
          (fn []
            (let [socket (:socket (first @created))]
              (aset socket "readyState" 1)
              ((aget socket "onopen") (js-obj))
              (js/setTimeout
                (fn []
                  (try
                    (let [recording (ws-client/get-flight-recording)
                          replay (flight-recorder/replay-runtime-messages {:recording recording
                                                                          :reducer reducer-step})
                          msg-types (recording-message-types recording)]
                      (testing "The live recorder captures the expected connect/open message path"
                        (is (some #{:cmd/init-connection} msg-types))
                        (is (some #{:evt/socket-open} msg-types))
                        (is (pos? (:event-count recording))))
                      (testing "Replay of the recorder output converges to the modeled connected state"
                        (is (= :connected (get-in replay [:final-state :status])))
                        (is (pos? (get-in replay [:final-state :active-socket-id])))))
                    (finally
                      (finish-live-recorder-test! original-config done))))
                0)))
          0)))))

(deftest recorder-derived-reconnect-trace-replays-through-retry-and-back-to-connected-test
  (async done
    (let [created (atom [])
          sent (atom [])
          closes (atom [])
          retry-timeouts (atom [])
          original-config @ws-client/connection-config]
      (ws-client/reset-manager-state!)
      (swap! ws-client/connection-config assoc :flight-recorder {:enabled? true
                                                                 :capacity 128})
      (with-redefs [hyperopen.websocket.client/create-websocket
                    (fn [ws-url]
                      (let [socket (make-fake-socket sent closes)]
                        (swap! created conj {:url ws-url :socket socket})
                        socket))
                    hyperopen.websocket.client/add-event-listener! (fn [& _] nil)
                    hyperopen.websocket.client/schedule-interval! (fn [& _] :watchdog)
                    hyperopen.websocket.client/schedule-timeout!
                    (fn [f delay-ms]
                      (swap! retry-timeouts conj {:callback f :delay-ms delay-ms})
                      :retry-timer)
                    hyperopen.websocket.client/clear-timeout! (fn [& _] nil)
                    hyperopen.websocket.client/random-value (constantly 0.5)]
        (ws-client/init-connection! "wss://example.test/ws")
        (js/setTimeout
          (fn []
            (let [socket-1 (:socket (first @created))]
              (aset socket-1 "readyState" 1)
              ((aget socket-1 "onopen") (js-obj))
              (js/setTimeout
                (fn []
                  ((aget socket-1 "onclose") (js-obj "code" 1006
                                                     "reason" "abnormal"
                                                     "wasClean" false))
                  (js/setTimeout
                    (fn []
                      ((:callback (first @retry-timeouts)))
                      (js/setTimeout
                        (fn []
                          (let [socket-2 (:socket (second @created))]
                            (aset socket-2 "readyState" 1)
                            ((aget socket-2 "onopen") (js-obj)))
                          (js/setTimeout
                            (fn []
                              (try
                                (let [recording (ws-client/get-flight-recording)
                                      replay (flight-recorder/replay-runtime-messages {:recording recording
                                                                                      :reducer reducer-step})
                                      msg-types (recording-message-types recording)]
                                  (testing "The live recorder captures close, retry fire, and second open"
                                    (is (some #{:evt/socket-close} msg-types))
                                    (is (some #{:evt/timer-retry-fired} msg-types))
                                    (is (>= (count (filter #{:evt/socket-open} msg-types)) 2)))
                                  (testing "Replay of the recorder output returns to the modeled connected state"
                                    (is (= :connected (get-in replay [:final-state :status])))
                                    (is (= 2 (count @created)))))
                                (finally
                                  (finish-live-recorder-test! original-config done))))
                            0))
                        0))
                    0))
                0)))
          0)))))
