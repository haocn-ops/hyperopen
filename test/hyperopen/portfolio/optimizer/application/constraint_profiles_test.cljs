(ns hyperopen.portfolio.optimizer.application.constraint-profiles-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.application.constraint-profiles :as profiles]))

(def ^:private universe-a
  [{:instrument-id "perp:BTC"} {:instrument-id "perp:ETH"}])

(deftest universe-key-is-stable-and-order-independent-test
  (is (= (profiles/universe-key universe-a)
         (profiles/universe-key (reverse universe-a)))
      "the key depends on the set of ids, not their order")
  (is (not= (profiles/universe-key universe-a)
            (profiles/universe-key (conj universe-a {:instrument-id "perp:SOL"})))
      "a different asset set gets a different key")
  (is (nil? (profiles/universe-key []))
      "an empty universe has no key"))

(deftest put-and-select-profile-round-trip-test
  (let [record (profiles/profile-record {:gross-max 2.0} "u1" 1000)
        store (profiles/put-profile {} record)]
    (is (= record (profiles/select-profile store "u1")))
    (is (= {:gross-max 2.0 :net-band-pct 0.0} (profiles/remembered-constraints store "u1"))
        "a pre-percentage profile reads back with the migrated :net-band-pct default")
    (is (true? (profiles/has-default? store "u1")))
    (is (false? (profiles/has-default? store "u2")))
    (is (nil? (profiles/remembered-constraints store "u2")))))

(deftest auto-apply-respects-pristine-draft-test
  (let [store (profiles/put-profile {} (profiles/profile-record {:gross-max 1.5} "u1" 1000))]
    (testing "pristine draft with a saved default ⇒ apply"
      (is (= {:gross-max 1.5 :net-band-pct 0.0}
             (profiles/auto-apply-constraints {:profiles store :universe-key "u1" :dirty? false}))))
    (testing "dirty draft ⇒ never clobber"
      (is (nil? (profiles/auto-apply-constraints {:profiles store :universe-key "u1" :dirty? true}))))
    (testing "no saved default ⇒ nil"
      (is (nil? (profiles/auto-apply-constraints {:profiles store :universe-key "u2" :dirty? false}))))))
