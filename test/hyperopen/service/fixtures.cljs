(ns hyperopen.service.fixtures)

(def default-tenant-raw
  {:tenant/id "hyperopen-default"
   :brand/name "Hyperopen"
   :brand/logo-url ""
   :theme/id "dark"
   :features {:terminal true
              :analytics true
              :affiliate true}
   :venue {:id :hyperliquid
           :label "Hyperliquid"
           :url "https://app.hyperliquid.xyz"}
   :affiliate {:provider :hyperliquid
               :id "hyperopen-official"
               :status :configured
               :referral-url "https://app.hyperliquid.xyz/?ref=hyperopen-official"
               :disclosure "交易通过官方合作链接归因；返佣状态以服务商确认为准。"}})

(def alternate-tenant-raw
  {:tenant/id "desk-alpha"
   :brand/name "Desk Alpha"
   :brand/logo-url "https://cdn.example.test/desk-alpha.svg"
   :theme/id "institutional"
   :features {:terminal true
              :analytics true
              :affiliate true
              :advanced-orders false}
   :venue {:id :hyperliquid
           :label "Hyperliquid Perps"
           :url "https://app.hyperliquid.xyz"}
   :affiliate {:provider :hyperliquid
               :id "desk-alpha-ref"
               :status :configured
               :referral-url "https://app.hyperliquid.xyz/?ref=desk-alpha-ref"
               :disclosure "Desk Alpha 使用官方合作链接；返佣状态以服务商确认为准。"}})

(def affiliate-disabled-tenant-raw
  (-> default-tenant-raw
      (assoc :tenant/id "hyperopen-default-offline")
      (assoc-in [:features :affiliate] false)
      (assoc :affiliate {:status :unavailable
                         :provider nil
                         :id nil
                         :referral-url ""
                         :disclosure "官方 affiliate 服务当前不可用；交易不受影响。"})))

(def malformed-tenant-raw
  {:tenant/id nil
   :brand/name 42
   :brand/logo-url "javascript:alert(1)"
   :theme/id :unknown-theme
   :features {:terminal "yes"
              :analytics nil
              :affiliate :enabled
              :unknown-feature true}
   :venue {:id nil
           :label 99
           :url "not-a-url"}
   :affiliate {:provider nil
               :id {:not "public"}
               :referral-url "not-a-url"
               :disclosure nil}})

(def secret-bearing-tenant-raw
  (assoc-in default-tenant-raw
            [:affiliate :metadata]
            {:api-secret "sk_live_do_not_store"
             :nested {:private-key "0xdeadbeef"
                      :seed-phrase "alpha beta gamma delta"}
             :access-token "token-do-not-store"}))

(def wallet-address "0x1111111111111111111111111111111111111111")

(def attribution-context
  {:tenant/id "hyperopen-default"
   :affiliate/id "hyperopen-official"
   :venue/id :hyperliquid
   :session/id "session-fixture-1"
   :wallet/address wallet-address
   :occurred-at-ms 1700000000000})

(def account-history
  {:account "0x1111111111111111111111111111111111111111"
   :status :ready
   :source :provider
   :accountValueHistory [[1700000000000 1000]
                         [1700003600000 1100]
                         [1700007200000 1050]]
   :pnlHistory [[1700000000000 0]
                [1700003600000 100]
                [1700007200000 50]]
   :userFills [{:time 1700003600000 :coin "BTC" :px "500" :sz "1" :fee "2"}
               {:time 1700007200000 :coin "BTC" :px "350" :sz "2" :fee "3"}]
   :freshness {:fetched-at-ms 1700007200000
               :now-ms 1700007300000
               :max-age-ms 3600000}})

(def pure-deposit-history
  {:account wallet-address
   :status :ready
   :source :provider
   :accountValueHistory [[1700000000000 1000]
                         [1700003600000 2000]]
   :pnlHistory [[1700000000000 0]
                [1700003600000 0]]
   :userFills []
   :freshness {:fetched-at-ms 1700003600000
               :now-ms 1700003700000
               :max-age-ms 3600000}})

(def cashflow-only-drawdown-history
  {:account wallet-address
   :status :ready
   :source :provider
   :accountValueHistory [[1700000000000 1000]
                         [1700003600000 500]
                         [1700007200000 1500]]
   :pnlHistory [[1700000000000 0]
                [1700003600000 0]
                [1700007200000 0]]
   :userFills []
   :freshness {:fetched-at-ms 1700007200000
               :now-ms 1700007300000
               :max-age-ms 3600000}})

(def non-positive-equity-history
  {:account wallet-address
   :status :ready
   :source :provider
   :accountValueHistory [[1700000000000 0]
                         [1700003600000 -500]
                         [1700007200000 100]]
   :pnlHistory [[1700000000000 0]
                [1700003600000 -500]
                [1700007200000 100]]
   :userFills []
   :freshness {:fetched-at-ms 1700007200000
               :now-ms 1700007300000
               :max-age-ms 3600000}})
