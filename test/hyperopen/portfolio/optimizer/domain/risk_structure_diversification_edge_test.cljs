(ns hyperopen.portfolio.optimizer.domain.risk-structure-diversification-edge-test
  "Boundary and invariant coverage for portfolio-level diversification benchmarks."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.domain.risk-structure :as risk-structure]))

(defn- near?
  ([expected actual] (near? expected actual 1e-12))
  ([expected actual tolerance]
   (and (number? actual)
        (js/isFinite actual)
        (< (js/Math.abs (- expected actual)) tolerance))))

(def ^:private scalar-keys
  [:modeled-volatility
   :all-move-together-volatility
   :zero-correlation-volatility
   :reduction-vs-all-move-together
   :reduction-ratio-vs-all-move-together
   :modeled-minus-zero-correlation])

(deftest signed-books-use-held-pnl-benchmarks-test
  (testing "a positive underlying correlation offsets a long-short held-P&L book"
    (let [covariance [[1.0 0.5] [0.5 1.0]]
          weights [0.5 -0.5]
          summary (risk-structure/portfolio-diversification-summary covariance
                                                                    weights)
          contributions (risk-structure/decomposition covariance weights)]
      (is (= :ok (:status summary)))
      (is (near? 1.0 (:all-move-together-volatility summary)))
      (is (near? (js/Math.sqrt 0.5)
                 (:zero-correlation-volatility summary)))
      (is (near? 0.5 (:modeled-volatility summary)))
      (is (neg? (:modeled-minus-zero-correlation summary)))
      (is (= [0.5 0.5] (:net-shares contributions)))))
  (testing "underlying -1 means perfectly comoving held P&L for a long-short pair"
    (let [summary (risk-structure/portfolio-diversification-summary
                   [[1.0 -1.0] [-1.0 1.0]]
                   [0.5 -0.5])]
      (is (= :ok (:status summary)))
      (is (near? 1.0 (:modeled-volatility summary)))
      (is (near? 1.0 (:all-move-together-volatility summary)))
      (is (near? 0.0 (:reduction-vs-all-move-together summary)))
      (is (near? 0.0 (:reduction-ratio-vs-all-move-together summary)))
      (is (every? #(and (number? %) (js/isFinite %))
                  (map summary scalar-keys))))))

(deftest zero-correlation-and-single-asset-boundaries-test
  (testing "diagonal covariance pins modeled to the zero-correlation marker"
    (let [summary (risk-structure/portfolio-diversification-summary
                   [[4.0 0.0] [0.0 1.0]]
                   [0.25 -0.5])]
      (is (= :ok (:status summary)))
      (is (near? (:zero-correlation-volatility summary)
                 (:modeled-volatility summary)))
      (is (near? 0.0 (:modeled-minus-zero-correlation summary) 1e-15))
      (is (pos? (:reduction-vs-all-move-together summary)))))
  (testing "one held asset has finite zero benefit"
    (let [summary (risk-structure/portfolio-diversification-summary [[0.09]]
                                                                    [-2.0])]
      (is (= :ok (:status summary)))
      (is (near? 0.6 (:modeled-volatility summary)))
      (is (near? (:modeled-volatility summary)
                 (:zero-correlation-volatility summary)))
      (is (near? (:modeled-volatility summary)
                 (:all-move-together-volatility summary)))
      (is (every? #(near? 0.0 (summary %))
                  [:reduction-vs-all-move-together
                   :reduction-ratio-vs-all-move-together
                   :modeled-minus-zero-correlation])))))

(deftest diversification-summary-is-homogeneous-in-weight-scale-test
  (let [covariance [[1.0 0.25] [0.25 4.0]]
        base (risk-structure/portfolio-diversification-summary covariance
                                                                [0.4 -0.2])]
    (doseq [[factor weights] [[3.0 [1.2 -0.6]]
                              [-2.0 [-0.8 0.4]]]]
      (let [scaled (risk-structure/portfolio-diversification-summary covariance
                                                                      weights)
            magnitude (js/Math.abs factor)]
        (doseq [key [:modeled-volatility
                     :all-move-together-volatility
                     :zero-correlation-volatility
                     :reduction-vs-all-move-together
                     :modeled-minus-zero-correlation]]
          (is (near? (* magnitude (key base)) (key scaled))
              (str "level scales by |factor| for " key)))
        (is (near? (:reduction-ratio-vs-all-move-together base)
                   (:reduction-ratio-vs-all-move-together scaled)))))))

(deftest malformed-and-degenerate-books-fail-closed-test
  (testing "malformed shapes and nonfinite inputs never leak scalar output"
    (doseq [[label covariance weights]
            [["empty" [] []]
             ["jagged" [[1.0 0.0] [0.0]] [0.5 0.5]]
             ["non-square" [[1.0 0.0 0.0] [0.0 1.0 0.0]] [0.5 0.5]]
             ["length mismatch" [[1.0 0.0] [0.0 1.0]] [1.0]]
             ["NaN covariance" [[1.0 js/NaN] [js/NaN 1.0]] [0.5 0.5]]
             ["Infinity covariance" [[1.0 js/Infinity]
                                      [js/Infinity 1.0]] [0.5 0.5]]
             ["negative diagonal" [[-1.0 0.0] [0.0 1.0]] [0.5 0.5]]
             ["nonfinite weight" [[1.0 0.0] [0.0 1.0]]
              [0.5 js/Infinity]]]]
      (let [summary (risk-structure/portfolio-diversification-summary
                     covariance weights)]
        (is (= :error (:status summary)) label)
        (is (keyword? (:reason summary)) label)
        (is (not-any? #(and (number? %) (not (js/isFinite %)))
                      (map summary scalar-keys))
            label))))
  (testing "an all-zero book is unavailable, not a zero-risk comparison"
    (let [summary (risk-structure/portfolio-diversification-summary
                   [[1.0 0.0] [0.0 1.0]]
                   [0.0 0.0])]
      (is (= :error (:status summary)))
      (is (= :degenerate-weights (:reason summary)))
      (is (not-any? #(contains? summary %) scalar-keys)))))

(deftest non-flat-perfect-hedge-is-degenerate-not-zero-risk-test
  (let [summary (risk-structure/portfolio-diversification-summary
                 [[1.0 1.0]
                  [1.0 1.0]]
                 [0.5 -0.5])]
    (is (= :error (:status summary)))
    (is (keyword? (:reason summary)))
    (is (not (contains? summary :modeled-volatility))
        "an exact zero-variance hedge is unavailable, not a renderable 0% book")))
