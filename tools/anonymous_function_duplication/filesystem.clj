(ns tools.anonymous-function-duplication.filesystem
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn canonical-path
  [path]
  (.getCanonicalPath (io/file path)))

(defn relativize
  [root path]
  (let [root-path (.toPath (io/file (canonical-path root)))
        file-path (.toPath (io/file (canonical-path path)))]
    (str (.relativize root-path file-path))))

(defn cljs-files-under
  [root-dir]
  (let [root (io/file root-dir)]
    (->> (file-seq root)
         (filter #(.isFile ^java.io.File %))
         (map #(.getPath ^java.io.File %))
         (filter #(str/ends-with? % ".cljs"))
         sort)))
