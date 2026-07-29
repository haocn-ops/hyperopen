(ns hyperopen.route-modules-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [goog.object :as gobj]
            [shadow.loader :as loader]
            [hyperopen.route-modules :as route-modules]))

(def ^:private funding-workflow-effect-keys
  [:api-fetch-hyperunit-fee-estimate
   :api-fetch-hyperunit-withdrawal-queue
   :api-submit-funding-transfer
   :api-submit-funding-send
   :api-submit-funding-repay
   :api-submit-funding-withdraw
   :api-submit-funding-deposit])

(defn- ensure-object-path!
  [path-segments]
  (let [root (or (some-> js/goog .-global)
                 js/globalThis)]
    (reduce (fn [acc segment]
              (or (gobj/get acc segment)
                  (let [child #js {}]
                    (gobj/set acc segment child)
                    child)))
            root
            path-segments)))

(defn- restore-export!
  [module-root export-key original-value]
  (if (some? original-value)
    (gobj/set module-root export-key original-value)
    (gobj/remove module-root export-key)))

(defn- reset-route-runtime-export-cache!
  []
  (reset! hyperopen.route-modules/resolved-route-runtime-exports {}))

(deftest route-module-id-maps-non-trade-routes-test
  (is (nil? (route-modules/route-module-id "/trade")))
  (is (nil? (route-modules/route-module-id "/trade/HYPE")))
  (is (= :portfolio (route-modules/route-module-id "/portfolio")))
  (is (= :portfolio (route-modules/route-module-id "/portfolio/optimize")))
  (is (= :portfolio (route-modules/route-module-id "/portfolio/optimize/new")))
  (is (= :portfolio (route-modules/route-module-id "/portfolio/optimize/scn_01")))
  (is (= :portfolio
         (route-modules/route-module-id "/portfolio/trader/0x1234567890abcdef1234567890abcdef12345678")))
  (is (= :leaderboard (route-modules/route-module-id "/leaderboard")))
  (is (= :funding-comparison (route-modules/route-module-id "/funding-comparison")))
  (is (= :staking (route-modules/route-module-id "/staking")))
  (is (= :referrals (route-modules/route-module-id "/referrals")))
  (is (= :referrals (route-modules/route-module-id "/join/ABC123")))
  (is (= :api-wallets (route-modules/route-module-id "/api")))
  (is (= :subaccounts (route-modules/route-module-id "/subAccounts")))
  (is (= :subaccounts (route-modules/route-module-id "/subaccounts")))
  (is (= :vaults (route-modules/route-module-id "/vaults")))
  (is (nil? (route-modules/route-module-id "/portfoliox")))
  (is (= :vaults
         (route-modules/route-module-id "/vaults/0x1234567890abcdef1234567890abcdef12345678"))))

(deftest route-module-state-helpers-track-loading-loaded-and-failure-test
  (let [state {:route-modules (route-modules/default-state)}
        loading-state (route-modules/mark-route-module-loading state "/portfolio")
        loaded-state (route-modules/mark-route-module-loaded loading-state :portfolio)
        failed-state (route-modules/mark-route-module-failed state :staking (js/Error. "boom"))]
    (is (= :portfolio (get-in loading-state [:route-modules :loading])))
    (is (true? (route-modules/route-loading? loading-state "/portfolio")))
    (is (= #{:portfolio} (get-in loaded-state [:route-modules :loaded])))
    (is (= "boom" (route-modules/route-error failed-state "/staking")))))

(deftest route-ready-requires-a-resolved-exported-view-test
  (with-redefs [route-modules/resolved-route-view (fn [_module-id] nil)
                hyperopen.route-modules/resolve-module-view
                (fn [module-id]
                  (when (= module-id :vaults)
                    {:list nil
                     :detail nil}))]
    (is (false? (route-modules/route-ready? {:route-modules {:loaded #{:vaults}}}
                                            "/vaults")))))

(deftest load-route-module-restores-vault-preview-only-for-list-route-test
  (async done
    (let [store (atom {:route-modules (route-modules/default-state)})
          restore-calls (atom [])]
      (with-redefs [hyperopen.route-modules/load-shadow-module!
                    (fn [_module-id]
                      (.resolve js/Promise :vaults))
                    route-modules/resolved-route-view (fn [_module-id] nil)
                    hyperopen.route-modules/resolve-module-view
                    (fn [module-id]
                      (when (= module-id :vaults)
                        {:list (fn [_state] [:div "list"])
                         :detail (fn [_state] [:div "detail"])}))
                    hyperopen.route-modules/maybe-restore-vaults-list-preview!
                    (fn [store-arg path]
                      (swap! restore-calls conj [store-arg path]))]
        (-> (route-modules/load-route-module! store "/vaults")
            (.then (fn [_]
                     (is (= [[store "/vaults"]] @restore-calls))
                     (reset! restore-calls [])
                     (-> (route-modules/load-route-module! store "/vaults/0x1234567890abcdef1234567890abcdef12345678")
                         (.then (fn [_]
                                  (is (= [] @restore-calls))
                                  (done)))
                         (.catch (fn [err]
                                   (is false (str "unexpected detail-route module load failure: " err))
                                   (done))))))
            (.catch (fn [err]
                      (is false (str "unexpected list-route module load failure: " err))
                      (done))))))))

(deftest route-ready-requires-a-resolved-route-runtime-test
  (with-redefs [route-modules/resolved-route-view
                (fn [_module-id]
                  (fn [_state] [:div "portfolio"]))
                hyperopen.route-modules/cached-or-exported-route-runtime-exports
                (fn [_module-id] nil)]
    (is (false? (route-modules/route-ready? {:route-modules {:loaded #{:portfolio}}}
                                            "/portfolio")))))

(deftest lazy-route-action-leaf-deps-invoke-loaded-route-runtime-handlers-test
  (let [action-handler (fn [state payload]
                         [:handled state payload])]
    (with-redefs [route-modules/resolved-route-runtime-action-deps
                  (fn [module-id]
                    (is (= :portfolio module-id))
                    {:portfolio-optimizer
                     {:run-portfolio-optimizer action-handler}})]
      (let [wrapped-handler
            (get-in (route-modules/lazy-route-action-leaf-deps
                     :portfolio
                     :portfolio-optimizer
                     [:run-portfolio-optimizer])
                    [:portfolio-optimizer :run-portfolio-optimizer])]
        (is (= [:handled :state :payload]
               (wrapped-handler :state :payload)))))))

(deftest lazy-route-effect-leaf-deps-load-route-module-before-invoking-handler-test
  (let [wrapped-handler
        (get-in (route-modules/lazy-route-effect-leaf-deps
                 :runtime
                 :vaults
                 :api
                 [:api-fetch-vault-index])
                [:api :api-fetch-vault-index])]
    (is (fn? wrapped-handler))))

(deftest funding-modal-effect-only-runtime-export-loads-and-forwards-workflow-calls-test
  (async done
    (let [runtime {:runtime-id :funding-modal-lazy-effect}
          module-root (ensure-object-path! ["hyperopen" "views" "funding_modal_module"])
          original-effect-deps (gobj/get module-root "effect_deps")
          load-calls (atom [])
          delegate-calls (atom [])
          handlers (into {}
                         (map (fn [handler-key]
                                [handler-key
                                 (fn [ctx store request attempt]
                                   (swap! delegate-calls conj
                                          [handler-key ctx store [request attempt]])
                                   {:handler handler-key
                                    :request-id (:request-id request)
                                    :attempt attempt})]))
                         funding-workflow-effect-keys)
          effect-deps (fn [runtime*]
                        (is (identical? runtime runtime*))
                        {:api handlers})
          restore! (fn []
                     (restore-export! module-root "effect_deps" original-effect-deps)
                     (reset-route-runtime-export-cache!))
          invoke-unloaded! (fn [handler-key attempt]
                             (reset-route-runtime-export-cache!)
                             (gobj/remove module-root "effect_deps")
                             (let [ctx {:caller handler-key}
                                   store (atom {:attempt attempt})
                                   request {:request-id (str (name handler-key) "-" attempt)}
                                   wrapped-handler
                                   (get-in (route-modules/lazy-route-effect-leaf-deps
                                            runtime
                                            :funding-modal
                                            :api
                                            [handler-key])
                                           [:api handler-key])]
                               (-> (wrapped-handler ctx store request attempt)
                                   (.then
                                    (fn [result]
                                      (is (= {:handler handler-key
                                              :request-id (:request-id request)
                                              :attempt attempt}
                                             result))
                                      (let [[called-key called-ctx called-store called-args]
                                            (some (fn [call]
                                                    (when (= handler-key (first call))
                                                      call))
                                                  @delegate-calls)]
                                        (is (= handler-key called-key))
                                        (is (identical? ctx called-ctx))
                                        (is (identical? store called-store))
                                        (is (= [request attempt] called-args))))))))]
      (reset-route-runtime-export-cache!)
      (gobj/remove module-root "effect_deps")
      (with-redefs [hyperopen.route-modules/load-shadow-module!
                    (fn [module-id]
                      (is (= :funding-modal module-id))
                      (swap! load-calls conj module-id)
                      (gobj/set module-root "effect_deps" effect-deps)
                      (js/Promise.resolve "funding_modal"))]
        (-> (js/Promise.all
             (clj->js
              (mapv (fn [[attempt handler-key]]
                      (invoke-unloaded! handler-key attempt))
                    (map-indexed vector funding-workflow-effect-keys))))
            (.then
             (fn [_]
               (let [handler-key :api-submit-funding-deposit
                     ctx {:caller :cached}
                     store (atom {:attempt :cached})
                     request {:request-id "cached"}
                     wrapped-handler
                     (get-in (route-modules/lazy-route-effect-leaf-deps
                              runtime
                              :funding-modal
                              :api
                              [handler-key])
                             [:api handler-key])]
                 (is (= {:handler handler-key
                         :request-id "cached"
                         :attempt :cached}
                        (wrapped-handler ctx store request :cached)))
                 (is (= funding-workflow-effect-keys
                        (mapv first
                              (take (count funding-workflow-effect-keys)
                                    @delegate-calls))))
                 (is (= (inc (count funding-workflow-effect-keys))
                        (count @delegate-calls)))
                 (is (= (count funding-workflow-effect-keys)
                        (count @load-calls)))
                 (restore!)
                 (done))))
            (.catch
             (fn [err]
               (restore!)
               (is false (str "unexpected Funding modal lazy effect failure: " err))
               (done))))))))

(deftest funding-modal-effect-only-runtime-export-propagates-delegate-rejection-test
  (async done
    (let [runtime {:runtime-id :funding-modal-rejection}
          module-root (ensure-object-path! ["hyperopen" "views" "funding_modal_module"])
          original-effect-deps (gobj/get module-root "effect_deps")
          delegate-failure (js/Error. "funding submit failed")
          delegate-calls (atom 0)
          effect-deps (fn [_]
                        {:api
                         {:api-submit-funding-transfer
                          (fn [_ctx _store _request]
                            (swap! delegate-calls inc)
                            (js/Promise.reject delegate-failure))}})
          restore! (fn []
                     (restore-export! module-root "effect_deps" original-effect-deps)
                     (reset-route-runtime-export-cache!))]
      (reset-route-runtime-export-cache!)
      (gobj/remove module-root "effect_deps")
      (with-redefs [hyperopen.route-modules/load-shadow-module!
                    (fn [module-id]
                      (is (= :funding-modal module-id))
                      (gobj/set module-root "effect_deps" effect-deps)
                      (js/Promise.resolve "funding_modal"))]
        (let [wrapped-handler
              (get-in (route-modules/lazy-route-effect-leaf-deps
                       runtime
                       :funding-modal
                       :api
                       [:api-submit-funding-transfer])
                      [:api :api-submit-funding-transfer])]
          (-> (wrapped-handler :ctx (atom {}) {:action {:type "usdClassTransfer"}})
              (.then (fn [_]
                       (restore!)
                       (is false "expected rejected Funding delegate promise")
                       (done)))
              (.catch (fn [err]
                        (restore!)
                        (is (identical? delegate-failure err))
                        (is (= 1 @delegate-calls))
                        (done)))))))))

(deftest dispatch-route-actions-after-load-loads-module-before-dispatch-test
  (async done
    (let [store (atom {:route-modules (route-modules/default-state)})
          calls (atom [])]
      (with-redefs [hyperopen.route-modules/route-module-id
                    (fn [path]
                      (is (= "/portfolio" path))
                      :portfolio)
                    hyperopen.route-modules/route-module-ready?
                    (fn [module-id]
                      (is (= :portfolio module-id))
                      false)
                    hyperopen.route-modules/load-route-module!
                    (fn [store-arg path]
                      (swap! calls conj [:load store-arg path])
                      (.resolve js/Promise :loaded))]
        (-> (route-modules/dispatch-route-actions-after-load!
             store
             (fn [store-arg ctx actions]
               (swap! calls conj [:dispatch store-arg ctx actions]))
             "/portfolio"
             [[:actions/run-portfolio-optimizer]])
            (.then (fn [_]
                     (is (= [[:load store "/portfolio"]
                             [:dispatch store nil [[:actions/run-portfolio-optimizer]]]]
                            @calls))
                     (done)))
            (.catch (fn [err]
                      (is false (str "unexpected dispatch-after-load failure: " err))
                      (done))))))))
