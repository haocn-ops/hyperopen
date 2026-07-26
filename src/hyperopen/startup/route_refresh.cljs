(ns hyperopen.startup.route-refresh
  (:require [hyperopen.api-wallets.actions :as api-wallets-actions]
            [hyperopen.funding-comparison.actions :as funding-comparison-actions]
            [hyperopen.leaderboard.actions :as leaderboard-actions]
            [hyperopen.portfolio.actions :as portfolio-actions]
            [hyperopen.portfolio.routes :as portfolio-routes]
            [hyperopen.referrals.actions :as referrals-actions]
            [hyperopen.router :as router]
            [hyperopen.staking.actions :as staking-actions]
            [hyperopen.subaccounts.actions :as subaccounts-actions]
            [hyperopen.vaults.infrastructure.routes :as vault-routes]))

(defn current-route-path
  [state]
  (router/normalize-path (or (get-in state [:router :path])
                             "/trade")))

(defn account-info-route?
  "Routes that render the account-info panel (open orders / positions / balances /
   trade history). Those tables can list spot instruments whose readable names
   (e.g. `@230` -> USDH) only resolve from the full, spot-inclusive asset-selector
   market catalog."
  [route]
  (or (router/trade-route? route)
      (portfolio-routes/portfolio-route? route)))

(defn account-info-markets-needed?
  "True when the account-info panel will render on `route` but the full
   (spot-inclusive) market catalog has not been requested yet. The perp-only
   bootstrap catalog cannot resolve spot coins, so account tables would otherwise
   show raw provider symbols like `@230`.

   Idempotent via the asset-selector phase: `begin-asset-selector-load` flips the
   phase to `:full` as soon as a full load starts, so an in-flight or completed
   full catalog makes this a no-op (mirrors the selector-open demand path)."
  [state route]
  (and (account-info-route? route)
       (not= :full (get-in state [:asset-selector :phase]))))

(defn current-route-refresh-effects
  [state new-address]
  (let [route (current-route-path state)]
    (cond-> (into []
                  (concat
                   (cond
                     (leaderboard-actions/leaderboard-route? route)
                     [[:actions/load-leaderboard-route route]]

                     (vault-routes/vault-route? route)
                     [[:actions/load-vault-route route]]

                     (funding-comparison-actions/funding-comparison-route? route)
                     [[:actions/load-funding-comparison-route route]]

                     (staking-actions/staking-route? route)
                     [[:actions/load-staking-route route]]

                     (referrals-actions/referrals-route? route)
                     [[:actions/load-referrals-route route]]

                     (api-wallets-actions/api-wallet-route? route)
                     [[:actions/load-api-wallet-route route]]

                     (subaccounts-actions/subaccounts-route? route)
                     [[:actions/load-subaccounts-route route]]

                     (portfolio-routes/portfolio-optimize-route? route)
                     [[:actions/load-portfolio-optimizer-route route]]

                     :else [])
                   (when (and (portfolio-routes/portfolio-route? route)
                              (seq (portfolio-actions/selected-portfolio-vault-benchmark-addresses state)))
                     [[:actions/load-vault-route route]])))
      (and new-address
           (portfolio-routes/portfolio-route? route)
           (not (portfolio-routes/portfolio-optimize-route? route)))
      (conj [:actions/select-portfolio-chart-tab
             (get-in state [:portfolio-ui :chart-tab])])

      (and new-address
           (not (subaccounts-actions/subaccounts-route? route)))
      (conj [:effects/api-load-subaccounts]))))
