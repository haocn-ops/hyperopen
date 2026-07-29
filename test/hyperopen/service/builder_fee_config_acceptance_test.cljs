(ns hyperopen.service.builder-fee-config-acceptance-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.service.tenant-config :as tenant-config]))

(def builder-address "0x36a47878219fb346e031f6cf82cbfc8c77e35932")
(def disclosure
  "DEXHelm charges an additional 0.01% builder fee on eligible fills after you approve it.")

(def configured-builder-fee
  {:status :configured
   :builder-address builder-address
   :fee-tenths-bp 10
   :disclosure disclosure})

(def normalized-configured-builder-fee
  (assoc configured-builder-fee :max-fee-rate "0.01%"))

(def disabled-builder-fee
  {:status :disabled
   :builder-address nil
   :fee-tenths-bp nil
   :disclosure "No DEXHelm builder fee is active in this release."})

(deftest configured-runtime-tenant-projects-one-public-builder-fee-contract-test
  (let [tenant (tenant-config/normalize-tenant-config
                (assoc tenant-config/default-tenant-raw
                       :tenant/id "dexhelm"
                       :builder-fee configured-builder-fee))]
    (is (= normalized-configured-builder-fee (:builder-fee tenant)))
    (is (= "0.01%" (get-in tenant [:builder-fee :max-fee-rate])))
    (is (nil? (get-in tenant [:builder-fee :max-fee-rate-input])))
    (is (tenant-config/valid-tenant-config? tenant))))

(deftest disabled-runtime-tenant-keeps-builder-fee-unavailable-to-order-and-ui-consumers-test
  (let [tenant (tenant-config/normalize-tenant-config
                (assoc tenant-config/default-tenant-raw
                       :tenant/id "dexhelm"
                       :builder-fee disabled-builder-fee))]
    (is (= disabled-builder-fee (:builder-fee tenant)))
    (is (nil? (get-in tenant [:builder-fee :builder-address])))
    (is (nil? (get-in tenant [:builder-fee :fee-tenths-bp])))
    (is (false? (true? (get-in tenant [:builder-fee :active?]))))))
