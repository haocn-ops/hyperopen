(ns hyperopen.account.history.position-reduce-pricing
  (:require [hyperopen.domain.trading :as trading-domain]))

(defn- positive-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)
       (pos? value)))

(defn- outcome-close?
  [popover market]
  (or (= :outcome (:market-type popover))
      (= :outcome (:market-type market))))

(defn- market-close-context
  [state popover market asset-id]
  {:active-asset (:coin popover)
   :asset-idx asset-id
   :market market
   :orderbook (get-in state [:orderbooks (:coin popover)])})

(defn- fallback-protection-price
  [context form]
  (let [price (trading-domain/parse-num (:price form))
        side (:side form)
        slippage (let [parsed (trading-domain/parse-num (:slippage form))]
                   (trading-domain/clamp-percent
                    (if (number? parsed)
                      parsed
                      trading-domain/default-market-slippage-pct)))
        adjustment (if (= side :buy)
                     (+ 1 (/ slippage 100))
                     (- 1 (/ slippage 100)))]
    (when (positive-number? price)
      (trading-domain/canonical-order-price-string context
                                                   (* price adjustment)))))

(defn market-close-form
  [state popover market asset-id form]
  (if (and (= :market (:type form))
           (not (outcome-close? popover market)))
    (let [context (market-close-context state popover market asset-id)
          form* (dissoc form :price)]
      (or (trading-domain/apply-market-price context form*)
          (if-let [fallback-price (fallback-protection-price context form)]
            (assoc form* :price fallback-price)
            form*)))
    form))
