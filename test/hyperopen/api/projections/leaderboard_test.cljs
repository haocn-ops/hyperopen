(ns hyperopen.api.projections.leaderboard-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api.projections.leaderboard :as leaderboard]))

(deftest leaderboard-projections-track-success-error-and-cache-hydration-test
  (let [state {:leaderboard {:rows []
                             :excluded-addresses #{}
                             :loading? false
                             :error "stale"
                             :error-category :transport
                             :loaded-at-ms nil}}
        loading (leaderboard/begin-leaderboard-load state)
        success (leaderboard/apply-leaderboard-success
                 loading
                 {:rows [{:eth-address "0x1"}]
                  :excluded-addresses ["0x2"]})
        hydrated (leaderboard/apply-leaderboard-cache-hydration
                  loading
                  {:saved-at-ms 1700000000000
                   :rows [{:eth-address "0x3"}]
                   :excluded-addresses ["0x4"]})
        failed (leaderboard/apply-leaderboard-error loading (js/Error. "leaderboard-fail"))]
    (is (= true (get-in loading [:leaderboard :loading?])))
    (is (nil? (get-in loading [:leaderboard :error])))
    (is (= [{:eth-address "0x1"}] (get-in success [:leaderboard :rows])))
    (is (= #{"0x2"} (get-in success [:leaderboard :excluded-addresses])))
    (is (number? (get-in success [:leaderboard :loaded-at-ms])))
    (is (= [{:eth-address "0x3"}] (get-in hydrated [:leaderboard :rows])))
    (is (= #{"0x4"} (get-in hydrated [:leaderboard :excluded-addresses])))
    (is (= 1700000000000 (get-in hydrated [:leaderboard :loaded-at-ms])))
    (is (= false (get-in hydrated [:leaderboard :loading?])))
    (is (nil? (get-in hydrated [:leaderboard :error])))
    (is (= "Error: leaderboard-fail" (get-in failed [:leaderboard :error])))
    (is (= :unexpected (get-in failed [:leaderboard :error-category])))))
