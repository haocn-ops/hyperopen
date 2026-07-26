(ns hyperopen.portfolio.optimizer.domain.black-litterman-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.domain.black-litterman :as black-litterman]))

(defn- near?
  [expected actual]
  (< (js/Math.abs (- expected actual)) 0.0000001))

(defn- within?
  [expected actual tolerance]
  (< (js/Math.abs (- expected actual)) tolerance))

(defn- abs*
  [value]
  (js/Math.abs value))

(deftest implied-equilibrium-returns-use-risk-aversion-covariance-and-prior-weights-test
  (let [pi (black-litterman/implied-equilibrium-returns
            {:risk-aversion 2
             :covariance [[1 0.5]
                          [0.5 2]]
             :prior-weights [0.6 0.4]})]
    (is (= [1.6 2.2] pi))))

(deftest posterior-returns-combine-prior-and-views-test
  (let [posterior (black-litterman/posterior-returns
                   {:instrument-ids ["A" "B"]
                    :covariance [[1 0]
                                 [0 1]]
                    :prior-weights [0.6 0.4]
                    :risk-aversion 1
                    :tau 1
                    :views [{:weights {"A" 1
                                       "B" -1}
                             :return 0.1
                             :confidence 0.5}]})]
    (is (= :black-litterman (:model posterior)))
    (is (near? 0.575 (get-in posterior [:expected-returns-by-instrument "A"])))
    (is (near? 0.425 (get-in posterior [:expected-returns-by-instrument "B"])))
    (is (= {:prior-source :market-cap
            :view-count 1
            :tau 1}
           (select-keys (:diagnostics posterior) [:prior-source :view-count :tau])))))

(deftest posterior-returns-use-provided-baseline-prior-returns-for-absolute-views-test
  (let [posterior (black-litterman/posterior-returns
                   {:instrument-ids ["perp:BTC" "vault:GROWI" "vault:HLP"]
                    :covariance [[1.8 0.15 -0.05]
                                 [0.15 0.35 0.02]
                                 [-0.05 0.02 0.16]]
                    :prior-weights [(/ 1 3) (/ 1 3) (/ 1 3)]
                    :prior-returns [-0.13 0.2 0.2]
                    :risk-aversion 1
                    :tau 0.05
                    :views [{:kind :absolute
                             :weights {"perp:BTC" 1}
                             :return 0.2
                             :confidence 0.75
                             :confidence-variance 0.25}
                            {:kind :absolute
                             :weights {"vault:GROWI" 1}
                             :return 0.2
                             :confidence 0.75
                             :confidence-variance 0.25}
                            {:kind :absolute
                             :weights {"vault:HLP" 1}
                             :return 0.2
                             :confidence 0.75
                             :confidence-variance 0.25}]
                    :prior-source :fallback-equal-weight})
        returns (:expected-returns-by-instrument posterior)
        diagnostics (:diagnostics posterior)]
    (is (<= (get returns "perp:BTC") 0.205)
        "A 20% absolute BTC view should not become a 60% return because BTC has high variance.")
    (is (within? 0.2 (get returns "vault:HLP") 0.01)
        "An unchanged HLP absolute view should remain near its 20% baseline/view value.")
    (is (= :provided (:prior-return-source diagnostics)))))

(deftest posterior-returns-confidence-controls-distance-from-baseline-prior-test
  (let [posterior-for-confidence
        (fn [confidence]
          (black-litterman/posterior-returns
           {:instrument-ids ["perp:BTC"]
            :covariance [[1.8]]
            :prior-weights [1]
            :prior-returns [-0.13]
            :risk-aversion 1
            :tau 0.05
            :views [{:kind :absolute
                     :weights {"perp:BTC" 1}
                     :return 0.2
                     :confidence confidence}]
            :prior-source :fallback-equal-weight}))
        low (get-in (posterior-for-confidence 0.25)
                    [:expected-returns-by-instrument "perp:BTC"])
        high (get-in (posterior-for-confidence 0.75)
                     [:expected-returns-by-instrument "perp:BTC"])]
    (is (<= -0.13 low 0.2))
    (is (<= -0.13 high 0.2))
    (is (< (abs* (- 0.2 high))
           (abs* (- 0.2 low)))
        "Higher confidence should move the posterior closer to the entered view.")))

(deftest posterior-returns-rejects-view-rows-without-matching-instruments-test
  (let [posterior (black-litterman/posterior-returns
                   {:instrument-ids ["perp:BTC"]
                    :covariance [[1.8]]
                    :prior-weights [1]
                    :prior-returns [-0.13]
                    :risk-aversion 1
                    :tau 0.05
                    :views [{:id "eth-view"
                             :kind :absolute
                             :weights {"perp:ETH" 1}
                             :return 0.2
                             :confidence 0.75}]
                    :prior-source :fallback-equal-weight})
        warning (first (:warnings posterior))]
    (is (= :invalid (:status posterior)))
    (is (= :black-litterman-view-has-no-matching-instrument
           (:code warning)))
    (is (= "eth-view" (:view-id warning)))
    (is (= ["perp:ETH"] (:instrument-ids warning)))
    (is (= 1 (get-in posterior [:diagnostics :view-count])))
    (is (= {"perp:BTC" -0.13}
           (:expected-returns-by-instrument posterior)))))
