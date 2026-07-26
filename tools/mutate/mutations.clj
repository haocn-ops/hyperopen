(ns tools.mutate.mutations
  (:require [tools.mutate.literals :as literals]))

(defn- literal=
  [expected node]
  (= expected (literals/literal-value node)))

(defn- rand-comparison?
  [{:keys [parent]}]
  (and (seq? parent)
       (>= (count parent) 3)
       (let [second-elem (second parent)]
         (and (seq? second-elem)
              (= 'rand (first second-elem))))))

(defn- rand-nth-guard-form?
  [form]
  (and (seq? form)
       (let [head (first form)]
         (and (or (= 'if head) (= 'if-not head))
              (>= (count form) 4)
              (let [cond-form (second form)
                    else-form (nth form 3 nil)]
                (and (seq? cond-form)
                     (= '= (first cond-form))
                     (some #(literal= 1 %) (rest cond-form))
                     (seq? else-form)
                     (= 'rand-nth (first else-form))))))))

(defn- rand-nth-single-element-guard?
  [{:keys [parent grandparent]}]
  (or (rand-nth-guard-form? parent)
      (rand-nth-guard-form? grandparent)))

(defn- inside-rand-nth-literal?
  [{:keys [parent grandparent]}]
  (and (vector? parent)
       (or (and (seq? grandparent) (= 'rand-nth (first grandparent)))
           (and (vector? grandparent)
                (every? #(and (vector? %)
                              (every? (fn [node]
                                        (number? (literals/literal-value node)))
                                      %))
                        grandparent)))))

(defn- subvec-trim-boundary?
  [{:keys [grandparent]}]
  (and (seq? grandparent)
       (let [head (first grandparent)]
         (and (or (= 'if head) (= 'if-not head))
              (>= (count grandparent) 4)
              (let [then-form (nth grandparent 2 nil)]
                (and (seq? then-form)
                     (= 'subvec (first then-form))))))))

(def rules
  [{:original '+ :mutant '- :category :arithmetic :position :head}
   {:original '- :mutant '+ :category :arithmetic :position :head}
   {:original '* :mutant '/ :category :arithmetic :position :head}
   {:original 'inc :mutant 'dec :category :arithmetic :position :head}
   {:original 'dec :mutant 'inc :category :arithmetic :position :head}
   {:original '> :mutant '>= :category :comparison :position :head :suppress-when [rand-comparison? subvec-trim-boundary?]}
   {:original '>= :mutant '> :category :comparison :position :head :suppress-when [rand-comparison?]}
   {:original '< :mutant '<= :category :comparison :position :head :suppress-when [rand-comparison?]}
   {:original '<= :mutant '< :category :comparison :position :head :suppress-when [rand-comparison?]}
   {:original '= :mutant 'not= :category :equality :position :head :suppress-when [rand-nth-single-element-guard?]}
   {:original 'not= :mutant '= :category :equality :position :head}
   {:original true :mutant false :category :boolean :position :any}
   {:original false :mutant true :category :boolean :position :any}
   {:original 'if :mutant 'if-not :category :conditional :position :head :suppress-when [rand-nth-single-element-guard?]}
   {:original 'if-not :mutant 'if :category :conditional :position :head :suppress-when [rand-nth-single-element-guard?]}
   {:original 'when :mutant 'when-not :category :conditional :position :head}
   {:original 'when-not :mutant 'when :category :conditional :position :head}
   {:original 0 :mutant 1 :category :constant :position :any :suppress-when [rand-nth-single-element-guard? inside-rand-nth-literal?]}
   {:original 1 :mutant 0 :category :constant :position :any :suppress-when [rand-nth-single-element-guard? inside-rand-nth-literal?]}])

(defn matches-rule?
  [rule context node]
  (let [parent (:parent context)]
    (and (= (:original rule) (literals/literal-value node))
         (not (when-let [suppressors (:suppress-when rule)]
                (some #(% context) suppressors)))
         (or (= :any (:position rule))
             (and (= :head (:position rule))
                  (seq? parent)
                  (= node (first parent)))))))

(defn- first-matching-rule
  [context node]
  (first (filter #(matches-rule? % context node) rules)))

(defn- node-line
  [parent loc]
  (or (:row loc)
      (-> parent meta :row)))

(defn- node-column
  [parent loc]
  (or (:col loc)
      (-> parent meta :col)))

(defn- walk-children
  [walk-fn grandparent parent node]
  (cond
    (seq? node) (doseq [child node] (walk-fn parent node child))
    (vector? node) (doseq [child node] (walk-fn parent node child))
    (map? node) (doseq [[k v] node] (walk-fn parent node k) (walk-fn parent node v))
    (set? node) (doseq [child node] (walk-fn parent node child))))

(defn find-mutations
  [form]
  (let [counter (atom 0)
        sites (atom [])]
    (letfn [(walk [grandparent parent node]
              (let [loc (or (meta node)
                            (literals/literal-location node))
                    context {:parent parent
                             :grandparent grandparent}]
                (when-let [rule (first-matching-rule context node)]
                  (swap! sites conj {:index @counter
                                     :original (:original rule)
                                     :mutant (:mutant rule)
                                     :category (:category rule)
                                     :line (node-line parent loc)
                                     :column (node-column parent loc)
                                     :description (str (:original rule) " -> " (:mutant rule))})
                  (swap! counter inc))
                (walk-children walk grandparent parent node)))]
      (walk nil nil form))
    @sites))
