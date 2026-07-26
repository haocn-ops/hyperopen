(ns hyperopen.schema.portfolio-returns-contracts)

(def ^:private ratio-keys
  #{:num :den})

(def ^:private generated-cumulative-row-keys
  #{:time-ms :percent})

(def ^:private generated-interval-row-keys
  #{:time-ms :return})

(def ^:private generated-daily-row-keys
  #{:day :time-ms :return})

(def ^:private summary-keys
  #{:accountValueHistory :pnlHistory})

(def ^:private simulator-expected-keys
  #{:latent-window-final-percent
    :estimator-rows
    :estimator-final-percent
    :exact?
    :first-row-zero?
    :avoid-false-wipeout?
    :max-final-error-bps})

(def ^:private simulator-vector-keys
  #{:id :latent :observation :observed-summary :expected})

(def ^:private latent-keys
  #{:initial-account-value :steps})

(def ^:private latent-step-keys
  #{:dt-ms :pnl-delta :cash-flow})

(def ^:private observation-keys
  #{:sample-indexes :pnl-window-mode})

(defn- exact-keys?
  [value expected]
  (and (map? value)
       (= expected (set (keys value)))))

(defn- vector-of?
  [predicate value]
  (and (vector? value)
       (every? predicate value)))

(defn- nat?
  [value]
  (and (integer? value)
       (not (neg? value))))

(defn- integer-number?
  [value]
  (and (number? value)
       (integer? value)))

(defn ratio-valid?
  [ratio]
  (and (exact-keys? ratio ratio-keys)
       (integer-number? (:num ratio))
       (nat? (:den ratio))
       (pos? (:den ratio))))

(defn ratio->number
  [ratio]
  (/ (:num ratio)
     (:den ratio)))

(defn- generated-cumulative-row-valid?
  [row]
  (and (exact-keys? row generated-cumulative-row-keys)
       (nat? (:time-ms row))
       (ratio-valid? (:percent row))))

(defn- generated-interval-row-valid?
  [row]
  (and (exact-keys? row generated-interval-row-keys)
       (nat? (:time-ms row))
       (ratio-valid? (:return row))))

(defn- generated-daily-row-valid?
  [row]
  (and (exact-keys? row generated-daily-row-keys)
       (string? (:day row))
       (nat? (:time-ms row))
       (ratio-valid? (:return row))))

(defn- history-row-valid?
  [row]
  (and (vector? row)
       (= 2 (count row))
       (nat? (nth row 0))
       (integer-number? (nth row 1))))

(defn- summary-valid?
  [summary]
  (and (exact-keys? summary summary-keys)
       (vector-of? history-row-valid? (:accountValueHistory summary))
       (vector-of? history-row-valid? (:pnlHistory summary))))

(defn- latent-step-valid?
  [step]
  (and (exact-keys? step latent-step-keys)
       (nat? (:dt-ms step))
       (integer-number? (:pnl-delta step))
       (integer-number? (:cash-flow step))))

(defn- latent-valid?
  [latent]
  (and (exact-keys? latent latent-keys)
       (integer-number? (:initial-account-value latent))
       (vector-of? latent-step-valid? (:steps latent))))

(defn- observation-valid?
  [observation]
  (and (exact-keys? observation observation-keys)
       (vector-of? nat? (:sample-indexes observation))
       (contains? #{:cumulative :rebased-at-first-sample}
                  (:pnl-window-mode observation))))

(defn- simulator-expected-valid?
  [expected]
  (and (exact-keys? expected simulator-expected-keys)
       (ratio-valid? (:latent-window-final-percent expected))
       (vector-of? generated-cumulative-row-valid? (:estimator-rows expected))
       (ratio-valid? (:estimator-final-percent expected))
       (boolean? (:exact? expected))
       (boolean? (:first-row-zero? expected))
       (boolean? (:avoid-false-wipeout? expected))
       (nat? (:max-final-error-bps expected))))

(defn series-vector-valid?
  [entry]
  (and (map? entry)
       (keyword? (:id entry))
       (summary-valid? (:summary entry))
       (vector-of? generated-cumulative-row-valid? (:expected entry))))

(defn interval-vector-valid?
  [entry]
  (and (map? entry)
       (keyword? (:id entry))
       (vector-of? generated-cumulative-row-valid? (:rows entry))
       (vector-of? generated-interval-row-valid? (:expected entry))))

(defn daily-vector-valid?
  [entry]
  (and (map? entry)
       (keyword? (:id entry))
       (vector-of? generated-cumulative-row-valid? (:rows entry))
       (vector-of? generated-daily-row-valid? (:expected entry))))

(defn simulator-vector-valid?
  [entry]
  (and (exact-keys? entry simulator-vector-keys)
       (keyword? (:id entry))
       (latent-valid? (:latent entry))
       (observation-valid? (:observation entry))
       (summary-valid? (:observed-summary entry))
       (simulator-expected-valid? (:expected entry))))

(defn generated-cumulative-rows->runtime-input
  [rows]
  (mapv (fn [{:keys [time-ms percent]}]
          [time-ms (ratio->number percent)])
        rows))

(defn generated-cumulative-rows->number-projection
  [rows]
  (mapv (fn [{:keys [time-ms percent]}]
          {:time-ms time-ms
           :percent (ratio->number percent)})
        rows))

(defn generated-interval-rows->number-projection
  [rows]
  (mapv (fn [{:keys [time-ms return]}]
          {:time-ms time-ms
           :return (ratio->number return)})
        rows))

(defn generated-daily-rows->number-projection
  [rows]
  (mapv (fn [{:keys [day time-ms return]}]
          {:day day
           :time-ms time-ms
           :return (ratio->number return)})
        rows))

(defn runtime-cumulative-rows-projection
  [rows]
  (mapv (fn [row]
          (cond
            (and (vector? row) (= 2 (count row)))
            {:time-ms (nth row 0)
             :percent (nth row 1)}

            (map? row)
            {:time-ms (:time-ms row)
             :percent (:percent row)}

            :else
            {:time-ms nil
             :percent nil}))
        rows))

(defn runtime-interval-rows-projection
  [rows]
  (mapv (fn [row]
          {:time-ms (:time-ms row)
           :return (:return row)})
        rows))

(defn runtime-daily-rows-projection
  [rows]
  (mapv (fn [row]
          {:day (:day row)
           :time-ms (:time-ms row)
           :return (:return row)})
        rows))

(defn runtime-cumulative-row-projection-valid?
  [row]
  (and (exact-keys? row generated-cumulative-row-keys)
       (nat? (:time-ms row))
       (number? (:percent row))))

(defn runtime-interval-row-projection-valid?
  [row]
  (and (exact-keys? row generated-interval-row-keys)
       (nat? (:time-ms row))
       (number? (:return row))))

(defn runtime-daily-row-projection-valid?
  [row]
  (and (exact-keys? row generated-daily-row-keys)
       (string? (:day row))
       (nat? (:time-ms row))
       (number? (:return row))))

(defn final-percent
  [cumulative-rows]
  (or (:percent (peek (vec cumulative-rows)))
      0))

(defn final-error-bps
  [actual-percent latent-percent]
  (* 100 (js/Math.abs (- actual-percent latent-percent))))

(defn assert-generated-cumulative-rows!
  [rows context]
  (when-not (vector-of? generated-cumulative-row-valid? rows)
    (throw (js/Error.
            (str "generated cumulative rows contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-generated-interval-rows!
  [rows context]
  (when-not (vector-of? generated-interval-row-valid? rows)
    (throw (js/Error.
            (str "generated interval rows contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-generated-daily-rows!
  [rows context]
  (when-not (vector-of? generated-daily-row-valid? rows)
    (throw (js/Error.
            (str "generated daily rows contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-runtime-cumulative-projection!
  [rows context]
  (when-not (vector-of? runtime-cumulative-row-projection-valid? rows)
    (throw (js/Error.
            (str "runtime cumulative projection contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-runtime-interval-projection!
  [rows context]
  (when-not (vector-of? runtime-interval-row-projection-valid? rows)
    (throw (js/Error.
            (str "runtime interval projection contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-runtime-daily-projection!
  [rows context]
  (when-not (vector-of? runtime-daily-row-projection-valid? rows)
    (throw (js/Error.
            (str "runtime daily projection contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-series-vector!
  [entry context]
  (when-not (series-vector-valid? entry)
    (throw (js/Error.
            (str "series vector contract validation failed. "
                 "context=" (pr-str context)
                 " entry=" (pr-str entry)))))
  entry)

(defn assert-interval-vector!
  [entry context]
  (when-not (interval-vector-valid? entry)
    (throw (js/Error.
            (str "interval vector contract validation failed. "
                 "context=" (pr-str context)
                 " entry=" (pr-str entry)))))
  entry)

(defn assert-daily-vector!
  [entry context]
  (when-not (daily-vector-valid? entry)
    (throw (js/Error.
            (str "daily vector contract validation failed. "
                 "context=" (pr-str context)
                 " entry=" (pr-str entry)))))
  entry)

(defn assert-simulator-vector!
  [entry context]
  (when-not (simulator-vector-valid? entry)
    (throw (js/Error.
            (str "simulator vector contract validation failed. "
                 "context=" (pr-str context)
                 " entry=" (pr-str entry)))))
  entry)
