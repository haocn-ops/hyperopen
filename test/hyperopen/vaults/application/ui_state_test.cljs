(ns hyperopen.vaults.application.ui-state-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.vaults.application.ui-state :as ui-state]))

(deftest vault-ui-state-defaults-and-token-normalization-test
  (is (= :month ui-state/default-vault-snapshot-range))
  (is (= :tvl ui-state/default-vault-sort-column))
  (is (= :desc ui-state/default-vault-sort-direction))
  (is (= 10 ui-state/default-vault-user-page-size))
  (is (= 1 ui-state/default-vault-user-page))
  (is (= :about ui-state/default-vault-detail-tab))
  (is (= :performance-metrics ui-state/default-vault-detail-activity-tab))
  (is (= :all ui-state/default-vault-detail-activity-direction-filter))
  (is (= :desc ui-state/default-vault-detail-activity-sort-direction))
  (is (= :returns ui-state/default-vault-detail-chart-series))
  (is (= [5 10 25 50] ui-state/vault-user-page-size-options))
  (is (= :all-time (ui-state/normalize-vault-snapshot-range "allTime")))
  (is (= :apr (ui-state/normalize-vault-sort-column "APR")))
  (is (= :vault-performance
         (ui-state/normalize-vault-detail-tab "vaultPerformance")))
  (is (= :open-orders
         (ui-state/normalize-vault-detail-activity-tab "openOrders")))
  (is (= :monte-carlo
         (ui-state/normalize-vault-detail-activity-tab "monteCarlo")))
  (is (= [:vaults-ui :monte-carlo] ui-state/vault-monte-carlo-state-path))
  (is (= :short
         (ui-state/normalize-vault-detail-activity-direction-filter "SHORT")))
  (is (= :asc (ui-state/normalize-sort-direction "ASC")))
  (is (= :account-value
         (ui-state/normalize-vault-detail-chart-series "accountValue"))))

(deftest vault-ui-state-normalizers-fall-back-and-clamp-test
  (is (= :month (ui-state/normalize-vault-snapshot-range :bad-range)))
  (is (= :tvl (ui-state/normalize-vault-sort-column "???")))
  (is (= :about (ui-state/normalize-vault-detail-tab "???")))
  (is (= :performance-metrics
         (ui-state/normalize-vault-detail-activity-tab "???")))
  (is (= :all
         (ui-state/normalize-vault-detail-activity-direction-filter "???")))
  (is (= :desc (ui-state/normalize-sort-direction "???")))
  (is (= :returns
         (ui-state/normalize-vault-detail-chart-series "???")))
  (is (= 25 (ui-state/normalize-vault-user-page-size "25")))
  (is (= 10 (ui-state/normalize-vault-user-page-size "999")))
  (is (= 1 (ui-state/normalize-vault-user-page "-4")))
  (is (= 3 (ui-state/normalize-vault-user-page "9" 3)))
  (is (= 1 (ui-state/normalize-vault-user-page nil nil))))
