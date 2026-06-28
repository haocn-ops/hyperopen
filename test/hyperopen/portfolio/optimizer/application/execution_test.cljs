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
