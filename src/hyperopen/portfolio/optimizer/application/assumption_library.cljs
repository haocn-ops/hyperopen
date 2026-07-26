(ns hyperopen.portfolio.optimizer.application.assumption-library
  "Pure logic for the per-wallet history-assumption library: the user's last
  authored assumption per instrument-id, durable across drafts, universe
  edits, and reloads. Once a trader has told the optimizer what a
  short-history asset behaves like, that answer follows the asset — removing
  it from the universe and re-adding it later (or preseeding a fresh draft)
  restores the authored entry instead of asking again. Works-in-progress are
  remembered too: any entry shape the draft holds is a valid library entry.

  Only an explicit Clear on the card forgets an asset; dropping it from the
  universe deliberately does not (re-adding is the whole point).

  A proxy entry may reference proxies outside the universe (reference-only
  instruments). Their resolved instrument maps ride the library entry so a
  hydrated basket can restore `:proxy-reference-instruments` (and prefetch
  their history) without a catalog round-trip.

  Persistence and timestamps live in the effect/boundary layer; this
  namespace is pure and side-effect free."
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.domain.history-assumptions :as history-assumptions]))

(def library-version 1)

(defn entry
  "Library entry for one asset's assumption. `reference-instruments` are the
  resolved instrument maps for the entry's out-of-universe proxies (may be
  empty); `updated-at-ms` is supplied by the effect layer at write time."
  [{:keys [instrument-id assumption reference-instruments]} updated-at-ms]
  (when (and (coercion/non-blank-text instrument-id)
             (map? assumption)
             (some? (:behavior assumption)))
    {:instrument-id instrument-id
     :entry assumption
     :reference-instruments (vec (or reference-instruments []))
     :updated-at-ms updated-at-ms}))

(defn entry-reference-instruments
  "The subset of a reference-instrument pool the given assumption entry
  actually references as proxies — what the library stores alongside it."
  [reference-instruments assumption]
  (let [proxy-ids (set (history-assumptions/proxy-instrument-ids assumption))]
    (filterv #(contains? proxy-ids (:instrument-id %))
             (or reference-instruments []))))

(defn apply-sync
  "Apply a sync payload ({:upserts [...] :removes [...]}) to the entries map.
  `now-ms` stamps every upsert. Upserts carry {:instrument-id :assumption
  :reference-instruments}; removes are instrument-ids."
  [entries {:keys [upserts removes]} now-ms]
  (let [entries* (reduce (fn [acc instrument-id]
                           (dissoc acc instrument-id))
                         (or entries {})
                         (keep coercion/non-blank-text removes))]
    (reduce (fn [acc upsert]
              (if-let [entry* (entry upsert now-ms)]
                (assoc acc (:instrument-id entry*) entry*)
                acc))
            entries*
            upserts)))

(defn library-record
  "The stored per-wallet record."
  [address entries]
  {:version library-version
   :address address
   :entries (or entries {})})

(defn usable-record?
  [record]
  (and (map? record)
       (= library-version (:version record))
       (map? (:entries record))))

(defn record->entries
  [record]
  (if (usable-record? record)
    (:entries record)
    {}))

(defn hydrate-assumptions
  "Gap-fill remembered assumptions into a draft: universe instruments with no
  draft entry take their library entry; existing draft entries always win
  (they are at least as fresh — every edit writes both). Returns
  {:assumptions ... :reference-instruments ... :new-reference-instruments ...}
  or nil when nothing was filled.

  Reference instruments stored on a hydrated entry are re-admitted only when
  the proxy is still outside the hydrating draft's universe and not already a
  reference instrument; the result vector keeps the reconcile convention
  (sorted by instrument-id, deduped). Carried-over references get the same
  universe check: an instrument that has since joined the universe is no
  longer reference-only (it is allocatable by right), so hydration prunes it
  instead of carrying the stale reference forward."
  [{:keys [assumptions universe entries reference-instruments]}]
  (let [assumptions* (or assumptions {})
        universe-ids (into #{} (keep :instrument-id) universe)
        hydrated (->> universe
                      (keep (fn [instrument]
                              (let [id (:instrument-id instrument)]
                                (when (and id
                                           (not (contains? assumptions* id)))
                                  (get entries id)))))
                      vec)]
    (when (seq hydrated)
      (let [existing-refs (into []
                                (remove #(contains? universe-ids
                                                    (:instrument-id %)))
                                (or reference-instruments []))
            existing-ref-ids (into #{} (keep :instrument-id) existing-refs)
            new-refs (->> hydrated
                          (mapcat :reference-instruments)
                          (filter (fn [ref-instrument]
                                    (let [ref-id (:instrument-id ref-instrument)]
                                      (and ref-id
                                           (not (contains? universe-ids ref-id))
                                           (not (contains? existing-ref-ids ref-id))))))
                          (reduce (fn [acc ref-instrument]
                                    (if (contains? acc (:instrument-id ref-instrument))
                                      acc
                                      (assoc acc (:instrument-id ref-instrument)
                                             ref-instrument)))
                                  {})
                          vals
                          vec)]
        {:assumptions (reduce (fn [acc library-entry]
                                (assoc acc
                                       (:instrument-id library-entry)
                                       (:entry library-entry)))
                              assumptions*
                              hydrated)
         :reference-instruments (->> (concat existing-refs new-refs)
                                     (sort-by :instrument-id)
                                     vec)
         :new-reference-instruments new-refs}))))
