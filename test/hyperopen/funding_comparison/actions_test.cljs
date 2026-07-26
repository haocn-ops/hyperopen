(ns hyperopen.funding-comparison.actions-test
  (:require [cljs.test :refer-macros [are deftest is]]
            [hyperopen.funding-comparison.actions :as actions]))

(deftest normalize-route-path-strips-query-fragment-whitespace-and-extra-slashes-test
  (are [path expected] (= expected (actions/normalize-route-path path))
    " /fundingComparison///?coin=BTC#top " "/fundingComparison"
    "/funding-comparison/#table" "/funding-comparison"
    "/" "/"
    nil ""))

(deftest parse-funding-comparison-route-supports-hyphen-and-camel-paths-test
  (is (= :page (:kind (actions/parse-funding-comparison-route "/funding-comparison"))))
  (is (= :page (:kind (actions/parse-funding-comparison-route "/fundingComparison?coin=BTC"))))
  (is (= {:kind :page
          :path "/FundingComparison"}
         (actions/parse-funding-comparison-route " /FundingComparison/// ")))
  (is (= :other (:kind (actions/parse-funding-comparison-route "/trade")))))

(deftest funding-comparison-route-predicate-follows-normalized-route-parser-test
  (is (true? (actions/funding-comparison-route? "/fundingcomparison/")))
  (is (false? (actions/funding-comparison-route? "/funding-comparison/stats"))))

(deftest load-funding-comparison-always-emits-loading-before-fetch-test
  (is (= [[:effects/save [:funding-comparison-ui :loading?] true]
          [:effects/api-fetch-predicted-fundings]]
         (actions/load-funding-comparison {}))))

(deftest load-funding-comparison-route-emits-projection-before-heavy-effects-test
  (is (= [[:effects/save [:funding-comparison-ui :loading?] true]
          [:effects/api-fetch-predicted-fundings]
          [:effects/fetch-asset-selector-markets {:phase :full}]]
         (actions/load-funding-comparison-route
          {:asset-selector {:markets []}}
          "/funding-comparison")))
  (is (= [[:effects/save [:funding-comparison-ui :loading?] true]
          [:effects/api-fetch-predicted-fundings]]
         (actions/load-funding-comparison-route
          {:asset-selector {:markets [{:coin "BTC"}]}}
          "/funding-comparison")))
  (is (= [[:effects/save [:funding-comparison-ui :loading?] true]
          [:effects/api-fetch-predicted-fundings]
          [:effects/fetch-asset-selector-markets {:phase :full}]]
         (actions/load-funding-comparison-route
          {}
          "/fundingComparison")))
  (is (= []
         (actions/load-funding-comparison-route
          {:asset-selector {:markets []}}
          "/trade"))))

(deftest set-funding-comparison-query-stringifies-nil-and-non-string-values-test
  (is (= [[:effects/save [:funding-comparison-ui :query] ""]]
         (actions/set-funding-comparison-query {} nil)))
  (is (= [[:effects/save [:funding-comparison-ui :query] "42"]]
         (actions/set-funding-comparison-query {} 42))))

(deftest normalize-funding-comparison-timeframe-supports-string-and-keyword-aliases-test
  (are [value expected] (= expected (actions/normalize-funding-comparison-timeframe value))
    :hour :hour
    " hourly " :hour
    "eight-hours" :8hour
    "8-hours" :8hour
    :week :week
    "YEAR" :year
    :unknown actions/default-timeframe))

(deftest set-funding-comparison-timeframe-normalizes-synonyms-test
  (is (= [[:effects/save [:funding-comparison-ui :timeframe] :8hour]]
         (actions/set-funding-comparison-timeframe {} "8h")))
  (is (= [[:effects/save [:funding-comparison-ui :timeframe] :day]]
         (actions/set-funding-comparison-timeframe {} :day)))
  (is (= [[:effects/save [:funding-comparison-ui :timeframe] :8hour]]
         (actions/set-funding-comparison-timeframe {} :unknown))))

(deftest normalize-funding-comparison-sort-column-supports-aliases-and-defaults-test
  (are [value expected] (= expected (actions/normalize-funding-comparison-sort-column value))
    :coin :coin
    " symbol " :coin
    "HL-perp" :hyperliquid
    "binperp" :binance
    "bybitPerp" :bybit
    "openinterest" :open-interest
    "binanceHLarb" :binance-hl-arb
    "bybit hL arb" :bybit-hl-arb
    :unknown actions/default-sort-column))

(deftest normalize-funding-comparison-sort-direction-supports-strings-and-defaults-test
  (are [value expected] (= expected (actions/normalize-funding-comparison-sort-direction value))
    :asc :asc
    " desc " :desc
    "ASC" :asc
    nil actions/default-sort-direction
    :sideways actions/default-sort-direction))

(deftest set-funding-comparison-sort-toggles-direction-for-same-column-test
  (is (= [[:effects/save [:funding-comparison-ui :sort]
           {:column :coin :direction :asc}]]
         (actions/set-funding-comparison-sort
          {:funding-comparison-ui {:sort {:column :coin :direction :desc}}}
          :coin)))
  (is (= [[:effects/save [:funding-comparison-ui :sort]
           {:column :open-interest :direction :desc}]]
         (actions/set-funding-comparison-sort
          {:funding-comparison-ui {:sort {:column :open-interest :direction :asc}}}
          :open-interest)))
  (is (= [[:effects/save [:funding-comparison-ui :sort]
           {:column :open-interest :direction :desc}]]
         (actions/set-funding-comparison-sort
          {:funding-comparison-ui {:sort {:column :coin :direction :asc}}}
          :open-interest))))

(deftest set-funding-comparison-sort-normalizes-current-state-and-new-column-aliases-test
  (is (= [[:effects/save [:funding-comparison-ui :sort]
           {:column :open-interest :direction :asc}]]
         (actions/set-funding-comparison-sort
          {:funding-comparison-ui {:sort {:column "openinterest"
                                          :direction "descending"}}}
          :open-interest)))
  (is (= [[:effects/save [:funding-comparison-ui :sort]
           {:column :coin :direction :asc}]]
         (actions/set-funding-comparison-sort {} "symbol")))
  (is (= [[:effects/save [:funding-comparison-ui :sort]
           {:column :hyperliquid :direction :desc}]]
         (actions/set-funding-comparison-sort {} "hl"))))
