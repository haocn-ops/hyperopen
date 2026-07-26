(ns hyperopen.api.promise-effects)

(defn reject-error
  [err]
  (js/Promise.reject err))

(defn apply-success-and-return
  [store apply-fn & leading-args]
  (fn [payload]
    (apply swap! store apply-fn (concat leading-args [payload]))
    payload))

(defn apply-error-and-reject
  [store apply-error-fn & leading-args]
  (fn [err]
    (apply swap! store apply-error-fn (concat leading-args [err]))
    (reject-error err)))

(defn log-error-and-reject
  [log-fn message]
  (fn [err]
    (when log-fn
      (log-fn message err))
    (reject-error err)))

(defn log-apply-error-and-reject
  [log-fn message store apply-error-fn & leading-args]
  (fn [err]
    (when log-fn
      (log-fn message err))
    (apply swap! store apply-error-fn (concat leading-args [err]))
    (reject-error err)))
