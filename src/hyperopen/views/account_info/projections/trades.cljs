(ns hyperopen.views.account-info.projections.trades
  (:require [hyperopen.views.account-info.projections.parse :as parse]))

(def ^:private trade-history-trade-value-keys
  [:tradeValue :trade-value :tradeValueUsd :tradeValueUSDC :notional :notionalValue :value :quoteValue])

(def ^:private trade-history-fee-keys
  [:fee :feePaid :feeUsd :feeUSDC])

(def ^:private trade-history-closed-pnl-keys
  [:closedPnl :closed-pnl :closed_pnl :closedPnlUsd :closedPnlUSDC :realizedPnl])

(defn trade-history-coin [row]
  (or (:coin row) (:symbol row) (:asset row)))

(defn trade-history-time-ms [row]
  (parse/parse-epoch-ms (or (:time row) (:timestamp row) (:ts row) (:t row))))

(defn trade-history-first-parseable-row-value [row keys]
  (some (fn [k]
          (let [value (get row k)]
            (when (number? (parse/parse-optional-num value))
              value)))
        keys))

(defn trade-history-value-number [row]
  (let [explicit-value (trade-history-first-parseable-row-value row trade-history-trade-value-keys)]
    (or (parse/parse-optional-num explicit-value)
        (let [size (parse/parse-optional-num (or (:sz row) (:size row) (:s row)))
              price (parse/parse-optional-num (or (:px row) (:price row) (:p row)))]
          (when (and (number? size)
                     (number? price))
            (* size price))))))

(defn trade-history-fee-number [row]
  (parse/parse-optional-num (trade-history-first-parseable-row-value row trade-history-fee-keys)))

(defn trade-history-closed-pnl-number [row]
  (parse/parse-optional-num (trade-history-first-parseable-row-value row trade-history-closed-pnl-keys)))

(defn trade-history-row-id [row]
  (str (or (:tid row) (:id row) "")
       "|"
       (or (trade-history-time-ms row) 0)
       "|"
       (or (trade-history-coin row) "")
       "|"
       (or (:px row) (:price row) (:p row) "")
       "|"
       (or (:sz row) (:size row) (:s row) "")))
