(ns hyperopen.account.spectate-watchlist-io
  "Pure serialize/parse/merge helpers for spectate-mode watchlist import and
  export. No state and no DOM access live here, so every outcome (added,
  duplicate, invalid, empty) is unit-testable by value."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [hyperopen.account.context :as account-context]))

(def export-document-type
  "hyperopen-spectate-watchlist")

(def export-document-version
  1)

(defn- export-entry
  [entry]
  {:address (:address entry)
   :label (or (:label entry) "")})

(defn export-payload
  "Build the download payload for the current watchlist. Returns a map with the
  timestamped `:filename`, the entry `:count`, and the JSON `:document` envelope
  that an effect adapter serializes and downloads."
  [watchlist now-ms]
  (let [entries (mapv export-entry (account-context/normalize-watchlist watchlist))]
    {:filename (str "spectate-watchlist-" now-ms ".json")
     :count (count entries)
     :document {:type export-document-type
                :version export-document-version
                :exported-at now-ms
                :entries entries}}))

(defn extract-entries
  "Pull the candidate entry sequence out of parsed import data. Accepts the full
  `{type,version,entries}` envelope, a bare array of entries, or a bare array of
  address strings. Returns nil when the structure is unusable. Keys may be
  strings (parsed JSON) or keywords."
  [data]
  (cond
    (map? data) (let [entries (or (get data "entries") (get data :entries))]
                  (when (sequential? entries)
                    entries))
    (sequential? data) data
    :else nil))

(defn merge-imported
  "Merge parsed import `data` into `existing` watchlist. Imported entries are
  upserted by address; a non-blank imported label wins, otherwise the existing
  label is preserved. Returns either
    {:status :ok :watchlist <merged> :new-count <n> :total <t>}
  or
    {:status :error :reason :invalid|:empty}."
  [existing data]
  (if-let [candidate (extract-entries data)]
    (let [imported (account-context/normalize-watchlist candidate)]
      (if (empty? imported)
        {:status :error :reason :empty}
        (let [existing* (account-context/normalize-watchlist existing)
              existing-addresses (set (map :address existing*))
              merged (reduce (fn [acc entry]
                               (account-context/upsert-watchlist-entry
                                acc
                                (:address entry)
                                (:label entry)
                                (str/blank? (:label entry))))
                             existing*
                             imported)
              merged-addresses (set (map :address merged))]
          {:status :ok
           :watchlist merged
           :new-count (count (set/difference merged-addresses existing-addresses))
           :total (count merged)})))
    {:status :error :reason :invalid}))

(defn- pluralize-addresses
  [n]
  (if (= 1 n)
    (str n " address")
    (str n " addresses")))

(defn export-success-message
  [n]
  (str "Exported " (pluralize-addresses n) "."))

(def export-empty-message
  "No saved addresses to export.")

(defn import-success-message
  [new-count total]
  (if (pos? new-count)
    (str "Imported " (pluralize-addresses new-count) ". " total " saved.")
    (str "Watchlist already up to date. " total " saved.")))

(defn import-error-message
  [reason]
  (case reason
    :empty "Import failed: no valid wallet addresses found in file."
    "Import failed: file is not valid JSON."))
