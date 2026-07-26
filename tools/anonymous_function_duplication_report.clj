(ns tools.anonymous-function-duplication-report
  (:require [tools.anonymous-function-duplication.analyzer :as analyzer]
            [tools.anonymous-function-duplication.cli-options :as cli]
            [tools.anonymous-function-duplication.filesystem :as fs]
            [tools.anonymous-function-duplication.report-output :as report-output]))

(defn -main
  [& args]
  (try
    (let [{:keys [scope top-files top-groups]} (cli/parse-args args)]
      (when-not scope
        (throw (ex-info "Missing required --scope argument" {:usage (cli/usage)})))
      (let [root (fs/canonical-path ".")
            report (analyzer/build-report {:root root
                                           :scope scope
                                           :top-files top-files
                                           :top-groups top-groups
                                           :usage-fn cli/usage})]
        (report-output/print-report report)))
    (catch Throwable t
      (binding [*out* *err*]
        (println (.getMessage t))
        (when-let [usage-text (:usage (ex-data t))]
          (println usage-text)))
      (System/exit 1))))

(apply -main *command-line-args*)
