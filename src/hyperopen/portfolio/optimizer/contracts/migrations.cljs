(ns hyperopen.portfolio.optimizer.contracts.migrations
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.domain.history-assumptions :as history-assumptions]))

(def draft-schema-version 1)
(def scenario-record-schema-version 1)
(def tracking-record-schema-version 1)

(defn- keyword-like
  [value]
  (coercion/normalize-keyword-like value))

(defn- normalize-position-side
  [value]
  (case (keyword-like value)
    :short :short
    :long))

(defn- shortable-instrument?
  [instrument]
  (cond
    (contains? instrument :shortable?)
    (true? (:shortable? instrument))

    (= :perp (keyword-like (or (:market-type instrument)
                               (:instrument-type instrument))))
    true

    :else false))

(defn- migrate-universe-instrument
  [instrument]
  (if (map? instrument)
    (let [shortable? (shortable-instrument? instrument)
          side (normalize-position-side (:position-side instrument))]
      (assoc instrument
             :position-side (if (and (= :short side)
                                     shortable?)
                              :short
                              :long)))
    instrument))

(defn- migrate-draft-universe
  [draft]
  (if (vector? (:universe draft))
    (update draft :universe #(mapv migrate-universe-instrument %))
    draft))

(defn- migrate-draft-history-assumptions
  [draft]
  ;; Additive backfill: drafts persisted before history assumptions existed lack
  ;; the key, which the strengthened ::draft spec now requires. Default to an
  ;; empty map; no schema-version bump is needed for a purely additive key.
  (update draft :history-assumptions #(or % {})))

(defn- migrate-history-assumption-entry
  [entry]
  ;; The :proxy history-assumption behavior was removed (it was collected but the
  ;; engine never consumed it). Convert any legacy proxy entry to a conservative
  ;; one - preserving the user's volatility/return, seeding the conservative
  ;; correlation floor + cap, and dropping the proxy-only fields - so the
  ;; conservative-only ::draft spec accepts it on load. Idempotent: a conservative
  ;; entry passes through unchanged.
  (if (= :proxy (:behavior entry))
    (-> entry
        (assoc :behavior :conservative
               :correlation-floor history-assumptions/conservative-correlation-floor)
        (update :max-weight #(if (coercion/positive-number? %)
                               %
                               history-assumptions/default-conservative-max-weight))
        (dissoc :proxy-instrument-id :relationship :implied-correlation))
    entry))

(defn- migrate-draft-history-assumption-behaviors
  [draft]
  (if (map? (:history-assumptions draft))
    (update draft :history-assumptions
            (fn [by-id]
              (reduce-kv (fn [acc id entry]
                           (assoc acc id (migrate-history-assumption-entry entry)))
                         {}
                         by-id)))
    draft))

(defn migrate-draft
  [draft]
  (let [draft* (or draft {})
        version (or (:schema-version draft*) draft-schema-version)]
    (case version
      1 (-> draft*
            migrate-draft-universe
            migrate-draft-history-assumptions
            migrate-draft-history-assumption-behaviors
            (assoc :schema-version draft-schema-version))
      (throw (ex-info "Unsupported optimizer draft schema version."
                      {:contract :optimizer/draft
                       :schema-version version})))))

(defn migrate-scenario-record
  [scenario-record]
  (let [record* (or scenario-record {})
        version (or (:schema-version record*) scenario-record-schema-version)]
    (case version
      1 (cond-> (assoc record* :schema-version scenario-record-schema-version)
          (map? (:config record*))
          (update :config migrate-draft))
      (throw (ex-info "Unsupported optimizer scenario record schema version."
                      {:contract :optimizer/scenario-record
                       :schema-version version})))))

(defn migrate-tracking-record
  [tracking-record]
  (let [record* (or tracking-record {})
        version (or (:schema-version record*) tracking-record-schema-version)]
    (case version
      1 (-> record*
            (assoc :schema-version tracking-record-schema-version)
            (update :snapshots #(vec (or % []))))
      (throw (ex-info "Unsupported optimizer tracking record schema version."
                      {:contract :optimizer/tracking-record
                       :schema-version version})))))

(defn migrate-contract
  [contract-id value]
  (case contract-id
    :optimizer/draft (migrate-draft value)
    :optimizer/scenario-record (migrate-scenario-record value)
    :optimizer/tracking-record (migrate-tracking-record value)
    (throw (ex-info "Unsupported optimizer contract migration."
                    {:contract contract-id}))))
