(ns hyperopen.portfolio.optimizer.application.black-litterman-editor-model-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as model]))

(def ^:private sample-universe
  [{:instrument-id "perp:BTC"
    :market-type :perp
    :coin "BTC"
    :symbol "BTC-USDC"}
   {:instrument-id "perp:ETH"
    :market-type :perp
    :coin "ETH"
    :symbol "ETH-USDC"}
   {:instrument-id "perp:SOL"
    :market-type :perp
    :coin "SOL"
    :symbol "SOL-USDC"}])

(defn- labels
  [instrument-id]
  (case instrument-id
    "perp:BTC" "BTC"
    "perp:ETH" "ETH"
    "perp:SOL" "SOL"
    instrument-id))

(deftest shared-black-litterman-editor-model-builds-canonical-absolute-draft-test
  (let [draft {:universe [(first sample-universe)]
               :return-model {:kind :black-litterman
                              :views []}
               :risk-model {:kind :sample-covariance}}
        readiness {:status :ready
                   :request {:universe (:universe draft)
                             :return-model (:return-model draft)
                             :risk-model (:risk-model draft)
                             :periods-per-year 10
                             :history {:return-series-by-instrument
                                       {"perp:BTC" [0.01 0.03]}}
                             :black-litterman-prior
                             {:source :market-cap
                              :weights-by-instrument {"perp:BTC" 1}}}}
        editor-state {:selected-kind :unsupported
                      :drafts {:absolute {:instrument-id nil
                                          :return-text ""
                                          :return-text-touched? false
                                          :confidence :medium
                                          :horizon :3m
                                          :notes ""}}}
        view-model (model/editor-view-model draft readiness editor-state)]
    (is (= :absolute (:kind view-model)))
    (is (= {:instrument-id "perp:BTC"
            :return-text "20"
            :return-text-touched? false
            :confidence :medium
            :horizon :3m
            :notes ""}
           (:draft view-model)))
    (is (= true (:valid? view-model)))
    (is (= true (:pending? view-model)))
    (is (= "BTC expected return +20% annualized"
           (model/preview-text labels (:kind view-model) (:draft view-model))))))

(deftest shared-black-litterman-editor-model-validates-relative-drafts-test
  (let [relative-draft {:instrument-id "perp:ETH"
                        :comparator-instrument-id "perp:ETH"
                        :direction :outperform
                        :return-text "5"
                        :return-text-touched? true
                        :confidence :medium
                        :horizon :6m
                        :notes ""}
        errors (model/validate-draft true
                                     sample-universe
                                     []
                                     :relative
                                     relative-draft
                                     false)]
    (is (= {:comparator-instrument-id "Choose a different comparator asset."}
           errors))
    (is (= false
           (model/draft-valid? sample-universe :relative relative-draft 0 false)))
    (is (= "Select a comparator asset to preview this view."
           (model/preview-text labels :relative relative-draft)))))

(deftest shared-black-litterman-editor-model-materializes-views-and-options-test
  (let [draft {:instrument-id "perp:ETH"
               :comparator-instrument-id "perp:SOL"
               :direction :underperform
               :return-text "3"
               :return-text-touched? true
               :confidence :low
               :horizon :1y
               :notes "Relative fade"}
        view (model/draft->view :relative draft "view-1")]
    (is (= [[:low "LOW"] [:medium "MEDIUM"] [:high "HIGH"]]
           model/confidence-options))
    (is (= [[:1m "1M"] [:3m "3M"] [:6m "6M"] [:1y "1Y"]]
           model/horizon-options))
    (is (= :relative (:kind view)))
    (is (= "perp:ETH" (:instrument-id view)))
    (is (= "perp:SOL" (:comparator-instrument-id view)))
    (is (= :underperform (:direction view)))
    (is (= 0.03 (:return view)))
    (is (= :low (:confidence-level view)))
    (is (= 0.25 (:confidence view)))
    (is (= 0.75 (:confidence-variance view)))
    (is (= :1y (:horizon view)))
    (is (= {"perp:ETH" -1
            "perp:SOL" 1}
           (:weights view)))
    (is (= "Relative fade" (:notes view)))))
