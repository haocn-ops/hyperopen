(ns hyperopen.account.history.effects-order-history-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [async deftest is]]
            [hyperopen.account.history.effects :as history-effects]
            [hyperopen.account.history.test-support.fixtures :as fixtures]
            [hyperopen.api.default :as api]))

(deftest api-fetch-historical-orders-effect-no-address-clears-only-current-request-test
  (let [current-store (atom (-> (fixtures/base-history-state nil)
                                (assoc-in [:account-info :selected-tab] :order-history)
                                (assoc-in [:account-info :order-history :request-id] 3)
                                (assoc-in [:orders :order-history] [{:id "old"}])))
        stale-store (atom (-> (fixtures/base-history-state nil)
                              (assoc-in [:account-info :selected-tab] :order-history)
                              (assoc-in [:account-info :order-history :request-id] 4)
                              (assoc-in [:orders :order-history] [{:id "old"}])))
        stale-before @stale-store]
    (history-effects/api-fetch-historical-orders-effect nil current-store 3)
    (is (false? (get-in @current-store [:account-info :order-history :loading?])))
    (is (nil? (get-in @current-store [:account-info :order-history :error])))
    (is (nil? (get-in @current-store [:account-info :order-history :loaded-at-ms])))
    (is (nil? (get-in @current-store [:account-info :order-history :loaded-for-address])))
    (is (= [] (get-in @current-store [:orders :order-history])))
    (history-effects/api-fetch-historical-orders-effect nil stale-store 3)
    (is (= stale-before @stale-store))))

(deftest api-fetch-historical-orders-effect-skips-when-order-history-tab-is-inactive-test
  (async done
    (let [calls (atom 0)
          store (atom (-> (fixtures/base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                          (assoc-in [:account-info :selected-tab] :balances)
                          (assoc-in [:account-info :order-history :request-id] 8)
                          (assoc-in [:account-info :order-history :loading?] true)))]
      (with-redefs [api/request-historical-orders! (fn
                                                     ([_address]
                                                      (swap! calls inc)
                                                      (js/Promise.resolve []))
                                                     ([_address _opts]
                                                      (swap! calls inc)
                                                      (js/Promise.resolve [])))]
        (-> (history-effects/api-fetch-historical-orders-effect nil store 8)
            (.then (fn [result]
                     (is (nil? result))
                     (is (= 0 @calls))
                     (is (true? (get-in @store [:account-info :order-history :loading?])))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (done))))))))

(deftest api-fetch-historical-orders-effect-success-applies-only-current-request-test
  (async done
    (let [rows (list {:oid "a"} {:oid "b"})
          current-store (atom (-> (fixtures/base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                                  (assoc-in [:account-info :selected-tab] :order-history)
                                  (assoc-in [:account-info :order-history :request-id] 20)))
          stale-store (atom (-> (fixtures/base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                                (assoc-in [:account-info :selected-tab] :order-history)
                                (assoc-in [:account-info :order-history :request-id] 21)))
          stale-before @stale-store]
      (with-redefs [api/request-historical-orders! (fn
                                                     ([_address]
                                                      (js/Promise.resolve rows))
                                                     ([_address _opts]
                                                      (js/Promise.resolve rows)))]
        (-> (js/Promise.all
             #js [(history-effects/api-fetch-historical-orders-effect nil current-store 20)
                  (history-effects/api-fetch-historical-orders-effect nil stale-store 20)])
            (.then (fn [_]
                     (is (false? (get-in @current-store [:account-info :order-history :loading?])))
                     (is (nil? (get-in @current-store [:account-info :order-history :error])))
                     (is (number? (get-in @current-store [:account-info :order-history :loaded-at-ms])))
                     (is (= "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" (get-in @current-store [:account-info :order-history :loaded-for-address])))
                     (is (vector? (get-in @current-store [:orders :order-history])))
                     (is (= rows (seq (get-in @current-store [:orders :order-history]))))
                     (is (= stale-before @stale-store))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (done))))))))

(deftest api-fetch-historical-orders-effect-error-applies-only-current-request-test
  (async done
    (let [current-store (atom (-> (fixtures/base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                                  (assoc-in [:account-info :selected-tab] :order-history)
                                  (assoc-in [:account-info :order-history :request-id] 30)))
          stale-store (atom (-> (fixtures/base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                                (assoc-in [:account-info :selected-tab] :order-history)
                                (assoc-in [:account-info :order-history :request-id] 31)))
          stale-before @stale-store]
      (with-redefs [api/request-historical-orders! (fn
                                                     ([_address]
                                                      (js/Promise.reject (js/Error. "orders-boom")))
                                                     ([_address _opts]
                                                      (js/Promise.reject (js/Error. "orders-boom"))))]
        (-> (js/Promise.all
             #js [(history-effects/api-fetch-historical-orders-effect nil current-store 30)
                  (history-effects/api-fetch-historical-orders-effect nil stale-store 30)])
            (.then (fn [_]
                     (is (false? (get-in @current-store [:account-info :order-history :loading?])))
                     (is (str/includes?
                          (get-in @current-store [:account-info :order-history :error])
                          "orders-boom"))
                     (is (= stale-before @stale-store))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (done))))))))
