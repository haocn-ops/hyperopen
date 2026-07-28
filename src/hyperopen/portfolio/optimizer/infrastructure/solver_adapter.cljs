(ns hyperopen.portfolio.optimizer.infrastructure.solver-adapter
  (:require [hyperopen.portfolio.optimizer.infrastructure.quadprog :as quadprog]))

(defn solve-with-quadprog
  [problem]
  (quadprog/solve problem))

(defn solve-with-osqp
  [problem]
  ;; Compatibility shim for callers/tests while the retired OSQP adapter is
  ;; removed from production bundles. Preserve its historical async shape.
  (js/Promise.resolve (quadprog/solve problem)))
