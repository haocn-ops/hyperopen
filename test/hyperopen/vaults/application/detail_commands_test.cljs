(ns hyperopen.vaults.application.detail-commands-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.vaults.application.detail-commands :as detail-commands]))

(deftest set-vault-monte-carlo-control-normalizes-and-persists-under-vault-path-test
  (testing "values are normalized before saving under the vault state path"
    (is (= [[:effects/save [:vaults-ui :monte-carlo :bust] -95]]
           (detail-commands/set-vault-monte-carlo-control {} :bust -200))
        "out-of-range drawdown clamps to the floor")
    (is (= [[:effects/save [:vaults-ui :monte-carlo :sims] 2500]]
           (detail-commands/set-vault-monte-carlo-control {} "sims" 2500))
        "string control keys are accepted"))
  (testing "unknown controls are ignored"
    (is (nil? (detail-commands/set-vault-monte-carlo-control {} :unknown 5)))))

(deftest rerun-vault-monte-carlo-bumps-the-vault-run-nonce-test
  (is (= [[:effects/save [:vaults-ui :monte-carlo :run-nonce] 1]]
         (detail-commands/rerun-vault-monte-carlo {}))
      "re-run bumps the nonce from zero")
  (is (= [[:effects/save [:vaults-ui :monte-carlo :run-nonce] 6]]
         (detail-commands/rerun-vault-monte-carlo {:vaults-ui {:monte-carlo {:run-nonce 5}}}))
      "re-run increments the stored nonce"))
