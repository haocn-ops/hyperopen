(ns hyperopen.portfolio.optimizer.execution-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]
            [hyperopen.portfolio.optimizer.actions.execution :as exec-actions]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]
            [hyperopen.schema.contracts :as contracts]))

(defn- emitted-effects-valid?
  "Runs the emitted effects through the real action-emission contract — every emitted item
  must be a registered `effects/*` effect with conforming args. This guards against an action
  returning a raw `[:actions/...]` chain (which throws at runtime dispatch, not in a plain
  return-value assertion)."
  [action-id effects]
  (= effects (contracts/assert-emitted-effects!
              effects {:phase :action-emission :action-id action-id})))

(def ^:private current-run-signature
  ;; A run-state whose signature matches the retained run marks the solve as *current* (via
  ;; run-identity/completed-run?), so the staged plan is not flagged stale at the entry. Real
  ;; staging always comes from an up-to-date solved run; the minimal draft fixtures here have no
  ;; universe, so without this they would yield a nil readiness signature and read as stale.
  {:scenario-id "draft-1" :input-signature "sig-current"})

(defn- current-run-state
  []
  {:status :succeeded :request-signature current-run-signature})

(deftest open-execution-stages-plan-and-switches-to-execution-tab-test
  (let [state {:portfolio {:optimizer
                           {:draft {:id "draft-1"
                                    :execution-assumptions {:default-order-type :market}}
                            :run-state (current-run-state)
                            :last-successful-run
                            (fixtures/sample-last-successful-run
                             {:request-signature current-run-signature
                              :result {:rebalance-preview
                                       {:summary {:estimated-fees-usd nil
                                                  :estimated-slippage-usd nil}
                                        :rows [{:instrument-id "perp:BTC"
                                                :instrument-type :perp
                                                :status :ready
                                                :side :buy
                                                :quantity 0.25
                                                :delta-notional-usd 1000}]}}})}}}
        effects (actions/open-portfolio-optimizer-execution state)]
    (is (= [:effects/save [:portfolio :optimizer :execution-modal]
            {:open? true
             :submitting? false
             :error nil
             :phase :staged
             :default-order-type :recommended
             :overrides {}
             :params {}
             :open-row nil
             :order-filter :all
             :plan {:scenario-id "draft-1"
                    :status :ready
                    :execution-disabled? false
                    :disabled-reason nil
                    :disabled-message nil
                    :summary {:ready-count 1
                              :blocked-count 0
                              :skipped-count 0
                              :gross-ready-notional-usd 1000
                              :estimated-fees-usd nil
                              :estimated-slippage-usd nil
                              :margin nil}
                    :rows [{:row-id "perp:BTC"
                            :instrument-id "perp:BTC"
                            :instrument-type :perp
                            :status :ready
                            :side :buy
                            :quantity 0.25
                            :order-type :market
                            :delta-notional-usd 1000
                            :cost nil
                            :intent {:kind :perp-order
                                     :instrument-id "perp:BTC"
                                     :side :buy
                                     :quantity 0.25
                                     :order-type :market
                                     :reduce-only? false}}]}}]
           (first effects)))
    ;; run-state is reset and the surface switches to the Execution tab.
    (is (= [:effects/save [:portfolio :optimizer :execution]
            {:status :idle :attempt nil :history [] :error nil}]
           (nth effects 1)))
    (is (= [:effects/save [:portfolio-ui :optimizer :results-tab] :execution]
           (nth effects 2)))
    (is (= [:effects/replace-shareable-route-query] (nth effects 3)))))

(deftest open-execution-derives-preview-when-solved-run-lacks-preview-test
  (let [state {:asset-selector
               {:market-by-key
                {"perp:BTC" {:key "perp:BTC"
                             :market-type :perp
                             :coin "BTC"
                             :markPx "100"}
                 "perp:ETH" {:key "perp:ETH"
                             :market-type :perp
                             :coin "ETH"
                             :markPx "50"}}}
               :webdata2
               {:clearinghouseState
                {:marginSummary {:accountValue "10000"
                                 :totalMarginUsed "500"}
                 :assetPositions [{:position {:coin "BTC"
                                              :szi "25"
                                              :positionValue "2500"
                                              :markPx "100"
                                              :leverage {:value 5
                                                         :type "cross"}}}]}}
               :portfolio {:optimizer
                           {:active-scenario {:loaded-id nil
                                              :status :computed}
                            :draft {:id "draft-1"
                                    :universe [{:instrument-id "perp:BTC"
                                                :market-type :perp
                                                :coin "BTC"}
                                               {:instrument-id "perp:ETH"
                                                :market-type :perp
                                                :coin "ETH"}]
                                    :objective {:kind :minimum-variance}
                                    :return-model {:kind :historical-mean}
                                    :risk-model {:kind :diagonal-shrink}
                                    :constraints {:long-only? true
                                                  :max-asset-weight 0.75
                                                  :rebalance-tolerance 0.001}
                                    :execution-assumptions
                                    {:default-order-type :market
                                     :fallback-slippage-bps 20
                                     :prices-by-id {"perp:BTC" 100
                                                    "perp:ETH" 50}
                                     :fee-bps-by-id {"perp:BTC" 4
                                                    "perp:ETH" 5}}}
                            :history-data {:candle-history-by-coin
                                           {"BTC" [{:time-ms 1000 :close "100"}
                                                   {:time-ms 2000 :close "101"}
                                                   {:time-ms 3000 :close "102"}]
                                            "ETH" [{:time-ms 1000 :close "50"}
                                                   {:time-ms 2000 :close "51"}
                                                   {:time-ms 3000 :close "52"}]}
                                           :funding-history-by-coin
                                           {"BTC" [{:time-ms 1000
                                                    :funding-rate-raw 0}]
                                            "ETH" [{:time-ms 1000
                                                    :funding-rate-raw 0}]}}
                            :runtime {:as-of-ms 3000
                                      :stale-after-ms 60000}
                            :last-successful-run
                            (fixtures/sample-minimal-last-successful-run
                             {:result {:status :solved
                                       :scenario-id "draft-1"
                                       :instrument-ids ["perp:BTC" "perp:ETH"]
                                       :current-weights [0.25 0.0]
                                       :target-weights [0.5 0.5]
                                       :target-weights-by-instrument {"perp:BTC" 0.5
                                                                      "perp:ETH" 0.5}
                                       :current-weights-by-instrument {"perp:BTC" 0.25
                                                                       "perp:ETH" 0.0}
                                       :diagnostics {:turnover 0.75}}})}}}
        effects (actions/open-portfolio-optimizer-execution state)
        plan (get-in effects [0 2 :plan])]
    (is (= :effects/save (get-in effects [0 0])))
    (is (= [:portfolio :optimizer :execution-modal] (get-in effects [0 1])))
    (is (= true (get-in effects [0 2 :open?])))
    (is (= :ready (:status plan)))
    (is (= 2 (get-in plan [:summary :ready-count])))
    (is (= #{"perp:BTC" "perp:ETH"} (set (map :instrument-id (:rows plan)))))
    (is (= #{:perp-order} (set (map #(get-in % [:intent :kind]) (:rows plan)))))))

(deftest open-execution-without-solved-run-still-switches-tab-with-nil-plan-test
  (let [effects (actions/open-portfolio-optimizer-execution
                 {:portfolio {:optimizer {:last-successful-run
                                          (fixtures/sample-last-successful-run
                                           {:result {:status :infeasible}})}}})]
    (is (nil? (get-in effects [0 2 :plan])))
    (is (= [:effects/save [:portfolio-ui :optimizer :results-tab] :execution]
           (nth effects 2)))))

(deftest open-execution-stages-disabled-plan-when-recommendation-stale-test
  ;; The entry gates on currency, not just solved?: a stale recommendation (dirty draft) stages a
  ;; plan flagged execution-disabled :stale-recommendation. The rows are still staged (the trader
  ;; sees what WOULD trade), but the plan can't be armed/committed until the optimizer is re-run.
  (let [state {:portfolio {:optimizer
                           {:draft {:id "draft-1"
                                    :metadata {:dirty? true}
                                    :execution-assumptions {:default-order-type :market}}
                            :last-successful-run
                            (fixtures/sample-last-successful-run
                             {:result {:rebalance-preview
                                       {:summary {:estimated-fees-usd nil
                                                  :estimated-slippage-usd nil}
                                        :rows [{:instrument-id "perp:BTC"
                                                :instrument-type :perp
                                                :status :ready
                                                :side :buy
                                                :quantity 0.25
                                                :delta-notional-usd 1000}]}}})}}}
        plan (get-in (actions/open-portfolio-optimizer-execution state) [0 2 :plan])]
    (is (true? (:execution-disabled? plan)))
    (is (= :stale-recommendation (:disabled-reason plan)))
    (is (= exec-actions/stale-recommendation-message (:disabled-message plan)))
    ;; rows still present for context; the ready row is not silently dropped
    (is (= 1 (count (:rows plan))))
    (is (= "perp:BTC" (:instrument-id (first (:rows plan)))))))

(deftest execution-staging-mutators-update-interaction-state-test
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :phase] :armed]
          [:effects/save [:portfolio :optimizer :execution-modal :error] nil]]
         (actions/set-portfolio-optimizer-execution-phase {} :armed)))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :phase] :staged]
          [:effects/save [:portfolio :optimizer :execution-modal :error] nil]]
         (actions/set-portfolio-optimizer-execution-phase {} :staged)))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :default-order-type] :twap]]
         (actions/set-portfolio-optimizer-execution-default-order-type {} :twap)))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :overrides] {"perp:BTC" :limit}]]
         (actions/set-portfolio-optimizer-execution-row-order-type {} "perp:BTC" :limit)))
  ;; the :recommended sentinel clears an existing override
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :overrides] {}]]
         (actions/set-portfolio-optimizer-execution-row-order-type
          {:portfolio {:optimizer {:execution-modal {:overrides {"perp:BTC" :limit}}}}}
          "perp:BTC" :recommended)))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :open-row] "perp:BTC"]]
         (actions/toggle-portfolio-optimizer-execution-row {} "perp:BTC")))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :open-row] nil]]
         (actions/toggle-portfolio-optimizer-execution-row
          {:portfolio {:optimizer {:execution-modal {:open-row "perp:BTC"}}}}
          "perp:BTC")))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :params] {"perp:BTC" {:limit-bps -5}}]]
         (actions/set-portfolio-optimizer-execution-row-param {} "perp:BTC" :limit-bps -5)))
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :order-filter] :working]]
         (actions/set-portfolio-optimizer-execution-order-filter {} :working))))

(deftest confirm-execution-applies-order-type-selections-and-dispatches-test
  ;; Confirm must re-resolve each ready row's order type from the LIVE staging
  ;; selections (overrides + params) and stamp them onto the intent before dispatch,
  ;; so the submitted order matches what the user picked — not the staged :market.
  (let [row {:row-id "perp:BTC"
             :status :ready
             :side :buy
             :instrument-type :perp
             :delta-notional-usd 30000
             :intent {:kind :perp-order :side :buy :quantity 0.25 :order-type :market}}
        plan {:scenario-id "draft-1"
              :status :ready
              :execution-disabled? false
              :summary {:ready-count 1}
              :rows [row]}
        state {:wallet {:agent {:status :ready}}
               :portfolio {:optimizer {:execution-modal
                                       {:open? true
                                        :plan plan
                                        :default-order-type :recommended
                                        :overrides {"perp:BTC" :limit}
                                        :params {"perp:BTC" {:limit-bps -5}}}}}}
        effects (actions/confirm-portfolio-optimizer-execution state)
        [effect-key dispatched] (nth effects 2)
        intent (get-in dispatched [:rows 0 :intent])]
    (is (= [:effects/save [:portfolio :optimizer :execution-modal :submitting?] true]
           (first effects)))
    (is (= [:effects/save [:portfolio :optimizer :execution-modal :error] nil]
           (second effects)))
    (is (= :effects/execute-portfolio-optimizer-plan effect-key))
    (is (= :limit (:order-type intent)))
    (is (= -5 (:limit-bps intent)))
    (is (= :limit (get-in dispatched [:rows 0 :order-type])))))

(deftest confirm-execution-recommended-default-routes-by-clip-size-test
  ;; With the default :recommended and no override, a medium perp clip (22k–70k)
  ;; resolves to :passive (the recommend-exec-type policy).
  (let [row {:row-id "perp:BTC"
             :status :ready
             :side :buy
             :instrument-type :perp
             :delta-notional-usd 30000
             :intent {:kind :perp-order :side :buy :quantity 0.25 :order-type :market}}
        state {:wallet {:agent {:status :ready}}
               :portfolio {:optimizer {:execution-modal
                                       {:open? true
                                        :plan {:scenario-id "draft-1"
                                               :status :ready
                                               :execution-disabled? false
                                               :summary {:ready-count 1}
                                               :rows [row]}
                                        :default-order-type :recommended
                                        :overrides {}
                                        :params {}}}}}
        [_ dispatched] (nth (actions/confirm-portfolio-optimizer-execution state) 2)]
    (is (= :passive (get-in dispatched [:rows 0 :intent :order-type])))))

(defn- locked-state
  [agent-status]
  (let [row {:row-id "perp:BTC"
             :status :ready
             :side :buy
             :instrument-type :perp
             :delta-notional-usd 30000
             :intent {:kind :perp-order :side :buy :quantity 0.25 :order-type :market}}]
    {:wallet {:agent {:status agent-status}}
     :portfolio {:optimizer {:execution-modal
                             {:open? true
                              :plan {:scenario-id "draft-1"
                                     :status :ready
                                     :execution-disabled? false
                                     :summary {:ready-count 1}
                                     :rows [row]}
                              :default-order-type :market
                              :overrides {}
                              :params {}}}}}))

(defn- stale-execution-state
  "A solved run whose draft was edited since the solve (dirty), so the staged plan is stale.
  agent :ready proves the block is staleness, not trading-readiness."
  []
  (-> (locked-state :ready)
      (assoc-in [:portfolio :optimizer :draft] {:id "draft-1" :metadata {:dirty? true}})
      (assoc-in [:portfolio :optimizer :last-successful-run]
                (fixtures/sample-last-successful-run {}))))

(defn- current-execution-state
  "A just-completed solved run: clean draft + a run-state whose signature matches the retained
  run, so `stale-run?` is false and the gate must let it through."
  []
  (let [signature {:scenario-id "draft-1" :input-signature "sig-current"}]
    (-> (locked-state :ready)
        (assoc-in [:portfolio :optimizer :draft] {:id "draft-1" :metadata {:dirty? false}})
        (assoc-in [:portfolio :optimizer :run-state]
                  {:status :succeeded :request-signature signature})
        (assoc-in [:portfolio :optimizer :last-successful-run]
                  (fixtures/sample-last-successful-run {:request-signature signature})))))

(deftest confirm-execution-blocks-stale-recommendation-test
  ;; Inputs edited since the solve => the staged plan is stale. Confirm must refuse to send
  ;; (no execute, no agent-unlock) even with a ready agent, and emit the re-run prompt.
  (let [effects (actions/confirm-portfolio-optimizer-execution (stale-execution-state))]
    (is (emitted-effects-valid? :actions/confirm-portfolio-optimizer-execution effects))
    (is (= [[:effects/save [:portfolio :optimizer :execution-modal :error]
             exec-actions/stale-recommendation-message]]
           effects))
    (is (not (some #{:effects/execute-portfolio-optimizer-plan} (map first effects))))))

(deftest arm-execution-blocked-when-stale-test
  ;; Arming a stale plan is refused: the surface stays :staged with the re-run prompt, so the
  ;; live-commit button is never reachable from a stale recommendation.
  (is (= [[:effects/save [:portfolio :optimizer :execution-modal :phase] :staged]
          [:effects/save [:portfolio :optimizer :execution-modal :error]
           exec-actions/stale-recommendation-message]]
         (actions/set-portfolio-optimizer-execution-phase (stale-execution-state) :armed))))

(deftest confirm-execution-current-solved-run-still-submits-test
  ;; A just-completed solved run is NOT stale, so a ready agent must still submit — guards the
  ;; gate against blocking a valid, current recommendation.
  (let [effects (actions/confirm-portfolio-optimizer-execution (current-execution-state))]
    (is (some #{:effects/execute-portfolio-optimizer-plan} (map first effects)))))

(deftest confirm-execution-locked-agent-prompts-unlock-and-replays-confirm-test
  ;; A locked agent must behave like normal order entry: prompt the passkey unlock and
  ;; replay the confirm on success, instead of dispatching the submit (which would dead-end
  ;; with a per-row "Trading is locked" rejection). An action can only emit effects/*, so it
  ;; runs the :effects/unlock-agent-trading EFFECT directly (not an :actions/* chain, which
  ;; would throw the effect-id schema at runtime dispatch).
  (let [effects (actions/confirm-portfolio-optimizer-execution (locked-state :locked))
        effect-keys (map first effects)]
    ;; the emitted effects must pass the real action-emission contract (regression guard:
    ;; an :actions/* emission throws here)
    (is (emitted-effects-valid? :actions/confirm-portfolio-optimizer-execution effects))
    (is (some #{:effects/unlock-agent-trading} effect-keys))
    ;; never dispatches the submit while locked
    (is (not (some #{:effects/execute-portfolio-optimizer-plan} effect-keys)))
    ;; flips the agent to :unlocking so a second confirm-click can't double-prompt
    (is (some #{[:effects/save-many [[[:wallet :agent :status] :unlocking]
                                     [[:wallet :agent :error] nil]]]}
              effects))
    (let [unlock (first (filter #(= :effects/unlock-agent-trading (first %)) effects))]
      (is (= {:after-success-actions [[:actions/confirm-portfolio-optimizer-execution]]}
             (second unlock))))))

(deftest confirm-execution-unlocking-agent-waits-without-submitting-test
  (let [effects (actions/confirm-portfolio-optimizer-execution (locked-state :unlocking))
        effect-keys (map first effects)]
    (is (emitted-effects-valid? :actions/confirm-portfolio-optimizer-execution effects))
    (is (not (some #{:effects/execute-portfolio-optimizer-plan} effect-keys)))
    (is (some #{:effects/save} effect-keys))))

(deftest confirm-execution-not-ready-agent-opens-enable-recovery-test
  ;; Trading never enabled (the DEFAULT :not-ready status) must open the enable-trading
  ;; recovery modal — like manual order entry — instead of submitting orders that would each
  ;; dead-end on "Enable trading first".
  (let [effects (actions/confirm-portfolio-optimizer-execution (locked-state :not-ready))
        effect-keys (map first effects)]
    (is (emitted-effects-valid? :actions/confirm-portfolio-optimizer-execution effects))
    (is (not (some #{:effects/execute-portfolio-optimizer-plan} effect-keys)))
    (is (some #{[:effects/save [:wallet :agent :recovery-modal-open?] true]} effects))))

(deftest confirm-execution-ready-agent-still-submits-test
  ;; Only a :ready agent submits.
  (let [effects (actions/confirm-portfolio-optimizer-execution (locked-state :ready))]
    (is (emitted-effects-valid? :actions/confirm-portfolio-optimizer-execution effects))
    (is (some #{:effects/execute-portfolio-optimizer-plan} (map first effects)))))

(deftest confirm-execution-blocks-read-only-plan-test
  (let [state {:portfolio {:optimizer {:execution-modal
                                       {:open? true
                                        :plan {:scenario-id "draft-1"
                                               :status :ready
                                               :execution-disabled? true
                                               :disabled-message "Spectate Mode is read-only."}}}}}]
    (is (= [[:effects/save
             [:portfolio :optimizer :execution-modal :error]
             "Spectate Mode is read-only."]]
           (actions/confirm-portfolio-optimizer-execution state)))))

(deftest resume-execution-retries-only-failed-rows-test
  ;; A partial run filled BTC and rejected ETH. Resume must re-arm ONLY ETH and demote
  ;; the already-filled BTC to :skipped :already-filled so it can never double-submit.
  (let [plan {:scenario-id "draft-1"
              :status :partially-blocked
              :execution-disabled? false
              :summary {:ready-count 2 :blocked-count 0 :skipped-count 0
                        :gross-ready-notional-usd 3000
                        :estimated-fees-usd 1 :estimated-slippage-usd 2 :margin nil}
              :rows [{:row-id "perp:BTC" :status :ready :delta-notional-usd 1000}
                     {:row-id "perp:ETH" :status :ready :delta-notional-usd 2000}]}
        ledger {:rows [{:row-id "perp:BTC" :instrument-id "perp:BTC" :status :submitted
                        :delta-notional-usd 1000 :intent {:kind :perp-order}
                        :response {:status "ok"}}
                       {:row-id "perp:ETH" :instrument-id "perp:ETH" :status :failed
                        :delta-notional-usd 2000 :intent {:kind :perp-order}
                        :error {:message "rejected"}}]}
        state {:portfolio {:optimizer {:execution-modal {:open? true :plan plan}
                                       :execution {:status :partially-executed
                                                   :history [ledger]}}}}
        effects (actions/resume-portfolio-optimizer-execution state)
        [effect-key resume-plan] (nth effects 2)
        row-by-id (into {} (map (juxt :row-id identity) (:rows resume-plan)))]
    (is (= [:effects/save [:portfolio :optimizer :execution-modal :submitting?] true]
           (first effects)))
    (is (= [:effects/save [:portfolio :optimizer :execution-modal :error] nil]
           (second effects)))
    (is (= :effects/execute-portfolio-optimizer-plan effect-key))
    ;; filled row demoted, stale response dropped, intent removed so it can't re-request
    (is (= {:status :skipped :reason :already-filled}
           (select-keys (row-by-id "perp:BTC") [:status :reason])))
    (is (nil? (:intent (row-by-id "perp:BTC"))))
    ;; failed row re-armed, stale error dropped
    (is (= :ready (:status (row-by-id "perp:ETH"))))
    (is (nil? (:error (row-by-id "perp:ETH"))))
    (is (= {:kind :perp-order} (:intent (row-by-id "perp:ETH"))))
    (is (= 1 (get-in resume-plan [:summary :ready-count])))
    (is (= 1 (get-in resume-plan [:summary :skipped-count])))
    (is (= 2000 (get-in resume-plan [:summary :gross-ready-notional-usd])))))

(deftest resume-execution-noops-when-nothing-recoverable-test
  (let [ledger {:rows [{:row-id "perp:BTC" :status :submitted :delta-notional-usd 1000}]}
        state {:portfolio {:optimizer {:execution-modal {:plan {:scenario-id "draft-1"
                                                                :execution-disabled? false
                                                                :summary {}
                                                                :rows []}}
                                       :execution {:history [ledger]}}}}]
    (is (= [[:effects/save
             [:portfolio :optimizer :execution-modal :error]
             "No orders are eligible to resume."]]
           (actions/resume-portfolio-optimizer-execution state)))))

(deftest revert-execution-builds-reversing-orders-from-filled-ledger-test
  ;; Only the filled (:submitted) BTC is reverted — opposite side, reduce-only, market.
  (let [plan {:scenario-id "draft-1" :execution-disabled? false :summary {} :rows []}
        ledger {:rows [{:row-id "perp:BTC" :instrument-id "perp:BTC" :instrument-type :perp
                        :status :submitted :side :buy :quantity 0.25 :price 100
                        :delta-notional-usd 1000}
                       {:row-id "perp:ETH" :instrument-id "perp:ETH" :instrument-type :perp
                        :status :failed :side :buy :quantity 0.5 :delta-notional-usd 2000}]}
        state {:portfolio {:optimizer {:execution-modal {:plan plan}
                                       :execution {:history [ledger]}}}}
        effects (actions/revert-portfolio-optimizer-execution-filled state)
        [effect-key revert-plan] (nth effects 2)
        row (first (:rows revert-plan))]
    (is (= [:effects/save [:portfolio :optimizer :execution-modal :submitting?] true]
           (first effects)))
    (is (= :effects/execute-portfolio-optimizer-plan effect-key))
    (is (= :revert (:kind revert-plan)))
    (is (= 1 (count (:rows revert-plan))))
    (is (= "perp:BTC" (:row-id row)))
    (is (= :sell (:side row)))
    (is (= :ready (:status row)))
    (is (= true (get-in row [:intent :reduce-only?])))
    (is (= :market (get-in row [:intent :order-type])))))

(deftest revert-execution-noops-without-filled-rows-test
  (let [ledger {:rows [{:row-id "perp:ETH" :status :failed :delta-notional-usd 2000}]}
        state {:portfolio {:optimizer {:execution-modal {:plan {:execution-disabled? false
                                                                :summary {}
                                                                :rows []}}
                                       :execution {:history [ledger]}}}}]
    (is (= [[:effects/save
             [:portfolio :optimizer :execution-modal :error]
             "No filled orders to revert."]]
           (actions/revert-portfolio-optimizer-execution-filled state)))))

(deftest restage-execution-halves-unfilled-rows-and-returns-to-staged-test
  (let [plan {:scenario-id "draft-1" :execution-disabled? false
              :summary {:ready-count 2}
              :rows [{:row-id "perp:BTC" :status :ready :quantity 0.25 :delta-notional-usd 1000
                      :intent {:quantity 0.25}}
                     {:row-id "perp:ETH" :status :ready :quantity 1.0 :delta-notional-usd 2000
                      :intent {:quantity 1.0}}]}
        ledger {:rows [{:row-id "perp:BTC" :status :submitted}
                       {:row-id "perp:ETH" :status :failed}]}
        state {:portfolio {:optimizer {:execution-modal {:plan plan}
                                       :execution {:history [ledger]}}}}
        effects (actions/restage-portfolio-optimizer-execution-smaller state)
        [_ plan-path restaged] (first effects)
        eth (first (:rows restaged))]
    (is (= [:portfolio :optimizer :execution-modal :plan] plan-path))
    ;; the filled BTC is dropped; only ETH remains, halved
    (is (= 1 (count (:rows restaged))))
    (is (= "perp:ETH" (:row-id eth)))
    (is (= 0.5 (:quantity eth)))
    (is (= 0.5 (get-in eth [:intent :quantity])))
    (is (= 1000 (:delta-notional-usd eth)))
    (is (= 1 (get-in restaged [:summary :ready-count])))
    ;; surface returns to staged for a fresh arm
    (is (= [:effects/save [:portfolio :optimizer :execution-modal :phase] :staged]
           (second effects)))))

(deftest pause-execution-sets-abort-flag-test
  (is (= [[:effects/save [:portfolio :optimizer :execution :abort-requested?] true]]
         (actions/pause-portfolio-optimizer-execution {}))))

(deftest discard-execution-clears-plan-and-returns-to-recommendation-test
  (let [state {:portfolio {:optimizer {:execution-modal {:open? true :submitting? false
                                                         :plan {:rows []}}}}}
        effects (actions/discard-portfolio-optimizer-execution state)]
    (is (= [:portfolio :optimizer :execution-modal] (get-in effects [0 1])))
    (is (= [:portfolio :optimizer :execution] (get-in effects [1 1])))
    ;; The standalone Rebalance preview tab was retired, so discarding returns to the
    ;; Recommendation tab rather than a dead :rebalance value.
    (is (= [:effects/save [:portfolio-ui :optimizer :results-tab] :recommendation]
           (nth effects 2)))
    (is (= [:effects/replace-shareable-route-query] (nth effects 3)))))

(deftest discard-execution-noops-while-submitting-test
  (is (= [] (actions/discard-portfolio-optimizer-execution
             {:portfolio {:optimizer {:execution-modal {:submitting? true}}}}))))

(deftest open-in-ticket-navigates-to-first-ready-market-test
  (let [state {:portfolio {:optimizer {:execution-modal
                                       {:plan {:rows [{:row-id "perp:ETH" :status :blocked :coin "ETH"}
                                                      {:row-id "perp:BTC" :status :ready :coin "BTC"}]}}}}}]
    (is (= [[:actions/navigate "/trade?market=BTC"]]
           (actions/open-portfolio-optimizer-execution-in-ticket state))))
  (is (= [] (actions/open-portfolio-optimizer-execution-in-ticket
             {:portfolio {:optimizer {:execution-modal {:plan {:rows []}}}}}))))
