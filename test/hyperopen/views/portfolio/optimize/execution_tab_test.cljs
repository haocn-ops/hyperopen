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

(def ^:private current-run-signature
  ;; A run-state whose signature matches the retained run marks the solve as *current* (via
  ;; run-identity/completed-run?), so the staged plan is not flagged stale. This mirrors the
  ;; real app, where a staged execution plan always comes from an up-to-date solved run.
  {:scenario-id "scn_01" :input-signature "sig-current"})

(defn- scenario-view
  "Renders the optimizer scenario surface at the given results-tab + optimizer state. The run is
  current by default (matching run-state/last-run signature); override :draft with a dirty
  metadata map to exercise the stale path."
  [results-tab optimizer]
  (portfolio-view/portfolio-view
   {:router {:path "/portfolio/optimize/scn_01"}
    :portfolio-ui {:optimizer {:results-tab results-tab}}
    :portfolio {:optimizer
                (merge {:active-scenario {:loaded-id "scn_01" :status :computed}
                        :draft {:id "scn_01"}
                        :run-state {:status :succeeded
                                    :request-signature current-run-signature}
                        :last-successful-run {:result solved-result
                                              :request-signature current-run-signature}}
                       optimizer)}}))

(def ^:private staged-plan
  {:status :partially-blocked
   :execution-disabled? false
   :summary {:ready-count 1 :blocked-count 1 :skipped-count 0
             :gross-ready-notional-usd 1000
             :estimated-fees-usd 10 :estimated-slippage-usd 5
             :margin {:after-utilization 0.42 :after-gross-leverage 1.85 :before-gross-leverage 1.79 :free-margin-usd 8600 :capital-usd 10000 :warning :none}}
   :rows [{:row-id "perp:BTC" :instrument-id "perp:BTC" :instrument-type :perp
           :status :ready :side :buy :quantity 0.25 :order-type :market
           :delta-notional-usd 1000
           :cost {:source :snapshot :slippage-bps 5.0 :estimated-slippage-usd 5}}
          {:row-id "spot:PURR" :instrument-id "spot:PURR" :instrument-type :spot
           :status :blocked :side :sell :reason :spot-submit-unsupported
           :delta-notional-usd -500}]})

(deftest execution-tab-staged-buys-sells-exclude-blocked-rows-test
  ;; Buys/Sells (ported from the retired Rebalance preview) reflect only tradeable
  ;; (non-blocked) rows, so a blocked sell never inflates the Sells headline — keeping
  ;; the dollar-flow consistent with the ready-only staged notional.
  (let [plan {:status :partially-blocked :execution-disabled? false
              :summary {:ready-count 1 :blocked-count 1
                        :gross-ready-notional-usd 300
                        :margin {:after-utilization 0.42 :after-gross-leverage 1.85 :before-gross-leverage 1.79 :free-margin-usd 8600 :capital-usd 10000 :warning :none}}
              :rows [{:row-id "perp:BTC" :instrument-id "perp:BTC" :instrument-type :perp
                      :status :ready :side :buy :quantity 3 :delta-notional-usd 300
                      :cost {:source :snapshot :slippage-bps 0 :estimated-slippage-usd 0}}
                     {:row-id "spot:PURR" :instrument-id "spot:PURR" :instrument-type :spot
                      :status :blocked :side :sell :reason :spot-submit-unsupported
                      :delta-notional-usd -50000}]}
        view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :staged :plan plan}})
        buys (node-text (node-by-role view-node "portfolio-optimizer-execution-kpi-buys"))
        sells (node-text (node-by-role view-node "portfolio-optimizer-execution-kpi-sells"))]
    (is (str/includes? buys "300"))
    ;; Blocked sell notional is excluded -> Sells is zero, not 50,000.
    (is (not (str/includes? sells "50,000")))
    (is (str/includes? sells "$0"))))

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
    (is (contains? strings "Account leverage after"))
    (is (contains? strings "spot-submit-unsupported"))))

(deftest execution-tab-stale-recommendation-disables-arm-test
  ;; Inputs edited since the solve (dirty draft) => the staged plan is stale. The Arm button is
  ;; disabled (no click action) and a re-run notice is shown, so a stale recommendation can't be
  ;; armed into live orders from the surface.
  (let [view-node (scenario-view :execution
                                 {:draft {:id "scn_01" :metadata {:dirty? true}}
                                  :execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :staged :plan staged-plan}})
        arm (node-by-role view-node "portfolio-optimizer-execution-arm")
        stale-notice (node-by-role view-node "portfolio-optimizer-execution-stale")]
    (is (some? arm))
    (is (true? (get-in arm [1 :disabled])))
    (is (nil? (click-actions arm)))
    (is (some? stale-notice))
    (is (str/includes? (node-text stale-notice) "re-run"))))

(deftest execution-tab-orders-largest-first-exact-notional-test
  ;; The pre-commit table leads with the largest-notional order so the riskiest line is never
  ;; buried in a long list, and each row shows its exact dollar notional (not $Nk-rounded) so
  ;; the same order reads identically in the rebalance preview and here.
  (let [plan {:status :ready :execution-disabled? false
              :summary {:ready-count 2 :blocked-count 0
                        :gross-ready-notional-usd 2300
                        :margin {:after-utilization 0.3 :after-gross-leverage 1.85 :before-gross-leverage 1.79 :free-margin-usd 8600 :capital-usd 10000 :warning :none}}
              :rows [{:row-id "perp:SMALL" :instrument-id "perp:SMALL" :instrument-type :perp
                      :status :ready :side :buy :quantity 1 :order-type :market
                      :delta-notional-usd 300
                      :cost {:source :snapshot :slippage-bps 5 :estimated-slippage-usd 1}}
                     {:row-id "perp:BIG" :instrument-id "perp:BIG" :instrument-type :perp
                      :status :ready :side :sell :quantity 1 :order-type :market
                      :delta-notional-usd -2345
                      :cost {:source :snapshot :slippage-bps 5 :estimated-slippage-usd 1}}]}
        view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :staged :plan plan}})
        order-list (node-by-role view-node "portfolio-optimizer-execution-order-list")
        big-row (node-by-role view-node "portfolio-optimizer-execution-order-row-perp-BIG")
        strs (vec (collect-strings order-list))
        idx (fn [s] (first (keep-indexed (fn [i x] (when (= x s) i)) strs)))]
    ;; exact dollar notional, not "$2.3k"
    (is (str/includes? (node-text big-row) "$2,345"))
    (is (not (str/includes? (node-text big-row) "$2.3k")))
    ;; the bigger order (sorted first) appears before the smaller one in the table
    (is (< (idx "BIG") (idx "SMALL")))))

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

(deftest execution-tab-cost-kpis-react-to-order-type-test
  ;; A market (crossing) row contributes its price cost (spread + impact) + taker fee, which
  ;; rolls into the all-in KPI; overriding it to limit (resting/maker) drops the price cost to
  ;; $0 and switches to the maker fee — recomputed live, no re-staging.
  (let [plan (assoc-in staged-plan [:rows 0 :cost]
                       {:source :snapshot :slippage-bps 5.0
                        :estimated-slippage-usd 12.0
                        :estimated-fee-usd 6.0
                        :maker-fee-usd 2.0})
        view (fn [overrides]
               (scenario-view :execution
                              {:execution {:status :idle :history []}
                               :execution-modal (cond-> {:open? true :phase :staged :plan plan}
                                                  overrides (assoc :overrides overrides))}))
        kpi (fn [v role] (node-text (node-by-role v (str "portfolio-optimizer-execution-kpi-" role))))
        market (view nil)
        limit (view {"perp:BTC" :limit})]
    ;; market: price cost $12, taker fee $6, all-in $18
    (is (str/includes? (kpi market "price-cost") "12"))
    (is (str/includes? (kpi market "fees") "6"))
    (is (str/includes? (kpi market "all-in") "18"))
    ;; limit override: rests -> price cost $0, maker fee $2, all-in $2
    (is (str/includes? (kpi limit "price-cost") "$0"))
    (is (not (str/includes? (kpi limit "price-cost") "12")))
    (is (str/includes? (kpi limit "fees") "2"))
    (is (str/includes? (kpi limit "all-in") "2"))
    (is (not (str/includes? (kpi limit "all-in") "18")))))

(deftest execution-tab-row-expansion-shows-cost-breakdown-test
  ;; Expanding a market row reveals the execution-cost breakdown: spread crossing + book
  ;; impact = price cost, + fees = all-in. all-in = price cost $0.68 + fees $0.23 = $0.91.
  (let [plan (assoc-in staged-plan [:rows 0 :cost]
                       {:source :snapshot :slippage-bps 25 :estimated-slippage-usd 0.68
                        :spread-bps 18 :spread-usd 0.49 :impact-bps 7 :impact-usd 0.19
                        :fee-bps 4 :estimated-fee-usd 0.23 :maker-fee-bps 1.5 :maker-fee-usd 0.08})
        view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :staged :plan plan
                                                    :open-row "perp:BTC"}})
        breakdown (node-by-role view-node "portfolio-optimizer-execution-cost-breakdown")
        text (node-text breakdown)]
    (is (some? breakdown))
    (is (str/includes? text "Spread crossing"))
    (is (str/includes? text "Book impact"))
    (is (str/includes? text "Price cost"))
    (is (str/includes? text "All-in"))
    (is (str/includes? text "0.91"))))

(deftest execution-tab-row-expansion-unsplittable-cost-shows-not-separable-note-test
  ;; When the cost model can't separate spread from impact (e.g. :untrusted-snapshot-fill, a
  ;; stale/mismatched book), the row carries a flat slippage with NO :spread-usd. The breakdown
  ;; must say so honestly rather than render a misleading "Spread crossing 0 bp" — the spread
  ;; term collapses into a "not separable" note while price cost / all-in still reconcile.
  (let [plan (assoc-in staged-plan [:rows 0 :cost]
                       {:source :untrusted-snapshot-fill :slippage-bps 25
                        :estimated-slippage-usd 0.19
                        :fee-bps 4.5 :estimated-fee-usd 0.03
                        :maker-fee-bps 1.5 :maker-fee-usd 0.01})
        view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :staged :plan plan
                                                    :open-row "perp:BTC"}})
        breakdown (node-by-role view-node "portfolio-optimizer-execution-cost-breakdown")
        text (node-text breakdown)]
    (is (some? breakdown))
    ;; no fabricated spread-crossing stat (which previously read "0 bp")
    (is (not (str/includes? text "Spread crossing")))
    ;; an honest note explains the missing split
    (is (str/includes? text "separable"))
    ;; the price cost / all-in equation still renders
    (is (str/includes? text "Price cost"))
    (is (str/includes? text "All-in"))))

(deftest execution-tab-armed-renders-enabled-confirm-test
  (let [view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :armed :plan staged-plan}})
        confirm (node-by-role view-node "portfolio-optimizer-execution-confirm")]
    (is (some? confirm))
    (is (= false (boolean (get-in confirm [1 :disabled]))))
    (is (= [[:actions/confirm-portfolio-optimizer-execution]] (click-actions confirm)))))

(deftest execution-tab-armed-restates-money-figures-test
  ;; The commit moment must restate how much money moves (buys/sells/gross/leverage),
  ;; not just the order count, and the commit button must carry the reserved danger style.
  (let [plan (assoc staged-plan :summary {:ready-count 1 :blocked-count 1
                                          :gross-ready-notional-usd 1000
                                          :margin {:after-utilization 0.42 :after-gross-leverage 1.85 :before-gross-leverage 1.79 :free-margin-usd 8600 :capital-usd 10000 :warning :none}})
        view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :armed :plan plan}})
        figures (node-by-role view-node "portfolio-optimizer-execution-armed-figures")
        confirm (node-by-role view-node "portfolio-optimizer-execution-confirm")
        ftext (node-text figures)]
    (is (some? figures))
    (is (str/includes? ftext "buys"))
    (is (str/includes? ftext "sells"))
    (is (str/includes? ftext "gross"))
    (is (str/includes? ftext "leverage"))
    (is (str/includes? ftext "1.85x"))
    ;; the irreversible commit no longer reuses the amber primary look of safe actions
    (is (str/includes? (str/join " " (get-in confirm [1 :class])) "optimizer-exec-commit"))))

(deftest execution-tab-running-abort-acknowledges-test
  ;; Clicking Pause flips an :abort-requested? flag the submit loop reads; the running band
  ;; must acknowledge it immediately (text + a disabled control), not look inert.
  (let [view-node (scenario-view :execution
                                 {:execution {:status :submitting :history [] :abort-requested? true}
                                  :execution-modal {:open? true :phase :armed :submitting? true
                                                    :plan staged-plan}})
        band (node-by-role view-node "portfolio-optimizer-execution-control-band")
        pause (node-by-role view-node "portfolio-optimizer-execution-pause")]
    (is (= "running" (get-in band [1 :data-phase])))
    (is (str/includes? (node-text band) "Stopping"))
    (is (= true (boolean (get-in pause [1 :disabled]))))
    (is (nil? (get-in pause [1 :on])))))

(deftest execution-tab-discard-requires-confirmation-test
  ;; "Abort & discard" must not wipe the staged plan in one click: it sits behind a nested
  ;; confirm disclosure, and only the inner button fires the discard action.
  (let [view-node (scenario-view :execution
                                 {:execution {:status :idle :history []}
                                  :execution-modal {:open? true :phase :staged :plan staged-plan}})
        confirm-wrap (node-by-role view-node "portfolio-optimizer-execution-discard-confirm")
        discard (node-by-role view-node "portfolio-optimizer-execution-discard")]
    (is (some? confirm-wrap) "discard is wrapped in a confirm disclosure")
    (is (some? discard))
    (is (= [[:actions/discard-portfolio-optimizer-execution]] (click-actions discard)))))

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
                                                    :margin {:after-utilization 0.1 :after-gross-leverage 1.85 :before-gross-leverage 1.79 :free-margin-usd 8600 :capital-usd 10000 :warning :none}}
                                          :rows [{:row-id vault-id :instrument-id vault-id
                                                  :status :blocked :side :sell
                                                  :reason :vault-submit-unsupported
                                                  :delta-notional-usd -400}]}}})
        tab (node-by-role view-node "portfolio-optimizer-execution-tab")
        text (node-text tab)]
    (is (str/includes? text "Alpha Yield"))
    (is (not (str/includes? text vault-id)))
    (is (not (str/includes? text vault-address)))))

(deftest execution-tab-resting-renders-open-state-not-filled-test
  ;; A completed run of passive orders that only REST on the book must render each order's state
  ;; as "open" (never "filled"), drive the resting control band, and report 0 filled with the
  ;; resting count surfaced — the exact bug: a resting (open) order was mislabeled "filled".
  (let [view-node (scenario-view :execution
                                 {:execution {:status :resting
                                              :history [{:attempt-id "exec_2000"
                                                         :status :resting
                                                         :rows [{:instrument-id "perp:BTC"
                                                                 :status :resting
                                                                 :side :buy
                                                                 :delta-notional-usd 1000}]}]}
                                  :execution-modal {:open? true :phase :staged
                                                    :plan staged-plan}})
        strings (set (collect-strings view-node))
        band (node-by-role view-node "portfolio-optimizer-execution-control-band")
        orders-kpi (node-by-role view-node "portfolio-optimizer-execution-kpi-orders")]
    (is (= "resting" (get-in band [1 :data-phase]))
        "a resting run drives the resting control band, not done/halted")
    (is (contains? strings "open")
        "the resting order renders its state as \"open\"")
    (is (not (contains? strings "filled"))
        "no order-state cell claims \"filled\" when nothing filled")
    (is (str/includes? (node-text orders-kpi) "0 / 1")
        "Orders filled reports 0 of 1 — the resting order is not counted as filled")
    (is (str/includes? (node-text orders-kpi) "resting on book")
        "the resting order is surfaced in the orders KPI")))
