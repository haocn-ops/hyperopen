#!/usr/bin/env bb

(ns dev.mutation-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests testing]]
            [dev.mutation-nightly-test]
            [tools.mutate.cli-options :as cli]
            [tools.mutate.core :as core]
            [tools.mutate.coverage :as coverage]
            [tools.mutate.filesystem :as fs]
            [tools.mutate.manifest :as manifest]
            [tools.mutate.runner :as runner]
            [tools.mutate.source :as source]))

(def sample-source
  (str "(ns hyperopen.sample.policy)\n\n"
       "(defn classify\n"
       "  [x]\n"
       "  (if (= x 0)\n"
       "    1\n"
       "    (when true\n"
       "      (inc x))))\n\n"
       "(defn compare-price [a b]\n"
       "  (> a b))\n"))

(def sample-lcov
  (str "TN:\n"
       "SF:.shadow-cljs/builds/test/dev/out/cljs-runtime/hyperopen/sample/policy.cljs\n"
       "DA:5,1\n"
       "DA:6,1\n"
       "DA:7,1\n"
       "DA:8,1\n"
       "DA:10,1\n"
       "DA:11,1\n"
       "end_of_record\n"
       "TN:\n"
       "SF:.shadow-cljs/builds/ws-test/dev/out/cljs-runtime/hyperopen/sample/policy.cljs\n"
       "DA:11,1\n"
       "end_of_record\n"))

(defn delete-recursive!
  [file]
  (when (.exists file)
    (doseq [child (reverse (file-seq file))]
      (.delete child))))

(defn with-temp-root
  [f]
  (let [tmp-path (java.nio.file.Files/createTempDirectory "mutation-tool" (make-array java.nio.file.attribute.FileAttribute 0))
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

(deftest cli-parse-args-validates-defaults-and-conflicts
  (let [opts (cli/parse-args ["--module" "src/hyperopen/sample/policy.cljs"
                              "--suite" "ws-test"
                              "--format" "json"
                              "--timeout-factor" "12"])]
    (is (= "src/hyperopen/sample/policy.cljs" (:module opts)))
    (is (= :ws-test (:suite opts)))
    (is (= "json" (:format opts)))
    (is (= 12 (:timeout-factor opts))))
  (is (thrown? Exception
               (cli/parse-args ["--module" "src/hyperopen/sample/policy.cljs"
                                "--scan"
                                "--update-manifest"])))
  (is (thrown? Exception
               (cli/parse-args ["--module" "src/hyperopen/sample/policy.cljs"
                                "--lines" "11"
                                "--mutate-all"]))))

(deftest cljs-parser-and-mutation-discovery-work-for-cljs-source
  (let [forms (source/read-source-forms "(def x #js {:a 1})\n")
        sample-forms (source/read-source-forms sample-source)
        sites (source/discover-all-mutations sample-forms)
        descriptions (set (map :description sites))]
    (is (= 1 (count forms)))
    (is (contains? descriptions "if -> if-not"))
    (is (contains? descriptions "= -> not="))
    (is (contains? descriptions "0 -> 1"))
    (is (contains? descriptions "1 -> 0"))
    (is (contains? descriptions "when -> when-not"))
    (is (contains? descriptions "true -> false"))
    (is (contains? descriptions "inc -> dec"))
    (is (contains? descriptions "> -> >="))))

(deftest mutate-source-text-rewrites-comparator-and-constant-sites
  (let [forms (source/read-source-forms sample-source)
        sites (source/discover-all-mutations forms)
        compare-site (first (filter #(= "> -> >=" (:description %)) sites))
        constant-site (first (filter #(and (= "0 -> 1" (:description %))
                                           (= 5 (:line %)))
                                     sites))
        compare-mutated (source/mutate-source-text sample-source compare-site)
        constant-mutated (source/mutate-source-text sample-source constant-site)]
    (is (.contains compare-mutated "(>= a b)"))
    (is (.contains constant-mutated "(= x 1)"))))

(deftest manifest-diff-detects-unchanged-and-changed-top-level-forms
  (let [forms (source/read-source-forms sample-source)
        manifest* (manifest/build-manifest "src/hyperopen/sample/policy.cljs"
                                           forms
                                           "2026-03-13T00:00:00Z"
                                           nil)
        unchanged (manifest/changed-form-indices forms manifest*)
        changed-source (str sample-source "\n(defn another [] 0)\n")
        changed-forms (source/read-source-forms changed-source)
        changed (manifest/changed-form-indices changed-forms manifest*)]
    (is (true? (:module-unchanged? unchanged)))
    (is (= #{} (:changed-form-indices unchanged)))
    (is (false? (:module-unchanged? changed)))
    (is (contains? (:changed-form-indices changed) 3))))

(deftest coverage-routing-partitions-sites-by-suite-mode
  (with-temp-root
    (fn [root]
      (write-file! root "src/hyperopen/sample/policy.cljs" sample-source)
      (write-file! root "coverage/lcov.info" sample-lcov)
      (let [forms (source/read-source-forms sample-source)
            sites (source/discover-all-mutations forms)
            line-builds (coverage/line-builds (coverage/load-records root "coverage/lcov.info"))
            compare-sites (filter #(= 11 (:line %)) sites)
            classify-sites (filter #(= 5 (:line %)) sites)
            [auto-covered _] (coverage/partition-sites "src/hyperopen/sample/policy.cljs"
                                                       compare-sites
                                                       line-builds
                                                       :auto)
            [test-covered _] (coverage/partition-sites "src/hyperopen/sample/policy.cljs"
                                                       classify-sites
                                                       line-builds
                                                       :test)
            [ws-covered ws-uncovered] (coverage/partition-sites "src/hyperopen/sample/policy.cljs"
                                                                classify-sites
                                                                line-builds
                                                                :ws-test)]
        (is (= [:test :ws-test] (:required-suites (first auto-covered))))
        (is (= [:test] (:required-suites (first test-covered))))
        (is (empty? ws-covered))
        (is (seq ws-uncovered))))))

(deftest filesystem-restores-stale-backups
  (with-temp-root
    (fn [root]
      (write-file! root "src/hyperopen/sample/policy.cljs" sample-source)
      (let [module "src/hyperopen/sample/policy.cljs"
            backup-path (fs/backup-path root module)
            module-path (str (io/file root "src/hyperopen/sample/policy.cljs"))]
        (spit backup-path "(ns hyperopen.sample.policy)\n(defn restored [] :ok)\n")
        (is (= module (fs/restore-stale-backup! root module)))
        (is (.contains (slurp module-path) "restored"))
        (is (false? (.exists (io/file backup-path))))))))

(deftest runner-success-detects-cljs-test-failures-even-with-zero-exit
  (is (true? (runner/success? {:exit 0
                               :timeout? false
                               :output "Ran 4 tests containing 8 assertions.\n0 failures, 0 errors.\n"})))
  (is (false? (runner/success? {:exit 0
                                :timeout? false
                                :output "FAIL in (sample-test)\nRan 4 tests containing 8 assertions.\n1 failures, 0 errors.\n"})))
  (is (false? (runner/success? {:exit 0
                                :timeout? false
                                :output "ERROR in (sample-test)\nRan 4 tests containing 8 assertions.\n0 failures, 1 errors.\n"})))
  (is (false? (runner/success? {:exit 1
                                :timeout? false
                                :output ""}))))

(deftest execute-command-supports-scan-update-and-full-run-with-stubbed-shells
  (with-temp-root
    (fn [root]
      (write-file! root "src/hyperopen/sample/policy.cljs" sample-source)
      (let [module "src/hyperopen/sample/policy.cljs"
            command-opts (fn [overrides]
                           (merge {:module module
                                   :scan false
                                   :update-manifest false
                                   :lines nil
                                   :since-last-run false
                                   :mutate-all false
                                   :suite :auto
                                   :timeout-factor 10
                                   :coverage-file "coverage/lcov.info"
                                   :format "text"}
                                  overrides))
            calls (atom [])
            stub-run-command (fn [command {:keys [timeout-ms]}]
                               (swap! calls conj {:command command
                                                  :timeout-ms timeout-ms})
                               (cond
                                 (= command "rm -rf .shadow-cljs/builds/test out/test.js && npx shadow-cljs --force-spawn compile test && node out/test.js")
                                 {:command command
                                  :exit 0
                                  :timeout? false
                                  :elapsed-ms 20
                                  :output ""}

                                 (= command "rm -rf .shadow-cljs/builds/test out/test.js && npx shadow-cljs --force-spawn compile test")
                                 {:command command
                                  :exit 0
                                  :timeout? false
                                  :elapsed-ms 5
                                  :output ""}

                                 (= command "npx shadow-cljs --force-spawn compile test && node out/test.js")
                                 (if timeout-ms
                                   {:command command
                                    :exit 1
                                    :timeout? false
                                    :elapsed-ms 5
                                    :output "mutant failed"}
                                   {:command command
                                    :exit 0
                                    :timeout? false
                                    :elapsed-ms 20
                                    :output ""})

                                 (= command "npx shadow-cljs compile test")
                                 {:command command
                                  :exit 0
                                  :timeout? false
                                  :elapsed-ms 5
                                  :output ""}

                                 :else
                                 (throw (ex-info (str "Unexpected command " command) {}))))]
        (with-redefs [fs/repo-root (constantly root)
                      core/prepare-test-runner! (fn [] :ok)
                      runner/run-command stub-run-command]
          (let [scan-report (core/execute-command (command-opts {:scan true}))]
            (is (false? (get-in scan-report [:summary :coverage-available?])))
            (is (pos? (get-in scan-report [:summary :total-sites])))
            (is (.exists (io/file (get-in scan-report [:summary :artifact-path])))))
          (let [update-report (core/execute-command (command-opts {:update-manifest true}))
                manifest-path (get-in update-report [:summary :manifest-path])
                scan-after-update (core/execute-command (command-opts {:scan true}))]
            (is (.exists (io/file manifest-path)))
            (is (= 0 (get-in scan-after-update [:summary :changed-sites]))))
          (write-file! root "coverage/lcov.info" sample-lcov)
          (let [run-report (core/execute-command (command-opts {:mutate-all true
                                                                :suite :test}))
                manifest-path (fs/manifest-path root module)
                report-path (get-in run-report [:summary :artifact-path])]
            (is (.exists (io/file manifest-path)))
            (is (.exists (io/file report-path)))
            (is (pos? (get-in run-report [:summary :executed-sites])))
            (is (= (get-in run-report [:summary :executed-sites])
                   (get-in run-report [:summary :killed])))
            (is (every? string? (map :command @calls)))))))))

(defn -main
  [& _args]
  (let [{:keys [fail error]} (run-tests 'dev.mutation-test
                                        'dev.mutation-nightly-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
