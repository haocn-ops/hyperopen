(ns hyperopen.views.degen.widgets-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.degen.widgets :as widgets]))

(def ^:private degen-state
  {:ui {:theme "hyperdegen"}})

(deftest decor-gated-off-under-default-voice-test
  (is (nil? (widgets/stats-strip {})))
  (is (nil? (widgets/widgets-row {})))
  (is (nil? (widgets/stats-strip {:ui {:theme "dark"}})))
  (is (nil? (widgets/stats-strip {:ui {:theme "institutional"}})))
  (is (nil? (widgets/widgets-row {:ui {:theme "dark"}}))))

(deftest stats-strip-renders-under-degen-test
  (let [strip (widgets/stats-strip degen-state)
        rendered (pr-str strip)]
    (is (vector? strip))
    (is (str/includes? rendered "degen-stats-strip"))
    (is (str/includes? rendered "CONGRATS"))
    (is (str/includes? rendered "TOP 100% of degens"))
    (is (str/includes? rendered "Number Go Up?"))
    (is (str/includes? rendered "WHO KNOWS"))
    (is (str/includes? rendered "NOT FINANCIAL ADVICE! 🤡"))))

(deftest widgets-row-renders-under-degen-test
  (let [row (widgets/widgets-row degen-state)
        rendered (pr-str row)]
    (is (vector? row))
    (is (str/includes? rendered "degen-widgets-row"))
    (is (str/includes? rendered "Degen Tip"))
    (is (str/includes? rendered "Whale Watch"))
    (is (str/includes? rendered "Such leverage. Much risk. Very degen. Wow."))
    (is (str/includes? rendered "Feeling Gauge"))))

(deftest liq-risk-tiers-test
  (is (= "NONE" (:text (widgets/liq-risk nil))))
  (is (= "NONE" (:text (widgets/liq-risk 0))))
  (is (= "MEH 😴" (:text (widgets/liq-risk 0.05))))
  (is (= "SPICY 🌶️" (:text (widgets/liq-risk 0.2))))
  (is (= "VERY HIGH 😬" (:text (widgets/liq-risk 0.5))))
  (is (= "text-ho-sell" (:class (widgets/liq-risk 0.5)))))

(deftest market-vibes-tiers-test
  (is (= "NO VIBES" (:text (widgets/market-vibes nil))))
  (is (= "BULLISH AF 🚀🚀" (:text (widgets/market-vibes 6.9))))
  (is (= "BEARISH AF 💀" (:text (widgets/market-vibes -4.2))))
  (is (= "CRAB MARKET 🦀" (:text (widgets/market-vibes 0.5)))))

(deftest feeling-tiers-test
  (is (= "AMAZING 🤑" (:status (widgets/feeling 123.45))))
  (is (= "TERRIBLE 💩" (:status (widgets/feeling -43.69))))
  (is (= "NUMB 😶" (:status (widgets/feeling 0))))
  (is (= "NUMB 😶" (:status (widgets/feeling nil)))))

(deftest daily-tip-deterministic-test
  (is (= (nth widgets/tips 0) (widgets/daily-tip 0)))
  (is (= (nth widgets/tips 1) (widgets/daily-tip 86400000)))
  (is (= (widgets/daily-tip 86400000)
         (widgets/daily-tip (+ 86400000 12345))))
  (is (= (nth widgets/tips 0)
         (widgets/daily-tip (* 86400000 (count widgets/tips))))))
