(ns hyperopen.trading-settings-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.trading-settings :as trading-settings]))

(deftest open-order-safety-mode-defaults-to-strict-test
  (is (= :strict (:open-order-safety-mode trading-settings/default-state)))
  (is (= :strict (:open-order-safety-mode (trading-settings/normalize-state {}))))
  (is (= :strict (:open-order-safety-mode
                  (trading-settings/normalize-state
                   {:open-order-safety-mode :unknown}))))
  (is (= :strict (:open-order-safety-mode
                  (trading-settings/normalize-state
                   {"open-order-safety-mode" "off"})))))

(deftest open-order-safety-mode-normalizes-supported-values-test
  (is (= :strict (:open-order-safety-mode
                  (trading-settings/normalize-state
                   {:open-order-safety-mode "strict"}))))
  (is (= :extended (:open-order-safety-mode
                    (trading-settings/normalize-state
                     {:open-order-safety-mode "extended"}))))
  (is (= :off (:open-order-safety-mode
               (trading-settings/normalize-state
                {:open-order-safety-mode :off})))))
