(ns hyperopen.views.portfolio.optimize.execution-tab-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]
            [hyperopen.views.portfolio-view :as portfolio-view]))

(defn- node-children
  [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn- find-first-node
  [node pred]
  (cond
    (vector? node)
    (let [children (node-children node)]
      (or (when (pred node) node)
          (some #(find-first-node % pred) children)))

    (seq? node)
    (some #(find-first-node % pred) node)

    :else nil))

(defn- collect-strings
  [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings (node-children node))
    (seq? node) (mapcat collect-strings node)
    :else []))

(defn- node-by-role
  [node role]
  (find-first-node node #(= role (get-in % [1 :data-role]))))

(defn- click-actions
  [node]
  (get-in node [1 :on :click]))

(defn- node-text
  [node]
  (apply str (collect-strings node)))

(def solved-result
  (fixtures/sample-solved-result
   {:instrument-ids ["perp:BTC"]
    :current-weights [0.1]
    :target-weights [0.2]
    :target-weights-by-instrument {"perp:BTC" 0.2}
    :current-weights-by-instrument {"perp:BTC" 0.1}
    :expected-return 0.12
    :volatility 0.24
    :diagnostics {:gross-exposure 0.2
                  :net-exposure 0.2
                  :effective-n 1
                  :turnover 0.1}
    :rebalance-preview
    {:status :ready
     :capital-usd 10000
     :summary {:ready-count 1
               :blocked-count 0
               :gross-trade-notional-usd 1000}
     :rows [{:instrument-id "perp:BTC"
             :instrument-type :perp
             :status :ready
             :side :buy
             :quantity 0.25
             :delta-notional-usd 1000}]}}))

(defn- scenario-view
  "Renders the optimizer scenario surface at the given results-tab + optimizer state."
  [results-tab optimizer]
  (portfolio-view/portfolio-view
   {:router {:path "/portfolio/optimize/scn_01"}
    :portfolio-ui {:optimizer {:results-tab results-tab}}
    :portfolio {:optimizer
                (merge {:active-scenario {:loaded-id "scn_01" :status :computed}
                        :draft {:id "scn_01"}
                        :last-successful-run {:result solved-result}}
                       optimizer)}}))

(def ^:private staged-plan
  {:status :partially-blocked
   :execution-disabled? false
   :summary {:ready-count 1 :blocked-count 1 :skipped-count 0
             :gross-ready-notional-usd 1000
             :estimated-fees-usd 10 :estimated-slippage-usd 5
             :margin {:after-utilization 0.42 :warning :none}}
   :rows [{:row-id "perp:BTC" :instrument-id "perp:BTC" :instrument-type :perp
           :status :ready :side :buy :quantity 0.25 :order-type :market
           :delta-notional-usd 1000
           :cost {:source :snapshot :slippage-bps 5.0 :estimated-slippage-usd 5}}
          {:row-id "spot:PURR" :instrument-id "spot:PURR" :instrument-type :spot
           :status :blocked :side :sell :reason :spot-submit-unsupported
           :delta-notional-usd -500}]})

(deftest rebalance-tab-stage-cta-opens-execution-test
  (let [view-node (scenario-view :rebalance {})
        cta (node-by-role view-node "portfolio-optimizer-stage-execution")]
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-tab-execution")))
    (is (some? cta))
    (is (= false (get-in cta [1 :disabled])))
    (is (= [[:actions/open-portfolio-optimizer-execution]] (click-actions cta)))))

(deftest execution-tab-staged-renders-plan-and-arm-action-test
  (let [view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :staged :plan staged-plan}})
        strings (set (collect-strings view-node))
        arm (node-by-role view-node "portfolio-optimizer-execution-arm")]
    (is (some? (node-by-role view-node "portfolio-optimizer-execution-tab")))
    (is (some? (node-by-role view-node "portfolio-optimizer-execution-order-row-perp-BTC")))
    (is (some? (node-by-role view-node "portfolio-optimizer-execution-order-row-spot-PURR")))
    (is (some? arm))
    (is (= [[:actions/set-portfolio-optimizer-execution-phase :armed]] (click-actions arm)))
    ;; cost-source + margin honesty signals + blocked reason are surfaced
    (is (some #(str/includes? % "snapshot") strings))
    (is (contains? strings "Margin after"))
    (is (contains? strings "spot-submit-unsupported"))))

(deftest execution-tab-slip-is-type-aware-test
  ;; A market row shows the book-crossing slippage estimate; a limit-overridden row
  ;; reads "rests" instead of the (misleading) market-impact number.
  (let [market-view (scenario-view :execution
                                   {:execution {:status :idle :history []}
                                    :execution-modal {:open? true :phase :staged :plan staged-plan}})
        market-row (node-by-role market-view "portfolio-optimizer-execution-order-row-perp-BTC")
        limit-view (scenario-view :execution
                                  {:execution {:status :idle :history []}
                                   :execution-modal {:open? true :phase :staged :plan staged-plan
                                                     :overrides {"perp:BTC" :limit}}})
        limit-row (node-by-role limit-view "portfolio-optimizer-execution-order-row-perp-BTC")]
    (is (str/includes? (node-text market-row) "bp"))
    (is (not (str/includes? (node-text market-row) "rests")))
    (is (str/includes? (node-text limit-row) "rests"))
    (is (not (str/includes? (node-text limit-row) "bp")))))

(deftest execution-tab-armed-renders-enabled-confirm-test
  (let [view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :armed :plan staged-plan}})
        confirm (node-by-role view-node "portfolio-optimizer-execution-confirm")]
    (is (some? confirm))
    (is (= false (boolean (get-in confirm [1 :disabled]))))
    (is (= [[:actions/confirm-portfolio-optimizer-execution]] (click-actions confirm)))))

(deftest execution-tab-running-hides-confirm-and-shows-progress-test
  (let [view-node (scenario-view :execution
                                 {:execution {:status :submitting :history []}
                                  :execution-modal {:open? true :phase :armed :submitting? true
                                                    :plan staged-plan}})
        band (node-by-role view-node "portfolio-optimizer-execution-control-band")]
    ;; while submitting the surface shows the running band, never a confirmable button
    (is (= "running" (get-in band [1 :data-phase])))
    (is (nil? (node-by-role view-node "portfolio-optimizer-execution-confirm")))))

(deftest execution-tab-halted-renders-failed-latest-attempt-test
  (let [view-node (scenario-view :execution
                                 {:execution {:status :partially-executed
                                              :history [{:attempt-id "exec_1000"
                                                         :status :partially-executed
                                                         :rows [{:instrument-id "perp:BTC"
                                                                 :status :failed
                                                                 :side :buy
                                                                 :delta-notional-usd 1000
                                                                 :error {:message "Order submit failed: exchange down"}}]}]}
                                  :execution-modal {:open? true :phase :staged
                                                    :error "Execution halted before all rows submitted."
                                                    :plan staged-plan}})
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-execution-latest-attempt")))
    (is (= "halted" (get-in (node-by-role view-node "portfolio-optimizer-execution-control-band")
                            [1 :data-phase])))
    (is (contains? strings "Latest attempt"))
    (is (contains? strings "Order submit failed: exchange down"))
    (is (some? (node-by-role view-node "portfolio-optimizer-execution-resume")))))

(deftest execution-tab-read-only-disables-arm-and-shows-message-test
  (let [message "Spectate Mode is read-only. Stop Spectate Mode to place trades or move funds."
        view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :staged
                                                    :plan (assoc staged-plan
                                                                 :execution-disabled? true
                                                                 :disabled-message message)}})
        strings (set (collect-strings view-node))
        arm (node-by-role view-node "portfolio-optimizer-execution-arm")]
    (is (some? (node-by-role view-node "portfolio-optimizer-execution-readonly")))
    (is (contains? strings message))
    (is (= true (boolean (get-in arm [1 :disabled]))))))

(deftest execution-tab-resolves-vault-labels-by-name-test
  (let [vault-address "0x6666666666666666666666666666666666666666"
        vault-id (str "vault:" vault-address)
        view-node (scenario-view :execution
                                 {:last-successful-run
                                  {:result (assoc solved-result
                                                  :labels-by-instrument {vault-id "Alpha Yield"})}
                                  :execution {:status :idle :history []}
                                  :execution-modal
                                  {:open? true :phase :staged
                                   :plan {:status :partially-blocked
                                          :summary {:ready-count 0 :blocked-count 1
                                                    :margin {:after-utilization 0.1 :warning :none}}
                                          :rows [{:row-id vault-id :instrument-id vault-id
                                                  :status :blocked :side :sell
                                                  :reason :vault-submit-unsupported
                                                  :delta-notional-usd -400}]}}})
        tab (node-by-role view-node "portfolio-optimizer-execution-tab")
        text (node-text tab)]
    (is (str/includes? text "Alpha Yield"))
    (is (not (str/includes? text vault-id)))
    (is (not (str/includes? text vault-address)))))
