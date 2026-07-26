(ns tools.anonymous-function-duplication.report-output)

(defn short-canon
  [canon]
  (subs canon 0 (min 220 (count canon))))

(defn print-report
  [{:keys [root
           scope
           scanned-files
           errors
           total-lambda-arities
           duplicate-groups
           duplicate-occurrences
           cross-file-duplicate-groups
           large-duplicate-groups-size>=10
           top-files
           top-groups]}]
  (println (str "root=" root))
  (println (str "scope=" scope))
  (println (str "scanned_files=" scanned-files))
  (println (str "parse_errors=" (count errors)))
  (doseq [{:keys [file error]} errors]
    (println (str "  error file=" file " msg=" error)))
  (println (str "total_lambda_arities=" total-lambda-arities))
  (println (str "duplicate_groups=" duplicate-groups))
  (println (str "duplicate_occurrences=" duplicate-occurrences))
  (println (str "cross_file_duplicate_groups=" cross-file-duplicate-groups))
  (println (str "large_duplicate_groups_size_ge_10=" large-duplicate-groups-size>=10))
  (println "")
  (println "top_files_by_lambda_arities:")
  (doseq [{:keys [file lambda-arity-count]} top-files]
    (println (str "  " lambda-arity-count " " file)))
  (println "")
  (println "top_duplicate_groups:")
  (doseq [{:keys [occurrence-count file-count argc size canon locations]} top-groups]
    (println (str "  count=" occurrence-count
                  " files=" file-count
                  " argc=" argc
                  " size=" size
                  " canon=" (short-canon canon)))
    (doseq [loc (take 8 locations)]
      (println (str "    - " loc)))
    (when (> (count locations) 8)
      (println "    - ..."))))
