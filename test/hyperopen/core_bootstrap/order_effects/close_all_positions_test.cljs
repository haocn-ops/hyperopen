(ns hyperopen.core-bootstrap.order-effects.close-all-positions-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.core-bootstrap.order-effects.test-support :as support]
            [hyperopen.order.effects :as order-effects]))

(def close-all-request
  {:snapshot [{:position-key "BTC|default" :coin "BTC" :dex nil :szi "1.25"}
              {:position-key "xyz:NVDA|xyz" :coin "xyz:NVDA" :dex "xyz" :szi "-2.5"}]
   :action {:type "order"
            :orders [{:a 1 :b false :p "99" :r true :s "1.25" :t {:limit {:tif "Ioc"}}}
                     {:a 110001 :b true :p "11" :r true :s "2.5" :t {:limit {:tif "Ioc"}}}]}})

(defn- submitting-store
  []
  (atom (support/base-position-store
         :close-all-confirmation
         {:positions-ui {:close-all-confirmation {:open? true
                                                   :lifecycle :submitting
                                                   :snapshot (:snapshot close-all-request)
                                                   :trigger-bounds nil
                                                   :error "old error"
                                                   :accepted-count 0
                                                   :rejected-count 0}}
          :order-form-runtime {:submitting? false
                               :error "unrelated form error"}})))

(deftest api-submit-close-all-positions-submits-one-batch-and-refreshes-only-after-every-leg-is-accepted-test
  (async done
    (let [store (submitting-store)
          before-order-form-runtime (:order-form-runtime @store)
          dispatched (atom [])
          refresh-calls (atom [])
          clearinghouse-calls (atom [])
          submitted-actions (atom [])
          deps (support/position-submit-deps dispatched)
          restore-refresh-mocks! (support/install-account-refresh-mocks! refresh-calls clearinghouse-calls [])
          original-submit-order trading-api/submit-order!]
      (set! trading-api/submit-order!
            (fn [_store _address action]
              (swap! submitted-actions conj action)
              (js/Promise.resolve {:status "ok"
                                   :response {:data {:statuses ["success" "success"]}}})))
      (order-effects/api-submit-close-all-positions deps nil store close-all-request)
      (js/setTimeout
       (fn []
         (try
           (is (= [(:action close-all-request)] @submitted-actions))
           (is (= :success (get-in @store [:positions-ui :close-all-confirmation :lifecycle])))
           (is (= 2 (get-in @store [:positions-ui :close-all-confirmation :accepted-count])))
           (is (= 0 (get-in @store [:positions-ui :close-all-confirmation :rejected-count])))
           (is (nil? (get-in @store [:positions-ui :close-all-confirmation :error])))
           (is (= before-order-form-runtime (:order-form-runtime @store)))
           (is (= [[[:actions/refresh-order-history]]] @dispatched))
           (is (= 1 (count @refresh-calls)))
           (is (= 1 (count @clearinghouse-calls)))
           (finally
             (restore-refresh-mocks!)
             (set! trading-api/submit-order! original-submit-order)
             (done))))
       0))))

(deftest api-submit-close-all-positions-records-partial-and-exchange-failures-with-counts-test
  (async done
    (let [partial-store (submitting-store)
          exchange-store (submitting-store)
          partial-refresh-calls (atom [])
          partial-clearinghouse-calls (atom [])
          exchange-refresh-calls (atom [])
          exchange-clearinghouse-calls (atom [])
          dispatched (atom [])
          deps (support/position-submit-deps dispatched)
          original-submit-order trading-api/submit-order!]
      (set! trading-api/submit-order!
            (fn [_store _address _action]
              (js/Promise.resolve {:status "ok"
                                   :response {:data {:statuses ["success" {:error "risk rejected"}]}}})))
      (let [restore-partial-refresh-mocks!
            (support/install-account-refresh-mocks! partial-refresh-calls partial-clearinghouse-calls [])]
        (order-effects/api-submit-close-all-positions deps nil partial-store close-all-request)
        (js/setTimeout
         (fn []
           (try
             (is (= :error (get-in @partial-store [:positions-ui :close-all-confirmation :lifecycle])))
             (is (= 1 (get-in @partial-store [:positions-ui :close-all-confirmation :accepted-count])))
             (is (= 1 (get-in @partial-store [:positions-ui :close-all-confirmation :rejected-count])))
             (is (string? (get-in @partial-store [:positions-ui :close-all-confirmation :error])))
             (is (= 1 (count @partial-refresh-calls)))
             (is (= 1 (count @partial-clearinghouse-calls)))
             (restore-partial-refresh-mocks!)
             (set! trading-api/submit-order!
                   (fn [_store _address _action]
                     (js/Promise.resolve {:status "error"
                                          :response {:data "exchange unavailable"}})))
             (let [restore-exchange-refresh-mocks!
                   (support/install-account-refresh-mocks! exchange-refresh-calls exchange-clearinghouse-calls [])]
               (order-effects/api-submit-close-all-positions deps nil exchange-store close-all-request)
               (js/setTimeout
                (fn []
                  (try
                    (is (= :error (get-in @exchange-store [:positions-ui :close-all-confirmation :lifecycle])))
                    (is (= 0 (get-in @exchange-store [:positions-ui :close-all-confirmation :accepted-count])))
                    (is (= 2 (get-in @exchange-store [:positions-ui :close-all-confirmation :rejected-count])))
                    (is (= "exchange unavailable"
                           (get-in @exchange-store [:positions-ui :close-all-confirmation :error])))
                    (is (empty? @exchange-refresh-calls))
                    (is (empty? @exchange-clearinghouse-calls))
                    (finally
                      (restore-exchange-refresh-mocks!)
                      (set! trading-api/submit-order! original-submit-order)
                      (done))))
                0))
           (catch :default err
             (restore-partial-refresh-mocks!)
             (set! trading-api/submit-order! original-submit-order)
             (is false (str "Unexpected close-all failure test error: " err))
             (done))))
         0)))))

(deftest api-submit-close-all-positions-records-transport-failure-without-mutating-order-form-runtime-test
  (async done
    (let [store (submitting-store)
          before-order-form-runtime (:order-form-runtime @store)
          deps (support/position-submit-deps (atom []))
          original-submit-order trading-api/submit-order!]
      (set! trading-api/submit-order!
            (fn [_store _address _action]
              (js/Promise.reject (js/Error. "rpc timeout"))))
      (order-effects/api-submit-close-all-positions deps nil store close-all-request)
      (js/setTimeout
       (fn []
         (try
           (is (= :error (get-in @store [:positions-ui :close-all-confirmation :lifecycle])))
           (is (= 0 (get-in @store [:positions-ui :close-all-confirmation :accepted-count])))
           (is (= 2 (get-in @store [:positions-ui :close-all-confirmation :rejected-count])))
           (is (= "rpc timeout" (get-in @store [:positions-ui :close-all-confirmation :error])))
           (is (= before-order-form-runtime (:order-form-runtime @store)))
           (finally
             (set! trading-api/submit-order! original-submit-order)
             (done))))
       0))))
