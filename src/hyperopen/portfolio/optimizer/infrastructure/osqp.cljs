(ns hyperopen.portfolio.optimizer.infrastructure.osqp
  (:require ["osqp" :default OSQP]
            [hyperopen.portfolio.optimizer.infrastructure.fallback :as fallback]
            [hyperopen.portfolio.optimizer.infrastructure.problem-adapter :as problem-adapter]
            [hyperopen.portfolio.optimizer.infrastructure.quadprog :as quadprog]))

(def ^:private osqp-infinity
  1.0e20)

(defn- float64-array
  [values]
  (js/Float64Array.from (clj->js values)))

(defn- int32-array
  [values]
  (js/Int32Array.from (clj->js values)))

(defn dense->csc
  [matrix opts]
  (let [upper-triangle? (:upper-triangle? opts)
        n-cols (if (seq matrix) (count (first matrix)) 0)]
    (loop [col 0
           data []
           row-indices []
           column-pointers [0]]
      (if (= col n-cols)
        #js {:data (float64-array data)
             :row_indices (int32-array row-indices)
             :column_pointers (int32-array column-pointers)}
        (let [entries (->> (range (count matrix))
                           (keep (fn [row]
                                   (let [value (get-in matrix [row col])]
                                     (when (and (number? value)
                                                (not (zero? value))
                                                (or (not upper-triangle?)
                                                    (<= row col)))
                                       {:row row
                                        :value value})))))
              data* (into data (map :value entries))
              rows* (into row-indices (map :row entries))]
          (recur (inc col)
                 data*
                 rows*
                 (conj column-pointers (count data*))))))))

(defn- unit-row
  [n idx]
  (mapv (fn [i]
          (if (= i idx) 1 0))
        (range n)))

(defn- rows
  [problem]
  (let [n (count (:instrument-ids problem))
        equality-rows (mapv (fn [constraint]
                              {:coefficients (:coefficients constraint)
                               :lower (:target constraint)
                               :upper (:target constraint)})
                            (:equalities problem))
        inequality-rows (mapcat (fn [constraint]
                                  (concat
                                   (when (number? (:lower constraint))
                                     [{:coefficients (:coefficients constraint)
                                       :lower (:lower constraint)
                                       :upper osqp-infinity}])
                                   (when (number? (:upper constraint))
                                     [{:coefficients (:coefficients constraint)
                                       :lower (- osqp-infinity)
                                       :upper (:upper constraint)}])))
                                (:inequalities problem))
        bound-rows (mapv (fn [idx lower upper]
                           {:coefficients (unit-row n idx)
                            :lower (if (number? lower) lower (- osqp-infinity))
                            :upper (if (number? upper) upper osqp-infinity)})
                         (range n)
                         (:lower-bounds problem)
                         (:upper-bounds problem))]
    (vec (concat equality-rows inequality-rows bound-rows))))

(defn- options
  [problem]
  (let [rows (rows problem)]
    #js {:P (dense->csc (problem-adapter/add-diagonal-epsilon (:quadratic problem))
                        {:upper-triangle? true})
         :A (dense->csc (mapv :coefficients rows)
                        {:upper-triangle? false})
         :q (float64-array (:linear problem))
         :l (float64-array (mapv :lower rows))
         :u (float64-array (mapv :upper rows))}))

(defn- settings
  []
  #js {:verbose false
       :eps_abs 0.00000001
       :eps_rel 0.00000001
       :polish true
       :max_iter 10000})

(defn- normalize-solution
  [problem solution decode]
  (let [weights (decode (vec (js->clj solution)))]
    {:status :solved
     :solver :osqp
     :weights weights
     :objective-value (problem-adapter/objective-value problem weights)}))

(defonce ^:private solve-chain
  ;; The osqp npm package backs every OSQP instance with one shared WASM
  ;; module and heap. The display-frontier sweep solves its points through
  ;; js/Promise.all, so without coordination several .setup/.solve/.cleanup
  ;; cycles race on that one shared heap. That race leaves the module in a
  ;; state where unrelated later solves throw and silently fall back to
  ;; quadprog. Serialize every OSQP solve through a single promise chain so
  ;; only one runs against the shared module at a time.
  (atom (js/Promise.resolve)))

(defn- run-serialized
  [work]
  (let [result (.then @solve-chain work work)]
    ;; Keep the chain alive whether this solve fulfils or rejects, so one bad
    ;; solve never blocks the queue.
    (reset! solve-chain (.then result (fn [_] nil) (fn [_] nil)))
    result))

(defn- solve-on-shared-module
  [problem]
  (let [{adapted-problem :problem decode :decode} (problem-adapter/adapt-problem problem)]
    (-> (.setup OSQP (options adapted-problem) (settings))
        (.then (fn [^js solver]
                 (try
                   (let [solution (.solve solver)]
                     (normalize-solution problem solution decode))
                   (finally
                     (.cleanup solver)))))
        (.catch (fn [err]
                  (fallback/recover-osqp-error problem err quadprog/solve))))))

(defn solve
  [problem]
  (if-let [unsupported (problem-adapter/unsupported-l1-constraints problem)]
    (js/Promise.resolve
     (problem-adapter/unsupported-result :invalid-l1-constraints
                                         {:constraints (vec unsupported)}))
    (run-serialized (fn [_] (solve-on-shared-module problem)))))
