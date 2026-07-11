(ns hyperopen.portfolio.optimizer.domain.equal-risk-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.domain.constraints :as constraints]
            [hyperopen.portfolio.optimizer.domain.equal-risk :as equal-risk]
            [hyperopen.portfolio.optimizer.domain.equal-risk-plan :as equal-risk-plan]
            [hyperopen.portfolio.optimizer.domain.equal-risk-presolve :as equal-risk-presolve]))

(defn- near?
  ([expected actual] (near? expected actual 1e-9))
  ([expected actual tolerance]
   (and (number? actual)
        (< (js/Math.abs (- expected actual)) tolerance))))

(defn- perp
  [id side & {:as extra}]
  (merge {:instrument-id id
          :market-type :perp
          :coin id
          :shortable? true
          :position-side side}
         extra))

(defn- encoded
  ([universe engine-constraints]
   (encoded universe engine-constraints {}))
  ([universe engine-constraints current-weights]
   (constraints/encode-constraints
    {:universe universe
     :current-weights current-weights
     :constraints engine-constraints})))

(def ^:private diag-covariance
  [[0.01 0.0]
   [0.0 0.04]])

(defn- violation-codes
  [presolve-result]
  (set (map :code (:violations presolve-result))))

(deftest exposure-targets-follow-canonical-policy-midpoints-test
  (testing "ceiling-only gross (no floor): target = gross-leverage"
    (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :long)]
                       {:gross-leverage 1.0
                        :net-exposure {:min 1.0 :max 1.0}})]
      (is (= {:gross 1.0 :net 1.0} (equal-risk/exposure-targets enc)))))
  (testing "banded gross: target = midpoint of floor and ceiling, NOT the ceiling"
    (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :short)]
                       {:gross-leverage 3.0
                        :gross-floor 1.0
                        :net-exposure {:min -0.5 :max 1.5}})]
      (is (= {:gross 2.0 :net 0.5} (equal-risk/exposure-targets enc)))))
  (testing "long-only pins G = N = 1"
    (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :long)]
                       {:long-only? true
                        :gross-leverage 2.0
                        :net-exposure {:min 0.0 :max 2.0}})]
      (is (= {:gross 1 :net 1} (equal-risk/exposure-targets enc))))))

(deftest book-split-follows-encoded-bounds-test
  (let [enc (encoded [(perp "perp:A" :long)
                      (perp "perp:B" :short)
                      ;; No side, shortable => two-sided bounds.
                      (dissoc (perp "perp:C" :long) :position-side)]
                     {:gross-leverage 2.0
                      :net-exposure {:min 0.0 :max 0.0}})
        books (equal-risk/book-split enc)]
    (is (= [0] (:long books)))
    (is (= [1] (:short books)))
    (is (= [2] (:two-sided books)))))

(deftest presolve-accepts-feasible-mixed-books-test
  (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :short)]
                     {:gross-leverage 2.0
                      :net-exposure {:min 0.0 :max 0.0}
                      :max-asset-weight 1.5})
        result (equal-risk-presolve/presolve enc diag-covariance)]
    (is (= :ok (:status result)))
    (is (near? 1.0 (get-in result [:targets :long-gross])))
    (is (near? 1.0 (get-in result [:targets :short-gross])))
    (is (= {:long [0] :short [1]} (:books result)))))

(deftest presolve-rejects-net-target-exceeding-gross-target-test
  ;; Banded gross keeps this out of the base encoder's reach: gross-max 3
  ;; satisfies |net| <= gross-max, but the TARGETS are gross 2 / net 2.5.
  (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :short)]
                     {:gross-leverage 3.0
                      :gross-floor 1.0
                      :net-exposure {:min 2.5 :max 2.5}
                      :max-asset-weight 3.0})
        result (equal-risk-presolve/presolve enc diag-covariance)]
    (is (= :infeasible (:status result)))
    (is (contains? (violation-codes result) :equal-risk-net-exceeds-gross))))

(deftest presolve-rejects-missing-or-nonpositive-gross-target-test
  (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :long)]
                     {:net-exposure {:min 0.0 :max 0.0}})
        result (equal-risk-presolve/presolve enc diag-covariance)]
    (is (= :infeasible (:status result)))
    (is (contains? (violation-codes result) :equal-risk-gross-target-not-positive))))

(deftest presolve-rejects-empty-books-with-positive-book-target-test
  (testing "positive short gross with no short assets"
    (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :long)]
                       {:gross-leverage 2.0
                        :net-exposure {:min 0.0 :max 0.0}
                        :max-asset-weight 2.0})
          result (equal-risk-presolve/presolve enc diag-covariance)]
      (is (= :infeasible (:status result)))
      (is (contains? (violation-codes result) :equal-risk-short-book-empty))))
  (testing "positive long gross with no long assets"
    (let [enc (encoded [(perp "perp:A" :short) (perp "perp:B" :short)]
                       {:gross-leverage 2.0
                        :net-exposure {:min 0.0 :max 0.0}
                        :max-asset-weight 2.0})
          result (equal-risk-presolve/presolve enc diag-covariance)]
      (is (= :infeasible (:status result)))
      (is (contains? (violation-codes result) :equal-risk-long-book-empty)))))

(deftest presolve-rejects-book-capacity-below-target-test
  ;; Long cap 0.4 cannot reach the 1.0 long budget of G=2, N=0, while the
  ;; short side keeps the base net-window checks satisfied.
  (let [enc (encoded [(perp "perp:A" :long)
                      (perp "perp:B" :short)]
                     {:gross-leverage 2.0
                      :net-exposure {:min 0.0 :max 0.0}
                      :per-asset-overrides {"perp:A" {:max-weight 0.4}
                                            "perp:B" {:max-weight 2.0}}})
        result (equal-risk-presolve/presolve enc diag-covariance)]
    (is (= :infeasible (:status result)))
    (is (contains? (violation-codes result)
                   :equal-risk-long-book-capacity-below-target))))

(deftest presolve-rejects-locked-minimum-above-book-target-test
  ;; A 0.9 lock exceeds the 0.8 long budget of G=1.6, N=0 while overall net
  ;; stays feasible for the base encoder.
  (let [enc (encoded [(perp "perp:A" :long)
                      (perp "perp:B" :long)
                      (perp "perp:C" :short)]
                     {:gross-leverage 1.6
                      :net-exposure {:min 0.0 :max 0.0}
                      :held-position-locks ["perp:A"]
                      :max-asset-weight 1.0}
                     {"perp:A" 0.9})
        result (equal-risk-presolve/presolve enc [[0.01 0.0 0.0]
                                         [0.0 0.04 0.0]
                                         [0.0 0.0 0.02]])]
    (is (= :infeasible (:status result)))
    (is (contains? (violation-codes result)
                   :equal-risk-long-book-minimum-above-target))))

(deftest presolve-rejects-two-sided-assets-test
  (let [enc (encoded [(perp "perp:A" :long)
                      (dissoc (perp "perp:B" :long) :position-side)]
                     {:gross-leverage 1.0
                      :net-exposure {:min 1.0 :max 1.0}})
        result (equal-risk-presolve/presolve enc diag-covariance)]
    (is (= :infeasible (:status result)))
    (let [violation (first (filter #(= :equal-risk-requires-fixed-sides (:code %))
                                   (:violations result)))]
      (is (some? violation))
      (is (= ["perp:B"] (:instrument-ids violation))))))

(deftest presolve-rejects-non-shortable-short-request-test
  ;; The bounds encoder silently long-flips a non-shortable short request;
  ;; Equal Risk must surface that instead of quietly changing the side.
  (let [enc (encoded [(perp "perp:A" :long)
                      (perp "perp:B" :short :shortable? false)]
                     {:gross-leverage 1.0
                      :net-exposure {:min 1.0 :max 1.0}})
        result (equal-risk-presolve/presolve enc diag-covariance)]
    (is (= :infeasible (:status result)))
    (let [violation (first (filter #(= :equal-risk-short-not-shortable (:code %))
                                   (:violations result)))]
      (is (some? violation))
      (is (= ["perp:B"] (:instrument-ids violation))))))

(deftest presolve-rejects-invalid-covariance-test
  (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :long)]
                     {:gross-leverage 1.0
                      :net-exposure {:min 1.0 :max 1.0}})]
    (testing "misaligned covariance"
      (let [result (equal-risk-presolve/presolve enc [[0.01]])]
        (is (= :infeasible (:status result)))
        (is (contains? (violation-codes result) :equal-risk-covariance-shape))))
    (testing "materially asymmetric covariance"
      (let [result (equal-risk-presolve/presolve enc [[0.01 0.005] [0.001 0.04]])]
        (is (= :infeasible (:status result)))
        (is (contains? (violation-codes result) :equal-risk-covariance-asymmetric))))))

(deftest project-book-magnitudes-hits-target-within-bounds-test
  (let [projected (equal-risk/project-book-magnitudes
                   [0.5 0.5 0.5]
                   [0.0 0.0 0.0]
                   [0.4 1.0 1.0]
                   1.2)]
    (is (near? 1.2 (reduce + 0 projected) 1e-9))
    (is (<= (nth projected 0) (+ 0.4 1e-12)))
    (doseq [value projected]
      (is (>= value 0)))))

(deftest seed-weights-hit-book-targets-and-respect-locks-test
  (let [enc (encoded [(perp "perp:A" :long)
                      (perp "perp:B" :long)
                      (perp "perp:C" :short)]
                     {:gross-leverage 2.0
                      :net-exposure {:min 0.5 :max 0.5}
                      :held-position-locks ["perp:A"]
                      :max-asset-weight 1.5}
                     {"perp:A" 0.25})
        presolve-result (equal-risk-presolve/presolve enc [[0.01 0.0 0.0]
                                                  [0.0 0.04 0.0]
                                                  [0.0 0.0 0.02]])
        {:keys [books targets]} presolve-result]
    (is (= :ok (:status presolve-result)))
    (doseq [kind equal-risk/seed-kinds]
      (let [seed (equal-risk/seed-weights kind enc books targets
                                          (:covariance presolve-result))]
        (is (some? seed) (str kind " seed should exist"))
        (is (near? 1.25 (reduce + 0 (map #(nth seed %) (:long books))) 1e-6)
            (str kind " long book"))
        (is (near? 0.75 (- (reduce + 0 (map #(nth seed %) (:short books)))) 1e-6)
            (str kind " short book"))
        (is (near? 0.25 (nth seed 0) 1e-9) (str kind " lock preserved"))))))

(deftest build-plan-produces-sequential-strategy-with-full-validation-rows-test
  (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :short)]
                     {:gross-leverage 2.0
                      :net-exposure {:min 0.0 :max 0.0}
                      :max-asset-weight 1.5
                      :max-turnover 0.5})
        plan (equal-risk-plan/build-plan {:instrument-ids ["perp:A" "perp:B"]
                                     :covariance [[0.04 0.02] [0.02 0.04]]
                                     :encoded-constraints enc})
        problem (first (:problems plan))]
    (is (= :ok (:status plan)))
    (is (= :sequential-equal-risk (:strategy plan)))
    (is (= 1 (count (:problems plan))))
    (is (= :sequential-equal-risk (:kind problem)))
    (is (= :equal-risk (:objective-kind problem)))
    ;; Validation rows: both books + net + signed gross equalities.
    (is (= #{:equal-risk-long-book :equal-risk-short-book :net-exposure :gross-target}
           (set (map :code (:equalities problem)))))
    ;; L1 rows: the (redundant but validated) gross cap and the 2x turnover
    ;; budget with the existing one-sided convention.
    (is (= #{:gross-exposure :turnover}
           (set (map :code (:l1-constraints problem)))))
    (is (near? 1.0 (:max (first (filter #(= :turnover (:code %))
                                        (:l1-constraints problem))))))
    (is (= [(/ 1 2) (/ 1 2)]
           (get-in problem [:targets :relative-contributions])))))

(deftest build-plan-surfaces-presolve-violations-test
  (let [enc (encoded [(perp "perp:A" :long) (perp "perp:B" :long)]
                     {:gross-leverage 2.0
                      :net-exposure {:min 0.0 :max 0.0}
                      :max-asset-weight 2.0})
        plan (equal-risk-plan/build-plan {:instrument-ids ["perp:A" "perp:B"]
                                     :covariance diag-covariance
                                     :encoded-constraints enc})]
    (is (= :infeasible (:status plan)))
    (is (= :equal-risk-presolve (:reason plan)))
    (is (some #(= :equal-risk-short-book-empty (:code %))
              (get-in plan [:details :violations])))))

(deftest classify-quality-is-truthful-test
  (let [n 4
        tolerance (equal-risk/exactness-tolerance n)]
    (is (= :exact (equal-risk/classify-quality
                   {:max-absolute-error (* 0.5 tolerance)
                    :rms-error (* 0.5 tolerance)}
                   n true)))
    (is (= :approximate (equal-risk/classify-quality
                         {:max-absolute-error (* 10 tolerance)
                          :rms-error (* 5 tolerance)}
                         n true)))
    ;; Non-converged never claims exactness, even at tiny realized error.
    (is (= :not-converged (equal-risk/classify-quality
                           {:max-absolute-error 0.0 :rms-error 0.0}
                           n false)))))

(deftest bfgs-update-keeps-positive-definiteness-signals-test
  (let [b (equal-risk/identity-hessian 2)
        s [0.1 -0.05]
        y [0.2 -0.02]
        updated (equal-risk/bfgs-update b s y)]
    ;; s'Bs > 0 after update for a couple of probe directions.
    (doseq [probe [[1 0] [0 1] [1 1] [1 -1]]]
      (let [bp (mapv (fn [row] (reduce + 0 (map * row probe))) updated)]
        (is (pos? (reduce + 0 (map * probe bp))))))
    ;; Invalid curvature leaves B unchanged.
    (is (= b (equal-risk/bfgs-update b [0 0] [0 0])))))
