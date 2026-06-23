(ns hyperopen.views.portfolio.optimize.result-vault-labels-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]
            [hyperopen.views.portfolio.optimize.rebalance-tab :as rebalance-tab]
            [hyperopen.views.portfolio.optimize.results-panel :as results-panel]))

(defn- node-children
  [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn- collect-strings
  [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings (node-children node))
    (seq? node) (mapcat collect-strings node)
    :else []))

(defn- node-text
  [node]
  (apply str (collect-strings node)))

(defn- find-first-node
  [node pred]
  (cond
    (vector? node)
    (or (when (pred node) node)
        (some #(find-first-node % pred) (node-children node)))

    (seq? node)
    (some #(find-first-node % pred) node)

    :else nil))

(defn- node-by-role
  [node role]
  (find-first-node node #(= role (get-in % [1 :data-role]))))

(defn- solved-result
  [vault-id vault-label]
  (fixtures/sample-solved-result
   {:instrument-ids ["perp:BTC" vault-id]
    :current-weights [0.0 0.0]
    :target-weights [0.5 0.5]
    :labels-by-instrument {"perp:BTC" "BTC"
                           vault-id vault-label}
    :expected-return 0.12
    :volatility 0.24
    :diagnostics {:gross-exposure 1
                  :net-exposure 1
                  :effective-n 2
                  :turnover 0.5
                  :covariance-conditioning {:status :ok}
                  :weight-sensitivity-by-instrument
                  {vault-id {:base-expected-return 0.12
                             :down-expected-return 0.1
                             :up-expected-return 0.14}}
                  :binding-constraints [{:instrument-id vault-id
                                         :constraint :upper-bound}]}
    :rebalance-preview {:status :partially-blocked
                        :capital-usd 10000
                        :summary {:ready-count 0
                                  :blocked-count 1
                                  :gross-trade-notional-usd 1200
                                  :estimated-fees-usd 0
                                  :estimated-slippage-usd 0}
                        :rows [{:instrument-id vault-id
                                :status :blocked
                                :reason :vault-submit-unsupported
                                :side :sell
                                :delta-notional-usd -1200}]}
    :frontier [{:id 0
                :expected-return 0.12
                :volatility 0.24
                :sharpe 0.5
                :weights [0.5 0.5]}]
    :warnings [{:code :sparse-history-risk-estimation
                :instrument-id vault-id
                :message (str vault-id
                              ": sparse history uses mixed-frequency covariance.")}
               {:code :insufficient-pairwise-history
                :left-instrument-id "perp:BTC"
                :right-instrument-id vault-id
                :instrument-ids ["perp:BTC" vault-id]
                :message (str "perp:BTC / " vault-id
                              ": mixed-frequency covariance only had 1 shared interval; 2 required.")}]}))

(deftest results-panel-renders-vault-result-labels-by-name-test
  (let [vault-address "0x1e37a337ed460039d1b15bd3bc489de789768d5e"
        vault-id (str "vault:" vault-address)
        view-node (results-panel/results-panel
                   {:result (solved-result vault-id "HLP Vault")
                    :computed-at-ms 2600}
                   {:objective {:kind :target-volatility}}
                   {:frontier-overlay-mode :none})
        diamond (node-by-role view-node
                              "portfolio-optimizer-target-exposure-vault-diamond-HLP-Vault")
        text (node-text view-node)]
    (is (str/includes? text "HLP Vault"))
    (is (some? diamond))
    (is (not (str/includes? text vault-id)))
    (is (not (str/includes? text vault-address)))))

(deftest rebalance-tab-renders-vault-result-labels-by-name-test
  (let [vault-address "0x4dec0a851849056e259128464ef28ce78afa27f6"
        vault-id (str "vault:" vault-address)
        view-node (rebalance-tab/rebalance-tab
                   {:result (solved-result vault-id "Growi Vault")})
        text (node-text view-node)]
    (is (str/includes? text "Growi Vault"))
    (is (not (str/includes? text vault-id)))
    (is (not (str/includes? text vault-address)))))

(deftest rebalance-tab-buys-sells-exclude-blocked-rows-test
  ;; A blocked sell (e.g. held spot) must not inflate the Buys/Sells headline; the
  ;; tradeable buy is the only thing counted, keeping Sells at zero and consistent
  ;; with the ready-only Gross trade KPI. Held spot also gets a friendlier note.
  (let [view-node (rebalance-tab/rebalance-tab
                   {:result
                    (fixtures/sample-solved-result
                     {:labels-by-instrument {"perp:BTC" "BTC"
                                             "spot:PURR" "PURR"}
                      :rebalance-preview
                      {:status :partially-blocked
                       :capital-usd 10000
                       :summary {:ready-count 1
                                 :blocked-count 1
                                 :gross-trade-notional-usd 300
                                 :estimated-fees-usd 0
                                 :estimated-slippage-usd 0}
                       :rows [{:instrument-id "perp:BTC"
                               :instrument-type :perp
                               :coin "BTC"
                               :status :ready
                               :side :buy
                               :quantity 3
                               :price 100
                               :delta-notional-usd 300
                               :cost {:source :snapshot
                                      :estimated-slippage-usd 0
                                      :slippage-bps 0
                                      :depth-status :full-visible-depth}}
                              {:instrument-id "spot:PURR"
                               :status :blocked
                               :reason :spot-submit-unsupported
                               :side :sell
                               :delta-notional-usd -50000}]}})})
        buys-text (node-text (node-by-role view-node
                                           "portfolio-optimizer-rebalance-kpi-buys"))
        sells-text (node-text (node-by-role view-node
                                            "portfolio-optimizer-rebalance-kpi-sells"))
        text (node-text view-node)]
    (is (str/includes? buys-text "300"))
    ;; Blocked sell notional is excluded -> Sells is zero, not 50,000.
    (is (not (str/includes? sells-text "50,000")))
    (is (str/includes? sells-text "$0"))
    ;; Held spot renders with a manage-manually note rather than a raw keyword.
    (is (str/includes? text "spot · manage manually"))))

(deftest rebalance-tab-renders-slippage-source-bps-and-freshness-test
  (let [view-node (rebalance-tab/rebalance-tab
                   {:result
                    (fixtures/sample-solved-result
                     {:labels-by-instrument {"perp:BTC" "BTC"}
                      :rebalance-preview
                      {:status :ready
                       :capital-usd 300
                       :summary {:ready-count 1
                                 :blocked-count 0
                                 :gross-trade-notional-usd 300
                                 :estimated-fees-usd 0
                                 :estimated-slippage-usd 5}
                       :rows [{:instrument-id "perp:BTC"
                               :instrument-type :perp
                               :coin "BTC"
                               :status :ready
                               :side :buy
                               :quantity 3
                               :price 100
                               :delta-notional-usd 300
                               :cost {:source :snapshot
                                      :estimated-slippage-usd 5
                                      :slippage-bps 166.66666666666669
                                      :age-ms 1000
                                      :depth-status :full-visible-depth}}]}})})
        text (node-text view-node)]
    (is (str/includes? text "snapshot"))
    (is (str/includes? text "1 snapshot"))
    (is (str/includes? text "max age 1s old"))
    (is (str/includes? text "166.67 bps"))
    (is (str/includes? text "1s old"))
    (is (str/includes? text "full depth"))))
