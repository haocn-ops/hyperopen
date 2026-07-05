(ns hyperopen.portfolio.optimizer.domain.history-assumption-proxy-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.domain.history-assumption-proxy :as proxy]
            [hyperopen.portfolio.optimizer.domain.risk :as risk]))

(defn- near?
  ([expected actual] (near? expected actual 1e-9))
  ([expected actual tolerance]
   (and (number? actual)
        (< (js/Math.abs (- expected actual)) tolerance))))

(deftest normalize-prior-weights-normalizes-valid-weights-test
  (let [{:keys [weights fallback?]}
        (proxy/normalize-prior-weights ["perp:BTC" "perp:ETH"]
                                       {"perp:BTC" 2 "perp:ETH" 6})]
    (is (near? 0.25 (get weights "perp:BTC")))
    (is (near? 0.75 (get weights "perp:ETH")))
    (is (false? fallback?))))

(deftest normalize-prior-weights-falls-back-to-equal-weight-test
  (testing "missing prior => equal weight, not flagged as a fallback"
    (let [{:keys [weights fallback?]}
          (proxy/normalize-prior-weights ["perp:BTC" "perp:ETH" "perp:SOL"] nil)]
      (is (every? #(near? (/ 1 3) %) (vals weights)))
      (is (false? fallback?))))
  (testing "invalid prior (negative / zero total / missing id) => equal weight + flag"
    (doseq [bad [{"perp:BTC" -1 "perp:ETH" 2}
                 {"perp:BTC" 0 "perp:ETH" 0}
                 {"perp:BTC" 1}]]
      (let [{:keys [weights fallback?]}
            (proxy/normalize-prior-weights ["perp:BTC" "perp:ETH"] bad)]
        (is (near? 0.5 (get weights "perp:BTC")))
        (is (near? 0.5 (get weights "perp:ETH")))
        (is (true? fallback?)))))
  (testing "no proxies => nil"
    (is (nil? (proxy/normalize-prior-weights [] {"perp:BTC" 1})))))

(deftest confidence-q-stays-low-for-tiny-samples-test
  ;; 10 observations cap q at 10/130 even with a perfect fit.
  (is (near? (/ 10 130) (proxy/confidence-q 10 1.0)))
  (is (> 0.08 (proxy/confidence-q 10 1.0)))
  (is (zero? (proxy/confidence-q 0 0.9)))
  (is (zero? (proxy/confidence-q 10 nil)))
  ;; fit quality scales the sample-side confidence down, never up.
  (is (near? (* (/ 240 360) 0.5) (proxy/confidence-q 240 0.25)))
  (is (near? (/ 240 360) (proxy/confidence-q 240 0.9))))

(deftest blend-exposure-is-prior-at-zero-confidence-test
  (let [prior {"perp:BTC" 0.5 "perp:ETH" 0.5}]
    (is (= prior (proxy/blend-exposure prior {"perp:BTC" 1 "perp:ETH" 0} 0)))
    (is (= prior (proxy/blend-exposure prior nil 0.4)))))

(deftest blend-exposure-shrinks-between-prior-and-regression-test
  (let [prior {"perp:BTC" 0.5 "perp:ETH" 0.5}
        regression {"perp:BTC" 1.0 "perp:ETH" 0.0}
        blended (proxy/blend-exposure prior regression 0.5)]
    (is (near? 0.75 (get blended "perp:BTC")))
    (is (near? 0.25 (get blended "perp:ETH")))
    (is (near? 1.0 (reduce + (vals blended))) "Exposure stays a normalized basket.")))

(defn- regression-series
  "y follows BTC exactly; ETH is noise-free but unrelated."
  [n]
  (let [btc (vec (map-indexed (fn [idx _] (if (even? idx) 0.01 -0.008)) (range n)))
        eth (vec (map-indexed (fn [idx _] (if (zero? (mod idx 3)) 0.004 -0.002)) (range n)))]
    {:x-returns btc
     :proxy-returns-by-id {"perp:BTC" btc
                           "perp:ETH" eth}
     :observations n}))

(deftest estimate-regression-uses-joint-fit-not-r2-weights-test
  (let [result (proxy/estimate-regression
                {:regression-series (regression-series 200)
                 :proxy-ids ["perp:BTC" "perp:ETH"]
                 :prior-weights {"perp:BTC" 0.5 "perp:ETH" 0.5}})]
    (is (= :estimated (:status result)))
    (is (= 200 (:sample-count result)))
    (is (> (:r2 result) 0.9) "y is BTC, so the joint fit is near-perfect.")
    ;; A normalized per-proxy R2 split would put ~everything on BTC; the joint
    ;; ridge keeps a deliberate prior pull on the weak-signal proxy instead.
    (is (> (get-in result [:beta "perp:BTC"]) 0.6)
        "The joint regression tilts toward BTC...")
    (is (< (get-in result [:beta "perp:ETH"]) 0.4)
        "...but never collapses to an R2-as-weights split.")
    (is (> (get-in result [:beta "perp:BTC"])
           (get-in result [:beta "perp:ETH"])))
    (is (near? 1.0 (reduce + (vals (:beta result)))) "V1 policy: basket sums to 1.")))

(deftest estimate-regression-skips-tiny-or-degenerate-overlap-test
  (is (= :no-overlap
         (:reason (proxy/estimate-regression
                   {:regression-series nil
                    :proxy-ids ["perp:BTC"]
                    :prior-weights {"perp:BTC" 1}}))))
  (is (= :insufficient-overlap
         (:reason (proxy/estimate-regression
                   {:regression-series (regression-series 2)
                    :proxy-ids ["perp:BTC" "perp:ETH"]
                    :prior-weights {"perp:BTC" 0.5 "perp:ETH" 0.5}})))))

(deftest specific-variance-is-floored-and-positive-test
  (testing "user variance above systematic: the gap wins"
    (is (near? 0.39 (proxy/specific-variance {:user-variance 0.64
                                              :systematic-variance 0.25
                                              :residual-variance nil
                                              :min-share 0.25}))))
  (testing "systematic near user variance: the floor keeps specific risk nonzero"
    (is (near? 0.16 (proxy/specific-variance {:user-variance 0.64
                                              :systematic-variance 0.6
                                              :residual-variance nil
                                              :min-share 0.25}))))
  (testing "residual variance can lift the floor"
    (is (near? 0.3 (proxy/specific-variance {:user-variance 0.64
                                             :systematic-variance 0.6
                                             :residual-variance 0.3
                                             :min-share 0.25}))))
  (testing "never negative"
    (is (near? 0.0 (proxy/specific-variance {:user-variance 0
                                             :systematic-variance 0.5
                                             :residual-variance nil
                                             :min-share 0})))))

(def ^:private base-risk-result
  ;; BTC/ETH annualized covariance: vols 0.2 / 0.3, correlation 0.5.
  {:model :sample-covariance
   :instrument-ids ["perp:BTC" "perp:ETH"]
   :covariance [[0.04 0.03]
                [0.03 0.09]]})

(def ^:private tokenx-entry
  {:behavior :proxy
   :volatility 0.8
   :max-weight 0.05
   :relationship-strength :medium
   :proxy-instrument-ids ["perp:BTC" "perp:ETH"]
   :proxy-prior-weights nil
   :regression-series nil})

(deftest augment-appends-proxy-row-from-proxy-covariance-test
  (let [augmented (proxy/augment-risk-result-with-proxy-assumptions
                   base-risk-result
                   {"perp:TOKENX" tokenx-entry})
        cov (:covariance augmented)
        diag (get-in augmented [:risk-estimation :history-assumptions "perp:TOKENX"])]
    (is (= ["perp:BTC" "perp:ETH" "perp:TOKENX"] (:instrument-ids augmented)))
    ;; beta = prior = [0.5 0.5] (no overlap, q = 0).
    ;; Cov(BTC, X) = 0.5*0.04 + 0.5*0.03 = 0.035
    ;; Cov(ETH, X) = 0.5*0.03 + 0.5*0.09 = 0.06
    (is (near? 0.035 (get-in cov [0 2]) 1e-6))
    (is (near? 0.06 (get-in cov [1 2]) 1e-6))
    (is (near? (get-in cov [0 2]) (get-in cov [2 0])) "Row/column stay symmetric.")
    (is (pos? (get-in cov [0 2]))
        "Proxy covariance is synthesized from the basket, never zero correlation.")
    ;; systematic = b' Sigma_PP b = 0.25*(0.04 + 0.09) + 2*0.25*0.03 = 0.0475
    ;; specific = max(0.64 - 0.0475, 0.25*0.64) = 0.5925 => Var(X) = 0.64
    (is (near? 0.64 (get-in cov [2 2]) 1e-6)
        "Total variance honors the user's stated volatility when it exceeds systematic.")
    (is (zero? (:confidence-q diag)))
    (is (nil? (:r2 diag)))
    (is (= {"perp:BTC" 0.5 "perp:ETH" 0.5} (:final-beta diag)))
    (is (near? 0.0475 (:systematic-variance diag) 1e-6))
    (is (pos? (:specific-variance diag)))))

(deftest augment-replaces-row-when-asset-already-present-test
  (let [base {:model :sample-covariance
              :instrument-ids ["perp:BTC" "perp:ETH" "perp:TOKENX"]
              :covariance [[0.04 0.03 0.001]
                           [0.03 0.09 0.0005]
                           [0.001 0.0005 0.0002]]}
        augmented (proxy/augment-risk-result-with-proxy-assumptions
                   base
                   {"perp:TOKENX" tokenx-entry})
        cov (:covariance augmented)]
    (is (= ["perp:BTC" "perp:ETH" "perp:TOKENX"] (:instrument-ids augmented))
        "The asset keeps its position; no duplicate row is appended.")
    (is (near? 0.035 (get-in cov [0 2]) 1e-6)
        "The thin realized row is replaced by the synthesized one.")
    (is (near? 0.64 (get-in cov [2 2]) 1e-6))
    (is (near? 0.04 (get-in cov [0 0])) "Other rows are untouched.")))

(deftest augment-uses-regression-when-overlap-exists-test
  (let [entry (assoc tokenx-entry
                     :regression-series (regression-series 200))
        augmented (proxy/augment-risk-result-with-proxy-assumptions
                   base-risk-result
                   {"perp:TOKENX" entry}
                   {:periods-per-year 365})
        diag (get-in augmented [:risk-estimation :history-assumptions "perp:TOKENX"])]
    (is (pos? (:confidence-q diag)))
    (is (> (get-in diag [:final-beta "perp:BTC"]) 0.5)
        "The regression tilts the basket toward the proxy the asset tracks.")
    (is (> (get-in diag [:final-beta "perp:BTC"]) (get-in diag [:final-beta "perp:ETH"])))
    (is (near? 1.0 (reduce + (vals (:final-beta diag))) 1e-6))
    (is (= 200 (:sample-count diag)))
    (is (pos? (:specific-variance diag)))))

(deftest augment-result-is-psd-and-warns-when-basket-exceeds-target-test
  ;; Vol target far below what the basket implies: 10% target vs ~22% basket.
  (let [entry (assoc tokenx-entry :volatility 0.1)
        augmented (proxy/augment-risk-result-with-proxy-assumptions
                   base-risk-result
                   {"perp:TOKENX" entry})
        conditioning (risk/covariance-conditioning (:covariance augmented))]
    (is (not= :not-positive-semidefinite (:status conditioning))
        "The synthesized matrix is PSD (repaired when needed).")
    (is (some #(= :proxy-systematic-variance-exceeds-target (:code %))
              (:warnings augmented))
        "Overshooting the stated volatility warns instead of crashing.")
    (is (>= (get-in augmented [:covariance 2 2])
            (get-in augmented [:risk-estimation :history-assumptions
                               "perp:TOKENX" :systematic-variance]))
        "Total variance is lifted through specific risk, never below systematic.")))

(deftest augment-skips-assets-with-no-usable-proxies-test
  (let [entry (assoc tokenx-entry :proxy-instrument-ids ["perp:GONE"])
        augmented (proxy/augment-risk-result-with-proxy-assumptions
                   base-risk-result
                   {"perp:TOKENX" entry})]
    (is (= ["perp:BTC" "perp:ETH"] (:instrument-ids augmented))
        "No synthetic row without a usable basket.")
    (is (some #(= :proxy-assumption-skipped (:code %)) (:warnings augmented)))))

(deftest augment-renormalizes-over-present-proxies-test
  (let [entry (assoc tokenx-entry
                     :proxy-instrument-ids ["perp:BTC" "perp:ETH" "perp:GONE"])
        augmented (proxy/augment-risk-result-with-proxy-assumptions
                   base-risk-result
                   {"perp:TOKENX" entry})
        diag (get-in augmented [:risk-estimation :history-assumptions "perp:TOKENX"])]
    (is (= {"perp:BTC" 0.5 "perp:ETH" 0.5} (:final-beta diag))
        "The basket renormalizes over the proxies that reached the risk model.")
    (is (some #(= :proxy-asset-unavailable (:code %)) (:warnings augmented)))))

(deftest augment-is-a-noop-without-usable-entries-test
  (is (= base-risk-result
         (proxy/augment-risk-result-with-proxy-assumptions base-risk-result {})))
  (is (= base-risk-result
         (proxy/augment-risk-result-with-proxy-assumptions
          base-risk-result
          {"perp:TOKENX" (assoc tokenx-entry :volatility nil)}))
      "No positive volatility => entry ignored.")
  (is (= base-risk-result
         (proxy/augment-risk-result-with-proxy-assumptions
          base-risk-result
          {"perp:TOKENX" (assoc tokenx-entry :proxy-instrument-ids [])}))
      "No proxies => entry ignored."))
