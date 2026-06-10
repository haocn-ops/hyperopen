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

(deftest chart-doodles-gated-and-render-test
  (is (nil? (widgets/chart-doodles {})))
  (is (nil? (widgets/chart-doodles {:ui {:theme "dark"}})))
  (let [rendered (pr-str (widgets/chart-doodles degen-state))]
    (is (str/includes? rendered "degen-chart-doodles"))
    (is (str/includes? rendered "pointer-events-none"))
    (is (str/includes? rendered "seems good"))
    (is (str/includes? rendered "uh oh"))
    (is (str/includes? rendered "MAGIC LINE ✨"))
    (is (str/includes? rendered "trust me bro"))))

(deftest top-gainer-test
  (is (nil? (widgets/top-gainer {})))
  (is (nil? (widgets/top-gainer
             {:asset-selector {:market-by-key {"a" {:key "a"}}}})))
  (let [state {:asset-selector
               {:market-by-key
                {"BTC" {:key "BTC" :coin "BTC" :change24hPct 2.5}
                 "GIGA" {:key "GIGA" :coin "GIGA" :change24hPct 9001.0}
                 "NPC" {:key "NPC" :coin "NPC" :change24hPct -9.99}
                 "BROKEN" {:key "BROKEN" :coin "BROKEN"}}}}
        winner (widgets/top-gainer state)]
    (is (= "GIGA" (:key winner)))
    (is (= 9001.0 (:degen/pct winner)))))

(deftest shill-card-uses-select-asset-action-test
  (let [state (assoc degen-state
                     :asset-selector {:market-by-key
                                      {"GIGA" {:key "GIGA" :coin "GIGA"
                                               :change24hPct 9001.0}}})
        rendered (pr-str (widgets/widgets-row state))]
    (is (str/includes? rendered "Shill of the Day"))
    (is (str/includes? rendered "GIGA +9001.00%"))
    (is (str/includes? rendered ":actions/select-asset-by-market-key"))
    (is (str/includes? rendered "not financial advice"))))

(deftest leverage-warning-banner-test
  (is (nil? (widgets/leverage-warning-banner {} 100)))
  (is (nil? (widgets/leverage-warning-banner degen-state nil)))
  (is (nil? (widgets/leverage-warning-banner degen-state 5)))
  (let [warn (pr-str (widgets/leverage-warning-banner degen-state 20))
        spicy (pr-str (widgets/leverage-warning-banner degen-state 50))
        max* (pr-str (widgets/leverage-warning-banner degen-state 100))]
    (is (str/includes? warn "BIG FUN"))
    (is (str/includes? warn "border-ho-warn"))
    (is (str/includes? spicy "0.1% wick"))
    (is (str/includes? max* "MAXIMUM DEGEN"))
    (is (str/includes? max* "border-ho-sell"))))

(deftest daily-tip-deterministic-test
  (is (= (nth widgets/tips 0) (widgets/daily-tip 0)))
  (is (= (nth widgets/tips 1) (widgets/daily-tip 86400000)))
  (is (= (widgets/daily-tip 86400000)
         (widgets/daily-tip (+ 86400000 12345))))
  (is (= (nth widgets/tips 0)
         (widgets/daily-tip (* 86400000 (count widgets/tips))))))
