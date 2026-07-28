(ns hyperopen.ui.theme
  "UI theme catalog and data-theme attribute ownership.

   Ids and labels must mirror src/styles/themes/palette.js (the styling
   source of truth); tools/styles/theme_css_sync.test.mjs fails on drift.
   See docs/THEMING.md."
  (:require [clojure.string :as str]))

(def themes
  [{:id "dark" :label "HyperLiquid"}
   {:id "institutional" :label "Institutional"}
   {:id "hyperdegen" :label "HyperDegen"}])

(def default-theme-id "dark")

(def ^:private theme-ids
  (into #{} (map :id) themes))

(def local-storage-key
  "hyperopen-ui-theme")

(defn- theme-id-token
  [value]
  (-> (cond
        (keyword? value) (name value)
        (nil? value) default-theme-id
        :else (str value))
      str/trim
      str/lower-case))

(defn valid-theme-id?
  [value]
  (and (some? value)
       (contains? theme-ids (theme-id-token value))))

(defn normalize-theme-id
  [value]
  (let [candidate (theme-id-token value)]
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
  (let [ui-theme (get-in state [:ui :theme])
        tenant-theme (get-in state [:tenant/override :theme/id])]
    (cond
      (valid-theme-id? ui-theme) (normalize-theme-id ui-theme)
      (valid-theme-id? tenant-theme) (normalize-theme-id tenant-theme)
      :else default-theme-id)))
