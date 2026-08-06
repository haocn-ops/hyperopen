(ns hyperopen.service.product-context-view-model-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.config :as app-config]
            [hyperopen.service.fixtures :as fixtures]
            [hyperopen.service.product-context :as product-context]
            [hyperopen.service.tenant-config :as tenant-config]))

(def alternate-config
  (assoc-in fixtures/alternate-tenant-raw
            [:affiliate :status]
            :configured))

(deftest default-tenant-product-context-exposes-branded-labels-test
  (let [vm (product-context/build-product-context-view-model
            tenant-config/default-tenant-raw)]
    (is (= "hyperopen-default" (:tenant/id vm)))
    (is (= "Hyperopen" (get-in vm [:brand :name])))
    (is (string? (get-in vm [:brand :logo-url])))
    (is (= "dark" (get-in vm [:brand :theme-id])))
    (is (= "Hyperliquid" (get-in vm [:venue :label])))
    (is (= (get-in app-config/config [:hyperliquid :hyperliquid-chain])
           (:network-label vm)))
    (is (= :unavailable (get-in vm [:affiliate :status])))
    (is (false? (get-in vm [:affiliate :enabled?])))
    (is (= "官方 affiliate 服务当前不可用；交易不受影响。"
           (get-in vm [:affiliate :disclosure])))
    (is (string? (:brand-label vm)))
    (is (string? (:venue-label vm)))
    (is (string? (:affiliate-disclosure vm)))))

(deftest alternate-tenant-product-context-keeps-the-same-shape-with-configured-affiliate-test
  (let [vm (product-context/build-product-context-view-model
            alternate-config)]
    (is (= "desk-alpha" (:tenant/id vm)))
    (is (= "Desk Alpha" (get-in vm [:brand :name])))
    (is (= "https://cdn.example.test/desk-alpha.svg"
           (get-in vm [:brand :logo-url])))
    (is (= "institutional" (get-in vm [:brand :theme-id])))
    (is (= "Hyperliquid Perps" (get-in vm [:venue :label])))
    (is (= :configured (get-in vm [:affiliate :status])))
    (is (true? (get-in vm [:affiliate :enabled?])))
    (is (= "desk-alpha-ref" (get-in vm [:affiliate :id])))
    (is (= "Desk Alpha 使用官方合作链接；返佣状态以服务商确认为准。"
           (get-in vm [:affiliate :disclosure])))
    (is (= (:brand-label vm) (get-in vm [:brand :name])))
    (is (= (:venue-label vm) (get-in vm [:venue :label])))
    (is (= (:affiliate-disclosure vm)
           (get-in vm [:affiliate :disclosure])))))
