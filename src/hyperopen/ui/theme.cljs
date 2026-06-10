(ns hyperopen.ui.theme
  "UI theme catalog and data-theme attribute ownership.

   Ids and labels must mirror src/styles/themes/palette.js (the styling
   source of truth); tools/styles/theme_css_sync.test.mjs fails on drift.
   See docs/THEMING.md."
  (:require [clojure.string :as str]))

(def themes
  [{:id "dark" :label "HyperOpen"}
   {:id "institutional" :label "Institutional"}
   {:id "hyperdegen" :label "HyperDegen"}])

(def default-theme-id "dark")

(def ^:private theme-ids
  (into #{} (map :id) themes))

(def local-storage-key
  "hyperopen-ui-theme")

(defn normalize-theme-id
  [value]
  (let [candidate (-> (or value default-theme-id)
                      str
                      str/trim
                      str/lower-case)]
    (if (contains? theme-ids candidate)
      candidate
      default-theme-id)))

(defn apply-theme-attribute!
  "Sets data-theme on <html>; both daisyui and the --ho-* token blocks key
   off this attribute. Returns the normalized theme id."
  [theme-id]
  (let [normalized (normalize-theme-id theme-id)]
    (when (exists? js/document)
      (set! (.-theme (.-dataset (.-documentElement js/document))) normalized))
    normalized))

(defn active-theme-id
  [state]
  (normalize-theme-id (get-in state [:ui :theme])))
