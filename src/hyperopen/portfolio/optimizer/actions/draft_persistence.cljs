(ns hyperopen.portfolio.optimizer.actions.draft-persistence
  "Per-wallet draft restore: route entry (and the holdings-arrival watcher) funnel
  through restore-or-preseed, whose effect reads the persisted `draft::<address>`
  IndexedDB record first — a restored draft always beats the machine preseed — and
  falls back to the holdings preseed only when nothing was persisted."
  (:require [cljs.spec.alpha :as s]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
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

(defn hydrate-portfolio-optimizer-draft
  "Apply a persisted draft record to state. Runs the contract migration and spec
  before writing, and only writes while the in-memory draft is still untouched, so
  a slow IndexedDB read can never clobber input the user typed in the meantime.
  Restored universes re-request history (the route's initial history load saw the
  pre-restore empty draft)."
  [state record]
  (let [draft (get-in state contracts/draft-path)
        migrated (when (usable-draft-record? record)
                   (contracts/migrate-draft (:draft record)))]
    (if (and (optimizer-defaults/untouched-draft? draft)
             (s/valid? ::contracts/draft migrated))
      (cond-> [[:effects/save-many
                [[contracts/draft-path migrated]
                 [contracts/draft-persist-path
                  {:status :saved
                   :at-ms (:saved-at-ms record)}]]]]
        (seq (:universe migrated))
        (conj [:effects/load-portfolio-optimizer-history]))
      [])))
