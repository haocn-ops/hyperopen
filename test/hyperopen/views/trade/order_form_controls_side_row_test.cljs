(ns hyperopen.views.trade.order-form-controls-side-row-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.trade.order-form-controls :as controls]))

(def ^:private handlers
  {:on-select-side (fn [side] [[:actions/select-side side]])})

(deftest side-row-compact-by-default-test
  (let [rendered (pr-str (controls/side-row :buy handlers))]
    (is (str/includes? rendered "Buy / Long"))
    (is (str/includes? rendered "Sell / Short"))
    (is (not (str/includes? rendered "degen-massive-side-row")))))

(deftest side-row-massive-variant-test
  (let [rendered (pr-str (controls/side-row :buy handlers
                                            {:buy-label "Buy / Moon 🚀"
                                             :sell-label "Sell / Panic 😱"
                                             :massive? true
                                             :buy-sublabel "(number go up pls)"
                                             :sell-sublabel "(get me out)"}))]
    (is (str/includes? rendered "degen-massive-side-row"))
    (is (str/includes? rendered "degen-massive-side-buy"))
    (is (str/includes? rendered "degen-massive-side-sell"))
    (is (str/includes? rendered "(number go up pls)"))
    (is (str/includes? rendered "(get me out)"))
    (is (str/includes? rendered "bg-ho-buy"))
    (is (str/includes? rendered "bg-ho-sell-soft"))))
