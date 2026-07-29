(ns hyperopen.builder-fee.approval-refresh-acceptance-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.gateway.account :as account-gateway]
            [hyperopen.builder-fee.effects :as effects]
            [hyperopen.builder-fee.policy :as policy]))

(def owner "0x1111111111111111111111111111111111111111")
(def builder "0x36a47878219fb346e031f6cf82cbfc8c77e35932")
(def configured-fee {:status :configured
                     :builder-address builder
                     :fee-tenths-bp 10
                     :disclosure "A disclosed 0.01% builder fee."})
(def ordinary-order (array-map :type "order" :orders [] :grouping "na"))

(defn- deferred-promise
  []
  (let [resolve* (atom nil)
        reject* (atom nil)
        promise (js/Promise. (fn [resolve reject]
                               (reset! resolve* resolve)
                               (reset! reject* reject)))]
    {:promise promise
     :resolve! (fn [value] (@resolve* value))
     :reject! (fn [error] (@reject* error))}))

(defn- with-fake-date!
  [timestamps f]
  (let [original-date (.-Date js/globalThis)
        remaining (atom (seq timestamps))
        fake-date (fn [] (js/Date.))]
    (aset fake-date "now"
          (fn []
            (let [next-value (or (first @remaining) 0)]
              (swap! remaining next)
              next-value)))
    (set! (.-Date js/globalThis) fake-date)
    (try
      (f)
      (finally
        (set! (.-Date js/globalThis) original-date)))))

(deftest successful-builder-fee-review-refreshes-the-authoritative-current-owner-approval-test
  (async done
    (let [requests (atom [])
          deps {:post-info! (fn [body opts]
                              (swap! requests conj [body opts])
                              (js/Promise.resolve 10))}]
      (-> (account-gateway/request-max-builder-fee! deps owner builder {:priority :high})
          (.then (fn [max-builder-fee]
                   (is (= 10 max-builder-fee))
                   (is (= [[{"type" "maxBuilderFee"
                             "user" owner
                             "builder" builder}
                            {:priority :high}]]
                          @requests))
                   (done)))
          (.catch (fn [error]
                    (is false (str "Unexpected builder-fee approval refresh error: " error))
                    (done)))))))

(deftest a-refreshed-max-builder-fee-below-the-configured-threshold-remains-inactive-test
  (async done
    (let [deps {:post-info! (fn [_body _opts] (js/Promise.resolve 9))}]
      (-> (account-gateway/request-max-builder-fee! deps owner builder {:priority :high})
          (.then (fn [max-builder-fee]
                   (let [decision (policy/policy-decision
                                   configured-fee
                                   {:status :ready
                                    :owner-address owner
                                    :builder-address builder
                                    :network :testnet
                                    :max-builder-fee max-builder-fee}
                                   owner owner :perp ordinary-order :sell)]
                     (is (= 9 max-builder-fee))
                     (is (false? (:active? decision)))
                     (is (identical? ordinary-order (:action decision)))
                     (done))))
          (.catch (fn [error]
                    (is false (str "Unexpected insufficient approval refresh error: " error))
                    (done)))))))

(defn- approval-store
  [wallet-address]
  (atom {:tenant/override {:tenant/id "dexhelm"
                           :builder-fee configured-fee}
         :wallet {:address wallet-address}
         :builder-fee {:approval {:status :unapproved}}}))

(defn- loading-approval
  [request-id wallet-address]
  {:status :loading
   :request-id request-id
   :owner-address wallet-address
   :builder-address builder
   :network :mainnet})

(defn- ready-approval
  [request-id wallet-address max-builder-fee]
  (assoc (loading-approval request-id wallet-address)
         :status :ready
         :max-builder-fee max-builder-fee))

(deftest stale-success-cannot-overwrite-a-newer-pending-approval-test
  (async done
    (let [next-owner "0x2222222222222222222222222222222222222222"
          first-response (deferred-promise)
          second-response (deferred-promise)
          requests (atom [])
          store (approval-store owner)
          request-max-builder-fee!
          (fn [requested-owner requested-builder options]
            (swap! requests conj [requested-owner requested-builder options])
            (:promise (if (= 1 (count @requests))
                        first-response
                        second-response)))]
      (with-fake-date!
        [1000 1001]
        (fn []
          (let [first-request (effects/refresh-builder-fee-approval!
                               nil store {:request-max-builder-fee! request-max-builder-fee!})]
            (swap! store assoc-in [:wallet :address] next-owner)
            (let [second-request (effects/refresh-builder-fee-approval!
                                  nil store {:request-max-builder-fee! request-max-builder-fee!})]
              (is (= [[owner builder {:priority :high}]
                      [next-owner builder {:priority :high}]]
                     @requests))
              (is (= (loading-approval 1001 next-owner)
                     (get-in @store [:builder-fee :approval])))
              ((:resolve! first-response) 100)
              (-> first-request
                  (.then (fn [_]
                           (is (= (loading-approval 1001 next-owner)
                                  (get-in @store [:builder-fee :approval])))
                           ((:resolve! second-response) 10)
                           second-request))
                  (.then (fn [_]
                           (is (= (ready-approval 1001 next-owner 10)
                                  (get-in @store [:builder-fee :approval])))
                           (done)))
                  (.catch (fn [error]
                            (is false (str "Unexpected stale success race error: " error))
                            (done)))))))))))

(deftest stale-error-cannot-overwrite-a-newer-pending-approval-test
  (async done
    (let [next-owner "0x2222222222222222222222222222222222222222"
          first-response (deferred-promise)
          second-response (deferred-promise)
          requests (atom [])
          store (approval-store owner)
          request-max-builder-fee!
          (fn [requested-owner requested-builder options]
            (swap! requests conj [requested-owner requested-builder options])
            (:promise (if (= 1 (count @requests))
                        first-response
                        second-response)))]
      (with-fake-date!
        [2000 2001]
        (fn []
          (let [first-request (effects/refresh-builder-fee-approval!
                               nil store {:request-max-builder-fee! request-max-builder-fee!})]
            (swap! store assoc-in [:wallet :address] next-owner)
            (let [second-request (effects/refresh-builder-fee-approval!
                                  nil store {:request-max-builder-fee! request-max-builder-fee!})]
              (is (= (loading-approval 2001 next-owner)
                     (get-in @store [:builder-fee :approval])))
              ((:reject! first-response) (js/Error. "stale R1"))
              (-> first-request
                  (.then (fn [_]
                           (is false "Expected stale R1 to reject")))
                  (.catch (fn [_]
                            (is (= (loading-approval 2001 next-owner)
                                   (get-in @store [:builder-fee :approval])))
                            ((:resolve! second-response) 10)
                            second-request))
                  (.then (fn [_]
                           (is (= (ready-approval 2001 next-owner 10)
                                  (get-in @store [:builder-fee :approval])))
                           (done)))
                  (.catch (fn [error]
                            (is false (str "Unexpected stale error race failure: " error))
                            (done)))))))))))
