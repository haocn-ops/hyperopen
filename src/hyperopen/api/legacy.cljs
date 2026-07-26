(ns hyperopen.api.legacy
  (:require [hyperopen.domain.funding-history :as funding-history]
            [hyperopen.platform :as platform]))

(def default-funding-history-window-ms funding-history/default-window-ms)

(def funding-position-side funding-history/funding-position-side)
(def funding-history-row-id funding-history/funding-history-row-id)
(def normalize-info-funding-row funding-history/normalize-info-funding-row)
(def normalize-info-funding-rows funding-history/normalize-info-funding-rows)
(def normalize-ws-funding-row funding-history/normalize-ws-funding-row)
(def normalize-ws-funding-rows funding-history/normalize-ws-funding-rows)
(def sort-funding-history-rows funding-history/sort-funding-history-rows)
(def merge-funding-history-rows funding-history/merge-funding-history-rows)

(defn normalize-funding-history-filters
  ([filters]
   (normalize-funding-history-filters filters (platform/now-ms)))
  ([filters now]
   (funding-history/normalize-funding-history-filters filters now default-funding-history-window-ms)))

(defn filter-funding-history-rows
  [rows filters]
  (let [filters* (normalize-funding-history-filters filters)]
    (funding-history/filter-funding-history-rows rows filters*)))
