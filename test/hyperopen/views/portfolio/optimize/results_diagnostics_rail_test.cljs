(ns hyperopen.views.portfolio.optimize.results-diagnostics-rail-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.results-diagnostics-rail :as rail]))

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

(deftest diversification-status-actually-evaluates-test
  ;; Audit regression: "Effective N · 0.5 of 18" rendered "● OK" because the
  ;; status was hardcoded. A one-position target is bad, a concentrated one is
  ;; caution, unknown is never falsely reassuring.
  (is (= :bad (rail/diversification-status 0.5 18)))
  (is (= :bad (rail/diversification-status 1.2 4)))
  (is (= :caution (rail/diversification-status 3 18)))
  (is (= :ok (rail/diversification-status 6 18)))
  (is (= :ok (rail/diversification-status 2 4)))
  (is (= :unknown (rail/diversification-status nil 18)))
  (is (= :unknown (rail/diversification-status js/NaN 18))))

(deftest warning-rows-group-per-code-with-plain-labels-test
  ;; 25 near-identical per-asset rows bury the signal: one row per CODE, assets
  ;; listed on the headline, engine message deduplicated into a detail line —
  ;; and raw namespaced ids never leak (perp:HYPE renders as HYPE).
  (let [rows (vec (rail/warning-rows
                   {}
                   [{:code :stale-history
                     :instrument-id "perp:XRP"
                     :message "Cached history is stale."}
                    {:code :stale-history
                     :instrument-id "perp:HYPE"
                     :message "Cached history is stale."}
                    {:code :proxy-history-used
                     :instrument-id "perp:PAXG"
                     :message "Proxy returns are not native Hyperliquid returns."}]))
        texts (mapv #(str/join " " (collect-strings %)) rows)]
    (is (= 2 (count rows)) "one row per code, not per asset")
    (let [stale-text (first (filter #(str/includes? % "Cached history is stale.") texts))]
      (is (some? stale-text))
      (is (str/includes? stale-text "XRP"))
      (is (str/includes? stale-text "HYPE"))
      (is (not (str/includes? stale-text "perp:")) "no raw namespaced ids")
      ;; The identical per-asset message appears once, not twice.
      (is (= 1 (count (re-seq #"Cached history is stale\." stale-text)))))))
