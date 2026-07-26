(ns tools.mutate.coverage
  (:require [tools.crap.coverage :as lcov]))

(def suite-order
  [:test :ws-test])

(defn load-records
  [root coverage-file]
  (lcov/read-lcov root coverage-file))

(defn load-optional-records
  [root coverage-file]
  (try
    (load-records root coverage-file)
    (catch Exception _
      nil)))

(defn line-builds
  [records]
  (lcov/line-builds-by-file records))

(defn- ordered-suites
  [suites]
  (vec (filter (set suites) suite-order)))

(defn covered-builds-for-line
  [line-build-map module line]
  (ordered-suites (lcov/covered-builds-for-line line-build-map module line)))

(defn required-suites
  [suite line-build-map module {:keys [line]}]
  (cond
    (nil? line)
    (case suite
      :auto suite-order
      [suite])

    (= suite :auto)
    (let [builds (covered-builds-for-line line-build-map module line)]
      (when (seq builds)
        builds))

    :else
    (let [builds (set (covered-builds-for-line line-build-map module line))]
      (when (contains? builds suite)
        [suite]))))

(defn partition-sites
  [module sites line-build-map suite]
  (reduce (fn [[covered uncovered] site]
            (if-let [suites (required-suites suite line-build-map module site)]
              [(conj covered (assoc site :required-suites suites))
               uncovered]
              [covered (conj uncovered site)]))
          [[] []]
          sites))
