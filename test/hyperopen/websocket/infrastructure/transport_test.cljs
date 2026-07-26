(ns hyperopen.websocket.infrastructure.transport-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.websocket.infrastructure.transport :as infra]))

(defn- make-socket [ready-state sent-payloads close-calls]
  (let [socket (js-obj "readyState" ready-state)]
    (set! (.-send socket)
          (fn [payload]
            (swap! sent-payloads conj payload)))
    (set! (.-close socket)
          (fn [code reason]
            (swap! close-calls conj [code reason])))
    socket))

(deftest function-transport-record-covers-connect-send-close-and-detach-branches-test
  (let [sent-payloads (atom [])
        close-calls (atom [])
        created-url (atom nil)
        socket (make-socket infra/ws-ready-state-open sent-payloads close-calls)
        transport (infra/make-function-transport
                   (fn [ws-url]
                     (reset! created-url ws-url)
                     socket))
        handler-calls (atom [])
        handlers {:on-open (fn [_] (swap! handler-calls conj :open))
                  :on-message (fn [_] (swap! handler-calls conj :message))
                  :on-close (fn [_] (swap! handler-calls conj :close))
                  :on-error (fn [_] (swap! handler-calls conj :error))}]
    (testing "connect installs lifecycle handlers on the returned socket"
      (is (identical? socket
                      (infra/connect-websocket! transport "wss://example.test/ws" handlers)))
      (is (= "wss://example.test/ws" @created-url))
      ((.-onopen socket) #js {})
      ((.-onmessage socket) #js {:data "{}"})
      ((.-onclose socket) #js {})
      ((.-onerror socket) #js {})
      (is (= [:open :message :close :error]
             @handler-calls)))
    (testing "send and close delegate to socket methods"
      (infra/send-json! transport socket {:op :ping :n 1})
      (infra/close-socket! transport socket 3001 "normal")
      (is (= {:op "ping" :n 1}
             (js->clj (js/JSON.parse (first @sent-payloads)) :keywordize-keys true)))
      (is (= [[3001 "normal"]] @close-calls)))
    (testing "ready-state and helper predicates cover open, connecting, and inactive branches"
      (is (= infra/ws-ready-state-open
             (infra/ready-state transport socket)))
      (is (true? (infra/socket-open? transport socket)))
      (is (true? (infra/socket-active? transport socket)))
      (set! (.-readyState socket) infra/ws-ready-state-connecting)
      (is (true? (infra/socket-connecting? transport socket)))
      (is (true? (infra/socket-active? transport socket)))
      (set! (.-readyState socket) 3)
      (is (false? (infra/socket-open? transport socket)))
      (is (false? (infra/socket-connecting? transport socket)))
      (is (false? (infra/socket-active? transport socket)))
      (is (nil? (infra/ready-state transport nil))))
    (testing "detach-handlers is safe for both socket and nil"
      (infra/detach-handlers! transport socket)
      (is (nil? (.-onopen socket)))
      (is (nil? (.-onmessage socket)))
      (is (nil? (.-onclose socket)))
      (is (nil? (.-onerror socket)))
      (is (nil? (infra/detach-handlers! transport nil))))))

(defn- make-scheduler
  [{:keys [navigator document]}]
  (let [timeout-callbacks (atom {})
        interval-callbacks (atom {})
        timeout-clears (atom [])
        interval-clears (atom [])
        listener-calls (atom [])
        next-timeout-id (atom 0)
        next-interval-id (atom 0)
        scheduler (infra/make-function-scheduler
                   {:schedule-timeout-fn (fn [f _ms]
                                           (let [id (keyword (str "timeout-" (swap! next-timeout-id inc)))]
                                             (swap! timeout-callbacks assoc id f)
                                             id))
                    :clear-timeout-fn (fn [id]
                                        (swap! timeout-clears conj id))
                    :schedule-interval-fn (fn [f _ms]
                                            (let [id (keyword (str "interval-" (swap! next-interval-id inc)))]
                                              (swap! interval-callbacks assoc id f)
                                              id))
                    :clear-interval-fn (fn [id]
                                         (swap! interval-clears conj id))
                    :window-object-fn (fn [] :window)
                    :document-object-fn (fn [] document)
                    :navigator-object-fn (fn [] navigator)
                    :add-event-listener-fn (fn [target event-name _handler]
                                             (swap! listener-calls conj [target event-name]))})]
    {:scheduler scheduler
     :timeout-callbacks timeout-callbacks
     :interval-callbacks interval-callbacks
     :timeout-clears timeout-clears
     :interval-clears interval-clears
     :listener-calls listener-calls}))

(deftest function-scheduler-record-covers-guard-and-default-branches-test
  (let [{:keys [scheduler timeout-callbacks interval-callbacks timeout-clears interval-clears listener-calls]}
        (make-scheduler {:navigator (js-obj "onLine" true)
                         :document (js-obj "visibilityState" "visible")})
        timeout-id (infra/schedule-timeout* scheduler (fn [] :timeout-fired) 25)
        interval-id (infra/schedule-interval* scheduler (fn [] :interval-fired) 50)]
    (testing "timeout and interval scheduling adapters execute and clear by id"
      (is (keyword? timeout-id))
      (is (keyword? interval-id))
      ((get @timeout-callbacks timeout-id))
      ((get @interval-callbacks interval-id))
      (infra/clear-timeout* scheduler timeout-id)
      (infra/clear-interval* scheduler interval-id)
      (is (= [timeout-id] @timeout-clears))
      (is (= [interval-id] @interval-clears)))
    (testing "window/document/navigator accessors and listener registration are delegated"
      (is (= :window (infra/window-object* scheduler)))
      (is (some? (infra/document-object* scheduler)))
      (is (some? (infra/navigator-object* scheduler)))
      (infra/add-event-listener* scheduler :window "focus" (fn [_] nil))
      (is (= [[:window "focus"]] @listener-calls)))
    (testing "online? and hidden-tab? cover true/false and fallback branches"
      (is (true? (infra/online?* scheduler)))
      (is (false? (infra/hidden-tab?* scheduler)))
      (is (false? (infra/online?* (:scheduler (make-scheduler {:navigator (js-obj "onLine" false)
                                                                :document (js-obj "visibilityState" "visible")})))))
      (is (true? (infra/online?* (:scheduler (make-scheduler {:navigator (js-obj)
                                                               :document (js-obj "visibilityState" "visible")})))))
      (is (true? (infra/online?* (:scheduler (make-scheduler {:navigator nil
                                                               :document (js-obj "visibilityState" "visible")})))))
      (is (true? (infra/hidden-tab?* (:scheduler (make-scheduler {:navigator (js-obj "onLine" true)
                                                                   :document (js-obj "visibilityState" "hidden")})))))
      (is (false? (infra/hidden-tab?* (:scheduler (make-scheduler {:navigator (js-obj "onLine" true)
                                                                    :document nil}))))))))

(deftest record-constructors-and-protocol-missing-branches-test
  (testing "map-based record constructors execute protocol methods"
    (let [clock (infra/map->FunctionClock {:now-ms-fn (constantly 123)
                                           :random-value-fn (constantly 0.75)})
          transport (infra/map->FunctionTransport
                     {:create-websocket-fn (fn [_]
                                             (js-obj "send" (fn [_] nil)
                                                     "close" (fn [_ _] nil)
                                                     "readyState" infra/ws-ready-state-open))})]
      (is (= 123 (infra/now-ms* clock)))
      (is (= 0.75 (infra/random-value* clock)))
      (is (some? (infra/connect-websocket! transport "wss://example.test/ws" {})))))
  (testing "protocol calls on unsupported values raise missing-protocol errors"
    (is (thrown? js/Error (infra/connect-websocket! nil "wss://example.test/ws" {})))
    (is (thrown? js/Error (infra/send-json! nil (js-obj) {:op :ping})))
    (is (thrown? js/Error (infra/schedule-timeout* nil (fn [] nil) 10)))
    (is (thrown? js/Error (infra/now-ms* nil)))))
