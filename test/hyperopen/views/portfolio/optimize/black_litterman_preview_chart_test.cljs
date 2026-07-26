(ns hyperopen.views.portfolio.optimize.black-litterman-preview-chart-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.portfolio.optimize.black-litterman-preview-chart :as preview-chart]))

(defn- ready-preview-readiness
  []
  {:status :ready
   :request {:universe [{:instrument-id "perp:BTC"
                         :market-type :perp
                         :coin "BTC"}
                        {:instrument-id "perp:ETH"
                         :market-type :perp
                         :coin "ETH"}]
             :return-model {:kind :black-litterman
                            :views [{:id "view-1"
                                     :kind :relative
                                     :instrument-id "perp:BTC"
                                     :comparator-instrument-id "perp:ETH"
                                     :direction :outperform
                                     :return 0.1
                                     :confidence 0.5
                                     :confidence-variance 1
                                     :weights {"perp:BTC" 1
                                               "perp:ETH" -1}}]}
             :risk-model {:kind :sample-covariance}
             :periods-per-year 10
             :history {:return-series-by-instrument
                       {"perp:BTC" [0.01 0.03 0.02]
                        "perp:ETH" [0.04 0.01 0.04]}}
             :black-litterman-prior
             {:source :market-cap
              :weights-by-instrument {"perp:BTC" 0.6
                                      "perp:ETH" 0.4}}}})

(defn- ready-preview-panel
  ([] (preview-chart/black-litterman-preview-panel
       (ready-preview-readiness)))
  ([opts]
   (preview-chart/black-litterman-preview-panel
    (ready-preview-readiness)
    opts)))

(deftest black-litterman-preview-chart-renders-vertical-grouped-bars-test
  (let [panel (ready-preview-panel)
        chart (hiccup/find-by-data-role
               panel
               "portfolio-optimizer-black-litterman-preview-svg")
        btc-prior (hiccup/find-by-data-role
                   panel
                   "portfolio-optimizer-black-litterman-preview-bar-prior-perp:BTC")
        btc-posterior (hiccup/find-by-data-role
                       panel
                       "portfolio-optimizer-black-litterman-preview-bar-posterior-perp:BTC")
        text (hiccup/node-text panel)]
    (is (some? chart))
    (is (= :svg (first chart)))
    (is (str/includes? text "Expected return per asset"))
    (doseq [bar [btc-prior btc-posterior]]
      (is (= :rect (first bar)))
      (is (number? (get-in bar [1 :x])))
      (is (number? (get-in bar [1 :y])))
      (is (number? (get-in bar [1 :height])))
      (is (pos? (get-in bar [1 :height]))))
    (is (= (get-in btc-prior [1 :width])
           (get-in btc-posterior [1 :width])))
    (is (not= (get-in btc-prior [1 :x])
              (get-in btc-posterior [1 :x])))))

(deftest black-litterman-preview-chart-renders-key-inside-chart-bottom-test
  (let [panel (ready-preview-panel)
        chart (hiccup/find-by-data-role
               panel
               "portfolio-optimizer-black-litterman-preview-svg")
        legend (hiccup/find-by-data-role
                chart
                "portfolio-optimizer-black-litterman-preview-legend")
        legend-text (hiccup/node-text legend)]
    (is (some? legend))
    (is (= :g (first legend)))
    (is (= "translate(0 296)" (get-in legend [1 :transform])))
    (is (= "translate(232 0)" (get-in legend [2 1 :transform])))
    (is (= "translate(552 0)" (get-in legend [3 1 :transform])))
    (is (str/includes? legend-text "Market reference"))
    (is (str/includes? legend-text "(prior)"))
    (is (str/includes? legend-text "Combined output"))
    (is (str/includes? legend-text "(posterior)"))))

(deftest black-litterman-preview-chart-supports-external-legend-layout-test
  (let [panel (ready-preview-panel {:legend-layout :external})
        chart (hiccup/find-by-data-role
               panel
               "portfolio-optimizer-black-litterman-preview-svg")
        legend (hiccup/find-by-data-role
                chart
                "portfolio-optimizer-black-litterman-preview-legend")]
    (is (some? chart))
    (is (nil? legend))
    (is (str/includes? (hiccup/node-text panel)
                       "Market reference vs combined output"))))

(deftest black-litterman-preview-chart-renders-vault-name-instead-of-address-test
  (let [vault-address "0x3333333333333333333333333333333333333333"
        vault-id (str "vault:" vault-address)
        panel (preview-chart/black-litterman-preview-panel
               {:status :ready
                :request {:universe [{:instrument-id "perp:BTC"
                                      :market-type :perp
                                      :coin "BTC"}
                                     {:instrument-id vault-id
                                      :market-type :vault
                                      :coin vault-id
                                      :vault-address vault-address
                                      :name "Alpha Yield"}]
                          :return-model {:kind :black-litterman
                                         :views [{:id "view-1"
                                                  :kind :absolute
                                                  :instrument-id vault-id
                                                  :return 0.04
                                                  :confidence 0.8
                                                  :weights {vault-id 1}}]}
                          :risk-model {:kind :sample-covariance}
                          :periods-per-year 10
                          :history {:return-series-by-instrument
                                    {"perp:BTC" [0.01 0.02 -0.01 0.03]
                                     vault-id [0.02 -0.01 0.04 0.01]}}
                          :black-litterman-prior
                          {:source :market-cap
                           :weights-by-instrument {"perp:BTC" 0.5
                                                   vault-id 0.5}}}})
        text (hiccup/node-text panel)]
    (is (str/includes? text "Alpha Yield"))
    (is (not (str/includes? text vault-id)))))
