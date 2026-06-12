(ns hyperopen.portfolio.optimizer.target-sigma-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]
            [hyperopen.portfolio.optimizer.application.scenario-state :as scenario-state]))

(defn- applied-objective
  [effects]
  (-> effects first second first second))

(deftest set-objective-parameter-percent-converts-to-decimal-test
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-volatility]
                                0.22]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter-percent
          {}
          :target-volatility
          "22")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-volatility]
                                0.04]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter-percent
          {}
          :target-volatility
          "2"))
      "sigma clamps to the 4% dial floor")
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-volatility]
                                0.8]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter-percent
          {}
          :target-volatility
          "80"))
      "sigma above the default dial commits and widens the dial")
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-volatility]
                                100]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter-percent
          {}
          :target-volatility
          "99999"))
      "pathological sigma stops at the 10000% sanity cap")
  (is (= []
         (actions/set-portfolio-optimizer-objective-parameter-percent
          {}
          :target-volatility
          "-5"))
      "non-positive sigma is rejected")
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-return]
                                0.15]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter-percent
          {}
          "targetReturn"
          15)))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-return]
                                2]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter-percent
          {}
          :target-return
          "200"))
      "target return is not dial-clamped")
  (is (= []
         (actions/set-portfolio-optimizer-objective-parameter-percent
          {}
          :unknown
          "22")))
  (is (= []
         (actions/set-portfolio-optimizer-objective-parameter-percent
          {}
          :target-volatility
          "not-a-number"))))

(deftest set-target-sigma-draft-saves-clamped-pending-decimal-test
  (is (= [[:effects/save
           [:portfolio-ui :optimizer :target-sigma-draft]
           0.31]]
         (actions/set-portfolio-optimizer-target-sigma-draft {} "31")))
  (is (= [[:effects/save
           [:portfolio-ui :optimizer :target-sigma-draft]
           0.04]]
         (actions/set-portfolio-optimizer-target-sigma-draft {} "2"))
      "clamps below the 4% dial floor")
  (is (= [[:effects/save
           [:portfolio-ui :optimizer :target-sigma-draft]
           0.55]]
         (actions/set-portfolio-optimizer-target-sigma-draft {} "55"))
      "values above the default dial commit and widen the dial")
  (is (= [[:effects/save
           [:portfolio-ui :optimizer :target-sigma-draft]
           10]]
         (actions/set-portfolio-optimizer-target-sigma-draft {} "1000"))
      "a typed 1000% target commits and rescales the dial")
  (is (= []
         (actions/set-portfolio-optimizer-target-sigma-draft {} "0")))
  (is (= []
         (actions/set-portfolio-optimizer-target-sigma-draft {} "-5")))
  (is (= []
         (actions/set-portfolio-optimizer-target-sigma-draft {} "abc"))))

(deftest set-objective-menu-target-sigma-saves-clamped-pending-decimal-test
  (is (= [[:effects/save
           [:portfolio-ui :optimizer :objective-menu-target-sigma]
           0.22]]
         (actions/set-portfolio-optimizer-objective-menu-target-sigma {} "22")))
  (is (= [[:effects/save
           [:portfolio-ui :optimizer :objective-menu-target-sigma]
           0.9]]
         (actions/set-portfolio-optimizer-objective-menu-target-sigma {} "90")))
  (is (= []
         (actions/set-portfolio-optimizer-objective-menu-target-sigma {} ""))))

(deftest reset-target-sigma-draft-clears-pending-test
  (is (= [[:effects/save
           [:portfolio-ui :optimizer :target-sigma-draft]
           nil]]
         (actions/reset-portfolio-optimizer-target-sigma-draft {}))))

(deftest objective-menu-apply-commits-staged-menu-sigma-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :max-sharpe}
                                               :return-model {:kind :historical-mean}
                                               :metadata {:dirty? false}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :target-volatility
                                          :objective-menu-target-sigma 0.22}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective]
               {:kind :target-volatility
                :target-volatility 0.22}]
              [[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio-ui :optimizer :objective-menu-target-sigma] nil]
              [[:portfolio-ui :optimizer :target-sigma-draft] nil]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/apply-portfolio-optimizer-objective-menu-selection-and-run state))
        "the sigma staged in the menu editor overrides the 12% preset on apply")))

(deftest rerun-at-target-sigma-commits-pending-and-runs-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :target-volatility
                                                           :target-volatility 0.12}
                                               :return-model {:kind :historical-mean}
                                               :metadata {:dirty? false}}}}
               :portfolio-ui {:optimizer {:target-sigma-draft 0.31}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective]
               {:kind :target-volatility
                :target-volatility 0.31}]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]
            [:effects/save
             [:portfolio-ui :optimizer :target-sigma-draft]
             nil]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/rerun-portfolio-optimizer-at-target-sigma state)))))

(deftest objective-menu-apply-remembers-sigma-across-kind-switches-test
  (let [draft-base {:universe [{:instrument-id "perp:BTC"}]
                    :return-model {:kind :historical-mean}
                    :metadata {:dirty? false}}
        state-with (fn [objective selection]
                     {:portfolio {:optimizer {:draft (assoc draft-base
                                                            :objective objective)}}
                      :portfolio-ui {:optimizer {:objective-menu-selection selection}}})]
    (is (= {:kind :max-sharpe
            :target-volatility 0.22}
           (applied-objective
            (actions/apply-portfolio-optimizer-objective-menu-selection-and-run
             (state-with {:kind :target-volatility :target-volatility 0.22}
                         :max-sharpe))))
        "switching away keeps the chosen sigma as a dormant key")
    (is (= {:kind :target-volatility
            :target-volatility 0.22}
           (applied-objective
            (actions/apply-portfolio-optimizer-objective-menu-selection-and-run
             (state-with {:kind :max-sharpe :target-volatility 0.22}
                         :target-volatility))))
        "switching back restores the remembered sigma instead of the 12% preset")))

(deftest scenario-load-clears-pending-target-sigma-test
  (let [state {:portfolio-ui {:optimizer {:target-sigma-draft 0.31
                                          :objective-menu-target-sigma 0.25}}}
        state* (scenario-state/apply-scenario-load-success
                state
                "scn_x"
                {:schema-version 1
                 :id "scn_x"
                 :status :saved
                 :config {:objective {:kind :target-volatility
                                      :target-volatility 0.22}}}
                100
                200)]
    (is (nil? (get-in state* [:portfolio-ui :optimizer :target-sigma-draft])))
    (is (nil? (get-in state* [:portfolio-ui :optimizer :objective-menu-target-sigma])))))

(deftest rerun-at-target-sigma-without-pending-is-a-no-op-test
  (is (= []
         (actions/rerun-portfolio-optimizer-at-target-sigma
          {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                           :objective {:kind :target-volatility
                                                       :target-volatility 0.12}}}}}))))
