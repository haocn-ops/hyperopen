(ns tools.mutate.cli-options
  (:require [clojure.string :as str]))

(def default-timeout-factor 10)

(defn usage
  []
  (str
   "Usage: bb tools/mutate.clj --module <repo-relative-path> [options]\n"
   "Options:\n"
   "  --scan                 Report mutation counts without running suites\n"
   "  --update-manifest      Rewrite the external manifest without running mutations\n"
   "  --lines L1,L2,...      Limit mutation execution to specific source lines\n"
   "  --since-last-run       Run only mutations in changed top-level forms since the last manifest\n"
   "  --mutate-all           Ignore differential narrowing and run all covered mutations\n"
   "  --suite MODE           One of auto, test, ws-test (default auto)\n"
   "  --timeout-factor N     Per-suite timeout multiplier vs baseline compile+run time (default 10)\n"
   "  --coverage-file PATH   LCOV file path (default coverage/lcov.info)\n"
   "  --format FORMAT        text or json (default text)\n"
   "  --help                 Print this help and exit\n"
   "Examples:\n"
   "  bb tools/mutate.clj --scan --module src/hyperopen/websocket/orderbook_policy.cljs\n"
   "  bb tools/mutate.clj --module src/hyperopen/api_wallets/domain/policy.cljs --suite test --lines 87"))

(defn usage-error
  [message]
  (throw (ex-info message {:usage (usage)})))

(defn parse-int
  [value]
  (try
    (Integer/parseInt (str value))
    (catch Throwable _
      nil)))

(defn parse-lines
  [value]
  (let [lines (->> (str/split (str value) #",")
                   (map str/trim)
                   (remove str/blank?)
                   (map parse-int)
                   vec)]
    (when (or (empty? lines) (some nil? lines))
      (usage-error "Invalid value for --lines. Expected comma-separated integers."))
    (set lines)))

(defn parse-timeout-factor
  [value]
  (let [parsed (parse-int value)]
    (when-not (and parsed (pos? parsed))
      (usage-error "Invalid value for --timeout-factor. Expected a positive integer."))
    parsed))

(defn normalize-suite
  [value]
  (let [suite (keyword (str/trim (str value)))]
    (when-not (contains? #{:auto :test :ws-test} suite)
      (usage-error (str "Unsupported suite mode: " value)))
    suite))

(defn validate-format
  [value]
  (when-not (contains? #{"text" "json"} value)
    (usage-error (str "Unsupported format: " value)))
  value)

(def default-options
  {:module nil
   :scan false
   :update-manifest false
   :lines nil
   :since-last-run false
   :mutate-all false
   :suite :auto
   :timeout-factor default-timeout-factor
   :coverage-file "coverage/lcov.info"
   :format "text"})

(defn parse-args
  [args]
  (if (some #{"--help"} args)
    {:help true
     :usage (usage)}
    (loop [remaining args
           opts default-options]
      (if (empty? remaining)
        (do
          (when-not (:module opts)
            (usage-error "Missing required --module argument."))
          (when (and (:scan opts) (:update-manifest opts))
            (usage-error "Cannot combine --scan with --update-manifest."))
          (when (and (:lines opts) (or (:scan opts) (:update-manifest opts)))
            (usage-error "Cannot combine --lines with --scan or --update-manifest."))
          (when (and (:since-last-run opts) (:mutate-all opts))
            (usage-error "Cannot combine --since-last-run with --mutate-all."))
          (when (and (:since-last-run opts) (:lines opts))
            (usage-error "Cannot combine --since-last-run with --lines."))
          (when (and (:mutate-all opts) (:lines opts))
            (usage-error "Cannot combine --mutate-all with --lines."))
          opts)
        (let [[key value & tail] remaining]
          (cond
            (= key "--module")
            (recur tail (assoc opts :module value))

            (= key "--scan")
            (recur (rest remaining) (assoc opts :scan true))

            (= key "--update-manifest")
            (recur (rest remaining) (assoc opts :update-manifest true))

            (= key "--lines")
            (recur tail (assoc opts :lines (parse-lines value)))

            (= key "--since-last-run")
            (recur (rest remaining) (assoc opts :since-last-run true))

            (= key "--mutate-all")
            (recur (rest remaining) (assoc opts :mutate-all true))

            (= key "--suite")
            (recur tail (assoc opts :suite (normalize-suite value)))

            (= key "--timeout-factor")
            (recur tail (assoc opts :timeout-factor (parse-timeout-factor value)))

            (= key "--coverage-file")
            (recur tail (assoc opts :coverage-file value))

            (= key "--format")
            (recur tail (assoc opts :format (validate-format value)))

            (str/starts-with? key "--")
            (usage-error (str "Unknown argument: " key))

            :else
            (usage-error (str "Unexpected positional argument: " key))))))))
