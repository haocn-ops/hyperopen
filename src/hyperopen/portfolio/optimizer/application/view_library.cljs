(ns hyperopen.portfolio.optimizer.application.view-library
  "Pure logic for the per-wallet return-view library: the user's remembered absolute
  return views, keyed by instrument-id, that survive preset switches, draft resets,
  and reloads. Only user-authored views enter the library — implied (baseline)
  returns are derived data and never stored. Persistence and timestamps live in the
  effect/boundary layer; this namespace is pure and side-effect free."
  (:require [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as bl-model]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def library-version 1)

(defn- absolute-view?
  [view]
  (= :absolute (coercion/normalize-keyword-like (:kind view))))

(defn entry
  "Library entry for one authored view. `updated-at-ms` is supplied by the effect
  layer at write time."
  [{:keys [instrument-id return confidence-level]} updated-at-ms]
  (when (and (coercion/non-blank-text instrument-id)
             (coercion/finite-number? return))
    {:instrument-id instrument-id
     :return return
     :confidence-level (bl-model/normalize-confidence-level confidence-level)
     :updated-at-ms updated-at-ms}))

(defn view->upsert
  "The upsert payload the row-edit actions hand the sync effect (no timestamp —
  the effect stamps it)."
  [view]
  (when (and (absolute-view? view)
             (coercion/non-blank-text (:instrument-id view))
             (coercion/finite-number? (:return view)))
    {:instrument-id (:instrument-id view)
     :return (:return view)
     :confidence-level (bl-model/normalize-confidence-level (:confidence-level view))}))

(defn apply-sync
  "Apply a sync payload ({:upserts [...] :removes [...]}) to the entries map.
  `now-ms` stamps every upsert."
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

(defn- entry->view
  "Materialize a library entry as a draft absolute view. The id is assigned by the
  caller (ids are draft-scoped)."
  [entry* id]
  (let [confidence-level (bl-model/normalize-confidence-level (:confidence-level entry*))
        confidence (bl-model/confidence-weight confidence-level)]
    {:id id
     :kind :absolute
     :instrument-id (:instrument-id entry*)
     :return (:return entry*)
     :confidence-level confidence-level
     :confidence confidence
     :confidence-variance (bl-model/confidence-variance confidence)
     :horizon :3m
     :weights {(:instrument-id entry*) 1}}))

(defn hydration-gap?
  "True when some universe instrument has NO authored absolute view but the
  library remembers one — the state the hydrate watcher gap-fills. Mirrors
  `hydrate-views` without building the merged vector."
  [views entries universe]
  (let [entries* (or entries {})
        covered (set (keep (fn [view]
                             (when (absolute-view? view)
                               (:instrument-id view)))
                           views))]
    (boolean (some (fn [instrument]
                     (let [id (:instrument-id instrument)]
                       (and id
                            (not (contains? covered id))
                            (contains? entries* id))))
                   universe))))

(defn hydrate-views
  "Merge remembered views into a draft's view vector for the given universe:
  existing draft views always win (they are at least as fresh — every edit writes
  both); library entries fill the gaps for universe instruments without a view.
  Relative views pass through untouched."
  [views entries universe]
  (let [views* (vec (or views []))
        universe-ids (set (keep :instrument-id universe))
        covered (set (keep (fn [view]
                             (when (absolute-view? view)
                               (:instrument-id view)))
                           views*))
        missing (->> universe
                     (keep :instrument-id)
                     (remove covered)
                     (keep #(get entries %)))]
    (reduce (fn [acc entry*]
              (if (contains? universe-ids (:instrument-id entry*))
                (conj acc (entry->view entry* (bl-model/next-view-id acc)))
                acc))
            views*
            missing)))
