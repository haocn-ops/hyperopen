(ns hyperopen.portfolio.optimizer.application.history-loader.window
  (:require [hyperopen.portfolio.metrics.history :as metrics-history]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(defn- finite-ms
  [value]
  (coercion/parse-ms value))

(defn- elapsed-days
  [start-ms end-ms]
  (when (and (coercion/finite-number? start-ms)
             (coercion/finite-number? end-ms)
             (<= start-ms end-ms))
    (/ (- end-ms start-ms) metrics-history/day-ms)))

(defn- finite-return?
  [row]
  (coercion/finite-number? (:return row)))

(defn- source-coverage
  [[instrument-id rows]]
  (let [rows* (->> rows
                   (keep (fn [row]
                           (when-let [time-ms (finite-ms (:time-ms row))]
                             (assoc row :time-ms time-ms))))
                   (sort-by :time-ms)
                   vec)
        finite-return-count (count (filter finite-return? rows*))
        point-count (count rows*)]
    (when (pos? point-count)
      {:instrument-id instrument-id
       :start-ms (:time-ms (first rows*))
       :end-ms (:time-ms (last rows*))
       :point-count point-count
       :return-observations (if (pos? finite-return-count)
                              finite-return-count
                              (max 0 (dec point-count)))
       :calendar-window-days (or (elapsed-days (:time-ms (first rows*))
                                               (:time-ms (last rows*)))
                                 0)})))

(defn- tied-candidate
  [reason coverages value-fn]
  (let [values (map value-fn coverages)
        target (case reason
                 :starts-later (apply max values)
                 :ends-earlier (apply min values)
                 :fewest-return-observations (apply min values))
        candidates (->> coverages
                        (filter #(= target (value-fn %)))
                        (sort-by :instrument-id)
                        vec)]
    (when-let [candidate (first candidates)]
      (assoc candidate
             :limiting-reason reason
             :limiting-candidate-count (count candidates)))))

(defn- limiter
  [coverages oldest-common-ms latest-common-ms]
  (when (seq coverages)
    (let [starts (map :start-ms coverages)
          ends (map :end-ms coverages)
          observations (map :return-observations coverages)
          latest-start (apply max starts)
          earliest-start (apply min starts)
          earliest-end (apply min ends)
          latest-end (apply max ends)]
      (cond
        (and (> latest-start earliest-start)
             (coercion/finite-number? oldest-common-ms)
             (<= latest-start oldest-common-ms))
        (tied-candidate :starts-later coverages :start-ms)

        (and (< earliest-end latest-end)
             (coercion/finite-number? latest-common-ms)
             (>= earliest-end latest-common-ms))
        (tied-candidate :ends-earlier coverages :end-ms)

        (not= (apply min observations) (apply max observations))
        (tied-candidate :fewest-return-observations coverages :return-observations)))))

(defn- return-days
  [return-intervals calendar-window-days]
  (let [days (reduce + 0 (keep :dt-days return-intervals))]
    (if (pos? days)
      days
      calendar-window-days)))

(defn history-window
  [{:keys [calendar
           return-calendar
           return-intervals
           source-series-by-instrument]}]
  (let [oldest-common-ms (first calendar)
        latest-common-ms (last calendar)
        calendar-window-days (or (elapsed-days oldest-common-ms latest-common-ms) 0)
        source-series (or source-series-by-instrument {})
        coverages (->> source-series
                       (keep source-coverage)
                       vec)
        source-coverage-unavailable? (not= (count coverages)
                                           (count source-series))
        limiter* (when-not source-coverage-unavailable?
                   (limiter coverages oldest-common-ms latest-common-ms))
        result (cond-> {:return-observations (count return-calendar)
                        :return-days (return-days return-intervals
                                                  calendar-window-days)
                        :calendar-window-days calendar-window-days
                        :oldest-common-ms oldest-common-ms
                        :latest-common-ms latest-common-ms}
                 source-coverage-unavailable?
                 (assoc :limiting-reason :source-coverage-unavailable)

                 (and (not source-coverage-unavailable?) limiter*)
                 (assoc :limiting-instrument-id (:instrument-id limiter*)
                        :limiting-reason (:limiting-reason limiter*)
                        :limiting-source-return-observations
                        (:return-observations limiter*)
                        :limiting-source-calendar-days
                        (:calendar-window-days limiter*)
                        :limiting-candidate-count
                        (:limiting-candidate-count limiter*))

                 (and (not source-coverage-unavailable?) (nil? limiter*))
                 (assoc :limiting-reason :shared-calendar))]
    result))
