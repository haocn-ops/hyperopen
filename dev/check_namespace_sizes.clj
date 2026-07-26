#!/usr/bin/env bb

(ns dev.check-namespace-sizes
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def default-threshold
  500)

(def default-exceptions-path
  "dev/namespace_size_exceptions.edn")

(def default-scan-dirs
  ["src" "test"])

(def default-excluded-paths
  #{"test/test_runner_generated.cljs"})

(def owner-pattern
  #"^[a-z][a-z0-9-]*$")

(defn utc-today
  []
  (java.time.LocalDate/now (java.time.ZoneId/of "UTC")))

(defn default-config
  []
  {:threshold default-threshold
   :exceptions-path default-exceptions-path
   :scan-dirs default-scan-dirs
   :excluded-paths default-excluded-paths
   :today (utc-today)})

(defn normalize-path
  [path]
  (str/replace path "\\" "/"))

(defn err
  [code path message]
  {:code code :path path :message message})

(defn rel-path
  [root file]
  (normalize-path
   (.toString (.relativize (.toPath (io/file root))
                           (.toPath (io/file file))))))

(defn cljs-files-under
  [root rel-dir excluded-paths]
  (let [dir (io/file root rel-dir)]
    (if-not (.exists dir)
      []
      (->> (file-seq dir)
           (filter #(.isFile %))
           (map #(rel-path root %))
           (filter #(str/ends-with? % ".cljs"))
           (remove excluded-paths)
           sort
           vec))))

(defn scan-cljs-paths
  [root {:keys [scan-dirs excluded-paths]}]
  (->> scan-dirs
       (mapcat #(cljs-files-under root % excluded-paths))
       distinct
       sort
       vec))

(defn parse-date
  [s]
  (try
    (java.time.LocalDate/parse s)
    (catch Exception _ nil)))

(defn line-count
  [root rel-path]
  (with-open [reader (io/reader (io/file root rel-path))]
    (count (line-seq reader))))

(defn read-exceptions
  [root rel-path]
  (let [file (io/file root rel-path)]
    (cond
      (not (.exists file))
      {:entries []
       :errors [(err :missing-size-exceptions-file
                     rel-path
                     "size exception registry is missing")]}

      :else
      (try
        {:entries (edn/read-string (slurp file))
         :errors []}
        (catch Exception ex
          {:entries []
           :errors [(err :invalid-size-exceptions-file
                         rel-path
                         (str "could not read EDN: " (.getMessage ex)))]})))))

(defn validate-exception-entry
  [root registry-path scan-paths today idx entry]
  (let [entry-label (str "entry " (inc idx))
        path (:path entry)
        owner (:owner entry)
        reason (:reason entry)
        max-lines (:max-lines entry)
        retire-by-raw (:retire-by entry)
        retire-by (when (string? retire-by-raw)
                    (parse-date retire-by-raw))
        target-path (if (string? path) path registry-path)
        errors (cond-> []
                 (not (map? entry))
                 (conj (err :invalid-size-exception
                            registry-path
                            (str entry-label " must be a map")))

                 (and (map? entry) (not (and (string? path) (seq path))))
                 (conj (err :invalid-size-exception
                            registry-path
                            (str entry-label " is missing :path")))

                 (and (string? path) (not (contains? scan-paths path)))
                 (conj (err :invalid-size-exception
                            target-path
                            "exception path must point at a scanned .cljs namespace"))

                 (and (map? entry) (not (and (string? owner)
                                             (re-matches owner-pattern owner))))
                 (conj (err :invalid-size-exception
                            target-path
                            (str entry-label " has invalid :owner")))

                 (and (map? entry) (not (and (string? reason)
                                             (not (str/blank? reason)))))
                 (conj (err :invalid-size-exception
                            target-path
                            (str entry-label " has blank :reason")))

                 (and (map? entry) (not (pos-int? max-lines)))
                 (conj (err :invalid-size-exception
                            target-path
                            (str entry-label " has invalid :max-lines")))

                 (and (map? entry) (nil? retire-by))
                 (conj (err :invalid-size-exception
                            target-path
                            (str entry-label " has invalid :retire-by; expected YYYY-MM-DD")))

                 (and retire-by (.isBefore retire-by today))
                 (conj (err :expired-size-exception
                            target-path
                            (str "size exception expired on " retire-by-raw))))]
    {:entry (when (empty? errors) entry)
     :errors errors}))

(defn validate-exceptions
  [root registry-path data config scan-paths]
  (if-not (vector? data)
    {:entries []
     :errors [(err :invalid-size-exceptions-file
                   registry-path
                   "size exception registry must contain a vector of maps")]}
    (let [validated (map-indexed #(validate-exception-entry root
                                                            registry-path
                                                            scan-paths
                                                            (:today config)
                                                            %1
                                                            %2)
                                 data)
          entries (keep :entry validated)
          duplicate-paths (->> entries
                               (group-by :path)
                               (keep (fn [[path rows]]
                                       (when (> (count rows) 1)
                                         path)))
                               set)]
      {:entries (->> entries
                     (remove #(contains? duplicate-paths (:path %)))
                     vec)
       :errors (into []
                     (concat
                      (mapcat :errors validated)
                      (map #(err :duplicate-size-exception
                                 %
                                 "multiple size exception entries target the same namespace")
                           (sort duplicate-paths))))})))

(defn check-repo
  ([root]
   (check-repo root (default-config)))
  ([root config]
   (let [scan-paths (scan-cljs-paths root config)
         path-set (set scan-paths)
         lines-by-path (into {}
                             (map (fn [path]
                                    [path (line-count root path)]))
                             scan-paths)
         {:keys [entries errors]} (read-exceptions root (:exceptions-path config))
         {:keys [entries validated-errors]} (let [{:keys [entries errors]} (validate-exceptions root
                                                                                               (:exceptions-path config)
                                                                                               entries
                                                                                               config
                                                                                               path-set)]
                                              {:entries entries
                                               :validated-errors errors})
         threshold (:threshold config)
         exception-by-path (into {} (map (juxt :path identity) entries))]
     (-> []
         (into errors)
         (into validated-errors)
         (into (for [[path lines] lines-by-path
                     :when (and (> lines threshold)
                                (not (contains? exception-by-path path)))]
                 (err :missing-size-exception
                      path
                      (str "namespace has " lines " lines; add an exception entry in "
                           (:exceptions-path config)))))
         (into (for [{:keys [path max-lines]} entries
                     :let [lines (get lines-by-path path)]
                     :when (and lines
                                (<= lines threshold))]
                 (err :stale-size-exception
                      path
                      (str "namespace is now " lines " lines; remove the stale exception entry"))))
         (into (for [{:keys [path max-lines]} entries
                     :let [lines (get lines-by-path path)]
                     :when (and lines
                                (> lines max-lines))]
                 (err :size-exception-exceeded
                      path
                      (str "namespace has " lines " lines; exception allows at most " max-lines))))))))

(defn print-errors!
  [errors]
  (doseq [{:keys [code path message]} errors]
    (println (str "[" (name code) "] " path " - " message))))

(defn -main
  [& _args]
  (let [root (.getCanonicalPath (io/file "."))
        errors (check-repo root (default-config))]
    (if (empty? errors)
      (do
        (println "Namespace size check passed.")
        (System/exit 0))
      (do
        (print-errors! errors)
        (System/exit 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
