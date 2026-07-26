(ns hyperopen.views.trading-chart.utils.chart-interop.candle-sync-policy)

(defn- decision
  [mode reason previous-count next-count]
  {:mode mode
   :reason reason
   :previous-count previous-count
   :next-count next-count})

(defn- prefix-equal?
  [candles-a candles-b prefix-count]
  (loop [idx 0]
    (if (>= idx prefix-count)
      true
      (if (= (nth candles-a idx) (nth candles-b idx))
        (recur (inc idx))
        false))))

(defn- same-length-decision
  [previous-candles next-candles previous-count next-count]
  (let [prefix-count (max 0 (dec next-count))
        previous-last (nth previous-candles (dec previous-count))
        next-last (nth next-candles (dec next-count))]
    (cond
      (not (prefix-equal? previous-candles next-candles prefix-count))
      (decision :full-reset :prefix-mutation previous-count next-count)

      (not= (:time previous-last) (:time next-last))
      (decision :full-reset :tail-time-mismatch previous-count next-count)

      (= previous-last next-last)
      (decision :noop :unchanged-tail previous-count next-count)

      :else
      (decision :update-last :tail-rewrite previous-count next-count))))

(defn- single-append-decision
  [previous-candles next-candles previous-count next-count]
  (let [previous-last (nth previous-candles (dec previous-count))
        next-last (nth next-candles (dec next-count))]
    (cond
      (not (prefix-equal? previous-candles next-candles previous-count))
      (decision :full-reset :prefix-mutation previous-count next-count)

      (= (:time previous-last) (:time next-last))
      (decision :full-reset :append-time-collision previous-count next-count)

      :else
      (decision :append-last :single-append previous-count next-count))))

(defn infer-decision
  "Compare previous and next ordered candle collections and return the
  smallest safe sync action. `:mode` is one of `:noop`, `:full-reset`,
  `:update-last`, or `:append-last`. `:reason` explains why that action is
  required so tests and future maintainers can read the branch table without
  tracing Lightweight Charts interop."
  [previous-candles next-candles]
  (let [previous-candles* (or previous-candles [])
        next-candles* (or next-candles [])
        previous-count (count previous-candles*)
        next-count (count next-candles*)]
    (cond
      (and (zero? previous-count) (zero? next-count))
      (decision :noop :both-empty previous-count next-count)

      (identical? previous-candles next-candles)
      (decision :noop :identical-reference previous-count next-count)

      (zero? previous-count)
      (decision :full-reset :seed-from-empty previous-count next-count)

      (zero? next-count)
      (decision :full-reset :cleared-data previous-count next-count)

      (= next-count previous-count)
      (same-length-decision previous-candles* next-candles* previous-count next-count)

      (= next-count (inc previous-count))
      (single-append-decision previous-candles* next-candles* previous-count next-count)

      :else
      (decision :full-reset :count-mismatch previous-count next-count))))
