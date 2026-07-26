(ns hyperopen.views.portfolio.montecarlo.controls-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.montecarlo.actions :as mc-actions]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.portfolio.montecarlo.controls :as controls]))

(defn- controls-view
  [overrides]
  (controls/controls-bar
   (merge {:controls (assoc mc-actions/default-controls :method :bootstrap)
           :sims-options mc-actions/sims-options
           :horizon-options mc-actions/horizon-options
           :method-options mc-actions/method-options
           :sample-size 53
           :total-years 1
           :chrome {:set-control-action :actions/set-portfolio-monte-carlo-control
                    :rerun-action :actions/rerun-portfolio-monte-carlo
                    :data-role-prefix "portfolio-monte-carlo"}}
          overrides)))

(defn- horizon-button
  [view label]
  (hiccup/find-first-node
   view
   #(and (= :button (first %))
         (= #{label} (hiccup/direct-texts %)))))

(deftest near-one-year-history-keeps-one-year-horizon-reselectable-test
  (let [view (controls-view {:controls (assoc mc-actions/default-controls
                                              :method :bootstrap
                                              :horizon 6)
                             :total-years (/ 364 365.2425)})
        one-year (horizon-button view "1Y")
        two-years (horizon-button view "2Y")]
    (testing "one-year remains clickable when elapsed history rounds to 12 months"
      (is (false? (boolean (get-in one-year [1 :disabled]))))
      (is (= [[:actions/set-portfolio-monte-carlo-control :horizon 12]]
             (get-in one-year [1 :on :click]))))
    (testing "truly over-observed horizons remain disabled"
      (is (true? (get-in two-years [1 :disabled])))
      (is (nil? (get-in two-years [1 :on :click]))))))
