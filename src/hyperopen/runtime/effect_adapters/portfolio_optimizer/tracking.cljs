(ns hyperopen.runtime.effect-adapters.portfolio-optimizer.tracking
  (:require [hyperopen.portfolio.optimizer.application.current-portfolio :as current-portfolio]
            [hyperopen.portfolio.optimizer.application.tracking :as tracking]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn refresh-portfolio-optimizer-tracking-effect
  [env _ store]
  (let [now-ms-fn (:now-ms env)
        load-tracking! (:load-tracking! env)
        save-tracking! (:save-tracking! env)
        state @store
        scenario-id (or (get-in state contracts/active-scenario-loaded-id-path)
                        (get-in state contracts/draft-id-path))
        snapshot (tracking/build-tracking-snapshot
                  {:scenario-id scenario-id
                   :as-of-ms (now-ms-fn)
                   :saved-run (get-in state contracts/last-successful-run-path)
                   :current-snapshot (current-portfolio/current-portfolio-snapshot state)})]
    (if-not (= :tracked (:status snapshot))
      (do
        (swap! store assoc-in contracts/tracking-path snapshot)
        (js/Promise.resolve snapshot))
      (-> (load-tracking! scenario-id)
          (.then (fn [loaded-tracking]
                   (let [tracking-record (tracking/append-tracking-snapshot
                                          loaded-tracking
                                          snapshot)]
                     (-> (save-tracking! scenario-id tracking-record)
                         (.then (fn [_]
                                  (swap! store assoc-in
                                         contracts/tracking-path
                                         tracking-record)
                                  tracking-record))))))))))
