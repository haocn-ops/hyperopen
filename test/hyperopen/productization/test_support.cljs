(ns hyperopen.productization.test-support)

(def secret-key-pattern
  #"(?i)(private[-_ ]?key|seed[-_ ]?phrase|api[-_ ]?secret|access[-_ ]?token|raw[-_ ]?signature)")

(defn contains-secret-shaped-value?
  [value]
  (cond
    (map? value)
    (or (some #(and (re-find secret-key-pattern (name %)) true) (keys value))
        (some contains-secret-shaped-value? (vals value)))

    (sequential? value)
    (some contains-secret-shaped-value? value)

    :else false))

(defn merge-optional-attribution-result
  [order-result attribution-result]
  (if (= :unavailable (:outcome attribution-result))
    order-result
    (assoc order-result :attribution/status (:outcome attribution-result))))
