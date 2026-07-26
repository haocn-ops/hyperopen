(ns hyperopen.order.outcome-option-sort)

(def ^:private outcome-option-sort-columns
  #{:label :chance :price :volume :open-interest})

(defn- normalize-sort-column
  [column]
  (let [column* (cond
                  (keyword? column) column
                  (string? column) (keyword column)
                  :else nil)]
    (when (contains? outcome-option-sort-columns column*)
      column*)))

(defn- default-sort-direction
  [column]
  (if (= :label column) :asc :desc))

(defn set-outcome-option-sort [state column]
  (if-let [column* (normalize-sort-column column)]
    (let [current-column (get-in state [:trade-ui :outcome-option-sort-by])
          current-direction (get-in state [:trade-ui :outcome-option-sort-direction])
          next-direction (if (= current-column column*)
                           (if (= current-direction :asc) :desc :asc)
                           (default-sort-direction column*))]
      [[:effects/save-many [[[:trade-ui :outcome-option-sort-by] column*]
                            [[:trade-ui :outcome-option-sort-direction] next-direction]]]])
    []))
