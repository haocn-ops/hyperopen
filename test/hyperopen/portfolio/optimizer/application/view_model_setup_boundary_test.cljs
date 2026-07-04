(ns hyperopen.portfolio.optimizer.application.view-model-setup-boundary-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.universe-candidates :as universe-candidates]
            [hyperopen.portfolio.optimizer.application.view-model :as view-model]))

(def ^:private btc-instrument
  {:instrument-id "perp:BTC"
   :market-type :perp
   :coin "BTC"
   :symbol "BTC-USDC"
   :name "Bitcoin"})

(def ^:private eth-instrument
  {:instrument-id "perp:ETH"
   :market-type :perp
   :coin "ETH"
   :symbol "ETH-USDC"
   :name "Ethereum"})

(deftest universe-section-model-projects-render-ready-row-labels-test
  (let [calls (atom [])
        selected-instrument (assoc btc-instrument
                                   :volume24h 99000000)
        candidate {:key "spot:@1"
                   :instrument-id "spot:@1"
                   :market-type :spot
                   :coin "@1"
                   :symbol "PURR/USDC"
                   :base "PURR"
                   :name "Purr"
                   :volume24h 125000000
                   :liquidity-label "thin"}
        candidate-markets-stub (fn
                                 ([_state _universe query]
                                  (swap! calls conj query)
                                  [candidate])
                                 ([_state _universe query _opts]
                                  (swap! calls conj query)
                                  [candidate]))
        model (with-redefs [universe-candidates/candidate-markets
                            candidate-markets-stub]
                (view-model/universe-section-model
                 {:portfolio-ui {:optimizer {:universe-search-query "purr"}}}
                 {:universe [selected-instrument]}
                 {:readiness {:request {:universe [selected-instrument]}}
                  :history-load-state {:status :idle}
                  :history-status-by-id {"perp:BTC" :aligned}}))]
    (is (= ["purr"] @calls))
    ;; An aligned (:sufficient) asset is "all clear" so the history chip is
    ;; suppressed (by-exception display): no :history-label / :history-tone.
    (is (= [{:instrument-id "perp:BTC"
             :market-type :perp
             :primary-label "BTC"
             :secondary-label "Bitcoin"
             :history-status :sufficient
             :liquidity-label "deep"}]
           (mapv #(select-keys % [:instrument-id
                                  :market-type
                                  :primary-label
                                  :secondary-label
                                  :history-status
                                  :history-label
                                  :history-tone
                                  :liquidity-label])
                 (:selected-rows model))))
    (is (= [{:market-key "spot:@1"
             :market-type :spot
             :active? true
             :label "PURR/USDC"
             :name "Purr"
             :adv-label "$125M"}]
           (mapv #(select-keys % [:market-key
                                  :market-type
                                  :active?
                                  :label
                                  :name
                                  :adv-label])
                 (:candidate-rows model))))))

(deftest readiness-panel-model-projects-copy-and-warning-rows-test
  (let [readiness {:status :blocked
                   :reason :incomplete-history
                   :request {:requested-universe [btc-instrument]
                             :universe []
                             :warnings []}
                   :blocking-warnings [{:code :missing-candle-history
                                        :instrument-id "perp:BTC"
                                        :coin "BTC"}]}
        failed-load {:status :failed
                     :error {:message "history endpoint unavailable"}}
        failed-model (view-model/readiness-panel-model readiness failed-load)
        missing-model (view-model/readiness-panel-model
                       {:status :blocked
                        :reason :missing-universe
                        :warnings []}
                       {:status :idle})]
    (is (= "History load failed. Existing history, if any, is retained."
           (:copy failed-model)))
    (is (= "history endpoint unavailable" (:error-message failed-model)))
    ;; Warnings are now GROUPED by code: one row per kind with a count + affected-asset list. A
    ;; single warning is a count-1 group whose message is the per-asset message. Groups built
    ;; from blocking-warnings carry :severity :blocking so the panel can rank them.
    (is (= [{:code :missing-candle-history
             :code-label "missing-candle-history"
             :count 1
             :severity :blocking
             :message "Bitcoin: no candle history returned for BTC."
             :assets [{:instrument-id "perp:BTC" :label "Bitcoin"}]}]
           (:warnings failed-model)))
    (is (= "Data health" (:title failed-model)))
    (is (= :blocked (get-in failed-model [:status :level])))
    (is (= "Select a universe before running."
           (:copy missing-model)))
    (is (= :blocked (get-in missing-model [:status :level])))))

(deftest group-readiness-warnings-collapses-same-code-warnings-test
  (let [readiness {:status :ready
                   :runnable? true
                   :request {:requested-universe [btc-instrument eth-instrument]
                             :warnings []}
                   :warnings [{:code :stale-history :instrument-id "perp:BTC"}
                              {:code :stale-history :instrument-id "perp:ETH"}
                              {:code :insufficient-common-history :instrument-id "perp:BTC"}]}
        {:keys [warnings]} (view-model/readiness-panel-model readiness {:status :idle})]
    (is (= 2 (count warnings)) "three raw warnings collapse into two code groups")
    (let [stale (first (filter #(= :stale-history (:code %)) warnings))]
      (is (= 2 (:count stale)) "both stale rows collapse into one group with count 2")
      (is (str/includes? (:message stale) "stale"))
      (is (= ["Bitcoin" "Ethereum"] (mapv :label (:assets stale)))
          "the affected-asset labels are resolved from the requested universe")
      (is (= ["perp:BTC" "perp:ETH"] (mapv :instrument-id (:assets stale)))))
    (let [stale (first (filter #(= :stale-history (:code %)) warnings))]
      (is (= :caution (:severity stale)))
      (is (some? (:detail stale)) "stale groups carry a plain-language context line"))
    (let [insufficient (first (filter #(= :insufficient-common-history (:code %)) warnings))]
      (is (= 1 (:count insufficient)))
      (is (= ["perp:BTC"] (mapv :instrument-id (:assets insufficient))))
      ;; Provider-limit groups lead with the human headline, never the raw
      ;; provider/vendor message.
      (is (= "History source is limited — not enough shared history across the selected assets."
             (:message insufficient))))))

(deftest setup-summary-model-projects-summary-row-data-test
  (let [formatters {:labelize {:risk-adjusted "Risk Adjusted"
                               :historical-mean "Historical Mean"
                               :max-sharpe "Max Sharpe"
                               :conservative "Conservative"}
                    :percent-label (fn [value] (str (* 100 value) "%"))}
        draft {:universe [btc-instrument eth-instrument]
               :objective {:kind :max-sharpe}
               :return-model {:kind :historical-mean}
               :constraints {:gross-max 1.5
                             :max-asset-weight 0.4}}
        model (view-model/setup-summary-model draft formatters)
        row-by-label (into {}
                           (map (juxt :label identity))
                           (:summary-rows model))]
    (is (= :max-sharpe (:active-preset model))
        "Risk-adjusted / Use my views consolidated: a max-sharpe objective IS the Maximum Sharpe preset.")
    (is (false? (:black-litterman? model)))
    (is (= "Max Sharpe" (get-in row-by-label ["Preset" :title])))
    (is (= "Historical Mean" (get-in row-by-label ["Expected Returns" :title])))
    (is (= "Max Sharpe" (get-in row-by-label ["Objective" :title])))
    (is (= "Gross ≤ 1.50× · Max asset 40%" (get-in row-by-label ["Portfolio exposure" :title]))))
  (is (true? (:black-litterman?
              (view-model/setup-summary-model
               {:return-model {:kind :black-litterman}}
               {:labelize name
                :percent-label str})))))

(deftest constraints-summary-line-uses-readable-exposure-copy-test
  (is (= "Gross 4.80×–5.80× · Net +0.55×–1.55× long · Max asset 574% · Rebalance 3.0 pp"
         (view-model/constraints-summary-line
          {:gross-min 4.8
           :gross-max 5.8
           :net-min 0.55
           :net-max 1.55
           :max-asset-weight 5.74
           :rebalance-tolerance 0.03})))
  (is (= "Gross ≤ 2.00× · Net +1.00× long · Max asset 50% · Rebalance 3.0 pp"
         (view-model/constraints-summary-line
          {:gross-max 2
           :net-min 1
           :net-max 1
           :max-asset-weight 0.5
           :rebalance-tolerance 0.03})))
  (is (= "Gross ≤ 3.00× · Net -0.25×–+0.25× neutral range · Max asset 25%"
         (view-model/constraints-summary-line
          {:gross-max 3
           :net-min -0.25
           :net-max 0.25
           :max-asset-weight 0.25}))))

(deftest black-litterman-cards-model-projects-preview-card-data-test
  (let [formatters {:pct (fn [value] (str "pct:" value))
                    :signed-view-pct (fn [value] (str "view:" value))
                    :signed-delta (fn [value] (str "delta:" value))}
        view {:id "view-1"
              :kind :relative
              :instrument-id "perp:BTC"
              :comparator-instrument-id "perp:ETH"
              :direction :outperform
              :return 0.3
              :confidence 0.2}
        draft {:universe [btc-instrument eth-instrument]
               :return-model {:kind :black-litterman
                              :views [view]}}
        readiness {:request {:universe [btc-instrument eth-instrument]
                             :return-model {:kind :black-litterman
                                            :views [view]}}}
        preview {:status :ready
                 :rows [{:instrument-id "perp:BTC"
                         :label "BTC"
                         :prior-return 0.2
                         :posterior-return 0.21}
                        {:instrument-id "perp:ETH"
                         :label "ETH"
                         :prior-return 0.3
                         :posterior-return 0.28}]}
        model (view-model/black-litterman-cards-model draft
                                                       readiness
                                                       preview
                                                       formatters)
        cards-by-role (into {}
                            (map (juxt :role identity))
                            (:cards model))
        market-card (get cards-by-role
                         "portfolio-optimizer-setup-use-my-views-card-market-reference")
        views-card (get cards-by-role
                        "portfolio-optimizer-setup-use-my-views-card-your-views")
        output-card (get cards-by-role
                         "portfolio-optimizer-setup-use-my-views-card-combined-output")
        output-note (some #(when (= :note (:kind %)) %)
                          (:rows output-card))]
    (is (= ["Market reference" "Your views" "Combined output"]
           (mapv :label (:cards model))))
    (is (= [{:kind :value
             :label "ETH"
             :value-label "pct:0.3"
             :value-tone :prior}
            {:kind :value
             :label "BTC"
             :value-label "pct:0.2"
             :value-tone :prior}]
           (mapv #(select-keys % [:kind :label :value-label :value-tone])
                 (:rows market-card))))
    (is (= [{:kind :view
             :label "BTC > ETH by"
             :return-label "view:0.3"
             :confidence-label "low"}]
           (mapv #(select-keys % [:kind
                                  :label
                                  :return-label
                                  :confidence-label])
                 (:rows views-card))))
    (is (some #(and (= :combined-output (:kind %))
                    (= "BTC" (:label %))
                    (= "pct:0.2" (:prior-label %))
                    (= "pct:0.21" (:posterior-label %))
                    (= "delta:0.009999999999999981" (:delta-label %)))
              (:rows output-card)))
    (is (str/includes? (:copy output-note)
                       "Low confidence on BTC means your view:0.3 view only pulls posterior to pct:0.21"))))

(def ^:private new-perp-instrument
  {:instrument-id "perp:NEW"
   :market-type :perp
   :coin "NEW"})

;; History assumption cards are load-aware: an asset is only carded once its history
;; has actually been fetched (a succeeded load that requested it), so a fully-historied
;; asset never flashes a card while history is still loading.
(def ^:private loaded-state-with-new
  {:status :succeeded
   :request-signature {:universe [btc-instrument new-perp-instrument]}})

(deftest history-assumption-cards-projects-render-ready-cards-test
  (let [draft {:universe [btc-instrument new-perp-instrument]
               :objective {:kind :minimum-variance}
               :history-assumptions
               {"perp:NEW" {:behavior :conservative
                            :expected-return nil
                            :volatility nil
                            :max-weight 0.03
                            :correlation-floor 0.75}}}
        readiness {:request {:requested-universe [btc-instrument new-perp-instrument]
                             :universe [btc-instrument]
                             :objective {:kind :minimum-variance}}
                   :blocking-warnings [{:code :history-assumption-incomplete
                                        :instrument-id "perp:NEW"
                                        :missing :volatility
                                        :message "NEW needs an expected annual volatility."}]}
        model (view-model/history-assumption-cards
               {} draft readiness loaded-state-with-new
               {:percent-label (fn [value] (str (js/Math.round (* 100 value)) "%"))})
        card (first (:cards model))]
    (is (true? (:applicable? model)))
    (is (= 1 (count (:cards model))))
    (is (= "perp:NEW" (:instrument-id card)))
    (is (= :conservative (:mode card)))
    (is (= :incomplete (:status card)))
    (is (= "Needs assumptions" (:status-label card)))
    (is (= [{:value :conservative :label "Use a conservative assumption"}]
           (:mode-options card)))
    (is (= "3%" (get-in card [:max-weight :percent-label])))
    (is (nil? (get-in card [:volatility :value])))
    (is (= ["NEW needs an expected annual volatility."] (:errors card)))
    (is (false? (:engine-applied? card)))
    (is (= :actions/set-portfolio-optimizer-history-assumption-mode
           (get-in card [:actions :set-mode])))
    (is (= :actions/clear-portfolio-optimizer-history-assumption
           (get-in card [:actions :clear])))))

(deftest history-assumption-cards-hidden-while-history-still-loading-test
  ;; Regression: before history loads, readiness reports every asset as :missing
  ;; (nothing aligned yet). A fully-historied asset (e.g. DOGE) must NOT be carded
  ;; until its history has actually been fetched.
  (let [draft {:universe [btc-instrument new-perp-instrument]
               :objective {:kind :minimum-variance}
               :history-assumptions {}}
        readiness {:request {:requested-universe [btc-instrument new-perp-instrument]
                             :universe []
                             :objective {:kind :minimum-variance}}
                   :blocking-warnings []}
        loading (view-model/history-assumption-cards
                 {} draft readiness {:status :loading} {})
        idle (view-model/history-assumption-cards
              {} draft readiness {:status :idle} {})]
    (is (false? (:applicable? loading)))
    (is (empty? (:cards loading)))
    (is (false? (:applicable? idle)))
    (is (empty? (:cards idle)))))

(deftest history-assumption-cards-flag-short-but-present-history-test
  ;; An asset with thin-but-present history (e.g. SKR's ~75 daily candles) clears the
  ;; engine's tiny minimum (status :sufficient) but is below the user-facing threshold,
  ;; so it should still be offered a card; an asset with ample history should not.
  (let [skr {:instrument-id "perp:SKR" :market-type :perp :coin "SKR"}
        draft {:universe [btc-instrument skr]
               :objective {:kind :minimum-variance}
               :history-assumptions {}}
        readiness {:request {:requested-universe [btc-instrument skr]
                             :universe [btc-instrument skr]
                             :objective {:kind :minimum-variance}}
                   :blocking-warnings []}
        state {:portfolio
               {:optimizer
                {:history-data
                 ;; SKR has only ~75 daily candles; BTC has the full ~1-year fetch cap.
                 {:candle-history-by-coin
                  {"SKR" (vec (repeat 75 {:time 1 :close "1"}))
                   "BTC" (vec (repeat 365 {:time 1 :close "1"}))}}}}}
        load-state {:status :succeeded
                    :request-signature {:universe [btc-instrument skr]}}
        model (view-model/history-assumption-cards state draft readiness load-state {})
        carded-ids (set (map :instrument-id (:cards model)))]
    (is (contains? carded-ids "perp:SKR")
        "Short-but-present history is flagged for assumptions.")
    (is (not (contains? carded-ids "perp:BTC"))
        "An asset with ample history is not flagged.")))

(deftest universe-row-carries-assumption-badge-test
  (let [draft {:universe [btc-instrument new-perp-instrument]
               :objective {:kind :minimum-variance}
               :history-assumptions
               {"perp:NEW" {:behavior :conservative
                            :expected-return nil
                            :volatility 0.9
                            :max-weight 0.03
                            :correlation-floor 0.75}}}
        model (view-model/universe-section-model
               {}
               draft
               {:readiness {:request {:universe [btc-instrument]
                                      :objective {:kind :minimum-variance}}}
                :history-load-state {:status :idle}
                :history-status-by-id {"perp:BTC" :aligned}})
        badge-by-id (into {} (map (juxt :instrument-id :assumption-badge-label))
                          (:selected-rows model))]
    (is (= "Ready" (get badge-by-id "perp:BTC")))
    (is (= "Conservative" (get badge-by-id "perp:NEW"))
        "A complete conservative assumption shows the Conservative badge.")))
