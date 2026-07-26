(ns dev.hiccup-lint
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [edamame.core :as edamame]))

(def root (.getCanonicalPath (io/file ".")))
(def src-dir (io/file root "src"))

(def ignored-dirs
  #{"node_modules" ".git" ".shadow-cljs" "out" "output" "tmp"})

(def parse-opts
  {:all true
   :read-cond :allow
   :features #{:cljs}
   :readers {'js identity
             'inst identity
             'uuid identity}})

(defn walk-cljs-files
  [root-dir]
  (letfn [(walk [^java.io.File dir]
            (->> (or (seq (.listFiles dir)) [])
                 (sort-by #(.getName ^java.io.File %))
                 (mapcat
                  (fn [^java.io.File entry]
                    (cond
                      (.isDirectory entry)
                      (if (contains? ignored-dirs (.getName entry))
                        []
                        (walk entry))

                      (and (.isFile entry)
                           (str/ends-with? (.getName entry) ".cljs"))
                      [(.getPath entry)]

                      :else
                      [])))))]
    (vec (walk (io/file root-dir)))))

(defn- rg-candidate-files
  [root-dir keyword]
  (try
    (let [{:keys [exit out]} (shell/sh "rg"
                                       "-l"
                                       "--glob"
                                       "*.cljs"
                                       keyword
                                       (.getPath (io/file root-dir)))]
      (when (<= exit 1)
        (->> (str/split-lines out)
             (remove str/blank?)
             (map #(-> (io/file %) .getPath))
             sort
             vec)))
    (catch Throwable _
      nil)))

(defn- fallback-candidate-files
  [root-dir keyword]
  (->> (walk-cljs-files root-dir)
       (filter (fn [file-path]
                 (str/includes? (slurp file-path) keyword)))
       sort
       vec))

(defn- candidate-cljs-files
  [root-dir keyword]
  (if-let [files (rg-candidate-files root-dir keyword)]
    files
    (fallback-candidate-files root-dir keyword)))

(defn- parse-forms-with-string-locations
  [^String text]
  (let [string-locs (java.util.IdentityHashMap.)
        forms (edamame/parse-string-all
               text
               (assoc parse-opts
                      :postprocess
                      (fn [{:keys [obj loc]}]
                        (when (string? obj)
                          (.put string-locs obj loc))
                        obj)))]
    {:forms forms
     :string-locs string-locs}))

(defn- string-line
  [^java.util.IdentityHashMap string-locs ^String value]
  (let [loc (.get string-locs value)]
    (if (map? loc)
      (or (:row loc) 1)
      1)))

(defn- collect-class-literals
  [value]
  (let [literals (transient [])]
    (letfn [(walk [x]
              (cond
                (string? x)
                (when (re-find #"\s" x)
                  (conj! literals x))

                (map? x)
                (doseq [[k v] x]
                  (walk k)
                  (walk v))

                (or (vector? x) (list? x) (set? x) (seq? x))
                (doseq [item x]
                  (walk item))

                :else
                nil))]
      (walk value)
      (persistent! literals))))

(defn- class-violations-in-forms
  [file-path forms ^java.util.IdentityHashMap string-locs]
  (let [violations (transient [])]
    (letfn [(walk [x]
              (cond
                (map? x)
                (doseq [[k v] x]
                  (when (= k :class)
                    (doseq [literal (collect-class-literals v)]
                      (conj! violations
                             {:file-path file-path
                              :line (string-line string-locs literal)
                              :literal literal})))
                  (walk k)
                  (walk v))

                (or (vector? x) (list? x) (set? x) (seq? x))
                (doseq [item x]
                  (walk item))

                :else
                nil))]
      (doseq [form forms]
        (walk form))
      (persistent! violations))))

(defn- style-map-string-key-violations-in-forms
  [file-path forms ^java.util.IdentityHashMap string-locs]
  (let [violations (transient [])]
    (letfn [(walk [x]
              (cond
                (map? x)
                (doseq [[k v] x]
                  (when (and (= k :style) (map? v))
                    (doseq [[style-k _] v]
                      (when (string? style-k)
                        (conj! violations
                               {:file-path file-path
                                :line (string-line string-locs style-k)
                                :literal style-k}))))
                  (walk k)
                  (walk v))

                (or (vector? x) (list? x) (set? x) (seq? x))
                (doseq [item x]
                  (walk item))

                :else
                nil))]
      (doseq [form forms]
        (walk form))
      (persistent! violations))))

(defn class-violations-in-text
  [file-path ^String text]
  (let [{:keys [forms string-locs]} (parse-forms-with-string-locations text)]
    (class-violations-in-forms file-path forms string-locs)))

(defn style-map-string-key-violations-in-text
  [file-path ^String text]
  (let [{:keys [forms string-locs]} (parse-forms-with-string-locations text)]
    (style-map-string-key-violations-in-forms file-path forms string-locs)))

(defn class-violations-in-file
  [file-path]
  (class-violations-in-text file-path (slurp file-path)))

(defn style-map-string-key-violations-in-file
  [file-path]
  (style-map-string-key-violations-in-text file-path (slurp file-path)))

(defn sort-violations
  [violations]
  (sort-by (juxt :file-path :line :literal) violations))

(defn relative-path
  [root-path file-path]
  (-> (.. (io/file root-path) toPath (relativize (.. (io/file file-path) toPath)))
      str
      (str/replace "\\" "/")))

(defn check-class-attrs!
  []
  (if-not (.exists src-dir)
    (do
      (println "No src directory found; skipping class attr check.")
      0)
    (let [violations (->> (candidate-cljs-files src-dir ":class")
                          (mapcat class-violations-in-file)
                          sort-violations
                          vec)]
      (if (seq violations)
        (do
          (binding [*out* *err*]
            (println "Found space-separated class strings in :class attrs:")
            (doseq [{:keys [file-path line literal]} violations]
              (println (str (relative-path root file-path)
                            ":" line
                            " "
                            (pr-str literal)))))
          1)
        (do
          (println "No space-separated class strings found in :class attrs.")
          0)))))

(defn check-style-map-string-keys!
  []
  (if-not (.exists src-dir)
    (do
      (println "No src directory found; skipping style key check.")
      0)
    (let [violations (->> (candidate-cljs-files src-dir ":style")
                          (mapcat style-map-string-key-violations-in-file)
                          sort-violations
                          vec)]
      (if (seq violations)
        (do
          (binding [*out* *err*]
            (println "Found string keys in literal :style maps:")
            (doseq [{:keys [file-path line literal]} violations]
              (println (str (relative-path root file-path)
                            ":" line
                            " "
                            (pr-str literal))))
            (println "Use keyword keys in :style maps, including CSS custom properties (example: :--slider-progress)."))
          1)
        (do
          (println "No string keys found in literal :style maps.")
          0)))))

(defn check-hiccup-attrs!
  []
  (let [class-status (check-class-attrs!)
        style-status (check-style-map-string-keys!)]
    (if (zero? (+ class-status style-status)) 0 1)))
