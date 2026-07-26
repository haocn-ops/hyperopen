(ns hyperopen.views.trading-chart.runtime-state)

(defonce ^:private runtime-sidecar (js/WeakMap.))

(defn get-state
  "Return runtime state map for chart mount node."
  [node]
  (if node
    (or (.get runtime-sidecar node) {})
    {}))

(defn set-state!
  "Replace runtime state map for chart mount node."
  [node state]
  (when node
    (.set runtime-sidecar node state))
  state)

(defn assoc-state!
  "Merge key/value pairs into mount node runtime state."
  [node & kvs]
  (let [next-state (apply assoc (get-state node) kvs)]
    (set-state! node next-state)))

(defn update-state!
  "Apply `f` to current runtime state and replace it."
  [node f & args]
  (let [next-state (apply f (get-state node) args)]
    (set-state! node next-state)))

(defn clear-state!
  "Clear runtime state for chart mount node."
  [node]
  (when node
    (.delete runtime-sidecar node)))
