#!/usr/bin/env bb

(ns dev.check-namespace-sizes-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [dev.check-namespace-sizes :as sizes]))

(def test-today
  (java.time.LocalDate/of 2026 3 25))

(defn test-config
  ([] (test-config {}))
  ([overrides]
   (merge {:threshold 500
           :exceptions-path "dev/namespace_size_exceptions.edn"
           :scan-dirs ["src" "test"]
           :excluded-paths #{"test/test_runner_generated.cljs"}
           :today test-today}
          overrides)))

(defn delete-recursive!
  [file]
  (when (.exists file)
    (doseq [child (reverse (file-seq file))]
      (.delete child))))

(defn with-temp-repo
  [f]
  (let [tmp-path (java.nio.file.Files/createTempDirectory "namespace-size-check" (make-array java.nio.file.attribute.FileAttribute 0))
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

(defn ns-text
  [line-count]
  (str "(ns temp.namespace)\n"
       (apply str (repeat (max 0 (dec line-count)) ";; filler\n"))))

(defn write-exceptions!
  [root entries]
  (write-file! root "dev/namespace_size_exceptions.edn" (str (pr-str entries) "\n")))

(deftest passing-repo-has-no-errors
  (with-temp-repo
    (fn [root]
      (write-file! root "src/hyperopen/example/core.cljs" (ns-text 499))
      (write-file! root "src/hyperopen/example/oversized.cljs" (ns-text 501))
      (write-file! root "test/test_runner_generated.cljs" (ns-text 800))
      (write-exceptions! root [{:path "src/hyperopen/example/oversized.cljs"
                                :owner "platform"
                                :reason "Temporary oversized namespace"
                                :max-lines 501
                                :retire-by "2026-06-30"}])
      (is (empty? (sizes/check-repo root (test-config)))))))

(deftest oversized-namespace-without-exception-is-reported
  (with-temp-repo
    (fn [root]
      (write-file! root "src/hyperopen/example/oversized.cljs" (ns-text 501))
      (write-exceptions! root [])
      (let [codes (set (map :code (sizes/check-repo root (test-config))))]
        (is (contains? codes :missing-size-exception))))))

(deftest malformed-size-exception-entry-is-reported
  (with-temp-repo
    (fn [root]
      (write-file! root "src/hyperopen/example/oversized.cljs" (ns-text 501))
      (write-exceptions! root [{:path "src/hyperopen/example/oversized.cljs"
                                :owner ""
                                :reason " "
                                :max-lines "501"
                                :retire-by "not-a-date"}])
      (let [codes (set (map :code (sizes/check-repo root (test-config))))]
        (is (contains? codes :invalid-size-exception))))))

(deftest expired-size-exception-is-reported
  (with-temp-repo
    (fn [root]
      (write-file! root "src/hyperopen/example/oversized.cljs" (ns-text 501))
      (write-exceptions! root [{:path "src/hyperopen/example/oversized.cljs"
                                :owner "platform"
                                :reason "Temporary oversized namespace"
                                :max-lines 501
                                :retire-by "2026-03-01"}])
      (let [codes (set (map :code (sizes/check-repo root (test-config))))]
        (is (contains? codes :expired-size-exception))))))

(deftest namespace-exceeds-exception-max-lines-is-reported
  (with-temp-repo
    (fn [root]
      (write-file! root "src/hyperopen/example/oversized.cljs" (ns-text 510))
      (write-exceptions! root [{:path "src/hyperopen/example/oversized.cljs"
                                :owner "platform"
                                :reason "Temporary oversized namespace"
                                :max-lines 505
                                :retire-by "2026-06-30"}])
      (let [codes (set (map :code (sizes/check-repo root (test-config))))]
        (is (contains? codes :size-exception-exceeded))))))

(deftest stale-size-exception-is-reported-when-file-drops-below-threshold
  (with-temp-repo
    (fn [root]
      (write-file! root "src/hyperopen/example/core.cljs" (ns-text 400))
      (write-exceptions! root [{:path "src/hyperopen/example/core.cljs"
                                :owner "platform"
                                :reason "Temporary oversized namespace"
                                :max-lines 520
                                :retire-by "2026-06-30"}])
      (let [codes (set (map :code (sizes/check-repo root (test-config))))]
        (is (contains? codes :stale-size-exception))))))

(deftest exception-path-without-file-is-reported
  (with-temp-repo
    (fn [root]
      (write-file! root "src/hyperopen/example/core.cljs" (ns-text 10))
      (write-exceptions! root [{:path "src/hyperopen/example/missing.cljs"
                                :owner "platform"
                                :reason "Temporary oversized namespace"
                                :max-lines 520
                                :retire-by "2026-06-30"}])
      (let [codes (set (map :code (sizes/check-repo root (test-config))))]
        (is (contains? codes :invalid-size-exception))))))

(defn -main
  [& _args]
  (let [{:keys [fail error]} (run-tests 'dev.check-namespace-sizes-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
