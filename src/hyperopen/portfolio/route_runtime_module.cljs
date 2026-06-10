(ns hyperopen.portfolio.route-runtime-module
  (:require [hyperopen.portfolio.optimizer.runtime-catalog :as optimizer-runtime-catalog]))

(def ^:private eager-action-keys
  #{:load-portfolio-optimizer-route})

(defn ^:export action-deps
  []
  {:portfolio-optimizer
   (apply dissoc
          (:portfolio-optimizer
           (optimizer-runtime-catalog/action-deps))
          eager-action-keys)})

(defn ^:export effect-deps
  [runtime]
  (optimizer-runtime-catalog/effect-deps runtime))

(goog/exportSymbol "hyperopen.portfolio.route_runtime_module.action_deps" action-deps)
(goog/exportSymbol "hyperopen.portfolio.route_runtime_module.effect_deps" effect-deps)
