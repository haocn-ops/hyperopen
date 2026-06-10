(ns hyperopen.views.degen.order-form-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.degen.order-form :as degen-order-form]))

(def ^:private degen-state
  {:ui {:theme "hyperdegen"}})

(deftest leverage-tier-escalation-test
  (is (nil? (degen-order-form/leverage-tier nil)))
  (is (= 0 (:level (degen-order-form/leverage-tier 5))))
  (is (str/includes? (:text (degen-order-form/leverage-tier 5)) "Sensible"))
  (is (= 1 (:level (degen-order-form/leverage-tier 10))))
  (is (str/includes? (:text (degen-order-form/leverage-tier 15)) "spicy"))
  (is (= 2 (:level (degen-order-form/leverage-tier 20))))
  (is (str/includes? (:text (degen-order-form/leverage-tier 25)) "BIG FUN"))
  (is (= 3 (:level (degen-order-form/leverage-tier 50))))
  (is (str/includes? (:text (degen-order-form/leverage-tier 60))
                     "liquidation price"))
  (is (= 4 (:level (degen-order-form/leverage-tier 100))))
  (is (str/includes? (:text (degen-order-form/leverage-tier 150)) "0.1% wick"))
  (is (= 5 (:level (degen-order-form/leverage-tier 200))))
  (is (str/includes? (:text (degen-order-form/leverage-tier 1000))
                     "MAXIMUM DEGEN")))

(deftest leverage-warning-banner-test
  (is (nil? (degen-order-form/leverage-warning-banner {} 100)))
  (is (nil? (degen-order-form/leverage-warning-banner degen-state nil)))
  (is (nil? (degen-order-form/leverage-warning-banner degen-state 5))
      "banner stays quiet below 20x")
  (is (nil? (degen-order-form/leverage-warning-banner degen-state 15)))
  (let [warn (pr-str (degen-order-form/leverage-warning-banner degen-state 20))
        max* (pr-str (degen-order-form/leverage-warning-banner degen-state 250))]
    (is (str/includes? warn "BIG FUN"))
    (is (str/includes? warn "border-ho-warn"))
    (is (str/includes? max* "MAXIMUM DEGEN"))
    (is (str/includes? max* "border-ho-sell"))))

(deftest leverage-popover-message-test
  (is (nil? (degen-order-form/leverage-popover-message {} 50)))
  (is (nil? (degen-order-form/leverage-popover-message degen-state nil)))
  (let [sensible (pr-str (degen-order-form/leverage-popover-message degen-state 3))
        spicy (pr-str (degen-order-form/leverage-popover-message degen-state 12))]
    (is (str/includes? sensible "Sensible. Are you lost?")
        "the popover speaks at every tier, including sensible")
    (is (str/includes? spicy "Getting spicy"))
    (is (str/includes? spicy "degen-leverage-popover-message"))))

(deftest effective-leverage-test
  (is (nil? (degen-order-form/effective-leverage nil 20)))
  (is (nil? (degen-order-form/effective-leverage 50 nil)))
  (is (= 0 (degen-order-form/effective-leverage 0 20)))
  (is (= 10 (degen-order-form/effective-leverage 50 20)))
  (is (= 40 (degen-order-form/effective-leverage 100 40)))
  (is (= 2.2 (degen-order-form/effective-leverage 11 20))))

(deftest size-risk-gating-test
  (is (nil? (degen-order-form/size-risk {} 50 20)))
  (is (nil? (degen-order-form/size-risk {:ui {:theme "dark"}} 50 20)))
  (is (nil? (degen-order-form/size-risk degen-state 0 20)))
  (is (nil? (degen-order-form/size-risk degen-state nil 20)))
  (is (nil? (degen-order-form/size-risk degen-state 50 nil))))

(deftest size-risk-imputes-account-leverage-test
  (let [sensible (degen-order-form/size-risk degen-state 11 20)
        all-in (degen-order-form/size-risk degen-state 100 20)
        maxed (degen-order-form/size-risk degen-state 100 250)]
    (is (str/includes? (pr-str (:message sensible)) "≈2.2x account leverage"))
    (is (str/includes? (pr-str (:message sensible)) "Sensible. Are you lost?"))
    (is (str/includes? (:slider-color sensible) "--ho-accent"))
    (is (str/includes? (pr-str (:message all-in)) "≈20.0x account leverage"))
    (is (str/includes? (pr-str (:message all-in)) "ALL IN."))
    (is (str/includes? (pr-str (:message all-in)) "BIG FUN"))
    (is (str/includes? (:slider-color all-in) "--ho-warn"))
    (is (str/includes? (pr-str (:message maxed)) "MAXIMUM DEGEN"))
    (is (str/includes? (:slider-color maxed) "--ho-sell"))
    (is (str/includes? (pr-str (:message maxed)) "degen-size-risk-message"))))

(deftest massive-side-row-test
  (let [handlers {:on-select-side (fn [side] [[:actions/select-side side]])}
        rendered (pr-str (degen-order-form/massive-side-row
                          :buy
                          handlers
                          {:buy-label "Buy / Moon 🚀"
                           :sell-label "Sell / Panic 😱"
                           :buy-sublabel "(number go up pls)"
                           :sell-sublabel "(get me out)"}))]
    (is (str/includes? rendered "degen-massive-side-row"))
    (is (str/includes? rendered "degen-massive-side-buy"))
    (is (str/includes? rendered "(number go up pls)"))
    (is (str/includes? rendered "(get me out)"))
    (is (str/includes? rendered "bg-ho-buy"))
    (is (str/includes? rendered "bg-ho-sell-soft"))))
