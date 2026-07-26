(ns tools.mutate.literals
  (:require [clojure.walk :as walk]))

(deftype LiteralNode [value loc])

(defn make-literal-node
  [value loc]
  (LiteralNode. value loc))

(defn literal-node?
  [obj]
  (instance? LiteralNode obj))

(defn literal-value
  [obj]
  (if (literal-node? obj)
    (.-value ^LiteralNode obj)
    obj))

(defn literal-location
  [obj]
  (when (literal-node? obj)
    (.-loc ^LiteralNode obj)))

(defn positionless-literal?
  [obj]
  (or (number? obj)
      (true? obj)
      (false? obj)
      (nil? obj)))

(defn stable-form
  [form]
  (walk/prewalk literal-value form))
