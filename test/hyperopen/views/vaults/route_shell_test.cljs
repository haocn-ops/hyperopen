(ns hyperopen.views.vaults.route-shell-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.vaults.route-shell :as route-shell]))

(def ^:private preview-empty-message
  "No cached vaults available.")

(def ^:private sample-row
  {:name "Alpha Vault"
   :vault-address "0x1111111111111111111111111111111111111111"
   :leader "0x2222222222222222222222222222222222222222"
   :apr 12.34
   :tvl 1000000
   :your-deposit 2500
   :age-days 3
   :snapshot-series [1 2 3]})

(def ^:private second-row
  {:name "Beta Vault"
   :vault-address "0x3333333333333333333333333333333333333333"
   :leader "0x4444444444444444444444444444444444444444"
   :apr -8.76
   :tvl 345678.9
   :your-deposit 100
   :age-days 11
   :snapshot-series [3 2 1]})

(deftest preview-section-renders-desktop-table-rows-test
  (let [view (@#'route-shell/preview-section "Protocol Vaults" [sample-row second-row] true)
        table (hiccup/find-first-node view #(= :table (first %)))
        row-nodes (hiccup/find-all-nodes view #(= "vault-route-shell-row" (get-in % [1 :data-role])))
        strings (set (hiccup/collect-strings view))]
    (is (some? table))
    (is (= 2 (count row-nodes)))
    (is (contains? strings "Protocol Vaults"))
    (is (contains? strings "Alpha Vault"))
    (is (contains? strings "Beta Vault"))
    (is (not (contains? strings preview-empty-message)))))

(deftest preview-section-renders-desktop-empty-row-test
  (let [view (@#'route-shell/preview-section "Protocol Vaults" [] true)
        empty-cell (hiccup/find-first-node view #(and (= :td (first %))
                                                      (= 7 (get-in % [1 :col-span]))))
        row-node (hiccup/find-by-data-role view "vault-route-shell-row")]
    (is (some? empty-cell))
    (is (= #{preview-empty-message}
           (set (hiccup/collect-strings empty-cell))))
    (is (nil? row-node))))

(deftest preview-section-renders-mobile-cards-test
  (let [view (@#'route-shell/preview-section "User Vaults" [sample-row second-row] false)
        table (hiccup/find-first-node view #(= :table (first %)))
        row-nodes (hiccup/find-all-nodes view #(= "vault-route-shell-row" (get-in % [1 :data-role])))
        strings (set (hiccup/collect-strings view))]
    (is (nil? table))
    (is (= 2 (count row-nodes)))
    (is (contains? strings "User Vaults"))
    (is (contains? strings "Alpha Vault"))
    (is (contains? strings "Beta Vault"))
    (is (not (contains? strings preview-empty-message)))))

(deftest preview-section-renders-mobile-empty-state-test
  (let [view (@#'route-shell/preview-section "User Vaults" [] false)
        empty-state (hiccup/find-first-node view #(and (= :div (first %))
                                                       (contains? (hiccup/node-class-set %) "rounded-lg")
                                                       (contains? (set (hiccup/collect-strings %))
                                                                  preview-empty-message)))
        row-node (hiccup/find-by-data-role view "vault-route-shell-row")]
    (is (some? empty-state))
    (is (contains? (set (hiccup/collect-strings empty-state)) preview-empty-message))
    (is (nil? row-node))))
