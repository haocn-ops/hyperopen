(ns hyperopen.startup.route-refresh-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.startup.route-refresh :as route-refresh]))

(deftest current-route-path-defaults-to-trade-when-route-is-missing-test
  (is (= "/trade"
         (route-refresh/current-route-path {}))))

(deftest current-route-refresh-effects-target-route-and-global-account-state-test
  (testing "trade route refreshes global subaccount header state"
    (is (= [[:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/trade"}}
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
  (testing "leaderboard route refreshes only leaderboard"
    (is (= [[:actions/load-leaderboard-route "/leaderboard"]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/leaderboard"}}
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
  (testing "vault detail route refreshes only vaults"
    (is (= [[:actions/load-vault-route "/vaults/0xabc"]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/vaults/0xabc"}}
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
  (testing "funding comparison route refreshes only funding comparison"
    (is (= [[:actions/load-funding-comparison-route "/funding-comparison"]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/funding-comparison"}}
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
  (testing "staking route refreshes only staking"
    (is (= [[:actions/load-staking-route "/staking"]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/staking"}}
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
  (testing "referrals route refreshes only referrals"
    (is (= [[:actions/load-referrals-route "/join/ABC123"]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/join/ABC123"}}
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
  (testing "api route refreshes only api wallets"
    (is (= [[:actions/load-api-wallet-route "/api"]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/api"}}
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
  (testing "subaccounts route refreshes only subaccounts"
    (is (= [[:actions/load-subaccounts-route "/subAccounts"]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/subAccounts"}}
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
  (testing "optimizer scenario route refreshes only optimizer scenario state"
    (is (= [[:actions/load-portfolio-optimizer-route "/portfolio/optimize/scn_route"]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/portfolio/optimize/scn_route"}}
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))))

(deftest current-route-refresh-effects-preserve-portfolio-chart-bootstrap-test
  (let [address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]
    (is (= [[:actions/select-portfolio-chart-tab :returns]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/portfolio"}
             :portfolio-ui {:chart-tab :returns}}
            address)))
    (is (= [[:actions/select-portfolio-chart-tab :returns]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/portfolio/trader/0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
             :portfolio-ui {:chart-tab :returns}}
            address)))
    (is (= []
           (route-refresh/current-route-refresh-effects
            {:router {:path "/portfolio"}
             :portfolio-ui {:chart-tab :returns}}
            nil)))
    (is (= [[:actions/load-portfolio-optimizer-route "/portfolio/optimize/scn_01"]]
           (route-refresh/current-route-refresh-effects
            {:router {:path "/portfolio/optimize/scn_01"}
             :portfolio-ui {:chart-tab :returns}}
            nil)))))

(deftest account-info-route?-test
  (testing "routes that render the account-info panel are account-info routes"
    (is (true? (boolean (route-refresh/account-info-route? "/trade"))))
    (is (true? (boolean (route-refresh/account-info-route? "/portfolio"))))
    (is (true? (boolean (route-refresh/account-info-route?
                         "/portfolio/trader/0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")))))
  (testing "routes without the account-info panel are not account-info routes"
    (is (not (route-refresh/account-info-route? "/leaderboard")))
    (is (not (route-refresh/account-info-route? "/vaults/0xabc")))
    (is (not (route-refresh/account-info-route? "/staking")))))

(deftest account-info-markets-needed?-test
  (testing "account-info routes need the full catalog until a :full load is in flight/done"
    ;; Regression: bootstrap builds a perp-only catalog, so spot open orders
    ;; (coin like \"@230\") would otherwise render as the raw provider symbol
    ;; instead of a readable name (USDH). The account-info routes must request
    ;; the full, spot-inclusive catalog.
    (is (true? (route-refresh/account-info-markets-needed?
                {:asset-selector {:phase :bootstrap}} "/trade")))
    (is (true? (route-refresh/account-info-markets-needed?
                {:asset-selector {:phase :bootstrap}} "/portfolio")))
    (is (true? (route-refresh/account-info-markets-needed?
                {:asset-selector {:phase :bootstrap}}
                "/portfolio/trader/0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")))
    (testing "missing phase (pre-bootstrap default) still counts as needed"
      (is (true? (route-refresh/account-info-markets-needed? {} "/trade")))))
  (testing "no-op once the full catalog has been requested (idempotent via phase)"
    (is (false? (route-refresh/account-info-markets-needed?
                 {:asset-selector {:phase :full}} "/trade")))
    (is (false? (route-refresh/account-info-markets-needed?
                 {:asset-selector {:phase :full}} "/portfolio"))))
  (testing "non-account-info routes never request the full catalog from here"
    (is (false? (route-refresh/account-info-markets-needed?
                 {:asset-selector {:phase :bootstrap}} "/leaderboard")))
    (is (false? (route-refresh/account-info-markets-needed?
                 {:asset-selector {:phase :bootstrap}} "/staking")))))

(deftest current-route-refresh-effects-loads-portfolio-vault-benchmark-support-test
  (let [address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        benchmark-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        state {:router {:path "/portfolio"}
               :portfolio-ui {:chart-tab :returns
                              :returns-benchmark-coins ["BTC"
                                                        "HYPE"
                                                        (str "vault:" benchmark-address)]}}]
    (is (= [[:actions/load-vault-route "/portfolio"]]
           (route-refresh/current-route-refresh-effects state nil)))
    (is (= [[:actions/load-vault-route "/portfolio"]
            [:actions/select-portfolio-chart-tab :returns]
            [:effects/api-load-subaccounts]]
           (route-refresh/current-route-refresh-effects state address)))))
