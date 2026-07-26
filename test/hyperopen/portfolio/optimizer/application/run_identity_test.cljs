(ns hyperopen.portfolio.optimizer.application.run-identity-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.run-identity :as run-identity]))

(defn- request
  [overrides]
  (merge
   {:requested-universe [{:instrument-id "perp:BTC"}]
    :universe [{:instrument-id "perp:BTC"}]
    :current-portfolio {:capital {:nav-usdc 1000}}
    :return-model {:kind :historical-mean}
    :risk-model {:kind :diagonal-shrink}
    :objective {:kind :minimum-variance}
    :constraints {:max-asset-weight 0.5}
    :execution-assumptions {:default-order-type :market
                            :fallback-slippage-bps 25
                            :fee-mode :taker
                            :cost-contexts-by-id
                            {"perp:BTC" {:source :live-orderbook
                                         :best-bid {:px "100" :sz "1"}
                                         :best-ask {:px "101" :sz "2"}
                                         :stale? false}}}
    :history {:return-series-by-instrument {"perp:BTC" [0.01 0.02]}
              :freshness {:as-of-ms 1000
                          :age-ms 10
                          :stale? false}}}
   overrides))

(deftest matching-request-ignores-volatile-render-time-fields-test
  (let [run-request (request {})
        render-request (request
                        {:execution-assumptions
                         {:default-order-type :market
                          :fallback-slippage-bps 25
                          :fee-mode :taker
                          :cost-contexts-by-id
                          {"perp:BTC" {:source :live-orderbook
                                       :best-bid {:px "100" :sz "9"}
                                       :best-ask {:px "101" :sz "7"}
                                       :stale? false}}}
                         :history {:return-series-by-instrument {"perp:BTC" [0.01 0.02]}
                                   :freshness {:as-of-ms 1200
                                               :age-ms 210
                                               :stale? false}}})
        last-run {:request-signature (run-identity/build-request-signature run-request)
                  :result {:status :solved}}]
    (is (run-identity/matching-request? render-request last-run)
        "A completed run should remain current when only live orderbook depth and render-time freshness age drift.")))

(deftest matching-request-rejects-model-input-changes-test
  (let [run-request (request {})
        changed-request (assoc run-request :return-model {:kind :black-litterman
                                                          :views [{:instrument-id "perp:BTC"
                                                                   :return 0.2}]})
        last-run {:request-signature (run-identity/build-request-signature run-request)
                  :result {:status :solved}}]
    (is (false? (run-identity/matching-request? changed-request last-run)))))

(deftest completed-run-signature-keeps-result-current-after-input-drift-test
  (let [run-request (request {})
        request-signature (run-identity/build-request-signature run-request)
        render-request (assoc-in run-request
                                 [:current-portfolio :capital :nav-usdc]
                                 2000)
        last-run {:request-signature request-signature
                  :result {:status :solved}}
        ctx {:draft {:metadata {:dirty? false}}
             :readiness {:request render-request}
             :run-state {:status :succeeded
                         :request-signature request-signature}
             :running? false
             :last-successful-run last-run}]
    (is (run-identity/current-solved-run? ctx)
        "The completed worker run should remain current when its own request signature matches the retained solved result.")
    (is (false? (run-identity/stale-run? ctx))
        "The detail route should not block a just-completed solved run because live inputs refreshed after completion.")))

(deftest completed-run-stays-current-after-rebalance-snapshot-enriches-last-run-test
  (let [run-request (request {})
        request-signature (run-identity/build-request-signature run-request)
        enriched-last-run {:request-signature
                           (assoc-in request-signature
                                     [:request
                                      :execution-assumptions
                                      :cost-contexts-by-id
                                      "perp:BTC"]
                                     {:source :snapshot
                                      :best-bid {:px "100" :sz "1"}
                                      :best-ask {:px "101" :sz "2"}
                                      :stale? false})
                           :result {:status :solved}}
        render-request (assoc-in run-request
                                 [:current-portfolio :capital :nav-usdc]
                                 2000)
        ctx {:draft {:metadata {:dirty? false}}
             :readiness {:request render-request}
             :run-state {:status :succeeded
                         :request-signature request-signature}
             :running? false
             :last-successful-run enriched-last-run}]
    (is (run-identity/current-solved-run? ctx)
        "Snapshot-enriched rebalance previews must not make a just-completed run stale when live portfolio marks drift.")
    (is (false? (run-identity/stale-run? ctx)))))
