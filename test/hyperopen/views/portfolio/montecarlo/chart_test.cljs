(ns hyperopen.views.portfolio.montecarlo.chart-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.portfolio.montecarlo.chart.model :as chart-model]))

(defn- sample-path
  [& values]
  (double-array values))

(def sample-result
  {:meta {:start-equity 100
          :horizon 4
          :span-years 1
          :bust -0.2
          :goal 0.5}
   :times [0 1 2 3 4]
   :band {:p5 [90 92 94 96 98]
          :p25 [95 98 101 104 107]
          :p50 [100 105 110 115 120]
          :p75 [105 112 119 126 133]
          :p95 [110 120 130 140 150]}
   :draw-paths [(sample-path 100 101 102 103 104)
                (sample-path 100 99 98 97 96)]})

(defn- model
  [spec]
  (chart-model/spaghetti-model {:w 500 :h 420}
                               (merge {:result sample-result}
                                      spec)))

(deftest spaghetti-model-derives-stable-geometry-and-visible-window-test
  (let [model* (model {:progress 0.5
                       :show-paths? true
                       :path-count 1})
        domain (:domain model*)]
    (testing "domain includes start, bust, goal, and band extrema with padding"
      (is (= 75.8 (:lo domain)))
      (is (= 154.2 (:hi domain)))
      (is (= 80 (:bust-eq domain)))
      (is (= 150 (:goal-eq domain))))
    (testing "plot geometry and hover geometry stay aligned"
      (is (= {:pad-l 64
              :plot-w 420
              :horizon 4
              :start 100
              :span-years 1}
             (:hover-geo model*)))
      (is (= 380 (get-in model* [:plot :plot-h]))))
    (testing "progress determines the revealed percentile grid and sampled paths"
      (is (= 2 (:max-i model*)))
      (is (= 2 (:last-t model*)))
      (is (= 3 (count (:median-points model*))))
      (is (= 1 (count (:sampled-paths model*))))
      (is (= 3 (count (first (:sampled-paths model*))))))
    (testing "axis labels preserve elapsed calendar-time semantics"
      (is (= ["0" "3mo" "6mo" "9mo" "1.0y"]
             (mapv :label (:x-axis model*)))))
    (testing "endpoint dots are withheld until the reveal completes"
      (is (empty? (:endpoint-dots model*))))))

(deftest spaghetti-model-clamps-default-progress-to-final-frame-test
  (let [model* (model {:show-paths? false})]
    (is (= 4 (:max-i model*)))
    (is (= 4 (:last-t model*)))
    (is (empty? (:sampled-paths model*)))
    (is (= 3 (count (:endpoint-dots model*))))))

(deftest spaghetti-model-draws-endpoint-dots-at-final-frame-on-complete-reveal-test
  (let [model* (model {:progress 0.999})
        endpoint-dots (:endpoint-dots model*)]
    (is (= 3 (:max-i model*)))
    (is (= [484 484 484] (mapv :x endpoint-dots)))))
