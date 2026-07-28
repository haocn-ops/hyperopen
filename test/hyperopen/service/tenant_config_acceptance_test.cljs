(ns hyperopen.service.tenant-config-acceptance-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.service.fixtures :as fixtures]
            [hyperopen.service.tenant-config :as tenant-config]
            [hyperopen.ui.theme :as ui-theme]))

(deftest default-tenant-normalizes-to-a-usable-public-config-test
  (let [normalized (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)]
    (is (tenant-config/valid-tenant-config? normalized))
    (is (= "hyperopen-default" (:tenant/id normalized)))
    (is (= "Hyperopen" (:brand/name normalized)))
    (is (string? (:brand/logo-url normalized)))
    (is (= "dark" (:theme/id normalized)))
    (is (every? #(true? (get-in normalized [:features %]))
                [:terminal :analytics :affiliate]))
    (is (= "Hyperliquid" (get-in normalized [:venue :label])))
    (is (= "hyperopen-official" (get-in normalized [:affiliate :id])))
    (is (string? (get-in normalized [:affiliate :disclosure])))
    (is (not (tenant-config/contains-secret? normalized)))
    (is (string? (tenant-config/canonical-serialize normalized)))))

(deftest alternate-tenant-is-configuration-driven-and-keeps-route-contract-test
  (let [default (tenant-config/normalize-tenant-config fixtures/default-tenant-raw)
        alternate (tenant-config/normalize-tenant-config fixtures/alternate-tenant-raw)]
    (is (tenant-config/valid-tenant-config? alternate))
    (is (= "desk-alpha" (:tenant/id alternate)))
    (is (= "Desk Alpha" (:brand/name alternate)))
    (is (not= (:brand/name default) (:brand/name alternate)))
    (is (not= (:theme/id default) (:theme/id alternate)))
    (is (not= (get-in default [:affiliate :id])
              (get-in alternate [:affiliate :id])))
    (is (= ["/trade" "/portfolio"]
           (tenant-config/enabled-routes alternate)))
    (is (= alternate
           (tenant-config/active-tenant-config {:tenant/override fixtures/alternate-tenant-raw})))))

(deftest missing-override-resolves-to-default-tenant-test
  (let [expected (tenant-config/normalize-tenant-config tenant-config/default-tenant-raw)]
    (is (= expected
           (tenant-config/active-tenant-config {:tenant/override nil})))
    (is (= expected
           (tenant-config/active-tenant-config {})))))

(deftest tenant-theme-ids-use-the-real-ui-theme-catalog-test
  (doseq [{:keys [id]} ui-theme/themes]
    (let [raw (assoc tenant-config/default-tenant-raw :theme/id id)
          normalized (tenant-config/normalize-tenant-config raw)]
      (is (= id (:theme/id normalized)))))
  (is (= ui-theme/default-theme-id
         (:theme/id
          (tenant-config/normalize-tenant-config
           (assoc tenant-config/default-tenant-raw :theme/id "midnight"))))))

(deftest enabled-affiliate-event-endpoints-normalize-to-a-canonical-href-test
  (let [raw (-> fixtures/default-tenant-raw
                (assoc-in [:affiliate :status] :enabled)
                (assoc-in [:affiliate :event-endpoint]
                          " HTTPS://EVENTS.Example.COM:443/a/../collect?campaign=one "))
        normalized (tenant-config/normalize-tenant-config raw)]
    (is (= "https://events.example.com/collect?campaign=one"
           (get-in normalized [:affiliate :event-endpoint])))
    (is (= "https://events.example.com/collect?campaign=one"
           (tenant-config/normalize-affiliate-event-endpoint
            "HTTPS://EVENTS.Example.COM:443/a/../collect?campaign=one")))
    (doseq [endpoint ["http://events.example.com/collect"
                      "https://user:pass@events.example.com/collect"
                      "https://events.example.com/collect#fragment"
                      "https://events.example.com:444/collect"]]
      (is (nil? (tenant-config/normalize-affiliate-event-endpoint endpoint)))))
  (is (= ""
         (get-in (tenant-config/normalize-tenant-config
                  fixtures/affiliate-disabled-tenant-raw)
                 [:affiliate :event-endpoint]))))
