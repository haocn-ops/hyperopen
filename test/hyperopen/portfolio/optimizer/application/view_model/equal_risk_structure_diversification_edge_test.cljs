(ns hyperopen.portfolio.optimizer.application.view-model.equal-risk-structure-diversification-edge-test
  "Geometry and degradation invariants for diversification and attribution read models."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.application.view-model.equal-risk-structure
             :as structure-model]))

(def ^:private target-summary
  {:modeled-volatility 0.40
   :all-move-together-volatility 0.80
   :zero-correlation-volatility 0.30
   :reduction-vs-all-move-together 0.40
   :reduction-ratio-vs-all-move-together 0.50
   :modeled-minus-zero-correlation 0.10})

(def ^:private current-summary
  {:modeled-volatility 0.20
   :all-move-together-volatility 0.30
   :zero-correlation-volatility 0.18
   :reduction-vs-all-move-together 0.10
   :reduction-ratio-vs-all-move-together (/ 1 3)
   :modeled-minus-zero-correlation 0.02})

(defn- result-with
  [current target]
  {:risk-structure (cond-> {:method :signed-euler-decomposition}
                     (some? current) (assoc :current-diversification current)
                     (some? target) (assoc :target-diversification target))})

(deftest comparison-markers-use-one-absolute-volatility-domain-test
  (let [{:keys [cards scale-max]}
        (structure-model/diversification-comparison-model
         (result-with current-summary target-summary))
        by-key (into {} (map (juxt :key identity)) cards)]
    (is (= 0.80 scale-max))
    (is (= scale-max (:scale-max (by-key :current))
           (:scale-max (by-key :target))))
    (is (< (get-in by-key [:current :positions :modeled])
           (get-in by-key [:target :positions :modeled])))
    (is (= 25.0 (get-in by-key [:current :positions :modeled])))
    (is (= 50.0 (get-in by-key [:target :positions :modeled])))))

(deftest unavailable-current-never-becomes-a-zero-marker-test
  (doseq [[label current]
          [["missing" nil]
           ["explicit unavailable" {:status :unavailable}]
           ["partial" (dissoc current-summary :modeled-volatility)]
           ["NaN" (assoc current-summary :modeled-volatility js/NaN)]
           ["Infinity" (assoc current-summary
                               :modeled-volatility js/Infinity)]]]
    (testing label
      (let [model (structure-model/diversification-comparison-model
                   (result-with current target-summary))]
        (is (= [:target] (mapv :key (:cards model))))
        (is (not-any? #(or (js/isNaN %) (= js/Infinity %))
                      (mapcat vals
                              (keep :positions (:cards model)))))))))

(deftest current-only-summary-cannot-render-without-target-test
  (is (nil? (structure-model/diversification-comparison-model
             (result-with current-summary nil)))
      "current is optional comparison context, never the canonical baseline"))

(deftest bridge-model-preserves-additive-endpoints-in-every-direction-test
  (let [source [{:instrument-id "positive"
                 :standalone 0.20 :diversification 0.10 :share 0.30}
                {:instrument-id "negative-crosses-zero"
                 :standalone 0.10 :diversification -0.25 :share -0.15}
                {:instrument-id "zero"
                 :standalone 0.15 :diversification 0.0 :share 0.15}]
        {:keys [rows]} (structure-model/breakdown-bridge-model source)
        by-id (into {} (map (juxt :instrument-id identity)) rows)]
    (doseq [row rows]
      (is (= (:own-end row) (:cross-start row)))
      (is (= (:net-end row) (:cross-end row)))
      (is (= (:net-end row) (+ (:own-end row) (:diversification row))))
      (is (every? #(and (number? %) (js/isFinite %))
                  (vals (:positions row)))))
    (is (< (:cross-start (by-id "positive"))
           (:cross-end (by-id "positive"))))
    (is (> (:cross-start (by-id "negative-crosses-zero"))
           (:cross-end (by-id "negative-crosses-zero"))))
    (is (= (:cross-start (by-id "zero"))
           (:cross-end (by-id "zero"))))))

(deftest bridge-scale-fits-cumulative-endpoints-not-component-magnitudes-test
  (let [source [{:instrument-id "outside"
                 :standalone 0.10 :diversification -0.25 :share -0.15}
                {:instrument-id "positive"
                 :standalone 0.40 :diversification 0.30 :share 0.70}]
        {:keys [scale rows]} (structure-model/breakdown-bridge-model source)]
    (is (<= (:lo scale) -0.15 0 (:hi scale)))
    (is (>= (:hi scale) 0.70))
    (doseq [row rows
            key [:zero :own :cross-start :cross-end :net]]
      (let [position (get-in row [:positions key])]
        (is (<= 0 position 100)
            (str (:instrument-id row) " " key " must remain visible"))))))
