(ns hyperopen.portfolio.optimizer.application.setup-readiness-warning-policy-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.history-warning-policy :as warning-policy]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]))

(defn- deep-merge
  [& maps]
  (apply merge-with
         (fn [left right]
           (if (and (map? left)
                    (map? right))
             (deep-merge left right)
             right))
         maps))

(defn- optimizer-state
  [overrides]
  (deep-merge
   {:portfolio
    {:optimizer
     {:draft
      {:objective {:kind :minimum-variance}
       :return-model {:kind :historical-mean}
       :risk-model {:kind :diagonal-shrink}
       :constraints {:long-only? true}}
      :runtime {:as-of-ms 2500
                :stale-after-ms 5000
                :funding-periods-per-year 1095}}}}
   overrides))

(defn- native-series
  [local-id backend-id base-close]
  {:local-instrument-id local-id
   :instrument-id backend-id
   :lineage-kind :native
   :points [{:time-ms 1000 :close base-close :return nil}
            {:time-ms 2000 :close (* 1.1 base-close) :return 0.1}]
   :funding {:status :available
             :annualized-carry 0}})

(defn- two-asset-state
  "BTC plus a second perp whose backend CATALOG-proxy extension failed
  validation, with the second asset's series overridable."
  [second-series extra-warnings]
  (optimizer-state
   {:portfolio
    {:optimizer
     {:draft {:universe [{:instrument-id "perp:BTC"
                          :market-type :perp
                          :coin "BTC"
                          :name "Bitcoin"}
                         {:instrument-id "perp:STRK"
                          :market-type :perp
                          :coin "STRK"
                          :name "Starknet"}]}
      ;; Discovery decoration is what lets the backend-keyed warning attribute
      ;; to the draft instrument at all (see setup-readiness-enrichment-test).
      :history-discovery
      {:backend-id-by-local-id {"perp:BTC" "hl:perp:BTC"
                                "perp:STRK" "hl:perp:STRK"}
       :instruments-by-backend-id
       {"hl:perp:BTC" {:instrument-kind :hl-perp}
        "hl:perp:STRK" {:instrument-kind :hl-perp}}}
      :history-data
      {:api-v2-history
       {:status :partial
        :warnings (into [{:code :proxy-validation-failed
                          :instrument-id "hl:perp:STRK"}]
                        extra-warnings)
        :common-calendar [1000 2000]
        :return-calendar [2000]
        :aligned-returns-by-instrument
        {"perp:BTC" {:returns [0.1]}
         "perp:STRK" {:returns [0.1]}}
        :series-by-instrument
        {"perp:BTC" (native-series "perp:BTC" "hl:perp:BTC" 100)
         "perp:STRK" second-series}}}}}}))

(deftest proxy-validation-failure-forgiven-for-usable-native-series-test
  ;; Regression (live 2026-07-07): STRK's Tiingo lookback-extension proxy
  ;; failed backend quality validation while the backend served 870 usable
  ;; NATIVE daily points - and the hard-warning exclusion discarded all of
  ;; them, single-handedly blocking the run. A failed OPTIONAL extension must
  ;; not reject the asset's own served history.
  (let [readiness (setup-readiness/build-readiness
                   (two-asset-state
                    (native-series "perp:STRK" "hl:perp:STRK" 50)
                    [{:code :stale-history
                      :instrument-id "hl:perp:STRK"}]))]
    (is (= :ready (:status readiness)))
    (is (= true (:runnable? readiness)))
    (is (= ["perp:BTC" "perp:STRK"]
           (mapv :instrument-id (get-in readiness [:request :universe])))
        "The asset stays eligible on its own native history.")
    (is (contains? (set (map :code (:warnings readiness)))
                   :proxy-validation-failed)
        "The proxy failure stays visible as information.")
    (is (->> (:warnings readiness)
             (filter #(= :proxy-validation-failed (:code %)))
             (every? :forgiven?))
        "The forgiven warning is tagged so no downstream surface reads it as a rejection.")
    ;; Regression (live 2026-07-08): POL aligned on 658 native days yet badged
    ;; "No history" - the forgiven warning still drove the per-asset status to
    ;; :rejected. The status must come from what actually happened (aligned,
    ;; stale note), never from a forgiven diagnostic.
    (is (not= :rejected
              (get (setup-readiness/history-status-by-instrument readiness)
                   "perp:STRK")))))

(deftest forgiven-warning-never-drives-history-status-test
  (is (= {}
         (warning-policy/strongest-warning-status-by-id
          [{:code :proxy-validation-failed
            :instrument-id "perp:A"
            :forgiven? true}])))
  (is (= {"perp:A" :stale}
         (warning-policy/strongest-warning-status-by-id
          [{:code :proxy-validation-failed
            :instrument-id "perp:A"
            :forgiven? true}
           {:code :stale-history :instrument-id "perp:A"}]))
      "Other warnings on the same asset still project normally."))

(deftest proxy-validation-failure-still-excludes-unusable-series-test
  ;; Forgiveness requires the served series to be the asset's OWN usable
  ;; history. A rejected/empty series (the GRAM case) must stay excluded.
  (let [readiness (setup-readiness/build-readiness
                   (two-asset-state
                    {:local-instrument-id "perp:STRK"
                     :instrument-id "hl:perp:STRK"
                     :lineage-kind :rejected
                     :points []}
                    []))]
    (is (= :blocked (:status readiness)))
    (is (= :incomplete-history (:reason readiness)))
    (is (= ["perp:BTC"]
           (mapv :instrument-id (get-in readiness [:request :universe]))))))

(deftest proxy-validation-failure-still-excludes-proxy-lineage-series-test
  ;; A series that is ITSELF proxy-derived cannot be forgiven by the proxy
  ;; failure that indicts it.
  (let [readiness (setup-readiness/build-readiness
                   (two-asset-state
                    (assoc (native-series "perp:STRK" "hl:perp:STRK" 50)
                           :lineage-kind :stitched-native-proxy)
                    []))]
    (is (= :blocked (:status readiness)))
    (is (= ["perp:BTC"]
           (mapv :instrument-id (get-in readiness [:request :universe]))))))

(deftest strongest-warning-status-wins-over-input-order-test
  ;; Regression (live 2026-07-07): last-warning-wins let STRK's stale-history
  ;; note overwrite the :rejected status from its hard warning, so the
  ;; excluded asset read as fine and never surfaced a proxy-workflow card.
  (is (= {"perp:A" :rejected}
         (warning-policy/strongest-warning-status-by-id
          [{:code :validation-failed :instrument-id "perp:A"}
           {:code :stale-history :instrument-id "perp:A"}])))
  (is (= {"perp:A" :rejected}
         (warning-policy/strongest-warning-status-by-id
          [{:code :stale-history :instrument-id "perp:A"}
           {:code :validation-failed :instrument-id "perp:A"}]))
      "Severity ordering is independent of warning order.")
  (is (= {"perp:A" :insufficient "perp:B" :stale}
         (warning-policy/strongest-warning-status-by-id
          [{:code :stale-history :instrument-id "perp:A"}
           {:code :insufficient-candle-history :instrument-id "perp:A"}
           {:code :stale-history :instrument-id "perp:B"}]))))
