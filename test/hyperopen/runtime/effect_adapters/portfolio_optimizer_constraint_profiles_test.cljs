(ns hyperopen.runtime.effect-adapters.portfolio-optimizer-constraint-profiles-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.portfolio.optimizer.application.constraint-profiles :as profiles]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer :as adapters]
            [hyperopen.test-support.async :as async-support]))

(def ^:private address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
(def ^:private universe [{:instrument-id "perp:BTC"} {:instrument-id "perp:ETH"}])

(defn- store-with
  [{:keys [constraints dirty? profiles]}]
  (atom {:wallet {:address address}
         :portfolio {:optimizer
                     {:draft {:universe universe
                              :constraints constraints
                              :metadata {:dirty? (boolean dirty?)}}
                      :constraint-profiles (or profiles {})}}}))

(deftest save-constraint-default-effect-persists-and-updates-state-test
  (async done
    (let [saved (atom nil)
          store (store-with {:constraints {:gross-max 1.7 :net-min 0.5 :net-max 0.5}})]
      (with-redefs [adapters/*now-ms* (fn [] 4242)
                    adapters/*save-constraint-profiles!*
                    (fn [addr profile-map]
                      (reset! saved [addr profile-map])
                      (js/Promise.resolve true))]
        (-> (adapters/save-portfolio-optimizer-constraint-default-effect nil store)
            (.then (fn [_]
                     (let [[addr profile-map] @saved
                           uk (profiles/universe-key universe)]
                       (is (= address addr))
                       (is (= {:gross-max 1.7 :net-min 0.5 :net-max 0.5}
                              (get-in profile-map [uk :controls])))
                       (is (= 4242 (get-in profile-map [uk :saved-at-ms])))
                       (is (= profile-map
                              (get-in @store [:portfolio :optimizer :constraint-profiles]))
                           "state mirrors what was persisted")
                       (done))))
            (.catch (async-support/unexpected-error done)))))))

(deftest load-constraint-profiles-effect-auto-applies-on-pristine-draft-test
  (async done
    (let [uk (profiles/universe-key universe)
          remembered {:gross-max 1.5 :net-min 0.0 :net-max 0.0 :net-band-pct 0.0
                      :max-asset-weight 0.3}
          stored {uk {:universe-key uk :controls remembered}}
          store (store-with {:constraints {:gross-max 2.0 :net-min 1.0 :net-max 1.0}
                             :dirty? false})]
      (with-redefs [adapters/*load-constraint-profiles!* (fn [_addr] (js/Promise.resolve stored))]
        (-> (adapters/load-portfolio-optimizer-constraint-profiles-effect nil store)
            (.then (fn [_]
                     (is (= stored (get-in @store [:portfolio :optimizer :constraint-profiles])))
                     (is (= remembered (get-in @store [:portfolio :optimizer :draft :constraints]))
                         "a pristine draft is seeded from the remembered default")
                     (done)))
            (.catch (async-support/unexpected-error done)))))))

(deftest load-constraint-profiles-effect-never-clobbers-dirty-draft-test
  (async done
    (let [uk (profiles/universe-key universe)
          remembered {:gross-max 1.5 :net-min 0.0 :net-max 0.0}
          stored {uk {:universe-key uk :controls remembered}}
          edited {:gross-max 2.2 :net-min 1.1 :net-max 1.3}
          store (store-with {:constraints edited :dirty? true})]
      (with-redefs [adapters/*load-constraint-profiles!* (fn [_addr] (js/Promise.resolve stored))]
        (-> (adapters/load-portfolio-optimizer-constraint-profiles-effect nil store)
            (.then (fn [_]
                     (is (= stored (get-in @store [:portfolio :optimizer :constraint-profiles]))
                         "profiles are still hydrated into state")
                     (is (= edited (get-in @store [:portfolio :optimizer :draft :constraints]))
                         "a user-edited (dirty) draft is left untouched")
                     (done)))
            (.catch (async-support/unexpected-error done)))))))
