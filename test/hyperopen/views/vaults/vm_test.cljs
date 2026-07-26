(ns hyperopen.views.vaults.vm-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.vaults.application.list-vm :as list-vm]
            [hyperopen.views.vaults.vm :as vm]))

(def facade-state
  {:wallet {:address "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"}
   :vaults-ui {:search-query ""
               :filter-leading? true
               :filter-deposited? true
               :filter-others? true
               :filter-closed? false
               :snapshot-range :month
               :user-vaults-page-size 10
               :user-vaults-page 1
               :sort {:column :tvl
                      :direction :desc}}
   :vaults {:loading {:index? false
                      :summaries? false}
            :errors {:index nil
                     :summaries nil}
            :user-equity-by-address {"0x2222222222222222222222222222222222222222" {:equity 25}}
            :merged-index-rows [{:name "Hyperliquidity Provider (HLP)"
                                 :vault-address "0x1111111111111111111111111111111111111111"
                                 :leader "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
                                 :tvl 120
                                 :apr 0.12
                                 :relationship {:type :parent}
                                 :is-closed? false
                                 :create-time-ms (- (.now js/Date) (* 3 24 60 60 1000))
                                 :snapshot-by-key {:month [0.1 0.2]}}
                                {:name "Beta"
                                 :vault-address "0x2222222222222222222222222222222222222222"
                                 :leader "0x3333333333333333333333333333333333333333"
                                 :tvl 80
                                 :apr 0.08
                                 :relationship {:type :normal}
                                 :is-closed? false
                                 :create-time-ms (- (.now js/Date) (* 8 24 60 60 1000))
                                 :snapshot-by-key {:month [0.05 0.09]}}
                                {:name "Gamma"
                                 :vault-address "0x3333333333333333333333333333333333333333"
                                 :leader "0x4444444444444444444444444444444444444444"
                                 :tvl 50
                                 :apr 0.03
                                 :relationship {:type :normal}
                                 :is-closed? false
                                 :create-time-ms (- (.now js/Date) (* 5 24 60 60 1000))
                                 :snapshot-by-key {:month [0.01 0.02]}}
                                {:name "Closed Vault"
                                 :vault-address "0x4444444444444444444444444444444444444444"
                                 :leader "0x5555555555555555555555555555555555555555"
                                 :tvl 999
                                 :apr 0.4
                                 :relationship {:type :normal}
                                 :is-closed? true
                                :create-time-ms (- (.now js/Date) (* 1 24 60 60 1000))
                                :snapshot-by-key {:month [0.03]}}
                                {:name "Gamma"
                                 :vault-address "0x3333333333333333333333333333333333333333"
                                 :leader "0x4444444444444444444444444444444444444444"
                                 :tvl 50
                                 :apr 0.03
                                 :relationship {:type :normal}
                                 :is-closed? false
                                 :create-time-ms 1699500000000
                                 :snapshot-by-key {:month [0.01 0.02]}}]}})

(deftest vaults-vm-facade-delegates-to-application-list-vm-test
  (is (= (list-vm/vault-list-vm facade-state {:now-ms 1700000000000})
         (vm/vault-list-vm facade-state {:now-ms 1700000000000})))
  (is (= (list-vm/build-startup-preview-record facade-state {:now-ms 1700000000000})
         (vm/build-startup-preview-record facade-state {:now-ms 1700000000000}))))
