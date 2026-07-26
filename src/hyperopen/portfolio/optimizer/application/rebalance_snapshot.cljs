(ns hyperopen.portfolio.optimizer.application.rebalance-snapshot
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.rebalance-preview :as rebalance-preview]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def default-max-snapshot-coins 8)
(def default-snapshot-stale-after-ms 30000)
(def default-snapshot-cache-ttl-ms 30000)

(def ^:private non-blank-text coercion/non-blank-text)
(def ^:private parse-number coercion/parse-float-number)

(defn- normalized-positive-int
  [value fallback]
  (if (and (integer? value) (pos? value))
    value
    fallback))

(defn- instrument-by-id
  [request]
  (into {}
        (keep (fn [instrument]
                (when-let [instrument-id (non-blank-text (:instrument-id instrument))]
                  [instrument-id instrument])))
        (concat (:requested-universe request)
                (:universe request)
                (get-in request [:current-portfolio :exposures])
                (vals (get-in request [:current-portfolio :by-instrument])))))

(defn- row-instrument
  [instruments row]
  (get instruments (:instrument-id row)))

(defn- row-coin
  [instruments row]
  (or (non-blank-text (:coin row))
      (non-blank-text (:coin (row-instrument instruments row)))))

(defn- row-instrument-type
  [instruments row]
  (or (:instrument-type row)
      (:market-type row)
      (:instrument-type (row-instrument instruments row))
      (:market-type (row-instrument instruments row))))

(defn- perp-row?
  [instruments row]
  (or (= :perp (row-instrument-type instruments row))
      (str/starts-with? (str (:instrument-id row)) "perp:")))

(defn- fresh-snapshot-row?
  [row stale-after-ms]
  (let [cost (:cost row)
        age-ms (:age-ms cost)]
    (and (= :snapshot (:source cost))
         (false? (:stale? cost))
         (number? age-ms)
         (<= age-ms stale-after-ms))))

(defn- eligible-row?
  [instruments stale-after-ms row]
  (and (= :ready (:status row))
       (contains? #{:buy :sell} (:side row))
       (perp-row? instruments row)
       (some? (row-coin instruments row))
       (not (fresh-snapshot-row? row stale-after-ms))))

(defn- add-plan-row
  [instruments acc row]
  (if-let [coin (row-coin instruments row)]
    (let [instrument-id (:instrument-id row)]
      (-> acc
          (update :order #(if (some #{coin} %) % (conj (or % []) coin)))
          (update-in [:by-coin coin :coin] #(or % coin))
          (update-in [:by-coin coin :instrument-ids]
                     (fn [ids]
                       (vec (distinct (conj (or ids []) instrument-id)))))
          (update-in [:by-coin coin :rows] (fnil conj []) row)))
    acc))

(defn build-snapshot-refresh-plan
  ([last-run]
   (build-snapshot-refresh-plan last-run {}))
  ([last-run opts]
   (let [opts* (or opts {})
         request (get-in last-run [:request-signature :request])
         rows (vec (get-in last-run [:result :rebalance-preview :rows]))
         instruments (instrument-by-id request)
         stale-after-ms (or (:snapshot-stale-after-ms opts*)
                            default-snapshot-stale-after-ms)
         max-coins (normalized-positive-int (:max-snapshot-coins opts*)
                                            default-max-snapshot-coins)
         eligible-rows (filterv #(eligible-row? instruments stale-after-ms %) rows)
         skipped-count (- (count rows) (count eligible-rows))
         grouped (reduce (partial add-plan-row instruments)
                         {:order []
                          :by-coin {}}
                         eligible-rows)
         ordered (mapv #(get-in grouped [:by-coin %]) (:order grouped))
         requests (subvec ordered 0 (min max-coins (count ordered)))]
     {:status (if (seq requests) :ready :no-op)
      :requests requests
      :eligible-count (count ordered)
      :skipped-count skipped-count
      :truncated-count (max 0 (- (count ordered) (count requests)))
      :max-snapshot-coins max-coins
      :snapshot-stale-after-ms stale-after-ms})))

(defn- level-price
  [level]
  (or (parse-number (:px-num level))
      (parse-number (:px level))
      (parse-number (:price level))))

(defn- sort-bids
  [levels]
  (vec (sort-by #(or (level-price %) 0) > (or levels []))))

(defn- sort-asks
  [levels]
  (vec (sort-by #(or (level-price %) 0) < (or levels []))))

(defn- observed-at-ms
  [payload now-ms]
  (or (parse-number (:time payload))
      (parse-number (:timestamp payload))
      (parse-number (:received-at-ms payload))
      now-ms))

(defn- age-ms
  [now-ms observed]
  (when (and (number? now-ms)
             (number? observed))
    (max 0 (- now-ms observed))))

(defn normalize-l2-book-snapshot-context
  ([coin payload opts]
   (let [opts* (or opts {})
         now-ms (:now-ms opts*)
         stale-after-ms (or (:snapshot-stale-after-ms opts*)
                            default-snapshot-stale-after-ms)
         levels (:levels payload)
         bids (sort-bids (or (:bids payload)
                             (first levels)))
         asks (sort-asks (or (:asks payload)
                             (second levels)))
         observed-at-ms* (observed-at-ms payload now-ms)
         age-ms* (age-ms now-ms observed-at-ms*)]
     (when (or (seq bids) (seq asks))
       (cond-> {:coin (non-blank-text coin)
                :source :snapshot
                :bids bids
                :asks asks
                :best-bid (first bids)
                :best-ask (first asks)
                :observed-at-ms observed-at-ms*
                :stale? (if (number? age-ms*)
                          (> age-ms* stale-after-ms)
                          false)}
         (number? age-ms*) (assoc :age-ms age-ms*))))))

(defn- request-with-snapshot-contexts
  [request contexts-by-id]
  (update-in request
             [:execution-assumptions :cost-contexts-by-id]
             merge
             contexts-by-id))

(defn last-run-with-snapshot-contexts
  [last-run contexts-by-id]
  (if (and (map? last-run) (seq contexts-by-id))
    (let [request (get-in last-run [:request-signature :request])
          request* (request-with-snapshot-contexts request contexts-by-id)
          result* (rebalance-preview/result-with-refreshed-rebalance-preview
                   request*
                   (:result last-run))]
      (-> last-run
          (assoc-in [:request-signature :request] request*)
          (assoc :result result*)))
    last-run))
