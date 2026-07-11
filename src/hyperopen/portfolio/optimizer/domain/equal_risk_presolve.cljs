(ns hyperopen.portfolio.optimizer.domain.equal-risk-presolve
  "Explicit feasibility screening for the Equal Risk (:equal-risk) objective,
  run BEFORE the nonlinear solver: exposure-target sanity (finite, positive
  gross, |net| <= gross, targets inside the encoded band/floor/ceiling), fixed
  sides (no two-sided bounds, no silently long-flipped shorts), per-book
  minimum/capacity against the implied long/short budgets, and covariance
  shape/symmetry. Every violation carries a specific :equal-risk-* code plus a
  user-facing :message rendered by the infeasible banner. Book/target
  primitives and tolerances live in domain.equal-risk."
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.domain.equal-risk :as equal-risk]
            [hyperopen.portfolio.optimizer.domain.risk-contributions :as risk-contributions]))

(def ^:private finite-number? coercion/finite-number?)

(def supported-l1-codes
  #{:gross-exposure :turnover})

(defn- fmt
  [value]
  (if (finite-number? value)
    (let [rounded (/ (js/Math.round (* 100 value)) 100)]
      (str rounded "x"))
    "?"))

(defn- book-capacity
  [indexes lower-bounds upper-bounds book]
  (reduce (fn [acc idx]
            (let [[magnitude-lower magnitude-upper]
                  (equal-risk/magnitude-bounds book
                                               (nth lower-bounds idx)
                                               (nth upper-bounds idx))]
              (-> acc
                  (update :minimum + magnitude-lower)
                  (update :capacity + magnitude-upper))))
          {:minimum 0 :capacity 0}
          indexes))

(defn- side-violations
  [{:keys [instrument-ids side-metadata]} books]
  (let [two-sided (seq (:two-sided books))
        flipped-shorts (->> side-metadata
                            (keep-indexed (fn [idx {:keys [requested-side shortable?]}]
                                            (when (and (= :short requested-side)
                                                       (not shortable?))
                                              (nth instrument-ids idx))))
                            seq)]
    (cond-> []
      two-sided
      (conj {:code :equal-risk-requires-fixed-sides
             :instrument-ids (mapv #(nth instrument-ids %) (:two-sided books))
             :message (str "Equal Risk needs every asset fixed to one side, but "
                           (count (:two-sided books))
                           (if (= 1 (count (:two-sided books)))
                             " asset can"
                             " assets can")
                           " still take either side. Set each asset Long or Short.")})

      flipped-shorts
      (conj {:code :equal-risk-short-not-shortable
             :instrument-ids (vec flipped-shorts)
             :message (str (count flipped-shorts)
                           (if (= 1 (count flipped-shorts))
                             " asset is set Short but cannot legally be shorted."
                             " assets are set Short but cannot legally be shorted."))}))))

(defn- book-violations
  [book-key indexes lower-bounds upper-bounds book-target tolerance]
  (let [{:keys [minimum capacity]} (book-capacity indexes lower-bounds upper-bounds book-key)
        side-label (case book-key :long "long" "short")
        empty-code (case book-key
                     :long :equal-risk-long-book-empty
                     :equal-risk-short-book-empty)
        minimum-code (case book-key
                       :long :equal-risk-long-book-minimum-above-target
                       :equal-risk-short-book-minimum-above-target)
        capacity-code (case book-key
                        :long :equal-risk-long-book-capacity-below-target
                        :equal-risk-short-book-capacity-below-target)]
    (cond-> []
      (and (> book-target tolerance) (empty? indexes))
      (conj {:code empty-code
             :target book-target
             :message (str "Your gross/net targets imply " (fmt book-target) " "
                           side-label " exposure, but no asset is set "
                           (if (= :long book-key) "Long" "Short")
                           ". Adjust sides or the exposure targets.")})

      (and (seq indexes) (> minimum (+ book-target tolerance)))
      (conj {:code minimum-code
             :minimum minimum
             :target book-target
             :message (str "Locked or minimum " side-label " positions total "
                           (fmt minimum) ", above the " (fmt book-target) " "
                           side-label " budget your gross/net targets imply.")})

      (and (seq indexes) (< capacity (- book-target tolerance)))
      (conj {:code capacity-code
             :capacity capacity
             :target book-target
             :message (str "Per-asset caps allow at most " (fmt capacity) " "
                           side-label " exposure, below the " (fmt book-target)
                           " your gross/net targets imply. Raise caps or adjust targets.")}))))

(defn- exposure-violations
  [encoded-constraints gross net tolerance]
  (let [gross-max (get-in encoded-constraints [:gross-exposure :max])
        gross-floor (get-in encoded-constraints [:gross-floor :min])
        net-min (get-in encoded-constraints [:net-exposure :min])
        net-max (get-in encoded-constraints [:net-exposure :max])
        long-only? (:long-only? encoded-constraints)]
    (cond-> []
      (or (not (finite-number? gross)) (not (finite-number? net)))
      (conj {:code :equal-risk-invalid-exposure-targets :gross gross :net net
             :message "Equal Risk needs finite gross and net exposure targets."})

      (and (finite-number? gross) (<= gross tolerance))
      (conj {:code :equal-risk-gross-target-not-positive :gross gross
             :message "Equal Risk needs a positive gross leverage target."})

      (and (finite-number? gross) (finite-number? net)
           (> (js/Math.abs net) (+ gross tolerance)))
      (conj {:code :equal-risk-net-exceeds-gross :gross gross :net net
             :message (str "The net bias target (" (fmt net)
                           ") exceeds the gross leverage target (" (fmt gross)
                           "); |net| must stay within gross.")})

      (and (finite-number? gross) (finite-number? gross-max)
           (> gross (+ gross-max tolerance)))
      (conj {:code :equal-risk-gross-target-above-max :gross gross :gross-max gross-max
             :message (str "The gross target " (fmt gross)
                           " sits above the gross ceiling " (fmt gross-max) ".")})

      (and (finite-number? gross) (finite-number? gross-floor)
           (< gross (- gross-floor tolerance)))
      (conj {:code :equal-risk-gross-target-below-floor :gross gross :gross-floor gross-floor
             :message (str "The gross target " (fmt gross)
                           " sits below the gross floor " (fmt gross-floor) ".")})

      (and (not long-only?) (finite-number? net)
           (or (and (finite-number? net-min) (< net (- net-min tolerance)))
               (and (finite-number? net-max) (> net (+ net-max tolerance)))))
      (conj {:code :equal-risk-net-target-outside-band
             :net net :net-min net-min :net-max net-max
             :message (str "The net target " (fmt net)
                           " falls outside the allowed net band.")}))))

(defn- unsupported-constraint-violations
  "Every hard channel the constraint encoder can currently produce (bounds,
  locks, book equalities, net band, gross floor/cap, turnover) is supported by
  the sequential solver, so this returns []. The seam exists so a FUTURE
  encoded channel gets an explicit :equal-risk-unsupported-constraint
  violation here instead of being silently dropped."
  [_encoded-constraints]
  [])

(def lopsided-book-ratio
  "A book whose budget falls below this fraction of gross is starved: with
  several assets forced to share almost nothing, Equal Risk must concentrate
  risk in the other book and the run will land far from parity. Warn — the
  request is still mathematically valid, so never block."
  0.05)

(defn- lopsided-book-warnings
  "Non-blocking warnings for feasible-but-degenerate exposure targets:
  min(G_long, G_short) is tiny relative to gross while MULTIPLE assets sit on
  the starved side (a deliberately one-sided single-asset book is not
  surprising the way four shorts sharing 0.02x is)."
  [books gross long-gross short-gross]
  (if-not (and (finite-number? gross) (pos? gross))
    []
    (->> [[:long long-gross (count (:long books))]
          [:short short-gross (count (:short books))]]
         (keep (fn [[book budget asset-count]]
                 (when (and (> asset-count 1)
                            (< budget (* lopsided-book-ratio gross)))
                   {:code :equal-risk-lopsided-books
                    :book book
                    :budget budget
                    :gross gross
                    :asset-count asset-count
                    :message (str "Your gross/net targets leave only " (fmt budget)
                                  " for the " asset-count " "
                                  (if (= :long book) "Long" "Short")
                                  " assets — Equal Risk will concentrate risk in"
                                  " the other book. Reduce the net bias or"
                                  " change sides.")})))
         vec)))

(defn presolve
  "Explicit feasibility screening before the nonlinear solver. Returns
  {:status :ok :targets {:gross :net :long-gross :short-gross} :books {...}
   :warnings [...]} (non-blocking, e.g. lopsided book budgets)
  or {:status :infeasible :violations [...]} with specific codes."
  [{:keys [instrument-ids lower-bounds upper-bounds] :as encoded-constraints}
   covariance]
  (let [tolerance (:exposure-feasibility equal-risk/tolerances)
        n (count instrument-ids)
        covariance-result (risk-contributions/validate-covariance covariance n)
        covariance-violations (when (= :error (:status covariance-result))
                                [{:code (case (:reason covariance-result)
                                          :covariance-asymmetric :equal-risk-covariance-asymmetric
                                          :equal-risk-covariance-shape)
                                  :details (:details covariance-result)
                                  :message (case (:reason covariance-result)
                                             :covariance-asymmetric
                                             "The covariance matrix is materially asymmetric; Equal Risk will not silently repair invalid risk input."
                                             "The covariance matrix is missing, non-finite, or misaligned with the selected assets.")}])
        {:keys [gross net]} (equal-risk/exposure-targets encoded-constraints)
        exposure-violations* (exposure-violations encoded-constraints gross net tolerance)
        books (equal-risk/book-split encoded-constraints)
        side-violations* (side-violations encoded-constraints books)
        targets-valid? (and (finite-number? gross) (finite-number? net))
        long-gross (when targets-valid? (max 0 (/ (+ gross net) 2)))
        short-gross (when targets-valid? (max 0 (/ (- gross net) 2)))
        book-violations* (when (and targets-valid?
                                    (empty? exposure-violations*)
                                    (empty? side-violations*))
                           (concat (book-violations :long (:long books)
                                                    lower-bounds upper-bounds
                                                    long-gross tolerance)
                                   (book-violations :short (:short books)
                                                    lower-bounds upper-bounds
                                                    short-gross tolerance)))
        violations (vec (concat covariance-violations
                                exposure-violations*
                                side-violations*
                                book-violations*
                                (unsupported-constraint-violations encoded-constraints)))]
    (if (seq violations)
      {:status :infeasible
       :violations violations}
      {:status :ok
       :covariance (:covariance covariance-result)
       :targets {:gross gross
                 :net net
                 :long-gross long-gross
                 :short-gross short-gross}
       :books (select-keys books [:long :short])
       :warnings (lopsided-book-warnings books gross long-gross short-gross)})))
