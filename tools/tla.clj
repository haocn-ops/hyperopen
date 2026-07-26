#!/usr/bin/env bb

(ns tools.tla
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io ByteArrayOutputStream File]))

(def ^:private supported-specs
  {"websocket-runtime" {:module-name "websocket_runtime"
                        :spec-file "spec/tla/websocket_runtime.tla"
                        :config-file "spec/tla/websocket_runtime.cfg"}
   "websocket-runtime-liveness" {:module-name "websocket_runtime"
                                 :spec-file "spec/tla/websocket_runtime.tla"
                                 :config-file "spec/tla/websocket_runtime_liveness.cfg"}})

(defn- usage
  []
  (str "Usage: bb tools/tla.clj verify --spec websocket-runtime\n"
       "Examples:\n"
       "  bb tools/tla.clj verify --spec websocket-runtime\n"
       "  bb tools/tla.clj verify --spec websocket-runtime-liveness\n"
       "  npm run tla:verify -- --spec websocket-runtime\n"
       "Notes:\n"
       "  - TLC verification is optional; normal repo gates run `npm run test:tla-tooling`,\n"
       "    not `npm run tla:verify`.\n"
       "  - Set `TLA2TOOLS_JAR` to a local `tla2tools.jar`, or place the jar at\n"
       "    `tools/tla/vendor/tla2tools.jar`.\n"
       "  - TLC metadata and logs are written only under `target/tla/<spec-id>/`.\n"))

(defn- fail!
  [message]
  (throw (ex-info message {:usage (usage)})))

(defn- env
  [name]
  (System/getenv name))

(defn- repo-root
  []
  (let [script-file (some-> (System/getProperty "babashka.file") io/file .getCanonicalFile)
        tools-dir (or (some-> script-file .getParentFile)
                      (io/file "."))]
    (some-> tools-dir .getParentFile .getCanonicalFile)))

(defn- shell-argv
  [command args]
  (into [command] args))

(defn- start-output-drainer!
  [process output-buffer]
  (doto (Thread.
         (fn []
           (try
             (with-open [stream (.getInputStream process)]
               (io/copy stream output-buffer))
             (catch Exception _)))
         "tla-output-drainer")
    (.setDaemon true)
    (.start)))

(defn- run-command
  [command args {:keys [dir]}]
  (let [builder (doto (ProcessBuilder. ^java.util.List (vec (shell-argv command args)))
                  (.redirectErrorStream true))
        _ (when dir
            (.directory builder ^File dir))
        process (.start builder)
        output-buffer (ByteArrayOutputStream.)
        drainer (start-output-drainer! process output-buffer)
        exit-code (.waitFor process)]
    (.join drainer 1000)
    {:command (cons command args)
     :dir dir
     :exit exit-code
     :output (.toString output-buffer "UTF-8")}))

(defn- normalize-spec
  [value]
  (when-let [spec-id (some-> value str/trim not-empty)]
    (when-let [spec (get supported-specs spec-id)]
      (assoc spec :id spec-id))))

(defn- parse-args
  [args]
  (when (empty? args)
    (fail! (usage)))
  (let [[command & tail] args]
    (cond
      (contains? #{"--help" "-h" "help"} command)
      {:help true}

      (not= "verify" command)
      (fail! (str "Unsupported command: " command "\n" (usage)))

      :else
      (loop [remaining tail
             opts {:command command
                   :spec nil}]
        (if (empty? remaining)
          (if (:spec opts)
            opts
            (fail! (str "Missing --spec value.\n" (usage))))
          (let [[key value & more] remaining]
            (cond
              (= key "--help")
              {:help true}

              (= key "--spec")
              (if value
                (if-let [spec (normalize-spec value)]
                  (recur more (assoc opts :spec spec))
                  (fail! (str "Unsupported spec: " value "\n" (usage))))
                (fail! (str "Missing value for --spec.\n" (usage))))

              :else
              (fail! (str "Unknown argument: " key "\n" (usage))))))))))

(defn- output-root
  [{:keys [id]}]
  (io/file (repo-root) "target" "tla" id))

(defn- metadata-root
  [spec]
  (io/file (output-root spec) "metadata"))

(defn- log-path
  [spec]
  (io/file (output-root spec) "tlc.log"))

(defn- vendor-jar-path
  []
  (io/file (repo-root) "tools" "tla" "vendor" "tla2tools.jar"))

(defn- spec-path
  [{:keys [spec-file]}]
  (io/file (repo-root) spec-file))

(defn- config-path
  [{:keys [config-file]}]
  (io/file (repo-root) config-file))

(defn- existing-file?
  [file]
  (and file (.exists file) (.isFile file)))

(defn- missing-jar-message
  [{:keys [env-path vendor-path]}]
  (str "TLA+ tools jar is required to run TLC, but no usable `tla2tools.jar` was found.\n"
       "Checked:\n"
       "  TLA2TOOLS_JAR"
       (if env-path
         (str " -> " env-path)
         " -> <unset>")
       "\n"
       "  repo-local fallback -> " vendor-path "\n"
       "Normal repo gates do not require this jar; only explicit `npm run tla:verify` runs do.\n"
       "Repair:\n"
       "  - export TLA2TOOLS_JAR=/absolute/path/to/tla2tools.jar\n"
       "  - or place the jar at tools/tla/vendor/tla2tools.jar\n"
       "The official TLA+ releases publish `tla2tools.jar`; once it is present, rerun the command."))

(defn- ensure-tla-tools-jar!
  []
  (let [env-path (some-> (env "TLA2TOOLS_JAR") str/trim not-empty)
        env-file (when env-path (io/file env-path))
        vendor-file (vendor-jar-path)]
    (cond
      (existing-file? env-file)
      (.getCanonicalPath env-file)

      (existing-file? vendor-file)
      (.getCanonicalPath vendor-file)

      :else
      (fail! (missing-jar-message {:env-path env-path
                                   :vendor-path (.getPath vendor-file)})))))

(defn- ensure-spec-inputs!
  [spec]
  (let [module-file (spec-path spec)
        cfg-file (config-path spec)]
    (when-not (existing-file? module-file)
      (fail! (str "Missing TLA+ spec module: " (.getPath module-file))))
    (when-not (existing-file? cfg-file)
      (fail! (str "Missing TLA+ config: " (.getPath cfg-file))))
    {:module-file (.getCanonicalFile module-file)
     :cfg-file (.getCanonicalFile cfg-file)}))

(defn- tlc-args
  [jar-path spec {:keys [module-file cfg-file]}]
  ["-XX:+UseParallelGC"
   "-cp" jar-path
   "tlc2.TLC"
   "-cleanup"
   "-workers" "auto"
   "-config" (.getAbsolutePath ^File cfg-file)
   "-metadir" (.getAbsolutePath ^File (metadata-root spec))
   (.getAbsolutePath ^File module-file)])

(defn- write-log!
  [spec output]
  (let [file (log-path spec)]
    (.mkdirs (.getParentFile file))
    (spit file output)
    file))

(defn- verify-spec!
  [spec]
  (let [jar-path (ensure-tla-tools-jar!)
        spec-inputs (ensure-spec-inputs! spec)
        run-root (output-root spec)
        _ (.mkdirs run-root)
        result (run-command "java" (tlc-args jar-path spec spec-inputs) {:dir run-root})
        log-file (write-log! spec (:output result))]
    (when-not (zero? (:exit result))
      (fail! (str "TLC verification failed for " (:id spec) ".\n"
                  "Log: " (.getPath log-file) "\n"
                  (:output result))))
    {:jar-path jar-path
     :log-file log-file
     :run-root run-root}))

(defn run!
  [args]
  (let [opts (parse-args args)]
    (if (:help opts)
      (println (usage))
      (let [{:keys [command spec]} opts]
        (case command
          "verify"
          (let [{:keys [log-file run-root]} (verify-spec! spec)]
            (println (str "Verified " (:id spec)
                          " with TLC. Logs and metadata are under "
                          (.getPath run-root)
                          " (log: " (.getPath log-file) ")."))))))))

(defn -main
  [& args]
  (try
    (run! args)
    (catch Throwable t
      (binding [*out* *err*]
        (println (.getMessage t))
        (when-let [usage-text (:usage (ex-data t))]
          (println usage-text)))
      (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
