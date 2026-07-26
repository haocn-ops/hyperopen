(ns hyperopen.portfolio.optimizer.infrastructure.solver-adapter
  (:require [hyperopen.portfolio.optimizer.infrastructure.osqp :as osqp]
            [hyperopen.portfolio.optimizer.infrastructure.quadprog :as quadprog]))

(defn solve-with-quadprog
  [problem]
  (quadprog/solve problem))

(defn solve-with-osqp
  [problem]
  (osqp/solve problem))
