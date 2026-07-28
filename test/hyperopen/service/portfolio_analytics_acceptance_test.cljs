(ns hyperopen.service.portfolio-analytics-acceptance-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.service.fixtures :as fixtures]
            [hyperopen.service.portfolio-analytics :as analytics]
            [hyperopen.service.tenant-config :as tenant-config]))

(deftest connected-account-analytics-exposes-professional-metrics-and-quality-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        vm (analytics/build-portfolio-view-model
            tenant
            fixtures/account-history
            {:account fixtures/wallet-address
             :range :all})]
    (is (= fixtures/wallet-address (:account vm)))
    (is (= 1050 (:equity vm)))
    (is (= 50 (:pnl vm)))
    (is (number? (:return-pct vm)))
    (is (number? (:max-drawdown-pct vm)))
    (is (= 1200 (:volume vm)))
    (is (= 5 (:fees vm)))
    (is (seq (:timeseries vm)))
    (is (keyword? (:data-quality vm)))
    (is (= :live (:data-quality vm)))
    (is (not (contains? vm :fills)))
    (is (not (contains? vm :raw-history)))))

(deftest native-hyperopen-history-keeps-deposits-out-of-pnl-and-return-test
  (let [vm (analytics/build-portfolio-view-model
            (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
            fixtures/pure-deposit-history
            {:account fixtures/wallet-address
             :range :all})]
    (is (= 2000 (:equity vm)))
    (is (= 0 (:pnl vm)))
    (is (= 0 (:return-pct vm)))
    (is (= 0 (:volume vm)))
    (is (nil? (:fees vm)))
    (is (= :live (:data-quality vm)))))

(deftest stale-partial-history-is-qualified-without-fabricated-zeroes-test
  (let [history (assoc fixtures/account-history
                       :accountValueHistory [[1700000000000 1000]]
                       :pnlHistory [[1700000000000 0]]
                       :userFills nil
                       :freshness {:fetched-at-ms 1600000000000
                                   :now-ms 1700007200000
                                   :max-age-ms 3600000})
        vm (analytics/build-portfolio-view-model
            (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
            history
            {:account fixtures/wallet-address
             :range :all})]
    (is (= :stale (:data-quality vm)))
    (is (nil? (:volume vm)))
    (is (nil? (:fees vm)))
    (is (nil? (:max-drawdown-pct vm)))
    (is (not= 0 (:volume vm)))))

(deftest analytics-event-is-scoped-to-selected-account-and-range-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        event (analytics/build-analytics-viewed-event
               tenant
               {:account fixtures/wallet-address
                :range :month
                :occurred-at-ms 1700000000000})]
    (is (= "hyperopen-default" (:tenant/id event)))
    (is (= :analytics-viewed (:event/type event)))
    (is (= :month (:range event)))
    (is (string? (:wallet/address-hash event)))
    (is (not (contains? event :fills)))
    (is (not (contains? event :history)))
    (is (not= fixtures/wallet-address (:wallet/address event)))))

(deftest analytics-data-quality-state-matrix-is-explicit-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        options {:account fixtures/wallet-address :range :all}
        build #(analytics/build-portfolio-view-model tenant % options)
        loading (build {:status :loading})
        empty (build {:status :ready
                      :source :provider
                      :accountValueHistory []
                      :pnlHistory []
                      :userFills []
                      :freshness {:fetched-at-ms 1700000000000
                                  :now-ms 1700000001000
                                  :max-age-ms 3600000}})
        provider-error (build {:status :error
                               :error {:category :provider-unavailable}})
        demo (build (assoc fixtures/account-history :source :demo))]
    (is (= :loading (:data-quality loading)))
    (is (= :empty (:data-quality empty)))
    (is (= :provider-error (:data-quality provider-error)))
    (is (= :demo (:data-quality demo)))
    (is (nil? (:equity loading)))
    (is (nil? (:equity provider-error)))))

(deftest fee-rates-do-not-count-as-a-retained-portfolio-snapshot-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        options {:account fixtures/wallet-address :range :all}
        build #(analytics/build-portfolio-view-model tenant % options)
        fee-only {:fee-rates {:maker 0.0001 :taker 0.0005}
                  :fee-rates-state {:loaded? true :fresh? true}}
        provider-error (build (assoc fee-only
                                     :status :error
                                     :error "portfolio provider timed out"))
        settled (build (assoc fee-only :status :ready))]
    (is (= :provider-error (:data-quality provider-error)))
    (is (= "portfolio provider timed out" (:message provider-error)))
    (is (not= :stale (:data-quality provider-error)))
    (is (nil? (:equity provider-error)))
    (is (nil? (:volume provider-error)))
    (is (= [] (:timeseries provider-error)))
    (is (= {:maker 0.0001 :taker 0.0005} (:fee-rates provider-error)))
    (is (= :available (get-in provider-error [:field-status :fee-rates])))
    (is (= :empty (:data-quality settled)))
    (is (nil? (:equity settled)))
    (is (nil? (:volume settled)))
    (is (= [] (:timeseries settled)))
    (is (= {:maker 0.0001 :taker 0.0005} (:fee-rates settled)))
    (is (= :available (get-in settled [:field-status :fee-rates])))))

(deftest historical-fees-require-complete-nonempty-fill-evidence-test
  (let [history (assoc fixtures/account-history :userFills [])
        build (fn [user-fills]
                (analytics/build-portfolio-view-model
                 (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
                 (assoc history :userFills user-fills)
                 {:account fixtures/wallet-address :range :all}))]
    (doseq [[label user-fills] [[:empty []]
                                [:missing-fee [{:px 100 :sz 1}]]
                                [:mixed-fees [{:px 100 :sz 1 :fee 1}
                                              {:px 200 :sz 1}]]]]
      (testing (name label)
        (let [vm (build user-fills)]
          (is (= :live (:data-quality vm)))
          (is (nil? (:fees vm)))
          (is (nil? (:historical-fees vm)))
          (is (= :unavailable (get-in vm [:field-status :historical-fees]))))))))

(deftest provider-error-messages-never-expose-provider-payloads-or-secrets-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        options {:account fixtures/wallet-address :range :all}
        build #(analytics/build-portfolio-view-model tenant % options)
        benign (build {:status :error :error "provider timed out"})
        hostile (build (assoc fixtures/account-history
                              :status :error
                              :error "api_secret=not-for-display access_token=also-not-for-display 0x1111111111111111111111111111111111111111"
                              :freshness {:fetched-at-ms 1000
                                          :now-ms 1700007300000
                                          :max-age-ms 3600000}))
        raw-payload (build {:status :error
                            :error {:api_secret "not-for-display"
                                    :token "also-not-for-display"
                                    :raw-response {:reason "provider failure"}}})]
    (is (= :provider-error (:data-quality benign)))
    (is (= "provider timed out" (:message benign)))
    (is (= :stale (:data-quality hostile)))
    (is (= "Showing the last known portfolio result while refresh is unavailable"
           (:message hostile)))
    (is (= "Portfolio provider is unavailable" (:message raw-payload)))
    (doseq [vm [hostile raw-payload]
            forbidden ["api_secret" "access_token" "token" "not-for-display"
                       "0x1111111111111111111111111111111111111111"]]
      (is (not (str/includes? (:message vm) forbidden))))))

(deftest provider-error-messages-reject-sensitive-authentication-headers-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        options {:account fixtures/wallet-address :range :all}
        build #(analytics/build-portfolio-view-model tenant % options)
        authorization (build {:status :error
                              :error "Authorization: Bearer do-not-display"})
        api-key (build {:status :error
                        :error "x-api-key: do-not-display"})
        session (build {:status :error
                        :error "X-Session: do-not-display"})]
    (doseq [vm [authorization api-key session]
            forbidden ["Authorization" "Bearer" "x-api-key" "X-Session" "do-not-display"]]
      (is (= :provider-error (:data-quality vm)))
      (is (= "Portfolio provider is unavailable" (:message vm)))
      (is (not (str/includes? (:message vm) forbidden))))))

(deftest unavailable-current-fee-rates-make-retained-portfolio-data-partial-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        options {:account fixtures/wallet-address :range :all}
        build (fn [fee-rates-state]
                (analytics/build-portfolio-view-model
                 tenant
                 (assoc fixtures/account-history
                        :fee-rates {:maker 0.0001 :taker 0.0005}
                        :fee-rates-state fee-rates-state)
                 options))]
    (doseq [fee-rates-state [{:loaded? true :error? true :fresh? false}
                             {:loaded? true :error? false :fresh? false}]]
      (let [vm (build fee-rates-state)]
        (is (= :partial (:data-quality vm)))
        (is (= 1050 (:equity vm)))
        (is (= 50 (:pnl vm)))
        (is (nil? (:fee-rates vm)))
        (is (= :stale (get-in vm [:field-status :fee-rates])))))))

(deftest future-freshness-timestamp-cannot-be-live-test
  (let [history (assoc fixtures/account-history
                       :freshness {:fetched-at-ms 1700000001000
                                   :now-ms 1700000000000
                                   :max-age-ms 3600000})
        vm (analytics/build-portfolio-view-model
            (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
            history
            {:account fixtures/wallet-address :range :all})]
    (is (not= :live (:data-quality vm)))))

(deftest cashflow-only-equity-drawdown-does-not-report-a-trading-loss-test
  (let [vm (analytics/build-portfolio-view-model
            (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
            fixtures/cashflow-only-drawdown-history
            {:account fixtures/wallet-address :range :all})]
    (is (= :live (:data-quality vm)))
    (is (= 0 (:pnl vm)))
    (is (= 0 (:return-pct vm)))
    (is (= 0 (:max-drawdown-pct vm)))))

(deftest non-positive-start-equity-does-not-fabricate-drawdown-test
  (let [vm (analytics/build-portfolio-view-model
            (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
            fixtures/non-positive-equity-history
            {:account fixtures/wallet-address :range :all})]
    (is (= :live (:data-quality vm)))
    (is (nil? (:max-drawdown-pct vm)))))

(deftest default-affiliate-can-be-explicitly-disabled-while-sample-is-configured-test
  (let [disabled (tenant-config/normalize-tenant-config fixtures/affiliate-disabled-tenant-raw)
        sample (tenant-config/normalize-tenant-config fixtures/alternate-tenant-raw)]
    (is (= "hyperopen-default-offline" (:tenant/id disabled)))
    (is (false? (get-in disabled [:features :affiliate])))
    (is (= :unavailable (get-in disabled [:affiliate :status])))
    (is (nil? (get-in disabled [:affiliate :id])))
    (is (= "desk-alpha-ref" (get-in sample [:affiliate :id])))
    (is (= :configured (get-in sample [:affiliate :status])))))

(deftest professional-analytics-keeps-current-fee-rates-separate-from-historical-fees-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        vm (analytics/build-portfolio-view-model
            tenant
            {:status :ready
             :source :provider
             :accountValueHistory [[1000 1000] [2000 1250] [3000 1200]]
             :pnlHistory [[1000 0] [2000 250] [3000 200]]
             :vlm 4321
             :userFills []
             :fee-rates {:maker 0.0001 :taker 0.0005}
             :freshness {:fetched-at-ms 3000
                         :now-ms 4000
                         :max-age-ms 1000}}
            {:account fixtures/wallet-address :range :month})]
    (is (= :live (:data-quality vm)))
    (is (= 1200 (:equity vm)))
    (is (= 200 (:pnl vm)))
    (is (= 20 (:return-pct vm)))
    (is (number? (:max-drawdown-pct vm)))
    (is (= 4321 (:volume vm)))
    (is (= {:maker 0.0001 :taker 0.0005} (:fee-rates vm)))
    (is (nil? (:historical-fees vm)))
    (is (= 3000 (:as-of-ms vm)))
    (is (= :available (get-in vm [:field-status :equity])))
    (is (= :unavailable (get-in vm [:field-status :historical-fees])))
    (is (not (contains? vm :userFills)))
    (is (not (contains? vm :raw-provider-response)))))

(deftest analytics-quality-vocabulary-is-closed-and-stale-retained-data-outranks-refresh-state-test
  (let [tenant (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        options {:account fixtures/wallet-address :range :all}
        live-history {:status :ready
                      :source :provider
                      :accountValueHistory [[1000 100] [2000 110]]
                      :pnlHistory [[1000 0] [2000 10]]
                      :userFills []
                      :freshness {:fetched-at-ms 2000 :now-ms 3000 :max-age-ms 1000}}
        cases [{:label :loading
                :history {:status :loading}
                :quality :loading}
               {:label :empty
                :history {:status :ready
                          :source :provider
                          :accountValueHistory []
                          :pnlHistory []
                          :userFills []
                          :freshness {:fetched-at-ms 2000 :now-ms 3000 :max-age-ms 1000}}
                :quality :empty}
               {:label :live
                :history live-history
                :quality :live}
               {:label :stale-error
                :history (assoc live-history
                                :status :error
                                :error "refresh unavailable"
                                :freshness {:fetched-at-ms 1000 :now-ms 3001 :max-age-ms 1000})
                :quality :stale}
               {:label :stale-loading
                :history (assoc live-history
                                :status :loading
                                :freshness {:fetched-at-ms 1000 :now-ms 3001 :max-age-ms 1000})
                :quality :stale}
               {:label :partial
                :history {:status :ready
                          :source :provider
                          :accountValueHistory [[2000 110]]
                          :pnlHistory [[2000 10]]
                          :freshness {:fetched-at-ms 2000 :now-ms 3000 :max-age-ms 1000}}
                :quality :partial}
               {:label :provider-error
                :history {:status :error :error "provider unavailable"}
                :quality :provider-error}
               {:label :demo
                :history (assoc live-history :source :demo)
                :quality :demo}]]
    (doseq [{:keys [label history quality]} cases]
      (testing (name label)
        (let [vm (analytics/build-portfolio-view-model tenant history options)]
          (is (= quality (:data-quality vm)))
          (is (contains? #{:loading :empty :live :stale :partial :provider-error :demo}
                         (:data-quality vm))))))
    (let [stale (analytics/build-portfolio-view-model
                 tenant
                 (:history (nth cases 3))
                 options)
          unavailable (analytics/build-portfolio-view-model tenant live-history {:range :all})]
      (is (= 110 (:equity stale)))
      (is (= 2000 (:as-of-ms stale)))
      (is (string? (:message stale)))
      (is (= :unavailable (:data-quality unavailable)))
      (is (nil? (:equity unavailable)))
      (is (= :unavailable (get-in unavailable [:field-status :equity]))))))
