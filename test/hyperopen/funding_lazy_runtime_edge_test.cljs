(ns hyperopen.funding-lazy-runtime-edge-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [goog.object :as gobj]
            [hyperopen.route-modules :as route-modules]))

(def ^:private funding-handler-key :api-submit-funding-transfer)

(def ^:private funding-handler-path [:api funding-handler-key])

(def ^:private unavailable-handler-message
  "Route runtime effect handler unavailable: :funding-modal [:api :api-submit-funding-transfer]")

(defn- funding-wrapper
  [runtime]
  (get-in (route-modules/lazy-route-effect-leaf-deps
           runtime
           :funding-modal
           :api
           [funding-handler-key])
          funding-handler-path))

(defn- funding-module-root
  []
  (let [root (or (some-> js/goog .-global)
                 js/globalThis)]
    (reduce (fn [acc segment]
              (or (gobj/get acc segment)
                  (let [child #js {}]
                    (gobj/set acc segment child)
                    child)))
            root
            ["hyperopen" "views" "funding_modal_module"])))

(defn- restore-effect-deps-export!
  [module-root original-effect-deps]
  (if (some? original-effect-deps)
    (gobj/set module-root "effect_deps" original-effect-deps)
    (gobj/remove module-root "effect_deps"))
  (reset! hyperopen.route-modules/resolved-route-runtime-exports {}))

(deftest cached-non-function-funding-handler-rejects-with-route-runtime-error-test
  (async done
    (let [runtime {:runtime-id :cached-invalid-handler}
          load-calls (atom 0)
          invalid-handler :not-a-function
          module-root (funding-module-root)
          original-effect-deps (gobj/get module-root "effect_deps")
          effect-deps (fn [runtime*]
                        (is (identical? runtime runtime*))
                        {:api {funding-handler-key invalid-handler}})
          restore! (fn []
                     (restore-effect-deps-export! module-root original-effect-deps))]
      (reset! hyperopen.route-modules/resolved-route-runtime-exports {})
      (gobj/set module-root "effect_deps" effect-deps)
      (let [result
            (with-redefs [hyperopen.route-modules/load-shadow-module!
                          (fn [_]
                            (swap! load-calls inc)
                            (js/Promise.resolve :unexpected-load))]
              (try
                ((funding-wrapper runtime) :ctx (atom {}) {:request-id :cached})
                (catch :default err
                  err)))]
      (if (instance? js/Promise result)
        (-> result
            (.then (fn [_]
                     (restore!)
                     (is false "expected cached non-function handler to reject")
                     (done)))
            (.catch (fn [err]
                      (restore!)
                      (is (= unavailable-handler-message (.-message err)))
                      (is (= 0 @load-calls))
                      (done))))
        (do
          (restore!)
          (is false "expected cached non-function handler to return a rejected promise")
          (is (= 0 @load-calls))
          (done)))))))

(deftest post-load-non-function-funding-handler-rejects-with-route-runtime-error-test
  (async done
    (let [runtime {:runtime-id :post-load-invalid-handler}
          load-calls (atom [])
          invalid-handler :not-a-function
          module-root (funding-module-root)
          original-effect-deps (gobj/get module-root "effect_deps")
          effect-deps (fn [runtime*]
                        (is (identical? runtime runtime*))
                        {:api {funding-handler-key invalid-handler}})
          restore! (fn []
                     (restore-effect-deps-export! module-root original-effect-deps))]
      (reset! hyperopen.route-modules/resolved-route-runtime-exports {})
      (gobj/remove module-root "effect_deps")
      (with-redefs [hyperopen.route-modules/load-shadow-module!
                    (fn [module-id]
                      (swap! load-calls conj module-id)
                      (gobj/set module-root "effect_deps" effect-deps)
                      (js/Promise.resolve "funding_modal"))]
        (-> ((funding-wrapper runtime) :ctx (atom {}) {:request-id :post-load})
            (.then (fn [_]
                     (restore!)
                     (is false "expected post-load non-function handler to reject")
                     (done)))
            (.catch (fn [err]
                      (restore!)
                      (is (= unavailable-handler-message (.-message err)))
                      (is (= [:funding-modal] @load-calls))
                      (done))))))))
