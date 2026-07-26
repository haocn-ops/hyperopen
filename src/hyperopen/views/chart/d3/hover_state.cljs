(ns hyperopen.views.chart.d3.hover-state)

(defonce ^:private hovered-surfaces*
  (atom #{}))

(defn set-surface-hover-active!
  [surface active?]
  (let [surface* (when (keyword? surface)
                   surface)]
    (when surface*
      (swap! hovered-surfaces*
             (fn [surfaces]
               (if active?
                 (conj surfaces surface*)
                 (disj surfaces surface*))))))
  nil)

(defn surface-hover-active?
  [surface]
  (contains? @hovered-surfaces* surface))

(defn clear-hover-state!
  []
  (reset! hovered-surfaces* #{})
  nil)
