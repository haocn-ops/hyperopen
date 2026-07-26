(ns hyperopen.websocket.application.flight-recorder-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.websocket.flight-recorder :as flight-recorder]))

(defn- monotonic-now-ms
  [seed]
  (let [clock (atom seed)]
    (fn []
      (let [value @clock]
        (swap! clock inc)
        value))))

(deftest recorder-bounded-capacity-drops-oldest-events-test
  (let [recorder (flight-recorder/create-recorder {:capacity 2
                                                   :now-ms (monotonic-now-ms 100)})]
    (flight-recorder/start-session! recorder {:label "capacity-test"})
    (flight-recorder/record-runtime-msg! recorder {:msg/type :evt/one})
    (flight-recorder/record-runtime-msg! recorder {:msg/type :evt/two})
    (flight-recorder/record-runtime-msg! recorder {:msg/type :evt/three})
    (let [snap (flight-recorder/snapshot recorder)]
      (is (= 2 (:event-count snap)))
      (is (= 1 (:dropped-count snap)))
      (is (= [:evt/two :evt/three]
             (mapv #(get-in % [:payload :msg-type]) (:events snap)))))))

(deftest redacted-snapshot-redacts-sensitive-runtime-message-fields-test
  (let [address "0x1234567890123456789012345678901234567890"
        recorder (flight-recorder/create-recorder {:capacity 8
                                                   :now-ms (monotonic-now-ms 200)})]
    (flight-recorder/start-session! recorder {:ownerAddress address})
    (flight-recorder/record-runtime-msg! recorder {:msg/type :cmd/send-message
                                                   :walletAddress address
                                                   :signature "0xdeadbeef"})
    (let [redacted (flight-recorder/redacted-snapshot recorder)
          message (get-in redacted [:events 0 :payload :msg])]
      (is (= "<redacted>" (:session redacted)))
      (is (= "<redacted>" (:walletAddress message)))
      (is (= "<redacted>" (:signature message))))))

(deftest replay-runtime-messages-rebuilds-deterministic-final-state-test
  (let [recording {:session {:metadata {:initial-state {:count 0}}}
                   :events [{:kind :runtime/msg
                             :payload {:msg {:msg/type :cmd/inc}}}
                            {:kind :runtime/effects
                             :payload {:effect-types [:fx/log]}}
                            {:kind :runtime/msg
                             :payload {:msg {:msg/type :cmd/add
                                             :delta 2}}}]}
        reducer (fn [state msg]
                  (case (:msg/type msg)
                    :cmd/inc {:state (update state :count (fnil inc 0))
                              :effects [{:fx/type :fx/log
                                         :message "inc"}]}
                    :cmd/add {:state (update state :count (fnil + 0) (or (:delta msg) 0))
                              :effects [{:fx/type :fx/timer-set-timeout
                                         :timer-key :demo}]}
                    {:state state
                     :effects []}))
        replay (flight-recorder/replay-runtime-messages {:recording recording
                                                         :reducer reducer})]
    (testing "Only recorded runtime messages participate in replay"
      (is (= 2 (:message-count replay)))
      (is (= 2 (:step-count replay))))
    (testing "Final state and effect trace are deterministic"
      (is (= {:count 3} (:final-state replay)))
      (is (= [[:fx/log]
              [:fx/timer-set-timeout]]
             (mapv :effect-types (:trace replay)))))))
