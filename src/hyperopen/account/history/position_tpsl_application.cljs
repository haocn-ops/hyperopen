(ns hyperopen.account.history.position-tpsl-application
  (:require [hyperopen.account.history.position-identity :as position-identity]
            [hyperopen.account.history.position-tpsl-policy :as position-tpsl-policy]
            [hyperopen.account.history.position-tpsl-state :as position-tpsl-state]
            [hyperopen.api.gateway.orders.commands :as order-commands]
            [hyperopen.asset-selector.markets :as markets]
            [hyperopen.domain.trading :as trading-domain]))

(defn- parse-num [value]
  (trading-domain/parse-num value))

(defn- parse-int-value
  [value]
  (let [num (cond
              (number? value) value
              (string? value) (js/parseInt value 10)
              :else js/NaN)]
    (when (and (number? num)
               (not (js/isNaN num)))
      (js/Math.floor num))))

(defn- candidate-market?
  [market coin dex]
  (let [coin* (position-tpsl-state/normalize-display-text coin)
        dex* (position-tpsl-state/normalize-display-text dex)
        market-coin* (position-tpsl-state/normalize-display-text (:coin market))
        market-dex* (position-tpsl-state/normalize-display-text (:dex market))]
    (and (= :perp (:market-type market))
         (= coin* market-coin*)
         (= (or dex* "")
            (or market-dex* "")))))

(defn- resolve-market-by-coin-and-dex [market-by-key coin dex]
  (let [markets* (vals (or market-by-key {}))
        exact (some #(when (candidate-market? % coin dex) %) markets*)
        fallback (markets/resolve-market-by-coin market-by-key coin)]
    (or exact fallback)))

(defn- resolve-market-asset-id [market]
  (or (some parse-int-value
            [(:asset-id market)
             (:assetId market)])
      (let [idx (parse-int-value (:idx market))
            dex (position-tpsl-state/normalize-display-text (:dex market))]
        (when (and (number? idx)
                   (or (nil? dex) (= "" dex)))
          idx))))

(defn- submit-form [modal]
  (let [{:keys [tp-trigger
                sl-trigger
                tp-limit
                sl-limit
                active-size
                limit-price?
                tp-enabled?
                sl-enabled?]} (position-tpsl-policy/parsed-inputs modal)
        order-side (position-tpsl-policy/side->order-side (:position-side modal))
        tp-is-market? (not limit-price?)
        sl-is-market? (not limit-price?)]
    {:side order-side
     :size active-size
     :tp {:enabled? tp-enabled?
          :trigger tp-trigger
          :is-market tp-is-market?
          :limit (if (and limit-price? tp-enabled?)
                   tp-limit
                   "")}
     :sl {:enabled? sl-enabled?
          :trigger sl-trigger
          :is-market sl-is-market?
          :limit (if (and limit-price? sl-enabled?)
                   sl-limit
                   "")}}))

(defn prepare-submit [state modal]
  (let [validation (position-tpsl-policy/validate-modal modal)
        market-by-key (get-in state [:asset-selector :market-by-key] {})
        market (resolve-market-by-coin-and-dex market-by-key
                                               (:coin modal)
                                               (:dex modal))
        asset-id (resolve-market-asset-id market)]
    (if-not (:is-ok validation)
      {:ok? false
       :display-message (:display-message validation)}
      (if-not (number? asset-id)
        {:ok? false
         :display-message "Select an asset and ensure market data is loaded."}
        (let [form (submit-form modal)
              orders (order-commands/build-tpsl-orders asset-id
                                                       (:side form)
                                                       form
                                                       {:active-asset (:coin modal)
                                                        :market market})]
          (if (seq orders)
            {:ok? true
             :display-message (:display-message validation)
             :request {:action {:type "order"
                                :orders orders
                                :grouping "positionTpsl"}}}
            {:ok? false
             :display-message "Place Order"}))))))

(defn from-position-row
  ([position-data]
   (from-position-row position-data nil))
  ([position-data anchor]
   (let [position (or (:position position-data) {})
         side (position-tpsl-policy/position-side (:szi position))
         size (position-tpsl-policy/absolute-position-size (:szi position))
         entry-price (or (parse-num (:entryPx position)) 0)
         mark-price (position-tpsl-policy/calculate-mark-price position)
         leverage (position-tpsl-policy/extract-leverage position)
         position-value (or (parse-num (:positionValue position))
                            (* size entry-price)
                            0)
         margin-used (or (parse-num (:marginUsed position))
                         (when (and (position-tpsl-policy/positive-number? position-value)
                                    (position-tpsl-policy/positive-number? leverage))
                           (/ position-value leverage))
                         0)]
     (assoc (position-tpsl-state/default-modal-state)
            :open? true
            :position-key (position-identity/position-unique-key position-data)
            :anchor (position-tpsl-state/normalize-anchor anchor)
            :coin (:coin position)
            :dex (position-tpsl-state/normalize-display-text (:dex position-data))
            :position-side side
            :entry-price entry-price
            :mark-price mark-price
            :position-size size
            :position-value position-value
            :margin-used margin-used
            :leverage leverage
            :size-input (if (position-tpsl-policy/positive-number? size)
                          (trading-domain/number->clean-string size 8)
                          "")
            :size-percent-input (if (position-tpsl-policy/positive-number? size) "100" "0")))))
