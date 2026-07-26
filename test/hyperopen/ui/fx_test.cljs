(ns hyperopen.ui.fx-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.ui.fx :as fx]))

(deftest fx-noop-without-dom-test
  ;; Node test environment may lack a browser document; both effects
  ;; must degrade to silent no-ops (and never throw) regardless.
  (is (nil? (fx/confetti!)))
  (is (nil? (fx/confetti! {:pieces 5})))
  (is (nil? (fx/rekt-overlay!)))
  (is (nil? (fx/rekt-overlay! {:quip "test quip"}))))
