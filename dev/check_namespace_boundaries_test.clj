#!/usr/bin/env bb

(ns dev.check-namespace-boundaries-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [dev.check-namespace-boundaries :as boundaries]))

(def test-today
  (java.time.LocalDate/of 2026 3 25))

(defn test-config
  ([] (test-config {}))
  ([overrides]
   (merge {:exceptions-path "dev/namespace_boundary_exceptions.edn"
           :source-dirs ["src"]
           :today test-today}
          overrides)))

(defn delete-recursive!
  [file]
  (when (.exists file)
    (doseq [child (reverse (file-seq file))]
      (.delete child))))

(defn with-temp-repo
  [f]
  (let [tmp-path (java.nio.file.Files/createTempDirectory "namespace-boundary-check" (make-array java.nio.file.attribute.FileAttribute 0))
        root (.toFile tmp-path)]
    (try
      (f (.getCanonicalPath root))
      (finally
        (delete-recursive! root)))))

(defn write-file!
  [root rel-path text]
  (let [f (io/file root rel-path)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit f text)))

(defn ns-form
  [ns-name require-vectors]
  (str "(ns " ns-name "\n"
       "  (:require " (str/join "\n            " require-vectors) "))\n"))

(defn write-boundary-exceptions!
  [root entries]
  (write-file! root "dev/namespace_boundary_exceptions.edn" (str (pr-str entries) "\n")))

(deftest required-namespaces-ignores-nested-refer-vectors
  (let [text (ns-form "hyperopen.sample"
                      ["[clojure.string :as str]"
                       "[hyperopen.views.chart.hover :as hover]"
                       "[hyperopen.foo :refer [a b]]"])]
    (is (= ["clojure.string" "hyperopen.views.chart.hover" "hyperopen.foo"]
           (boundaries/required-namespaces text)))))

(deftest passing-repo-has-no-errors
  (with-temp-repo
    (fn [root]
      (write-file! root
                   "src/hyperopen/views/app_view.cljs"
                   (ns-form "hyperopen.views.app-view" ["[hyperopen.views.chart.hover :as hover]"]))
      (write-file! root
                   "src/hyperopen/app/bootstrap.cljs"
                   (ns-form "hyperopen.app.bootstrap" ["[hyperopen.views.app-view :as app-view]"]))
      (write-file! root
                   "src/hyperopen/app/other.cljs"
                   (ns-form "hyperopen.app.other" ["[hyperopen.ui.chart.hover :as hover]"]))
      (write-boundary-exceptions! root [{:path "src/hyperopen/app/bootstrap.cljs"
                                         :import-ns "hyperopen.views.app-view"
                                         :owner "platform"
                                         :reason "Temporary bridge to root view"
                                         :retire-by "2026-06-30"}])
      (is (empty? (boundaries/check-repo root (test-config)))))))

(deftest forbidden-non-view-import-to-views-is-reported
  (with-temp-repo
    (fn [root]
      (write-file! root
                   "src/hyperopen/portfolio/actions.cljs"
                   (ns-form "hyperopen.portfolio.actions" ["[hyperopen.views.chart.hover :as hover]"]))
      (write-boundary-exceptions! root [])
      (let [codes (set (map :code (boundaries/check-repo root (test-config))))]
        (is (contains? codes :missing-boundary-exception))))))

(deftest forbidden-domain-import-to-views-is-reported-even-with-exception
  (with-temp-repo
    (fn [root]
      (write-file! root
                   "src/hyperopen/funding/domain/modal_state.cljs"
                   (ns-form "hyperopen.funding.domain.modal-state" ["[hyperopen.views.chart.hover :as hover]"]))
      (write-boundary-exceptions! root [{:path "src/hyperopen/funding/domain/modal_state.cljs"
                                         :import-ns "hyperopen.views.chart.hover"
                                         :owner "platform"
                                         :reason "Should never be allowed"
                                         :retire-by "2026-06-30"}])
      (let [codes (set (map :code (boundaries/check-repo root (test-config))))]
        (is (contains? codes :forbidden-domain-view-import))
        (is (contains? codes :boundary-exception-forbidden))))))

(deftest malformed-boundary-exception-entry-is-reported
  (with-temp-repo
    (fn [root]
      (write-file! root
                   "src/hyperopen/portfolio/actions.cljs"
                   (ns-form "hyperopen.portfolio.actions" ["[hyperopen.views.chart.hover :as hover]"]))
      (write-boundary-exceptions! root [{:path "src/hyperopen/portfolio/actions.cljs"
                                         :import-ns "hyperopen.foo"
                                         :owner ""
                                         :reason " "
                                         :retire-by "nope"}])
      (let [codes (set (map :code (boundaries/check-repo root (test-config))))]
        (is (contains? codes :invalid-boundary-exception))))))

(deftest expired-boundary-exception-is-reported
  (with-temp-repo
    (fn [root]
      (write-file! root
                   "src/hyperopen/portfolio/actions.cljs"
                   (ns-form "hyperopen.portfolio.actions" ["[hyperopen.views.chart.hover :as hover]"]))
      (write-boundary-exceptions! root [{:path "src/hyperopen/portfolio/actions.cljs"
                                         :import-ns "hyperopen.views.chart.hover"
                                         :owner "platform"
                                         :reason "Temporary bridge"
                                         :retire-by "2026-03-01"}])
      (let [codes (set (map :code (boundaries/check-repo root (test-config))))]
        (is (contains? codes :expired-boundary-exception))))))

(deftest stale-boundary-exception-is-reported-when-import-is-removed
  (with-temp-repo
    (fn [root]
      (write-file! root
                   "src/hyperopen/portfolio/actions.cljs"
                   (ns-form "hyperopen.portfolio.actions" ["[hyperopen.ui.chart.hover :as hover]"]))
      (write-boundary-exceptions! root [{:path "src/hyperopen/portfolio/actions.cljs"
                                         :import-ns "hyperopen.views.chart.hover"
                                         :owner "platform"
                                         :reason "Temporary bridge"
                                         :retire-by "2026-06-30"}])
      (let [codes (set (map :code (boundaries/check-repo root (test-config))))]
        (is (contains? codes :stale-boundary-exception))))))

(deftest boundary-exception-must-match-exact-import
  (with-temp-repo
    (fn [root]
      (write-file! root
                   "src/hyperopen/portfolio/actions.cljs"
                   (ns-form "hyperopen.portfolio.actions" ["[hyperopen.views.chart.hover :as hover]"]))
      (write-boundary-exceptions! root [{:path "src/hyperopen/portfolio/actions.cljs"
                                         :import-ns "hyperopen.views.account-info.sort-kernel"
                                         :owner "platform"
                                         :reason "Temporary bridge"
                                         :retire-by "2026-06-30"}])
      (let [codes (set (map :code (boundaries/check-repo root (test-config))))]
        (is (contains? codes :missing-boundary-exception))
        (is (contains? codes :stale-boundary-exception))))))

(defn -main
  [& _args]
  (let [{:keys [fail error]} (run-tests 'dev.check-namespace-boundaries-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
