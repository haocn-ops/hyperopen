(ns hyperopen.portfolio.application.analytics-state-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.api.endpoints.account :as account-endpoints]
            [hyperopen.api.projections.portfolio :as portfolio-projections]
            [hyperopen.portfolio.application.analytics-state :as analytics-state]))

(def ^:private account-a
  "0x1111111111111111111111111111111111111111")

(def ^:private account-b
  "0x2222222222222222222222222222222222222222")

(def ^:private account-c
  "0x3333333333333333333333333333333333333333")

(defn- selected-summary
  [account equity]
  {:account account
   :accountValueHistory [[1000 (- equity 25)] [2000 equity]]
   :pnlHistory [[1000 0] [2000 25]]
   :vlm (* 10 equity)})

(defn- analytics-state-fixture
  [{:keys [wallet route summary user-fees fees-loaded-for fees-loaded-at-ms
           user-fees-error fees-error-for portfolio-loading? portfolio-error
           portfolio-error-for portfolio-loaded-at-ms portfolio-loaded-for
           spectate-address]}]
  {:wallet {:address wallet}
   :router {:path (or route "/portfolio")}
   :account-context (when spectate-address
                      {:spectate-mode {:active? true
                                      :address spectate-address}})
   :portfolio-ui {:summary-scope :all
                  :summary-time-range :month}
   :portfolio {:summary-by-key {:month summary}
               :loading? (boolean portfolio-loading?)
               :error portfolio-error
               :error-for-address portfolio-error-for
               :loaded-at-ms (or portfolio-loaded-at-ms 2000)
               :loaded-for-address portfolio-loaded-for
               :user-fees user-fees
               :user-fees-loaded-for-address fees-loaded-for
               :user-fees-loaded-at-ms (or fees-loaded-at-ms 2000)
               :user-fees-error user-fees-error
               :user-fees-error-for-address fees-error-for}})

(deftest analytics-state-uses-the-effective-connected-or-trader-route-address-test
  (let [connected (analytics-state/build-analytics-state
                   (analytics-state-fixture
                    {:wallet account-a
                     :summary (selected-summary account-a 125)
                     :user-fees {:userCrossRate 0.0005
                                 :userAddRate 0.0001
                                 :dailyUserVlm [{:userCross 20 :userAdd 5}]}
                     :fees-loaded-for account-a})
                   2500)
        observed (analytics-state/build-analytics-state
                  (analytics-state-fixture
                   {:wallet account-a
                    :route (str "/portfolio/trader/" (str/upper-case account-b))
                    :summary (selected-summary account-b 225)
                    :user-fees {:userCrossRate 0.0099
                                :userAddRate 0.0088
                                :dailyUserVlm [{:userCross 9999 :userAdd 8888}]}
                    :fees-loaded-for account-a})
                  2500)]
    (is (= account-a (:account connected)))
    (is (= :live (:data-quality connected)))
    (is (= 125 (:equity connected)))
    (is (= 1250 (:volume connected)))
    (is (= {:maker 0.0001 :taker 0.0005} (:fee-rates connected)))
    (is (= account-b (:account observed)))
    (is (= 225 (:equity observed)))
    (is (= 2250 (:volume observed)))
    (is (nil? (:fee-rates observed)))
    (is (not= 18887 (:volume observed)))))

(deftest trader-analytics-requires-an-explicitly-matched-summary-address-test
  (let [vm (analytics-state/build-analytics-state
            (analytics-state-fixture
             {:wallet account-a
              :route (str "/portfolio/trader/" account-b)
              :summary (dissoc (selected-summary account-a 125) :account)
              :portfolio-loading? true
              :user-fees {:userCrossRate 0.0005 :userAddRate 0.0001}
              :fees-loaded-for account-a})
            2500)]
    (is (= account-b (:account vm)))
    (is (= :loading (:data-quality vm)))
    (is (nil? (:equity vm)))
    (is (nil? (:pnl vm)))
    (is (nil? (:volume vm)))
    (is (= [] (:timeseries vm)))))

(deftest observed-analytics-accepts-only-matching-projection-owned-endpoint-summaries-test
  (let [endpoint-summary (account-endpoints/normalize-portfolio-summary
                          {:data {"month" (dissoc (selected-summary account-b 225)
                                                    :account)}})
        matching-state (-> (analytics-state-fixture
                            {:wallet account-a
                             :route (str "/portfolio/trader/" (str/upper-case account-b))
                             :summary nil
                             :user-fees {:userCrossRate 0.0005
                                         :userAddRate 0.0001}
                             :fees-loaded-for account-b})
                           (portfolio-projections/apply-portfolio-success
                            (str/upper-case account-b)
                            endpoint-summary)
                           (assoc-in [:portfolio :loaded-at-ms] 2000))
        unaddressed-summary (:month endpoint-summary)
        rejected-states [(analytics-state-fixture
                          {:wallet account-a
                           :spectate-address account-b
                           :summary unaddressed-summary
                           :portfolio-loaded-for nil
                           :portfolio-loading? true})
                         (analytics-state-fixture
                          {:wallet account-a
                           :spectate-address account-b
                           :summary unaddressed-summary
                           :portfolio-loaded-for account-a
                           :portfolio-loading? true})]
        matching (analytics-state/build-analytics-state matching-state 2500)]
    (is (nil? (:account unaddressed-summary)))
    (is (= account-b (get-in matching-state [:portfolio :loaded-for-address])))
    (is (= account-b (:account matching)))
    (is (= :live (:data-quality matching)))
    (is (= 225 (:equity matching)))
    (is (= 25 (:pnl matching)))
    (is (= 2250 (:volume matching)))
    (doseq [state rejected-states]
      (let [vm (analytics-state/build-analytics-state state 2500)]
        (is (= account-b (:account vm)))
        (is (= :loading (:data-quality vm)))
        (is (nil? (:equity vm)))
        (is (nil? (:pnl vm)))
        (is (nil? (:volume vm)))
        (is (= [] (:timeseries vm)))))))

(deftest portfolio-and-user-fee-lifecycles-are-address-scoped-and-independent-test
  (let [portfolio-error (analytics-state/build-analytics-state
                         (analytics-state-fixture
                          {:wallet account-a
                           :summary nil
                           :portfolio-error "provider timed out"})
                         2500)
        fee-error (analytics-state/build-analytics-state
                   (analytics-state-fixture
                    {:wallet account-a
                     :summary (selected-summary account-a 125)
                     :portfolio-loaded-at-ms 10000
                     :user-fees {:userCrossRate 0.0005
                                 :userAddRate 0.0001}
                     :fees-loaded-for account-a
                     :fees-loaded-at-ms 10000
                     :user-fees-error "fee endpoint unavailable"
                     :fees-error-for account-a})
                   10500)
        expired-fees (analytics-state/build-analytics-state
                      (analytics-state-fixture
                       {:wallet account-a
                        :summary (selected-summary account-a 125)
                        :portfolio-loaded-at-ms 10000
                        :user-fees {:userCrossRate 0.0005
                                    :userAddRate 0.0001}
                        :fees-loaded-for account-a
                        :fees-loaded-at-ms 1000})
                      10500)
        discounted-rates (analytics-state/build-analytics-state
                          (analytics-state-fixture
                           {:wallet account-a
                            :summary (selected-summary account-a 125)
                            :portfolio-loaded-at-ms 10000
                            :user-fees {:userCrossRate 0.0005
                                        :userAddRate 0.0001
                                        :activeReferralDiscount 0.1}
                            :fees-loaded-for account-a
                            :fees-loaded-at-ms 10000})
                          10500)]
    (is (= :provider-error (:data-quality portfolio-error)))
    (is (= "provider timed out" (:message portfolio-error)))
    (is (= :partial (:data-quality fee-error)))
    (is (= 125 (:equity fee-error)))
    (is (nil? (:fee-rates fee-error)))
    (is (= :stale (get-in fee-error [:field-status :fee-rates])))
    (is (= :partial (:data-quality expired-fees)))
    (is (= 125 (:equity expired-fees)))
    (is (nil? (:fee-rates expired-fees)))
    (is (= :stale (get-in expired-fees [:field-status :fee-rates])))
    (is (= {:maker 0.00009 :taker 0.00045}
           (:fee-rates discounted-rates)))))

(deftest observed-analytics-requires-explicit-summary-and-error-ownership-test
  (let [missing-summary-owner (analytics-state/build-analytics-state
                               (analytics-state-fixture
                                {:wallet account-a
                                 :spectate-address account-b
                                 :summary (dissoc (selected-summary account-a 125) :account)
                                 :portfolio-loading? true})
                               2500)
        unowned-error (analytics-state/build-analytics-state
                        (analytics-state-fixture
                         {:wallet account-a
                          :spectate-address account-b
                          :summary nil
                          :portfolio-error "owner request failed"})
                        2500)
        owned-initial-error (analytics-state/build-analytics-state
                             (analytics-state-fixture
                              {:wallet account-a
                               :spectate-address account-b
                               :summary nil
                               :portfolio-error "observed request failed"
                               :portfolio-error-for account-b})
                             2500)
        current-summary-error (analytics-state/build-analytics-state
                               (analytics-state-fixture
                                {:wallet account-a
                                 :spectate-address account-b
                                 :summary (selected-summary account-b 225)
                                 :user-fees {:userCrossRate 0.0005 :userAddRate 0.0001}
                                 :fees-loaded-for account-b
                                 :portfolio-error "observed request failed"})
                               2500)
        owned-error (analytics-state/build-analytics-state
                     (analytics-state-fixture
                      {:wallet account-a
                       :spectate-address account-b
                       :summary (selected-summary account-b 225)
                       :user-fees {:userCrossRate 0.0005 :userAddRate 0.0001}
                       :fees-loaded-for account-b
                       :portfolio-error "observed request failed"
                       :portfolio-error-for account-b})
                     2500)]
    (is (= account-b (:account missing-summary-owner)))
    (is (= :loading (:data-quality missing-summary-owner)))
    (is (nil? (:equity missing-summary-owner)))
    (is (nil? (:volume missing-summary-owner)))
    (is (= :empty (:data-quality unowned-error)))
    (is (not= "owner request failed" (:message unowned-error)))
    (is (= :provider-error (:data-quality owned-initial-error)))
    (is (= "observed request failed" (:message owned-initial-error)))
    (is (= :stale (:data-quality current-summary-error)))
    (is (= "observed request failed" (:message current-summary-error)))
    (is (= :stale (:data-quality owned-error)))
    (is (= "observed request failed" (:message owned-error)))))

(deftest user-fee-volume-requires-nonempty-parseable-row-evidence-test
  (let [summary (dissoc (selected-summary account-a 125) :vlm)
        build (fn [rows]
                (analytics-state/build-analytics-state
                 (analytics-state-fixture
                  {:wallet account-a
                   :summary summary
                   :user-fees {:userCrossRate 0.0005
                               :userAddRate 0.0001
                               :dailyUserVlm rows}
                   :fees-loaded-for account-a})
                 2500))
        empty-rows (build [])
        invalid-row (build [{:exchange "not-a-number"}])
        mixed-rows (build [{:userCross 20 :userAdd 5}
                           {:unexpected "row"}])
        valid-rows (build [{:userCross "20" :userAdd "5"}
                           {:exchange "7"}])]
    (doseq [vm [empty-rows invalid-row mixed-rows]]
      (is (nil? (:volume vm)))
      (is (= :unavailable (get-in vm [:field-status :volume]))))
    (is (= 32 (:volume valid-rows)))))

(deftest analytics-state-fails-closed-for-missing-or-previous-address-data-test
  (testing "an unconnected portfolio is unavailable rather than a zero-balance account"
    (doseq [wallet [nil "" "  " "not-an-address"]]
      (let [vm (analytics-state/build-analytics-state
                (analytics-state-fixture {:wallet wallet
                                          :summary (selected-summary account-a 125)
                                          :user-fees {:userCrossRate 0.01}
                                          :fees-loaded-for account-a})
                2500)]
        (is (= :unavailable (:data-quality vm)))
        (is (nil? (:equity vm)))
        (is (nil? (:pnl vm)))
        (is (nil? (:return-pct vm)))
        (is (nil? (:max-drawdown-pct vm)))
        (is (nil? (:volume vm)))
        (is (nil? (:fee-rates vm)))
        (is (= [] (:timeseries vm))))))
  (testing "a route switch cannot retain the previous route's metrics or error"
    (let [vm (analytics-state/build-analytics-state
              (analytics-state-fixture
               {:wallet account-a
                :route (str "/portfolio/trader/" account-c)
                :summary (selected-summary account-b 225)
                :portfolio-loading? true
                :portfolio-error "account-b provider error"
                :user-fees {:userCrossRate 0.0099 :userAddRate 0.0088}
                :fees-loaded-for account-b})
              2500)]
      (is (= account-c (:account vm)))
      (is (= :loading (:data-quality vm)))
      (is (nil? (:equity vm)))
      (is (nil? (:fee-rates vm)))
      (is (not= "account-b provider error" (:message vm))))))
