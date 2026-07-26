(ns hyperopen.wallet.agent-runtime.errors)

(defn exchange-response-error
  [resp]
  (or (:error resp)
      (:response resp)
      (:message resp)
      (pr-str resp)))

(defn runtime-error-message
  [err]
  (or (some-> err .-message str)
      (some-> err (aget "message") str)
      (some-> err (aget "data") (aget "message") str)
      (some-> err (aget "error") (aget "message") str)
      (when (map? err)
        (or (some-> (:message err) str)
            (some-> err :data :message str)
            (some-> err :error :message str)))
      (try
        (let [clj-value (js->clj err :keywordize-keys true)]
          (when (map? clj-value)
            (or (some-> (:message clj-value) str)
                (some-> clj-value :data :message str)
                (some-> clj-value :error :message str)
                (pr-str clj-value))))
        (catch :default _
          nil))
      (str err)))

(defn runtime-error
  [runtime-error-message* err]
  (js/Error. (runtime-error-message* err)))

(defn known-error
  [message]
  (doto (js/Error. message)
    (aset "__hyperopenKnownMessage" true)))

(defn known-error?
  [err]
  (true? (some-> err (aget "__hyperopenKnownMessage"))))

(defn known-or-runtime-error-message
  [runtime-error-message* err]
  (if (known-error? err)
    (or (some-> err .-message str)
        (runtime-error-message* err))
    (runtime-error-message* err)))
