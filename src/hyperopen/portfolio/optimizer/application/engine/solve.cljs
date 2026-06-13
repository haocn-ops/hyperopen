(ns hyperopen.portfolio.optimizer.application.engine.solve
  (:require [hyperopen.portfolio.optimizer.domain.closed-form :as closed-form]))

(defn default-solve-problem
  [_problem]
  {:status :error
   :reason :solver-not-configured})

(defn solve-one
  "Solves a single planned problem. Closed-form problems are computed by the
  pure domain solver and never reach the injected QP solver."
  [problem solve-problem]
  (if (= :closed-form-portfolio (:kind problem))
    (closed-form/solve-portfolio problem)
    (solve-problem problem)))

(defn solve-plan
  [solver-plan solve-problem]
  (mapv (fn [problem]
          (let [result (solve-one problem solve-problem)]
            (assoc result :problem problem)))
        (:problems solver-plan)))

(defn solve-display-frontier-plans
  [display-frontier-plans solve-problem]
  (into {}
        (keep (fn [[constraint-mode display-frontier-plan]]
                (when display-frontier-plan
                  [constraint-mode
                   (solve-plan display-frontier-plan solve-problem)])))
        display-frontier-plans))

(defn- report-progress!
  [on-progress payload]
  (when (fn? on-progress)
    (on-progress payload)))

(defn- solve-problems-async
  [problems solve-problem on-problem-result!]
  (-> (js/Promise.all
       (clj->js
        (mapv
         (fn [problem]
           (-> (js/Promise.resolve (solve-one problem solve-problem))
               (.then
                (fn [result]
                  (when on-problem-result!
                    (on-problem-result!))
                  (assoc result :problem problem)))))
         problems)))
      (.then (fn [results]
               (vec (array-seq results))))))

(def solve-start-percent
  "Solve-step percent reported when the plan is handed to the solver;
  per-problem updates never drop below it."
  5)

(defn solve-plan-async
  ([solver-plan solve-problem]
   (solve-plan-async solver-plan solve-problem nil))
  ([solver-plan solve-problem on-progress]
   (let [problems (vec (:problems solver-plan))
         total (count problems)
         completed (atom 0)]
     (solve-problems-async
      problems
      solve-problem
      (fn []
        (let [done (swap! completed inc)]
          (report-progress!
           on-progress
           {:step :solve
            :status (if (= done total) :succeeded :running)
            :percent (if (pos? total)
                       (max solve-start-percent
                            (* 100 (/ done total)))
                       100)
            :detail (str done "/" total " problems")})))))))

(def frontier-sweep-percent-span
  "The :frontier step reserves the tail of its bar for selection and
  result assembly, so per-point sweep progress tops out below 100%."
  75)

(defn solve-display-frontier-plans-async
  ([display-frontier-plans solve-problem]
   (solve-display-frontier-plans-async display-frontier-plans solve-problem nil))
  ([display-frontier-plans solve-problem on-progress]
   (let [entries (vec (keep (fn [[constraint-mode display-frontier-plan]]
                              (when display-frontier-plan
                                [constraint-mode display-frontier-plan]))
                            display-frontier-plans))
         total (reduce + 0 (map #(count (:problems (second %))) entries))
         completed (atom 0)
         report-point! (fn []
                         (let [done (swap! completed inc)]
                           (report-progress!
                            on-progress
                            {:step :frontier
                             :status :running
                             :percent (if (pos? total)
                                        (* frontier-sweep-percent-span (/ done total))
                                        0)
                             :detail (str done "/" total " points")})))]
     (when (pos? total)
       (report-progress!
        on-progress
        {:step :frontier
         :status :running
         :percent 0
         :detail (str "0/" total " points")}))
     (reduce (fn [chain [constraint-mode display-frontier-plan]]
               (.then chain
                      (fn [results-by-mode]
                        (-> (solve-problems-async (vec (:problems display-frontier-plan))
                                                  solve-problem
                                                  report-point!)
                            (.then (fn [results]
                                     (assoc results-by-mode constraint-mode results)))))))
             (js/Promise.resolve {})
             entries))))
