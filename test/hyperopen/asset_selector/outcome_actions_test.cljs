(ns hyperopen.asset-selector.outcome-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.asset-selector.actions :as actions]))

(defn- path-value
  [effects path]
  (some (fn [effect]
          (when (and (vector? effect)
                     (= :effects/save-many (first effect)))
            (some (fn [[effect-path value]]
                    (when (= path effect-path)
                      value))
                  (second effect))))
        effects))

(deftest select-question-outcome-side-preserves-clicked-option-coin-test
  (let [market {:key "question:30"
                :coin "#1610"
                :market-type :outcome
                :outcome-kind :question
                :outcome-sides [{:side-index 0 :coin "#1610"}
                                {:side-index 1 :coin "#1611"}]
                :outcome-side-aliases {"#1620" {:coin "#1620"
                                                 :outcome-id 162
                                                 :side-index 0
                                                 :sibling-coins ["#1620" "#1621"]}}
                :outcome-subscription-coins ["#1610" "#1611" "#1620" "#1621"]}
        effects (actions/select-asset
                 {:active-asset nil
                  :router {:path "/trade"}
                  :asset-selector {:market-by-key {"question:30" market}}}
                 "#1620")]
    (is (= "#1620" (:coin (path-value effects [:active-market]))))
    (is (= [[:effects/save [:router :path] "/trade/%231620"]
            [:effects/push-state "/trade?market=%231620"]]
           (subvec effects 2 4)))
    (is (= [[:effects/subscribe-active-asset "#1620"]
            [:effects/subscribe-orderbook "#1610"]
            [:effects/subscribe-trades "#1610"]
            [:effects/subscribe-orderbook "#1611"]
            [:effects/subscribe-trades "#1611"]
            [:effects/subscribe-orderbook "#1620"]
            [:effects/subscribe-trades "#1620"]
            [:effects/subscribe-orderbook "#1621"]
            [:effects/subscribe-trades "#1621"]
            [:effects/sync-active-asset-funding-predictability "#1620"]]
           (subvec effects 4)))))
