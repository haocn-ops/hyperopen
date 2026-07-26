(ns hyperopen.portfolio.metrics.history-normalization-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.metrics :as metrics]
            [hyperopen.portfolio.metrics.normalization :as normalization]
            [hyperopen.portfolio.metrics.test-utils :refer [approx=]]))

(deftest history-points-normalizes-mixed-map-and-vector-rows-test
  (let [rows [[2000 "2.5"]
              {:timestamp "1000" :pnl "1.5"}
              {:timeMs 3000 :value "4"}]]
    (is (= [{:time-ms 1000 :value 1.5}
            {:time-ms 2000 :value 2.5}
            {:time-ms 3000 :value 4}]
           (normalization/history-points rows)))))

(deftest aligned-account-pnl-points-dedupes-each-history-last-write-wins-before-join-test
  (let [summary {:accountValueHistory [[1000 100]
                                       [2000 120]
                                       [2000 130]
                                       [3000 140]]
                 :pnlHistory [[1000 0]
                              [2000 10]
                              [2000 15]
                              [3000 25]]}]
    (is (= [{:time-ms 1000 :account-value 100 :pnl-value 0}
            {:time-ms 2000 :account-value 130 :pnl-value 15}
            {:time-ms 3000 :account-value 140 :pnl-value 25}]
           (normalization/aligned-account-pnl-points summary)))))

(deftest aligned-account-pnl-points-keeps-only-exact-timestamp-matches-test
  (let [summary {:accountValueHistory [[1000 100]
                                       [2000 120]
                                       [3000 140]]
                 :pnlHistory [[1000 0]
                              [2500 10]
                              [3000 20]]}]
    (is (= [{:time-ms 1000 :account-value 100 :pnl-value 0}
            {:time-ms 3000 :account-value 140 :pnl-value 20}]
           (normalization/aligned-account-pnl-points summary)))))

(deftest returns-history-rows-is-stable-under-mixed-shape-duplicates-test
  (let [canonical-summary {:accountValueHistory [[1000 100]
                                                 [2000 110]
                                                 [3000 121]]
                           :pnlHistory [[1000 0]
                                        [2000 10]
                                        [3000 21]]}
        raw-summary {:accountValueHistory [{:timeMs "3000" :accountValue "121"}
                                           ["1000" "100"]
                                           {:timestamp "2000" :value "110"}
                                           {:time "2000" :accountValue "110"}
                                           {:timestamp "3000" :value "121"}]
                     :pnlHistory [{:timestamp "3000" :pnl "21"}
                                  ["1000" "0"]
                                  {:timeMs "2000" :pnl "10"}
                                  {:time "2000" :value "10"}
                                  {:time "3000" :pnl "21"}]}
        canonical (metrics/returns-history-rows-from-summary canonical-summary)
        raw (metrics/returns-history-rows-from-summary raw-summary)]
    (is (= canonical raw))))

(deftest normalize-cumulative-percent-rows-rejects-nonfinite-timestamps-test
  (let [rows [[1000 10]
              [js/Infinity 20]
              [js/NaN 30]
              ["Infinity" 40]]]
    (is (= [{:time-ms 1000 :percent 10 :factor 1.1}]
           (normalization/normalize-cumulative-percent-rows rows)))))

(deftest normalize-cumulative-percent-rows-last-write-wins-on-duplicate-timestamps-test
  (let [rows [[1000 0]
              [2000 10]
              [2000 21]
              [3000 33.1]]
        normalized (normalization/normalize-cumulative-percent-rows rows)]
    (is (= [1000 2000 3000]
           (mapv :time-ms normalized)))
    (is (= 21
           (:percent (nth normalized 1))))
    (is (approx= 1.21
                 (:factor (nth normalized 1))
                 1e-12))))

(deftest normalize-cumulative-percent-rows-keeps-negative-one-hundred-percent-rows-test
  (let [rows [[1000 0]
              [2000 -100]
              [3000 -25]]
        normalized (normalization/normalize-cumulative-percent-rows rows)]
    (is (= [1000 2000 3000]
           (mapv :time-ms normalized)))
    (is (= -100
           (:percent (nth normalized 1))))
    (is (= 0
           (:factor (nth normalized 1))))))

(deftest normalize-daily-rows-rejects-nonfinite-timestamps-even-with-valid-day-test
  (let [rows [{:day "2024-01-01" :time-ms js/Infinity :return 0.1}
              {:day "2024-01-02" :time-ms js/NaN :return 0.2}
              {:day "2024-01-03" :time-ms 3000 :return 0.3}]]
    (is (= [{:day "2024-01-03" :time-ms 3000 :return 0.3}]
           (normalization/normalize-daily-rows rows)))))

(deftest normalize-daily-rows-last-write-wins-on-duplicate-timestamps-test
  (let [rows [{:day "2024-01-01" :time-ms 1000 :return 0.1}
              {:day "2024-01-01" :time-ms 1000 :return 0.2}
              {:day "2024-01-01" :time-ms 2000 :return 0.3}]
        normalized (normalization/normalize-daily-rows rows)]
    (is (= [{:day "2024-01-01" :time-ms 1000 :return 0.2}
            {:day "2024-01-01" :time-ms 2000 :return 0.3}]
           normalized))))
