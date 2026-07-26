(ns tools.mutate.manifest
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [tools.mutate.filesystem :as fs]
            [tools.mutate.source :as source]))

(defn load-manifest
  [root module]
  (let [path (fs/manifest-path root module)
        file (io/file path)]
    (when (.exists file)
      (edn/read-string (slurp file)))))

(defn write-manifest!
  [root module manifest]
  (let [path (fs/manifest-path root module)]
    (fs/ensure-parent! path)
    (spit path (pr-str manifest))
    path))

(defn build-manifest
  [module forms timestamp last-run]
  {:version 1
   :module module
   :updated-at timestamp
   :module-hash (source/module-hash forms)
   :forms (source/top-level-form-manifest forms)
   :last-run last-run})

(defn changed-form-indices
  [forms prior-manifest]
  (let [current-forms (source/top-level-form-manifest forms)
        current-count (count current-forms)
        prior-forms (vec (:forms prior-manifest))
        current-hash (source/module-hash forms)
        module-unchanged? (and prior-manifest
                               (= current-hash (:module-hash prior-manifest)))
        changed (cond
                  (nil? prior-manifest)
                  (set (range current-count))

                  module-unchanged?
                  #{}

                  :else
                  (->> (range current-count)
                       (filter (fn [idx]
                                 (let [current (nth current-forms idx)
                                       prior (nth prior-forms idx nil)]
                                   (or (nil? prior)
                                       (not= (:id current) (:id prior))
                                       (not= (:hash current) (:hash prior))))))
                       set))
        new-forms (cond
                    (nil? prior-manifest)
                    (set (range current-count))

                    :else
                    (->> (range current-count)
                         (filter (fn [idx]
                                   (let [current (nth current-forms idx)
                                         prior (nth prior-forms idx nil)]
                                     (or (nil? prior)
                                         (not= (:id current) (:id prior))))))
                         set))]
    {:current-forms current-forms
     :module-unchanged? module-unchanged?
     :changed-form-indices changed
     :new-form-indices new-forms
     :changed-form-count (count changed)}))
