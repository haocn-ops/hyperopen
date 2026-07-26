(ns tools.mutate.source
  (:require [clojure.string :as str]
            [edamame.core :as ed]
            [tools.mutate.literals :as literals]
            [tools.mutate.mutations :as mutations]))

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

(defn read-source-forms
  [source-str]
  (ed/parse-string-all source-str
                       (assoc parse-opts
                              :postprocess
                              (fn [{:keys [obj loc]}]
                                (cond
                                  (instance? clojure.lang.IObj obj)
                                  (with-meta obj (merge (meta obj) loc))

                                  (literals/positionless-literal? obj)
                                  (literals/make-literal-node obj loc)

                                  :else
                                  obj)))))

(defn- form-kind
  [form]
  (cond
    (and (seq? form) (symbol? (first form))) (name (first form))
    (map? form) "map"
    (vector? form) "vector"
    (set? form) "set"
    :else "literal"))

(defn- sanitize-id-token
  [value]
  (-> value
      str
      (str/replace #"\s+" "-")))

(defn- form-id
  [idx form]
  (let [head (when (seq? form) (first form))
        second-form (when (seq? form) (second form))]
    (cond
      (and (= 'ns head) second-form)
      (str "ns/" (sanitize-id-token second-form))

      (and (contains? #{'def 'defn 'defn- 'defmulti} head)
           (symbol? second-form))
      (str (name head) "/" second-form)

      (and (= 'defmethod head)
           (symbol? second-form))
      (str "defmethod/" second-form "/" (sanitize-id-token (nth form 2 nil)))

      :else
      (str "form/" idx "/" (form-kind form)))))

(defn top-level-form-manifest
  [forms]
  (mapv (fn [idx form]
           {:id (form-id idx form)
            :kind (form-kind form)
            :line (-> form meta :row)
            :end-line (-> form meta :end-row)
           :hash (str (hash (pr-str (literals/stable-form form))))})
        (range)
        forms))

(defn module-hash
  [forms]
  (str (hash (mapv :hash (top-level-form-manifest forms)))))

(defn discover-all-mutations
  [forms]
  (vec
   (mapcat (fn [idx form]
             (map #(assoc % :form-index idx)
                  (mutations/find-mutations form)))
           (range)
           forms)))

(defn filter-by-lines
  [sites lines]
  (if (seq lines)
    (vec (filter #(contains? lines (:line %)) sites))
    sites))

(defn filter-by-form-indices
  [sites form-indices]
  (if (seq form-indices)
    (vec (filter #(contains? form-indices (:form-index %)) sites))
    sites))

(defn token-pattern
  [token]
  (let [value (str token)]
    (or ({"=" (re-pattern "(?<![><=!])=(?!=)")
          "not=" (re-pattern "not=")
          ">" (re-pattern ">(?!=)")
          ">=" (re-pattern ">=")
          "<" (re-pattern "<(?!=)")
          "<=" (re-pattern "<=")} value)
        (when (re-matches #"\d+" value)
          (re-pattern (str "(?<!\\d|\\.)"
                           (java.util.regex.Pattern/quote value)
                           "(?!\\d|\\.)")))
        (when (re-matches #"[A-Za-z].*" value)
          (re-pattern (str "(?<![A-Za-z0-9_-])"
                           (java.util.regex.Pattern/quote value)
                           "(?![A-Za-z0-9_-])")))
        (re-pattern (str "(?<=[\\s(])"
                         (java.util.regex.Pattern/quote value)
                         "(?=[\\s)])")))))

(defn mutate-source-text
  [original-content site]
  (let [lines (str/split original-content #"\n" -1)
        line-number (:line site)
        line-index (dec (or line-number 0))]
    (when (or (neg? line-index) (>= line-index (count lines)))
      (throw (ex-info (str "Cannot locate mutation line for site " (:description site))
                      {:site site})))
    (let [line (nth lines line-index)
          pattern (token-pattern (:original site))
          col (:column site)
          replaced (if col
                     (let [search-start (max 0 (- col 2))
                           prefix (subs line 0 search-start)
                           suffix (subs line search-start)
                           new-suffix (str/replace-first suffix pattern (str (:mutant site)))]
                       (str prefix new-suffix))
                     (str/replace-first line pattern (str (:mutant site))))]
      (when (= replaced line)
        (throw (ex-info (str "Failed to mutate source text for " (:description site))
                        {:site site})))
      (->> (assoc lines line-index replaced)
           (str/join "\n")))))
