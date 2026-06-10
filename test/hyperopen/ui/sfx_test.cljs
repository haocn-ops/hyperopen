(ns hyperopen.ui.sfx-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.ui.sfx :as sfx]))

(deftest fill-sound-noops-without-webaudio-test
  ;; Node test environment has no AudioContext; both variants must be
  ;; silent no-ops rather than throwing.
  (is (nil? (sfx/fill! true)))
  (is (nil? (sfx/fill! false))))
