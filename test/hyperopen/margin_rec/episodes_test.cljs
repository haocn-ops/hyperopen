(ns hyperopen.margin-rec.episodes-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.margin-rec.episodes :as episodes]))

(def hour 3600000)

(defn- fill
  [coin t side sz & [start-position]]
  (cond-> {:coin coin
           :time (* t hour)
           :side side
           :sz (str sz)}
    (some? start-position) (assoc :startPosition (str start-position))))

(defn- fill-ms
  "Like `fill` but `t` is milliseconds and an order id can be attached."
  [coin t-ms side sz start-position oid]
  (cond-> {:coin coin :time t-ms :side side :sz (str sz)
           :startPosition (str start-position)}
    (some? oid) (assoc :oid oid)))

(deftest normalize-fill-shapes
  (is (= {:coin "TSM" :time-ms 0 :delta 2 :start-position 0 :order-id 42}
         (episodes/normalize-fill {:coin "TSM" :time 0 :side "B" :sz "2"
                                   :startPosition "0" :oid 42})))
  (is (nil? (:order-id (episodes/normalize-fill {:coin "TSM" :time 0 :side "B" :sz "2"}))))
  (is (= -1.5 (:delta (episodes/normalize-fill {:coin "X" :time 5 :side "A" :sz 1.5}))))
  (is (nil? (episodes/normalize-fill {:coin "X" :time 5 :side "?" :sz 1})))
  (is (nil? (episodes/normalize-fill {:coin "" :time 5 :side "B" :sz 1}))))

(deftest coalesce-fills-collapses-multi-fill-orders
  (testing "partial fills sharing an order id merge into one intervention"
    (let [rows (keep episodes/normalize-fill
                     (for [i (range 12)]
                       (fill-ms "TSM" (+ (* 3 24 hour) (* i 8000)) "A" 1 (- 12 i) 7001)))
          merged (episodes/coalesce-fills rows)]
      (is (= 1 (count merged)))
      (is (= -12 (:delta (first merged))))
      ;; keeps the earliest time and the pre-order start position
      (is (= (* 3 24 hour) (:time-ms (first merged))))
      (is (= 12 (:start-position (first merged))))))
  (testing "without ids, same-direction fills within the window merge"
    (let [rows (keep episodes/normalize-fill
                     (for [i (range 5)]
                       (fill-ms "ETH" (* i 10000) "B" 2 (* i 2) nil)))]
      (is (= 1 (count (episodes/coalesce-fills rows))))
      (is (= 10 (:delta (first (episodes/coalesce-fills rows)))))))
  (testing "different orders and opposite directions stay separate"
    (let [rows (keep episodes/normalize-fill
                     [(fill-ms "TSM" 0 "B" 5 0 100)          ;; order 100 buy
                      (fill-ms "TSM" 1000 "A" 2 5 200)        ;; order 200 sell (different id)
                      (fill-ms "TSM" 2000 "A" 1 3 200)])]     ;; continues order 200
      (is (= 2 (count (episodes/coalesce-fills rows))))))
  (testing "same-direction fills beyond the window stay separate"
    (let [rows (keep episodes/normalize-fill
                     [(fill-ms "X" 0 "A" 1 5 nil)
                      (fill-ms "X" (* 2 60000) "A" 1 4 nil)])] ;; 2 min apart, no id
      (is (= 2 (count (episodes/coalesce-fills rows))))))
  (testing "one order id merges even when it fills across a long span"
    ;; A resting limit order can fill in pieces minutes apart; the shared id
    ;; keeps it a single intervention regardless of the window.
    (let [rows (keep episodes/normalize-fill
                     [(fill-ms "X" 0 "B" 1 0 555)
                      (fill-ms "X" (* 10 60000) "B" 1 1 555)])] ;; 10 min apart, same id
      (is (= 1 (count (episodes/coalesce-fills rows))))
      (is (= 2 (:delta (first (episodes/coalesce-fills rows))))))))

(deftest horizon-not-collapsed-by-multi-fill-close
  ;; The reported bug: hold positions for hours/days, then close each with one
  ;; order that fills in many pieces. Each partial fill used to count as its
  ;; own intervention, so the real hold gaps were buried under a swarm of
  ;; ~8s intra-order gaps and the 80th-percentile horizon pinned to the 6h
  ;; floor. Eight episodes held 10..17h, each closed by a 5-fill order:
  ;; coalescing yields eight real gaps (80th pctile 15.6h); without it the
  ;; distribution would be 40 gaps (32 near-zero) and clamp to 6h.
  (let [rows (mapcat
              (fn [i]
                (let [open-t (* i 100 hour)
                      close-t (+ open-t (* (+ 10 i) hour))]
                  (cons
                   (fill-ms "TSM" open-t "B" 5 0 (+ 1000 i))
                   (for [k (range 5)]
                     (fill-ms "TSM" (+ close-t (* k 8000)) "A" 1 (- 5 k) (+ 2000 i))))))
              (range 8))
        {:keys [hours source samples]} (episodes/horizon-hours rows "TSM")]
    (is (= :per-coin source))
    (is (= 8 samples))
    (is (< (js/Math.abs (- hours 15.6)) 1e-9))
    (is (> hours episodes/min-horizon-hours))))

(deftest gaps-from-episode-lifecycle
  (testing "open, add, partial reduce, close"
    (let [fills (keep episodes/normalize-fill
                      [(fill "TSM" 0 "B" 1 0)
                       (fill "TSM" 2 "B" 1 1)      ;; add: not an intervention
                       (fill "TSM" 5 "A" 1 2)      ;; reduce: gap 5h from open
                       (fill "TSM" 9 "A" 1 1)])    ;; close: gap 4h from reduce
          gaps (episodes/intervention-gaps-for-coin fills)]
      (is (= [(* 5 hour) (* 4 hour)] gaps))))
  (testing "direction flip is an intervention and re-anchors"
    (let [fills (keep episodes/normalize-fill
                      [(fill "X" 0 "B" 1 0)
                       (fill "X" 3 "A" 2 1)        ;; flip long->short: gap 3h
                       (fill "X" 10 "B" 1 -1)])    ;; close short: gap 7h
          gaps (episodes/intervention-gaps-for-coin fills)]
      (is (= [(* 3 hour) (* 7 hour)] gaps))))
  (testing "running net without startPosition matches"
    (let [fills (keep episodes/normalize-fill
                      [(fill "Y" 0 "B" 1)
                       (fill "Y" 5 "A" 1)])
          gaps (episodes/intervention-gaps-for-coin fills)]
      (is (= [(* 5 hour)] gaps)))))

(deftest horizon-fallbacks
  (testing "no usable history falls back to the default"
    (is (= {:hours episodes/default-horizon-hours :source :default :samples 0}
           (episodes/horizon-hours [] "TSM"))))
  (testing "per-coin quantile with enough samples"
    ;; Nine open/close episodes with gaps 1..9 hours -> 0.8 quantile = 7.4 h.
    (let [rows (mapcat (fn [i]
                         (let [t0 (* i 100)]
                           [(fill "TSM" t0 "B" 1 0)
                            (fill "TSM" (+ t0 (inc i)) "A" 1 1)]))
                       (range 9))
          {:keys [hours source samples]} (episodes/horizon-hours rows "TSM")]
      (is (= :per-coin source))
      (is (= 9 samples))
      (is (< (js/Math.abs (- hours 7.4)) 1e-9))))
  (testing "thin coin falls back to the median of per-market horizons"
    ;; Three markets, one episode each (gaps 5h, 10h, 20h). The target coin is
    ;; thin, so the horizon is the median market horizon (10h), and samples is
    ;; the number of contributing markets.
    (let [rows [(fill "TSM" 0 "B" 1 0) (fill "TSM" 5 "A" 1 1)
                (fill "ETH" 0 "B" 1 0) (fill "ETH" 10 "A" 1 1)
                (fill "SOL" 0 "B" 1 0) (fill "SOL" 20 "A" 1 1)]
          {:keys [hours source samples]} (episodes/horizon-hours rows "TSM")]
      (is (= :account source))
      (is (= 3 samples))
      (is (< (js/Math.abs (- hours 10)) 1e-9))))
  (testing "a hyperactively traded market cannot collapse the account estimate"
    ;; Two markets held ~80-100h, plus one scalped market with many
    ;; alternating-direction fills seconds apart (which coalescing cannot
    ;; merge, being genuine round-trips). Pooling raw gaps would drown the
    ;; slow markets and clamp to the floor; the per-market median does not.
    (let [scalp (mapcat (fn [i]
                          (let [t0 (* i 10000)]
                            [(fill-ms "SCALP" t0 "B" 1 0 nil)
                             (fill-ms "SCALP" (+ t0 5000) "A" 1 1 nil)]))
                        (range 12))
          rows (concat
                [(fill "AAA" 0 "B" 1 0) (fill "AAA" 80 "A" 1 1)
                 (fill "BBB" 0 "B" 1 0) (fill "BBB" 100 "A" 1 1)]
                scalp)
          {:keys [hours source]} (episodes/horizon-hours rows "AAA")]
      (is (= :account source))
      (is (> hours episodes/min-horizon-hours))
      (is (< (js/Math.abs (- hours 80)) 1e-9))))
  (testing "too few markets falls back to the default horizon"
    (let [rows [(fill "AAA" 0 "B" 1 0) (fill "AAA" 5 "A" 1 1)
                (fill "BBB" 0 "B" 1 0) (fill "BBB" 9 "A" 1 1)]
          {:keys [hours source]} (episodes/horizon-hours rows "AAA")]
      (is (= :default source))
      (is (= episodes/default-horizon-hours hours))))
  (testing "clamped to the floor"
    (let [rows (mapcat (fn [i]
                         (let [t0 (* i 10)]
                           [(fill "Z" t0 "B" 1 0)
                            (fill "Z" (+ t0 1) "A" 1 1)]))
                       (range 8))
          {:keys [hours]} (episodes/horizon-hours rows "Z")]
      (is (= episodes/min-horizon-hours hours)))))
