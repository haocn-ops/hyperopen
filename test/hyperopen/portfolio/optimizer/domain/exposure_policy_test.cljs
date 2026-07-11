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

(deftest fit-level-frames-policy-without-headroom-test
  (testing "small policy + no current exposure ⇒ the floor level"
    (is (= 0 (policy/fit-level {:gross-target 2.0 :gross-band 0.0
                                :net-target 1.0 :net-band 0.0}))))
  (testing "a target dragged exactly to the visible max still fits its own level (no headroom),
            so a drag can never force a re-fit"
    (is (= 0 (policy/fit-level {:gross-target 3.0 :gross-band 0.0 :net-target 0.0})))
    (is (= 0 (policy/fit-level {:gross-target 2.5 :gross-band 0.5 :net-target 0.0}))))
  (testing "a gross need beyond a level steps to the next paired level"
    (is (= 1 (policy/fit-level {:gross-target 4.0 :gross-band 0.0})))
    (is (= 2 (policy/fit-level {:gross-target 6.0 :gross-band 0.5}))))
  (testing "the current portfolio exposure also expands the frame"
    (is (= 2 (policy/fit-level {:gross-target 2.0 :current-gross 8.0}))))
  (testing "a wide long/short bias raises the level through the paired net extent"
    (is (= 1 (policy/fit-level {:net-target 2.5 :net-band 0.0}))))
  (testing "beyond the largest level nothing fits (the overflow case)"
    (is (nil? (policy/fit-level {:gross-target 55.0 :gross-band 0.0})))))

(deftest render-axis-is-fixed-and-only-widens-test
  (testing "no stored zoom ⇒ the fit level's paired axes"
    (let [{:keys [axis level fit-level zoom-in-level zoom-out-level]}
          (policy/render-axis {:gross-target 2.0 :net-target 1.0} nil)]
      (is (= {:gross-max 3.0 :net-extent 2.0} axis))
      (is (= 0 level))
      (is (= 0 fit-level))
      (is (nil? zoom-in-level) "already at the tightest level that fits")
      (is (= 1 zoom-out-level))))
  (testing "a stored zoom widens the view and exposes a zoom-in step back toward fit"
    (let [{:keys [axis level zoom-in-level zoom-out-level]}
          (policy/render-axis {:gross-target 2.0 :net-target 1.0} 3)]
      (is (= {:gross-max 20.0 :net-extent 10.0} axis))
      (is (= 3 level))
      (is (= 2 zoom-in-level))
      (is (= 4 zoom-out-level))))
  (testing "the largest level disables zooming out"
    (is (nil? (:zoom-out-level (policy/render-axis {:gross-target 2.0 :net-target 1.0}
                                                   policy/max-zoom-level)))))
  (testing "a stored zoom below fit is ignored — zooming in can never clip the band box"
    (let [{:keys [axis level zoom-in-level]}
          (policy/render-axis {:gross-target 6.0 :gross-band 0.5 :net-target 1.0} 0)]
      (is (= {:gross-max 10.0 :net-extent 5.0} axis))
      (is (= 2 level))
      (is (nil? zoom-in-level))))
  (testing "an unfittable policy renders a computed overflow scale with zoom disabled"
    (let [{:keys [axis level zoom-in-level zoom-out-level]}
          (policy/render-axis {:gross-target 55.0 :net-target 0.0} 2)]
      (is (= 60.0 (:gross-max axis)))
      (is (nil? level))
      (is (nil? zoom-in-level))
      (is (nil? zoom-out-level)))))

(deftest point->targets-honors-the-baked-scale-test
  (let [bounds {:left 0.0 :top 0.0 :width 100.0 :height 100.0}]
    (testing "top of a 10x-scaled pad yields gross 10x, not the 3x floor"
      (is (= 10.0 (:gross-target (policy/point->targets
                                  {:client-x 50.0 :client-y 0.0 :bounds bounds :buttons 1
                                   :gross-axis-max 10.0 :net-axis-extent 2.0})))))
    (testing "a missing scale falls back to the floor"
      (is (= 3.0 (:gross-target (policy/point->targets
                                 {:client-x 50.0 :client-y 0.0 :bounds bounds :buttons 1})))))))

(deftest point->targets-keeps-the-band-box-inside-the-view-test
  ;; `target + band ≤ axis max` per axis: the fixpoint that keeps an edge drag from ever
  ;; forcing the scale to re-fit mid-gesture (the old adaptive axis ratcheted 3×→5×→10×→…).
  (let [bounds {:left 0.0 :top 0.0 :width 100.0 :height 100.0}]
    (testing "a positive gross band lowers the reachable gross ceiling"
      (is (= 2.5 (:gross-target (policy/point->targets
                                 {:client-x 50.0 :client-y 0.0 :bounds bounds :buttons 1
                                  :gross-band 0.5})))))
    (testing "a positive net band pulls the reachable net edges inward"
      (is (= 1.5 (:net-target (policy/point->targets
                               {:client-x 100.0 :client-y 0.0 :bounds bounds :buttons 1
                                :net-band 0.5}))))
      (is (= -1.5 (:net-target (policy/point->targets
                                {:client-x 0.0 :client-y 0.0 :bounds bounds :buttons 1
                                 :net-band 0.5})))))
    (testing "band 0 keeps the full range reachable"
      (is (= 3.0 (:gross-target (policy/point->targets
                                 {:client-x 50.0 :client-y 0.0 :bounds bounds :buttons 1
                                  :gross-band 0.0})))))
    (testing "net reach also respects the gross reach: an oversized gross band (advanced raw
              fields are not capped at max-band) cannot let the gross ≥ |net| lift push
              target + band past the axis max"
      ;; gross-reach = 3 − 1.5 = 1.5, so net clamps to ±1.5 and the lifted gross stays 1.5:
      ;; 1.5 + 1.5 = 3.0 ≤ axis max — no mid-drag re-fit.
      (let [out (policy/point->targets {:client-x 100.0 :client-y 0.0 :bounds bounds :buttons 1
                                        :gross-band 1.5})]
        (is (= 1.5 (:net-target out)))
        (is (= 1.5 (:gross-target out)))))))

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

(deftest engine-constraints-policy-derives-the-same-targets-test
  ;; The request builder renames the draft keys before the engine sees them;
  ;; engine-constraints->policy must recover the SAME targets constraints->policy
  ;; derives from the draft keys — one midpoint semantics, two key spellings.
  (is (= (policy/constraints->policy {:gross-max 2.0 :net-min 1.0 :net-max 1.0})
         (policy/engine-constraints->policy {:gross-leverage 2.0
                                             :net-exposure {:min 1.0 :max 1.0}}))
      "zero band: gross target IS the ceiling")
  (is (= (policy/constraints->policy {:gross-max 3.0 :gross-min 1.0
                                      :net-min -0.5 :net-max 1.5})
         (policy/engine-constraints->policy {:gross-leverage 3.0
                                             :gross-floor 1.0
                                             :net-exposure {:min -0.5 :max 1.5}}))
      "banded: targets are the midpoints, never the ceilings")
  (is (= {:gross-target 2.0 :gross-band 1.0 :net-target 0.5 :net-band 1.0}
         (policy/engine-constraints->policy {:gross-leverage 3.0
                                             :gross-floor 1.0
                                             :net-exposure {:min -0.5 :max 1.5}}))))
