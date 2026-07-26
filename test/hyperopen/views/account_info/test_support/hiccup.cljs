(ns hyperopen.views.account-info.test-support.hiccup
  (:require [clojure.string :as str]
            [hyperopen.test-support.hiccup :as hiccup]))

(def node-class-set hiccup/node-class-set)

(def node-children hiccup/node-children)

(def direct-texts hiccup/direct-texts)

(def collect-strings hiccup/collect-strings)

(def find-first-node hiccup/find-first-node)

(def find-by-data-role hiccup/find-by-data-role)

(def find-all-nodes hiccup/find-all-nodes)

(def count-nodes hiccup/count-nodes)

(defn tab-header-node [tab-content]
  (first (vec (node-children tab-content))))

(defn tab-rows-viewport-node [tab-content]
  (second (vec (node-children tab-content))))

(defn first-viewport-row [tab-content]
  (-> tab-content tab-rows-viewport-node node-children first))

(defn- balance-row-coin [row-node]
  (let [coin-cell (first (vec (node-children row-node)))]
    (first (remove str/blank? (collect-strings coin-cell)))))

(defn balance-tab-coins [tab-content]
  (->> (node-children (tab-rows-viewport-node tab-content))
       (map balance-row-coin)
       vec))

(defn balance-row-contract-cell [row-node]
  (nth (vec (node-children row-node)) 8))
