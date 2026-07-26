(ns hyperopen.portfolio.optimizer.application.run-bridge-boundary-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]))

(def ^:private fs (js/require "fs"))
(def ^:private path (js/require "path"))

(def ^:private forbidden-workflow-patterns
  [{:label "worker-client"
    :pattern #"worker-client"}
   {:label "system/store"
    :pattern #"hyperopen\.system|system/store"}
   {:label "browser clock"
    :pattern #"js/Date"}
   {:label "hidden mutable runtime state"
    :pattern #"\batom\b|\bdelay\b"}
   {:label "store mutation"
    :pattern #"reset!|swap!"}
   {:label "worker construction"
    :pattern #"make-worker!"}
   {:label "worker posting"
    :pattern #"post-run!"}
   {:label "worker listener installation"
    :pattern #"add-message-listener!"}])

(defn- project-root
  []
  (.cwd js/process))

(defn- join-path
  [& parts]
  (reduce (fn [acc part]
            (.join path acc part))
          (first parts)
          (rest parts)))

(defn- read-text
  [file-path]
  (.readFileSync fs file-path "utf8"))

(defn- relative-path
  [file-path]
  (.relative path (project-root) file-path))

(defn- workflow-violations
  [source]
  (->> forbidden-workflow-patterns
       (keep (fn [{:keys [label pattern]}]
               (when (re-find pattern source)
                 label)))
       vec))

(deftest run-bridge-runtime-side-effects-stay-out-of-application-test
  (let [retired-run-bridge-path (join-path (project-root)
                                           "src"
                                           "hyperopen"
                                           "portfolio"
                                           "optimizer"
                                           "application"
                                           "run_bridge.cljs")
        workflow-path (join-path (project-root)
                                 "src"
                                 "hyperopen"
                                 "portfolio"
                                 "optimizer"
                                 "application"
                                 "run_bridge_workflow.cljs")
        workflow-source (read-text workflow-path)
        violations (workflow-violations workflow-source)]
    (testing "the side-effecting run bridge controller is not an application namespace"
      (is (not (.existsSync fs retired-run-bridge-path))
          (str (relative-path retired-run-bridge-path)
               " should be retired; runtime controllers and interpreters belong in optimizer infrastructure or runtime effect adapters.")))
    (testing "run_bridge_workflow remains pure application logic"
      (is (empty? violations)
          (str (relative-path workflow-path)
               " mentions runtime side-effect owners: "
               (str/join ", " violations))))))
