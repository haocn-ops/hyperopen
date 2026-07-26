(ns hyperopen.portfolio.optimizer.infrastructure.worker-client
  (:require [hyperopen.portfolio.optimizer.infrastructure.wire :as wire]))

(def default-worker-url
  "/js/portfolio_optimizer_worker.js")

(defn normalize-worker-message
  [data]
  (wire/normalize-worker-boundary
   (cond
     (map? data) data
     (some? data) (js->clj data :keywordize-keys true)
     :else {})))

(defn make-worker!
  ([] (make-worker! default-worker-url))
  ([url]
   (when (exists? js/Worker)
     (js/Worker. url))))

(defn add-message-listener!
  [worker handler]
  (when worker
    (.addEventListener worker "message"
                       (fn [^js event]
                         (handler (normalize-worker-message (.-data event)))))
    true))

(defn- worker-error-payload
  [kind ^js event]
  (cond-> {:code kind
           :message (or (some-> event .-message)
                        (name kind))}
    (some-> event .-filename)
    (assoc :filename (.-filename event))
    (some-> event .-lineno)
    (assoc :lineno (.-lineno event))
    (some-> event .-colno)
    (assoc :colno (.-colno event))))

(defn add-error-listener!
  [worker handler]
  (when worker
    (.addEventListener worker "error"
                       (fn [^js event]
                         (handler (worker-error-payload
                                   :optimizer-worker-error
                                   event))))
    (.addEventListener worker "messageerror"
                       (fn [^js event]
                         (handler (worker-error-payload
                                   :optimizer-worker-message-error
                                   event))))
    true))

(defn current-worker
  [worker-ref]
  (cond
    (nil? worker-ref) nil
    (satisfies? IDeref worker-ref) @worker-ref
    :else worker-ref))

(defn post-run!
  ([id request]
   (post-run! (delay (make-worker!)) id request))
  ([worker-ref id request]
   (when-let [worker (current-worker worker-ref)]
     (.postMessage worker #js {:id id
                               :type "run-optimizer"
                               :payload (wire/clj->worker-boundary request)})
     true)))
