(ns hyperopen.portfolio.optimizer.application.execution-order-type
  "Pure per-order execution-type policy, shared by the Execution view (the order-type
  column + per-order editor) and the submission layer (apply-order-type-selections in
  application.execution) so the type a user sees can never diverge from the order that
  is actually routed.

  The four user-facing types map onto the order gateway as: :market -> marketable IOC,
  :limit -> resting GTC at mark +/- limit-bps, :passive -> post-only (ALO) limit that
  never crosses, :twap -> twapOrder sliced over twap-min minutes.")

(defn- abs-num [value] (if (number? value) (js/Math.abs value) 0))

(defn recommend-exec-type
  "Algorithm-recommended order type for a row, by clip size / side / market type
  (mirrors the v4 recommendExecType policy)."
  [{:keys [instrument-type side delta-notional-usd]}]
  (let [amount (abs-num delta-notional-usd)]
    (cond
      (>= amount 70000) :twap
      (and (= :sell side) (= :spot instrument-type)) :limit
      (<= amount 22000) :market
      :else :passive)))

(defn effective-type
  "Resolves the effective order type for a row from the staging selections: a per-row
  override wins, else the default order type (:recommended expands via
  recommend-exec-type). Always returns one of :market/:limit/:twap/:passive."
  [{:keys [default-order-type overrides]} row]
  (or (get overrides (:row-id row))
      (if (= :recommended default-order-type)
        (recommend-exec-type row)
        (or default-order-type :market))))

(defn row-params
  "Resolves the per-row execution params (limit-bps price offset + TWAP duration in
  minutes), merging the size/side-derived defaults with any per-row override params."
  [{:keys [params]} row]
  (merge {:limit-bps (if (= :buy (:side row)) -2 2)
          :twap-min (if (>= (abs-num (:delta-notional-usd row)) 70000) 20 10)}
         (get params (:row-id row))))

(defn recommended-routed-cost-usd
  "Total estimated execution cost of the ready rows under the Execution tab's default
  'recommended' routing (the same per-clip policy recommend-exec-type applies). Lets the
  rebalance preview show the cost the user actually pays at the execution default, not only
  the all-market upper bound that the domain summary aggregates. Crossing rows (market/twap)
  keep their taker fee + book-crossing slippage; resting rows (limit/passive) pay only the
  maker fee and no market impact — matching execution-tab type-aware-costs at default routing."
  [rows]
  (reduce
   (fn [total row]
     (if (= :ready (:status row))
       (let [cost (:cost row)
             resting? (contains? #{:limit :passive} (recommend-exec-type row))]
         (+ total
            (if resting?
              (or (:maker-fee-usd cost) 0)
              (+ (or (:estimated-slippage-usd cost) 0)
                 (or (:estimated-fee-usd cost) 0)))))
       total))
   0
   rows))
