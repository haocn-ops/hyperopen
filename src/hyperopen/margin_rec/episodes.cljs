(ns hyperopen.margin-rec.episodes
  "Intervention-horizon inference from the user's own fills.

  Fills are reconstructed into position episodes per coin (an episode starts
  when net exposure leaves zero and ends at zero or on a direction flip).
  Risk-reducing fills, closes, and flips are interventions; the gaps between
  consecutive interventions (episode start included) form the sample. The
  horizon is an upper quantile of those gaps — how long this user plausibly
  leaves a position unattended — not their average trade frequency.")

(def ^:private zero-size-epsilon 1e-9)

(defn- parse-num
  [value]
  (let [n (cond
            (number? value) value
            (string? value) (js/parseFloat value)
            :else js/NaN)]
    (when (and (number? n) (js/isFinite n))
      n)))

(defn normalize-fill
  "Raw userFills row -> {:coin :time-ms :delta :start-position} or nil.
  `delta` is the signed size change (B buys increase net, A/S sells decrease);
  `start-position` is the exchange-reported net before the fill when present."
  [row]
  (let [coin (let [c (:coin row)]
               (when (and (string? c) (seq c)) c))
        time-ms (some parse-num [(:time row) (:timestamp row) (:ts row)])
        size (some parse-num [(:sz row) (:size row)])
        side (let [s (:side row)]
               (cond
                 (contains? #{"B" "b" :buy "buy"} s) :buy
                 (contains? #{"A" "a" "S" "s" :sell "sell"} s) :sell
                 :else nil))
        start-position (parse-num (:startPosition row))]
    (when (and coin time-ms size (pos? size) side)
      {:coin coin
       :time-ms time-ms
       :delta (if (= :buy side) size (- size))
       :start-position start-position})))

(defn- zeroish?
  [value]
  (< (js/Math.abs value) zero-size-epsilon))

(defn intervention-gaps-for-coin
  "Gap durations (ms) between consecutive interventions across this coin's
  episodes, in fill order."
  [fills]
  (let [ordered (sort-by :time-ms fills)]
    (loop [remaining ordered
           net 0.0
           anchor nil
           gaps []]
      (if-let [{:keys [time-ms delta start-position]} (first remaining)]
        (let [net-before (if (some? start-position) start-position net)
              net-after (+ net-before delta)
              opening? (and (zeroish? net-before) (not (zeroish? net-after)))
              closing? (and (not (zeroish? net-before)) (zeroish? net-after))
              flip? (and (not (zeroish? net-before))
                         (not (zeroish? net-after))
                         (not= (pos? net-before) (pos? net-after)))
              reducing? (and (not (zeroish? net-before))
                             (< (js/Math.abs net-after) (js/Math.abs net-before)))
              intervention? (or closing? flip? reducing?)
              gaps* (if (and intervention? (some? anchor) (> time-ms anchor))
                      (conj gaps (- time-ms anchor))
                      gaps)
              anchor* (cond
                        closing? nil
                        (or opening? flip? intervention?) time-ms
                        :else anchor)]
          (recur (next remaining) net-after anchor* gaps*))
        gaps))))

(defn- quantile
  [sorted p]
  (let [n (count sorted)]
    (when (pos? n)
      (let [pos (* p (dec n))
            lo (js/Math.floor pos)
            hi (js/Math.ceil pos)
            frac (- pos lo)]
        (if (== lo hi)
          (nth sorted lo)
          (+ (* (- 1 frac) (nth sorted lo))
             (* frac (nth sorted hi))))))))

(def default-horizon-hours 72)
(def min-horizon-hours 6)
(def max-horizon-hours 720)
(def ^:private min-gap-samples 8)
(def ^:private horizon-quantile 0.8)

(defn horizon-hours
  "Estimated intervention horizon for `coin` from raw fill rows.
  Returns {:hours :source (:per-coin | :account | :default) :samples n}."
  [fill-rows coin]
  (let [fills (keep normalize-fill fill-rows)
        by-coin (group-by :coin fills)
        coin-gaps (when coin
                    (intervention-gaps-for-coin (get by-coin coin [])))
        account-gaps (when (< (count coin-gaps) min-gap-samples)
                       (mapcat intervention-gaps-for-coin (vals by-coin)))
        [gaps source] (cond
                        (>= (count coin-gaps) min-gap-samples) [coin-gaps :per-coin]
                        (>= (count account-gaps) min-gap-samples) [account-gaps :account]
                        :else [nil :default])
        hours (if gaps
                (/ (quantile (vec (sort gaps)) horizon-quantile)
                   3600000)
                default-horizon-hours)]
    {:hours (-> hours
                (max min-horizon-hours)
                (min max-horizon-hours))
     :source source
     :samples (count (or gaps []))}))
