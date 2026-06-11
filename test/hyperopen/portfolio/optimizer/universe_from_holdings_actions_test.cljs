(ns hyperopen.portfolio.optimizer.universe-from-holdings-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]))

(def ^:private selection-prefetch-effect
  [:effects/load-portfolio-optimizer-history
   {:source :selection-prefetch
    :queue? true
    :merge? true}])

(defn- queued-prefetch-state
  [instruments]
  {:queue (vec instruments)
   :active-instrument-id nil
   :by-instrument-id
   (into {}
         (map (fn [instrument]
                [(:instrument-id instrument)
                 {:status :queued
                  :started-at-ms nil
                  :completed-at-ms nil
                  :error nil
                  :warnings []}]))
         instruments)})

(defn- effect-values-by-path
  [effects]
  (reduce (fn [acc effect]
            (case (first effect)
              :effects/save
              (assoc acc (second effect) (nth effect 2))

              :effects/save-many
              (reduce (fn [acc [path value]]
                        (assoc acc path value))
                      acc
                      (second effect))

              acc))
          {}
          (or effects [])))

(defn- perp-position-row
  [coin notional-usdc]
  {:position {:coin coin
              :szi "1"
              :positionValue (str notional-usdc)
              :leverage {:type "cross"
                         :value "3"}}})

(defn- discovery-state
  [coins missing-coins]
  {:backend-id-by-local-id
   (into {}
         (map (fn [coin]
                [(str "perp:" coin) (str "hl:perp:" coin)]))
         coins)
   :instruments-by-backend-id
   (into {}
         (map (fn [coin]
                (let [missing? (contains? missing-coins coin)]
                  [(str "hl:perp:" coin)
                   {:instrument-id (str "hl:perp:" coin)
                    :display-symbol coin
                    :instrument-kind :hl-perp
                    :history {:status (if missing? :missing :available)
                              :quality-status (if missing? :failed :passed)}}])))
         coins)})

(deftest set-draft-universe-from-current-holdings-skips-known-unusable-history-without-truncating-test
  (let [coins (mapv #(str "COIN" %) (range 30))
        missing-coins #{"COIN29" "COIN28"}
        state {:webdata2 {:clearinghouseState
                          {:marginSummary {:accountValue "100000"}
                           :assetPositions
                           (mapv (fn [idx coin]
                                   (perp-position-row coin (inc idx)))
                                 (range)
                                 coins)}}
               :portfolio {:optimizer
                           {:history-discovery
                            (discovery-state coins missing-coins)}}}
        effects (actions/set-portfolio-optimizer-universe-from-current state)
        path-values (effect-values-by-path effects)
        universe (get path-values [:portfolio :optimizer :draft :universe])
        prefetch-state (get path-values [:portfolio :optimizer :history-prefetch])
        selected-ids (mapv :instrument-id universe)
        expected-ids (mapv #(str "perp:COIN" %)
                           (range 27 -1 -1))]
    (is (= 28 (count universe)))
    (is (= expected-ids selected-ids))
    (is (not-any? missing-coins (mapv :coin universe)))
    (is (every? #(= :available (:optimizer-history/history-status %))
                universe))
    (is (= (queued-prefetch-state universe)
           prefetch-state))
    (is (= selection-prefetch-effect
           (second effects)))))
