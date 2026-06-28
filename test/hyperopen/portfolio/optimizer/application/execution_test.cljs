(ns hyperopen.portfolio.optimizer.application.execution-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.execution :as execution]))

(def sample-preview
  {:status :partially-blocked
   :summary {:ready-count 1
             :blocked-count 1
             :within-tolerance-count 1
             :gross-trade-notional-usd 2000
             :estimated-fees-usd 1.0
             :estimated-slippage-usd 2.5
             :margin {:after-used-usd 500}}
   :rows [{:instrument-id "perp:BTC"
           :instrument-type :perp
           :status :ready
           :side :buy
           :quantity 0.25
           :delta-notional-usd 1000
           :cost {:source :fallback-bps
                  :estimated-fee-usd 1.0
                  :estimated-slippage-usd 2.5}}
          {:instrument-id "spot:PURR"
           :instrument-type :spot
           :status :blocked
           :reason :spot-submit-unsupported
           :side :sell
           :delta-notional-usd -1000}
          {:instrument-id "perp:ETH"
           :instrument-type :perp
           :status :within-tolerance
           :side :none
           :delta-notional-usd 0.5}]})

(deftest build-execution-plan-classifies-ready-blocked-and-skipped-rows-test
  (let [plan (execution/build-execution-plan
              {:scenario-id "scn_1"
               :rebalance-preview sample-preview
               :execution-assumptions {:default-order-type :market
                                       :fee-mode :taker}})]
    (is (= :partially-blocked (:status plan)))
    (is (= "scn_1" (:scenario-id plan)))
    (is (= {:ready-count 1
            :blocked-count 1
            :skipped-count 1
            :gross-ready-notional-usd 1000
            :estimated-fees-usd 1.0
            :estimated-slippage-usd 2.5
            :margin {:after-used-usd 500}}
           (:summary plan)))
    (is (= {:kind :perp-order
            :instrument-id "perp:BTC"
            :side :buy
            :quantity 0.25
            :order-type :market
            :reduce-only? false}
           (get-in plan [:rows 0 :intent])))
    (is (= :market (get-in plan [:rows 0 :order-type])))
    (is (= :blocked (get-in plan [:rows 1 :status])))
    (is (= :spot-submit-unsupported (get-in plan [:rows 1 :reason])))
    (is (= :skipped (get-in plan [:rows 2 :status])))
    (is (= :within-tolerance (get-in plan [:rows 2 :reason])))))

(deftest build-execution-plan-keeps-spectate-mode-read-only-test
  (let [plan (execution/build-execution-plan
              {:scenario-id "scn_read_only"
               :rebalance-preview sample-preview
               :mutations-blocked-message "Spectate Mode is read-only."})]
    (is (= true (:execution-disabled? plan)))
    (is (= :read-only (:disabled-reason plan)))
    (is (= "Spectate Mode is read-only." (:disabled-message plan)))
    (is (= :partially-blocked (:status plan)))))

(deftest build-execution-attempt-attaches-order-request-for-ready-perp-row-test
  (let [plan (execution/build-execution-plan
              {:scenario-id "scn_submit"
               :rebalance-preview
               {:rows [{:instrument-id "perp:BTC"
                        :instrument-type :perp
                        :coin "BTC"
                        :status :ready
                        :side :buy
                        :price 100
                        :quantity 0.25
                        :delta-notional-usd 25}]}
               :execution-assumptions {:default-order-type :market}})
        attempt (execution/build-execution-attempt
                 {:plan plan
                  :market-by-key {"perp:BTC" {:coin "BTC"
                                              :market-type :perp
                                              :asset-id 0
                                              :szDecimals 4}}})
        row (first (:rows attempt))]
    (is (= :ready (:status attempt)))
    (is (= :ready (:status row)))
    (is (= {:type "order"
            :orders [{:a 0
                      :b true
                      :p "100"
                      :s "0.25"
                      :r false
                      :t {:limit {:tif "Ioc"}}}]
            :grouping "na"}
           (get-in row [:request :action])))))

(deftest build-execution-attempt-floors-wire-size-to-sz-decimals-test
  ;; The optimizer derives quantity = |notional| / price as a raw float, so the wire :s
  ;; would otherwise carry 14-17 sig figs (e.g. SOPH szDecimals 0 -> "7968.855385980289").
  ;; Hyperliquid deserializes :s into a precision-bounded decimal and rejects over-precise
  ;; sizes ("Failed to deserialize the JSON body into the target type"). The size must be
  ;; floored to the catalog szDecimals, exactly like the manual order path.
  (let [attempt-for (fn [coin sz-decimals quantity]
                      (let [plan (execution/build-execution-plan
                                  {:scenario-id "scn_sz"
                                   :rebalance-preview
                                   {:rows [{:instrument-id (str "perp:" coin)
                                            :instrument-type :perp
                                            :coin coin
                                            :status :ready
                                            :side :sell
                                            :price 0.005
                                            :quantity quantity
                                            :delta-notional-usd -40}]}
                                   :execution-assumptions {:default-order-type :market}})]
                        (execution/build-execution-attempt
                         {:plan plan
                          :market-by-key {(str "perp:" coin)
                                          {:coin coin
                                           :market-type :perp
                                           :asset-id 197
                                           :szDecimals sz-decimals}}})))
        wire-size (fn [attempt]
                    (get-in (first (:rows attempt))
                            [:request :action :orders 0 :s]))]
    ;; szDecimals 0 -> integer size, no fractional part
    (is (= "7968" (wire-size (attempt-for "SOPH" 0 7968.855385980289))))
    ;; szDecimals 2 -> at most 2 decimals (floored)
    (is (= "1.08" (wire-size (attempt-for "SILVER" 2 1.089665624397716))))
    ;; szDecimals 3 -> at most 3 decimals (floored)
    (is (= "0.574" (wire-size (attempt-for "BABA" 3 0.57497234024216))))))

(deftest build-execution-attempt-blocks-row-when-size-floors-below-one-lot-test
  ;; A quantity smaller than one lot for the asset (szDecimals 0, quantity < 1) floors to
  ;; zero, which is not a placeable order; the row is blocked rather than sent as ":s 0".
  (let [plan (execution/build-execution-plan
              {:scenario-id "scn_dust"
               :rebalance-preview
               {:rows [{:instrument-id "perp:SOPH"
                        :instrument-type :perp
                        :coin "SOPH"
                        :status :ready
                        :side :sell
                        :price 0.005
                        :quantity 0.7
                        :delta-notional-usd -0.0035}]}
               :execution-assumptions {:default-order-type :market}})
        attempt (execution/build-execution-attempt
                 {:plan plan
                  :market-by-key {"perp:SOPH" {:coin "SOPH"
                                               :market-type :perp
                                               :asset-id 197
                                               :szDecimals 0}}})
        row (first (:rows attempt))]
    (is (= :blocked (:status row)))
    (is (nil? (get-in row [:request :action])))))

(deftest build-execution-attempt-routes-selected-order-types-test
  (let [base (execution/build-execution-plan
              {:scenario-id "scn_types"
               :rebalance-preview
               {:rows [{:instrument-id "perp:BTC"
                        :instrument-type :perp
                        :coin "BTC"
                        :status :ready
                        :side :buy
                        :price 100
                        :quantity 0.25
                        :delta-notional-usd 25}]}
               :execution-assumptions {:default-order-type :market}})
        market-by-key {"perp:BTC" {:coin "BTC"
                                   :market-type :perp
                                   :asset-id 0
                                   :szDecimals 4}}
        action-for (fn [selections]
                     (-> base
                         (execution/apply-order-type-selections selections)
                         (#(execution/build-execution-attempt
                            {:plan % :market-by-key market-by-key}))
                         (get-in [:rows 0 :request :action])))]
    ;; Limit override -> resting GTC limit, price offset below mark for a buy.
    (let [action (action-for {:default-order-type :recommended
                              :overrides {"perp:BTC" :limit}
                              :params {"perp:BTC" {:limit-bps -5}}})
          order (first (:orders action))]
      (is (= "order" (:type action)))
      (is (= {:tif "Gtc"} (:limit (:t order))))
      (is (< (js/parseFloat (:p order)) 100)))
    ;; Passive override -> post-only (ALO) limit that never crosses.
    (let [action (action-for {:default-order-type :recommended
                              :overrides {"perp:BTC" :passive}
                              :params {}})]
      (is (= {:tif "Alo"} (:limit (:t (first (:orders action)))))))
    ;; TWAP override -> twapOrder action sliced over the selected minutes.
    (let [action (action-for {:default-order-type :recommended
                              :overrides {"perp:BTC" :twap}
                              :params {"perp:BTC" {:twap-min 20}}})]
      (is (= "twapOrder" (:type action)))
      (is (= 20 (get-in action [:twap :m]))))))

(deftest build-execution-attempt-routes-ready-spot-row-test
  (let [plan (execution/build-execution-plan
              {:scenario-id "scn_spot"
               :rebalance-preview
               {:rows [{:instrument-id "spot:PURR"
                        :instrument-type :spot
                        :coin "PURR"
                        :status :ready
                        :side :sell
                        :price 2
                        :quantity 400
                        :delta-notional-usd -800}]}
               :execution-assumptions {:default-order-type :market}})
        attempt (execution/build-execution-attempt
                 {:plan plan
                  :market-by-key {"spot:PURR" {:coin "PURR"
                                               :market-type :spot
                                               :asset-id 11035
                                               :szDecimals 2}}})
        row (first (:rows attempt))
        order (first (get-in row [:request :action :orders]))]
    (is (= :ready (:status attempt)))
    (is (= :ready (:status row)))
    (is (= :spot-order (get-in plan [:rows 0 :intent :kind])))
    (is (= "order" (get-in row [:request :action :type])))
    ;; spot wire asset-id = 10000 + spot pair index
    (is (= 11035 (:a order)))
    ;; sell -> is-buy false
    (is (= false (:b order)))
    ;; reduce-only forced false for spot (no position to reduce)
    (is (= false (:r order)))))

(deftest build-execution-attempt-blocks-ready-row-without-market-metadata-test
  (let [plan (execution/build-execution-plan
              {:scenario-id "scn_missing_market"
               :rebalance-preview
               {:rows [{:instrument-id "perp:BTC"
                        :instrument-type :perp
                        :coin "BTC"
                        :status :ready
                        :side :buy
                        :price 100
                        :quantity 0.25
                        :delta-notional-usd 25}]}})
        attempt (execution/build-execution-attempt {:plan plan})]
    (is (= :blocked (:status attempt)))
    (is (= :blocked (get-in attempt [:rows 0 :status])))
    (is (= :market-metadata-missing (get-in attempt [:rows 0 :reason])))))

(deftest build-execution-plan-blocks-ready-row-with-non-executable-values-test
  (let [zero-quantity (execution/build-execution-plan
                       {:scenario-id "scn_zero_qty"
                        :rebalance-preview
                        {:rows [{:instrument-id "perp:BTC"
                                 :instrument-type :perp
                                 :status :ready
                                 :side :buy
                                 :quantity 0
                                 :delta-notional-usd 100}]}})
        no-side (execution/build-execution-plan
                 {:scenario-id "scn_no_side"
                  :rebalance-preview
                  {:rows [{:instrument-id "perp:BTC"
                           :instrument-type :perp
                           :status :ready
                           :side :none
                           :quantity 0.01
                           :delta-notional-usd 100}]}})]
    (is (= :blocked (:status zero-quantity)))
    (is (= :quantity-below-lot (get-in zero-quantity [:rows 0 :reason])))
    (is (= 0 (get-in zero-quantity [:summary :ready-count])))
    (is (= :blocked (:status no-side)))
    (is (= :zero-delta-notional (get-in no-side [:rows 0 :reason])))))

(defn- near?
  [expected actual]
  (and (number? actual)
       (< (js/Math.abs (- expected actual)) 0.0000001)))

(deftest realized-fill-scales-slippage-usd-to-filled-quantity-test
  ;; Realized $ slippage must scale with the quantity ACTUALLY filled. On a partial fill
  ;; (totalSz < order size, remainder resting under the same oid), only the filled portion
  ;; incurs slippage cost — using the full intended notional overstates it. bps is a per-unit
  ;; price comparison and is unaffected by the filled quantity.
  (let [row {:side :buy :price 100 :quantity 1.0 :delta-notional-usd 100}
        partial (execution/realized-fill
                 row
                 {:status "ok"
                  :response {:data {:statuses [{:filled {:oid 1 :totalSz "0.5" :avgPx "101"}}]}}})
        full (execution/realized-fill
              row
              {:status "ok"
               :response {:data {:statuses [{:filled {:oid 1 :totalSz "1.0" :avgPx "101"}}]}}})]
    ;; buy @ ref 100, fill 101 -> +100 bp regardless of how much filled
    (is (near? 100 (:slippage-bps partial)))
    (is (near? 100 (:slippage-bps full)))
    ;; 0.5 filled @ ref 100 = $50 filled notional -> $0.50 slippage (NOT $1.00 on the full size)
    (is (near? 0.5 (:slippage-usd partial)))
    ;; a full fill of the same row still costs the full $1.00
    (is (near? 1.0 (:slippage-usd full)))))

(deftest settled-row-status-classifies-resting-vs-filled-test
  ;; HL reports per-order outcomes in [:response :data :statuses]. A :filled entry => the order
  ;; crossed (:submitted); a :resting entry (no fill) => it sits open on the book (:resting); an
  ;; OK response with neither (legacy "success" shape) keeps the :submitted fallback.
  (is (= :submitted
         (execution/settled-row-status
          {:status "ok"
           :response {:data {:statuses [{:filled {:oid 1 :avgPx "10" :totalSz "1"}}]}}}))
      "a filled entry -> :submitted")
  (is (= :resting
         (execution/settled-row-status
          {:status "ok" :response {:data {:statuses [{:resting {:oid 2}}]}}}))
      "a resting (open) entry, no fill -> :resting")
  (is (= :submitted
         (execution/settled-row-status
          {:status "ok" :response {:data {:statuses ["success"]}}}))
      "legacy/empty OK shape -> :submitted (immediate-cross behavior preserved)"))

(deftest final-ledger-status-distinguishes-resting-from-filled-test
  ;; A passive limit order that only rests on the book (HL :resting, no :filled) is accepted but
  ;; NOT a fill. A run of resting orders must be :resting, never :executed — otherwise the tab
  ;; reports "all filled" when nothing filled.
  (is (= :resting
         (execution/final-ledger-status [{:status :resting} {:status :resting}]))
      "all orders resting on the book -> :resting (never :executed)")
  (is (= :resting
         (execution/final-ledger-status [{:status :submitted} {:status :resting}]))
      "some filled, some still resting -> :resting (not fully executed)")
  (is (= :executed
         (execution/final-ledger-status [{:status :submitted} {:status :submitted}]))
      "every accepted order filled outright -> :executed (unchanged)")
  (is (= :partially-executed
         (execution/final-ledger-status [{:status :resting} {:status :failed}]))
      "a live resting order alongside a rejection -> partial run (halted)")
  (is (= :failed
         (execution/final-ledger-status [{:status :failed} {:status :failed}]))
      "pure failures unchanged"))

(deftest build-resume-plan-skips-resting-rows-test
  ;; Resume retries the failed rows; an order already resting (live, open) on the book must
  ;; never be re-submitted, so it is demoted to :skipped just like an already-filled row.
  (let [plan {:summary {} :rows []}
        ledger {:rows [{:row-id "perp:BTC" :status :resting :side :buy
                        :quantity 1 :delta-notional-usd 10}
                       {:row-id "perp:ETH" :status :failed :side :buy
                        :quantity 2 :delta-notional-usd 20}]}
        resumed (execution/build-resume-plan plan ledger)
        by-id (into {} (map (juxt :row-id identity) (:rows resumed)))]
    (is (= :skipped (get-in by-id ["perp:BTC" :status]))
        "resting row is demoted to :skipped, never re-submitted")
    (is (= :ready (get-in by-id ["perp:ETH" :status]))
        "the failed row is re-armed for retry")
    (is (= 1 (get-in resumed [:summary :ready-count]))
        "only the failed row counts toward the resume")))

(deftest build-restaged-plan-drops-resting-rows-test
  ;; Re-stage smaller drops orders already live on the exchange (filled OR resting) so a
  ;; re-stage can never duplicate a working order; only still-unsent rows are re-staged.
  (let [plan {:summary {}
              :rows [{:row-id "perp:BTC" :status :ready :quantity 4
                      :delta-notional-usd 40 :intent {:quantity 4}}
                     {:row-id "perp:ETH" :status :ready :quantity 2
                      :delta-notional-usd 20 :intent {:quantity 2}}]}
        ledger {:rows [{:row-id "perp:BTC" :status :resting}
                       {:row-id "perp:ETH" :status :failed}]}
        restaged (execution/build-restaged-plan plan ledger 0.5)
        ids (set (map :row-id (:rows restaged)))]
    (is (not (contains? ids "perp:BTC"))
        "resting (live) row is dropped, not re-staged")
    (is (contains? ids "perp:ETH")
        "the unfilled/failed row is re-staged")
    (is (= 1 (count (:rows restaged))))))

(deftest build-revert-plan-excludes-resting-rows-test
  ;; Revert unwinds FILLED legs with reversing reduce-only orders. A resting (open, unfilled)
  ;; order holds no position to unwind, so it must be excluded from the revert plan — only
  ;; filled rows are reversed.
  (let [plan {:summary {} :rows []}
        ledger {:rows [{:row-id "perp:BTC" :status :submitted :side :buy :instrument-type :perp
                        :instrument-id "perp:BTC" :price 100 :quantity 1 :delta-notional-usd 100}
                       {:row-id "perp:ETH" :status :resting :side :buy :instrument-type :perp
                        :instrument-id "perp:ETH" :price 50 :quantity 2 :delta-notional-usd 100}]}
        revert (execution/build-revert-plan plan ledger)
        ids (set (map :row-id (:rows revert)))]
    (is (contains? ids "perp:BTC") "the filled leg is reversed")
    (is (not (contains? ids "perp:ETH")) "the resting (unfilled) leg is NOT reversed")
    (is (= 1 (count (:rows revert))))))
