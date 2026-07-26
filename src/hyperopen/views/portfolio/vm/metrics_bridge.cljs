(ns hyperopen.views.portfolio.vm.metrics-bridge
  (:require [hyperopen.portfolio.application.metrics-bridge :as app-metrics-bridge]))

(def normalize-worker-metric-values
  app-metrics-bridge/normalize-worker-metric-values)

(def normalize-worker-metrics-result
  app-metrics-bridge/normalize-worker-metrics-result)

(def compute-metrics-sync
  app-metrics-bridge/compute-metrics-sync)

(def metrics-request-signature
  app-metrics-bridge/metrics-request-signature)

(def request-benchmark-daily-rows
  app-metrics-bridge/request-benchmark-daily-rows)

(def request-strategy-daily-rows
  app-metrics-bridge/request-strategy-daily-rows)

(def vault-snapshot-range-keys
  app-metrics-bridge/vault-snapshot-range-keys)

(def vault-snapshot-point-value
  app-metrics-bridge/vault-snapshot-point-value)

(def normalize-vault-snapshot-return
  app-metrics-bridge/normalize-vault-snapshot-return)

(def vault-benchmark-snapshot-values
  app-metrics-bridge/vault-benchmark-snapshot-values)

(def aligned-vault-return-rows
  app-metrics-bridge/aligned-vault-return-rows)

(def ^:dynamic metrics-worker
  app-metrics-bridge/metrics-worker)

(def last-metrics-request
  app-metrics-bridge/last-metrics-request)

(defn request-metrics-computation!
  [request-data request-signature]
  (binding [app-metrics-bridge/metrics-worker metrics-worker]
    (app-metrics-bridge/request-metrics-computation! request-data request-signature)))
