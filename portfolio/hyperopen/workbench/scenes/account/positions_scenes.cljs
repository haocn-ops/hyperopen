(ns hyperopen.workbench.scenes.account.positions-scenes
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.fixtures :as fixtures]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.views.account-info.tabs.positions :as positions]))

(portfolio/configure-scenes
  {:title "Positions"
   :collection :account})

(defn- positions-rows
  []
  [(fixtures/position-row)
   (fixtures/position-row {:position {:coin "ETH"
                                      :leverage {:value 3}
                                      :szi "-1.2000"
                                      :positionValue "4105.80"
                                      :entryPx "3424.20"
                                      :markPx "3418.10"
                                      :unrealizedPnl "-18.40"
                                      :returnOnEquity "-0.014"
                                      :liquidationPx "4218.12"
                                      :marginUsed "1368.60"
                                      :cumFunding {:allTime "-3.44"}}
                          :dex "HL"})])

(defn- positions-panel
  [content]
  (layout/page-shell
   (layout/desktop-shell
    (layout/panel-shell {:class ["h-[640px]"]}
     content))))

(portfolio/defscene mixed-book
  []
  (positions-panel
   (positions/positions-tab-content {:positions (positions-rows)
                                     :sort-state {:column "Position Value" :direction :desc}
                                     :positions-state {:direction-filter :all}})))

(portfolio/defscene long-only
  []
  (positions-panel
   (positions/positions-tab-content {:positions [(fixtures/position-row)]
                                     :sort-state {:column "Coin" :direction :asc}
                                     :positions-state {:direction-filter :long}})))

(portfolio/defscene empty-state
  []
  (positions-panel
   (positions/positions-tab-content {:positions []
                                     :sort-state {:column "Coin" :direction :asc}
                                     :positions-state {:direction-filter :all}})))

(portfolio/defscene mobile-expanded-card
  []
  (layout/page-shell
   (layout/mobile-shell
    (layout/panel-shell {:class ["h-[720px]" "p-0"]}
     (positions/positions-tab-content {:positions (positions-rows)
                                       :sort-state {:column "Coin" :direction :asc}
                                       :positions-state {:direction-filter :all
                                                         :mobile-expanded-card {:positions (positions/position-unique-key (first (positions-rows)))}}})))))
