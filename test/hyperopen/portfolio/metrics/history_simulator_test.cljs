(ns hyperopen.portfolio.metrics.history-simulator-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.formal.portfolio-returns-estimator-vectors :as vectors]
            [hyperopen.portfolio.metrics :as metrics]
            [hyperopen.portfolio.metrics.test-utils :refer [approx=]]
            [hyperopen.schema.portfolio-returns-contracts :as contracts]))

(def ^:private epsilon 1e-9)
(def ^:private wipeout-threshold -99.9999)

(defn- assert-cumulative-rows=
  [expected actual context]
  (is (= (count expected) (count actual))
      (str "row count mismatch " (pr-str context)))
  (doseq [[idx expected-row] (map-indexed vector expected)]
    (let [actual-row (nth actual idx)]
      (is (= (:time-ms expected-row)
             (:time-ms actual-row))
          (str "time-ms mismatch " (pr-str (assoc context :idx idx))))
      (is (approx= (:percent expected-row)
                   (:percent actual-row)
                   epsilon)
          (str "percent mismatch " (pr-str (assoc context :idx idx))
               " expected=" (pr-str expected-row)
               " actual=" (pr-str actual-row))))))

(deftest simulator-cases-preserve-committed-safety-and-cadence-behavior-test
  (doseq [{:keys [id observed-summary expected] :as entry} vectors/returns-simulator-vectors]
    (testing (name id)
      (contracts/assert-simulator-vector! entry {:vector id})
      (let [actual (metrics/returns-history-rows-from-summary observed-summary)
            projection (contracts/assert-runtime-cumulative-projection!
                        (contracts/runtime-cumulative-rows-projection actual)
                        {:vector id})
            expected-rows (-> (:estimator-rows expected)
                              (contracts/assert-generated-cumulative-rows! {:vector id :phase :expected-estimator-rows})
                              contracts/generated-cumulative-rows->number-projection)
            expected-final (contracts/ratio->number (:estimator-final-percent expected))
            latent-final (contracts/ratio->number (:latent-window-final-percent expected))
            actual-final (contracts/final-percent projection)
            final-error-bps (contracts/final-error-bps actual-final latent-final)]
        (assert-cumulative-rows= expected-rows projection {:vector id})
        (is (approx= expected-final actual-final epsilon)
            (str "estimator final percent mismatch " (pr-str {:vector id
                                                              :expected expected-final
                                                              :actual actual-final})))
        (if (:exact? expected)
          (is (approx= latent-final actual-final epsilon)
              (str "exact latent comparison mismatch " (pr-str {:vector id
                                                                :latent latent-final
                                                                :actual actual-final})))
          (is (<= final-error-bps
                  (+ (:max-final-error-bps expected) epsilon))
              (str "cadence error budget exceeded " (pr-str {:vector id
                                                             :actual-error-bps final-error-bps
                                                             :max-final-error-bps (:max-final-error-bps expected)}))))
        (if (:first-row-zero? expected)
          (if (seq projection)
            (is (approx= 0 (:percent (first projection)) epsilon)
                (str "first-row-zero invariant failed " (pr-str {:vector id
                                                                 :first-row (first projection)})))
            (is true))
          (is true))
        (if (:avoid-false-wipeout? expected)
          (is (> actual-final wipeout-threshold)
              (str "false wipeout regression " (pr-str {:vector id
                                                        :actual-final actual-final
                                                        :threshold wipeout-threshold})))
          (is true))))))

(defn- simulator-entry
  [target-id]
  (first (filter #(= target-id (:id %))
                 vectors/returns-simulator-vectors)))

(defn- encode-history-row
  [kind idx [time-ms value]]
  (case (mod idx 4)
    0 [(str time-ms) (str value)]
    1 (assoc {:timestamp (str time-ms)}
             (if (= kind :pnl) :pnl :accountValue)
             (str value))
    2 {:timeMs time-ms
       :value value}
    {:time (str time-ms)
     (if (= kind :pnl) :pnl :account-value) value}))

(defn- perturb-history-rows
  [kind rows]
  (->> rows
       (map-indexed (fn [idx row]
                      (let [encoded (encode-history-row kind idx row)]
                        (if (even? idx)
                          [encoded (encode-history-row kind (+ idx 11) row)]
                          [encoded]))))
       reverse
       (apply concat)
       vec))

(defn- perturb-observed-summary
  [summary]
  {:accountValueHistory (perturb-history-rows :account (:accountValueHistory summary))
   :pnlHistory (perturb-history-rows :pnl (:pnlHistory summary))})

(defn- duplicate-history-rows
  [rows]
  (vec (mapcat (fn [row] [row row]) rows)))

(deftest simulator-observation-perturbations-preserve-estimator-output-for-exact-case-test
  (let [{:keys [observed-summary expected] :as entry} (simulator-entry :no-flow-full-cadence-exact)
        _ (contracts/assert-simulator-vector! entry {:vector :no-flow-full-cadence-exact})
        actual (metrics/returns-history-rows-from-summary (perturb-observed-summary observed-summary))
        projection (contracts/assert-runtime-cumulative-projection!
                    (contracts/runtime-cumulative-rows-projection actual)
                    {:vector :no-flow-full-cadence-exact :phase :perturbed})
        expected-rows (-> (:estimator-rows expected)
                          (contracts/assert-generated-cumulative-rows! {:vector :no-flow-full-cadence-exact :expected true})
                          contracts/generated-cumulative-rows->number-projection)]
    (assert-cumulative-rows= expected-rows projection {:vector :no-flow-full-cadence-exact :phase :perturbed})))

(deftest simulator-observation-duplicate-row-perturbations-are-normalization-invariant-test
  (let [{:keys [observed-summary expected] :as entry} (simulator-entry :positive-rebase-full-cadence-exact)
        _ (contracts/assert-simulator-vector! entry {:vector :positive-rebase-full-cadence-exact})
        duplicate-summary {:accountValueHistory (duplicate-history-rows (:accountValueHistory observed-summary))
                           :pnlHistory (duplicate-history-rows (:pnlHistory observed-summary))}
        actual (metrics/returns-history-rows-from-summary duplicate-summary)
        projection (contracts/assert-runtime-cumulative-projection!
                    (contracts/runtime-cumulative-rows-projection actual)
                    {:vector :positive-rebase-full-cadence-exact :phase :duplicates})
        expected-rows (-> (:estimator-rows expected)
                          (contracts/assert-generated-cumulative-rows! {:vector :positive-rebase-full-cadence-exact :expected true})
                          contracts/generated-cumulative-rows->number-projection)]
    (assert-cumulative-rows= expected-rows projection {:vector :positive-rebase-full-cadence-exact :phase :duplicates})
    (is (approx= (contracts/ratio->number (:estimator-final-percent expected))
                 (contracts/final-percent projection)
                 epsilon))))

(deftest simulator-merged-rebase-case-preserves-safety-under-raw-perturbations-test
  (let [{:keys [observed-summary expected] :as entry} (simulator-entry :positive-rebase-merged-small-trade)
        _ (contracts/assert-simulator-vector! entry {:vector :positive-rebase-merged-small-trade})
        actual (metrics/returns-history-rows-from-summary (perturb-observed-summary observed-summary))
        projection (contracts/assert-runtime-cumulative-projection!
                    (contracts/runtime-cumulative-rows-projection actual)
                    {:vector :positive-rebase-merged-small-trade :phase :perturbed})
        expected-rows (-> (:estimator-rows expected)
                          (contracts/assert-generated-cumulative-rows! {:vector :positive-rebase-merged-small-trade :expected true})
                          contracts/generated-cumulative-rows->number-projection)
        expected-final (contracts/ratio->number (:estimator-final-percent expected))
        latent-final (contracts/ratio->number (:latent-window-final-percent expected))
        actual-final (contracts/final-percent projection)
        final-error-bps (contracts/final-error-bps actual-final latent-final)]
    (assert-cumulative-rows= expected-rows projection {:vector :positive-rebase-merged-small-trade :phase :perturbed})
    (is (approx= expected-final actual-final epsilon))
    (is (> actual-final wipeout-threshold))
    (is (<= final-error-bps
            (+ (:max-final-error-bps expected) epsilon)))))
