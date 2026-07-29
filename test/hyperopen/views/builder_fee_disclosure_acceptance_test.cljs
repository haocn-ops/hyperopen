(ns hyperopen.views.builder-fee-disclosure-acceptance-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.config :as app-config]
            [hyperopen.service.tenant-config :as tenant-config]
            [hyperopen.views.header.vm :as header-vm]
            [hyperopen.views.trade.order-form-summary-display :as summary-display]))

(def builder "0x36a47878219fb346e031f6cf82cbfc8c77e35932")
(def owner "0x1111111111111111111111111111111111111111")
(def configured-tenant
  (assoc tenant-config/default-tenant-raw
         :tenant/id "dexhelm"
         :builder-fee {:status :configured
                       :builder-address builder
                       :fee-tenths-bp 10
                       :disclosure "DEXHelm charges an additional 0.01% builder fee on eligible fills after you approve it."}))

(defn section-by-id [sections id]
  (some #(when (= id (:id %)) %) sections))

(defn builder-fee-row
  [state]
  (-> (get-in (header-vm/header-vm state) [:settings :sections])
      (section-by-id :builder-fee)
      :rows
      first))

(defn builder-fee-state
  [approval]
  {:tenant/override configured-tenant
   :header-ui {:settings-open? true}
   :wallet {:address owner}
   :builder-fee {:approval approval}})

(deftest configured-builder-fee-disclosure-keeps-the-charge-separate-from-exchange-maker-and-taker-rates-test
  (with-redefs [app-config/config {:hyperliquid {:network :testnet}}]
    (let [state (builder-fee-state {:status :ready
                                    :owner-address owner
                                    :builder-address builder
                                    :network :testnet
                                    :max-builder-fee 10})
          section (section-by-id (get-in (header-vm/header-vm state) [:settings :sections]) :builder-fee)
          row (builder-fee-row state)
          display (summary-display/summary-display
                   {:fees {:effective {:taker 0.045 :maker 0.015}
                           :baseline {:taker 0.05 :maker 0.02}}
                    :builder-fee {:active? true
                                  :builder-address builder
                                  :max-fee-rate "0.01%"}}
                   4)]
      (is (= "Builder fee" (:title section)))
      (is (= "Enabled" (:title row)))
      (is (true? (:disabled? row)))
      (is (some #(re-find #"0\.01%" (:hint %)) (:rows section)))
      (is (= "0.0450% / 0.0150%" (get-in display [:fees :effective])))
      (is (= "0.01% additional builder fee active" (:builder-fee display))))))

(deftest stale-or-insufficient-builder-fee-approvals-remain-reviewable-test
  (with-redefs [app-config/config {:hyperliquid {:network :testnet}}]
    (doseq [[label approval]
            [["different owner" {:status :ready
                                  :owner-address "0x2222222222222222222222222222222222222222"
                                  :builder-address builder
                                  :network :testnet
                                  :max-builder-fee 10}]
             ["different builder" {:status :ready
                                    :owner-address owner
                                    :builder-address "0x9999999999999999999999999999999999999999"
                                    :network :testnet
                                    :max-builder-fee 10}]
             ["different network" {:status :ready
                                   :owner-address owner
                                   :builder-address builder
                                   :network :mainnet
                                   :max-builder-fee 10}]
             ["insufficient maximum fee" {:status :ready
                                           :owner-address owner
                                           :builder-address builder
                                           :network :testnet
                                           :max-builder-fee 9}]
             ["stale loading approval" {:status :loading
                                         :owner-address owner
                                         :builder-address builder
                                         :network :testnet}]]]
      (testing label
        (let [row (builder-fee-row (builder-fee-state approval))]
          (is (= "Review and enable" (:title row)))
          (is (false? (:disabled? row))))))))

(deftest disabled-builder-fee-configuration-renders-no-review-control-or-active-fee-note-test
  (let [disabled (assoc-in configured-tenant [:builder-fee]
                           {:status :disabled
                            :builder-address nil
                            :fee-tenths-bp nil
                            :disclosure "No DEXHelm builder fee is active in this release."})
        vm (header-vm/header-vm {:tenant/override disabled
                                 :header-ui {:settings-open? true}})
        display (summary-display/summary-display {:fees {:taker 0.045 :maker 0.015}} 4)]
    (is (nil? (section-by-id (get-in vm [:settings :sections]) :builder-fee)))
    (is (nil? (:builder-fee display)))))
