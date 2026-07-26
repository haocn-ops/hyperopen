(ns tools.anonymous-function-duplication.cli-options)

(def default-top-files 25)
(def default-top-groups 20)

(defn usage []
  (str "Usage: bb tools/anonymous_function_duplication_report.clj --scope <src|test|all> [--top-files N] [--top-groups N]\n"
       "Example: bb tools/anonymous_function_duplication_report.clj --scope src --top-files 20 --top-groups 15"))

(defn parse-int
  [s fallback]
  (try
    (Integer/parseInt (str s))
    (catch Throwable _ fallback)))

(defn parse-args
  [args]
  (loop [remaining args
         opts {:scope nil
               :top-files default-top-files
               :top-groups default-top-groups}]
    (if (empty? remaining)
      opts
      (let [[k v & tail] remaining]
        (cond
          (= k "--scope")
          (recur tail (assoc opts :scope v))

          (= k "--top-files")
          (recur tail (assoc opts :top-files (parse-int v default-top-files)))

          (= k "--top-groups")
          (recur tail (assoc opts :top-groups (parse-int v default-top-groups)))

          :else
          (throw (ex-info (str "Unknown argument: " k) {:usage (usage)})))))))
