(ns tools.mutate.nightly
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [tools.mutate.core :as core]
            [tools.mutate.filesystem :as fs]
            [tools.mutate.runner :as runner]))

(def default-config-path "tools/mutate/nightly_targets.edn")
(def default-coverage-command "npm run coverage")
(def nightly-root (str fs/target-root "/nightly"))

(defn usage
  []
  (str
   "Usage: bb tools/mutate_nightly.clj [options]\n"
   "Options:\n"
   "  --config PATH          Nightly target config path (default tools/mutate/nightly_targets.edn)\n"
   "  --skip-coverage        Reuse the existing coverage/lcov.info instead of rebuilding coverage\n"
   "  --limit N              Run only the first N configured targets\n"
   "  --format FORMAT        text or json (default text)\n"
   "  --help                 Print this help and exit\n"
   "Examples:\n"
   "  bb tools/mutate_nightly.clj\n"
   "  bb tools/mutate_nightly.clj --skip-coverage --limit 2"))

(defn usage-error
  [message]
  (throw (ex-info message {:usage (usage)})))

(defn- parse-int
  [value]
  (try
    (Integer/parseInt (str value))
    (catch Throwable _
      nil)))

(defn- parse-limit
  [value]
  (let [n (parse-int value)]
    (when-not (and n (pos? n))
      (usage-error "Invalid value for --limit. Expected a positive integer."))
    n))

(defn- validate-format
  [value]
  (when-not (contains? #{"text" "json"} value)
    (usage-error (str "Unsupported format: " value)))
  value)

(def default-options
  {:config default-config-path
   :skip-coverage false
   :limit nil
   :format "text"})

(defn parse-args
  [args]
  (if (some #{"--help"} args)
    {:help true
     :usage (usage)}
    (loop [remaining args
           opts default-options]
      (if (empty? remaining)
        opts
        (let [[key value & tail] remaining]
          (cond
            (= key "--config")
            (recur tail (assoc opts :config value))

            (= key "--skip-coverage")
            (recur (rest remaining) (assoc opts :skip-coverage true))

            (= key "--limit")
            (recur tail (assoc opts :limit (parse-limit value)))

            (= key "--format")
            (recur tail (assoc opts :format (validate-format value)))

            (str/starts-with? key "--")
            (usage-error (str "Unknown argument: " key))

            :else
            (usage-error (str "Unexpected positional argument: " key))))))))

(defn- nightly-dir
  [root timestamp]
  (let [dir (str (io/file root nightly-root (fs/sanitize-for-filename timestamp)))]
    (.mkdirs (io/file dir))
    dir))

(defn- summary-json-path
  [run-dir]
  (str (io/file run-dir "summary.json")))

(defn- summary-md-path
  [run-dir]
  (str (io/file run-dir "summary.md")))

(defn- target-suite
  [target]
  (or (:suite target) :auto))

(defn- validate-target
  [root idx target]
  (when-not (map? target)
    (usage-error (str "Nightly target at index " idx " must be a map.")))
  (let [module (fs/require-module-path root (:module target))
        suite (target-suite target)
        mutate-all (if (contains? target :mutate-all)
                     (boolean (:mutate-all target))
                     true)
        timeout-factor (or (:timeout-factor target) 10)]
    (when-not (contains? #{:auto :test :ws-test} suite)
      (usage-error (str "Nightly target suite must be one of :auto, :test, :ws-test: " suite)))
    (when-not (and (integer? timeout-factor) (pos? timeout-factor))
      (usage-error (str "Nightly target timeout-factor must be a positive integer for " module)))
    {:label (or (:label target) module)
     :module module
     :suite suite
     :mutate-all mutate-all
     :timeout-factor timeout-factor
     :coverage-file (or (:coverage-file target) "coverage/lcov.info")}))

(defn load-targets
  [root config-path]
  (let [file (io/file root config-path)]
    (when-not (.exists file)
      (usage-error (str "Nightly target config does not exist: " config-path)))
    (let [targets (edn/read-string (slurp file))]
      (when-not (vector? targets)
        (usage-error "Nightly target config must be an EDN vector of target maps."))
      (->> targets
           (map-indexed (fn [idx target] (validate-target root idx target)))
           vec))))

(defn select-targets
  [targets {:keys [limit]}]
  (vec (if limit
         (take limit targets)
         targets)))

(defn run-coverage!
  [{:keys [skip-coverage]}]
  (if skip-coverage
    {:status :skipped
     :command default-coverage-command
     :elapsed-ms 0
     :output ""}
    (let [result (runner/run-command default-coverage-command {})]
      (when-not (runner/success? result)
        (throw (ex-info "Nightly mutation coverage build failed."
                        {:command default-coverage-command
                         :output (:output result)})))
      {:status :rebuilt
       :command default-coverage-command
       :elapsed-ms (:elapsed-ms result)
       :output (:output result)})))

(defn- previous-run-dirs
  [root]
  (let [dir (io/file root nightly-root)]
    (->> (or (.listFiles dir) [])
         (filter #(.isDirectory ^java.io.File %))
         (map #(.getPath ^java.io.File %))
         sort)))

(defn latest-summary-path
  [root]
  (->> (previous-run-dirs root)
       reverse
       (map #(summary-json-path %))
       (filter #(.exists (io/file %)))
       first))

(defn load-latest-summary
  [root]
  (when-let [path (latest-summary-path root)]
    (assoc (json/parse-string (slurp path) true)
           :summary-path path)))

(defn- target-run-opts
  [target]
  {:module (:module target)
   :scan false
   :update-manifest false
   :lines nil
   :since-last-run false
   :mutate-all (:mutate-all target)
   :suite (:suite target)
   :timeout-factor (:timeout-factor target)
   :coverage-file (:coverage-file target)
   :format "json"})

(defn execute-target
  [target]
  (let [start-ms (System/currentTimeMillis)]
    (try
      (let [report (core/execute-command (target-run-opts target))
            elapsed-ms (- (System/currentTimeMillis) start-ms)
            summary (:summary report)]
        {:label (:label target)
         :module (:module target)
         :suite (:suite target)
         :mutate-all (:mutate-all target)
         :status :ok
         :duration-ms elapsed-ms
         :summary {:total-sites (:total-sites summary)
                   :selected-sites (:selected-sites summary)
                   :changed-sites (:changed-sites summary)
                   :covered-sites (:covered-sites summary)
                   :uncovered-sites (:uncovered-sites summary)
                   :executed-sites (:executed-sites summary)
                   :killed (:killed summary)
                   :survived (:survived summary)
                   :kill-pct (:kill-pct summary)}
         :manifest-path (:manifest-path summary)
         :artifact-path (:artifact-path summary)
         :uncovered-mutations (count (:uncovered-sites report))})
      (catch Throwable t
        {:label (:label target)
         :module (:module target)
         :suite (:suite target)
         :mutate-all (:mutate-all target)
         :status :error
         :duration-ms (- (System/currentTimeMillis) start-ms)
         :error-message (.getMessage t)
         :error-data (ex-data t)}))))

(defn compare-target
  [current previous]
  (cond
    (nil? previous)
    {:trend :new}

    (= :error (:status current))
    {:trend (if (= :error (:status previous)) :error :failed)
     :previous-status (:status previous)}

    (= :error (:status previous))
    {:trend :recovered
     :previous-status :error}

    :else
    (let [current-summary (:summary current)
          previous-summary (:summary previous)
          kill-pct-delta (- (or (:kill-pct current-summary) 0.0)
                            (or (:kill-pct previous-summary) 0.0))
          survivor-delta (- (or (:survived current-summary) 0)
                            (or (:survived previous-summary) 0))
          uncovered-delta (- (or (:uncovered-sites current-summary) 0)
                             (or (:uncovered-sites previous-summary) 0))
          executed-delta (- (or (:executed-sites current-summary) 0)
                            (or (:executed-sites previous-summary) 0))
          trend (cond
                  (or (neg? kill-pct-delta)
                      (pos? survivor-delta)
                      (pos? uncovered-delta)) :regressed
                  (or (pos? kill-pct-delta)
                      (neg? survivor-delta)
                      (neg? uncovered-delta)) :improved
                  :else :unchanged)]
      {:trend trend
       :previous-status :ok
       :kill-pct-delta kill-pct-delta
       :survivor-delta survivor-delta
       :uncovered-delta uncovered-delta
       :executed-delta executed-delta
       :previous-kill-pct (:kill-pct previous-summary)})))

(defn annotate-with-previous
  [current-targets previous-summary]
  (let [previous-targets (into {}
                               (map (juxt :module identity))
                               (:targets previous-summary))]
    (mapv (fn [target]
            (assoc target :comparison (compare-target target (get previous-targets (:module target)))))
          current-targets)))

(defn- weakness-key
  [target]
  (let [summary (:summary target)
        status (:status target)
        trend (get-in target [:comparison :trend])]
    [(case status
       :error 0
       1)
     (case trend
       :failed 0
       :regressed 1
       2)
     (or (:kill-pct summary) 1000.0)
     (- (or (:survived summary) 0))
     (- (or (:uncovered-sites summary) 0))
     (str (:module target))]))

(defn rank-targets
  [targets]
  (vec (sort-by weakness-key targets)))

(defn build-summary
  [opts run-info coverage-result targets previous-summary]
  (let [annotated-targets (annotate-with-previous targets previous-summary)
        successful-targets (filter #(= :ok (:status %)) annotated-targets)
        failed-targets (filter #(= :error (:status %)) annotated-targets)
        overall-executed (reduce + 0 (map #(get-in % [:summary :executed-sites] 0) successful-targets))
        overall-killed (reduce + 0 (map #(get-in % [:summary :killed] 0) successful-targets))
        overall-survived (reduce + 0 (map #(get-in % [:summary :survived] 0) successful-targets))
        overall-uncovered (reduce + 0 (map #(get-in % [:summary :uncovered-sites] 0) successful-targets))
        overall-kill-pct (if (zero? overall-executed)
                           0.0
                           (* 100.0 (/ overall-killed (double overall-executed))))
        weakest-targets (take 10 (rank-targets annotated-targets))
        regressions (filter #(contains? #{:regressed :failed}
                                        (get-in % [:comparison :trend]))
                            annotated-targets)]
    {:run {:started-at (:started-at run-info)
           :completed-at (:completed-at run-info)
           :duration-ms (:duration-ms run-info)
           :config-path (:config opts)
           :skip-coverage (:skip-coverage opts)
           :coverage (:status coverage-result)
           :coverage-command (:command coverage-result)
           :coverage-elapsed-ms (:elapsed-ms coverage-result)
           :run-dir (:run-dir run-info)
           :previous-summary-path (:summary-path previous-summary)}
     :overall {:target-count (count annotated-targets)
               :successful-target-count (count successful-targets)
               :failed-target-count (count failed-targets)
               :executed-sites overall-executed
               :killed overall-killed
               :survived overall-survived
               :uncovered-sites overall-uncovered
               :kill-pct overall-kill-pct}
     :weakest-targets weakest-targets
     :regressions (vec regressions)
     :targets annotated-targets}))

(defn- percent
  [value]
  (format "%.1f%%" (double (or value 0.0))))

(defn- seconds
  [elapsed-ms]
  (format "%.2fs" (/ (double (or elapsed-ms 0)) 1000.0)))

(defn render-markdown
  [summary]
  (let [{:keys [run overall weakest-targets regressions targets]} summary]
    (str
     "# Nightly Mutation Sweep\n\n"
     "- Started: " (:started-at run) "\n"
     "- Completed: " (:completed-at run) "\n"
     "- Duration: " (seconds (:duration-ms run)) "\n"
     "- Coverage: " (name (:coverage run)) " via `" (:coverage-command run) "`"
     (when-not (:skip-coverage run)
       (str " in " (seconds (:coverage-elapsed-ms run))))
     "\n"
     "- Run dir: `" (:run-dir run) "`\n"
     (when-let [previous-path (:previous-summary-path run)]
       (str "- Previous summary: `" previous-path "`\n"))
     "\n## Overall\n\n"
     "- Targets: " (:target-count overall) "\n"
     "- Successful targets: " (:successful-target-count overall) "\n"
     "- Failed targets: " (:failed-target-count overall) "\n"
     "- Executed mutants: " (:executed-sites overall) "\n"
     "- Killed mutants: " (:killed overall) "\n"
     "- Surviving mutants: " (:survived overall) "\n"
     "- Uncovered selected mutants: " (:uncovered-sites overall) "\n"
     "- Overall kill rate: " (percent (:kill-pct overall)) "\n"
     "\n## Weakest Targets\n\n"
     (if (seq weakest-targets)
       (apply str
              (for [target weakest-targets]
                (let [metrics (:summary target)]
                  (str "- `" (:module target) "` (`" (name (:suite target)) "`, "
                       (if (:mutate-all target) "full" "differential") ")"
                       " -> " (if (= :ok (:status target))
                                (str (percent (:kill-pct metrics))
                                     " kill, "
                                     (:survived metrics) " survivors, "
                                     (:uncovered-sites metrics) " uncovered, trend `"
                                     (name (get-in target [:comparison :trend])) "`")
                                (str "ERROR: " (:error-message target)))
                       "\n"))))
       "- No targets were executed.\n")
     "\n## Regressions\n\n"
     (if (seq regressions)
       (apply str
              (for [target regressions]
                (let [comparison (:comparison target)
                      metrics (:summary target)]
                  (str "- `" (:module target) "` trend `"
                       (name (:trend comparison))
                       "`"
                       (when (= :ok (:status target))
                         (str " -> kill "
                              (percent (:kill-pct metrics))
                              " (" (format "%+.1fpp" (double (or (:kill-pct-delta comparison) 0.0))) "), "
                              "survivors delta " (format "%+d" (or (:survivor-delta comparison) 0)) ", "
                              "uncovered delta " (format "%+d" (or (:uncovered-delta comparison) 0))))
                       (when (= :error (:status target))
                         (str " -> " (:error-message target)))
                       "\n"))))
       "- No regressions compared with the previous nightly summary.\n")
     "\n## Targets\n\n"
     (apply str
            (for [target targets]
              (if (= :ok (:status target))
                (let [metrics (:summary target)]
                  (str "- `" (:module target) "` (`" (name (:suite target)) "`) executed "
                       (:executed-sites metrics) ", killed "
                       (:killed metrics) ", survived "
                       (:survived metrics) ", uncovered "
                       (:uncovered-sites metrics) ", kill "
                       (percent (:kill-pct metrics))
                       ", trend `" (name (get-in target [:comparison :trend])) "`"
                       ", report `" (:artifact-path target) "`\n"))
                (str "- `" (:module target) "` (`" (name (:suite target)) "`) ERROR: "
                     (:error-message target) "\n")))))))

(defn write-summary!
  [summary]
  (let [run-dir (get-in summary [:run :run-dir])
        json-path (summary-json-path run-dir)
        md-path (summary-md-path run-dir)]
    (spit json-path (json/generate-string summary {:pretty true}))
    (spit md-path (render-markdown summary))
    (assoc-in (assoc-in summary [:run :summary-json-path] json-path)
              [:run :summary-md-path]
              md-path)))

(defn print-text
  [summary]
  (let [{:keys [run overall weakest-targets regressions]} summary]
    (println "=== Nightly Mutation Sweep ===")
    (println (format "Run dir: %s" (:run-dir run)))
    (println (format "Summary JSON: %s" (:summary-json-path run)))
    (println (format "Summary Markdown: %s" (:summary-md-path run)))
    (println (format "Coverage: %s" (name (:coverage run))))
    (println (format "Targets: %d (%d ok, %d failed)"
                     (:target-count overall)
                     (:successful-target-count overall)
                     (:failed-target-count overall)))
    (println (format "Overall kill rate: %s (%d/%d)"
                     (percent (:kill-pct overall))
                     (:killed overall)
                     (:executed-sites overall)))
    (println (format "Survivors: %d" (:survived overall)))
    (println (format "Uncovered selected mutants: %d" (:uncovered-sites overall)))
    (when (seq weakest-targets)
      (println)
      (println "Weakest targets:")
      (doseq [target (take 5 weakest-targets)]
        (if (= :ok (:status target))
          (println (format "  %-65s %6s kill  %2d survivors  %2d uncovered  %s"
                           (:module target)
                           (percent (get-in target [:summary :kill-pct]))
                           (get-in target [:summary :survived] 0)
                           (get-in target [:summary :uncovered-sites] 0)
                           (name (get-in target [:comparison :trend]))))
          (println (format "  %-65s ERROR  %s"
                           (:module target)
                           (:error-message target))))))
    (when (seq regressions)
      (println)
      (println "Regressions:")
      (doseq [target regressions]
        (println (format "  %-65s %s"
                         (:module target)
                         (name (get-in target [:comparison :trend]))))))))

(defn print-json
  [summary]
  (println (json/generate-string summary {:pretty true})))

(defn execute-nightly
  [opts]
  (let [start-ms (System/currentTimeMillis)
        root (fs/repo-root)
        started-at (fs/now-stamp)
        run-dir (nightly-dir root started-at)
        previous-summary (load-latest-summary root)
        targets (select-targets (load-targets root (:config opts)) opts)
        coverage-result (run-coverage! opts)
        target-results (mapv execute-target targets)
        completed-at (fs/now-stamp)
        summary (build-summary opts
                               {:started-at started-at
                                :completed-at completed-at
                                :duration-ms (- (System/currentTimeMillis) start-ms)
                                :run-dir run-dir}
                               coverage-result
                               target-results
                               previous-summary)]
    (write-summary! summary)))
