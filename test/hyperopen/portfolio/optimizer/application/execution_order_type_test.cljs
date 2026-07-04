(ns hyperopen.portfolio.optimizer.application.execution-order-type-test
  "The per-order routing policy is the single source for the type the Execution table
  displays AND the type the wire order carries, so its rules are pinned here directly:
  cost-aware passive protection first (a 'Recommended' that markets through a 45bp
  spread is a trust-breaking lie), then the clip-size/side rules."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.application.execution-order-type
             :as order-type]))

(defn- perp-buy
  [notional & [cost-bps]]
  (cond-> {:row-id "perp:X"
           :instrument-id "perp:X"
           :instrument-type :perp
           :side :buy
           :delta-notional-usd notional}
    (some? cost-bps) (assoc :cost {:slippage-bps cost-bps})))

(deftest recommend-exec-type-cost-aware-test
  (testing "a small clip whose estimated crossing cost is high posts passively"
    ;; The motivating case: an $87 buy with a 45.4bp market cost must not route :market.
    (is (= :passive (order-type/recommend-exec-type (perp-buy 87 45.4))))
    ;; Exactly at the threshold counts as high-cost (flat 25bp fallback rows included).
    (is (= :passive (order-type/recommend-exec-type (perp-buy 18 order-type/high-cost-crossing-bps))))
    (is (= :passive (order-type/recommend-exec-type (perp-buy 18 25)))))
  (testing "a small clip with a cheap crossing stays market"
    (is (= :market (order-type/recommend-exec-type (perp-buy 130 0.3))))
    (is (= :market (order-type/recommend-exec-type (perp-buy 95 8.1)))))
  (testing "a row with no cost estimate falls through to the size rules"
    (is (= :market (order-type/recommend-exec-type (perp-buy 5000))))
    (is (= :passive (order-type/recommend-exec-type (perp-buy 30000))))))

(deftest recommend-exec-type-size-and-side-rules-test
  (testing "very large clips slice over time regardless of cost"
    (is (= :twap (order-type/recommend-exec-type (perp-buy 70000 45))))
    (is (= :twap (order-type/recommend-exec-type (perp-buy 120000 0)))))
  (testing "spot sells rest as limits"
    (is (= :limit (order-type/recommend-exec-type
                   {:instrument-type :spot :side :sell :delta-notional-usd -8
                    :cost {:slippage-bps 30}}))))
  (testing "medium perp clips post passively"
    (is (= :passive (order-type/recommend-exec-type (perp-buy 25000 1))))))

(deftest high-cost-crossing-row-predicate-test
  (is (true? (order-type/high-cost-crossing-row? (perp-buy 87 45.4))))
  (is (false? (order-type/high-cost-crossing-row? (perp-buy 87 8))))
  (is (false? (order-type/high-cost-crossing-row? (perp-buy 87)))
      "no estimate is not high-cost — the size rules decide")
  (is (nil? (order-type/crossing-cost-bps (perp-buy 87 js/NaN)))
      "a non-finite estimate reads as no estimate"))

(deftest effective-type-override-precedence-test
  (let [row (perp-buy 87 45.4)]
    (testing "a per-row override beats the recommendation"
      (is (= :market (order-type/effective-type
                      {:default-order-type :recommended
                       :overrides {"perp:X" :market}}
                      row))))
    (testing ":recommended default expands through the cost-aware policy"
      (is (= :passive (order-type/effective-type
                       {:default-order-type :recommended :overrides {}}
                       row))))
    (testing "a concrete default applies verbatim"
      (is (= :twap (order-type/effective-type
                    {:default-order-type :twap :overrides {}}
                    row))))))
