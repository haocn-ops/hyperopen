(ns hyperopen.websocket.interval-coverage-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.utils.interval :as interval]))

(def ^:private expected-ms
  {:1m 60000
   :3m 180000
   :5m 300000
   :15m 900000
   :30m 1800000
   :1h 3600000
   :2h 7200000
   :4h 14400000
   :8h 28800000
   :12h 43200000
   :1d 86400000
   :3d 259200000
   :1w 604800000
   :1M 2592000000})

(deftest interval-multimethod-covers-all-supported-branches-test
  (doseq [[interval-key expected] expected-ms]
    (testing (str "interval " interval-key)
      (is (= expected
             (interval/interval-to-milliseconds interval-key))))))

(deftest interval-multimethod-default-fallback-branch-test
  (is (= 86400000 (interval/interval-to-milliseconds :unknown)))
  (is (= 86400000 (interval/interval-to-milliseconds nil))))
