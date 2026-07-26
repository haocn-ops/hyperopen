(ns hyperopen.funding-comparison.effects-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.funding-comparison.effects :as effects]))

(deftest api-fetch-predicted-fundings-applies-begin-and-success-projections-test
  (async done
    (let [request-calls (atom [])
          store (atom {:router {:path "/funding-comparison"}})]
      (-> (effects/api-fetch-predicted-fundings!
           {:store store
            :request-predicted-fundings! (fn [opts]
                                           (swap! request-calls conj opts)
                                           (js/Promise.resolve [["BTC" []]]))
            :begin-funding-comparison-load (fn [state]
                                             (assoc state :loading? true))
            :apply-funding-comparison-success (fn [state rows]
                                                (assoc state :rows rows))
            :apply-funding-comparison-error (fn [state err]
                                              (assoc state :error err))
            :opts {:priority :high}})
          (.then (fn [rows]
                   (is (= [["BTC" []]] rows))
                   (is (= [{:priority :high}] @request-calls))
                   (is (= true (:loading? @store)))
                   (is (= [["BTC" []]] (:rows @store)))
                   (done)))
          (.catch (fn [err]
                    (js/console.error err)
                    (is false "Unexpected predicted fundings success-path error")
                    (done)))))))

(deftest api-fetch-predicted-fundings-bypasses-route-gate-when-explicitly-disabled-test
  (async done
    (let [request-calls (atom [])
          store (atom {:router {:path "/trade"}})]
      (-> (effects/api-fetch-predicted-fundings!
           {:store store
            :request-predicted-fundings! (fn [opts]
                                           (swap! request-calls conj opts)
                                           (js/Promise.resolve [["ETH" []]]))
            :begin-funding-comparison-load (fn [state]
                                             (assoc state :loading? true))
            :apply-funding-comparison-success (fn [state rows]
                                                (assoc state :rows rows))
            :apply-funding-comparison-error (fn [state err]
                                              (assoc state :error err))
            :opts {:skip-route-gate? true
                   :priority :high
                   :client-tag "test"}})
          (.then (fn [rows]
                   (is (= [["ETH" []]] rows))
                   (is (= [{:priority :high
                            :client-tag "test"}]
                          @request-calls))
                   (is (= true (:loading? @store)))
                   (is (= [["ETH" []]] (:rows @store)))
                   (done)))
          (.catch (fn [err]
                    (js/console.error err)
                    (is false "Unexpected route-gate bypass error")
                    (done)))))))

(deftest api-fetch-predicted-fundings-defaults-options-to-empty-map-test
  (async done
    (let [request-calls (atom [])
          store (atom {:router {:path "/funding-comparison"}})]
      (-> (effects/api-fetch-predicted-fundings!
           {:store store
            :request-predicted-fundings! (fn [opts]
                                           (swap! request-calls conj opts)
                                           (js/Promise.resolve []))
            :begin-funding-comparison-load (fn [state]
                                             (assoc state :loading? true))
            :apply-funding-comparison-success (fn [state rows]
                                                (assoc state :rows rows))
            :apply-funding-comparison-error (fn [state err]
                                              (assoc state :error err))})
          (.then (fn [rows]
                   (is (= [] rows))
                   (is (= [{}] @request-calls))
                   (is (= true (:loading? @store)))
                   (is (= [] (:rows @store)))
                   (done)))
          (.catch (fn [err]
                    (js/console.error err)
                    (is false "Unexpected default-options error")
                    (done)))))))

(deftest api-fetch-predicted-fundings-applies-error-projection-test
  (async done
    (let [store (atom {:router {:path "/funding-comparison"}})]
      (-> (effects/api-fetch-predicted-fundings!
           {:store store
            :request-predicted-fundings! (fn [_opts]
                                           (js/Promise.reject (js/Error. "boom")))
            :begin-funding-comparison-load (fn [state]
                                             (assoc state :loading? true))
            :apply-funding-comparison-success (fn [state rows]
                                                (assoc state :rows rows))
            :apply-funding-comparison-error (fn [state err]
                                              (assoc state :error (.-message err)))})
          (.then (fn [_]
                   (is false "Expected predicted fundings request to reject")
                   (done)))
          (.catch (fn [err]
                    (is (= "boom" (.-message err)))
                    (is (= true (:loading? @store)))
                    (is (= "boom" (:error @store)))
                    (done)))))))

(deftest api-fetch-predicted-fundings-skips-when-route-is-inactive-test
  (async done
    (let [request-calls (atom 0)
          store (atom {:router {:path "/trade"}})]
      (-> (effects/api-fetch-predicted-fundings!
           {:store store
            :request-predicted-fundings! (fn [_opts]
                                           (swap! request-calls inc)
                                           (js/Promise.resolve []))
            :begin-funding-comparison-load (fn [state]
                                             (assoc state :loading? true))
            :apply-funding-comparison-success (fn [state rows]
                                                (assoc state :rows rows))
            :apply-funding-comparison-error (fn [state err]
                                              (assoc state :error err))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= 0 @request-calls))
                   (is (nil? (:loading? @store)))
                   (done)))
          (.catch (fn [err]
                    (js/console.error err)
                    (is false "Unexpected inactive-route rejection")
                    (done)))))))
