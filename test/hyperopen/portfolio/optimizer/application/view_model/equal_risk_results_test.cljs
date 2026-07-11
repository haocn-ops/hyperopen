(ns hyperopen.portfolio.optimizer.application.view-model.equal-risk-results-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model.equal-risk-results
             :as equal-risk-results]))

(defn- result-with-shares
  "Solved-result skeleton for n assets with the given signed shares."
  [shares & {:keys [current-shares solver diagnostics]}]
  (let [n (count shares)
        ids (mapv #(str "perp:A" %) (range n))
        targets (vec (repeat n (/ 1 n)))]
    (cond-> {:risk-contributions
             {:instrument-ids ids
              :relative-contributions (vec shares)
              :target-relative-contributions targets
              :rms-error 0.01
              :max-absolute-error 0.02
              :negative-contribution-count (count (filter neg? shares))
              :quality :approximate}
             :target-weights-by-instrument (zipmap ids (repeat 0.1))
             :labels-by-instrument (zipmap ids ids)}
      current-shares
      (assoc :current-risk-contributions
             {:relative-contributions-by-instrument (zipmap ids current-shares)
              :rms-error 0.2
              :max-absolute-error 0.3})

      solver (assoc :equal-risk-solver solver)
      diagnostics (assoc :diagnostics diagnostics))))

(deftest balance-model-sorts-by-deviation-and-caps-rows-test
  (testing "rows are worst-deviation-first with current shares attached"
    (let [model (equal-risk-results/balance-model
                 (result-with-shares [0.5 0.1 0.4]
                                     :current-shares [0.9 0.05 0.05]))]
      (is (= ["perp:A1" "perp:A0" "perp:A2"]
             (mapv :instrument-id (:rows model)))
          "|0.1-1/3| > |0.5-1/3| > |0.4-1/3|")
      (is (= 0.9 (:current-share (first (filter #(= "perp:A0" (:instrument-id %))
                                                (:rows model))))))
      (is (zero? (:hidden-count model)))))
  (testing "large universes cap at 16 rows with an honest remainder"
    (let [shares (into [0.9] (repeat 24 (/ 0.1 24)))
          model (equal-risk-results/balance-model (result-with-shares shares))]
      (is (= equal-risk-results/display-row-cap (count (:rows model))))
      (is (= (- 25 equal-risk-results/display-row-cap) (:hidden-count model)))
      (is (number? (:hidden-max-pts model)))))
  (testing "negative shares flag as hedges"
    (let [model (equal-risk-results/balance-model
                 (result-with-shares [1.1 -0.1]))]
      (is (true? (:negative? (first (filter #(= "perp:A1" (:instrument-id %))
                                            (:rows model)))))))))

(deftest kpi-risk-balance-reads-current-and-target-deviations-test
  (let [with-current (equal-risk-results/kpi-risk-balance
                      (result-with-shares [0.5 0.5] :current-shares [0.9 0.1]))
        without-current (equal-risk-results/kpi-risk-balance
                         (result-with-shares [0.5 0.5]))]
    (is (= 2.0 (:target-max-pts with-current)))
    (is (= 30.0 (:current-max-pts with-current)))
    (is (= -28.0 (:delta-pts with-current)))
    (is (nil? (:current-max-pts without-current)))
    (is (nil? (:delta-pts without-current)))))

(deftest freedom-view-labels-every-status-test
  (is (= "Fully determined"
         (:label (equal-risk-results/freedom-view {:status :fully-determined}))))
  (is (str/includes? (:detail (equal-risk-results/freedom-view
                               {:status :limited :binding-count 2}))
                     "2 binding caps"))
  (is (= :ok (:status (equal-risk-results/freedom-view {:status :open}))))
  (testing "persisted pre-redesign results degrade honestly"
    (is (str/includes? (:detail (equal-risk-results/freedom-view nil))
                       "Not recorded"))))

(deftest solution-stability-classifies-initialization-agreement-test
  (let [stability #(equal-risk-results/solution-stability
                    {:equal-risk-solver {:initializations %}})]
    (is (= :unknown (:status (stability nil))))
    (is (= "Single start"
           (:label (stability [{:status :completed :objective 1e-4}]))))
    (is (= "High"
           (:label (stability [{:status :completed :objective 1.00e-4}
                               {:status :completed :objective 1.02e-4}]))))
    (is (= :caution
           (:status (stability [{:status :completed :objective 1e-4}
                                {:status :completed :objective 9e-3}]))))))

(deftest stop-reason-maps-solver-terminations-test
  (is (= "Projected step tolerance reached"
         (:label (equal-risk-results/stop-reason
                  {:equal-risk-solver {:termination-reason :step-tolerance}}))))
  (is (= "Iteration limit reached"
         (:label (equal-risk-results/stop-reason
                  {:equal-risk-solver {:termination-reason :max-iterations}}))))
  (testing "fully-determined overrides with the constraint explanation"
    (is (= "Allocation fully determined by constraints"
           (:label (equal-risk-results/stop-reason
                    {:equal-risk-solver
                     {:termination-reason :step-tolerance
                      :allocation-freedom {:status :fully-determined}}}))))))

(deftest verdict-body-branches-on-allocation-freedom-test
  (let [base (result-with-shares [0.5 0.5]
                                 :diagnostics {:gross-exposure 2.0
                                               :net-exposure -0.5})]
    (testing "open books get the balance sentence"
      (let [body (equal-risk-results/verdict-body base)]
        (is (str/includes? body "Balances 2 selected positions toward 50.0%"))
        (is (str/includes? body "2.00x gross"))
        (is (str/includes? body "-0.50x net"))
        (is (str/includes? body "Max contribution deviation 2.0 pts"))))
    (testing "limited books name the binding caps"
      (let [body (equal-risk-results/verdict-body
                  (assoc-in base [:equal-risk-solver :allocation-freedom]
                            {:status :limited :binding-count 2}))]
        (is (str/includes? body "2 binding caps limit exact equality"))))
    (testing "fully-determined books say the optimizer could not choose"
      (let [body (equal-risk-results/verdict-body
                  (assoc-in base [:equal-risk-solver :allocation-freedom]
                            {:status :fully-determined}))]
        (is (str/includes? body "fully determine"))
        (is (str/includes? body "cannot improve it"))))))
