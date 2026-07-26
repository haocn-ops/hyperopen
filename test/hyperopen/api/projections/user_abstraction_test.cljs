(ns hyperopen.api.projections.user-abstraction-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api.projections.user-abstraction :as user-abstraction]))

(deftest user-abstraction-projection-ignores-stale-address-updates-test
  (let [snapshot {:mode :unified
                  :abstraction-raw "unifiedAccount"}
        owner-state {:wallet {:address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                     :account {:mode :classic}}
        owner-match (user-abstraction/apply-user-abstraction-snapshot owner-state
                                                                      "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                                      snapshot)
        owner-stale (user-abstraction/apply-user-abstraction-snapshot owner-state
                                                                      "0xdddddddddddddddddddddddddddddddddddddddd"
                                                                      snapshot)
        spectate-state {:wallet {:address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                        :account-context {:spectate-mode {:active? true
                                                          :address "0xdddddddddddddddddddddddddddddddddddddddd"}}
                        :account {:mode :classic}}
        spectate-match (user-abstraction/apply-user-abstraction-snapshot spectate-state
                                                                         "0xdddddddddddddddddddddddddddddddddddddddd"
                                                                         snapshot)
        spectate-stale (user-abstraction/apply-user-abstraction-snapshot spectate-state
                                                                         "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                                         snapshot)]
    (is (= snapshot (:account owner-match)))
    (is (= {:mode :classic} (:account owner-stale)))
    (is (= snapshot (:account spectate-match)))
    (is (= {:mode :classic} (:account spectate-stale)))))

(deftest user-abstraction-projection-uses-trader-route-before-owner-or-spectate-test
  (let [owner "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        spectate "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        trader "0xcccccccccccccccccccccccccccccccccccccccc"
        snapshot {:mode :unified
                  :abstraction-raw "unifiedAccount"}
        state {:wallet {:address owner}
               :router {:path (str "/portfolio/trader/" trader)}
               :account-context {:spectate-mode {:active? true
                                                 :address spectate}}
               :account {:mode :classic}}
        owner-request (user-abstraction/apply-user-abstraction-snapshot state owner snapshot)
        spectate-request (user-abstraction/apply-user-abstraction-snapshot state spectate snapshot)
        trader-request (user-abstraction/apply-user-abstraction-snapshot state trader snapshot)]
    (is (= {:mode :classic} (:account owner-request)))
    (is (= {:mode :classic} (:account spectate-request)))
    (is (= snapshot (:account trader-request)))))
