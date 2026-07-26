(ns hyperopen.portfolio.optimizer.actions.tracking
  (:require [hyperopen.portfolio.optimizer.contracts :as contracts]))

(def manual-tracking-source-statuses
  #{:saved :computed})

(defn refresh-portfolio-optimizer-tracking
  [state]
  (if (contains? #{:executed :partially-executed :tracking}
                 (get-in state contracts/active-scenario-status-path))
    [[:effects/refresh-portfolio-optimizer-tracking]]
    []))

(defn enable-portfolio-optimizer-manual-tracking
  [state]
  (if (and (contains? manual-tracking-source-statuses
                      (get-in state contracts/active-scenario-status-path))
           (or (get-in state contracts/active-scenario-loaded-id-path)
               (get-in state contracts/draft-id-path)))
    [[:effects/enable-portfolio-optimizer-manual-tracking]]
    []))
