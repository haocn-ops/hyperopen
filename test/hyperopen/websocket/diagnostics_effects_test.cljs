(ns hyperopen.websocket.diagnostics-effects-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.websocket.diagnostics-effects :as diagnostics-effects]))

(deftest confirm-ws-diagnostics-reveal-persists-flag-only-after-confirmation-test
  (let [store (atom {:websocket-ui {:reveal-sensitive? false}})]
    (diagnostics-effects/confirm-ws-diagnostics-reveal!
     {:store store
      :confirm-fn (fn [_] false)})
    (is (false? (get-in @store [:websocket-ui :reveal-sensitive?])))
    (diagnostics-effects/confirm-ws-diagnostics-reveal!
     {:store store
      :confirm-fn (fn [_] true)})
    (is (true? (get-in @store [:websocket-ui :reveal-sensitive?])))))

(deftest copy-websocket-diagnostics-builds-payload-with-app-version-and-delegates-test
  (let [captured-args (atom nil)
        store (atom {:websocket {:health {:generated-at-ms 1700000000000}}
                     :websocket-ui {:copy-status nil}})
        payload-calls (atom [])]
    (diagnostics-effects/copy-websocket-diagnostics!
     {:store store
      :app-version "9.9.9"
      :log-fn (fn [& _] nil)
      :set-copy-status! (fn [_ _] nil)
      :diagnostics-copy-payload-fn (fn [state health app-version]
                                     (swap! payload-calls conj [state health app-version])
                                     {:app {:version app-version}})
      :sanitize-value-fn (fn [_ payload] payload)
      :copy-success-status-fn (fn [_] {:kind :success})
      :copy-error-status-fn (fn [_ _] {:kind :error})
      :copy-websocket-diagnostics-fn (fn [args]
                                       (reset! captured-args args)
                                       (let [payload ((:diagnostics-copy-payload args)
                                                      @store
                                                      {:generated-at-ms 1700000000000})]
                                         ((:sanitize-value args) :redact payload)
                                         ((:copy-success-status args) {:generated-at-ms 1700000000000})
                                         ((:copy-error-status args)
                                          {:generated-at-ms 1700000000000}
                                          "{}")
                                         nil))})
    (is (map? @captured-args))
    (is (= [[@store {:generated-at-ms 1700000000000} "9.9.9"]]
           @payload-calls))))
