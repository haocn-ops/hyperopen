(ns hyperopen.portfolio.optimizer.application.constraint-profiles
  "Pure logic for remembered constraint profiles. A profile is the positioning policy (the
  constraint map) the trader last saved as their default for a given universe. Profiles are kept
  in a map keyed by a stable hash of the universe's instrument-id set, so the same universe gets
  the same remembered policy across sessions, while a materially different universe does not
  silently inherit it. Persistence and timestamps live in the effect/boundary layer; this
  namespace is pure and side-effect free."
  (:require [hyperopen.portfolio.optimizer.contracts.migrations :as migrations]))

(def profile-version 1)

(defn universe-key
  "Stable key for a draft universe: a hash of its sorted, de-duplicated instrument-id set.
  Returns nil for an empty universe so no profile is keyed to 'no assets'."
  [universe]
  (let [ids (->> universe
                 (keep :instrument-id)
                 (map str)
                 distinct
                 sort
                 vec)]
    (when (seq ids)
      (str "u" (hash ids)))))

(defn select-profile
  [profiles universe-key]
  (when (and universe-key (map? profiles))
    (get profiles universe-key)))

(defn remembered-constraints
  "The remembered constraint map for a universe, or nil. Profiles saved before
  the net band became a percentage of gross are migrated on read (the stored
  absolute net-min/net-max spread converts against the saved gross target)."
  [profiles universe-key]
  (some-> (:controls (select-profile profiles universe-key))
          migrations/migrate-constraints-net-band))

(defn profile-record
  "Build the stored profile record. `now-ms` is supplied by the effect layer."
  [constraints universe-key now-ms]
  {:universe-key universe-key
   :controls constraints
   :version profile-version
   :saved-at-ms now-ms})

(defn put-profile
  [profiles record]
  (assoc (or profiles {}) (:universe-key record) record))

(defn has-default?
  [profiles universe-key]
  (some? (select-profile profiles universe-key)))

(defn auto-apply-constraints
  "Constraints to auto-apply when an optimizer route loads: the remembered controls when a
  profile exists for the universe AND the draft is still pristine (not user-edited). Returns nil
  otherwise so user edits are never clobbered."
  [{:keys [profiles universe-key dirty?]}]
  (when-not dirty?
    (remembered-constraints profiles universe-key)))
