(ns hyperopen.views.portfolio.montecarlo.format
  "Small display formatters for the Monte Carlo surface.")

(defn signed-pct
  "Signed percent from a fraction, e.g. 0.123 -> \"+12.3%\"."
  ([v] (signed-pct v 1))
  ([v digits]
   (if (number? v)
     (str (when (>= v 0) "+") (.toFixed (* v 100) digits) "%")
     "—")))

(defn unsigned-pct
  "Unsigned percent from a fraction, e.g. 0.6 -> \"60%\"."
  ([v] (unsigned-pct v 1))
  ([v digits]
   (if (number? v)
     (str (.toFixed (* v 100) digits) "%")
     "—")))

(defn ratio
  "Plain fixed-decimal number, e.g. a Sharpe ratio."
  ([v] (ratio v 2))
  ([v digits]
   (if (number? v)
     (.toFixed v digits)
     "—")))

(defn years-label
  "Compact elapsed-time label: <1mo / 6mo / 1.8y. The Monte Carlo surface
  expresses time in calendar units because vault points are sparse and
  irregularly spaced, not one-per-trading-day."
  [y]
  (cond
    (not (number? y)) "—"
    (< y (/ 1.0 12)) "<1mo"
    (< y 1) (str (js/Math.round (* y 12)) "mo")
    :else (str (.toFixed y 1) "y")))

(defn usd
  "Compact dollar amount: $5.59M / $4.21K / $812."
  [v]
  (if (number? v)
    (let [a (js/Math.abs v)]
      (cond
        (>= a 1e6) (str "$" (.toFixed (/ v 1e6) 2) "M")
        (>= a 1e3) (str "$" (.toFixed (/ v 1e3) 1) "K")
        :else (str "$" (.toFixed v 0))))
    "—"))
