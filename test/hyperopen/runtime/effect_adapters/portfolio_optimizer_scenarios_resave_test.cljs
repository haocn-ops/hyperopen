(ns hyperopen.runtime.effect-adapters.portfolio-optimizer-scenarios-resave-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer-scenarios :as scenario-effects]
            [hyperopen.test-support.async :as async-support]))

(deftest loaded-saved-scenario-can-save-again-using-record-address-test
  (async done
    (let [address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          saved-run (fixtures/sample-last-successful-run
                     {:computed-at-ms 2000
                      :result {:status :solved
                               :expected-return 0.18
                               :volatility 0.42}})
          scenario-record {:schema-version 1
                           :id "scn_saved"
                           :name "Core Hedge"
                           :address address
                           :status :saved
                           :config {:id "scn_saved"
                                    :name "Core Hedge"
                                    :objective {:kind :max-sharpe}
                                    :return-model {:kind :historical-mean}
                                    :risk-model {:kind :diagonal-shrink}
                                    :metadata {:dirty? false}}
                           :saved-run saved-run
                           :updated-at-ms 3000}
          store (atom {:router {:path "/portfolio/optimize/scn_saved"}
                       :portfolio {:optimizer {}}})
          calls (atom [])
          env {:now-ms (fn [] 4200)
               :next-scenario-id (fn [_now-ms] "scn_unexpected")
               :load-scenario!
               (fn [scenario-id]
                 (swap! calls conj [:load-scenario scenario-id])
                 (js/Promise.resolve scenario-record))
               :load-tracking!
               (fn [scenario-id]
                 (swap! calls conj [:load-tracking scenario-id])
                 (js/Promise.resolve nil))
               :load-scenario-index!
               (fn [addr]
                 (swap! calls conj [:load-index addr])
                 (js/Promise.resolve {:ordered-ids ["scn_saved"]
                                      :by-id {"scn_saved" {:id "scn_saved"
                                                           :name "Core Hedge"
                                                           :status :saved}}}))
               :save-scenario!
               (fn [scenario-id record]
                 (swap! calls conj [:save-scenario scenario-id record])
                 (js/Promise.resolve true))
               :save-scenario-index!
               (fn [addr index]
                 (swap! calls conj [:save-index addr index])
                 (js/Promise.resolve true))
               :dispatch! (fn [& _] nil)}]
      (-> (scenario-effects/load-portfolio-optimizer-scenario-effect
           env
           store
           "scn_saved"
           nil)
          (.then (fn [_loaded]
                   (scenario-effects/save-portfolio-optimizer-scenario-effect
                    env
                    store
                    {:name "Updated Core Hedge"})))
          (.then (fn [record]
                   (is (= "scn_saved" (:id record)))
                   (is (= "Updated Core Hedge" (:name record)))
                   (is (= "Updated Core Hedge" (get-in record [:config :name])))
                   (is (= address (:address record)))
                   (is (= address
                          (get-in @store
                                  [:portfolio :optimizer :active-scenario :address])))
                   (is (= address
                          (second (first (filter #(= :load-index (first %))
                                                 @calls)))))
                   (is (= address
                          (second (first (filter #(= :save-index (first %))
                                                 @calls)))))
                   (is (= "Updated Core Hedge"
                          (get-in @store
                                  [:portfolio :optimizer :scenario-index :by-id "scn_saved" :name])))
                   (done)))
          (.catch (async-support/unexpected-error done))))))
