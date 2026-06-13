(ns hyperopen.views.account-info.tab-filters
  (:require [clojure.string :as str]))

(defn- normalize-keyword-filter
  [value default-value valid-values]
  (let [normalized (cond
                     (keyword? value) value
                     (string? value) (keyword (str/lower-case value))
                     :else default-value)]
    (if (contains? valid-values normalized)
      normalized
      default-value)))

(def positions-direction-filter-options
  [[:all "All"]
   [:long "Long"]
   [:short "Short"]])

(def positions-direction-filter-labels
  (into {} positions-direction-filter-options))

(defn positions-direction-filter-key
  [positions-state]
  (normalize-keyword-filter (:direction-filter positions-state)
                            :all
                            (set (keys positions-direction-filter-labels))))

(def open-orders-direction-filter-options
  [[:all "All"]
   [:long "Long"]
   [:short "Short"]])

(def open-orders-direction-filter-labels
  (into {} open-orders-direction-filter-options))

(defn open-orders-direction-filter-key
  [open-orders-state]
  (normalize-keyword-filter (:direction-filter open-orders-state)
                            :all
                            (set (keys open-orders-direction-filter-labels))))

(def trade-history-direction-filter-options
  [[:all "All"]
   [:long "Long"]
   [:short "Short"]])

(def trade-history-direction-filter-labels
  (into {} trade-history-direction-filter-options))

(defn trade-history-direction-filter-key
  [trade-history-state]
  (normalize-keyword-filter (:direction-filter trade-history-state)
                            :all
                            (set (keys trade-history-direction-filter-labels))))

(def order-history-status-options
  [[:all "All"]
   [:long "Long"]
   [:short "Short"]])

(def order-history-status-labels
  (into {} order-history-status-options))

(defn order-history-status-filter-key
  [order-history-state]
  (normalize-keyword-filter (:status-filter order-history-state)
                            :all
                            (set (keys order-history-status-labels))))
