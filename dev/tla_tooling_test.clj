#!/usr/bin/env bb

(ns dev.tla-tooling-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests testing]]
            [tools.tla :as tla]))

(def websocket-spec
  {:id "websocket-runtime"
   :module-name "websocket_runtime"
   :spec-file "spec/tla/websocket_runtime.tla"
   :config-file "spec/tla/websocket_runtime.cfg"})

(def websocket-liveness-spec
  {:id "websocket-runtime-liveness"
   :module-name "websocket_runtime"
   :spec-file "spec/tla/websocket_runtime.tla"
   :config-file "spec/tla/websocket_runtime_liveness.cfg"})

(defn delete-recursive!
  [file]
  (when (.exists file)
    (doseq [child (reverse (file-seq file))]
      (.delete child))))

(defn with-temp-root
  [f]
  (let [tmp-path (java.nio.file.Files/createTempDirectory "tla-tooling" (make-array java.nio.file.attribute.FileAttribute 0))
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

(deftest parse-args-requires-supported-spec-test
  (is (= {:command "verify"
          :spec websocket-spec}
         (#'tla/parse-args ["verify" "--spec" "websocket-runtime"])))
  (is (= {:command "verify"
          :spec websocket-liveness-spec}
         (#'tla/parse-args ["verify" "--spec" "websocket-runtime-liveness"])))
  (is (thrown-with-msg?
       Exception
       #"Unsupported spec: unknown-spec"
       (#'tla/parse-args ["verify" "--spec" "unknown-spec"]))))

(deftest ensure-tla-tools-jar-fails-fast-with-install-guidance-test
  (with-temp-root
    (fn [root]
      (with-redefs [tools.tla/repo-root (constantly (io/file root))
                    tools.tla/env (constantly nil)]
        (is (thrown-with-msg?
             Exception
             #"Normal repo gates do not require this jar|TLA2TOOLS_JAR=.*tools/tla/vendor/tla2tools.jar|TLA2TOOLS_JAR"
             (#'tla/ensure-tla-tools-jar!)))))))

(deftest ensure-tla-tools-jar-prefers-env-then-vendor-test
  (with-temp-root
    (fn [root]
      (write-file! root "tools/tla/vendor/tla2tools.jar" "vendor-jar")
      (write-file! root "custom/tla2tools.jar" "env-jar")
      (with-redefs [tools.tla/repo-root (constantly (io/file root))
                    tools.tla/env (fn [_] (str root "/custom/tla2tools.jar"))]
        (is (= (str root "/custom/tla2tools.jar")
               (#'tla/ensure-tla-tools-jar!))))
      (with-redefs [tools.tla/repo-root (constantly (io/file root))
                    tools.tla/env (constantly nil)]
        (is (= (str root "/tools/tla/vendor/tla2tools.jar")
               (#'tla/ensure-tla-tools-jar!)))))))

(deftest verify-spec-runs-tlc-from-target-tla-root-test
  (with-temp-root
    (fn [root]
      (write-file! root "spec/tla/websocket_runtime.tla" "---- MODULE websocket_runtime ----")
      (write-file! root "spec/tla/websocket_runtime.cfg" "SPECIFICATION Spec")
      (write-file! root "spec/tla/websocket_runtime_liveness.cfg" "SPECIFICATION LivenessSpec")
      (write-file! root "tools/tla/vendor/tla2tools.jar" "jar")
      (let [invocation (atom nil)]
        (with-redefs [tools.tla/repo-root (constantly (io/file root))
                      tools.tla/env (constantly nil)
                      tools.tla/run-command (fn [command args opts]
                                              (reset! invocation {:command command
                                                                  :args args
                                                                  :dir (:dir opts)})
                                              {:command (cons command args)
                                               :dir (:dir opts)
                                               :exit 0
                                               :output "TLC finished"})]
          (let [output (with-out-str
                         (tla/run! ["verify" "--spec" "websocket-runtime"]))
                run-root (io/file root "target/tla/websocket-runtime")
                log-file (io/file run-root "tlc.log")
                metadata-root (io/file run-root "metadata")]
            (is (.contains output "Verified websocket-runtime with TLC."))
            (is (= "java" (:command @invocation)))
            (is (= (.getCanonicalPath run-root)
                   (.getCanonicalPath ^java.io.File (:dir @invocation))))
            (is (= (.getCanonicalPath metadata-root)
                   (nth (:args @invocation) 10)))
            (is (= (.getCanonicalPath (io/file root "spec/tla/websocket_runtime.tla"))
                   (last (:args @invocation))))
            (is (= "TLC finished"
                   (slurp log-file)))))))))

(deftest verify-spec-fails-when-spec-inputs-are-missing-test
  (with-temp-root
    (fn [root]
      (write-file! root "tools/tla/vendor/tla2tools.jar" "jar")
      (with-redefs [tools.tla/repo-root (constantly (io/file root))
                    tools.tla/env (constantly nil)]
        (testing "missing module is rejected"
          (is (thrown-with-msg?
               Exception
               #"Missing TLA\+ spec module"
               (tla/run! ["verify" "--spec" "websocket-runtime"]))))
        (write-file! root "spec/tla/websocket_runtime.tla" "---- MODULE websocket_runtime ----")
        (testing "missing config is rejected"
          (is (thrown-with-msg?
               Exception
               #"Missing TLA\+ config"
               (tla/run! ["verify" "--spec" "websocket-runtime"]))))))))

(defn -main
  [& _args]
  (let [{:keys [fail error]} (run-tests 'dev.tla-tooling-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
