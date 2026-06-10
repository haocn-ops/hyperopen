(ns hyperopen.views.degen.illustrations-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.degen.illustrations :as illustrations]))

(deftest characters-render-as-svg-test
  (doseq [[label node] {"frog" (illustrations/pepe "w-9")
                        "shiba" (illustrations/doge "w-10")
                        "shiba-shades" (illustrations/doge "w-10" {:shades? true})
                        "whale" (illustrations/whale "w-12")}]
    (is (vector? node) label)
    (is (= :svg (first node)) label)
    (let [rendered (pr-str node)]
      (is (str/includes? rendered "currentColor")
          (str label " must inherit currentColor"))
      (is (not (re-find #"#[0-9a-fA-F]{3,8}" rendered))
          (str label " must not hardcode colors")))))

(deftest doge-shades-variant-test
  (let [plain (pr-str (illustrations/doge "w-10"))
        shades (pr-str (illustrations/doge "w-10" {:shades? true}))]
    (is (not= plain shades))))

(deftest gauge-angle-test
  (is (zero? (illustrations/gauge-angle nil)))
  (is (zero? (illustrations/gauge-angle 0)))
  (is (= 37.5 (illustrations/gauge-angle 250)))
  (is (= -37.5 (illustrations/gauge-angle -250)))
  (is (= 75 (illustrations/gauge-angle 5000000)))
  (is (= -75 (illustrations/gauge-angle -5000000))))

(deftest feeling-gauge-dial-positions-needle-test
  (is (str/includes? (pr-str (illustrations/feeling-gauge-dial 250))
                     "rotate(37.5 50 50)"))
  (is (str/includes? (pr-str (illustrations/feeling-gauge-dial nil))
                     "rotate(0 50 50)"))
  (let [rendered (pr-str (illustrations/feeling-gauge-dial -43.69))]
    (is (str/includes? rendered "text-ho-sell"))
    (is (str/includes? rendered "text-ho-warn"))
    (is (str/includes? rendered "text-ho-buy"))))
