(ns hyperopen.portfolio.optimizer.application.view-model.execution-reconcile-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.view-model.execution :as vm]
            [hyperopen.portfolio.optimizer.application.view-model.execution-reconcile :as reconcile]))

;; A settled ledger row for a passive order that rested on the book under `oid`. The :response
;; shape mirrors the live Hyperliquid order response the effect adapter stores verbatim
;; ({:status "ok" :response {:data {:statuses [{:resting {:oid N}}]}}}).
(defn- resting-row
  [oid]
  {:row-id "perp:BTC"
   :instrument-id "perp:BTC"
   :coin "BTC"
   :status :resting
   :side :buy
   :price 50000
   :quantity 1
   :delta-notional-usd 50000
   :response {:status "ok"
              :response {:data {:statuses [{:resting {:oid oid}}]}}}})

(defn- orders-state
  [{:keys [open-orders fills hydrated?]}]
  {:orders {:open-orders (vec open-orders)
            :open-orders-hydrated? hydrated?
            :fills (vec fills)}})

(deftest resting-oid-still-on-book-stays-resting-test
  (let [state (orders-state {:open-orders [{:oid 111 :coin "BTC"}]
                             :fills []
                             :hydrated? true})
        [row] (reconcile/reconcile-rows state [(resting-row 111)])]
    (is (= :resting (:status row)))
    (is (nil? (:reconciled row)))))

(deftest resting-oid-gone-and-in-fills-promotes-to-filled-with-realized-test
  ;; The reported bug: a passive order that filled on the book (gone from open-orders, present in
  ;; fills) must read as filled, with realized price/size derived from the fills.
  (let [state (orders-state {:open-orders []
                             :fills [{:oid 111 :coin "BTC" :px "50050" :sz "1.0"}]
                             :hydrated? true})
        [row] (reconcile/reconcile-rows state [(resting-row 111)])]
    (is (= :submitted (:status row)))
    (is (= :filled (:reconciled row)))
    (is (= 50050 (get-in row [:realized :avg-px])))
    (is (some? (get-in row [:realized :slippage-bps])))))

(deftest resting-oid-still-on-book-with-fill-is-partial-test
  ;; A partially filled passive order keeps its remainder on the book, so it stays :resting but
  ;; carries a realized partial stamped from the fill.
  (let [state (orders-state {:open-orders [{:oid 111 :coin "BTC"}]
                             :fills [{:oid 111 :coin "BTC" :px "50050" :sz "0.4"}]
                             :hydrated? true})
        [row] (reconcile/reconcile-rows state [(resting-row 111)])]
    (is (= :resting (:status row)))
    (is (= :partial (:reconciled row)))
    (is (= 50050 (get-in row [:realized :avg-px])))))

(deftest no-fill-evidence-leaves-row-unchanged-test
  ;; Gone from a hydrated book but with NO fill: do NOT assert a fill (could be a cancel, or the
  ;; userFills race). Safe degradation = stay exactly as recorded.
  (let [state (orders-state {:open-orders []
                             :fills []
                             :hydrated? true})
        [row] (reconcile/reconcile-rows state [(resting-row 111)])]
    (is (= :resting (:status row)))
    (is (nil? (:reconciled row)))))

(deftest not-hydrated-without-fill-stays-resting-test
  ;; Before the open-orders snapshot hydrates (and with no fills), absence-from-book is "unknown",
  ;; never a false promotion.
  (let [state (orders-state {:open-orders []
                             :fills []
                             :hydrated? false})
        [row] (reconcile/reconcile-rows state [(resting-row 111)])]
    (is (= :resting (:status row)))))

(deftest not-hydrated-with-fill-promotes-to-filled-test
  ;; A fill is positive evidence the order executed, even before the open-orders book hydrates.
  (let [state (orders-state {:open-orders []
                             :fills [{:oid 111 :coin "BTC" :px "50050" :sz "1.0"}]
                             :hydrated? false})
        [row] (reconcile/reconcile-rows state [(resting-row 111)])]
    (is (= :submitted (:status row)))
    (is (= :filled (:reconciled row)))))

(deftest non-resting-row-is-untouched-test
  (let [state (orders-state {:open-orders []
                             :fills [{:oid 111 :coin "BTC" :px "50050" :sz "1.0"}]
                             :hydrated? true})
        submitted (assoc (resting-row 111) :status :submitted)
        [row] (reconcile/reconcile-rows state [submitted])]
    (is (= :submitted (:status row)))
    (is (nil? (:reconciled row)))))

(deftest multi-fill-volume-weighted-avg-test
  (let [state (orders-state {:open-orders []
                             :fills [{:oid 111 :coin "BTC" :px "50000" :sz "1.0"}
                                     {:oid 111 :coin "BTC" :px "50100" :sz "1.0"}]
                             :hydrated? true})
        [row] (reconcile/reconcile-rows state [(resting-row 111)])]
    (is (= :submitted (:status row)))
    (is (= 50050 (get-in row [:realized :avg-px])))))

;; ── integration: the execution-tab view-model flips the phase to :done ───────────────

(defn- execution-state
  [orders]
  (let [row (resting-row 111)]
    (merge orders
           {:portfolio
            {:optimizer
             {:execution {:status :resting
                          :history [{:rows [row] :status :resting}]}
              :execution-modal {:open? true
                                :phase :staged
                                :plan {:scenario-id "draft-1"
                                       :status :resting
                                       :summary {:ready-count 0}
                                       :rows [row]}}}}})))

(deftest execution-tab-model-reflects-fill-and-advances-phase-to-done-test
  ;; End to end through the view-model: a resting run whose only order has since filled shows the
  ;; row as filled and advances the run phase from :resting to :done (so the tab stops reading
  ;; "orders resting on the book").
  (let [resting-model (vm/execution-tab-model
                       (execution-state (orders-state {:open-orders [{:oid 111 :coin "BTC"}]
                                                       :fills []
                                                       :hydrated? true})))
        filled-model (vm/execution-tab-model
                      (execution-state (orders-state {:open-orders []
                                                      :fills [{:oid 111 :coin "BTC"
                                                               :px "50050" :sz "1.0"}]
                                                      :hydrated? true})))]
    (is (= :resting (:phase resting-model)))
    (is (= :resting (:status (first (:rows resting-model)))))
    (is (= :done (:phase filled-model)))
    (is (= :submitted (:status (first (:rows filled-model)))))
    (is (= :executed (:status (:latest-attempt filled-model))))))
