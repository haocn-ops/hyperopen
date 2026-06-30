(ns hyperopen.portfolio.optimizer.exposure-actions-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.actions :as actions]
            [hyperopen.portfolio.optimizer.application.constraint-profiles :as profiles]
            [hyperopen.portfolio.optimizer.domain.exposure-policy :as policy]))

(def ^:private constraints-path [:portfolio :optimizer :draft :constraints])
(def ^:private dirty-path [:portfolio :optimizer :draft :metadata :dirty?])

(def ^:private base-constraints
  {:gross-max 2.0 :net-min 1.0 :net-max 1.0 :max-asset-weight 0.5})

(defn- state-with
  [constraints]
  {:portfolio {:optimizer {:draft {:constraints constraints}}}})

(defn- expect-write
  "The save-many effect a handler emits when it writes the whole constraints map."
  [constraints]
  [[:effects/save-many
    [[constraints-path constraints]
     [dirty-path true]]]])

(deftest exposure-point-writes-the-policy-derived-constraints-test
  (let [bounds {:left 0.0 :top 0.0 :width 100.0 :height 100.0}
        ;; centre drag ⇒ gross-target 1.5, net-target 0.0; bands stay 0
        expected (policy/apply-point base-constraints {:gross-target 1.5 :net-target 0.0})]
    (is (= (expect-write expected)
           (actions/set-portfolio-optimizer-exposure-point
            (state-with base-constraints) 50.0 50.0 bounds 1)))
    (is (= {:gross-max 1.5 :net-min 0.0 :net-max 0.0 :max-asset-weight 0.5} expected)
        "centre drag caps gross at 1.5 and flattens net, with no floor")))

(deftest exposure-point-hover-is-a-no-op-test
  (let [bounds {:left 0.0 :top 0.0 :width 100.0 :height 100.0}]
    (is (= [] (actions/set-portfolio-optimizer-exposure-point
               (state-with base-constraints) 50.0 50.0 bounds 0))
        "a pointer move with no button pressed must not rewrite the draft")))

(deftest exposure-band-writes-one-axis-test
  (testing "net band widens net-min/net-max symmetrically"
    (let [expected (policy/apply-band base-constraints :net 0.25)]
      (is (= (expect-write expected)
             (actions/set-portfolio-optimizer-exposure-band
              (state-with base-constraints) :net "0.25")))
      (is (= 0.75 (:net-min expected)))
      (is (= 1.25 (:net-max expected)))))
  (testing "an unknown axis or non-number is a no-op"
    (is (= [] (actions/set-portfolio-optimizer-exposure-band
               (state-with base-constraints) :sideways "0.1")))
    (is (= [] (actions/set-portfolio-optimizer-exposure-band
               (state-with base-constraints) :net "not-a-number")))))

(deftest exposure-preset-writes-the-merged-preset-test
  (let [expected (policy/apply-preset base-constraints :long-bias)]
    (is (= (expect-write expected)
           (actions/apply-portfolio-optimizer-exposure-preset
            (state-with base-constraints) :long-bias)))
    (is (= :long-bias (policy/active-preset expected))))
  (is (= [] (actions/apply-portfolio-optimizer-exposure-preset
             (state-with base-constraints) :nonsense))
      "an unknown preset is a no-op"))

(deftest save-constraint-default-emits-persist-effect-test
  (is (= [[:effects/save-portfolio-optimizer-constraint-default]]
         (actions/save-portfolio-optimizer-constraint-default (state-with base-constraints)))
      "the action is pure; the effect reads the draft, stamps time, and persists"))

(deftest apply-constraint-default-writes-remembered-constraints-test
  (let [remembered {:gross-max 1.5 :net-min 0.0 :net-max 0.0 :max-asset-weight 0.3}
        universe [{:instrument-id "perp:BTC"} {:instrument-id "perp:ETH"}]
        uk (profiles/universe-key universe)
        state {:portfolio {:optimizer
                           {:draft {:constraints base-constraints :universe universe}
                            :constraint-profiles nil}}}]
    (testing "no profile for this universe ⇒ no-op"
      (is (= [] (actions/apply-portfolio-optimizer-constraint-default state))))
    (testing "a saved profile is written to the draft"
      (let [state* (assoc-in state [:portfolio :optimizer :constraint-profiles]
                             {uk {:universe-key uk :controls remembered}})]
        (is (= (expect-write remembered)
               (actions/apply-portfolio-optimizer-constraint-default state*)))))))

(deftest reset-constraints-to-system-restores-defaults-test
  (let [out (actions/reset-portfolio-optimizer-constraints-to-system
             (state-with {:gross-min 1.5 :gross-max 1.6 :net-min 1.4 :net-max 1.6
                          :max-asset-weight 0.2}))
        written (-> out first second first second)]
    (is (= 2.0 (:gross-max written)) "system default gross ceiling restored")
    (is (not (contains? written :gross-min)) "system default has no gross floor")
    (is (= 0.5 (:max-asset-weight written)))
    (is (= [dirty-path true] (-> out first second second)))))
