(ns hyperopen.portfolio.optimizer.application.engine-return-inputs-memo-test
  "The by-instrument expected-return helpers are read on every render of the
  Return-views panel and Black-Litterman preview — including once per
  pointermove while dragging the exposure pad. Each computes a full covariance
  matrix from history, so they memoize on the request sub-values they actually
  consume. These tests pin that contract: a constraints-only request change (a
  drag) must reuse the cached result, while a change to any consumed input must
  recompute."
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.engine.context :as engine-context]))

(defn- request-with-constraints
  [constraints]
  {:universe [{:instrument-id "A"}
              {:instrument-id "B"}]
   :return-model {:kind :black-litterman
                  :views []}
   :risk-model {:kind :sample-covariance}
   :periods-per-year 10
   :constraints constraints
   :history {:return-series-by-instrument {"A" [0.01 0.03 0.02]
                                           "B" [0.04 0.01 0.04]}}
   :black-litterman-prior {:source :market-cap
                           :weights-by-instrument {"A" 0.6
                                                   "B" 0.4}}})

(deftest baseline-inputs-are-cached-across-constraints-only-request-changes-test
  (let [first-result (engine-context/baseline-expected-return-inputs-by-instrument
                      (request-with-constraints {:gross-min 1.0 :gross-max 2.0}))
        second-result (engine-context/baseline-expected-return-inputs-by-instrument
                       (request-with-constraints {:gross-min 1.2 :gross-max 2.4}))]
    (is (= #{"A" "B"} (set (keys first-result))))
    (is (identical? first-result second-result)
        "a drag rebuilds the request but only :constraints differ — the cached value must be reused")))

(deftest baseline-inputs-recompute-when-a-consumed-input-changes-test
  (let [base (request-with-constraints {})
        first-result (engine-context/baseline-expected-return-inputs-by-instrument base)
        changed (assoc-in base [:history :return-series-by-instrument "A"] [0.05 0.05 0.05])
        second-result (engine-context/baseline-expected-return-inputs-by-instrument changed)]
    (is (not (identical? first-result second-result)))
    (is (not= (get first-result "A") (get second-result "A")))))

(deftest expected-inputs-are-cached-across-constraints-only-request-changes-test
  (let [first-result (engine-context/expected-return-inputs-by-instrument
                      (request-with-constraints {:net-min -0.5}))
        second-result (engine-context/expected-return-inputs-by-instrument
                       (request-with-constraints {:net-min -0.1}))]
    (is (= #{"A" "B"} (set (keys first-result))))
    (is (identical? first-result second-result))))

(deftest expected-inputs-recompute-when-the-prior-changes-test
  (let [base (request-with-constraints {})
        first-result (engine-context/expected-return-inputs-by-instrument base)
        changed (assoc-in base [:black-litterman-prior :weights-by-instrument]
                          {"A" 0.1 "B" 0.9})
        second-result (engine-context/expected-return-inputs-by-instrument changed)]
    (is (not (identical? first-result second-result))
        "the posterior consumes the Black-Litterman prior, so a prior change must recompute")))
