#!/usr/bin/env bb

(ns tools.mutate
  (:require [tools.mutate.cli-options :as cli]
            [tools.mutate.core :as core]
            [tools.mutate.report-output :as report-output]))

(defn -main
  [& args]
  (try
    (let [opts (cli/parse-args args)]
      (if (:help opts)
        (println (:usage opts))
        (let [report (core/execute-command opts)]
          (if (= "json" (:format opts))
            (report-output/print-json report)
            (report-output/print-text report)))))
    (catch Throwable t
      (binding [*out* *err*]
        (println (.getMessage t))
        (when-let [usage-text (:usage (ex-data t))]
          (println usage-text)))
      (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
