(ns hyperopen.leaderboard.normalization-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.leaderboard.normalization :as normalization]))

(deftest normalize-window-performance-handles-supported-metrics-and-invalid-values-test
  (is (= {:pnl -2
          :roi 0
          :volume 9}
         (normalization/normalize-window-performance
          {"pnl" " -2 "
           :roi "nope"
           "vlm" "9"
           :ignored 4})))
  (is (nil? (normalization/normalize-window-performance []))))

(deftest normalize-window-performances-handles-map-and-sequential-inputs-test
  (is (= {:day {:pnl 0 :roi 0 :volume 0}
          :week {:pnl 0 :roi 0 :volume 0}
          :month {:pnl 2 :roi 0.1 :volume 9}
          :all-time {:pnl 0 :roi 0 :volume 0}}
         (normalization/normalize-window-performances
          {:month {:pnl "2"
                   :roi "0.1"
                   :vlm "9"}
           :ignored {:pnl 1}})))
  (is (= {:day {:pnl 1 :roi 0 :volume 0}
          :week {:pnl 0 :roi 0 :volume 0}
          :month {:pnl 0 :roi 0 :volume 0}
          :all-time {:pnl 4 :roi 0.2 :volume 7}}
         (normalization/normalize-window-performances
          [["day" {:pnl "1"}]
           [:alltime {"pnl" "4" :roi "0.2" :volume "7"}]]))))

(deftest normalize-window-key-normalizes-common-aliases-test
  (is (= :all-time (normalization/normalize-window-key :alltime)))
  (is (= :all-time (normalization/normalize-window-key "allTime")))
  (is (= :all-time (normalization/normalize-window-key "all")))
  (is (= :month (normalization/normalize-window-key "month")))
  (is (nil? (normalization/normalize-window-key "quarter"))))
