(ns hyperopen.vaults.detail.metrics-bridge-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.system :as system]
            [hyperopen.vaults.detail.metrics-bridge :as metrics-bridge]))

(use-fixtures :each
  (fn [f]
    (reset! metrics-bridge/last-metrics-request nil)
    (f)
    (reset! metrics-bridge/last-metrics-request nil)))

(deftest request-metrics-computation-dedupes-and-posts-through-worker-test
  (let [posted-message (atom nil)
        store (atom {:vaults-ui {}})
        signature-a {:summary-time-range :month
                     :selected-benchmark-coins ["BTC"]
                     :strategy-source-version 101
                     :benchmark-source-versions [["BTC" 201]]}
        signature-b (assoc signature-a :strategy-source-version 102)
        fake-worker #js {}]
    (set! (.-postMessage fake-worker)
          (fn [message]
            (reset! posted-message (js->clj message :keywordize-keys true))))
    (with-redefs [system/store store
                  metrics-bridge/metrics-worker fake-worker]
      (metrics-bridge/request-metrics-computation! {:seed 1} signature-a)
      (is (= {:type "compute-metrics"
              :payload {:seed 1}}
             @posted-message))
      (is (= signature-a (:signature @metrics-bridge/last-metrics-request)))
      (is (true? (get-in @store [:vaults-ui :detail-performance-metrics-loading?])))
      (reset! posted-message nil)
      (metrics-bridge/request-metrics-computation! {:seed 2} signature-a)
      (is (nil? @posted-message))
      (metrics-bridge/request-metrics-computation! {:seed 3} signature-b)
      (is (= {:type "compute-metrics"
              :payload {:seed 3}}
             @posted-message))
      (is (= signature-b (:signature @metrics-bridge/last-metrics-request))))))

(deftest request-metrics-computation-keeps-existing-result-visible-test
  (let [store (atom {:vaults-ui {:detail-performance-metrics-loading? false
                                 :detail-performance-metrics-result {:portfolio-values {:metric-status {}
                                                                                         :metric-reason {}}}}})]
    (with-redefs [system/store store
                  metrics-bridge/metrics-worker nil]
      (metrics-bridge/request-metrics-computation! {:seed 7}
                                                   {:summary-time-range :month
                                                    :selected-benchmark-coins []
                                                    :strategy-source-version 1
                                                    :benchmark-source-versions []})
      (is (false? (get-in @store [:vaults-ui :detail-performance-metrics-loading?]))))))

(deftest apply-worker-metrics-result-normalizes-and-writes-store-test
  (let [store (atom {:vaults-ui {:detail-performance-metrics-loading? true}})]
    (with-redefs [system/store store]
      (metrics-bridge/apply-worker-metrics-result!
       #js {:portfolio-values #js {:metric-status #js {:cagr "ok"}}
            :benchmark-values-by-coin #js {"BTC" #js {:metric-reason #js {:r2 "benchmark-coverage-gate-failed"}}}})
      (is (false? (get-in @store [:vaults-ui :detail-performance-metrics-loading?])))
      (is (= {:metric-status {:cagr :ok}
              :metric-reason {}}
             (get-in @store [:vaults-ui :detail-performance-metrics-result :portfolio-values])))
      (is (= {:metric-status {}
              :metric-reason {:r2 :benchmark-coverage-gate-failed}}
             (get-in @store [:vaults-ui :detail-performance-metrics-result :benchmark-values-by-coin "BTC"]))))))
