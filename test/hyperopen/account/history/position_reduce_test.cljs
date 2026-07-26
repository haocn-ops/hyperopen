(ns hyperopen.account.history.position-reduce-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.history.actions :as history-actions]
            [hyperopen.account.history.position-reduce :as position-reduce]))

(deftest position-reduce-opens-and-submits-outcome-side-sell-order-test
  (let [row {:key "outcome-#10"
             :title "BTC above 78213 on May 4 at 2:00 AM?"
             :market-key "outcome:1"
             :raw-coin "+10"
             :side-coin "#10"
             :side-name "Yes"
             :side-index 0
             :type-label "Outcome"
             :size 17
             :mark-price 0.58793
             :entry-price 0.604
             :quote "USDH"}
        market {:key "outcome:1"
                :coin "#10"
                :market-type :outcome
                :asset-id 100000010
                :mark 0.58793
                :outcome-sides [{:side-index 0
                                 :side-name "Yes"
                                 :coin "#10"
                                 :asset-id 100000010
                                 :mark 0.58793}
                                {:side-index 1
                                 :side-name "No"
                                 :coin "#11"
                                 :asset-id 100000011
                                 :mark 0.41207}]}
        open-effects (history-actions/open-position-reduce-popover {:ui {:locale "en-US"}} row)
        opened-popover (get-in (first open-effects) [1 0 1])
        submit-popover (assoc opened-popover
                              :close-type :limit
                              :limit-price "0.59"
                              :size-percent-input "50")
        submit-effects (history-actions/submit-position-reduce-close
                        {:trading-settings {:confirm-close-position? false}
                         :positions-ui {:reduce-popover submit-popover}
                         :asset-selector {:market-by-key {"outcome:1" market}}})
        submitted-order (get-in submit-effects [1 1 :action :orders 0])]
    (is (= :effects/save-many
           (ffirst open-effects)))
    (is (true? (:open? opened-popover)))
    (is (= "outcome-#10" (:position-key opened-popover)))
    (is (= "#10" (:coin opened-popover)))
    (is (= :outcome (:market-type opened-popover)))
    (is (= "Yes" (:position-side-label opened-popover)))
    (is (= 17 (:position-size opened-popover)))
    (is (= "0.58793" (:mid-price opened-popover)))
    (is (= :effects/api-submit-order
           (first (second submit-effects))))
    (is (= 100000010 (:a submitted-order)))
    (is (= false (:b submitted-order)))
    (is (= false (:r submitted-order)))
    (is (= "8.5" (:s submitted-order)))
    (is (= "0.59" (:p submitted-order)))))

(deftest position-reduce-market-close-uses-orderbook-protection-price-for-short-test
  (let [row {:key "CFX"
             :position {:coin "CFX"
                        :szi "-526.0"
                        :entryPx "0.053943"
                        :positionValue "24.588396"
                        :unrealizedPnl "3.785622"
                        :returnOnEquity "0.6670930427"
                        :liquidationPx "0.5810973954"
                        :markPx 0.046731}
             :mark-price "0.046676"}
        popover (position-reduce/from-position-row row)
        result (position-reduce/prepare-submit
                {:asset-selector {:market-by-key {"perp:CFX"
                                                  {:coin "CFX"
                                                   :market-type :perp
                                                   :asset-id 21
                                                   :mark 0.046677
                                                   :szDecimals 0}}}
                 :orderbooks {"CFX" {:bids [{:px "0.046744" :sz "1500.0"}]
                                      :asks [{:px "0.046819" :sz "8078.0"}]}}}
                popover)
        submitted-order (get-in result [:request :action :orders 0])]
    (is (true? (:ok? result)))
    (is (= 21 (:a submitted-order)))
    (is (= true (:b submitted-order)))
    (is (= true (:r submitted-order)))
    (is (= "526" (:s submitted-order)))
    (is (= "Ioc" (get-in submitted-order [:t :limit :tif])))
    (is (= "0.049159" (:p submitted-order)))))

(deftest position-reduce-market-close-uses-mark-protection-price-without-target-orderbook-test
  (let [row {:key "CFX"
             :position {:coin "CFX"
                        :szi "-526.0"
                        :markPx 0.046731}
             :mark-price "0.046676"}
        popover (position-reduce/from-position-row row)
        result (position-reduce/prepare-submit
                {:asset-selector {:market-by-key {"perp:CFX"
                                                  {:coin "CFX"
                                                   :market-type :perp
                                                   :asset-id 21
                                                   :mark 0.046677
                                                   :szDecimals 0}}}
                 :orderbooks {"KAITO" {:bids [{:px "1.23" :sz "1"}]
                                        :asks [{:px "1.24" :sz "1"}]}}}
                popover)
        submitted-order (get-in result [:request :action :orders 0])]
    (is (true? (:ok? result)))
    (is (= 21 (:a submitted-order)))
    (is (= true (:b submitted-order)))
    (is (= true (:r submitted-order)))
    (is (= "526" (:s submitted-order)))
    (is (= "Ioc" (get-in submitted-order [:t :limit :tif])))
    (is (= "0.049067" (:p submitted-order)))))
