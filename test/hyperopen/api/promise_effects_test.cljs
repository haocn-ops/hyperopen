(ns hyperopen.api.promise-effects-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.promise-effects :as promise-effects]))

(deftest apply-success-and-return-applies-projection-and-returns-payload-test
  (let [store (atom {})
        handler (promise-effects/apply-success-and-return
                 store
                 (fn [state payload]
                   (assoc state :payload payload)))
        payload {:rows [{:id 1}]}]
    (is (= payload (handler payload)))
    (is (= payload (:payload @store)))))

(deftest apply-success-and-return-supports-leading-projection-args-test
  (let [store (atom {})
        handler (promise-effects/apply-success-and-return
                 store
                 (fn [state scope payload]
                   (assoc state :projection [scope payload]))
                 :bootstrap)
        payload [{:coin "BTC"}]]
    (is (= payload (handler payload)))
    (is (= [:bootstrap payload]
           (:projection @store)))))

(deftest apply-error-and-reject-applies-projection-and-rejects-test
  (async done
    (let [store (atom {})
          err (js/Error. "request failed")
          handler (promise-effects/apply-error-and-reject
                   store
                   (fn [state failure]
                     (assoc state :error (.-message failure))))]
      (-> (handler err)
          (.then (fn [_]
                   (is false "Expected apply-error-and-reject callback to reject")
                   (done)))
          (.catch (fn [caught]
                    (is (identical? err caught))
                    (is (= "request failed" (:error @store)))
                    (done)))))))

(deftest log-error-and-reject-logs-and-rejects-test
  (async done
    (let [logs (atom [])
          err (js/Error. "boom")
          handler (promise-effects/log-error-and-reject
                   (fn [& args]
                     (swap! logs conj args))
                   "request failed:")]
      (-> (handler err)
          (.then (fn [_]
                   (is false "Expected log-error-and-reject callback to reject")
                   (done)))
          (.catch (fn [caught]
                    (is (identical? err caught))
                    (is (= [["request failed:" err]] @logs))
                    (done)))))))

(deftest log-apply-error-and-reject-applies-projection-logs-and-rejects-test
  (async done
    (let [logs (atom [])
          store (atom {})
          err (js/Error. "dex fetch failed")
          handler (promise-effects/log-apply-error-and-reject
                   (fn [& args]
                     (swap! logs conj args))
                   "Error fetching dexes:"
                   store
                   (fn [state source failure]
                     (assoc state :failure [source (.-message failure)]))
                   :bootstrap)]
      (-> (handler err)
          (.then (fn [_]
                   (is false "Expected log-apply-error-and-reject callback to reject")
                   (done)))
          (.catch (fn [caught]
                    (is (identical? err caught))
                    (is (= [:bootstrap "dex fetch failed"]
                           (:failure @store)))
                    (is (= [["Error fetching dexes:" err]] @logs))
                    (done)))))))
