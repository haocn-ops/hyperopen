(ns hyperopen.workbench.collections
  (:require [portfolio.data :as data]))

(def ^:private collections
  [[:shell "Shell" "App frame components and notifications."]
   [:primitives "Primitives" "Shared reusable controls and low-level UI pieces."]
   [:markets "Markets" "Market-selection, active market, order book, and chart controls."]
   [:trade "Trade" "Order ticket flows and trading-side composites."]
   [:account "Account" "Balances, positions, orders, and history surfaces."]
   [:account.history "History" "Trade, funding, and order history workbench scenes."]
   [:optimize "Optimize" "Portfolio optimizer result, refinement, and frontier surfaces."]
   [:funding "Funding" "Deposit, transfer, send, and withdraw workflows."]
   [:api "API" "API wallet management surfaces."]
   [:vaults "Vaults" "Vault discovery, detail charts, activity, and transfers."]])

(defn register!
  []
  (doseq [[id title docs] collections]
    (data/register-collection! id (cond-> {:kind :folder
                                           :title title
                                           :docs docs}
                                    (= id :account.history)
                                    (assoc :collection :account)))))
