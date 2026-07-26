(ns hyperopen.portfolio.optimizer.infrastructure.fallback)

(defn recover-osqp-error
  [problem err solve-with-quadprog]
  (let [fallback-result (solve-with-quadprog problem)]
    (if (= :solved (:status fallback-result))
      (assoc fallback-result
             :solver :quadprog-fallback
             :fallback-from :osqp
             :fallback-reason :solver-error
             :fallback-message (str err))
      {:status :error
       :solver :osqp
       :reason :solver-error
       :message (str err)
       :fallback-result fallback-result})))
