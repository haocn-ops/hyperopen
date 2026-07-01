(ns hyperopen.portfolio.optimizer.domain.exposure-policy-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.domain.exposure-policy :as policy]))

(def ^:private default-constraints
  ;; Mirrors defaults/default-draft :constraints for the keys this namespace touches:
  ;; gross-max 2.0, no gross floor, net 1.0/1.0, cap 0.5.
  {:long-only? false
   :include-spot? false
   :gross-max 2.0
   :net-min 1.0
   :net-max 1.0
   :max-asset-weight 0.5})

(deftest constraints->policy-derives-default-targets-and-zero-bands-test
  (let [{:keys [gross-target gross-band net-target net-band]}
        (policy/constraints->policy default-constraints)]
    (is (= 2.0 gross-target) "gross target is the ceiling when there is no floor")
    (is (= 0.0 gross-band) "no gross floor ⇒ zero gross band")
    (is (= 1.0 net-target))
    (is (= 0.0 net-band))))

(deftest constraints->policy-handles-a-seeded-gross-floor-test
  ;; The screenshot case: gross 1.91..1.92, net 1.31..1.42.
  (let [{:keys [gross-target gross-band net-target net-band]}
        (policy/constraints->policy {:gross-min 1.91 :gross-max 1.92
                                     :net-min 1.31 :net-max 1.42})]
    (is (= 1.915 gross-target))
    (is (= 0.005 gross-band))
    (is (= 1.365 net-target))
    (is (= 0.055 net-band))))

(deftest policy->constraints-round-trips-and-preserves-no-floor-test
  (testing "zero gross band clears the floor (dissoc, not nil)"
    (let [out (policy/policy->constraints default-constraints
                                          (policy/constraints->policy default-constraints))]
      (is (not (contains? out :gross-min))
          "a zero gross band must DISSOC :gross-min so the solver sees no floor")
      (is (= 2.0 (:gross-max out)))
      (is (= 1.0 (:net-min out)))
      (is (= 1.0 (:net-max out)))))
  (testing "a positive gross band writes a floor and round-trips"
    (let [seeded {:gross-min 1.91 :gross-max 1.92 :net-min 1.31 :net-max 1.42}
          out (policy/policy->constraints seeded (policy/constraints->policy seeded))]
      (is (= 1.91 (:gross-min out)))
      (is (= 1.92 (:gross-max out)))
      (is (= 1.31 (:net-min out)))
      (is (= 1.42 (:net-max out))))))

(deftest apply-point-moves-targets-and-keeps-bands-test
  (let [seeded {:gross-min 1.0 :gross-max 2.0 :net-min 0.8 :net-max 1.2 :max-asset-weight 0.5}
        ;; bands: gross 0.5, net 0.2; move targets to gross 2.5, net 0.5
        out (policy/apply-point seeded {:gross-target 2.5 :net-target 0.5})]
    (is (= 3.0 (:gross-max out)) "gross-max = target 2.5 + band 0.5")
    (is (= 2.0 (:gross-min out)) "gross-min = target 2.5 - band 0.5")
    (is (= 0.3 (:net-min out)) "net-min = target 0.5 - band 0.2")
    (is (= 0.7 (:net-max out)) "net-max = target 0.5 + band 0.2")
    (is (= 0.5 (:max-asset-weight out)) "unrelated keys are preserved")))

(deftest apply-band-widens-one-axis-and-clamps-test
  (let [out (policy/apply-band default-constraints :net 0.25)]
    (is (= 0.75 (:net-min out)))
    (is (= 1.25 (:net-max out)))
    (is (not (contains? out :gross-min)) "net band change leaves gross floor absent"))
  (testing "a positive gross band introduces a floor"
    (let [out (policy/apply-band default-constraints :gross 0.1)]
      (is (= 1.9 (:gross-min out)))
      (is (= 2.1 (:gross-max out)))))
  (testing "bands clamp to max-band"
    (let [out (policy/apply-band default-constraints :net 5.0)]
      (is (= (- 1.0 policy/max-band) (:net-min out)))
      (is (= (+ 1.0 policy/max-band) (:net-max out))))))

(deftest point->targets-maps-fractions-and-ignores-hover-test
  (let [bounds {:left 0.0 :top 0.0 :width 100.0 :height 100.0}]
    (testing "centre of the pad while dragging"
      (is (= {:gross-target 1.5 :net-target 0.0}
             (policy/point->targets {:client-x 50.0 :client-y 50.0
                                     :bounds bounds :buttons 1}))))
    (testing "top-right corner: max gross, max long"
      (is (= {:gross-target 3.0 :net-target 2.0}
             (policy/point->targets {:client-x 100.0 :client-y 0.0
                                     :bounds bounds :buttons 1}))))
    (testing "gross is clamped to at least |net|"
      ;; bottom-right: fy=1 ⇒ gross 0, but net 2.0 forces gross up to 2.0
      (is (= {:gross-target 2.0 :net-target 2.0}
             (policy/point->targets {:client-x 100.0 :client-y 100.0
                                     :bounds bounds :buttons 1}))))
    (testing "no pressed button ⇒ nil (a hover, not a drag)"
      (is (nil? (policy/point->targets {:client-x 50.0 :client-y 50.0
                                        :bounds bounds :buttons 0}))))
    (testing "degenerate bounds ⇒ nil"
      (is (nil? (policy/point->targets {:client-x 50.0 :client-y 50.0
                                        :bounds {:left 0 :top 0 :width 0 :height 0}
                                        :buttons 1}))))))

(deftest axis-scale-is-adaptive-and-uncapped-test
  (testing "small policy + no current exposure ⇒ floor scale"
    (is (= {:gross-max policy/gross-axis-floor :net-extent policy/net-axis-floor}
           (policy/axis-scale {:gross-target 2.0 :gross-band 0.0 :net-target 1.0 :net-band 0.0}))))
  (testing "a gross target beyond the floor grows the axis to the next nice step"
    (is (= 10.0 (:gross-max (policy/axis-scale {:gross-target 6.0 :gross-band 0.5}))))
    (is (= 5.0 (:gross-max (policy/axis-scale {:gross-target 4.0 :gross-band 0.0})))))
  (testing "the current portfolio exposure also expands the frame"
    (is (= 10.0 (:gross-max (policy/axis-scale {:gross-target 2.0 :current-gross 8.0})))))
  (testing "net extent grows with a wide long/short bias"
    (is (= 3.0 (:net-extent (policy/axis-scale {:net-target 2.5 :net-band 0.0})))))
  (testing "there is no hard cap — huge leverage rounds up past the largest step"
    (is (<= 60.0 (:gross-max (policy/axis-scale {:gross-target 55.0 :gross-band 0.0}))))))

(deftest point->targets-honors-the-baked-scale-test
  (let [bounds {:left 0.0 :top 0.0 :width 100.0 :height 100.0}]
    (testing "top of a 10x-scaled pad yields gross 10x, not the 3x floor"
      (is (= 10.0 (:gross-target (policy/point->targets
                                  {:client-x 50.0 :client-y 0.0 :bounds bounds :buttons 1
                                   :gross-axis-max 10.0 :net-axis-extent 2.0})))))
    (testing "a missing scale falls back to the floor"
      (is (= 3.0 (:gross-target (policy/point->targets
                                 {:client-x 50.0 :client-y 0.0 :bounds bounds :buttons 1})))))))

(deftest presets-apply-and-are-detected-test
  (testing "each preset applies its partial and clears the gross floor"
    (let [out (policy/apply-preset {:gross-min 1.5 :gross-max 1.5} :balanced)]
      (is (not (contains? out :gross-min)))
      (is (= 2.0 (:gross-max out)))
      (is (= 1.0 (:net-min out)))
      (is (= 1.0 (:net-max out)))
      (is (= 0.5 (:max-asset-weight out)))))
  (testing "active-preset detects an applied preset and falls back to :custom"
    (is (= :balanced (policy/active-preset (policy/apply-preset {} :balanced))))
    (is (= :conservative (policy/active-preset (policy/apply-preset {} :conservative))))
    (is (= :long-bias (policy/active-preset (policy/apply-preset {} :long-bias))))
    (is (= :balanced (policy/active-preset default-constraints))
        "the system default constraints ARE the Balanced preset by design")
    (is (= :custom (policy/active-preset {:gross-max 2.5 :net-min 0.4 :net-max 0.6
                                          :max-asset-weight 0.5}))
        "values that match no preset read as :custom")
    (is (= :custom (policy/active-preset (assoc (policy/apply-preset {} :balanced)
                                                :gross-min 1.0)))
        "a gross floor disqualifies a ceiling-only preset match")))

(deftest plotting-helpers-place-markers-test
  (let [marker (policy/target-marker {:gross-target 1.5 :net-target 0.0})]
    (is (= 0.5 (:x marker)) "net 0 is centre-x")
    (is (= 0.5 (:y marker)) "gross 1.5 of 3.0 is centre-y"))
  (let [rect (policy/band-rect {:gross-target 1.5 :gross-band 0.0
                                :net-target 0.0 :net-band 0.5})]
    (is (= 0.0 (:h rect)) "zero gross band ⇒ flat box")
    (is (< 0.0 (:w rect)) "net band ⇒ box has width"))
  (is (nil? (policy/current-exposure-marker {:gross nil :net 1.0})))
  (is (some? (policy/current-exposure-marker {:gross 1.8 :net 1.2}))))
