(ns hyperopen.portfolio.optimizer.application.view-model.equal-risk-diversification
  "Pure read models for portfolio diversification comparisons and additive
  own-variance/cross-covariance bridges."
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def cross-effect-neutral-threshold 0.0005)

(defn cross-effect-direction
  [effect]
  (cond
    (< (js/Math.abs effect) cross-effect-neutral-threshold) :neutral
    (neg? effect) :offsets
    :else :amplifies))

(def ^:private scalar-keys
  [:modeled-volatility
   :all-move-together-volatility
   :zero-correlation-volatility
   :reduction-vs-all-move-together
   :reduction-ratio-vs-all-move-together
   :modeled-minus-zero-correlation])

(defn- valid-summary?
  [summary]
  (and (map? summary)
       (every? #(coercion/finite-number? (get summary %)) scalar-keys)))

(defn- format-pct
  [value]
  (str (.toFixed (* 100 value) 1) "%"))

(defn comparison-model
  [result]
  (let [structure (:risk-structure result)
        target (:target-diversification structure)]
    (when (valid-summary? target)
      (let [source (keep (fn [[key label summary]]
                           (when (valid-summary? summary)
                             {:key key :label label :summary summary}))
                         [[:current "Current" (:current-diversification structure)]
                          [:target "Recommended" target]])]
      (let [scale-max (reduce max 0
                              (mapcat (fn [{:keys [summary]}]
                                        [(:modeled-volatility summary)
                                         (:all-move-together-volatility summary)
                                         (:zero-correlation-volatility summary)])
                                      source))
            position #(if (pos? scale-max) (* 100 (/ % scale-max)) 0)]
        {:scale-max scale-max
         :cards
         (mapv
          (fn [{:keys [key label summary]}]
            (let [effect (:modeled-minus-zero-correlation summary)
                  direction (cross-effect-direction effect)]
              {:key key
               :label label
               :scale-max scale-max
               :benchmarks
               [{:key :all-move-together :label "All move together"
                 :value (:all-move-together-volatility summary)}
                {:key :zero-correlation :label "Zero correlation"
                 :value (:zero-correlation-volatility summary)}
                {:key :modeled :label "Modeled"
                 :value (:modeled-volatility summary)}]
               :positions
               {:all-move-together
                (position (:all-move-together-volatility summary))
                :zero-correlation
                (position (:zero-correlation-volatility summary))
                :modeled (position (:modeled-volatility summary))}
               :benefit-copy
               (str (format-pct
                     (:reduction-ratio-vs-all-move-together summary))
                    " lower than all move together")
               :correlation-direction direction
               :correlation-effect effect
               :correlation-copy
               (case direction
                 :offsets "Correlations offset risk vs zero correlation"
                 :amplifies "Correlations amplify risk vs zero correlation"
                 "Correlations are neutral vs zero correlation")}))
          source)})))))

(defn bridge-model
  [fit-scale rows]
  (let [scale (fit-scale (mapcat (fn [{:keys [standalone share]}]
                                   [standalone share]) rows))
        x (:x scale)]
    {:scale scale
     :rows
     (mapv (fn [{:keys [standalone diversification share] :as row}]
             (let [own (when (coercion/finite-number? standalone) standalone)
                   cross (when (coercion/finite-number? diversification)
                           diversification)
                   net (cond
                         (and own cross) (+ own cross)
                         (coercion/finite-number? share) share
                         :else nil)]
               (assoc row
                      :own-end own :cross-start own :cross-end net :net-end net
                      :cross-direction (when cross
                                         (cross-effect-direction cross))
                      :positions {:zero (x 0)
                                  :own (when own (x own))
                                  :cross-start (when own (x own))
                                  :cross-end (when net (x net))
                                  :net (when net (x net))})))
           rows)}))
