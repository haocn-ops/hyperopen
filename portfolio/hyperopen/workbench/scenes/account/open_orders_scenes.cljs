(ns hyperopen.workbench.scenes.account.open-orders-scenes
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.fixtures :as fixtures]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.views.account-info.tabs.open-orders :as open-orders]))

(portfolio/configure-scenes
  {:title "Open Orders"
   :collection :account})

(defn- orders
  []
  [(fixtures/open-order)
   (fixtures/open-order {:oid 987654322
                         :coin "ETH"
                         :type "Stop Market"
                         :side "S"
                         :sz 1.2
                         :orig-sz 1.2
                         :px 3410
                         :reduce-only true
                         :is-trigger true
                         :trigger-condition "Below"
                         :trigger-px 3380})
   (fixtures/open-order {:oid 987654323
                         :coin "SOL"
                         :type "Take Profit Limit"
                         :side "A"
                         :sz 4.0
                         :orig-sz 5.0
                         :px 214
                         :reduce-only true
                         :is-position-tpsl true
                         :is-trigger true
                         :trigger-condition "Above"
                         :trigger-px 215})])

(defn- orders-panel
  [content]
  (layout/page-shell
   (layout/desktop-shell
    (layout/panel-shell {:class ["h-[620px]"]}
     content))))

(portfolio/defscene default
  []
  (orders-panel
   (open-orders/open-orders-tab-content
    (orders)
    {:column "Time" :direction :desc}
    {:direction-filter :all
     :coin-search ""
     :market-by-key {"BTC" {:coin "BTC" :symbol "BTC-USDC"}
                     "ETH" {:coin "ETH" :symbol "ETH-USDC"}
                     "SOL" {:coin "SOL" :symbol "SOL-USDC"}}})))

(portfolio/defscene filtered
  []
  (orders-panel
   (open-orders/open-orders-tab-content
    (orders)
    {:column "Coin" :direction :asc}
    {:direction-filter :short
     :coin-search "eth"
     :market-by-key {"ETH" {:coin "ETH" :symbol "ETH-USDC"}}})))

(portfolio/defscene empty-state
  []
  (orders-panel
   (open-orders/open-orders-tab-content [] {:column "Time" :direction :desc} {:direction-filter :all})))
