(ns hyperopen.ui.table.sort-kernel)

(defn zero-accessor [_]
  0)

(defn resolve-column-accessor
  ([column accessor-by-column]
   (resolve-column-accessor column accessor-by-column zero-accessor))
  ([column accessor-by-column fallback-accessor]
   (or (get accessor-by-column column)
       fallback-accessor
       zero-accessor)))

(defn apply-direction [sorted-rows direction]
  (if (= direction :desc)
    (reverse sorted-rows)
    sorted-rows))

(defn- row-sort-key-fn [accessor tie-breaker]
  (if tie-breaker
    (fn [row]
      [(accessor row)
       (tie-breaker row)])
    accessor))

(defn sort-rows-by-column
  [rows {:keys [column direction accessor-by-column fallback-accessor tie-breaker]}]
  (let [accessor (resolve-column-accessor column accessor-by-column fallback-accessor)
        sorted-rows (sort-by (row-sort-key-fn accessor tie-breaker) rows)]
    (apply-direction sorted-rows direction)))
