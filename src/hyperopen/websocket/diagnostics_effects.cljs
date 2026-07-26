(ns hyperopen.websocket.diagnostics-effects
  (:require [hyperopen.platform :as platform]
            [hyperopen.websocket.diagnostics-copy :as diagnostics-copy]
            [hyperopen.websocket.diagnostics-payload :as diagnostics-payload]
            [hyperopen.websocket.diagnostics-sanitize :as diagnostics-sanitize]))

(def ^:private reveal-sensitive-confirm-message
  "Reveal sensitive diagnostics values? This may expose wallet identifiers.")

(defn confirm-ws-diagnostics-reveal!
  [{:keys [store confirm-fn]}]
  (let [confirm!* (or confirm-fn platform/confirm!)
        confirmed? (confirm!* reveal-sensitive-confirm-message)]
    (when confirmed?
      (swap! store assoc-in [:websocket-ui :reveal-sensitive?] true))))

(defn copy-websocket-diagnostics!
  [{:keys [store
           app-version
           log-fn
           set-copy-status!
           diagnostics-copy-payload-fn
           sanitize-value-fn
           copy-success-status-fn
           copy-error-status-fn
           copy-websocket-diagnostics-fn]}]
  (let [payload-fn (or diagnostics-copy-payload-fn
                       (fn [state health app-version*]
                         (diagnostics-payload/diagnostics-copy-payload state
                                                                       health
                                                                       app-version*)))
        copy-fn (or copy-websocket-diagnostics-fn
                    diagnostics-copy/copy-websocket-diagnostics!)]
    (copy-fn {:store store
              :diagnostics-copy-payload (fn [state health]
                                          (payload-fn state health app-version))
              :sanitize-value (or sanitize-value-fn
                                  diagnostics-sanitize/sanitize-value)
              :set-copy-status! set-copy-status!
              :copy-success-status (or copy-success-status-fn
                                       diagnostics-payload/copy-success-status)
              :copy-error-status (or copy-error-status-fn
                                     diagnostics-payload/copy-error-status)
              :log-fn log-fn})))
