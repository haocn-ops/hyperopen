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

(deftest normalize-fill-shapes
  (is (= {:coin "TSM" :time-ms 0 :delta 2 :start-position 0}
         (episodes/normalize-fill {:coin "TSM" :time 0 :side "B" :sz "2" :startPosition "0"})))
  (is (= -1.5 (:delta (episodes/normalize-fill {:coin "X" :time 5 :side "A" :sz 1.5}))))
  (is (nil? (episodes/normalize-fill {:coin "X" :time 5 :side "?" :sz 1})))
  (is (nil? (episodes/normalize-fill {:coin "" :time 5 :side "B" :sz 1}))))

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
  (testing "account-level fallback when the coin is thin"
    (let [rows (concat
                [(fill "TSM" 0 "B" 1 0) (fill "TSM" 4 "A" 1 1)]
                (mapcat (fn [i]
                          (let [t0 (* (inc i) 1000)]
                            [(fill "ETH" t0 "B" 1 0)
                             (fill "ETH" (+ t0 8) "A" 1 1)]))
                        (range 8)))
          {:keys [source samples]} (episodes/horizon-hours rows "TSM")]
      (is (= :account source))
      (is (= 9 samples))))
  (testing "clamped to the floor"
    (let [rows (mapcat (fn [i]
                         (let [t0 (* i 10)]
                           [(fill "Z" t0 "B" 1 0)
                            (fill "Z" (+ t0 1) "A" 1 1)]))
                       (range 8))
          {:keys [hours]} (episodes/horizon-hours rows "Z")]
      (is (= episodes/min-horizon-hours hours)))))
