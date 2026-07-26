#!/usr/bin/env bb

(ns dev.mutation-nightly-test
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [tools.mutate.core :as core]
            [tools.mutate.filesystem :as fs]
            [tools.mutate.nightly :as nightly]))

(defn delete-recursive!
  [file]
  (when (.exists file)
    (doseq [child (reverse (file-seq file))]
      (.delete child))))

(defn with-temp-root
  [f]
  (let [tmp-path (java.nio.file.Files/createTempDirectory "mutation-nightly" (make-array java.nio.file.attribute.FileAttribute 0))
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

(def sample-source
  "(ns hyperopen.sample)\n(defn ok [] true)\n")

(deftest nightly-parse-args-and-load-targets-apply-defaults
  (with-temp-root
    (fn [root]
      (write-file! root "src/hyperopen/sample/one.cljs" sample-source)
      (write-file! root "src/hyperopen/sample/two.cljs" sample-source)
      (write-file! root "tools/mutate/nightly_targets.edn"
                   (pr-str [{:module "src/hyperopen/sample/one.cljs"}
                            {:module "src/hyperopen/sample/two.cljs"
                             :label "Two"
                             :suite :test
                             :mutate-all false
                             :timeout-factor 12}]))
      (let [opts (nightly/parse-args ["--skip-coverage" "--limit" "1" "--format" "json"])
            targets (nightly/load-targets root "tools/mutate/nightly_targets.edn")]
        (is (:skip-coverage opts))
        (is (= 1 (:limit opts)))
        (is (= "json" (:format opts)))
        (is (= [{:label "src/hyperopen/sample/one.cljs"
                 :module "src/hyperopen/sample/one.cljs"
                 :suite :auto
                 :mutate-all true
                 :timeout-factor 10
                 :coverage-file "coverage/lcov.info"}
                {:label "Two"
                 :module "src/hyperopen/sample/two.cljs"
                 :suite :test
                 :mutate-all false
                 :timeout-factor 12
                 :coverage-file "coverage/lcov.info"}]
               targets))))))

(deftest nightly-compare-target-detects-regressions-and-improvements
  (let [current-ok {:module "src/hyperopen/sample/one.cljs"
                    :status :ok
                    :summary {:kill-pct 70.0
                              :survived 3
                              :uncovered-sites 1
                              :executed-sites 10}}
        previous-ok {:module "src/hyperopen/sample/one.cljs"
                     :status :ok
                     :summary {:kill-pct 90.0
                               :survived 1
                               :uncovered-sites 0
                               :executed-sites 10}}
        improved (nightly/compare-target previous-ok current-ok)
        regressed (nightly/compare-target current-ok previous-ok)
        recovered (nightly/compare-target current-ok {:status :error})
        failed (nightly/compare-target {:status :error} previous-ok)
        fresh (nightly/compare-target current-ok nil)]
    (is (= :improved (:trend improved)))
    (is (= :regressed (:trend regressed)))
    (is (= :recovered (:trend recovered)))
    (is (= :failed (:trend failed)))
    (is (= :new (:trend fresh)))))

(deftest execute-nightly-writes-summary-and-compares-to-previous-run
  (with-temp-root
    (fn [root]
      (write-file! root "src/hyperopen/sample/one.cljs" sample-source)
      (write-file! root "src/hyperopen/sample/two.cljs" sample-source)
      (write-file! root "tools/mutate/nightly_targets.edn"
                   (pr-str [{:label "One"
                             :module "src/hyperopen/sample/one.cljs"
                             :suite :test
                             :mutate-all true}
                            {:label "Two"
                             :module "src/hyperopen/sample/two.cljs"
                             :suite :auto
                             :mutate-all true}]))
      (write-file! root
                   "target/mutation/nightly/2026-03-12T02-00-00Z/summary.json"
                   (json/generate-string
                    {:run {:started-at "2026-03-12T02:00:00Z"
                           :completed-at "2026-03-12T02:10:00Z"
                           :run-dir (str (io/file root "target/mutation/nightly/2026-03-12T02-00-00Z"))}
                     :overall {:target-count 1}
                     :targets [{:module "src/hyperopen/sample/one.cljs"
                                :status :ok
                                :summary {:kill-pct 90.0
                                          :survived 1
                                          :uncovered-sites 0
                                          :executed-sites 10
                                          :killed 9}}]}
                    {:pretty true}))
      (let [calls (atom [])
            stamps (atom ["2026-03-13T02:00:00Z" "2026-03-13T02:30:00Z"])]
        (with-redefs [fs/repo-root (constantly root)
                      fs/now-stamp (fn []
                                     (let [next-stamp (first @stamps)]
                                       (swap! stamps #(vec (rest %)))
                                       next-stamp))
                      nightly/run-coverage! (fn [_]
                                              {:status :skipped
                                               :command "npm run coverage"
                                               :elapsed-ms 0
                                               :output ""})
                      core/execute-command (fn [{:keys [module suite mutate-all]}]
                                             (swap! calls conj {:module module
                                                                :suite suite
                                                                :mutate-all mutate-all})
                                             (case module
                                               "src/hyperopen/sample/one.cljs"
                                               {:summary {:mode :run
                                                          :module module
                                                          :manifest-path (str (io/file root "target/mutation/manifests/one.edn"))
                                                          :artifact-path (str (io/file root "target/mutation/reports/one.edn"))
                                                          :suite suite
                                                          :total-sites 10
                                                          :selected-sites 10
                                                          :changed-sites 10
                                                          :covered-sites 10
                                                          :uncovered-sites 1
                                                          :executed-sites 10
                                                          :killed 7
                                                          :survived 3
                                                          :kill-pct 70.0}
                                                :results []
                                                :uncovered-sites [{}]}

                                               "src/hyperopen/sample/two.cljs"
                                               {:summary {:mode :run
                                                          :module module
                                                          :manifest-path (str (io/file root "target/mutation/manifests/two.edn"))
                                                          :artifact-path (str (io/file root "target/mutation/reports/two.edn"))
                                                          :suite suite
                                                          :total-sites 5
                                                          :selected-sites 5
                                                          :changed-sites 5
                                                          :covered-sites 5
                                                          :uncovered-sites 0
                                                          :executed-sites 5
                                               :killed 5
                                                          :survived 0
                                                          :kill-pct 100.0}
                                                :results []
                                                :uncovered-sites []}))]
          (let [summary (nightly/execute-nightly {:config "tools/mutate/nightly_targets.edn"
                                                  :skip-coverage true
                                                  :limit nil
                                                  :format "text"})
                json-path (get-in summary [:run :summary-json-path])
                md-path (get-in summary [:run :summary-md-path])
                markdown (slurp md-path)]
            (is (= 2 (count @calls)))
            (is (= [{:module "src/hyperopen/sample/one.cljs"
                     :suite :test
                     :mutate-all true}
                    {:module "src/hyperopen/sample/two.cljs"
                     :suite :auto
                     :mutate-all true}]
                   @calls))
            (is (.exists (io/file json-path)))
            (is (.exists (io/file md-path)))
            (is (= 15 (get-in summary [:overall :executed-sites])))
            (is (= 12 (get-in summary [:overall :killed])))
            (is (= 3 (get-in summary [:overall :survived])))
            (is (= :regressed (get-in (first (:regressions summary)) [:comparison :trend])))
            (is (= :new (get-in (second (:targets summary)) [:comparison :trend])))
            (is (.contains markdown "Weakest Targets"))
            (is (.contains markdown "src/hyperopen/sample/one.cljs"))
            (is (.contains markdown "trend `regressed`"))))))))
