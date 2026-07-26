(ns hyperopen.portfolio.optimizer.application.view-model.workspace-loading-test
  "The /optimize/new default path auto-seeds the universe from holdings once
  account data arrives. These tests pin the derived pending state: readiness
  must say :holdings-loading (not \"select a universe\") exactly while (a) the
  draft is untouched, (b) an account address exists, and (c) the perp
  clearinghouse snapshot has not arrived."
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.view-model :as view-model]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]))

(def ^:private route
  {:kind :optimize-new})

(def ^:private pending-state
  {:router {:path "/portfolio/optimize/new"}
   :wallet {:address "0x162cc7c861ebd0c06b3d72319201150482518185"}})

(deftest workspace-model-reports-holdings-loading-while-seed-pending-test
  (let [pending (view-model/workspace-model pending-state route)]
    (is (= :holdings-loading (get-in pending [:readiness :reason])))
    (is (false? (:run-triggerable? pending)))))

(deftest workspace-model-ends-holdings-loading-on-source-arrival-test
  (let [arrived (view-model/workspace-model
                 (assoc pending-state :webdata2
                        {:clearinghouseState {:marginSummary {:accountValue "1000"}
                                              :assetPositions []}})
                 route)]
    (is (= :missing-universe (get-in arrived [:readiness :reason]))
        "Source arrival ends the wait even for an empty book (the seed writes nothing).")))

(deftest workspace-model-skips-holdings-loading-without-an-account-test
  (let [no-account (view-model/workspace-model
                    {:router {:path "/portfolio/optimize/new"}}
                    route)]
    (is (= :missing-universe (get-in no-account [:readiness :reason]))
        "No account means no holdings are coming; keep the manual affordances.")))

(deftest workspace-model-skips-holdings-loading-for-a-touched-draft-test
  (let [touched (view-model/workspace-model
                 (assoc-in pending-state
                           [:portfolio :optimizer :draft]
                           (assoc-in (optimizer-defaults/default-draft)
                                     [:metadata :universe-source]
                                     {:kind :custom :omitted []}))
                 route)]
    (is (= :missing-universe (get-in touched [:readiness :reason]))
        "A cleared/custom draft is a deliberate empty universe, not a pending load.")))

(deftest workspace-model-skips-holdings-loading-off-the-new-draft-route-test
  (let [other-route (view-model/workspace-model pending-state
                                                {:kind :optimize-scenario
                                                 :scenario-id "scn_x"})]
    (is (= :missing-universe (get-in other-route [:readiness :reason])))))

(deftest readiness-panel-model-names-the-holdings-wait-test
  (let [model (view-model/readiness-panel-model
               {:status :blocked :reason :holdings-loading :runnable? false}
               {})]
    (is (= "Waiting for your holdings snapshot — the universe fills itself when account data arrives."
           (:copy model)))))
