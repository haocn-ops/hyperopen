(ns hyperopen.views.portfolio.summary-cards-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.portfolio.summary-cards :as summary-cards]))

(deftest summary-card-renders-selector-hooks-and-account-breakdown-test
  (let [view (summary-cards/summary-card
              {:summary {:pnl -12.34
                         :volume 456.78
                         :max-drawdown-pct 0.12
                         :total-equity 890.12
                         :show-perps-account-equity? true
                         :perps-account-equity 222.22
                         :spot-equity-label "Spot Account Equity"
                         :spot-account-equity 333.33
                         :show-vault-equity? true
                         :vault-equity 444.44
                         :show-earn-balance? true
                         :earn-balance 555.55
                         :show-staking-account? true
                         :staking-account-hype 7}
               :selectors {:summary-scope {:label "Perps + Spot + Vaults"
                                           :open? true
                                           :value :all
                                           :options [{:value :all :label "Perps + Spot + Vaults"}
                                                     {:value :perps :label "Perps only"}]}
                           :summary-time-range {:label "30D"
                                                :open? true
                                                :value :month
                                                :options [{:value :month :label "30D"}
                                                          {:value :day :label "24H"}]}}})
        scope-trigger (hiccup/find-by-data-role view "portfolio-summary-scope-selector-trigger")
        scope-perps-option (hiccup/find-by-data-role view "portfolio-summary-scope-selector-option-perps")
        time-range-trigger (hiccup/find-by-data-role view "portfolio-summary-time-range-selector-trigger")
        time-range-day-option (hiccup/find-by-data-role view "portfolio-summary-time-range-selector-option-day")
        negative-pnl (hiccup/find-first-node view #(and (= :span (first %))
                                                        (contains? (hiccup/direct-texts %) "-$12.34")))
        all-text (set (hiccup/collect-strings view))]
    (is (= [[:actions/toggle-portfolio-summary-scope-dropdown]]
           (get-in scope-trigger [1 :on :click])))
    (is (true? (get-in scope-trigger [1 :aria-expanded])))
    (is (= [[:actions/select-portfolio-summary-scope :perps]]
           (get-in scope-perps-option [1 :on :click])))
    (is (= [[:actions/toggle-portfolio-summary-time-range-dropdown]]
           (get-in time-range-trigger [1 :on :click])))
    (is (= [[:actions/select-portfolio-summary-time-range :day]]
           (get-in time-range-day-option [1 :on :click])))
    (is (contains? (hiccup/node-class-set negative-pnl) "text-error"))
    (is (contains? all-text "Perps Account Equity"))
    (is (contains? all-text "Vault Equity"))
    (is (contains? all-text "Earn Balance"))
    (is (contains? all-text "Staking Account"))
    (is (contains? all-text "7 HYPE"))))

(deftest metric-cards-render-stable-volume-and-fee-copy-test
  (let [view (summary-cards/metric-cards {:volume-14d-usd 0
                                          :fees {:taker 0.45
                                                 :maker 0.15}
                                          :fee-schedule {:open? true}})
        volume-card (hiccup/find-by-data-role view "portfolio-14d-volume-card")
        fees-card (hiccup/find-by-data-role view "portfolio-fees-card")
        fee-schedule-trigger (hiccup/find-by-data-role view "portfolio-fee-schedule-trigger")
        all-text (set (hiccup/collect-strings view))]
    (is (some? volume-card))
    (is (some? fees-card))
    (is (= "button" (get-in fee-schedule-trigger [1 :type])))
    (is (= "dialog" (get-in fee-schedule-trigger [1 :aria-haspopup])))
    (is (= "true" (get-in fee-schedule-trigger [1 :aria-expanded])))
    (is (= [[:actions/open-portfolio-fee-schedule
             :event.currentTarget/bounds]]
           (get-in fee-schedule-trigger [1 :on :click])))
    (is (contains? all-text "14 Day Volume"))
    (is (some #(re-find #"^\$0(?:\.0)?$" %) all-text))
    (is (contains? all-text "Fees (Taker / Maker)"))
    (is (contains? all-text "0.450% / 0.150%"))
    (is (contains? all-text "View Volume"))
    (is (contains? all-text "View Fee Schedule"))))

(deftest analytics-metrics-render-stable-evidence-anchors-without-zero-fallbacks-test
  (let [live-analytics {:data-quality :live
                        :range :month
                        :equity 1234
                        :pnl -56
                        :return-pct -4.5
                        :max-drawdown-pct -7.25
                        :volume 7890
                        :fee-rates {:taker 0.0005 :maker 0.0001}
                        :as-of-ms 2000
                        :message "Live provider data"}
        unavailable-analytics {:data-quality :unavailable
                               :equity nil
                               :pnl nil
                               :return-pct nil
                               :max-drawdown-pct nil
                               :volume nil
                               :fee-rates nil
                               :message "Connect a wallet to view portfolio analytics"}
        expired-fee-rates-analytics (assoc live-analytics :fee-rates nil)
        selectors {:summary-scope {:label "Perps + Spot + Vaults"
                                   :open? false
                                   :value :all
                                   :options [{:value :all :label "Perps + Spot + Vaults"}]}
                   :summary-time-range {:label "30D"
                                        :open? false
                                        :value :month
                                        :options [{:value :month :label "30D"}]}}
        render (fn [analytics]
                 [:div
                  (summary-cards/summary-card {:analytics analytics
                                               :summary {}
                                               :selectors selectors})
                  (summary-cards/metric-cards {:analytics analytics
                                               :fee-schedule {:open? false}})])
        live-view (render live-analytics)
        unavailable-view (render unavailable-analytics)
        expired-fee-rates-view (render expired-fee-rates-analytics)
        live-text (set (hiccup/collect-strings live-view))
        unavailable-text (set (hiccup/collect-strings unavailable-view))]
    (is (= "live" (get-in (hiccup/find-by-data-role live-view "portfolio-analytics-status")
                            [1 :data-quality])))
    (doseq [[data-role expected-text] [["portfolio-analytics-equity" "$1,234.00"]
                                      ["portfolio-analytics-pnl" "-$56.00"]
                                      ["portfolio-analytics-return" "-4.50%"]
                                      ["portfolio-analytics-drawdown" "-7.25%"]
                                      ["portfolio-analytics-volume" "$7,890.00"]
                                      ["portfolio-analytics-fee-rates" "0.050% / 0.010%"]]]
      (is (some? (hiccup/find-by-data-role live-view data-role)))
      (is (contains? live-text expected-text)))
    (is (contains? live-text "Current maker / taker rates"))
    (is (contains? live-text "30 Day Volume"))
    (is (= "unavailable"
           (get-in (hiccup/find-by-data-role unavailable-view "portfolio-analytics-status")
                   [1 :data-quality])))
    (is (contains? unavailable-text "14 Day Volume"))
    (doseq [data-role ["portfolio-analytics-equity"
                       "portfolio-analytics-pnl"
                       "portfolio-analytics-return"
                       "portfolio-analytics-drawdown"
                       "portfolio-analytics-volume"
                       "portfolio-analytics-fee-rates"]]
      (is (contains? (set (hiccup/collect-strings
                           (hiccup/find-by-data-role unavailable-view data-role)))
                     "Unavailable")))
    (is (not (contains? unavailable-text "$0.00")))
    (is (not (contains? unavailable-text "0.000%")))
    (is (contains? (set (hiccup/collect-strings
                         (hiccup/find-by-data-role expired-fee-rates-view
                                                   "portfolio-analytics-fee-rates")))
                   "Unavailable"))))

(deftest analytics-summary-preserves-supported-account-composition-values-test
  (let [analytics {:data-quality :live
                   :equity 1234
                   :pnl 20
                   :return-pct 2
                   :max-drawdown-pct -1
                   :volume 100
                   :message "Live provider data"}
        selectors {:summary-scope {:label "All"
                                   :open? false
                                   :value :all
                                   :options [{:value :all :label "All"}]}
                   :summary-time-range {:label "30D"
                                        :open? false
                                        :value :month
                                        :options [{:value :month :label "30D"}]}}
        summary {:show-perps-account-equity? true
                 :perps-account-equity 222.22
                 :spot-equity-label "Spot Account Equity"
                 :spot-account-equity 333.33
                 :show-vault-equity? true
                 :vault-equity 444.44
                 :show-earn-balance? true
                 :earn-balance 555.55
                 :show-staking-account? true
                 :staking-account-hype 7}
        render #(summary-cards/summary-card {:analytics analytics
                                              :summary %
                                              :selectors selectors})
        supported-text (set (hiccup/collect-strings (render summary)))
        unavailable-text (hiccup/collect-strings
                          (render (assoc summary
                                         :perps-account-equity nil
                                         :spot-account-equity nil
                                         :vault-equity nil
                                         :earn-balance nil
                                         :staking-account-hype nil)))]
    (doseq [value ["$222.22" "$333.33" "$444.44" "$555.55" "7 HYPE"]]
      (is (contains? supported-text value)))
    (is (= 5 (count (filter #(= "Unavailable" %) unavailable-text))))))
