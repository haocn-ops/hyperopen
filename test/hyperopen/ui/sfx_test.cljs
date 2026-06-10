(ns hyperopen.ui.sfx-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.ui.sfx :as sfx]))

(deftest fill-sound-noops-without-webaudio-test
  ;; Node test environment has no AudioContext; both variants must be
  ;; silent no-ops rather than throwing.
  (is (nil? (sfx/fill! true)))
  (is (nil? (sfx/fill! false))))

(deftest rekt-sound-noops-without-webaudio-test
  (is (nil? (sfx/rekt!))))

(deftest leverage-tick-tracks-tier-changes-test
  ;; first observation is silent, repeats are silent, changes tick (a
  ;; no-op here without WebAudio, but the state machine must not throw)
  (is (nil? (sfx/leverage-tick-on-change! 2 true)))
  (is (nil? (sfx/leverage-tick-on-change! 2 true)))
  (is (nil? (sfx/leverage-tick-on-change! 3 true)))
  (is (nil? (sfx/leverage-tick-on-change! 0 false))))
