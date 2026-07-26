#!/usr/bin/env bb

(ns dev.check-namespace-boundaries
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def default-exceptions-path
  "dev/namespace_boundary_exceptions.edn")

(def default-source-dirs
  ["src"])

(def owner-pattern
  #"^[a-z][a-z0-9-]*$")

(defn utc-today
  []
  (java.time.LocalDate/now (java.time.ZoneId/of "UTC")))

(defn default-config
  []
  {:exceptions-path default-exceptions-path
   :source-dirs default-source-dirs
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
  [root rel-dir]
  (let [dir (io/file root rel-dir)]
    (if-not (.exists dir)
      []
      (->> (file-seq dir)
           (filter #(.isFile %))
           (map #(rel-path root %))
           (filter #(str/ends-with? % ".cljs"))
           sort
           vec))))

(defn scan-cljs-paths
  [root {:keys [source-dirs]}]
  (->> source-dirs
       (mapcat #(cljs-files-under root %))
       distinct
       sort
       vec))

(defn parse-date
  [s]
  (try
    (java.time.LocalDate/parse s)
    (catch Exception _ nil)))

(defn extract-balanced-form
  [text start-idx]
  (let [length (count text)]
    (loop [idx start-idx
           depth 0
           in-string? false
           escaped? false
           in-comment? false]
      (cond
        (>= idx length)
        nil

        in-comment?
        (let [ch (.charAt text idx)]
          (recur (inc idx)
                 depth
                 false
                 false
                 (not= ch \newline)))

        in-string?
        (let [ch (.charAt text idx)]
          (cond
            escaped?
            (recur (inc idx) depth true false false)

            (= ch \\)
            (recur (inc idx) depth true true false)

            (= ch \")
            (recur (inc idx) depth false false false)

            :else
            (recur (inc idx) depth true false false)))

        :else
        (let [ch (.charAt text idx)]
          (cond
            (= ch \;)
            (recur (inc idx) depth false false true)

            (= ch \")
            (recur (inc idx) depth true false false)

            (= ch \()
            (recur (inc idx) (inc depth) false false false)

            (= ch \))
            (let [next-depth (dec depth)]
              (if (zero? next-depth)
                (subs text start-idx (inc idx))
                (recur (inc idx) next-depth false false false)))

            :else
            (recur (inc idx) depth false false false)))))))

(defn extract-first-form
  [text]
  (let [length (count text)]
    (loop [idx 0
           in-string? false
           escaped? false
           in-comment? false]
      (cond
        (>= idx length)
        nil

        in-comment?
        (let [ch (.charAt text idx)]
          (recur (inc idx) false false (not= ch \newline)))

        in-string?
        (let [ch (.charAt text idx)]
          (cond
            escaped?
            (recur (inc idx) true false false)

            (= ch \\)
            (recur (inc idx) true true false)

            (= ch \")
            (recur (inc idx) false false false)

            :else
            (recur (inc idx) true false false)))

        :else
        (let [ch (.charAt text idx)]
          (cond
            (= ch \;)
            (recur (inc idx) false false true)

            (= ch \")
            (recur (inc idx) true false false)

            (= ch \()
            (extract-balanced-form text idx)

            :else
            (recur (inc idx) false false false)))))))

(defn find-require-start
  [ns-form]
  (let [matcher (re-matcher #"\(\s*:require\b" ns-form)]
    (when (.find matcher)
      (.start matcher))))

(defn top-level-vectors
  [text]
  (let [length (count text)]
    (loop [idx 0
           start nil
           depth 0
           in-string? false
           escaped? false
           in-comment? false
           acc []]
      (cond
        (>= idx length)
        acc

        in-comment?
        (let [ch (.charAt text idx)]
          (recur (inc idx)
                 start
                 depth
                 false
                 false
                 (not= ch \newline)
                 acc))

        in-string?
        (let [ch (.charAt text idx)]
          (cond
            escaped?
            (recur (inc idx) start depth true false false acc)

            (= ch \\)
            (recur (inc idx) start depth true true false acc)

            (= ch \")
            (recur (inc idx) start depth false false false acc)

            :else
            (recur (inc idx) start depth true false false acc)))

        :else
        (let [ch (.charAt text idx)]
          (cond
            (= ch \;)
            (recur (inc idx) start depth false false true acc)

            (= ch \")
            (recur (inc idx) start depth true false false acc)

            (= ch \[)
            (if (zero? depth)
              (recur (inc idx) (inc idx) 1 false false false acc)
              (recur (inc idx) start (inc depth) false false false acc))

            (= ch \])
            (if (= depth 1)
              (recur (inc idx)
                     nil
                     0
                     false
                     false
                     false
                     (conj acc (subs text start idx)))
              (recur (inc idx) start (max 0 (dec depth)) false false false acc))

            :else
            (recur (inc idx) start depth false false false acc)))))))

(defn parse-require-vector
  [vector-text]
  (when-let [[_ import-ns] (re-find #"^\s*([^ \t\r\n\[\]\{\}\(\)]+)" vector-text)]
    import-ns))

(defn required-namespaces
  [text]
  (if-let [ns-form (extract-first-form text)]
    (if-let [require-start (find-require-start ns-form)]
      (->> (extract-balanced-form ns-form require-start)
           top-level-vectors
           (keep parse-require-vector)
           distinct
           vec)
      [])
    []))

(defn read-exceptions
  [root rel-path]
  (let [file (io/file root rel-path)]
    (cond
      (not (.exists file))
      {:entries []
       :errors [(err :missing-boundary-exceptions-file
                     rel-path
                     "boundary exception registry is missing")]}

      :else
      (try
        {:entries (edn/read-string (slurp file))
         :errors []}
        (catch Exception ex
          {:entries []
           :errors [(err :invalid-boundary-exceptions-file
                         rel-path
                         (str "could not read EDN: " (.getMessage ex)))]})))))

(defn view-path?
  [rel-path]
  (str/starts-with? rel-path "src/hyperopen/views/"))

(defn domain-path?
  [rel-path]
  (str/includes? rel-path "/domain/"))

(defn view-import?
  [import-ns]
  (str/starts-with? import-ns "hyperopen.views."))

(defn violation-key
  [{:keys [path import-ns]}]
  [path import-ns])

(defn boundary-violations
  [root config]
  (->> (scan-cljs-paths root config)
       (mapcat (fn [path]
                 (let [imports (required-namespaces (slurp (io/file root path)))]
                   (for [import-ns imports
                         :when (and (not (view-path? path))
                                    (view-import? import-ns))]
                     {:path path
                      :import-ns import-ns}))))
       distinct
       vec))

(defn validate-exception-entry
  [registry-path scan-paths today idx entry]
  (let [entry-label (str "entry " (inc idx))
        path (:path entry)
        import-ns (:import-ns entry)
        owner (:owner entry)
        reason (:reason entry)
        retire-by-raw (:retire-by entry)
        retire-by (when (string? retire-by-raw)
                    (parse-date retire-by-raw))
        target-path (if (string? path) path registry-path)
        errors (-> []
                   (cond->
                     (not (map? entry))
                     (conj (err :invalid-boundary-exception
                                registry-path
                                (str entry-label " must be a map")))

                     (and (map? entry) (not (and (string? path) (seq path))))
                     (conj (err :invalid-boundary-exception
                                registry-path
                                (str entry-label " is missing :path")))

                     (and (string? path) (not (contains? scan-paths path)))
                     (conj (err :invalid-boundary-exception
                                target-path
                                "boundary exception path must point at a scanned .cljs namespace"))

                     (and (map? entry) (not (and (string? import-ns)
                                                 (view-import? import-ns))))
                     (conj (err :invalid-boundary-exception
                                target-path
                                (str entry-label " must declare a hyperopen.views.* :import-ns")))

                     (and (map? entry) (not (and (string? owner)
                                                 (re-matches owner-pattern owner))))
                     (conj (err :invalid-boundary-exception
                                target-path
                                (str entry-label " has invalid :owner")))

                     (and (map? entry) (not (and (string? reason)
                                                 (not (str/blank? reason)))))
                     (conj (err :invalid-boundary-exception
                                target-path
                                (str entry-label " has blank :reason")))

                     (and (map? entry) (nil? retire-by))
                     (conj (err :invalid-boundary-exception
                                target-path
                                (str entry-label " has invalid :retire-by; expected YYYY-MM-DD")))

                     (and retire-by (.isBefore retire-by today))
                     (conj (err :expired-boundary-exception
                                target-path
                                (str "boundary exception expired on " retire-by-raw)))

                     (and (string? path) (domain-path? path))
                     (conj (err :boundary-exception-forbidden
                                target-path
                                "domain namespaces cannot use boundary exceptions for hyperopen.views.* imports"))))]
    {:entry (when (empty? errors) entry)
     :errors errors}))

(defn validate-exceptions
  [registry-path data config scan-paths]
  (if-not (vector? data)
    {:entries []
     :errors [(err :invalid-boundary-exceptions-file
                   registry-path
                   "boundary exception registry must contain a vector of maps")]}
    (let [validated (map-indexed #(validate-exception-entry registry-path
                                                            scan-paths
                                                            (:today config)
                                                            %1
                                                            %2)
                                 data)
          entries (keep :entry validated)
          duplicate-keys (->> entries
                              (group-by violation-key)
                              (keep (fn [[key rows]]
                                      (when (> (count rows) 1)
                                        key)))
                              set)]
      {:entries (->> entries
                     (remove #(contains? duplicate-keys (violation-key %)))
                     vec)
       :errors (into []
                     (concat
                      (mapcat :errors validated)
                      (map (fn [[path import-ns]]
                             (err :duplicate-boundary-exception
                                  path
                                  (str "multiple boundary exception entries target " import-ns)))
                           (sort duplicate-keys))))})))

(defn check-repo
  ([root]
   (check-repo root (default-config)))
  ([root config]
   (let [scan-paths (set (scan-cljs-paths root config))
         violations (boundary-violations root config)
         violation-set (set (map violation-key violations))
         {:keys [entries errors]} (read-exceptions root (:exceptions-path config))
         raw-entries entries
         {:keys [entries validated-errors]} (let [{:keys [entries errors]} (validate-exceptions (:exceptions-path config)
                                                                                               raw-entries
                                                                                               config
                                                                                               scan-paths)]
                                              {:entries entries
                                               :validated-errors errors})
         validated-entries entries
         exception-keys (set (map violation-key validated-entries))]
     (-> []
         (into errors)
         (into validated-errors)
         (into (for [{:keys [path import-ns]} violations
                     :when (domain-path? path)]
                 (err :forbidden-domain-view-import
                      path
                      (str "domain namespace cannot import " import-ns))))
         (into (for [{:keys [path import-ns] :as violation} violations
                     :when (and (not (domain-path? path))
                                (not (contains? exception-keys (violation-key violation))))]
                 (err :missing-boundary-exception
                      path
                      (str "non-view namespace imports " import-ns
                           "; add a boundary exception or move the helper out of views/"))))
         (into (for [{:keys [path import-ns]} validated-entries
                     :when (not (contains? violation-set [path import-ns]))]
                 (err :stale-boundary-exception
                      path
                      (str "remove stale boundary exception for " import-ns))))))))

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
        (println "Namespace boundary check passed.")
        (System/exit 0))
      (do
        (print-errors! errors)
        (System/exit 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
