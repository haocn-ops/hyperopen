(ns hyperopen.portfolio.optimizer.domain.rebalance
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def default-fallback-slippage-bps
  25)

(def ^:private min-order-notional-usd
  ;; Hyperliquid rejects orders below a $10 notional. The same floor is encoded
  ;; for scale/TWAP suborders in hyperopen.domain.trading.core
  ;; (scale-min-endpoint-notional / twap-min-suborder-notional); kept local here
  ;; so the optimizer domain stays decoupled from the scale/TWAP trading domain.
  10)

(defn- abs-num
  [value]
  (js/Math.abs value))

(def ^:private finite-number? coercion/finite-number?)

(defn- side-for
  [delta-value]
  (cond
    (pos? delta-value) :buy
    (neg? delta-value) :sell
    :else :none))

(def ^:private finite-positive? coercion/positive-number?)

(def ^:private parse-number coercion/parse-float-number)

(defn- level-price
  [level]
  (or (parse-number (:px-num level))
      (parse-number (:px level))
      (parse-number (:price level))))

(defn- level-size
  [level]
  (or (parse-number (:sz-num level))
      (parse-number (:sz level))
      (parse-number (:size level))
      (parse-number (:qty level))))

(defn- snapshot-side-levels
  [context side]
  (case side
    :buy (or (:asks context)
             (:ask-levels context)
             (second (:levels context)))
    :sell (or (:bids context)
              (:bid-levels context)
              (first (:levels context)))
    nil))

(defn- slippage-bps-from-fill-price
  [side reference-price fill-price]
  (when (and (finite-positive? reference-price)
             (finite-positive? fill-price))
    (* 10000
       (/ (max 0
               (case side
                 :buy (- fill-price reference-price)
                 :sell (- reference-price fill-price)
                 0))
          reference-price))))

(defn- visible-depth-fill
  [levels quantity]
  (when (and (finite-positive? quantity)
             (seq levels))
    (loop [remaining quantity
           notional 0
           filled 0
           levels* (seq levels)]
      (if (or (not (pos? remaining))
              (nil? levels*))
        (when (pos? filled)
          (if (not (pos? remaining))
            {:estimated-fill-price (/ notional filled)
             :depth-status :full-visible-depth}
            {:visible-size filled
             :visible-notional notional
             :depth-status :insufficient-visible-depth}))
        (let [level (first levels*)
              price (level-price level)
              size (level-size level)]
          (if (and (finite-positive? price)
                   (finite-positive? size))
            (let [fill-size (min remaining size)]
              (recur (- remaining fill-size)
                     (+ notional (* fill-size price))
                     (+ filled fill-size)
                     (next levels*)))
            (recur remaining
                   notional
                   filled
                   (next levels*))))))))

(defn- orderbook-fill-price
  [context side]
  (case side
    :buy (level-price (:best-ask context))
    :sell (level-price (:best-bid context))
    nil))

(defn- orderbook-slippage-bps
  [context side reference-price]
  (let [fill-price (orderbook-fill-price context side)]
    (when (and (finite-positive? reference-price)
               (finite-positive? fill-price))
      (slippage-bps-from-fill-price side reference-price fill-price))))

(defn- depth-limited-slippage-bps
  "Conservative slippage for an order whose size exceeds the visible book. Charges
   the real VWAP slippage on the visible (consumed) portion, plus a penalty on the
   unfilled remainder that scales with how far the order overruns the book
   (shortfall multiple = quantity / visible-size). Floored at the flat fallback so
   it never under-charges relative to the prior flat assumption. We cannot see past
   the visible book, so the remainder penalty is an explicit assumption rather than
   a fabricated deep-book fill."
  [side reference-price quantity {:keys [visible-size visible-notional]} fallback]
  (if (and (finite-positive? visible-size)
           (finite-positive? visible-notional)
           (finite-positive? quantity))
    (let [visible-vwap (/ visible-notional visible-size)
          visible-slip (or (slippage-bps-from-fill-price side
                                                         reference-price
                                                         visible-vwap)
                           fallback)
          depth-ratio (min 1 (/ visible-size quantity))
          shortfall-mult (max 1 (/ quantity visible-size))
          remainder-bps (* fallback shortfall-mult)
          blended (+ (* depth-ratio visible-slip)
                     (* (- 1 depth-ratio) remainder-bps))]
      (max fallback blended))
    fallback))

(defn- finite-nonzero?
  [value]
  (and (finite-number? value)
       (not (zero? value))))

(defn- size-decimals
  [instrument]
  (some (fn [k]
          (let [value (get instrument k)]
            (cond
              (and (integer? value) (not (neg? value))) value
              (and (number? value) (not (neg? value))) (js/Math.floor value)
              :else nil)))
        [:szDecimals :sz-decimals :size-decimals :quantity-decimals]))

(defn- min-quantity
  [instrument]
  (some (fn [k]
          (let [value (get instrument k)]
            (when (finite-positive? value)
              value)))
        [:min-size :minSize :minSz :min-quantity]))

(defn- round-down-quantity
  [quantity decimals]
  (if (and (finite-positive? quantity)
           (integer? decimals))
    (let [scale (js/Math.pow 10 decimals)]
      (/ (js/Math.floor (* quantity scale)) scale))
    quantity))

(defn- executable-quantity
  [instrument price delta-notional-usd]
  (when (and (finite-positive? price)
             (finite-nonzero? delta-notional-usd))
    (let [raw-quantity (/ (abs-num delta-notional-usd) price)
          rounded (round-down-quantity raw-quantity (size-decimals instrument))
          min-size (min-quantity instrument)]
      (if (and (finite-positive? min-size)
               (< rounded min-size))
        0
        rounded))))

(defn- cost-metadata
  [context]
  (select-keys context [:age-ms :stale? :observed-at-ms :loaded-at-ms :received-at-ms]))

(defn- cost-context
  [opts instrument-id side reference-price quantity]
  (let [fallback (or (:fallback-slippage-bps opts)
                     default-fallback-slippage-bps)
        context (get-in opts [:cost-contexts-by-id instrument-id])
        source (case (:source context)
                 :fallback-cost-assumption :fallback-bps
                 nil :fallback-bps
                 (:source context))
        snapshot-fill (visible-depth-fill (snapshot-side-levels context side) quantity)
        fill-price (orderbook-fill-price context side)
        metadata (cost-metadata context)]
    (cond
      (:slippage-bps context)
      (merge metadata
             {:source source
              :estimated-fill-price (or fill-price reference-price)
              :slippage-bps (:slippage-bps context)})

      (= :full-visible-depth (:depth-status snapshot-fill))
      (let [estimated-fill-price (:estimated-fill-price snapshot-fill)]
        (merge metadata
               {:source source
                :estimated-fill-price estimated-fill-price
                :slippage-bps (or (slippage-bps-from-fill-price side
                                                                 reference-price
                                                                 estimated-fill-price)
                                  fallback)
                :depth-status :full-visible-depth}))

      (= :insufficient-visible-depth (:depth-status snapshot-fill))
      (merge metadata
             {:source :depth-extrapolated
              :estimated-fill-price reference-price
              :slippage-bps (depth-limited-slippage-bps side
                                                        reference-price
                                                        quantity
                                                        snapshot-fill
                                                        fallback)
              :depth-status :insufficient-visible-depth
              :fallback-reason :snapshot-depth-limited})

      :else
      (merge metadata
             {:source source
              :estimated-fill-price (or fill-price reference-price)
              :slippage-bps (or (orderbook-slippage-bps context side reference-price)
                                fallback)}))))

(defn- cost-estimate
  [opts instrument-id side reference-price delta-notional-usd quantity]
  (let [{:keys [source slippage-bps estimated-fill-price]
         :as context} (cost-context opts
                                    instrument-id
                                    side
                                    reference-price
                                    quantity)
        fee-bps (or (get-in opts [:fee-bps-by-id instrument-id])
                    (:default-fee-bps opts)
                    0)
        notional (abs-num delta-notional-usd)]
    (merge context
           {:source source
            :estimated-fill-price estimated-fill-price
            :notional-usd notional
            :slippage-bps slippage-bps
            :estimated-slippage-usd (* notional (/ slippage-bps 10000))
            :fee-bps fee-bps
            :estimated-fee-usd (* notional (/ fee-bps 10000))})))

(defn- row-status
  [{:keys [rebalance-tolerance]} instrument price capital-usd delta-weight delta-notional-usd quantity]
  (cond
    (<= (abs-num delta-weight) (or rebalance-tolerance 0))
    {:status :within-tolerance}

    (nil? instrument)
    {:status :blocked
     :reason :market-metadata-missing}

    (not (finite-positive? capital-usd))
    {:status :blocked
     :reason :missing-capital-base}

    (not (finite-positive? price))
    {:status :blocked
     :reason :missing-price}

    (not (finite-nonzero? delta-notional-usd))
    {:status :blocked
     :reason :zero-delta-notional}

    (< (abs-num delta-notional-usd) min-order-notional-usd)
    {:status :blocked
     :reason :below-min-notional}

    (not (finite-positive? quantity))
    {:status :blocked
     :reason :quantity-below-lot}

    :else
    {:status :ready}))

(defn- rebalance-row
  [opts instrument-id current-weight target-weight]
  (let [instrument (get-in opts [:instruments-by-id instrument-id])
        price (get-in opts [:prices-by-id instrument-id])
        capital-usd (or (:capital-usd opts) 0)
        delta-weight (- target-weight current-weight)
        delta-notional-usd (* capital-usd delta-weight)
        quantity (executable-quantity instrument price delta-notional-usd)
        status (row-status opts instrument price capital-usd delta-weight delta-notional-usd quantity)
        side (side-for (if (finite-nonzero? delta-notional-usd)
                         delta-notional-usd
                         delta-weight))]
    (merge {:instrument-id instrument-id
            :instrument-type (:instrument-type instrument)
            :coin (:coin instrument)
            :current-weight current-weight
            :target-weight target-weight
            :delta-weight delta-weight
            :delta-notional-usd delta-notional-usd
            :side side
            :price price
            :quantity quantity}
           status
           (when (= :ready (:status status))
             {:cost (cost-estimate opts
                                   instrument-id
                                   side
                                   price
                                   delta-notional-usd
                                   quantity)}))))

(defn- preview-status
  [rows]
  (let [ready? (boolean (some #(= :ready (:status %)) rows))
        blocked? (boolean (some #(= :blocked (:status %)) rows))]
    (cond
      (and ready? blocked?) :partially-blocked
      blocked? :blocked
      ready? :ready
      :else :no-op)))

(defn- leverage-for-row
  [opts row]
  (or (let [value (get-in opts [:leverage-by-id (:instrument-id row)])]
        (when (finite-positive? value) value))
      1))

(defn- margin-impact-usd
  [opts row]
  (if (and (= :ready (:status row))
           (= :perp (:instrument-type row)))
    (/ (abs-num (:delta-notional-usd row))
       (leverage-for-row opts row))
    0))

(defn- utilization
  [used total]
  (when (finite-positive? total)
    (/ (or used 0) total)))

(defn- margin-warning
  [after-utilization]
  (cond
    (and (finite-number? after-utilization)
         (> after-utilization 1)) :exceeds-equity
    (and (finite-number? after-utilization)
         (> after-utilization 0.8)) :high-utilization
    :else nil))

(defn- margin-summary
  [opts ready-rows]
  (let [capital-usd (:capital-usd opts)
        current-used (or (:current-margin-used-usdc opts) 0)
        impact (reduce + 0 (map #(margin-impact-usd opts %) ready-rows))
        after-used (+ current-used impact)
        before-utilization (utilization current-used capital-usd)
        after-utilization (utilization after-used capital-usd)]
    {:capital-usd capital-usd
     :current-used-usd current-used
     :estimated-impact-usd impact
     :after-used-usd after-used
     :before-utilization before-utilization
     :after-utilization after-utilization
     :warning (margin-warning after-utilization)}))

(defn build-rebalance-preview
  [{:keys [instrument-ids current-weights target-weights] :as opts}]
  (let [rows (mapv (fn [instrument-id current-weight target-weight]
                     (rebalance-row opts instrument-id current-weight target-weight))
                   instrument-ids
                   current-weights
                   target-weights)
        ready-rows (filter #(= :ready (:status %)) rows)]
    {:status (preview-status rows)
     :capital-usd (:capital-usd opts)
     :rows rows
     :summary {:ready-count (count (filter #(= :ready (:status %)) rows))
               :blocked-count (count (filter #(= :blocked (:status %)) rows))
               :within-tolerance-count (count (filter #(= :within-tolerance (:status %)) rows))
               :gross-trade-notional-usd (reduce + 0 (map #(abs-num (:delta-notional-usd %)) ready-rows))
               :estimated-fees-usd (reduce + 0 (map #(or (get-in % [:cost :estimated-fee-usd]) 0) ready-rows))
               :estimated-slippage-usd (reduce + 0 (map #(or (get-in % [:cost :estimated-slippage-usd]) 0) ready-rows))
               :margin (margin-summary opts ready-rows)}}))
