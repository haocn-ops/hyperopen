(ns hyperopen.views.active-asset.test-support
  (:require [clojure.string :as str]
            [hyperopen.test-support.hiccup :as hiccup]))

(def collect-strings hiccup/collect-strings)

(defn collect-path-ds
  [node]
  (cond
    (vector? node)
    (let [attrs (when (map? (second node)) (second node))
          d-value (:d attrs)
          children (if (map? (second node))
                     (drop 2 node)
                     (drop 1 node))]
      (concat (when d-value [d-value])
              (mapcat collect-path-ds children)))

    (seq? node)
    (mapcat collect-path-ds node)

    :else
    []))

(defn find-node
  [pred node]
  (cond
    (vector? node)
    (or (when (pred node) node)
        (some #(find-node pred %) (rest node)))

    (seq? node)
    (some #(find-node pred %) node)

    :else nil))

(defn find-node-by-role
  [node role]
  (find-node #(and (vector? %)
                   (= role (get-in % [1 :data-role])))
             node))

(def class-values hiccup/class-values)

(def contains-class? hiccup/contains-class?)

(def find-first-node hiccup/find-first-node)

(defn find-img-nodes
  [node]
  (letfn [(walk [n]
            (cond
              (vector? n)
              (let [tag (first n)
                    attrs (when (map? (second n)) (second n))
                    children (if attrs (drop 2 n) (drop 1 n))
                    child-results (mapcat walk children)]
                (if (and (keyword? tag)
                         (str/starts-with? (name tag) "img"))
                  (cons n child-results)
                  child-results))

              (seq? n)
              (mapcat walk n)

              :else
              []))]
    (vec (walk node))))

(defn find-nodes-with-style-key
  [node style-key]
  (letfn [(walk [n]
            (cond
              (vector? n)
              (let [attrs (when (map? (second n)) (second n))
                    children (if attrs (drop 2 n) (drop 1 n))
                    child-results (mapcat walk children)]
                (if (contains? (or (:style attrs) {}) style-key)
                  (cons n child-results)
                  child-results))

              (seq? n)
              (mapcat walk n)

              :else
              []))]
    (vec (walk node))))

(def with-viewport-width hiccup/with-viewport-width)

(defn fake-image-node
  [{:keys [complete? natural-width]}]
  (let [listeners (atom {})
        removed-listeners (atom [])]
    {:node (doto (js-obj)
             (aset "complete" (boolean complete?))
             (aset "naturalWidth" (or natural-width 0))
             (aset "addEventListener"
                   (fn [event handler]
                     (swap! listeners assoc event handler)))
             (aset "removeEventListener"
                   (fn [event handler]
                     (swap! removed-listeners conj [event handler]))))
     :listeners listeners
     :removed-listeners removed-listeners}))

(defn funding-tooltip-pin-id
  [coin]
  (str "funding-rate-tooltip-pin-"
       (-> (or coin "asset")
           str
           str/lower-case
           (str/replace #"[^a-z0-9_-]" "-"))))

(defn with-visible-funding-tooltip
  [state coin]
  (assoc-in state
            [:funding-ui :tooltip :visible-id]
            (funding-tooltip-pin-id coin)))

(defn active-asset-row-ctx
  ([] (active-asset-row-ctx {}))
  ([overrides]
   (merge {} overrides)))

(defn active-asset-market
  ([] (active-asset-market {}))
  ([overrides]
   (merge {:market-type :perp}
          overrides)))
