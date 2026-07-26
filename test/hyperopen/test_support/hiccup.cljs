 (ns hyperopen.test-support.hiccup
   (:require [clojure.string :as str]))

 (defn node-attrs
   [node]
   (when (and (vector? node) (map? (second node)))
     (second node)))

 (defn node-children
   [node]
   (if (map? (second node))
     (drop 2 node)
     (drop 1 node)))

 (defn class-values
   [class-attr]
   (cond
     (nil? class-attr) []
     (string? class-attr) (remove str/blank? (str/split class-attr #"\s+"))
     (sequential? class-attr) (mapcat class-values class-attr)
     :else []))

 (defn- classes-from-tag
   [tag]
   (if (keyword? tag)
     (let [parts (str/split (name tag) #"\.")]
       (if (> (count parts) 1)
         (rest parts)
         []))
     []))

 (defn node-class-set
   [node]
   (set (concat (classes-from-tag (first node))
                (class-values (:class (node-attrs node))))))

 (defn root-class-set
   [node]
   (node-class-set node))

 (defn direct-texts
   [node]
   (->> (node-children node)
        (filter string?)
        set))

 (defn collect-strings
   [node]
   (cond
     (string? node) [node]
     (vector? node) (mapcat collect-strings (node-children node))
     (seq? node) (mapcat collect-strings node)
     :else []))

 (defn node-text
   [node]
   (str/join " " (collect-strings node)))

 (defn find-first-node
   [node pred]
   (cond
     (vector? node)
     (let [children (node-children node)]
       (or (when (pred node) node)
           (some #(find-first-node % pred) children)))

     (seq? node)
     (some #(find-first-node % pred) node)

     :else nil))

 (defn find-all-nodes
   [node pred]
   (cond
     (vector? node)
     (let [children (node-children node)
           self-match (when (pred node) [node])]
       (into (or self-match [])
             (mapcat #(find-all-nodes % pred) children)))

     (seq? node)
     (mapcat #(find-all-nodes % pred) node)

     :else []))

 (defn count-nodes
   [node pred]
   (cond
     (vector? node)
     (let [children (node-children node)
           self-count (if (pred node) 1 0)]
       (+ self-count
          (reduce + 0 (map #(count-nodes % pred) children))))

     (seq? node)
     (reduce + 0 (map #(count-nodes % pred) node))

     :else 0))

 (defn find-by-data-role
   [node data-role]
   (find-first-node node #(= data-role (get-in % [1 :data-role]))))

 (defn find-by-parity-id
   [node parity-id]
   (find-first-node node #(= parity-id (get-in % [1 :data-parity-id]))))

 (defn contains-class?
   [node class-name]
   (boolean
    (find-first-node node #(contains? (node-class-set %) class-name))))

 (defn with-viewport-width
   [width f]
   (let [original-inner-width (.-innerWidth js/globalThis)]
     (set! (.-innerWidth js/globalThis) width)
     (try
       (f)
       (finally
         (set! (.-innerWidth js/globalThis) original-inner-width)))))
