(ns hyperopen.views.trade.test-support
  (:require [hyperopen.surface-modules :as surface-modules]
            [hyperopen.state.trading :as trading]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.active-asset.test-support :as active-asset-support]))

(def with-viewport-width hiccup/with-viewport-width)

(def collect-strings hiccup/collect-strings)

(def find-first-node hiccup/find-first-node)

(def find-all-nodes hiccup/find-all-nodes)

(def node-class-set hiccup/node-class-set)

(def root-class-set hiccup/root-class-set)

(def node-text hiccup/node-text)

(def find-by-data-role hiccup/find-by-data-role)

(def find-by-parity-id hiccup/find-by-parity-id)

(defn base-state
  []
  {:active-asset nil
   :active-market nil
   :orderbooks {}
   :webdata2 {}
   :orders {:open-orders []
            :open-orders-snapshot []
            :open-orders-snapshot-by-dex {}
            :fills []
            :fundings []
            :order-history []
            :ledger []}
   :spot {:meta nil
          :clearinghouse-state nil}
   :perp-dex-clearinghouse {}
   :order-form (trading/default-order-form)
   :asset-selector {:visible-dropdown nil
                    :search-term ""
                    :sort-by :volume
                    :sort-direction :desc
                    :markets []
                    :market-by-key {}
                    :loading? false
                    :phase :bootstrap
                    :favorites #{}
                    :missing-icons #{}
                    :favorites-only? false
                    :strict? false
                    :active-tab :all}
   :chart-options {:selected-timeframe :1d
                   :selected-chart-type :candlestick}
   :orderbook-ui {:size-unit :base
                  :size-unit-dropdown-visible? false
                  :price-aggregation-dropdown-visible? false
                  :price-aggregation-by-coin {}
                  :active-tab :orderbook}
   :account-info {:selected-tab :balances
                  :loading false
                  :error nil
                  :hide-small-balances? false
                  :balances-sort {:column nil :direction :asc}
                  :positions-sort {:column nil :direction :asc}
                  :open-orders-sort {:column "Time" :direction :desc}}
   :trade-ui {:mobile-asset-details-open? false}})

(defn active-asset-state
  []
  (assoc (base-state)
         :active-asset "BTC"
         :active-market {:key "perp:BTC"
                         :coin "BTC"
                         :symbol "BTC-USDC"
                         :base "BTC"
                         :market-type :perp}
         :active-assets {:contexts {"BTC" {:coin "BTC"
                                           :mark 64000.0
                                           :markRaw "64000.0"
                                           :oracle 63990.0
                                           :oracleRaw "63990.0"
                                           :change24h 1500.0
                                           :change24hPct 2.4
                                           :volume24h 1250000.0
                                           :openInterest 250000.0
                                           :fundingRate 0.01}}
                         :funding-predictability {:by-coin {}
                                                  :loading-by-coin {}
                                                  :error-by-coin {}}}
         :asset-selector {:visible-dropdown nil
                          :search-term ""
                          :sort-by :volume
                          :sort-direction :desc
                          :markets [{:key "perp:BTC"
                                     :coin "BTC"
                                     :symbol "BTC-USDC"
                                     :base "BTC"
                                     :market-type :perp}
                                    {:key "perp:ETH"
                                     :coin "ETH"
                                     :symbol "ETH-USDC"
                                     :base "ETH"
                                     :market-type :perp}]
                          :market-by-key {"perp:BTC" {:key "perp:BTC"
                                                      :coin "BTC"
                                                      :symbol "BTC-USDC"
                                                      :base "BTC"
                                                      :market-type :perp}
                                          "perp:ETH" {:key "perp:ETH"
                                                      :coin "ETH"
                                                      :symbol "ETH-USDC"
                                                      :base "ETH"
                                                      :market-type :perp}}
                          :loading? false
                          :phase :bootstrap
                          :favorites #{}
                          :missing-icons #{}
                          :loaded-icons #{"perp:BTC"}
                          :favorites-only? false
                          :strict? false
                          :active-tab :all}
         :funding-ui {:tooltip {}}
         :trade-ui {:mobile-asset-details-open? false}))

(defn with-active-asset
  [state asset market]
  (assoc state
         :active-asset asset
         :active-market market))

(defn with-orderbooks
  [state asset orderbook]
  (assoc-in state [:orderbooks asset] orderbook))

(defn with-mobile-surface
  [state surface]
  (assoc-in state [:trade-ui :mobile-surface] surface))

(defn with-visible-funding-tooltip
  [state coin]
  (active-asset-support/with-visible-funding-tooltip state coin))

(defn account-surface-export-resolver
  [exports]
  (fn [surface-id export-id]
    (when (= :account-surfaces surface-id)
      (get exports export-id))))

(defn with-account-surface-exports
  [exports f]
  (with-redefs [surface-modules/resolved-surface-export
                (account-surface-export-resolver exports)]
    (f)))
