#!/usr/bin/env bb

(ns dev.formal-tooling-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests testing]]
            [tools.formal.core :as formal]))

(def vault-surface
  {:id "vault-transfer"
   :lean-module "Hyperopen.Formal.VaultTransfer"
   :status "modeled"
   :manifest "generated/vault-transfer.edn"
   :target-source "target/formal/vault-transfer-vectors.cljs"
   :committed-source "test/hyperopen/formal/vault_transfer_vectors.cljs"})

(def standard-surface
  {:id "order-request-standard"
   :lean-module "Hyperopen.Formal.OrderRequest.Standard"
   :status "modeled"
   :manifest "generated/order-request-standard.edn"
   :target-source "target/formal/order-request-standard-vectors.cljs"
   :committed-source "test/hyperopen/formal/order_request_standard_vectors.cljs"})

(def advanced-surface
  {:id "order-request-advanced"
   :lean-module "Hyperopen.Formal.OrderRequest.Advanced"
   :status "modeled"
   :manifest "generated/order-request-advanced.edn"
   :target-source "target/formal/order-request-advanced-vectors.cljs"
   :committed-source "test/hyperopen/formal/order_request_advanced_vectors.cljs"})

(def effect-order-contract-surface
  {:id "effect-order-contract"
   :lean-module "Hyperopen.Formal.EffectOrderContract"
   :status "modeled"
   :manifest "generated/effect-order-contract.edn"
   :target-source "target/formal/effect-order-contract-vectors.cljs"
   :committed-source "test/hyperopen/formal/effect_order_contract_vectors.cljs"})

(def portfolio-returns-estimator-surface
  {:id "portfolio-returns-estimator"
   :lean-module "Hyperopen.Formal.PortfolioReturnsEstimator"
   :status "modeled"
   :manifest "generated/portfolio-returns-estimator.edn"
   :target-source "target/formal/portfolio-returns-estimator-vectors.cljs"
   :committed-source "test/hyperopen/formal/portfolio_returns_estimator_vectors.cljs"})

(def portfolio-returns-normalization-surface
  {:id "portfolio-returns-normalization"
   :lean-module "Hyperopen.Formal.PortfolioReturnsNormalization"
   :status "modeled"
   :manifest "generated/portfolio-returns-normalization.edn"
   :target-source "target/formal/portfolio-returns-normalization-vectors.cljs"
   :committed-source "test/hyperopen/formal/portfolio_returns_normalization_vectors.cljs"})

(def trading-submit-policy-surface
  {:id "trading-submit-policy"
   :lean-module "Hyperopen.Formal.TradingSubmitPolicy"
   :status "modeled"
   :manifest "generated/trading-submit-policy.edn"
   :target-source "target/formal/trading-submit-policy-vectors.cljs"
   :committed-source "test/hyperopen/formal/trading_submit_policy_vectors.cljs"})

(def order-form-ownership-surface
  {:id "order-form-ownership"
   :lean-module "Hyperopen.Formal.OrderFormOwnership"
   :status "modeled"
   :manifest "generated/order-form-ownership.edn"
   :target-source "target/formal/order-form-ownership-vectors.cljs"
   :committed-source "test/hyperopen/formal/order_form_ownership_vectors.cljs"})

(def bootstrap-test-surface
  {:id "bootstrap-test"
   :lean-module "Hyperopen.Formal.Bootstrap"
   :status "bootstrap"
   :manifest "generated/bootstrap-test.edn"})

(def modeled-surfaces
  [vault-surface
   standard-surface
   advanced-surface
   effect-order-contract-surface
   portfolio-returns-estimator-surface
   portfolio-returns-normalization-surface
   trading-submit-policy-surface
   order-form-ownership-surface])

(defn delete-recursive!
  [file]
  (when (.exists file)
    (doseq [child (reverse (file-seq file))]
      (.delete child))))

(defn with-temp-root
  [f]
  (let [tmp-path (java.nio.file.Files/createTempDirectory "formal-tooling" (make-array java.nio.file.attribute.FileAttribute 0))
        root (.toFile tmp-path)]
    (try
      (f (.getCanonicalPath root))
      (finally
        (delete-recursive! root)))))

(defn write-file!
  [root relative-path text]
  (let [file (io/file root relative-path)]
    (when-let [parent (.getParentFile file)]
      (.mkdirs parent))
    (spit file text)))

(deftest manifest-content-respects-surface-status-test
  (is (= "{:surface \"vault-transfer\" :module \"Hyperopen.Formal.VaultTransfer\" :status \"modeled\"}\n"
         (#'formal/manifest-content vault-surface)))
  (is (= "{:surface \"order-request-standard\" :module \"Hyperopen.Formal.OrderRequest.Standard\" :status \"modeled\"}\n"
         (#'formal/manifest-content standard-surface)))
  (is (= "{:surface \"order-request-advanced\" :module \"Hyperopen.Formal.OrderRequest.Advanced\" :status \"modeled\"}\n"
         (#'formal/manifest-content advanced-surface)))
  (is (= "{:surface \"effect-order-contract\" :module \"Hyperopen.Formal.EffectOrderContract\" :status \"modeled\"}\n"
         (#'formal/manifest-content effect-order-contract-surface)))
  (is (= "{:surface \"portfolio-returns-estimator\" :module \"Hyperopen.Formal.PortfolioReturnsEstimator\" :status \"modeled\"}\n"
         (#'formal/manifest-content portfolio-returns-estimator-surface)))
  (is (= "{:surface \"portfolio-returns-normalization\" :module \"Hyperopen.Formal.PortfolioReturnsNormalization\" :status \"modeled\"}\n"
         (#'formal/manifest-content portfolio-returns-normalization-surface)))
  (is (= "{:surface \"trading-submit-policy\" :module \"Hyperopen.Formal.TradingSubmitPolicy\" :status \"modeled\"}\n"
         (#'formal/manifest-content trading-submit-policy-surface)))
  (is (= "{:surface \"order-form-ownership\" :module \"Hyperopen.Formal.OrderFormOwnership\" :status \"modeled\"}\n"
         (#'formal/manifest-content order-form-ownership-surface))))

(deftest sync-generated-source-copies-transient-export-into-committed-namespace-test
  (doseq [{:keys [target-source committed-source] :as surface} modeled-surfaces]
    (with-temp-root
      (fn [root]
        (write-file! root target-source "generated")
        (with-redefs [tools.formal.core/repo-root (constantly (io/file root))]
          (#'formal/sync-generated-source! surface)
          (is (= "generated"
                 (slurp (io/file root committed-source)))))))))

(deftest verify-generated-source-detects-stale-committed-namespace-test
  (doseq [{:keys [target-source committed-source] :as surface} modeled-surfaces]
    (with-temp-root
      (fn [root]
        (write-file! root target-source "generated")
        (write-file! root committed-source "stale")
        (with-redefs [tools.formal.core/repo-root (constantly (io/file root))]
          (is (thrown-with-msg?
               Exception
               #"Stale generated source"
               (#'formal/verify-generated-source! surface))))))))

(deftest bootstrap-surface-skips-generated-source-checks-test
  (is (nil? (#'formal/verify-generated-source! bootstrap-test-surface)))
  (is (nil? (#'formal/sync-generated-source! bootstrap-test-surface))))

(deftest lean-root-points-at-spec-lean-even-when-historical-root-exists-test
  (with-temp-root
    (fn [root]
      (write-file! root "spec/lean/lakefile.toml" "preferred")
      (write-file! root "tools/formal/lean/lakefile.toml" "historical")
      (with-redefs [tools.formal.core/repo-root (constantly (io/file root))]
        (is (= (.getCanonicalPath (io/file root "spec" "lean"))
               (.getCanonicalPath ^java.io.File (#'formal/lean-root))))))))

(deftest build-and-entrypoint-run-from-spec-lean-root-even-when-historical-root-exists-test
  (with-temp-root
    (fn [root]
      (write-file! root "spec/lean/lakefile.toml" "preferred")
      (write-file! root "tools/formal/lean/lakefile.toml" "historical")
      (let [preferred-root (.getCanonicalPath (io/file root "spec" "lean"))
            invocations (atom [])]
        (with-redefs [tools.formal.core/repo-root (constantly (io/file root))
                      tools.formal.core/run-command (fn [command args {:keys [dir]}]
                                                      (swap! invocations conj {:command command
                                                                               :args args
                                                                               :dir (.getCanonicalPath ^java.io.File dir)})
                                                      {:exit 0
                                                       :output ""})]
          (#'formal/build-lean-workspace!)
          (#'formal/run-lean-entrypoint! "verify" "vault-transfer")
          (is (= [preferred-root preferred-root]
                 (mapv :dir @invocations))))))))

(deftest missing-spec-lean-does-not-fall-back-to-historical-root-test
  (with-temp-root
    (fn [root]
      (write-file! root "tools/formal/lean/lakefile.toml" "historical")
      (let [attempted-dirs (atom [])]
        (with-redefs [tools.formal.core/repo-root (constantly (io/file root))
                      tools.formal.core/run-command (fn [_command _args {:keys [dir]}]
                                                      (swap! attempted-dirs conj {:path (.getCanonicalPath ^java.io.File dir)
                                                                                  :exists (.exists ^java.io.File dir)})
                                                      {:exit 1
                                                       :output "missing spec root"})]
          (is (thrown-with-msg?
               Exception
               #"Lean build failed\."
               (#'formal/build-lean-workspace!)))
          (is (= [{:path (.getCanonicalPath (io/file root "spec" "lean"))
                   :exists false}]
                 @attempted-dirs)))))))

(deftest run-sync-and-verify-support-modeled-generated-source-artifacts-test
  (doseq [{:keys [id target-source committed-source lean-module] :as surface} modeled-surfaces]
    (with-temp-root
      (fn [root]
        (write-file! root target-source "generated")
        (write-file! root committed-source "generated")
        (with-redefs [tools.formal.core/repo-root (constantly (io/file root))
                      tools.formal.core/ensure-lean-tools! (fn [] nil)
                      tools.formal.core/build-lean-workspace! (fn [] {:exit 0})
                      tools.formal.core/run-lean-entrypoint! (fn [_command _surface-id] {:exit 0})]
          (testing (str "sync writes manifest and committed source for " id)
            (let [output (with-out-str
                           (formal/run! ["sync" "--surface" id]))]
              (is (.contains output (str "Synced " id)))
              (is (= "generated"
                     (slurp (io/file root committed-source))))
              (is (= (str "{:surface \"" id "\" :module \"" lean-module "\" :status \"modeled\"}\n")
                     (slurp (io/file root (str "tools/formal/generated/" id ".edn")))))))
          (testing (str "verify accepts current generated source for " id)
            (let [output (with-out-str
                           (formal/run! ["verify" "--surface" id]))]
              (is (.contains output (str "Verified " id))))))))))

(defn -main
  [& _args]
  (let [{:keys [fail error]} (run-tests 'dev.formal-tooling-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
