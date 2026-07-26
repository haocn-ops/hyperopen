(ns hyperopen.state.trading.test-support
  (:require [hyperopen.state.trading :as trading]))

(def base-state
  {:active-asset "BTC"
   :active-market {:coin "BTC"
                   :mark 100
                   :maxLeverage 40
                   :szDecimals 4}
   :orderbooks {"BTC" {:bids [{:px "99"}]
                       :asks [{:px "101"}]}}
   :webdata2 {:clearinghouseState {:marginSummary {:accountValue "1000"
                                                   :totalMarginUsed "250"}
                                   :assetPositions [{:position {:coin "BTC"
                                                                :szi "0.5"
                                                                :liquidationPx "80"}}]}}})

(defn apply-order-form-transition
  "Merge an order-form transition map (as returned by the transition helpers)
   back into app state so a sequence of transitions can be threaded in tests."
  [state transition]
  (merge state (select-keys transition [:order-form :order-form-ui :order-form-runtime])))

(defn spot-buy-state
  "Classic (non-unified) spot-market app state with USDC buying power and a
   quote-denominated buy ticket, for spot affordability / size-coherence tests."
  [{:keys [ask usdc sz-decimals]
    :or {ask "1.00" usdc "100" sz-decimals 2}}]
  {:active-asset "PURR"
   :active-market {:coin "PURR"
                   :quote "USDC"
                   :market-type :spot
                   :maxLeverage 1
                   :szDecimals sz-decimals}
   :orderbooks {"PURR" {:bids [{:px "0.99" :sz "1000"}]
                        :asks [{:px ask :sz "1000"}]}}
   :spot {:clearinghouse-state {:balances [{:coin "USDC" :total usdc :hold "0"}
                                           {:coin "PURR" :total "0" :hold "0"}]}}
   :order-form (assoc (trading/default-order-form) :side :buy :type :market :entry-mode :market)
   :order-form-ui (assoc (trading/default-order-form-ui)
                         :size-input-mode :quote
                         :entry-mode :market)
   :order-form-runtime (trading/default-order-form-runtime)})

(defn set-active-best-ask
  "Replace the active spot market's best-ask, simulating an order-book tick."
  [state px]
  (assoc-in state [:orderbooks "PURR" :asks] [{:px px :sz "1000"}]))

(defn approx= [a b]
  (<= (js/Math.abs (- a b)) 0.000001))

(defn js-object-keys
  [value]
  (->> (js/Object.keys value)
       array-seq
       vec))

(defn validation-codes
  [errors]
  (->> (or errors [])
       (keep :code)
       set))
