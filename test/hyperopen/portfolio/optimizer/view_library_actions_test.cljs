(ns hyperopen.portfolio.optimizer.view-library-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]))

(def ^:private views-path
  [:portfolio :optimizer :draft :return-model :views])

(defn- state-with
  [{:keys [return-model universe library]}]
  {:portfolio {:optimizer {:draft {:universe universe
                                   :return-model return-model
                                   :metadata {:dirty? false}}
                           :view-library library}}})

(deftest hydrate-view-library-gap-fills-remembered-views-test
  (let [state (state-with
               {:return-model {:kind :black-litterman :views []}
                :universe [{:instrument-id "perp:BTC"}]
                :library {"perp:BTC" {:instrument-id "perp:BTC"
                                      :return 0.2
                                      :confidence-level :high
                                      :updated-at-ms 1}}})]
    (is (= [[:effects/save-many
             [[views-path
               [{:id "bl_view_1"
                 :kind :absolute
                 :instrument-id "perp:BTC"
                 :return 0.2
                 :confidence-level :high
                 :confidence 0.75
                 :confidence-variance 0.25
                 :horizon :3m
                 :weights {"perp:BTC" 1}}]]]]]
           (actions/hydrate-portfolio-optimizer-view-library state))
        "The remembered view re-enters the draft; the draft is NOT marked dirty.")))

(deftest hydrate-view-library-no-ops-without-a-gap-test
  (let [covered (state-with
                 {:return-model {:kind :black-litterman
                                 :views [{:id "bl_view_1"
                                          :kind :absolute
                                          :instrument-id "perp:BTC"
                                          :return 0.5}]}
                  :universe [{:instrument-id "perp:BTC"}]
                  :library {"perp:BTC" {:instrument-id "perp:BTC"
                                        :return 0.2
                                        :confidence-level :high
                                        :updated-at-ms 1}}})]
    (is (= [] (actions/hydrate-portfolio-optimizer-view-library covered))
        "An authored draft view always wins over the library entry."))
  (is (= [] (actions/hydrate-portfolio-optimizer-view-library
             (state-with
              {:return-model {:kind :historical-mean}
               :universe [{:instrument-id "perp:BTC"}]
               :library {"perp:BTC" {:instrument-id "perp:BTC"
                                     :return 0.2
                                     :confidence-level :high
                                     :updated-at-ms 1}}})))
      "Non-views return models never hydrate.")
  (is (= [] (actions/hydrate-portfolio-optimizer-view-library
             (state-with
              {:return-model {:kind :black-litterman :views []}
               :universe [{:instrument-id "perp:ETH"}]
               :library {"perp:BTC" {:instrument-id "perp:BTC"
                                     :return 0.2
                                     :confidence-level :high
                                     :updated-at-ms 1}}})))
      "A remembered entry for an out-of-universe asset stays in the library only."))
