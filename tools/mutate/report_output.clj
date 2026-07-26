(ns tools.mutate.report-output
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [tools.mutate.filesystem :as fs]))

(defn suites-label
  [suites]
  (str/join "+" (map name suites)))

(defn- result-label
  [{:keys [result timeout?]}]
  (cond
    timeout? "TIMEOUT"
    (= :killed result) "KILLED"
    :else "SURVIVED"))

(defn- print-uncovered-sites
  [sites]
  (when (seq sites)
    (println (format "\nUncovered sites (%d):" (count sites)))
    (doseq [site sites]
      (println (format "  L%-4d %s"
                       (or (:line site) 0)
                       (:description site))))))

(defn- print-run-results
  [results]
  (when (seq results)
    (println)
    (doseq [[idx result] (map-indexed vector results)]
      (println (format "[%3d/%d] %-8s L%-4d %-12s %s"
                       (inc idx)
                       (count results)
                       (result-label result)
                       (or (get-in result [:site :line]) 0)
                       (suites-label (get-in result [:site :required-suites]))
                       (get-in result [:site :description]))))))

(defn print-text
  [{:keys [summary uncovered-sites results]}]
  (println (format "=== Mutation %s: %s ==="
                   (str/upper-case (name (:mode summary)))
                   (:module summary)))
  (println (format "Manifest path: %s" (:manifest-path summary)))
  (println (format "Report path: %s" (:artifact-path summary)))
  (println (format "Suite mode: %s" (name (:suite summary))))
  (println (format "Total mutation sites: %d" (:total-sites summary)))
  (println (format "Selected mutation sites: %d" (:selected-sites summary)))
  (println (format "Changed mutation sites: %d" (:changed-sites summary)))
  (if (:coverage-available? summary)
    (do
      (println (format "Covered mutation sites: %d" (:covered-sites summary)))
      (println (format "Uncovered mutation sites: %d" (:uncovered-sites summary))))
    (println "Coverage-aware counts unavailable. Run npm run coverage first."))
  (when-let [baseline-summary (:baseline summary)]
    (println)
    (println "Baselines:")
    (doseq [[suite {:keys [elapsed-ms timeout-ms]}] baseline-summary]
      (println (format "  %-7s %.2fs baseline, %.2fs timeout"
                       (name suite)
                       (/ elapsed-ms 1000.0)
                       (/ timeout-ms 1000.0)))))
  (print-uncovered-sites uncovered-sites)
  (print-run-results results)
  (when (= :run (:mode summary))
    (println)
    (println "Summary:")
    (println (format "  %d/%d mutants killed (%.1f%%)"
                     (:killed summary)
                     (:executed-sites summary)
                     (:kill-pct summary)))
    (when (pos? (:survived summary))
      (println "  Survivors:")
      (doseq [result (filter #(= :survived (:result %)) results)]
        (println (format "    L%-4d %-12s %s"
                         (or (get-in result [:site :line]) 0)
                         (suites-label (get-in result [:site :required-suites]))
                         (get-in result [:site :description])))))))

(defn print-json
  [report]
  (println (json/generate-string report {:pretty true})))

(defn save-report!
  [root module report]
  (let [path (fs/report-path root module (fs/now-stamp))
        report* (assoc-in report [:summary :artifact-path] path)]
    (spit path (pr-str report*))
    report*))
