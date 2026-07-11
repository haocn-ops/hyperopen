(ns hyperopen.portfolio.optimizer.domain.risk-contributions
  "Signed Euler risk-contribution math for the Equal Risk objective.

  With covariance Sigma, signed weights w, m = Sigma*w, q = w'Sigma*w and
  sigma = sqrt(q):

      RC_i  = w_i * m_i / sigma     (volatility contribution; sums to sigma)
      RRC_i = w_i * m_i / q         (relative contribution; sums to 1)

  Contributions are SIGNED: a hedging position may contribute negatively and
  is reported as such — no absolute-value transformation anywhere. The Equal
  Risk objective minimizes the dispersion of RRC around the uniform target
  b_i = 1/n:

      F(w)  = 0.5 * mean_i((RRC_i - b_i)^2)

  and its analytic gradient (u_i = w_i*m_i, e_i = RRC_i - b_i, Sigma symmetric):

      dRRC_i/dw_j = (delta_ij*m_i + w_i*Sigma_ij)/q - 2*u_i*m_j/q^2
      dF/dw_j     = (1/(n*q)) * (e_j*m_j + (Sigma*(e.*w))_j - (2*(e.u)/q)*m_j)

  Both are exactly invariant under positive rescaling of Sigma (m and q scale
  together), which the covariance-scale-invariance test relies on.

  Degeneracy: when q is nonfinite, nonpositive, or too small for a stable
  normalization (below `variance-degeneracy-factor * mean|diag| * ||w||^2`),
  evaluation fails explicitly with :degenerate-variance rather than dividing
  by an arbitrary epsilon."
  (:require [hyperopen.portfolio.optimizer.domain.math :as math]))

(def variance-degeneracy-factor
  "Relative floor for the portfolio variance q used to normalize contributions:
  q must exceed this factor times mean|diag(Sigma)| times ||w||^2 (with an
  absolute floor of 1e-300 to survive an all-zero covariance)."
  1e-12)

(def covariance-asymmetry-tolerance
  "Maximum relative |Sigma_ij - Sigma_ji| treated as floating-point noise and
  symmetrized away; anything larger is materially invalid input and rejected."
  1e-8)

(def ^:private finite-number? math/finite-number?)

(defn- finite-square-matrix?
  [covariance n]
  (and (vector? covariance)
       (= n (count covariance))
       (every? (fn [row]
                 (and (sequential? row)
                      (= n (count row))
                      (every? finite-number? row)))
               covariance)))

(defn- max-abs-entry
  [covariance]
  (reduce (fn [acc row]
            (reduce (fn [acc* value]
                      (max acc* (js/Math.abs value)))
                    acc
                    row))
          0
          covariance))

(defn- max-asymmetry
  [covariance]
  (let [n (count covariance)]
    (reduce (fn [acc [row col]]
              (max acc (js/Math.abs (- (get-in covariance [row col])
                                       (get-in covariance [col row])))))
            0
            (for [row (range n)
                  col (range (inc row) n)]
              [row col]))))

(defn- symmetrize
  [covariance]
  (mapv (fn [row-idx row]
          (mapv (fn [col-idx value]
                  (* 0.5 (+ value (get-in covariance [col-idx row-idx]))))
                (range)
                row))
        (range)
        covariance))

(defn validate-covariance
  "Validates the covariance for contribution math: finite, square, aligned to
  `n` assets, and symmetric up to floating-point noise (which is symmetrized).
  Materially asymmetric input is rejected, never silently repaired."
  [covariance n]
  (cond
    (not (finite-square-matrix? (vec (map vec covariance)) n))
    {:status :error
     :reason :covariance-shape
     :details {:expected-dimension n}}

    :else
    (let [covariance* (mapv vec covariance)
          scale (max (max-abs-entry covariance*) 1e-300)
          asymmetry (max-asymmetry covariance*)]
      (if (> asymmetry (* covariance-asymmetry-tolerance scale))
        {:status :error
         :reason :covariance-asymmetric
         :details {:max-asymmetry asymmetry
                   :relative-tolerance covariance-asymmetry-tolerance}}
        {:status :ok
         :covariance (symmetrize covariance*)}))))

(defn- mean-abs-diagonal
  [covariance]
  (let [values (map js/Math.abs (math/diagonal covariance))]
    (if (seq values)
      (/ (reduce + 0 values) (count values))
      0)))

(defn variance-degeneracy-tolerance
  "The q floor below which normalizing by portfolio variance is unstable,
  scaled to the covariance magnitude and the portfolio size."
  [covariance weights]
  (max 1e-300
       (* variance-degeneracy-factor
          (mean-abs-diagonal covariance)
          (math/dot weights weights))))

(defn contributions
  "Signed Euler contributions of `weights` under (symmetric) `covariance`.
  Returns {:status :ok :m m :q q :sigma sigma
           :variance-contributions u :volatility-contributions rc
           :relative-contributions rrc}
  or {:status :error :reason :degenerate-variance ...} when q is nonfinite,
  nonpositive beyond tolerance, or too close to zero to normalize."
  [covariance weights]
  (let [m (math/mat-vec covariance weights)
        q (math/dot weights m)
        tolerance (variance-degeneracy-tolerance covariance weights)]
    (if (or (not (finite-number? q))
            (< q tolerance))
      {:status :error
       :reason :degenerate-variance
       :details {:q q :tolerance tolerance}}
      (let [sigma (js/Math.sqrt q)
            u (mapv * weights m)]
        {:status :ok
         :m m
         :q q
         :sigma sigma
         :variance-contributions u
         :volatility-contributions (mapv #(/ % sigma) u)
         :relative-contributions (mapv #(/ % q) u)}))))

(defn objective-value
  "F = 0.5 * mean((RRC_i - target_i)^2). Dimensionless dispersion of the
  relative contributions around their targets."
  [relative-contributions targets]
  (let [n (count relative-contributions)
        sum-squares (reduce + 0
                            (map (fn [rrc target]
                                   (let [e (- rrc target)]
                                     (* e e)))
                                 relative-contributions
                                 targets))]
    (if (pos? n)
      (/ sum-squares (* 2 n))
      0)))

(defn objective-gradient
  "Analytic gradient of the objective at `weights`, given the already-computed
  `contributions` moments (see docstring formula). O(n^2): one mat-vec."
  [covariance weights {:keys [m q variance-contributions relative-contributions]} targets]
  (let [n (count weights)
        e (mapv - relative-contributions targets)
        s (math/dot e variance-contributions)
        ew (mapv * e weights)
        sigma-ew (math/mat-vec covariance ew)
        scale (/ 1 (* n q))
        two-s-over-q (/ (* 2 s) q)]
    (mapv (fn [e-j m-j sigma-ew-j]
            (* scale
               (+ (* e-j m-j)
                  sigma-ew-j
                  (- (* two-s-over-q m-j)))))
          e
          m
          sigma-ew)))

(defn evaluate
  "Objective value + gradient + contributions at `weights`, or the explicit
  degenerate-variance error."
  [covariance weights targets]
  (let [result (contributions covariance weights)]
    (if (= :ok (:status result))
      (assoc result
             :objective (objective-value (:relative-contributions result) targets)
             :gradient (objective-gradient covariance weights result targets))
      result)))

(defn- rms
  [values]
  (if (seq values)
    (js/Math.sqrt (/ (reduce + 0 (map #(* % %) values))
                     (count values)))
    0))

(defn contribution-summary
  "The :risk-contributions result-payload section, computed from the FINAL
  published weights (never a pre-cleanup solver candidate). Quality
  classification is folded in by the caller (it also depends on solver
  convergence). Returns {:status :error ...} on degenerate variance."
  [{:keys [instrument-ids covariance weights targets]}]
  (let [result (contributions covariance weights)]
    (if-not (= :ok (:status result))
      result
    (let [{:keys [variance-contributions
                  volatility-contributions
                  relative-contributions]} result
          errors (mapv - relative-contributions targets)]
      {:status :ok
       :method :signed-euler-volatility
       :instrument-ids (vec instrument-ids)
       :variance-contributions variance-contributions
       :volatility-contributions volatility-contributions
       :relative-contributions relative-contributions
       :target-relative-contributions (vec targets)
       :relative-contributions-by-instrument (zipmap instrument-ids
                                                     relative-contributions)
       :target-relative-contributions-by-instrument (zipmap instrument-ids
                                                            targets)
       :sum-relative-contributions (reduce + 0 relative-contributions)
       :rms-error (rms errors)
       :max-absolute-error (reduce max 0 (map js/Math.abs errors))
       :negative-contribution-count (count (filter neg? relative-contributions))}))))
