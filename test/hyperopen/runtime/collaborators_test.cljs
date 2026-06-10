(ns hyperopen.runtime.collaborators-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.history.effects :as account-history-effects]
            [hyperopen.funding.effects :as funding-effects]
            [hyperopen.funding-comparison.effects :as funding-comparison-effects]
            [hyperopen.leaderboard.effects :as leaderboard-effects]
            [hyperopen.runtime.collaborators :as collaborators]
            [hyperopen.staking.effects :as staking-effects]))

(deftest runtime-effect-deps-merges-defaults-with-overrides-test
  (let [save-fn (fn [& _] :save)
        export-fn (fn [& _] :export)
        deps (collaborators/runtime-effect-deps
              {:storage {:save save-fn}
               :api {:export-funding-history-csv export-fn}})]
    (is (identical? save-fn (get-in deps [:storage :save])))
    (is (identical? export-fn (get-in deps [:api :export-funding-history-csv])))
    (is (identical? account-history-effects/api-fetch-user-funding-history-effect
                    (get-in deps [:api :api-fetch-user-funding-history])))
    (is (identical? account-history-effects/api-fetch-historical-orders-effect
                    (get-in deps [:api :api-fetch-historical-orders])))
    (is (identical? leaderboard-effects/api-fetch-leaderboard!
                    (get-in deps [:api :api-fetch-leaderboard])))
    (is (identical? funding-comparison-effects/api-fetch-predicted-fundings!
                    (get-in deps [:api :api-fetch-predicted-fundings])))
    (is (nil? (get-in deps [:api :api-fetch-vault-index])))
    (is (nil? (get-in deps [:api :api-fetch-vault-index-with-cache])))
    (is (nil? (get-in deps [:api :api-fetch-vault-webdata2])))
    (is (nil? (get-in deps [:api :api-fetch-vault-ledger-updates])))
    (is (nil? (get-in deps [:api :api-submit-vault-transfer])))
    (is (identical? staking-effects/api-fetch-staking-validator-summaries!
                    (get-in deps [:api :api-fetch-staking-validator-summaries])))
    (is (identical? staking-effects/api-submit-staking-deposit!
                    (get-in deps [:api :api-submit-staking-deposit])))
    (is (identical? funding-effects/api-submit-funding-send!
                    (get-in deps [:api :api-submit-funding-send])))))

(deftest runtime-action-deps-overrides-default-action-handlers-test
  (let [connect-wallet-action* (fn [& _] :override-connect)
        navigate* (fn [& _] :navigate)
        deps (collaborators/runtime-action-deps
              {:wallet {:connect-wallet-action connect-wallet-action*}
               :core {:navigate navigate*}})]
    (is (identical? connect-wallet-action*
                    (get-in deps [:wallet :connect-wallet-action])))
    (is (identical? navigate*
                    (get-in deps [:core :navigate])))))
