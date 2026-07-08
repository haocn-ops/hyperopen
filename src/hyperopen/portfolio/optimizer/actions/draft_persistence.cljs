(ns hyperopen.portfolio.optimizer.actions.draft-persistence
  "Per-wallet draft restore: route entry (and the holdings-arrival watcher) funnel
  through restore-or-preseed, whose effect reads the persisted `draft::<address>`
  IndexedDB record first — a restored draft always beats the machine preseed — and
  falls back to the holdings preseed only when nothing was persisted."
  (:require [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]
            [hyperopen.portfolio.routes :as portfolio-routes]))

(def draft-record-version 1)

(defn restore-or-preseed-portfolio-optimizer-draft
  "Entry funnel for the opinionated default path on /portfolio/optimize/new: while
  the in-memory draft is untouched, ask the restore effect to hydrate the
  persisted per-wallet draft or, failing that, preseed from holdings. No-op on
  every other route and once the draft carries any input."
  [state path]
  (let [route (portfolio-routes/parse-portfolio-route path)
        draft (get-in state contracts/draft-path)]
    (if (and (= :optimize-new (:kind route))
             (optimizer-defaults/untouched-draft? draft))
      [[:effects/restore-portfolio-optimizer-draft]]
      [])))

(defn- usable-draft-record?
  [record]
  (and (map? record)
       (= draft-record-version (:version record))
       (map? (:draft record))))

(def ^:private required-draft-keys
  #{:universe
    :objective
    :return-model
    :risk-model
    :constraints
    :execution-assumptions
    :history-assumptions
    :metadata})

(defn- contains-required-draft-keys?
  [draft]
  (every? #(contains? draft %) required-draft-keys))

(defn- non-blank-string?
  [value]
  (and (string? value)
       (boolean (re-find #"\S" value))))

(defn- instrument?
  [value]
  (and (map? value)
       (non-blank-string? (:instrument-id value))))

(defn- instrument-vector?
  [value]
  (and (vector? value)
       (every? instrument? value)))

(defn- allowed-status?
  [status]
  (or (nil? status)
      (contains? contracts/draft-statuses status)))

(defn- optimizer-model-kind?
  [allowed value]
  (and (map? value)
       (contains? allowed (:kind value))))

(defn- history-assumption-entry?
  [entry]
  (and (map? entry)
       (contains? contracts/history-assumption-behaviors (:behavior entry))
       (case (:behavior entry)
         :proxy (map? (:proxy entry))
         :conservative true
         false)))

(defn- history-assumptions?
  [value]
  (and (map? value)
       (every? (fn [[instrument-id entry]]
                 (and (non-blank-string? instrument-id)
                      (history-assumption-entry? entry)))
               value)))

(defn- restorable-draft?
  [draft]
  (and (map? draft)
       (= contracts/draft-schema-version (:schema-version draft))
       (contains-required-draft-keys? draft)
       (allowed-status? (:status draft))
       (instrument-vector? (:universe draft))
       (or (nil? (:proxy-reference-instruments draft))
           (instrument-vector? (:proxy-reference-instruments draft)))
       (optimizer-model-kind? contracts/objective-kinds (:objective draft))
       (optimizer-model-kind? contracts/return-model-kinds (:return-model draft))
       (optimizer-model-kind? contracts/risk-model-kinds (:risk-model draft))
       (map? (:constraints draft))
       (map? (:execution-assumptions draft))
       (history-assumptions? (:history-assumptions draft))
       (map? (:metadata draft))))

(defn hydrate-portfolio-optimizer-draft
  "Apply a persisted draft record to state. Runs the contract migration and a
  lightweight persisted-shape guard before writing, and only writes while the
  in-memory draft is still untouched, so a slow IndexedDB read can never clobber
  input the user typed in the meantime.
  Restored universes re-request history (the route's initial history load saw the
  pre-restore empty draft)."
  [state record]
  (let [draft (get-in state contracts/draft-path)
        migrated (when (usable-draft-record? record)
                   (contracts/migrate-draft (:draft record)))]
    (if (and (optimizer-defaults/untouched-draft? draft)
             (restorable-draft? migrated))
      (cond-> [[:effects/save-many
                [[contracts/draft-path migrated]
                 [contracts/draft-persist-path
                  {:status :saved
                   :at-ms (:saved-at-ms record)}]]]]
        (seq (:universe migrated))
        (conj [:effects/load-portfolio-optimizer-history]))
      [])))
