(ns hyperopen.runtime.dispatch-errors
  "Surfaces the errors that nexus dispatch otherwise swallows.

  `nexus.core/dispatch` wraps every action handler and effect interpreter in a
  try/catch and folds exceptions, unknown-effect keywords, and malformed effect
  vectors into the `:errors` of its return map. Every dispatch call site in this
  app discards that return value, so a thrown handler or a typo'd `:effects/*`
  keyword becomes an invisible no-op in both dev and release.

  This namespace registers a single `:after-dispatch` interceptor that runs
  after the dispatch handler has accumulated `:errors`. It records each error to
  an always-on bounded ring buffer (so a trail exists even in release and is
  ready for the production telemetry tier), emits a telemetry event, and -- in
  dev only -- logs loudly to the console. The recording is intentionally NOT
  gated on `goog.DEBUG`: making dispatch failures observable is the whole point."
  (:require [nexus.registry :as nxr]
            [hyperopen.platform :as platform]
            [hyperopen.telemetry :as telemetry]))

(def ^:private max-dispatch-errors
  200)

(defonce ^:private dispatch-error-log
  (atom []))

(defonce ^:private installed?
  (atom false))

(defn clear-dispatch-error-log!
  []
  (reset! dispatch-error-log [])
  true)

(defn dispatch-error-log-snapshot
  []
  @dispatch-error-log)

(defn- error-message
  [err]
  (when (some? err)
    (or (some-> err .-message)
        (try
          (pr-str err)
          (catch :default _ "<unprintable>")))))

(defn summarize-error
  "Pure: distill one nexus dispatch error entry into a flat, printable summary.

  nexus error entries vary by phase: action-expansion errors carry `:action`,
  effect errors carry `:effect-k` or `:effect`, and every entry carries `:phase`
  and (usually) `:err`. We defensively read whatever identity is available."
  [error]
  (let [{:keys [phase err id action effect effect-k effects]} error
        action-id (or (when (vector? action) (first action)) id)
        effect-id (or effect-k (when (vector? effect) (first effect)))]
    (cond-> {:phase phase}
      action-id (assoc :action-id action-id)
      effect-id (assoc :effect-id effect-id)
      (seq effects) (assoc :effect-count (count effects))
      (some? err) (assoc :error (error-message err))
      (some-> err .-name) (assoc :error-name (.-name err)))))

(defn- append-bounded
  [entries entry limit]
  (let [next-entries (conj (vec entries) entry)
        overflow (- (count next-entries) limit)]
    (if (pos? overflow)
      (subvec next-entries overflow)
      next-entries)))

(defn record-dispatch-errors!
  "Append summaries for a dispatch's `errors` to the bounded ring buffer.
  Always-on (not `goog.DEBUG`-gated). Returns true when anything was recorded."
  [errors]
  (when (seq errors)
    (let [captured-at-ms (platform/now-ms)]
      (swap! dispatch-error-log
             (fn [entries]
               (reduce (fn [acc error]
                         (append-bounded acc
                                         (assoc (summarize-error error)
                                                :captured-at-ms captured-at-ms)
                                         max-dispatch-errors))
                       entries
                       errors))))
    true))

(defn surface-dispatch-errors!
  "Handle one dispatch ctx: record errors, emit telemetry, and (in dev) log
  loudly. Never throws -- a throw here would just be re-swallowed by nexus."
  [{:keys [errors]}]
  (when (seq errors)
    (record-dispatch-errors! errors)
    (doseq [error errors]
      (telemetry/emit! :runtime/dispatch-error (summarize-error error)))
    (when ^boolean goog.DEBUG
      (doseq [error errors]
        (let [summary (summarize-error error)]
          (js/console.error "[nexus dispatch error]"
                            (clj->js summary)
                            (or (:err error) (:phase summary)))))))
  nil)

(defn after-dispatch-interceptor
  "An `:after-dispatch` nexus interceptor map. Runs after the dispatch handler
  has folded `:errors` into the ctx; returns the ctx unchanged."
  []
  {:id ::surface-dispatch-errors
   :after-dispatch (fn [ctx]
                     (try
                       (surface-dispatch-errors! ctx)
                       (catch :default _ nil))
                     ctx)})

(defn install!
  "Idempotently register the dispatch-error interceptor on the nexus registry.

  `nexus.registry/register-interceptor!` appends rather than replaces, and
  `register-runtime!` re-runs on every dev hot-reload, so the `installed?`
  guard (a `defonce` that survives reload) prevents duplicate registration.
  Accepts an injected register fn for testing."
  ([] (install! nxr/register-interceptor!))
  ([register-interceptor!]
   (when-not @installed?
     (reset! installed? true)
     (register-interceptor! (after-dispatch-interceptor))
     true)))

(defn ^:no-doc reset-installed-for-test!
  []
  (reset! installed? false)
  true)
