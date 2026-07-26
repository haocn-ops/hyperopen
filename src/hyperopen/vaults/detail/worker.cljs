(ns hyperopen.vaults.detail.worker
  (:require [hyperopen.portfolio.worker :as portfolio-worker]))

(defn ^:export init []
  (portfolio-worker/init))
