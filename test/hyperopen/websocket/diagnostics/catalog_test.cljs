(ns hyperopen.websocket.diagnostics.catalog-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.websocket.diagnostics.catalog :as catalog]))

(deftest normalize-status-normalizes-legacy-and-missing-values-test
  (is (= :event-driven (catalog/normalize-status :n-a)))
  (is (= :unknown (catalog/normalize-status nil)))
  (is (= :unknown (catalog/normalize-status :mystery-status))))

(deftest unknown-source-label-stays-unknown-test
  (is (= "unknown" (catalog/source-label :mystery-group)))
  (is (= "Transport" (catalog/group-title :transport))))
