(ns hyperopen.state.trading.scale-pm-summary-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.state.trading :as trading]
            [hyperopen.state.trading.test-support :as support]
            [hyperopen.views.trade.order-form-summary-display :as summary-display]))

(def ^:private base-state
  support/base-state)

(defn- approx=
  [a b]
  (support/approx= a b))

(deftest order-summary-values-scale-from-generated-floored-legs-test
  (let [scale-state (assoc base-state
                           :active-asset "HYPE"
                           :active-market {:coin "HYPE"
                                           :mark 51.413
                                           :maxLeverage 40
                                           :market-type :perp
                                           :szDecimals 2}
                           :orderbooks {"HYPE" {:bids [] :asks []}})
        scale-form (assoc (trading/default-order-form)
                          :type :scale
                          :size "1414.70"
                          :ui-leverage 10
                          :scale {:start "48.5"
                                  :end "40.5"
                                  :count 100
                                  :skew "1.5"})
        incomplete-scale (assoc-in scale-form [:scale :end] "")
        limit-form (assoc (trading/default-order-form)
                          :type :limit
                          :size "2"
                          :price "100"
                          :ui-leverage 20)
        scale-summary (trading/order-summary scale-state scale-form)
        incomplete-summary (trading/order-summary scale-state incomplete-scale)
        limit-summary (trading/order-summary base-state limit-form)]
    (is (approx= 62546.995757575765 (:order-value scale-summary)))
    (is (= "$62,547.00"
           (summary-display/format-currency-or-na (:order-value scale-summary))))
    (is (nil? (:order-value incomplete-summary)))
    (is (= "N/A"
           (summary-display/format-currency-or-na (:order-value incomplete-summary))))
    (is (= 200 (:order-value limit-summary)))
    (is (= 10 (:margin-required limit-summary)))))

(deftest order-summary-keeps-portfolio-margin-risk-values-unavailable-test
  (let [form (assoc (trading/default-order-form)
                    :type :limit
                    :size "2"
                    :price "100"
                    :ui-leverage 20)
        state-with-balances (-> base-state
                                (assoc-in [:spot :clearinghouse-state :balances]
                                          [{:coin "USDC" :total "999" :hold "0"}])
                                (assoc-in [:webdata2 :clearinghouseState :withdrawable] "888"))
        pm-summary (trading/order-summary
                    (assoc state-with-balances
                           :account {:mode :unified
                                     :abstraction-raw " portfolioMargin "})
                    form)
        classic-summary (trading/order-summary
                         (assoc state-with-balances
                                :account {:mode :unified
                                          :abstraction-raw "unifiedAccount"})
                         form)]
    (is (nil? (:available-to-trade pm-summary)))
    (is (nil? (:margin-required pm-summary)))
    (is (= "N/A" (summary-display/format-usdc (:available-to-trade pm-summary))))
    (is (= "N/A" (summary-display/format-currency-or-na (:margin-required pm-summary))))
    (is (= 999 (:available-to-trade classic-summary)))
    (is (= 10 (:margin-required classic-summary)))))
