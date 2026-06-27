(ns hyperopen.portfolio.optimizer.application.execution
  (:require [clojure.string :as str]
            [hyperopen.api.gateway.orders.commands :as order-commands]
            [hyperopen.asset-selector.markets :as markets]
            [hyperopen.portfolio.optimizer.application.execution-order-type :as execution-order-type]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def ^:private finite-positive? coercion/positive-number?)

(defn- parse-int-value
  [value]
  (let [num (cond
              (number? value) value
              (string? value) (js/parseInt value 10)
              :else js/NaN)]
    (when (and (number? num)
               (not (js/isNaN num)))
      (js/Math.floor num))))

(def ^:private non-blank-text coercion/non-blank-text)

(defn- ready-row?
  "A row is executable when it is :ready, has a tradeable instrument type, a positive
  lot quantity, a directional side, and a non-zero delta. Both perp and spot legs
  execute on Hyperliquid; the gateway routes by the market metadata (spot markets carry
  the 10000+idx wire asset-id and force reduce-only false)."
  [row]
  (and (= :ready (:status row))
       (contains? #{:perp :spot} (:instrument-type row))
       (finite-positive? (:quantity row))
       (not= :none (:side row))
       (not (zero? (or (:delta-notional-usd row) 0)))))

(defn- intent-for-row
  [execution-assumptions row]
  {:kind (if (= :spot (:instrument-type row)) :spot-order :perp-order)
   :instrument-id (:instrument-id row)
   :side (:side row)
   :quantity (:quantity row)
   :order-type (or (:default-order-type execution-assumptions) :market)
   ;; Reduce-only is perp-only — spot has no position to reduce. Kept explicitly false
   ;; (the gateway also force-falses it for spot) so the intent is unambiguous.
   :reduce-only? false})

(defn- execution-row
  [execution-assumptions row]
  (let [instrument-id (:instrument-id row)
        base-row (cond-> {:row-id instrument-id
                          :instrument-id instrument-id
                          :instrument-type (:instrument-type row)
                          :side (:side row)
                          :quantity (:quantity row)
                          :order-type (or (:default-order-type execution-assumptions) :market)
                          :delta-notional-usd (:delta-notional-usd row)
                          :cost (:cost row)}
                   (some? (:coin row)) (assoc :coin (:coin row))
                   (some? (:price row)) (assoc :price (:price row)))]
    (cond
      (ready-row? row)
      (assoc base-row
             :status :ready
             :intent (intent-for-row execution-assumptions row))

      (and (= :ready (:status row))
           (not (finite-positive? (:quantity row))))
      (assoc base-row
             :status :blocked
             :reason :quantity-below-lot)

      (and (= :ready (:status row))
           (or (= :none (:side row))
               (zero? (or (:delta-notional-usd row) 0))))
      (assoc base-row
             :status :blocked
             :reason :zero-delta-notional)

      (= :within-tolerance (:status row))
      (assoc base-row
             :status :skipped
             :reason :within-tolerance)

      (= :blocked (:status row))
      (assoc base-row
             :status :blocked
             :reason (:reason row))

      (= :ready (:status row))
      (assoc base-row
             :status :blocked
             :reason :unsupported-market-type)

      :else
      (assoc base-row
             :status :blocked
             :reason :unsupported-row-status))))

(defn- plan-status
  [ready-count blocked-count]
  (cond
    (and (pos? ready-count)
         (pos? blocked-count)) :partially-blocked
    (pos? ready-count) :ready
    (pos? blocked-count) :blocked
    :else :no-op))

(defn build-execution-plan
  [{:keys [scenario-id
           rebalance-preview
           execution-assumptions
           mutations-blocked-message]}]
  (let [rows (mapv #(execution-row execution-assumptions %)
                   (:rows rebalance-preview))
        ready-rows (filter #(= :ready (:status %)) rows)
        blocked-rows (filter #(= :blocked (:status %)) rows)
        skipped-rows (filter #(= :skipped (:status %)) rows)
        disabled-message (non-blank-text mutations-blocked-message)]
    {:scenario-id scenario-id
     :status (plan-status (count ready-rows) (count blocked-rows))
     :execution-disabled? (boolean disabled-message)
     :disabled-reason (when disabled-message :read-only)
     :disabled-message disabled-message
     :summary {:ready-count (count ready-rows)
               :blocked-count (count blocked-rows)
               :skipped-count (count skipped-rows)
               :gross-ready-notional-usd
               (reduce + 0 (map #(js/Math.abs (:delta-notional-usd %))
                                ready-rows))
               :estimated-fees-usd (get-in rebalance-preview
                                            [:summary :estimated-fees-usd])
               :estimated-slippage-usd (get-in rebalance-preview
                                                [:summary :estimated-slippage-usd])
               :margin (get-in rebalance-preview [:summary :margin])}
     :rows rows}))

(defn apply-order-type-selections
  "Re-resolves each ready row's order type + params from the live staging selections
  ({:default-order-type :overrides :params}) and stamps them onto the row's :intent
  (and top-level :order-type), so the submitted order matches what the user selected on
  the Execution tab. Non-ready rows are left untouched. Called at confirm time because
  the staged plan is built before the user picks types, and the order form is only
  materialized later (in the effect adapter) from the row's :intent."
  [plan selections]
  (if-not (map? selections)
    plan
    (update plan :rows
            (fn [rows]
              (mapv (fn [row]
                      (if (= :ready (:status row))
                        (let [order-type (execution-order-type/effective-type selections row)
                              params (execution-order-type/row-params selections row)]
                          (-> row
                              (assoc :order-type order-type)
                              (update :intent assoc
                                      :order-type order-type
                                      :limit-bps (:limit-bps params)
                                      :twap-min (:twap-min params))))
                        row))
                    (or rows []))))))

(defn- coin-for-row
  [row]
  (or (non-blank-text (:coin row))
      (let [instrument-id (non-blank-text (:instrument-id row))]
        (cond
          (str/starts-with? instrument-id "perp:")
          (subs instrument-id 5)

          (str/starts-with? instrument-id "spot:")
          (subs instrument-id 5)

          :else instrument-id))))

(defn- row-market
  [market-by-key row]
  (let [instrument-id (:instrument-id row)
        coin (coin-for-row row)]
    (or (get market-by-key instrument-id)
        (markets/resolve-or-infer-market-by-coin market-by-key coin))))

(defn- market-asset-idx
  [market]
  (some parse-int-value [(:asset-id market)
                         (:assetId market)
                         (:idx market)]))

(defn- offset-price
  "Mark price adjusted by a signed bps offset. The limit-bps default rests away from the
  market (buy -2 below mark, sell +2 above), so the resting order does not cross."
  [mark bps]
  (when (finite-positive? mark)
    (* mark (+ 1 (/ (or bps 0) 10000.0)))))

(defn- order-form-for-row
  "Translates a ready row's resolved :intent into the order-gateway form. The four UI
  types map onto the gateway as: :market -> marketable IOC at mark; :limit -> resting
  GTC at mark +/- limit-bps; :passive -> post-only (ALO) limit that never crosses;
  :twap -> twapOrder over twap-min minutes. Any unmapped type falls back to :market so
  it can never leak to build-order-request and become a stray resting GTC limit."
  [row]
  (let [intent (:intent row)
        order-type (or (:order-type intent) :market)
        mark (:price row)
        base {:side (:side intent)
              :size (:quantity intent)
              :reduce-only (boolean (:reduce-only? intent))
              :margin-mode :cross}]
    (case order-type
      :limit (assoc base
                    :type :limit
                    :tif :gtc
                    :price (offset-price mark (:limit-bps intent)))
      :passive (assoc base
                      :type :limit
                      :post-only true
                      :price (offset-price mark (:limit-bps intent)))
      :twap (assoc base
                   :type :twap
                   :twap {:minutes (max 5 (or (:twap-min intent) 10))
                          :randomize true})
      (assoc base :type :market :price mark))))

(defn- order-request-for-row
  [{:keys [market-by-key orderbooks]} row]
  (let [market (row-market market-by-key row)
        coin (coin-for-row row)
        asset-idx (market-asset-idx market)]
    (cond
      (nil? market)
      {:blocked-reason :market-metadata-missing}

      (nil? asset-idx)
      {:blocked-reason :market-metadata-missing}

      (not (finite-positive? (:price row)))
      {:blocked-reason :missing-price}

      :else
      (let [command-context {:active-asset coin
                             :asset-idx asset-idx
                             :market market
                             :orderbook (get orderbooks coin)}
            request (order-commands/build-order-request command-context
                                                        (order-form-for-row row))]
        (if (map? request)
          {:request request}
          {:blocked-reason :request-unavailable})))))

(defn- attempt-row
  [opts row]
  (if (= :ready (:status row))
    (let [{:keys [request blocked-reason]} (order-request-for-row opts row)]
      (if (map? request)
        (assoc row :request request)
        (-> row
            (assoc :status :blocked
                   :reason blocked-reason)
            (dissoc :intent))))
    row))

(defn build-execution-attempt
  [{:keys [plan market-by-key orderbooks]}]
  (let [rows (mapv #(attempt-row {:market-by-key (or market-by-key {})
                                  :orderbooks (or orderbooks {})}
                                 %)
                   (:rows plan))
        ready-count (count (filter #(= :ready (:status %)) rows))
        blocked-count (count (filter #(= :blocked (:status %)) rows))
        skipped-count (count (filter #(= :skipped (:status %)) rows))]
    (assoc plan
           :status (plan-status ready-count blocked-count)
           :summary (assoc (:summary plan)
                           :ready-count ready-count
                           :blocked-count blocked-count
                           :skipped-count skipped-count)
           :rows rows)))

(defn response-ok?
  [resp]
  (let [top-level-ok? (= "ok" (:status resp))
        statuses (let [statuses (get-in resp [:response :data :statuses])
                       status (get-in resp [:response :data :status])]
                   (cond
                     (sequential? statuses) statuses
                     (some? status) [status]
                     :else []))]
    (and top-level-ok?
         (not-any? #(and (map? %)
                         (contains? % :error))
                   statuses))))

(defn final-ledger-status
  [rows]
  (let [submitted-count (count (filter #(= :submitted (:status %)) rows))
        failed-count (count (filter #(= :failed (:status %)) rows))
        blocked-count (count (filter #(= :blocked (:status %)) rows))]
    (cond
      (and (pos? submitted-count)
           (zero? failed-count)
           (zero? blocked-count)) :executed
      (pos? submitted-count) :partially-executed
      (pos? failed-count) :failed
      (pos? blocked-count) :blocked
      :else :no-op)))

(defn- recoverable-row
  "Maps one ledger-attempt row into a fresh row for a Resume attempt. Failed rows are
  reset to :ready (dropping the stale request/response so a fresh request is built),
  already-submitted rows become :skipped :already-filled so they can NEVER be
  re-submitted, and everything else passes through unchanged."
  [row]
  (case (:status row)
    :failed (-> row
                (assoc :status :ready)
                (dissoc :request :response :error :pre-action-responses))
    :submitted (-> row
                   (assoc :status :skipped :reason :already-filled)
                   (dissoc :intent :request :response :error :pre-action-responses))
    row))

(defn build-resume-plan
  "Builds a retry plan from the original plan + the latest execution ledger. Only
  still-recoverable rows (previously :failed) are armed :ready; already-:submitted rows
  are demoted to :skipped :already-filled so a Resume can never double-submit a filled
  order. Summary counts and gross-ready notional are recomputed from the recovered set."
  [plan ledger]
  (let [rows (mapv recoverable-row (:rows ledger))
        ready-rows (filter #(= :ready (:status %)) rows)
        blocked-rows (filter #(= :blocked (:status %)) rows)
        skipped-rows (filter #(= :skipped (:status %)) rows)]
    (assoc plan
           :status (plan-status (count ready-rows) (count blocked-rows))
           :summary (assoc (:summary plan)
                           :ready-count (count ready-rows)
                           :blocked-count (count blocked-rows)
                           :skipped-count (count skipped-rows)
                           :gross-ready-notional-usd
                           (reduce + 0 (map #(js/Math.abs (or (:delta-notional-usd %) 0))
                                            ready-rows)))
           :rows rows)))

(defn- opposite-side
  [side]
  (case side :buy :sell :sell :buy side))

(defn- reversing-row
  "Turns one filled (:submitted) ledger row into a fresh :ready row that unwinds it:
  opposite side, same size, reduce-only for perps (spot has no position to reduce so the
  gateway forces reduce-only false anyway). Reverts always cross immediately (:market)."
  [row]
  (let [side (opposite-side (:side row))
        perp? (= :perp (:instrument-type row))]
    (-> (select-keys row [:row-id :instrument-id :instrument-type :coin :price :quantity])
        (assoc :side side
               :delta-notional-usd (- (or (:delta-notional-usd row) 0))
               :status :ready
               :order-type :market
               :intent {:kind (if perp? :perp-order :spot-order)
                        :instrument-id (:instrument-id row)
                        :side side
                        :quantity (:quantity row)
                        :order-type :market
                        :reduce-only? perp?}))))

(defn build-revert-plan
  "Builds a plan that unwinds every filled order from the latest ledger (reversing,
  reduce-only market orders), reusing the same execute path. The ledger is tagged
  :kind :revert so the persisted scenario history is distinguishable from a normal run."
  [plan ledger]
  (let [rows (->> (:rows ledger)
                  (filter #(= :submitted (:status %)))
                  (mapv reversing-row))]
    (assoc plan
           :kind :revert
           :status (plan-status (count rows) 0)
           :summary (assoc (:summary plan)
                           :ready-count (count rows)
                           :blocked-count 0
                           :skipped-count 0
                           :gross-ready-notional-usd
                           (reduce + 0 (map #(js/Math.abs (or (:delta-notional-usd %) 0))
                                            rows)))
           :rows rows)))

(defn- scale-row
  [factor row]
  (if (= :ready (:status row))
    (-> row
        (update :quantity #(when (number? %) (* % factor)))
        (update :delta-notional-usd #(when (number? %) (* % factor)))
        (update :intent (fn [intent]
                          (some-> intent
                                  (update :quantity #(when (number? %) (* % factor)))))))
    row))

(defn build-restaged-plan
  "Rebuilds the staged plan at a smaller clip: rows already filled in the latest ledger
  are dropped, and each remaining ready row's quantity + notional are scaled by `factor`
  (e.g. 0.5). Summary counts are recomputed; the result replaces the modal plan and the
  surface returns to :staged for re-review."
  [plan ledger factor]
  (let [filled-ids (->> (:rows ledger)
                        (filter #(= :submitted (:status %)))
                        (map :row-id)
                        set)
        rows (->> (:rows plan)
                  (remove #(contains? filled-ids (:row-id %)))
                  (mapv #(scale-row factor %)))
        ready-rows (filter #(= :ready (:status %)) rows)
        blocked-rows (filter #(= :blocked (:status %)) rows)
        skipped-rows (filter #(= :skipped (:status %)) rows)]
    (assoc plan
           :status (plan-status (count ready-rows) (count blocked-rows))
           :summary (assoc (:summary plan)
                           :ready-count (count ready-rows)
                           :blocked-count (count blocked-rows)
                           :skipped-count (count skipped-rows)
                           :gross-ready-notional-usd
                           (reduce + 0 (map #(js/Math.abs (or (:delta-notional-usd %) 0))
                                            ready-rows)))
           :rows rows)))
