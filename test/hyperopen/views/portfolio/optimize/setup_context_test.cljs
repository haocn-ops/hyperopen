(ns hyperopen.views.portfolio.optimize.setup-context-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.optimize.setup-context :as setup-context]
            [hyperopen.views.portfolio.optimize.test-support :as ts]))

(def ^:private btc {:instrument-id "perp:BTC" :market-type :perp :coin "BTC"})

(defn- thin-history-instrument
  [n]
  {:instrument-id (str "perp:NEW" n) :market-type :perp :coin (str "NEW" n)})

(defn- proxy-entry
  []
  {:behavior :proxy
   :expected-return 0.0
   :volatility 0.8
   :max-weight 0.05
   :proxy {:instrument-ids ["perp:BTC"]
           :relationship-strength :medium
           :prior-weights nil}})

(def ^:private thin-instruments (mapv thin-history-instrument (range 1 9)))
(def ^:private universe (into [btc] thin-instruments))

(defn- context-rail-node
  []
  (setup-context/context-rail
   {:state {}
    :draft {:universe universe
            :objective {:kind :minimum-variance}
            :constraints {:max-asset-weight 0.5}
            :history-assumptions (into {}
                                       (map (fn [{:keys [instrument-id]}]
                                              [instrument-id (proxy-entry)]))
                                       thin-instruments)}
    :readiness {:request {:requested-universe universe
                          :universe [btc]
                          :objective {:kind :minimum-variance}
                          :history {:eligible-instruments [btc]}}
                :blocking-warnings []
                :warnings []}
    :snapshot {:snapshot-loaded? true}
    :preview-snapshot {:capital-ready? true :execution-ready? true :account {}}
    :run-state {:status :idle}
    :optimization-progress {:status :idle}
    :history-load-state {:status :succeeded :request-signature {:universe universe}}
    :last-successful-run {}
    :current-result? false
    :result-path nil}))

(deftest history-assumptions-rail-rows-are-height-capped-and-scrollable-test
  ;; Regression guard: a universe with many thin-history assets used to grow
  ;; this list without bound, stretching the whole 3-column setup grid row
  ;; (2026-07-08 regression). The rows wrapper must cap its height and scroll
  ;; internally instead, same idiom as the Return views editor's
  ;; optimizer-objective-view-rows.
  (let [node (context-rail-node)
        rows (ts/node-by-role node "portfolio-optimizer-history-assumptions-rail-rows")
        classes (set (get-in rows [1 :class]))
        strings (set (ts/collect-strings rows))]
    (is (some? (ts/node-by-role node "portfolio-optimizer-history-assumptions-rail")))
    (is (some? rows))
    (is (contains? classes "optimizer-history-assumptions-rows"))
    (is (contains? classes "overflow-y-auto"))
    (is (contains? classes "min-h-0"))
    (doseq [{:keys [coin]} thin-instruments]
      (is (contains? strings coin)
          (str coin " renders inside the capped rows wrapper.")))))
