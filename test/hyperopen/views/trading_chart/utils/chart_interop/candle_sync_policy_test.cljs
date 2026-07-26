(ns hyperopen.views.trading-chart.utils.chart-interop.candle-sync-policy-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trading-chart.utils.chart-interop.candle-sync-policy :as candle-sync-policy]))

(defn- infer
  [previous-candles next-candles]
  (candle-sync-policy/infer-decision previous-candles next-candles))

(deftest infer-decision-noops-for-empty-and-identical-reference-test
  (is (= {:mode :noop
          :reason :both-empty
          :previous-count 0
          :next-count 0}
         (infer [] [])))
  (let [candles [{:time 1 :open 10 :high 11 :low 9 :close 10.5}]]
    (is (= {:mode :noop
            :reason :identical-reference
            :previous-count 1
            :next-count 1}
           (infer candles candles)))))

(deftest infer-decision-full-resets-for-empty-seed-and-clear-test
  (let [candles [{:time 1 :open 10 :high 11 :low 9 :close 10.5}]]
    (is (= {:mode :full-reset
            :reason :seed-from-empty
            :previous-count 0
            :next-count 1}
           (infer nil candles)))
    (is (= {:mode :full-reset
            :reason :cleared-data
            :previous-count 1
            :next-count 0}
           (infer candles [])))))

(deftest infer-decision-distinguishes-unchanged-tail-from-tail-rewrite-test
  (let [previous [{:time 1 :open 10 :high 11 :low 9 :close 10.5}
                  {:time 2 :open 10.5 :high 12 :low 10 :close 11.5}]
        rewritten [{:time 1 :open 10 :high 11 :low 9 :close 10.5}
                   {:time 2 :open 10.5 :high 12.5 :low 10 :close 12.0}]]
    (is (= {:mode :noop
            :reason :unchanged-tail
            :previous-count 2
            :next-count 2}
           (infer previous (mapv identity previous))))
    (is (= {:mode :update-last
            :reason :tail-rewrite
            :previous-count 2
            :next-count 2}
           (infer previous rewritten)))))

(deftest infer-decision-appends-single-trailing-candle-test
  (let [previous [{:time 1 :open 10 :high 11 :low 9 :close 10.5}
                  {:time 2 :open 10.5 :high 12 :low 10 :close 11.5}]
        next-candles (conj previous {:time 3 :open 11.5 :high 12.2 :low 11.2 :close 12.0})]
    (is (= {:mode :append-last
            :reason :single-append
            :previous-count 2
            :next-count 3}
           (infer previous next-candles)))))

(deftest infer-decision-full-resets-for-prefix-mutations-and-tail-time-mismatch-test
  (let [previous [{:time 1 :open 10 :high 11 :low 9 :close 10.5}
                  {:time 2 :open 10.5 :high 12 :low 10 :close 11.5}]
        prefix-mutated [{:time 1 :open 10 :high 13 :low 9 :close 12.5}
                        {:time 2 :open 10.5 :high 12 :low 10 :close 11.5}]
        tail-time-mismatch [{:time 1 :open 10 :high 11 :low 9 :close 10.5}
                            {:time 3 :open 10.5 :high 12 :low 10 :close 11.5}]]
    (is (= {:mode :full-reset
            :reason :prefix-mutation
            :previous-count 2
            :next-count 2}
           (infer previous prefix-mutated)))
    (is (= {:mode :full-reset
            :reason :tail-time-mismatch
            :previous-count 2
            :next-count 2}
           (infer previous tail-time-mismatch)))))

(deftest infer-decision-full-resets-for-append-collision-and-count-mismatch-test
  (let [previous [{:time 1 :open 10 :high 11 :low 9 :close 10.5}
                  {:time 2 :open 10.5 :high 12 :low 10 :close 11.5}]
        append-collision (conj previous {:time 2 :open 11.5 :high 12.2 :low 11.2 :close 12.0})
        count-mismatch [{:time 1 :open 10 :high 11 :low 9 :close 10.5}
                        {:time 2 :open 10.5 :high 12 :low 10 :close 11.5}
                        {:time 3 :open 11.5 :high 12.2 :low 11.2 :close 12.0}
                        {:time 4 :open 12.0 :high 12.6 :low 11.8 :close 12.4}]]
    (is (= {:mode :full-reset
            :reason :append-time-collision
            :previous-count 2
            :next-count 3}
           (infer previous append-collision)))
    (is (= {:mode :full-reset
            :reason :count-mismatch
            :previous-count 2
            :next-count 4}
           (infer previous count-mismatch)))))
