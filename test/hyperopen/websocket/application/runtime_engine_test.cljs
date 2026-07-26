(ns hyperopen.websocket.application.runtime-engine-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.websocket.application.runtime-engine :as runtime-engine]))

(defn- monotonic-now-ms
  [seed]
  (let [clock (atom seed)]
    (fn []
      (let [value @clock]
        (swap! clock inc)
        value))))

(deftest start-engine-records-runtime-messages-and-effects-test
  (async done
    (let [recorded-msgs (atom [])
          recorded-effects (atom [])
          interpreted (atom [])
          engine (runtime-engine/start-engine!
                   {:initial-state {:count 0}
                    :now-ms (monotonic-now-ms 1000)
                    :reducer (fn [state msg]
                               {:state (update state :count (fnil inc 0))
                                :effects [{:fx/type :fx/log
                                           :msg-type (:msg/type msg)}]})
                    :interpret-effect! (fn [_ctx fx]
                                         (swap! interpreted conj fx))
                    :record-runtime-msg! #(swap! recorded-msgs conj %)
                    :record-runtime-effects! #(swap! recorded-effects conj {:msg %1
                                                                            :effects %2})})]
      (runtime-engine/dispatch! engine {:msg/type :evt/test})
      (js/setTimeout
        (fn []
          (is (= 1 (count @recorded-msgs)))
          (is (= :evt/test (:msg/type (first @recorded-msgs))))
          (is (= 1000 (:ts (first @recorded-msgs))))
          (is (= 1 (count @recorded-effects)))
          (is (= [:fx/log]
                 (mapv :fx/type (:effects (first @recorded-effects)))))
          (is (= [{:fx/type :fx/log
                   :msg-type :evt/test}]
                 @interpreted))
          (runtime-engine/stop-engine! engine)
          (done))
        10))))

(deftest dispatch-after-stop-records-drop-payload-test
  (let [drops (atom [])
        engine (runtime-engine/start-engine!
                 {:initial-state {}
                  :reducer (fn [state _]
                             {:state state
                              :effects []})
                  :interpret-effect! (fn [_ _] nil)
                  :record-runtime-drop! #(swap! drops conj %)})]
    (runtime-engine/stop-engine! engine)
    (runtime-engine/dispatch! engine {:msg/type :evt/after-stop})
    (is (= 1 (count @drops)))
    (is (= :mailbox-enqueue-failure (:reason (first @drops))))
    (is (= :evt/after-stop (get-in (first @drops) [:message :msg/type])))))
