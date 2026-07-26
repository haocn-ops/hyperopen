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

(deftest optimizer-auto-exit-excluded-defaults-on-test
  (is (true? (:optimizer-auto-exit-excluded? trading-settings/default-state)))
  ;; A stored pre-feature settings blob (key absent) restores as ON; only an
  ;; explicitly persisted false keeps the opt-out.
  (is (true? (:optimizer-auto-exit-excluded? (trading-settings/normalize-state {}))))
  (is (false? (:optimizer-auto-exit-excluded?
               (trading-settings/normalize-state
                {:optimizer-auto-exit-excluded? false}))))
  (is (true? (:optimizer-auto-exit-excluded?
              (trading-settings/normalize-state
               {:optimizer-auto-exit-excluded? true})))))

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
