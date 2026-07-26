(ns hyperopen.margin-rec.paths
  "Adverse-path simulation for isolated-margin sizing.

  Liquidation is a first-touch event, so the statistic that matters is the
  maximum adverse excursion of the mark over the horizon, not the terminal
  return. Hourly bars are block-bootstrapped (close-to-close log return plus
  the intra-bar wick excursion), rescaled to current EWMA volatility, and each
  simulated bar is tested at both its adverse wick and its close against the
  maintenance curve. Per path the simulation records the minimum starting
  equity that would have survived every tested point:

      requiredE = max_t [ mm(|q|*P_t) - q*(P_t - P0) ]

  The sorted requiredE distribution answers every downstream question:
  p_liq(E) is the fraction above E, and the minimal collateral for a risk
  limit alpha is the (1 - alpha) quantile. Everything here is pure and
  deterministic under a fixed seed (per-path seeding keeps results invariant
  to batch slicing).")

(def ^:private uint-scale 4294967296)

(defn mulberry32
  "Deterministic uniform floats in [0, 1) seeded by integer `seed`. Same
  generator as the portfolio Monte Carlo engine; duplicated locally so the
  margin-rec lazy module does not create a shared-code dependency that would
  hoist portfolio namespaces into :main."
  [seed]
  (let [state (volatile! (bit-or 0 seed))]
    (fn []
      (let [a (bit-or 0 (+ @state 0x6D2B79F5))]
        (vreset! state a)
        (let [t (js/Math.imul (bit-xor a (unsigned-bit-shift-right a 15))
                              (bit-or 1 a))
              t (bit-xor (+ t (js/Math.imul (bit-xor t (unsigned-bit-shift-right t 7))
                                            (bit-or 61 t)))
                         t)]
          (/ (unsigned-bit-shift-right (bit-xor t (unsigned-bit-shift-right t 14)) 0)
             uint-scale))))))

(defn mix-seed
  "Stable per-path seed derived from a base seed and a path index."
  [seed path-index]
  (bit-or 0 (bit-xor (bit-or 0 seed)
                     (js/Math.imul (inc path-index) 0x9E3779B9))))

(defn- parse-pos
  [value]
  (let [n (cond
            (number? value) value
            (string? value) (js/parseFloat value)
            :else js/NaN)]
    (when (and (number? n) (js/isFinite n) (pos? n))
      n)))

(defn prepare-bars
  "Candle rows ({:t :o :h :l :c}, values possibly strings) to typed return
  arrays. Returns nil when fewer than 2 valid consecutive bars exist.

  Per bar i (relative to previous close): ret = ln(c/c-prev),
  low-exc = ln(l/c-prev) clamped <= ret, high-exc = ln(h/c-prev) clamped >= ret."
  [candle-rows]
  (let [rows (->> (or candle-rows [])
                  (keep (fn [row]
                          (let [t (or (:t row) (:time row))
                                c (parse-pos (:c row))
                                l (or (parse-pos (:l row)) c)
                                h (or (parse-pos (:h row)) c)]
                            (when (and (number? t) c)
                              {:t t :c c :l l :h h}))))
                  (sort-by :t)
                  vec)
        n-rows (count rows)]
    (when (>= n-rows 2)
      (let [n (dec n-rows)
            rets (js/Float64Array. n)
            lows (js/Float64Array. n)
            highs (js/Float64Array. n)]
        (loop [i 1]
          (when (<= i n)
            (let [prev-c (:c (nth rows (dec i)))
                  {:keys [c l h]} (nth rows i)
                  ret (js/Math.log (/ c prev-c))
                  low-exc (js/Math.min ret (js/Math.log (/ l prev-c)))
                  high-exc (js/Math.max ret (js/Math.log (/ h prev-c)))]
              (aset rets (dec i) ret)
              (aset lows (dec i) low-exc)
              (aset highs (dec i) high-exc)
              (recur (inc i)))))
        {:rets rets
         :lows lows
         :highs highs
         :n n
         :first-t (:t (first rows))
         :last-t (:t (peek rows))}))))

(defn ewma-vol
  "Per-bar EWMA volatility (zero-mean, RiskMetrics-style) over `rets` with
  decay `lambda`."
  [rets lambda]
  (let [n (alength rets)]
    (when (pos? n)
      (loop [i 0
             v 0.0]
        (if (< i n)
          (let [r (aget rets i)
                r2 (* r r)]
            (recur (inc i)
                   (if (zero? i)
                     r2
                     (+ (* lambda v) (* (- 1 lambda) r2)))))
          (js/Math.sqrt v))))))

(defn sample-vol
  "Root mean square of `rets`."
  [rets]
  (let [n (alength rets)]
    (when (pos? n)
      (loop [i 0
             acc 0.0]
        (if (< i n)
          (let [r (aget rets i)]
            (recur (inc i) (+ acc (* r r))))
          (js/Math.sqrt (/ acc n)))))))

(defn stress-starts
  "Block starts whose realized volatility is in the top decile, as an
  int-array. With too few blocks to rank, every start qualifies."
  [rets block-len]
  (let [n (alength rets)
        len (min block-len n)
        n-starts (inc (- n len))]
    (when (pos? n-starts)
      (let [vols (map (fn [start]
                        (loop [i 0
                               acc 0.0]
                          (if (< i len)
                            (let [r (aget rets (+ start i))]
                              (recur (inc i) (+ acc (* r r))))
                            [start (/ acc len)])))
                      (range n-starts))
            ranked (sort-by (comp - second) vols)
            keep-n (max 1 (js/Math.floor (/ n-starts 10)))]
        (into-array (map first (take keep-n ranked)))))))

(defn simulate-batch
  "Simulate paths [start-index, start-index + path-count) and return an
  unsorted Float64Array of per-path required equity.

  bars: output of `prepare-bars`.
  params: {:q signed-size
           :p0 mark
           :mm-fn (fn [notional] mm-usd)
           :horizon-bars int
           :seed int
           :rho vol-rescale
           :block-len int
           :stress-prob 0..1
           :stress-starts int-array|nil}"
  [{:keys [rets lows highs n]}
   {:keys [q p0 mm-fn horizon-bars seed rho block-len stress-prob]
    :as params}
   start-index path-count]
  (let [abs-q (js/Math.abs q)
        long? (pos? q)
        excs (if long? lows highs)
        len (max 1 (min (or block-len 24) n))
        max-start (- n len)
        stress (or (:stress-starts params) (into-array []))
        n-stress (alength stress)
        stress-p (if (pos? n-stress) (or stress-prob 0) 0)
        horizon (max 1 horizon-bars)
        out (js/Float64Array. path-count)
        mm0 (mm-fn (* abs-q p0))]
    (loop [j 0]
      (if (>= j path-count)
        out
        (let [rng (mulberry32 (mix-seed seed (+ start-index j)))]
          (loop [t 0
                 block-i len
                 block-start 0
                 cum 0.0
                 req mm0]
            (if (>= t horizon)
              (aset out j req)
              (let [new-block? (>= block-i len)
                    block-start* (if new-block?
                                   (if (< (rng) stress-p)
                                     (aget stress (js/Math.floor (* (rng) n-stress)))
                                     (js/Math.floor (* (rng) (inc max-start))))
                                   block-start)
                    block-i* (if new-block? 0 block-i)
                    idx (+ block-start* block-i*)
                    p-ext (* p0 (js/Math.exp (+ cum (* rho (aget excs idx)))))
                    cum* (+ cum (* rho (aget rets idx)))
                    p-close (* p0 (js/Math.exp cum*))
                    req-ext (- (mm-fn (* abs-q p-ext))
                               (* q (- p-ext p0)))
                    req-close (- (mm-fn (* abs-q p-close))
                                 (* q (- p-close p0)))
                    req* (-> req
                             (js/Math.max req-ext)
                             (js/Math.max req-close))]
                (recur (inc t) (inc block-i*) block-start* cum* req*))))
          (recur (inc j)))))))

(defn sort-required
  "Concatenate one or more Float64Arrays of required equity into a single
  ascending-sorted Float64Array."
  [arrays]
  (let [total (reduce + 0 (map alength arrays))
        out (js/Float64Array. total)]
    (loop [remaining arrays
           offset 0]
      (when-let [arr (first remaining)]
        (.set out arr offset)
        (recur (next remaining) (+ offset (alength arr)))))
    (.sort out)
    out))

(defn prob-above
  "Fraction of the sorted distribution strictly greater than `x`."
  [sorted x]
  (let [n (alength sorted)]
    (if (zero? n)
      0
      (loop [lo 0
             hi n]
        (if (< lo hi)
          (let [mid (js/Math.floor (/ (+ lo hi) 2))]
            (if (<= (aget sorted mid) x)
              (recur (inc mid) hi)
              (recur lo mid)))
          (/ (- n lo) n))))))

(defn quantile-of-sorted
  "Linear-interpolated quantile p (0..1) of an ascending-sorted Float64Array."
  [sorted p]
  (let [n (alength sorted)]
    (when (pos? n)
      (let [p* (-> p (max 0) (min 1))
            pos (* p* (dec n))
            lo (js/Math.floor pos)
            hi (js/Math.ceil pos)
            frac (- pos lo)]
        (if (== lo hi)
          (aget sorted lo)
          (+ (* (- 1 frac) (aget sorted lo))
             (* frac (aget sorted hi))))))))

(defn simulate-required-sync
  "Single-shot convenience: simulate `paths` paths and return the sorted
  required-equity distribution. Used by tests and as the non-chunked fallback."
  [bars params paths]
  (sort-required [(simulate-batch bars params 0 paths)]))
