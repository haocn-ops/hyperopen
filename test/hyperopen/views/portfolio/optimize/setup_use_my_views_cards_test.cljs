(ns hyperopen.views.portfolio.optimize.setup-use-my-views-cards-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.setup-use-my-views-cards :as use-my-views-cards]))

(defn- node-children
  [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn- find-first-node
  [node pred]
  (cond
    (vector? node)
    (let [children (node-children node)]
      (or (when (pred node) node)
          (some #(find-first-node % pred) children)))

    (seq? node)
    (some #(find-first-node % pred) node)

    :else nil))

(defn- collect-strings
  [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings (node-children node))
    (seq? node) (mapcat collect-strings node)
    :else []))

(defn- node-by-role
  [node role]
  (find-first-node node #(= role (get-in % [1 :data-role]))))

(defn- node-text
  [node]
  (apply str (collect-strings node)))

(def ^:private btc-instrument
  {:instrument-id "perp:BTC"
   :market-type :perp
   :coin "BTC"
   :symbol "BTC-USDC"
   :name "Bitcoin"})

(def ^:private eth-instrument
  {:instrument-id "perp:ETH"
   :market-type :perp
   :coin "ETH"
   :symbol "ETH-USDC"
   :name "Ethereum"})

(defn- draft
  [instruments views]
  {:universe instruments
   :return-model {:kind :black-litterman
                  :views views}})

(defn- readiness
  [instruments views]
  {:request {:universe instruments
             :return-model {:views views}}})

(defn- output-card
  [view-node]
  (node-by-role view-node "portfolio-optimizer-setup-use-my-views-card-combined-output"))

(deftest combined-output-surfaces-dominated-prior-callout-test
  (let [views [{:id "view-1"
                :kind :absolute
                :instrument-id "perp:BTC"
                :return 0.2
                :confidence-level :high}]
        view-node (use-my-views-cards/cards
                   (draft [btc-instrument] views)
                   (readiness [btc-instrument] views)
                   {:status :ready
                    :view-count 1
                    :rows [{:instrument-id "perp:BTC"
                            :label "BTC"
                            :prior-return -0.216
                            :posterior-return 0.096}]})
        card (output-card view-node)
        output-text (node-text card)
        callout (node-by-role card
                              "portfolio-optimizer-setup-use-my-views-card-combined-output-callout")]
    (is (str/includes? output-text "-21.6%"))
    (is (str/includes? output-text "9.6%"))
    (is (str/includes? output-text "(+31.2)"))
    (is (some? callout))
    (is (= "note" (get-in callout [1 :role])))
    (is (str/includes? output-text
                       "BTC moved +31.2pp — your high view dominated the prior here."))))

(deftest combined-output-surfaces-low-confidence-callout-test
  (let [views [{:id "view-1"
                :kind :absolute
                :instrument-id "perp:BTC"
                :return 0.45
                :confidence-level :low}]
        view-node (use-my-views-cards/cards
                   (draft [btc-instrument] views)
                   (readiness [btc-instrument] views)
                   {:status :ready
                    :view-count 1
                    :rows [{:instrument-id "perp:BTC"
                            :label "BTC"
                            :prior-return 0.22
                            :posterior-return 0.261}]})
        output-text (node-text (output-card view-node))]
    (is (str/includes? output-text
                       "Low confidence on BTC means your +45% view only pulls posterior to 26.1%. Strong claims need strong confidence."))))

(deftest combined-output-sorts-material-moves-test
  (let [sol-instrument {:instrument-id "perp:SOL"
                        :market-type :perp
                        :coin "SOL"}
        instruments [btc-instrument eth-instrument sol-instrument]
        views [{:id "view-1"
                :kind :absolute
                :instrument-id "perp:ETH"
                :return 0.2
                :confidence-level :medium}
               {:id "view-2"
                :kind :absolute
                :instrument-id "perp:BTC"
                :return 0.1
                :confidence-level :medium}]
        view-node (use-my-views-cards/cards
                   (draft instruments views)
                   (readiness instruments views)
                   {:status :ready
                    :view-count 2
                    :rows [{:instrument-id "perp:BTC"
                            :label "BTC"
                            :prior-return 0.1
                            :posterior-return 0.12}
                           {:instrument-id "perp:ETH"
                            :label "ETH"
                            :prior-return 0.1
                            :posterior-return 0.18}
                           {:instrument-id "perp:SOL"
                            :label "SOL"
                            :prior-return 0.1
                            :posterior-return 0.1005}]})
        output-text (node-text (output-card view-node))
        eth-idx (str/index-of output-text "ETH")
        btc-idx (str/index-of output-text "BTC")]
    (is (number? eth-idx))
    (is (number? btc-idx))
    (is (< eth-idx btc-idx))
    (is (not (str/includes? output-text "SOL")))))

(deftest combined-output-falls-back-when-view-id-is-stale-test
  (let [view-node (use-my-views-cards/cards
                   (draft [btc-instrument]
                          [{:id "stale-view"
                            :kind :absolute
                            :instrument-id "perp:MISSING"
                            :return 0.2
                            :confidence 0.75}])
                   (readiness [btc-instrument]
                              [{:id "stale-view"
                                :kind :absolute
                                :instrument-id "perp:MISSING"
                                :return 0.2
                                :confidence 0.75}])
                   {:status :ready
                    :view-count 1
                    :rows [{:instrument-id "perp:BTC"
                            :label "BTC"
                            :prior-return 0.1
                            :posterior-return 0.12}]})
        output-text (node-text (output-card view-node))]
    (is (str/includes? output-text "BTC"))
    (is (str/includes? output-text "10.0%"))
    (is (str/includes? output-text "12.0%"))
    (is (not (str/includes? output-text
                            "Active views do not match the current model universe yet.")))))
