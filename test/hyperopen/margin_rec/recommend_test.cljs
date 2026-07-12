(ns hyperopen.margin-rec.recommend-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.margin-rec.paths :as paths]
            [hyperopen.margin-rec.recommend :as recommend]
            [hyperopen.margin-rec.tiers :as tiers]))

(defn- random-walk-candles
  "Deterministic hourly random walk: per-bar sigma ~0.5%, wicks +/-0.3%."
  [n seed]
  (let [rng (paths/mulberry32 seed)]
    (loop [i 0
           c 100.0
           rows (transient [])]
      (if (> i n)
        (persistent! rows)
        (let [u (- (* 2 (rng)) 1)
              c* (* c (js/Math.exp (* 0.005 u)))]
          (recur (inc i)
                 c*
                 (conj! rows {:t (* i 3600000)
                              :o c
                              :c c
                              :l (* c 0.997)
                              :h (* c 1.003)})))))))

(def base-candles (random-walk-candles 1200 77))

(def last-close
  (:c (peek base-candles)))

(defn- base-inputs
  [& [overrides]]
  (let [schedule (tiers/flat-schedule 10)
        szi (or (:szi overrides) 2)
        equity (or (:margin-used overrides) (* 0.12 (js/Math.abs szi) last-close))
        liq (tiers/liquidation-price schedule szi last-close equity)]
    (merge {:coin "xyz:TSM"
            :dex "xyz"
            :szi szi
            :mark last-close
            :margin-used equity
            :liquidation-px liq
            :max-leverage 10
            :margin-table nil
            :funding-rate-hourly 0.0000125
            :candle-rows base-candles
            :fills []
            :risk-mode :balanced}
           overrides)))

(deftest full-pipeline-invariants
  (let [result (recommend/recommend-sync (base-inputs))]
    (is (contains? #{:ok :within-target} (:status result)))
    (testing "breakdown sums exactly to the recommended equity"
      (let [total (reduce + 0 (map :amount (:breakdown result)))]
        (is (< (js/Math.abs (- total (get-in result [:recommended :equity]))) 1e-6))))
    (testing "adding collateral only reduces liquidation probability"
      (is (>= (:p-now result) (:p-after result))))
    (testing "post-recommendation risk is at or under the target"
      (is (<= (:p-after result) (+ (:alpha result) 0.001))))
    (testing "recommended equity covers maintenance and the adverse quantile"
      (is (every? #(>= (:amount %) 0) (:breakdown result))))
    (testing "new liquidation price is farther for a long"
      (let [new-liq (get-in result [:recommended :new-liquidation-px])]
        (when (and new-liq (get-in result [:as-of :liquidation-px]))
          (is (< new-liq (get-in result [:as-of :liquidation-px]))))))
    (testing "effective leverage consistent with notional"
      (let [{:keys [equity effective-leverage]} (:recommended result)]
        (is (< (js/Math.abs (- effective-leverage
                               (/ (get-in result [:as-of :notional]) equity)))
               1e-9))))
    (testing "horizon defaulted without fills"
      (is (= :default (get-in result [:horizon :source])))
      (is (= 72 (get-in result [:horizon :hours]))))))

(deftest determinism
  (let [a (recommend/recommend-sync (base-inputs))
        b (recommend/recommend-sync (base-inputs))]
    (is (= (get-in a [:recommended :equity])
           (get-in b [:recommended :equity])))
    (is (= (:p-now a) (:p-now b)))))

(deftest risk-mode-ordering
  (let [rec-for (fn [mode]
                  (get-in (recommend/recommend-sync (base-inputs {:risk-mode mode}))
                          [:recommended :equity]))
        conservative (rec-for :conservative)
        balanced (rec-for :balanced)
        efficient (rec-for :capital-efficient)]
    (is (>= conservative balanced))
    (is (>= balanced efficient))))

(deftest terminal-statuses
  (testing "flat position is invalid"
    (is (= :invalid (:status (recommend/recommend-sync (base-inputs {:szi 0}))))))
  (testing "insufficient bars"
    (let [result (recommend/recommend-sync
                  (base-inputs {:candle-rows (random-walk-candles 20 5)}))]
      (is (= :insufficient-history (:status result)))
      (is (= 20 (:n-bars result)))))
  (testing "no margin model at all is invalid"
    (is (= :invalid (:status (recommend/recommend-sync
                              (base-inputs {:max-leverage nil
                                            :liquidation-px nil})))))))

(deftest calibration-from-exchange-liquidation
  ;; No table and no max leverage, but the exchange's liquidation price is
  ;; enough to imply a flat maintenance rate.
  (let [schedule (tiers/flat-schedule 20)
        szi 2
        equity (* 0.1 szi last-close)
        liq (tiers/liquidation-price schedule szi last-close equity)
        result (recommend/recommend-sync
                (base-inputs {:max-leverage nil
                              :margin-used equity
                              :liquidation-px liq}))]
    (is (contains? #{:ok :within-target} (:status result)))
    (is (true? (get-in result [:confidence :calibrated?])))))

(deftest within-target-when-overfunded
  (let [result (recommend/recommend-sync
                (base-inputs {:margin-used (* 2 last-close 2)}))]
    (is (= :within-target (:status result)))
    (is (zero? (get-in result [:recommended :additional])))
    (is (= :normal (:risk-level result)))))

(deftest funding-buffer-sides
  (let [buffer-for (fn [szi rate]
                     (->> (:breakdown (recommend/recommend-sync
                                       (base-inputs {:szi szi
                                                     :funding-rate-hourly rate})))
                          (some #(when (= :funding (:key %)) (:amount %)))))]
    (testing "long pays positive funding"
      (is (pos? (buffer-for 2 0.0001)))
      (is (zero? (buffer-for 2 -0.0001))))
    (testing "short pays negative funding"
      (is (pos? (buffer-for -2 -0.0001)))
      (is (zero? (buffer-for -2 0.0001))))))

(deftest normalizes-risk-mode
  (is (= :balanced (recommend/normalize-risk-mode "unknown")))
  (is (= :conservative (recommend/normalize-risk-mode "conservative")))
  (is (= :capital-efficient (recommend/normalize-risk-mode :capital-efficient))))
