(ns hyperopen.workbench.portfolio-client-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as str]))

(def ^:private fs (js/require "node:fs"))

(defn- slurp-file [path]
  (.readFileSync fs path "utf8"))

(deftest uses-non-conflicting-workbench-assets
  (testing "workbench assets avoid the /portfolio route namespace"
    (let [client-source (slurp-file "portfolio/portfolio/ui/client.cljs")
          shadow-config (slurp-file "shadow-cljs.edn")]
      (is (str/includes? client-source
                         "(def css-file (str workbench-asset-root \"/styles/portfolio.css\"))"))
      (is (str/includes? client-source
                         "(def ^:private prism-file (str workbench-asset-root \"/prism.js\"))"))
      (is (not (str/includes? shadow-config "classpath:public"))))))
