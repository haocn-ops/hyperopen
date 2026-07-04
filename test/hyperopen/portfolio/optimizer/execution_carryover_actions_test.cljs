(ns hyperopen.portfolio.optimizer.execution-carryover-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]))

(def ^:private ready-plan
  {:scenario-id "draft-1"
   :status :ready
   :execution-disabled? false
   :summary {:ready-count 1}
   :rows [{:row-id "perp:BTC"
           :status :ready
           :side :buy
           :instrument-type :perp
           :delta-notional-usd 25
           :intent {:kind :perp-order :side :buy :quantity 0.25
                    :order-type :market}}]})

(deftest confirm-execution-attaches-live-resting-carryover-as-cancel-orders-test
  ;; Re-optimizing while a previous run's passive orders still rest on the book must
  ;; NOT submit new orders on top of them (the stale orders would fill later and
  ;; over-allocate). Confirm reads the carryover fresh from state, drops entries no
  ;; longer on the hydrated book, and hands the survivors to the execute effect as
  ;; :cancel-orders — cancelled before any new order is released.
  (let [state {:wallet {:agent {:status :ready}}
               :orders {:open-orders-hydrated? true
                        :open-orders [{:oid 111}]}
               :portfolio {:optimizer
                           {:execution-resting-carryover
                            [{:oid 111 :asset-id 7 :coin "ZETA"}
                             ;; 222 already left the book (filled/cancelled elsewhere):
                             ;; it must NOT be re-cancelled.
                             {:oid 222 :asset-id 8 :coin "ZEN"}]
                            :execution-modal {:open? true :plan ready-plan}}}}
        effects (actions/confirm-portfolio-optimizer-execution state)
        [effect-key dispatched] (nth effects 2)]
    (is (= :effects/execute-portfolio-optimizer-plan effect-key))
    (is (= [111] (mapv :oid (:cancel-orders dispatched)))
        "only the still-live oid rides along for pre-run cancellation")))

(deftest confirm-execution-without-carryover-omits-cancel-orders-test
  (let [state {:wallet {:agent {:status :ready}}
               :portfolio {:optimizer {:execution-modal {:open? true
                                                         :plan ready-plan}}}}
        [_ dispatched] (nth (actions/confirm-portfolio-optimizer-execution state) 2)]
    (is (not (contains? dispatched :cancel-orders)))))
