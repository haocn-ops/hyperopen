(ns hyperopen.portfolio.optimizer.domain.risk-structure
  "Correlation and contribution-decomposition math for the Equal Risk results
  page (the :risk-structure payload section).

  With covariance Sigma, signed target weights w, m = Sigma*w, q = w'Sigma*w,
  sigma_p = sqrt(q), sigma_i = sqrt(Sigma_ii) and s_i = sign(w_i):

      rho_ij = Sigma_ij / (sigma_i * sigma_j)      underlying-return correlation
      std_i  = w_i^2 * Sigma_ii / q                standalone share (>= 0)
      net_i  = w_i * m_i / q                       relative contribution (RRC)
      div_i  = net_i - std_i                       diversification share
                                                   (= sum_{j/=i} w_i w_j Sigma_ij / q)
      p_i    = s_i * m_i / (sigma_i * sigma_p)     P&L correlation to portfolio
                                                   (= Corr(s_i r_i, r_p))

  net comes from the SAME risk-contributions/contributions call the
  :risk-contributions payload section uses, and div is defined as the residual
  net - std, so std_i + div_i = net_i holds exactly per asset and the shares
  sum exactly across assets. The position-P&L correlation matrix
  (s_i * s_j * rho_ij) is a pure sign flip and is derived at view time — only
  the underlying matrix is computed and persisted here, capped to the largest
  positions (see structure-summary).

  Degeneracy: an asset variance below `asset-variance-tolerance` (or a zero
  weight, for p_i — no position means no P&L stream) yields nil correlation
  entries rather than fabricated numbers; a degenerate portfolio variance
  fails the whole summary explicitly, exactly like the contribution math."
  (:require [hyperopen.portfolio.optimizer.domain.math :as math]
            [hyperopen.portfolio.optimizer.domain.risk-contributions
             :as risk-contributions]))

(def ^:private finite-number? math/finite-number?)

(def correlation-display-cap
  "Instruments kept in the persisted correlation matrix, largest |net share|
  first. Results persist per wallet (IndexedDB) and universes reach 88+
  assets, so the full O(n^2) matrix is deliberately not stored. 24 columns is
  what renders legibly now that the correlation tab gives the heatmap the
  card's FULL width (2026-07-11 per-asset breakdown redesign — the old cap of
  12 was sized for a half-width heatmap and, on an equal-risk book where
  every |net share| is near-equal, dropped an essentially arbitrary subset of
  the held assets)."
  24)

(def asset-variance-tolerance-factor
  "Relative floor for a usable per-asset variance: Sigma_ii must exceed this
  factor times mean|diag(Sigma)| (with an absolute floor of 1e-300) or the
  asset's correlations are reported as nil."
  1e-12)

(defn- clamp-correlation
  [value]
  (when (finite-number? value)
    (-> value (max -1) (min 1))))

(defn- mean-abs-diagonal
  [covariance]
  (let [values (map js/Math.abs (math/diagonal covariance))]
    (if (seq values)
      (/ (reduce + 0 values) (count values))
      0)))

(defn asset-volatilities
  "Per-asset volatility sqrt(Sigma_ii), or nil where the variance is
  nonfinite or degenerate relative to the covariance's own scale."
  [covariance]
  (let [tolerance (max 1e-300 (* asset-variance-tolerance-factor
                                 (mean-abs-diagonal covariance)))]
    (mapv (fn [variance]
            (when (and (finite-number? variance)
                       (>= variance tolerance))
              (js/Math.sqrt variance)))
          (math/diagonal covariance))))

(defn portfolio-diversification-summary
  "Portfolio-level volatility benchmarks for one aligned signed book.

  `:all-move-together-volatility` is sum_i |w_i| sigma_i: the volatility if
  every held position P&L stream moved together. `:zero-correlation-volatility`
  keeps only diagonal covariance. `:modeled-volatility` uses the full
  covariance. The result is deliberately separate from the per-asset signed
  Euler decomposition below: a book can have positive cross-covariance terms
  and still be diversified relative to perfect held-P&L comovement.

  Returns a finite `{:status :ok ...}` scalar map, or
  `{:status :error :reason ...}` for malformed, nonfinite, or degenerate input.
  It never throws on shape/alignment errors."
  [covariance weights]
  (let [weights* (when (sequential? weights) (vec weights))
        n (count weights*)]
    (cond
      (or (zero? n) (not-every? finite-number? weights*))
      {:status :error :reason :weights-shape}

      (not-any? #(not (zero? %)) weights*)
      {:status :error :reason :degenerate-weights}

      :else
      (let [validation (risk-contributions/validate-covariance covariance n)]
        (if-not (= :ok (:status validation))
          validation
          (let [covariance* (:covariance validation)
                diagonal (math/diagonal covariance*)]
            (if (some neg? diagonal)
              {:status :error :reason :negative-variance}
              (let [volatilities (mapv js/Math.sqrt diagonal)
                    all-move-together (reduce + 0 (map (fn [weight sigma]
                                                        (* (js/Math.abs weight)
                                                           sigma))
                                                      weights*
                                                      volatilities))
                    zero-variance (reduce + 0 (map (fn [weight variance]
                                                    (* weight weight variance))
                                                  weights*
                                                  diagonal))
                    modeled-variance (math/portfolio-variance weights*
                                                              covariance*)
                    tolerance (risk-contributions/variance-degeneracy-tolerance
                               covariance* weights*)]
                (cond
                  (or (not (finite-number? all-move-together))
                      (not (finite-number? zero-variance))
                      (not (finite-number? modeled-variance)))
                  {:status :error :reason :nonfinite-summary}

                  (<= all-move-together 0)
                  {:status :error :reason :degenerate-volatility}

                  (< modeled-variance tolerance)
                  {:status :error :reason :degenerate-variance}

                  :else
                  (let [modeled (js/Math.sqrt (max 0 modeled-variance))
                        zero-correlation (js/Math.sqrt (max 0 zero-variance))
                        reduction (- all-move-together modeled)]
                    {:status :ok
                     :modeled-volatility modeled
                     :all-move-together-volatility all-move-together
                     :zero-correlation-volatility zero-correlation
                     :reduction-vs-all-move-together reduction
                     :reduction-ratio-vs-all-move-together
                     (/ reduction all-move-together)
                     :modeled-minus-zero-correlation
                     (- modeled zero-correlation)}))))))))))

(defn correlation-matrix
  "Underlying-return correlation matrix rho_ij = Sigma_ij/(sigma_i*sigma_j),
  clamped to [-1, 1] against float noise, with an exact 1.0 diagonal. Entries
  touching a degenerate-variance asset are nil."
  [covariance]
  (let [volatilities (asset-volatilities covariance)
        n (count covariance)]
    (mapv (fn [row-idx]
            (mapv (fn [col-idx]
                    (let [sigma-row (nth volatilities row-idx)
                          sigma-col (nth volatilities col-idx)]
                      (cond
                        (or (nil? sigma-row) (nil? sigma-col)) nil
                        (= row-idx col-idx) 1.0
                        :else (clamp-correlation
                               (/ (get-in covariance [row-idx col-idx])
                                  (* sigma-row sigma-col))))))
                  (range n)))
          (range n))))

(defn decomposition
  "Standalone/diversification decomposition of the signed relative
  contributions, plus each position's P&L correlation to the portfolio.
  Returns {:status :ok :portfolio-volatility sigma_p :volatilities [...]
           :standalone-shares [...] :diversification-shares [...]
           :net-shares [...] :pnl-portfolio-correlations [... nil-able ...]}
  or the contribution math's explicit :degenerate-variance error."
  [covariance weights]
  (let [result (risk-contributions/contributions covariance weights)]
    (if-not (= :ok (:status result))
      result
      (let [{:keys [m q sigma relative-contributions]} result
            volatilities (asset-volatilities covariance)
            standalone (mapv (fn [weight variance]
                               (/ (* weight weight variance) q))
                             weights
                             (math/diagonal covariance))]
        {:status :ok
         :portfolio-volatility sigma
         :volatilities volatilities
         :standalone-shares standalone
         :diversification-shares (mapv - relative-contributions standalone)
         :net-shares relative-contributions
         :pnl-portfolio-correlations
         (mapv (fn [weight m-i sigma-i]
                 (when (and sigma-i
                            (finite-number? weight)
                            (not (zero? weight)))
                   (clamp-correlation
                    (/ (* (if (neg? weight) -1 1) m-i)
                       (* sigma-i sigma)))))
               weights
               m
               volatilities)}))))

(defn- correlation-section
  "The capped, display-ordered correlation sub-section: candidates are the
  instruments actually holding a position (nonzero weight), the cap keeps the
  largest |net share| ones, and the survivors display in signed net-share
  descending order — the balance chart's silhouette — so the two views
  cross-reference row for row."
  [{:keys [instrument-ids weights net-shares matrix cap]}]
  (let [candidates (->> (range (count instrument-ids))
                        (filter (fn [idx]
                                  (let [weight (nth weights idx)]
                                    (and (finite-number? weight)
                                         (not (zero? weight)))))))
        kept (->> candidates
                  (sort-by (fn [idx]
                             (- (js/Math.abs (or (nth net-shares idx) 0)))))
                  (take cap)
                  (sort-by (fn [idx]
                             (- (or (nth net-shares idx) ##-Inf))))
                  vec)]
    {:instrument-ids (mapv #(nth instrument-ids %) kept)
     :matrix (mapv (fn [row-idx]
                     (mapv (fn [col-idx]
                             (get-in matrix [row-idx col-idx]))
                           kept))
                   kept)
     :hidden-count (- (count candidates) (count kept))}))

(defn structure-summary
  "The :risk-structure result-payload section, computed from the FINAL
  published weights and the SAME covariance as the :risk-contributions
  section (never validated/symmetrized differently, so the two sections'
  shares are bit-identical). Zero-weight instruments keep their (zero)
  decomposition entries but are excluded from the correlation matrix and the
  P&L-correlation map — an unheld asset has no position P&L stream.
  Returns {:status :error ...} on degenerate portfolio variance."
  [{:keys [instrument-ids covariance weights correlation-cap]
    :or {correlation-cap correlation-display-cap}}]
  (let [result (decomposition covariance weights)]
    (if-not (= :ok (:status result))
      result
      (let [{:keys [portfolio-volatility standalone-shares
                    diversification-shares net-shares
                    pnl-portfolio-correlations]} result
            ids (vec instrument-ids)]
        {:status :ok
         :method :signed-euler-decomposition
         :portfolio-volatility portfolio-volatility
         :standalone-share-by-instrument (zipmap ids standalone-shares)
         :diversification-share-by-instrument (zipmap ids diversification-shares)
         :pnl-portfolio-correlation-by-instrument
         (into {}
               (keep (fn [[instrument-id correlation]]
                       (when (some? correlation)
                         [instrument-id correlation])))
               (map vector ids pnl-portfolio-correlations))
         :correlation (correlation-section
                       {:instrument-ids ids
                        :weights (vec weights)
                        :net-shares net-shares
                        :matrix (correlation-matrix covariance)
                        :cap correlation-cap})}))))
