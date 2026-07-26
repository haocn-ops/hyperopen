(ns hyperopen.schema.portfolio-returns-normalization-contracts
  (:require [hyperopen.schema.portfolio-returns-contracts :as returns-contracts]))

(def ^:private generated-history-point-row-keys
  #{:time-ms :value})

(def ^:private generated-aligned-point-row-keys
  #{:time-ms :account-value :pnl-value})

(def ^:private generated-normalized-cumulative-row-keys
  #{:time-ms :percent :factor})

(def ^:private history-point-vector-keys
  #{:id :rows :expected})

(def ^:private aligned-summary-vector-keys
  #{:id :summary :expected})

(def ^:private anchored-summary-vector-keys
  #{:id :summary :expected})

(def ^:private cumulative-normalization-vector-keys
  #{:id :rows :expected})

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

(defn- history-row-valid?
  [row]
  (and (vector? row)
       (= 2 (count row))
       (nat? (nth row 0))
       (integer-number? (nth row 1))))

(defn- summary-valid?
  [summary]
  (and (exact-keys? summary #{:accountValueHistory :pnlHistory})
       (vector-of? history-row-valid? (:accountValueHistory summary))
       (vector-of? history-row-valid? (:pnlHistory summary))))

(defn- generated-history-point-row-valid?
  [row]
  (and (exact-keys? row generated-history-point-row-keys)
       (nat? (:time-ms row))
       (integer-number? (:value row))))

(defn- generated-aligned-point-row-valid?
  [row]
  (and (exact-keys? row generated-aligned-point-row-keys)
       (nat? (:time-ms row))
       (integer-number? (:account-value row))
       (integer-number? (:pnl-value row))))

(defn- generated-normalized-cumulative-row-valid?
  [row]
  (and (exact-keys? row generated-normalized-cumulative-row-keys)
       (nat? (:time-ms row))
       (integer-number? (:percent row))
       (returns-contracts/ratio-valid? (:factor row))))

(defn history-point-vector-valid?
  [entry]
  (and (exact-keys? entry history-point-vector-keys)
       (keyword? (:id entry))
       (vector-of? history-row-valid? (:rows entry))
       (vector-of? generated-history-point-row-valid? (:expected entry))))

(defn aligned-summary-vector-valid?
  [entry]
  (and (exact-keys? entry aligned-summary-vector-keys)
       (keyword? (:id entry))
       (summary-valid? (:summary entry))
       (vector-of? generated-aligned-point-row-valid? (:expected entry))))

(defn anchored-summary-vector-valid?
  [entry]
  (and (exact-keys? entry anchored-summary-vector-keys)
       (keyword? (:id entry))
       (summary-valid? (:summary entry))
       (vector-of? generated-aligned-point-row-valid? (:expected entry))))

(defn cumulative-normalization-vector-valid?
  [entry]
  (and (exact-keys? entry cumulative-normalization-vector-keys)
       (keyword? (:id entry))
       (vector-of? history-row-valid? (:rows entry))
       (vector-of? generated-normalized-cumulative-row-valid? (:expected entry))))

(defn generated-history-point-rows->number-projection
  [rows]
  (mapv (fn [{:keys [time-ms value]}]
          {:time-ms time-ms
           :value value})
        rows))

(defn generated-aligned-point-rows->number-projection
  [rows]
  (mapv (fn [{:keys [time-ms account-value pnl-value]}]
          {:time-ms time-ms
           :account-value account-value
           :pnl-value pnl-value})
        rows))

(defn generated-normalized-cumulative-rows->number-projection
  [rows]
  (mapv (fn [{:keys [time-ms percent factor]}]
          {:time-ms time-ms
           :percent percent
           :factor (returns-contracts/ratio->number factor)})
        rows))

(defn runtime-history-point-projection
  [rows]
  (mapv (fn [row]
          {:time-ms (:time-ms row)
           :value (:value row)})
        rows))

(defn runtime-aligned-point-projection
  [rows]
  (mapv (fn [row]
          {:time-ms (:time-ms row)
           :account-value (:account-value row)
           :pnl-value (:pnl-value row)})
        rows))

(defn runtime-normalized-cumulative-row-projection
  [rows]
  (mapv (fn [row]
          {:time-ms (:time-ms row)
           :percent (:percent row)
           :factor (:factor row)})
        rows))

(defn runtime-history-point-row-projection-valid?
  [row]
  (and (exact-keys? row generated-history-point-row-keys)
       (nat? (:time-ms row))
       (number? (:value row))))

(defn runtime-aligned-point-row-projection-valid?
  [row]
  (and (exact-keys? row generated-aligned-point-row-keys)
       (nat? (:time-ms row))
       (number? (:account-value row))
       (number? (:pnl-value row))))

(defn runtime-normalized-cumulative-row-projection-valid?
  [row]
  (and (exact-keys? row generated-normalized-cumulative-row-keys)
       (nat? (:time-ms row))
       (number? (:percent row))
       (number? (:factor row))))

(defn assert-generated-history-point-rows!
  [rows context]
  (when-not (vector-of? generated-history-point-row-valid? rows)
    (throw (js/Error.
            (str "generated history-point rows contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-generated-aligned-point-rows!
  [rows context]
  (when-not (vector-of? generated-aligned-point-row-valid? rows)
    (throw (js/Error.
            (str "generated aligned-point rows contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-generated-normalized-cumulative-rows!
  [rows context]
  (when-not (vector-of? generated-normalized-cumulative-row-valid? rows)
    (throw (js/Error.
            (str "generated normalized cumulative rows contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-runtime-history-point-projection!
  [rows context]
  (when-not (vector-of? runtime-history-point-row-projection-valid? rows)
    (throw (js/Error.
            (str "runtime history-point projection contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-runtime-aligned-point-projection!
  [rows context]
  (when-not (vector-of? runtime-aligned-point-row-projection-valid? rows)
    (throw (js/Error.
            (str "runtime aligned-point projection contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-runtime-normalized-cumulative-projection!
  [rows context]
  (when-not (vector-of? runtime-normalized-cumulative-row-projection-valid? rows)
    (throw (js/Error.
            (str "runtime normalized cumulative projection contract validation failed. "
                 "context=" (pr-str context)
                 " rows=" (pr-str rows)))))
  rows)

(defn assert-history-point-vector!
  [entry context]
  (when-not (history-point-vector-valid? entry)
    (throw (js/Error.
            (str "history-point vector contract validation failed. "
                 "context=" (pr-str context)
                 " entry=" (pr-str entry)))))
  entry)

(defn assert-aligned-summary-vector!
  [entry context]
  (when-not (aligned-summary-vector-valid? entry)
    (throw (js/Error.
            (str "aligned summary vector contract validation failed. "
                 "context=" (pr-str context)
                 " entry=" (pr-str entry)))))
  entry)

(defn assert-anchored-summary-vector!
  [entry context]
  (when-not (anchored-summary-vector-valid? entry)
    (throw (js/Error.
            (str "anchored summary vector contract validation failed. "
                 "context=" (pr-str context)
                 " entry=" (pr-str entry)))))
  entry)

(defn assert-cumulative-normalization-vector!
  [entry context]
  (when-not (cumulative-normalization-vector-valid? entry)
    (throw (js/Error.
            (str "cumulative normalization vector contract validation failed. "
                 "context=" (pr-str context)
                 " entry=" (pr-str entry)))))
  entry)
