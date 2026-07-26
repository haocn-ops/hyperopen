(ns hyperopen.portfolio.montecarlo.actions-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.montecarlo.actions :as mc]))

(deftest normalize-sims-test
  (is (= 2500 (mc/normalize-sims 2500)))
  (is (= 1000 (mc/normalize-sims 1000.0)) "floats are rounded to a valid option")
  (is (= 250 (mc/normalize-sims "250")) "numeric strings are accepted")
  (is (= 1000 (mc/normalize-sims 999)) "non-options fall back to the default")
  (is (= 1000 (mc/normalize-sims "junk"))))

(deftest normalize-horizon-test
  (is (= 24 (mc/normalize-horizon 24)))
  (is (= 3 (mc/normalize-horizon 3)))
  (is (= 12 (mc/normalize-horizon 45)) "non-option months fall back to the default"))

(deftest normalize-bust-clamps-test
  (is (= -30 (mc/normalize-bust -30)))
  (is (= -95 (mc/normalize-bust -200)) "clamped to the floor")
  (is (= -1 (mc/normalize-bust 5)) "positive values clamp up to -1")
  (is (= -30 (mc/normalize-bust "-30%")) "percent-suffixed strings parse")
  (is (= -30 (mc/normalize-bust "abc")) "unparseable falls back to default"))

(deftest normalize-goal-clamps-test
  (is (= 50 (mc/normalize-goal 50)))
  (is (= 1 (mc/normalize-goal -10)) "clamped to the floor")
  (is (= 500 (mc/normalize-goal 9999)) "clamped to the ceiling"))

(deftest normalize-seed-clamps-test
  (is (= 42 (mc/normalize-seed 42)))
  (is (= 0 (mc/normalize-seed -5)))
  (is (= 9999 (mc/normalize-seed 100000))))

(deftest normalize-method-test
  (is (= :shuffle (mc/normalize-method :shuffle)))
  (is (= :bootstrap (mc/normalize-method :bootstrap)))
  (is (= :bootstrap (mc/normalize-method "bootstrap")) "string method keys are accepted")
  (is (= :shuffle (mc/normalize-method :nonsense)) "unknown methods fall back to the default")
  (is (= :shuffle (mc/normalize-method nil)))
  (is (= :shuffle (mc/normalize-control :method "junk"))
      "normalize-control routes :method through normalize-method"))

(deftest set-control-returns-normalized-save-effect-test
  (is (= [[:effects/save [:portfolio-ui :monte-carlo :bust] -95]]
         (mc/set-portfolio-monte-carlo-control {} :bust -200))
      "value is normalized before saving")
  (is (= [[:effects/save [:portfolio-ui :monte-carlo :sims] 2500]]
         (mc/set-portfolio-monte-carlo-control {} "sims" 2500))
      "string control keys are accepted")
  (is (nil? (mc/set-portfolio-monte-carlo-control {} :unknown 5))
      "unknown controls are ignored"))

(deftest rerun-bumps-run-nonce-test
  (is (= [[:effects/save [:portfolio-ui :monte-carlo :run-nonce] 1]]
         (mc/rerun-portfolio-monte-carlo {})))
  (is (= [[:effects/save [:portfolio-ui :monte-carlo :run-nonce] 5]]
         (mc/rerun-portfolio-monte-carlo {:portfolio-ui {:monte-carlo {:run-nonce 4}}}))))

(deftest controls-reader-fills-defaults-test
  (is (= mc/default-controls (mc/controls {})))
  (is (= {:run-nonce 0 :method :shuffle :sims 2500 :horizon 12 :bust -30 :goal 200 :seed 42}
         (mc/controls {:portfolio-ui {:monte-carlo {:sims 2500 :goal 200}}}))
      "stored values override defaults and are normalized")
  (is (= :bootstrap (:method (mc/controls {:portfolio-ui {:monte-carlo {:method :bootstrap}}})))
      "a stored method is read back"))
