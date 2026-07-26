(ns hyperopen.core-bootstrap.order-effects.subaccount-routing-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [nexus.registry :as nxr]
            [hyperopen.api.default :as api]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.core.compat :as core]
            [hyperopen.core-bootstrap.order-effects.test-support :as support]))

(def owner-address
  "0x1111111111111111111111111111111111111111")

(def selected-subaccount-address
  "0x2222222222222222222222222222222222222222")

(def order-action
  {:type "order"
   :orders []
   :grouping "na"})

(defn- submit-order-store
  [selected?]
  (support/base-submit-order-store
   {:wallet {:address owner-address
             :agent {:status :ready}}
    :account-context {:spectate-mode {:active? false
                                      :address nil}
                      :subaccounts {:rows (if selected?
                                            [{:name "Desk A"
                                              :sub-account-user selected-subaccount-address
                                              :master owner-address}]
                                            [])
                                    :selected-address (when selected?
                                                        selected-subaccount-address)}}
    :websocket {:migration-flags {:order-fill-ws-first? false}}}))

(defn- install-submit-routing-mocks!
  [{:keys [submit-calls dispatched refresh-calls clearinghouse-calls spot-clearinghouse-calls]}]
  (let [original-submit-order trading-api/submit-order!
        original-dispatch nxr/dispatch
        original-request-open-orders api/request-frontend-open-orders!
        original-request-clearinghouse-state api/request-clearinghouse-state!
        original-request-spot-clearinghouse-state api/request-spot-clearinghouse-state!
        original-ensure-perp-dexs-data api/ensure-perp-dexs-data!]
    (set! trading-api/submit-order!
          (fn submit-order-mock
            ([store address action]
             (submit-order-mock store address action nil))
            ([_store address action opts]
             (swap! submit-calls conj [address action opts])
             (js/Promise.resolve {:status "ok"}))))
    (set! nxr/dispatch
          (fn [_store _evt actions]
            (swap! dispatched conj actions)))
    (set! api/request-frontend-open-orders!
          (fn request-frontend-open-orders-mock
            ([address]
             (request-frontend-open-orders-mock address {}))
            ([address opts]
             (request-frontend-open-orders-mock address (:dex opts) (dissoc opts :dex)))
            ([address dex opts]
             (swap! refresh-calls conj [address dex opts])
             (js/Promise.resolve []))))
    (set! api/request-clearinghouse-state!
          (fn request-clearinghouse-state-mock
            ([address dex]
             (request-clearinghouse-state-mock address dex {}))
            ([address dex opts]
             (swap! clearinghouse-calls conj [address dex opts])
             (js/Promise.resolve {:assetPositions []}))))
    (set! api/request-spot-clearinghouse-state!
          (fn request-spot-clearinghouse-state-mock
            ([address]
             (request-spot-clearinghouse-state-mock address {}))
            ([address opts]
             (swap! spot-clearinghouse-calls conj [address opts])
             (js/Promise.resolve {:balances []}))))
    (set! api/ensure-perp-dexs-data!
          (fn ensure-perp-dexs-data-mock
            ([_store]
             (ensure-perp-dexs-data-mock nil {}))
            ([_store _opts]
             (js/Promise.resolve ["dex-a"]))))
    (fn restore-submit-routing-mocks! []
      (set! trading-api/submit-order! original-submit-order)
      (set! nxr/dispatch original-dispatch)
      (set! api/request-frontend-open-orders! original-request-open-orders)
      (set! api/request-clearinghouse-state! original-request-clearinghouse-state)
      (set! api/request-spot-clearinghouse-state! original-request-spot-clearinghouse-state)
      (set! api/ensure-perp-dexs-data! original-ensure-perp-dexs-data))))

(deftest selected-subaccount-submit-effect-routes-vault-and-refreshes-selected-account-test
  (async done
    (let [store (atom (submit-order-store true))
          submit-calls (atom [])
          dispatched (atom [])
          refresh-calls (atom [])
          clearinghouse-calls (atom [])
          spot-clearinghouse-calls (atom [])
          restore! (install-submit-routing-mocks!
                    {:submit-calls submit-calls
                     :dispatched dispatched
                     :refresh-calls refresh-calls
                     :clearinghouse-calls clearinghouse-calls
                     :spot-clearinghouse-calls spot-clearinghouse-calls})]
      (support/clear-order-feedback-toast-timeout!)
      (core/api-submit-order nil store {:action order-action})
      (js/setTimeout
       (fn []
         (try
           (is (= [[owner-address order-action {:vault-address selected-subaccount-address}]]
                  @submit-calls))
           (is (= [[selected-subaccount-address nil {:force-refresh? true
                                                     :priority :high}]
                   [selected-subaccount-address "dex-a" {:force-refresh? true
                                                         :priority :low}]]
                  @refresh-calls))
           (is (= [[selected-subaccount-address nil {:priority :high}]
                   [selected-subaccount-address "dex-a" {:priority :low}]]
                  @clearinghouse-calls))
           (is (= [] @spot-clearinghouse-calls))
           (is (= [[[:actions/refresh-order-history]]]
                  @dispatched))
           (finally
             (support/clear-order-feedback-toast-timeout!)
             (restore!)
             (done))))
       25))))

(deftest master-submit-effect-routes-without-vault-and-refreshes-owner-account-test
  (async done
    (let [store (atom (submit-order-store false))
          submit-calls (atom [])
          dispatched (atom [])
          refresh-calls (atom [])
          clearinghouse-calls (atom [])
          spot-clearinghouse-calls (atom [])
          restore! (install-submit-routing-mocks!
                    {:submit-calls submit-calls
                     :dispatched dispatched
                     :refresh-calls refresh-calls
                     :clearinghouse-calls clearinghouse-calls
                     :spot-clearinghouse-calls spot-clearinghouse-calls})]
      (support/clear-order-feedback-toast-timeout!)
      (core/api-submit-order nil store {:action order-action})
      (js/setTimeout
       (fn []
         (try
           (let [[submit-address submit-action submit-opts] (first @submit-calls)]
             (is (= owner-address submit-address))
             (is (= order-action submit-action))
             (is (or (nil? submit-opts)
                     (empty? submit-opts)
                     (nil? (:vault-address submit-opts)))))
           (is (= [[owner-address nil {:force-refresh? true
                                       :priority :high}]
                   [owner-address "dex-a" {:force-refresh? true
                                           :priority :low}]]
                  @refresh-calls))
           (is (= [[owner-address nil {:priority :high}]
                   [owner-address "dex-a" {:priority :low}]]
                  @clearinghouse-calls))
           (is (= [] @spot-clearinghouse-calls))
           (is (= [[[:actions/refresh-order-history]]]
                  @dispatched))
           (finally
             (support/clear-order-feedback-toast-timeout!)
             (restore!)
             (done))))
       25))))
