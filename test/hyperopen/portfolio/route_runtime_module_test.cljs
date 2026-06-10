(ns hyperopen.portfolio.route-runtime-module-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.runtime-catalog :as optimizer-runtime-catalog]
            [hyperopen.portfolio.route-runtime-module]))

(deftest portfolio-route-runtime-module-exports-optimizer-runtime-catalog-test
  (let [module (aget js/globalThis "hyperopen" "portfolio" "route_runtime_module")
        action-deps-fn (aget module "action_deps")
        effect-deps-fn (aget module "effect_deps")
        base-action-deps (:portfolio-optimizer
                          (optimizer-runtime-catalog/action-deps))
        exported-action-deps (:portfolio-optimizer
                              (action-deps-fn))
        exported-effect-deps (:portfolio-optimizer
                              (effect-deps-fn nil))
        base-effect-deps (:portfolio-optimizer
                          (optimizer-runtime-catalog/effect-deps nil))]
    (is (fn? action-deps-fn))
    (is (fn? effect-deps-fn))
    (is (not (contains? exported-action-deps :load-portfolio-optimizer-route)))
    (is (= (disj (set (keys base-action-deps))
                 :load-portfolio-optimizer-route)
           (set (keys exported-action-deps))))
    (is (identical? (:run-portfolio-optimizer base-action-deps)
                    (:run-portfolio-optimizer exported-action-deps)))
    (is (= (set (keys base-effect-deps))
           (set (keys exported-effect-deps))))
    (is (identical? (:load-portfolio-optimizer-history base-effect-deps)
                    (:load-portfolio-optimizer-history exported-effect-deps)))))
