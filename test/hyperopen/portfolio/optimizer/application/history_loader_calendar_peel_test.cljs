(ns hyperopen.portfolio.optimizer.application.history-loader-calendar-peel-test
  "Unit coverage for calendar/peel-poisoning-members: the recovery that stops
  one thin, calendar-disjoint member from emptying the shared return calendar
  and (pre-fix, live 2026-07-08) excluding an entire universe."
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.history-loader.calendar :as calendar]))

(def day-ms
  (* 24 60 60 1000))

(defn- points
  "Daily point rows spanning [start-day, start-day + n) with finite returns on
  every row except the first (matching real served series)."
  [start-day n]
  (mapv (fn [idx]
          {:time-ms (* (+ start-day idx) day-ms)
           :close (+ 100 idx)
           :return (when (pos? idx) 0.01)})
        (range n)))

(deftest peel-returns-nil-when-calendar-already-sufficient-test
  (is (nil? (calendar/peel-poisoning-members
             {"perp:AAA" (points 0 40)
              "perp:BBB" (points 0 40)}
             1))))

(deftest peel-removes-single-disjoint-thin-member-test
  ;; The live shape: two deep overlapping series plus one young listing whose
  ;; 5-day window starts after a stale-ended series stops. The intersection over
  ;; all three is empty; peeling must drop ONLY the young member.
  (let [result (calendar/peel-poisoning-members
                {"perp:AAA" (points 0 40)
                 "perp:BBB" (points 0 40)
                 "perp:GRAM" (points 45 5)}
                1)]
    (is (= #{"perp:AAA" "perp:BBB"} (:kept-ids result)))
    (is (= [{:instrument-id "perp:GRAM" :observations 0}]
           (:peeled result)))
    ;; Days 1..39 carry finite returns for both survivors.
    (is (= 39 (count (:return-calendar result))))))

(deftest peel-removes-both-of-two-disjoint-young-members-test
  ;; Removing either young member alone still leaves an empty intersection (the
  ;; other still poisons), so a strictly-improving loop would give up. The peel
  ;; must keep going until the calendar clears the bar.
  (let [result (calendar/peel-poisoning-members
                {"perp:AAA" (points 0 40)
                 "perp:BBB" (points 0 40)
                 "perp:YY1" (points 45 5)
                 "perp:YY2" (points 52 5)}
                1)]
    (is (= #{"perp:AAA" "perp:BBB"} (:kept-ids result)))
    (is (= #{"perp:YY1" "perp:YY2"}
           (set (map :instrument-id (:peeled result)))))
    (is (= 39 (count (:return-calendar result))))))

(deftest peel-two-member-universe-keeps-the-deeper-side-test
  ;; A universe of exactly two disjoint members must still resolve: the survivor
  ;; is the one whose own calendar is larger (argmax of the removal score).
  (let [result (calendar/peel-poisoning-members
                {"perp:DEEP" (points 0 40)
                 "perp:YOUNG" (points 45 5)}
                1)]
    (is (= #{"perp:DEEP"} (:kept-ids result)))
    (is (= ["perp:YOUNG"] (mapv :instrument-id (:peeled result))))
    (is (= 39 (count (:return-calendar result))))))

(deftest peel-reports-partial-overlap-observations-test
  ;; A peeled member that DOES share some days with the survivors reports that
  ;; overlap honestly (feeds the per-instrument warning's :observations).
  (let [result (calendar/peel-poisoning-members
                {"perp:AAA" (points 0 40)
                 "perp:BBB" (points 0 40)
                 ;; 10 points overlapping the survivors' last 5 days only; its
                 ;; presence caps the shared calendar at 4 return days, below a
                 ;; min of 10.
                 "perp:LATE" (points 35 10)}
                10)]
    (is (= #{"perp:AAA" "perp:BBB"} (:kept-ids result)))
    (is (= [{:instrument-id "perp:LATE" :observations 4}]
           (:peeled result)))
    (is (= 39 (count (:return-calendar result))))))

(deftest peel-returns-nil-when-no-viable-subset-test
  ;; Members whose rows never carry finite returns can never clear the bar, even
  ;; peeled down to one - the caller keeps its existing total-gap behavior.
  (let [returnless (mapv #(dissoc % :return) (points 0 10))]
    (is (nil? (calendar/peel-poisoning-members
               {"perp:AAA" returnless
                "perp:BBB" (mapv #(dissoc % :return) (points 20 10))}
               1)))))
