(ns hyperopen.portfolio.optimizer.actions.execution-margin-rec-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.margin-rec.state :as margin-rec-state]
            [hyperopen.portfolio.optimizer.actions.execution :as execution-actions]))

(def ^:private ready-isolated-row
  {:row-id "perp:xyz:TSM"
   :instrument-id "perp:xyz:TSM"
   :instrument-type :perp
   :status :ready
   :coin "xyz:TSM"
   :side :buy
   :quantity 0.36
   :delta-notional-usd 157.5})

(def ^:private ready-cross-row
  {:row-id "perp:BTC"
   :instrument-id "perp:BTC"
   :instrument-type :perp
   :status :ready
   :coin "BTC"
   :side :buy
   :quantity 0.01
   :delta-notional-usd 1000})

(defn- base-state
  [& [overrides]]
  (merge {:trading-settings {:margin-rec-auto-topup? true}
          :asset-selector {:market-by-key
                           {"perp:xyz:TSM" {:market-type :perp
                                            :coin "xyz:TSM"
                                            :dex "xyz"
                                            :only-isolated? true}
                            "perp:BTC" {:market-type :perp
                                        :coin "BTC"
                                        :dex nil
                                        :only-isolated? false}}}
          :webdata2 {}
          :perp-dex-clearinghouse {}
          :margin-rec (margin-rec-state/default-state)}
         overrides))

(deftest isolated-execution-legs-extraction
  (let [legs (margin-rec-state/isolated-execution-legs
              (base-state)
              [ready-isolated-row
               ready-cross-row
               (assoc ready-isolated-row :status :blocked)
               (assoc ready-isolated-row :instrument-type :spot)])]
    (is (= 1 (count legs)))
    (let [leg (first legs)]
      (is (= "xyz:TSM|xyz" (:position-key leg)))
      (is (= "xyz" (:dex leg)))
      (is (= 0.36 (:expected-size leg)))
      (is (= 157.5 (:max-add leg)))))
  (testing "sells that reduce an existing long are not top-up candidates"
    (let [with-position
          (base-state
           {:perp-dex-clearinghouse
            {"xyz" {:assetPositions
                    [{:position {:coin "xyz:TSM"
                                 :szi "0.5"
                                 :positionValue "220"
                                 :marginUsed "20"
                                 :leverage {:type "isolated" :value 10}}}]}}})]
      (is (empty? (margin-rec-state/isolated-execution-legs
                   with-position
                   [(assoc ready-isolated-row :side :sell :quantity 0.2)])))
      (testing "buys that grow it are"
        (is (= 1 (count (margin-rec-state/isolated-execution-legs
                         with-position
                         [ready-isolated-row]))))))))

(deftest execution-intent-save-gating
  (testing "creates draft intents for isolated legs when enabled"
    (let [[effect-id path intents]
          (execution-actions/margin-rec-execution-intent-save
           (base-state)
           {:rows [ready-isolated-row ready-cross-row]})]
      (is (= :effects/save effect-id))
      (is (= margin-rec-state/intents-path path))
      (is (= [:pending] (map :status (vals intents))))
      (is (= :optimizer (:source (get intents "xyz:TSM|xyz"))))
      (is (nil? (:expires-at (get intents "xyz:TSM|xyz"))))))
  (testing "nil when the setting is off"
    (is (nil? (execution-actions/margin-rec-execution-intent-save
               (base-state {:trading-settings {}})
               {:rows [ready-isolated-row]}))))
  (testing "nil when no isolated legs exist"
    (is (nil? (execution-actions/margin-rec-execution-intent-save
               (base-state)
               {:rows [ready-cross-row]})))))
