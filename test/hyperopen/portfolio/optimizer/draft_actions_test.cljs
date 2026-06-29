(ns hyperopen.portfolio.optimizer.draft-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]))

(deftest objective-menu-actions-select-apply-and-rerun-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :max-sharpe}
                                               :return-model {:kind :historical-mean}
                                               :metadata {:dirty? false}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :minimum-volatility}}}]
    (is (= [[:effects/save-many
             [[[:portfolio-ui :optimizer :objective-menu-open?] true]
              [[:portfolio-ui :optimizer :objective-menu-selection] :max-sharpe]
              [[:portfolio-ui :optimizer :objective-menu-target-sigma] nil]]]]
           (actions/open-portfolio-optimizer-objective-menu state)))
    (is (= [[:effects/save
             [:portfolio-ui :optimizer :objective-menu-selection]
             :target-volatility]]
           (actions/select-portfolio-optimizer-objective-menu-option
            state
            "targetVolatility")))
    (is (= [[:effects/save-many
             [[[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio-ui :optimizer :objective-menu-target-sigma] nil]]]]
           (actions/close-portfolio-optimizer-objective-menu state)))
    (is (= [[:effects/save-many
             [[[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio-ui :optimizer :objective-menu-target-sigma] nil]]]]
           (actions/handle-portfolio-optimizer-objective-menu-keydown
            state
            "Escape")))
    (is (= []
           (actions/handle-portfolio-optimizer-objective-menu-keydown
            state
            "Enter")))
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective]
               {:kind :minimum-variance}]
              [[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio-ui :optimizer :objective-menu-target-sigma] nil]
              [[:portfolio-ui :optimizer :target-sigma-draft] nil]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/apply-portfolio-optimizer-objective-menu-selection-and-run
            state)))))

(deftest objective-menu-apply-preserves-current-objective-parameter-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :target-volatility
                                                           :target-volatility 0.22}
                                               :return-model {:kind :historical-mean}
                                               :metadata {:dirty? false}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :target-volatility}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective]
               {:kind :target-volatility
                :target-volatility 0.22}]
              [[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio-ui :optimizer :objective-menu-target-sigma] nil]
              [[:portfolio-ui :optimizer :target-sigma-draft] nil]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/apply-portfolio-optimizer-objective-menu-selection-and-run
            state))
        "re-applying target-volatility keeps the user-chosen sigma instead of the 12% preset")))

(deftest objective-menu-apply-use-my-views-updates-return-model-and-reruns-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :minimum-variance}
                                               :return-model {:kind :black-litterman
                                                              :views [{:kind :absolute
                                                                       :instrument-id "perp:BTC"
                                                                       :return 0.2
                                                                       :confidence 0.75
                                                                       :weights {"perp:BTC" 1}}]}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :use-my-views}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective]
               {:kind :max-sharpe}]
              [[:portfolio :optimizer :draft :return-model]
               {:kind :black-litterman
                :views [{:id "bl_view_1"
                         :kind :absolute
                         :instrument-id "perp:BTC"
                         :return 0.2
                         :confidence-level :high
                         :confidence 0.75
                         :confidence-variance 0.25
                         :horizon :3m
                         :weights {"perp:BTC" 1}}]}]
              [[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio-ui :optimizer :objective-menu-target-sigma] nil]
              [[:portfolio-ui :optimizer :target-sigma-draft] nil]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/apply-portfolio-optimizer-objective-menu-selection-and-run
            state)))))

(deftest objective-menu-apply-leaving-use-my-views-restores-baseline-return-model-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :max-sharpe}
                                               :return-model {:kind :black-litterman
                                                              :views [{:kind :absolute
                                                                       :instrument-id "perp:BTC"
                                                                       :return 0.2
                                                                       :confidence 0.75
                                                                       :weights {"perp:BTC" 1}}]}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :minimum-volatility}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective]
               {:kind :minimum-variance}]
              [[:portfolio :optimizer :draft :return-model]
               {:kind :historical-mean}]
              [[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio-ui :optimizer :objective-menu-target-sigma] nil]
              [[:portfolio-ui :optimizer :target-sigma-draft] nil]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/apply-portfolio-optimizer-objective-menu-selection-and-run
            state)))))

(deftest objective-menu-use-my-views-inline-actions-and-apply-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}
                                                           {:instrument-id "perp:ETH"}
                                                           {:instrument-id "perp:HYPE"}]
                                               :objective {:kind :minimum-variance}
                                               :return-model {:kind :historical-mean}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :use-my-views
                                           :objective-menu-view-drafts
                                           {:perp:BTC {:return-text "18"
                                                       :confidence :medium}
                                            :perp:ETH {:return-text "16.5"
                                                       :confidence :low}
                                            :perp:HYPE {:return-text "45"
                                                        :confidence :high}}}}}]
    (is (= [[:effects/save
             [:portfolio-ui :optimizer :objective-menu-view-drafts
              :perp:BTC
              :return-text]
             "19.25"]]
           (actions/set-portfolio-optimizer-objective-menu-view-return
            state
            "perp:BTC"
            "19.25")))
    (is (= [[:effects/save
             [:portfolio-ui :optimizer :objective-menu-view-drafts
              :perp:BTC
              :confidence]
             :high]]
           (actions/set-portfolio-optimizer-objective-menu-view-confidence
            state
            "perp:BTC"
            "high")))
    (is (= [[:effects/save
             [:portfolio-ui :optimizer :objective-menu-view-drafts
              :perp:BTC
              :return-text]
             "18.5"]]
           (actions/step-portfolio-optimizer-objective-menu-view-return
            state
            "perp:BTC"
            :up)))
    (is (= [[:effects/save
             [:portfolio-ui :optimizer :objective-menu-view-drafts
              :perp:BTC
              :return-text]
             "17.5"]]
           (actions/step-portfolio-optimizer-objective-menu-view-return
            state
            "perp:BTC"
            "ArrowDown")))
    (is (= []
           (actions/add-portfolio-optimizer-objective-menu-view
            (assoc-in state
                      [:portfolio-ui :optimizer :objective-menu-view-order]
                      ["perp:BTC" "perp:ETH"]))))
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective]
               {:kind :max-sharpe}]
              [[:portfolio :optimizer :draft :return-model]
               {:kind :black-litterman
                :views [{:id "bl_view_1"
                         :kind :absolute
                         :instrument-id "perp:BTC"
                         :return 0.18
                         :confidence-level :medium
                         :confidence 0.5
                         :confidence-variance 0.5
                         :horizon :3m
                         :weights {"perp:BTC" 1}}
                        {:id "bl_view_2"
                         :kind :absolute
                         :instrument-id "perp:ETH"
                         :return 0.165
                         :confidence-level :low
                         :confidence 0.25
                         :confidence-variance 0.75
                         :horizon :3m
                         :weights {"perp:ETH" 1}}
                        {:id "bl_view_3"
                         :kind :absolute
                         :instrument-id "perp:HYPE"
                         :return 0.45
                         :confidence-level :high
                         :confidence 0.75
                         :confidence-variance 0.25
                         :horizon :3m
                         :weights {"perp:HYPE" 1}}]}]
              [[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio-ui :optimizer :objective-menu-target-sigma] nil]
              [[:portfolio-ui :optimizer :target-sigma-draft] nil]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/apply-portfolio-optimizer-objective-menu-selection-and-run
            state)))))

(deftest objective-menu-use-my-views-prefills-from-baseline-return-data-on-apply-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :minimum-variance}
                                               :return-model {:kind :historical-mean}
                                               :risk-model {:kind :sample-covariance}}
                                      :last-successful-run
                                      {:result {:expected-returns-by-instrument
                                                {"perp:BTC" 0.2}}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :use-my-views}}}]
    (let [effects (actions/apply-portfolio-optimizer-objective-menu-selection-and-run state)
          saved-values (second (first effects))
          return-model (second (second saved-values))]
      (is (= {:kind :black-litterman
              :views [{:id "bl_view_1"
                       :kind :absolute
                       :instrument-id "perp:BTC"
                       :return 0.2
                       :confidence-level :medium
                       :confidence 0.5
                       :confidence-variance 0.5
                       :horizon :3m
                       :weights {"perp:BTC" 1}}]}
             return-model)))))

(deftest objective-menu-use-my-views-preserves-relative-views-and-removes-edited-absolute-rows-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}
                                                           {:instrument-id "perp:ETH"}]
                                               :objective {:kind :max-sharpe}
                                               :return-model
                                               {:kind :black-litterman
                                                :views [{:id "abs-btc"
                                                         :kind :absolute
                                                         :instrument-id "perp:BTC"
                                                         :return 0.1
                                                         :confidence 0.25
                                                         :weights {"perp:BTC" 1}}
                                                        {:id "rel-eth-btc"
                                                         :kind :relative
                                                         :instrument-id "perp:ETH"
                                                         :comparator-instrument-id "perp:BTC"
                                                         :return 0.04
                                                         :confidence 0.5
                                                         :weights {"perp:ETH" 1
                                                                   "perp:BTC" -1}}]}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :use-my-views
                                           :objective-menu-view-order ["perp:ETH"]
                                           :objective-menu-view-drafts
                                           {:perp:ETH {:return-text "12"
                                                       :confidence :high}}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio-ui :optimizer :objective-menu-view-order] ["perp:BTC"]]
              [[:portfolio-ui :optimizer :objective-menu-view-drafts] {}]]]]
           (actions/remove-portfolio-optimizer-objective-menu-view
            state
            "perp:ETH")))
    (let [effects (actions/apply-portfolio-optimizer-objective-menu-selection-and-run state)
          saved-values (second (first effects))
          return-model (second (second saved-values))]
      (is (= {:kind :black-litterman
              :views [{:id "rel-eth-btc"
                      :kind :relative
                      :instrument-id "perp:ETH"
                      :comparator-instrument-id "perp:BTC"
                       :return 0.04
                       :confidence 0.5
                       :weights {"perp:ETH" 1
                                 "perp:BTC" -1}}
                      {:id "abs-btc"
                       :kind :absolute
                       :instrument-id "perp:BTC"
                       :return 0.1
                       :confidence-level :low
                       :confidence 0.25
                       :confidence-variance 0.75
                       :horizon :3m
                       :weights {"perp:BTC" 1}}
                      {:id "bl_view_3"
                       :kind :absolute
                       :instrument-id "perp:ETH"
                       :return 0.12
                       :confidence-level :high
                       :confidence 0.75
                       :confidence-variance 0.25
                       :horizon :3m
                       :weights {"perp:ETH" 1}}]}
             return-model)))))

(deftest set-draft-constraint-normalizes-supported-values-test
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :max-asset-weight]
                                0.42]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-constraint
          {}
          :max-asset-weight
          "0.42")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :long-only?]
                                true]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-constraint
          {}
          :long-only?
          true)))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :include-spot?]
                                true]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-constraint
          {}
          :include-spot?
          true)))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :max-turnover]
                                nil]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-constraint
          {}
          :max-turnover
          nil)))
  (is (= []
         (actions/set-portfolio-optimizer-constraint
          {}
          :gross-max
          "not-a-number"))))

(deftest set-draft-objective-parameter-updates-supported-targets-test
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-return]
                                0.18]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter
          {}
          :target-return
          "0.18")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-volatility]
                                0.22]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter
          {}
          "targetVolatility"
          "0.22")))
  (is (= []
         (actions/set-portfolio-optimizer-objective-parameter
          {}
          :unknown
          "0.1"))))

(deftest set-draft-execution-assumption-normalizes-supported-values-test
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :fallback-slippage-bps]
                                35]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :fallback-slippage-bps
          "35")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :manual-capital-usdc]
                                100000]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :manual-capital-usdc
          "100000")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :manual-capital-usdc]
                                nil]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :manual-capital-usdc
          "")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :default-order-type]
                                :market]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :default-order-type
          "market")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :fee-mode]
                                :taker]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :fee-mode
          :taker)))
  (is (= []
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :fallback-slippage-bps
          "not-a-number"))))

(deftest set-draft-instrument-filter-updates-allowlist-and-blocklist-test
  (let [state {:portfolio {:optimizer {:draft {:constraints {:allowlist ["perp:BTC"]
                                                             :blocklist ["spot:PURR"]}}}}}]
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :allowlist]
                                  ["perp:BTC" "perp:ETH"]]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-instrument-filter
            state
            :allowlist
            "perp:ETH"
            true)))
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :blocklist]
                                  []]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-instrument-filter
            state
            :blocklist
            "spot:PURR"
            false)))
    (is (= []
           (actions/set-portfolio-optimizer-instrument-filter
            state
            :unknown
            "perp:BTC"
            true)))))

(deftest set-draft-asset-override-updates-row-level-constraints-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:ETH"
                                                            :market-type :perp}
                                                           {:instrument-id "spot:PURR"
                                                            :market-type :spot}]
                                           :constraints {:held-locks ["perp:BTC"]
                                                         :asset-overrides {"perp:BTC" {:max-weight 0.1}}}}}}}]
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :asset-overrides]
                                  {"perp:BTC" {:max-weight 0.1}
                                   "perp:ETH" {:max-weight 0.28}}]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-asset-override
            state
            :max-weight
            "perp:ETH"
            "0.28"))
        "The save-many contract only allows keyword paths, so instrument-keyed maps are saved whole.")
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :held-locks]
                                  ["perp:BTC" "perp:ETH"]]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-asset-override
            state
            :held-lock?
            "perp:ETH"
            true)))
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :perp-leverage]
                                  {"perp:ETH" {:max-weight 0.5}}]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-asset-override
            state
            :perp-max-weight
            "perp:ETH"
            "0.5")))
    (is (= []
           (actions/set-portfolio-optimizer-asset-override
            state
            :max-weight
            "perp:ETH"
            "not-a-number")))
    (is (= []
           (actions/set-portfolio-optimizer-asset-override
            state
            :perp-max-weight
            "spot:PURR"
            "0.5")))))

(def ^:private history-assumptions-path
  [:portfolio :optimizer :draft :history-assumptions])

(def ^:private dirty-path
  [:portfolio :optimizer :draft :metadata :dirty?])

(defn- ha-state
  [assumptions]
  {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:NEW"}
                                              {:instrument-id "perp:BTC"}]
                                   :history-assumptions assumptions
                                   :metadata {:dirty? false}}}}})

(deftest set-history-assumption-mode-seeds-conservative-anchors-test
  (is (= [[:effects/save-many
           [[history-assumptions-path
             {"perp:NEW" {:behavior :conservative
                          :expected-return 0.0
                          :volatility 0.8
                          :max-weight 0.03
                          :correlation-floor 0.75}}]
            [dirty-path true]]]]
         (actions/set-portfolio-optimizer-history-assumption-mode
          (ha-state {}) "perp:NEW" :conservative))
      "Choosing the conservative behavior on a fresh asset seeds editable anchors and saves the whole map.")
  (is (= [] (actions/set-portfolio-optimizer-history-assumption-mode
             (ha-state {}) "perp:NEW" :proxy))
      "The removed :proxy behavior is a no-op.")
  (is (= [] (actions/set-portfolio-optimizer-history-assumption-mode
             (ha-state {}) "perp:NEW" :bogus))
      "An unknown mode is a no-op.")
  (is (= [] (actions/set-portfolio-optimizer-history-assumption-mode
             (ha-state {}) " " :conservative))
      "A blank instrument-id is a no-op."))

(deftest set-history-assumption-mode-reselect-is-a-no-op-test
  (let [filled (ha-state {"perp:NEW" {:behavior :conservative
                                      :expected-return 0.25
                                      :volatility 0.9
                                      :max-weight 0.03
                                      :correlation-floor 0.75}})]
    (is (= [] (actions/set-portfolio-optimizer-history-assumption-mode
               filled "perp:NEW" :conservative))
        "Re-selecting the current (only) mode is a no-op so user input is never discarded.")))

(deftest set-history-assumption-fields-parse-percent-on-existing-entry-test
  (let [conservative (ha-state {"perp:NEW" {:behavior :conservative
                                            :expected-return nil
                                            :volatility nil
                                            :max-weight 0.03
                                            :correlation-floor 0.75}})]
    (is (= [[:effects/save-many
             [[history-assumptions-path
               {"perp:NEW" {:behavior :conservative
                            :expected-return 0.25
                            :volatility nil
                            :max-weight 0.03
                            :correlation-floor 0.75}}]
              [dirty-path true]]]]
           (actions/set-portfolio-optimizer-history-assumption-expected-return
            conservative "perp:NEW" "25"))
        "Percent text is stored as a decimal (25% => 0.25).")
    (is (= 0.9
           (get-in (actions/set-portfolio-optimizer-history-assumption-expected-volatility
                    conservative "perp:NEW" "90")
                   [0 1 0 1 "perp:NEW" :volatility])))
    (is (= 0.05
           (get-in (actions/set-portfolio-optimizer-history-assumption-max-weight-cap
                    conservative "perp:NEW" "5")
                   [0 1 0 1 "perp:NEW" :max-weight])))
    (is (= [] (actions/set-portfolio-optimizer-history-assumption-expected-return
               (ha-state {}) "perp:NEW" "25"))
        "Field setters no-op until a mode has seeded an entry.")
    (is (= [] (actions/set-portfolio-optimizer-history-assumption-expected-return
               conservative "perp:NEW" "")))))

(deftest clear-history-assumption-removes-entry-test
  (let [proxy (ha-state {"perp:NEW" {:behavior :conservative
                                     :expected-return 0.25
                                     :volatility 0.9
                                     :max-weight 0.05
                                     :correlation-floor 0.75}
                         "perp:OTHER" {:behavior :conservative
                                       :expected-return nil
                                       :volatility 0.5
                                       :max-weight 0.03
                                       :correlation-floor 0.75}})]
    (is (= [[:effects/save-many
             [[history-assumptions-path
               {"perp:OTHER" {:behavior :conservative
                              :expected-return nil
                              :volatility 0.5
                              :max-weight 0.03
                              :correlation-floor 0.75}}]
              [dirty-path true]]]]
           (actions/clear-portfolio-optimizer-history-assumption proxy "perp:NEW"))
        "Clearing removes only the target entry and preserves the rest.")
    (is (= [] (actions/clear-portfolio-optimizer-history-assumption
               (ha-state {}) "perp:NEW"))
        "Clearing an absent assumption is a no-op.")))

(deftest remove-universe-instrument-clears-history-assumption-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:NEW"}
                                                          {:instrument-id "perp:BTC"}]
                                               :constraints {:asset-overrides {}
                                                             :perp-leverage {}
                                                             :allowlist []
                                                             :blocklist []
                                                             :held-locks []}
                                               :history-assumptions
                                               {"perp:NEW" {:behavior :conservative
                                                            :expected-return nil
                                                            :volatility 0.9
                                                            :max-weight 0.03
                                                            :correlation-floor 0.75}}
                                               :metadata {:dirty? false}}}}}
        effects (actions/remove-portfolio-optimizer-universe-instrument state "perp:NEW")
        path-values (get-in (vec effects) [0 1])]
    (is (some (fn [[path value]]
                (and (= history-assumptions-path path)
                     (= {} value)))
              path-values)
        "Removing an instrument also drops its stale history assumption.")))
