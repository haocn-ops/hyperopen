(ns hyperopen.views.vaults.detail-view-hover-freeze-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.views.chart.d3.hover-state :as chart-hover-state]
            [hyperopen.views.vaults.detail-view :as vault-detail-view]
            [hyperopen.views.vaults.detail-view-test :refer [sample-state]]))

(defn- node-children [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn- collect-strings [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings (node-children node))
    (seq? node) (mapcat collect-strings node)
    :else []))

(def ^:private original-vault-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def ^:private hover-freeze-vault-address
  "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

(defn- vault-state-with-name
  [name]
  (-> sample-state
      (assoc-in [:router :path] (str "/vaults/" hover-freeze-vault-address))
      (assoc-in [:vaults :details-by-address hover-freeze-vault-address]
                (assoc (get-in sample-state [:vaults :details-by-address original-vault-address])
                       :name name))
      (assoc-in [:vaults :webdata-by-vault hover-freeze-vault-address]
                (get-in sample-state [:vaults :webdata-by-vault original-vault-address]))
      (assoc-in [:vaults :fills-by-vault hover-freeze-vault-address]
                (get-in sample-state [:vaults :fills-by-vault original-vault-address]))
      (assoc-in [:vaults :funding-history-by-vault hover-freeze-vault-address]
                (get-in sample-state [:vaults :funding-history-by-vault original-vault-address]))
      (assoc-in [:vaults :order-history-by-vault hover-freeze-vault-address]
                (get-in sample-state [:vaults :order-history-by-vault original-vault-address]))
      (assoc-in [:vaults :loading :fills-by-vault hover-freeze-vault-address] false)
      (assoc-in [:vaults :loading :funding-history-by-vault hover-freeze-vault-address] false)
      (assoc-in [:vaults :loading :order-history-by-vault hover-freeze-vault-address] false)
      (assoc-in [:vaults :loading :ledger-updates-by-vault hover-freeze-vault-address] false)
      (assoc-in [:vaults :errors :details-by-address hover-freeze-vault-address] nil)
      (assoc-in [:vaults :errors :webdata-by-vault hover-freeze-vault-address] nil)
      (assoc-in [:vaults :merged-index-rows]
                [(assoc (first (get-in sample-state [:vaults :merged-index-rows]))
                        :vaultAddress hover-freeze-vault-address
                        :name name)])))

(use-fixtures :each
  (fn [f]
    (chart-hover-state/clear-hover-state!)
    (f)
    (chart-hover-state/clear-hover-state!)))

(deftest vault-detail-view-freezes-shared-sections-while-chart-hover-is-active-test
  (let [initial-view (vault-detail-view/vault-detail-view (vault-state-with-name "Vault Initial"))
        initial-text (set (collect-strings initial-view))]
    (is (contains? initial-text "Vault Initial"))
    (chart-hover-state/set-surface-hover-active! :vaults true)
    (let [hover-view (vault-detail-view/vault-detail-view (vault-state-with-name "Vault Updated"))
          hover-text (set (collect-strings hover-view))]
      (is (contains? hover-text "Vault Initial"))
      (is (not (contains? hover-text "Vault Updated"))))
    (chart-hover-state/set-surface-hover-active! :vaults false)
    (let [updated-view (vault-detail-view/vault-detail-view (vault-state-with-name "Vault Updated"))
          updated-text (set (collect-strings updated-view))]
      (is (contains? updated-text "Vault Updated")))))
