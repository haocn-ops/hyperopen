(ns hyperopen.views.portfolio.vm.volume
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.views.account-info.projections :as projections]
            [hyperopen.views.portfolio.vm.constants :as constants]))

(defn- optional-number
  [value]
  (projections/parse-optional-num value))

(defn- number-or-zero
  [value]
  (if-let [n (optional-number value)]
    n
    0))

(defn- fills-source
  [state]
  (or (get-in state [:orders :fills])
      (get-in state [:webdata2 :fills])
      []))

(defn- trade-values
  [rows]
  (keep (fn [row]
          (let [value (projections/trade-history-value-number row)
                time-ms (projections/trade-history-time-ms row)]
            (when (number? value)
              {:value value
               :time-ms time-ms})))
        rows))

(defonce fills-volume-cache (atom nil))

(defn volume-14d-usd
  [state]
  (let [fills (fills-source state)
        cache @fills-volume-cache
        values (if (and cache
                        (identical? fills (:fills cache)))
                 (:values cache)
                 (let [new-values (trade-values fills)]
                   (reset! fills-volume-cache {:fills fills
                                               :values new-values})
                   new-values))
        cutoff (- (.now js/Date) constants/fourteen-days-ms)
        recent-values (let [timed-values (keep :time-ms values)]
                        (if (seq timed-values)
                          (filter (fn [{:keys [time-ms]}]
                                    (and (number? time-ms)
                                         (>= time-ms cutoff)))
                                  values)
                          values))]
    (reduce (fn [acc {:keys [value]}]
              (+ acc value))
            0
            recent-values)))

(defn- user-fees-loaded-for-current-account?
  [state]
  (let [effective-address (account-context/effective-account-address state)
        loaded-for-address (account-context/normalize-address
                            (get-in state [:portfolio :user-fees-loaded-for-address]))]
    (or (nil? effective-address)
        (nil? loaded-for-address)
        (= effective-address loaded-for-address))))

(defn- current-account-user-fees
  [state]
  (when (user-fees-loaded-for-current-account? state)
    (get-in state [:portfolio :user-fees])))

(defn daily-user-vlm-rows
  [state]
  (let [user-fees (current-account-user-fees state)
        rows (or (:dailyUserVlm user-fees)
                 (:daily-user-vlm user-fees))]
    (if (sequential? rows)
      rows
      [])))

(defn- first-present
  [row keys]
  (some (fn [k]
          (let [value (get row k)]
            (when (some? value)
              value)))
        keys))

(defn- row-number
  [row keys fallback]
  (cond
    (map? row)
    (number-or-zero (first-present row keys))

    (and (sequential? row)
         (number? fallback)
         (< fallback (count row)))
    (number-or-zero (nth row fallback))

    :else
    0))

(defn daily-user-vlm-row-volume
  [row]
  (cond
    (map? row)
    (let [exchange (optional-number (first-present row [:exchange :exchange-volume]))
          user-cross (optional-number (first-present row [:userCross :user-cross :user_cross]))
          user-add (optional-number (first-present row [:userAdd :user-add :user_add]))]
      (if (or (number? user-cross)
              (number? user-add))
        (+ (or user-cross 0)
           (or user-add 0))
        (or exchange 0)))

    (and (sequential? row)
         (>= (count row) 2))
    (number-or-zero (second row))

    :else
    0))

(defn- completed-daily-user-vlm-rows
  [state]
  (let [rows (daily-user-vlm-rows state)]
    (if (seq rows)
      (take-last 14 (butlast rows))
      [])))

(defn- volume-history-row-values
  [row]
  {:exchange-volume (row-number row [:exchange :exchange-volume] 1)
   :weighted-maker-volume (row-number row [:userAdd :user-add :user_add] 3)
   :weighted-taker-volume (row-number row [:userCross :user-cross :user_cross] 2)})

(def ^:private utc-weekday-labels
  ["Sun." "Mon." "Tue." "Wed." "Thu." "Fri." "Sat."])

(def ^:private utc-month-labels
  ["Jan." "Feb." "Mar." "Apr." "May." "Jun."
   "Jul." "Aug." "Sep." "Oct." "Nov." "Dec."])

(defn- row-date-value
  [row]
  (cond
    (map? row)
    (first-present row [:date :day :time :time-ms :timestamp :t])

    (and (sequential? row)
         (seq row))
    (first row)

    :else
    nil))

(defn- js-date-from-value
  [value]
  (let [date (cond
               (instance? js/Date value) value
               (or (number? value)
                   (string? value)) (js/Date. value)
               :else nil)]
    (when (and date
               (not (js/isNaN (.getTime date))))
      date)))

(defn- format-volume-history-date
  [value fallback-label]
  (if-let [date (js-date-from-value value)]
    (str (get utc-weekday-labels (.getUTCDay date))
         " "
         (.getUTCDate date)
         ". "
         (get utc-month-labels (.getUTCMonth date))
         " "
         (.getUTCFullYear date))
    (or (when (string? value) value)
        fallback-label)))

(defn- volume-history-row-model
  [index row]
  (let [date-value (row-date-value row)
        fallback-label (str "Day " (inc index))]
    (assoc (volume-history-row-values row)
           :id (or (some-> date-value str)
                   (str "day-" index))
           :date-label (format-volume-history-date date-value fallback-label))))

(defn- sum-history-values
  [rows value-key]
  (reduce (fn [acc row]
            (+ acc (get (volume-history-row-values row) value-key 0)))
          0
          rows))

(defn- maker-volume-share-pct
  [{:keys [exchange-volume weighted-maker-volume]}]
  (let [exchange (if (number? exchange-volume) exchange-volume 0)
        maker (if (number? weighted-maker-volume) weighted-maker-volume 0)]
    (if (pos? exchange)
      (* 100 (/ maker exchange))
      0)))

(defn volume-history-model
  [state]
  (let [completed-rows (completed-daily-user-vlm-rows state)
        totals {:exchange-volume (sum-history-values completed-rows :exchange-volume)
                :weighted-maker-volume (sum-history-values completed-rows :weighted-maker-volume)
                :weighted-taker-volume (sum-history-values completed-rows :weighted-taker-volume)}
        day-rows (map-indexed volume-history-row-model (reverse completed-rows))]
    {:open? (true? (get-in state [:portfolio-ui :volume-history-open?]))
     :anchor (get-in state [:portfolio-ui :volume-history-anchor])
     :loading? (true? (get-in state [:portfolio :user-fees-loading?]))
     :error (get-in state [:portfolio :user-fees-error])
     :rows (conj (vec day-rows)
                 (assoc totals
                        :id :total
                        :date-label "Total"
                        :total? true))
     :totals totals
     :maker-volume-share-pct (maker-volume-share-pct totals)}))

(defn volume-14d-usd-from-user-fees
  [state]
  (let [rows (completed-daily-user-vlm-rows state)]
    (when (seq (daily-user-vlm-rows state))
      (reduce (fn [acc row]
                (+ acc (daily-user-vlm-row-volume row)))
              0
              rows))))

(defn fees-from-user-fees
  [user-fees]
  (let [referral-discount (number-or-zero (:activeReferralDiscount user-fees))
        cross-rate (optional-number (:userCrossRate user-fees))
        add-rate (optional-number (:userAddRate user-fees))
        adjusted-cross-rate (when (number? cross-rate)
                              (* cross-rate (- 1 referral-discount)))
        adjusted-add-rate (when (number? add-rate)
                            (if (pos? add-rate)
                              (* add-rate (- 1 referral-discount))
                              add-rate))]
    (when (and (number? adjusted-cross-rate)
               (number? adjusted-add-rate))
      {:taker (* 100 adjusted-cross-rate)
       :maker (* 100 adjusted-add-rate)})))
