#!/usr/bin/env bb

(ns tools.mutate-nightly
  (:require [tools.mutate.nightly :as nightly]))

(defn -main
  [& args]
  (try
    (let [opts (nightly/parse-args args)]
      (if (:help opts)
        (println (:usage opts))
        (let [summary (nightly/execute-nightly opts)]
          (if (= "json" (:format opts))
            (nightly/print-json summary)
            (nightly/print-text summary))
          (when (pos? (get-in summary [:overall :failed-target-count] 0))
            (System/exit 1)))))
    (catch Throwable t
      (binding [*out* *err*]
        (println (.getMessage t))
        (when-let [usage-text (:usage (ex-data t))]
          (println usage-text)))
      (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
