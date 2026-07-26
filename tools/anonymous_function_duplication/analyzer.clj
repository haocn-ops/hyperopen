(ns tools.anonymous-function-duplication.analyzer
  (:require [clojure.walk :as walk]
            [edamame.core :as ed]
            [tools.anonymous-function-duplication.filesystem :as fs]))

(def parse-opts
  {:all true
   :features #{:cljs}
   :auto-resolve {:current 'user}
   :current 'user
   :readers {'js identity
             'inst identity
             'uuid identity}
   :row-key :row
   :col-key :col
   :end-row-key :end-row
   :end-col-key :end-col})

(defn anon-fn-form?
  [x]
  (and (seq? x)
       (symbol? (first x))
       (contains? #{"fn" "fn*"} (name (first x)))))

(defn parse-fn-arities
  [form]
  (let [tail (rest form)
        tail (if (symbol? (first tail)) (rest tail) tail)]
    (cond
      (empty? tail) []
      (vector? (first tail)) [{:args (first tail) :body (rest tail)}]
      :else (->> tail
                 (filter seq?)
                 (filter #(vector? (first %)))
                 (map (fn [clause]
                        {:args (first clause)
                         :body (rest clause)}))))))

(defn canonicalize-arity
  [{:keys [args body]}]
  (let [arg-syms (->> args
                      (remove #{'&})
                      (filter symbol?))
        replacements (zipmap arg-syms
                             (map (fn [idx] (symbol (str "arg" idx))) (range)))
        canon-body (walk/postwalk
                    (fn [x]
                      (if (symbol? x)
                        (get replacements x x)
                        x))
                    (cons 'do body))]
    {:argc (count arg-syms)
     :canon (pr-str canon-body)
     :size (count (tree-seq coll? seq canon-body))}))

(defn collect-records-for-file
  [root file]
  (let [text (slurp file)
        forms (ed/parse-string-all text parse-opts)
        acc (transient [])
        rel-file (fs/relativize root file)]
    (doseq [form forms]
      (walk/postwalk
       (fn [x]
         (when (anon-fn-form? x)
           (let [row (:row (meta x))]
             (doseq [arity (parse-fn-arities x)]
               (let [{:keys [argc canon size]} (canonicalize-arity arity)]
                 (conj! acc {:file rel-file
                             :row row
                             :argc argc
                             :canon canon
                             :size size})))))
         x)
       form))
    (persistent! acc)))

(defn scope-directories
  [scope usage-fn]
  (case scope
    "src" ["src"]
    "test" ["test"]
    "all" ["src" "test"]
    (throw (ex-info (str "Unsupported --scope: " scope)
                    {:usage (usage-fn)}))))

(defn build-report
  [{:keys [root scope top-files top-groups usage-fn]}]
  (let [scope-dirs (scope-directories scope usage-fn)
        files (->> scope-dirs
                   (mapcat fs/cljs-files-under)
                   sort)
        results (map (fn [f]
                       (try
                         {:file f
                          :records (collect-records-for-file root f)}
                         (catch Throwable t
                           {:file f
                            :error (.getMessage t)})))
                     files)
        errors (->> results
                    (filter :error)
                    (map (fn [{:keys [file error]}]
                           {:file (fs/relativize root file)
                            :error error}))
                    vec)
        records (->> results
                     (remove :error)
                     (mapcat :records)
                     vec)
        grouped (->> records
                     (group-by (juxt :argc :canon))
                     (map (fn [[[argc canon] items]]
                            {:argc argc
                             :canon canon
                             :size (:size (first items))
                             :occurrence-count (count items)
                             :file-count (count (distinct (map :file items)))
                             :locations (->> items
                                             (sort-by (juxt :file :row))
                                             (map (fn [it]
                                                    (str (:file it) ":" (or (:row it) "?"))))
                                             vec)}))
                     vec)
        duplicate-groups (->> grouped (filter #(> (:occurrence-count %) 1)) vec)
        large-duplicate-groups (->> duplicate-groups (filter #(>= (:size %) 10)) vec)
        cross-file-duplicate-groups (->> duplicate-groups (filter #(> (:file-count %) 1)) vec)
        top-files-data (->> records
                            (group-by :file)
                            (map (fn [[file recs]]
                                   {:file file
                                    :lambda-arity-count (count recs)}))
                            (sort-by (juxt (comp - :lambda-arity-count) :file))
                            (take top-files)
                            vec)
        top-groups-data (->> grouped
                             (filter #(>= (:occurrence-count %) 2))
                             (sort-by (juxt (comp - :occurrence-count)
                                            (comp - :file-count)
                                            (comp - :size)
                                            :canon))
                             (take top-groups)
                             vec)]
    {:root root
     :scope scope
     :scanned-files (count files)
     :errors errors
     :total-lambda-arities (count records)
     :duplicate-groups (count duplicate-groups)
     :duplicate-occurrences (reduce + 0 (map :occurrence-count duplicate-groups))
     :cross-file-duplicate-groups (count cross-file-duplicate-groups)
     :large-duplicate-groups-size>=10 (count large-duplicate-groups)
     :top-files top-files-data
     :top-groups top-groups-data}))
