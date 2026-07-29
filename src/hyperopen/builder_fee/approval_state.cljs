(ns hyperopen.builder-fee.approval-state)

(defn- same-identity?
  [left right]
  (and (= (:owner-address left) (:owner-address right))
       (= (:builder-address left) (:builder-address right))
       (= (:network left) (:network right))))

(defn- valid-max-builder-fee?
  [value]
  (and (number? value)
       (js/isFinite value)
       (not (js/isNaN value))
       (integer? value)
       (not (neg? value))))

(defn- unapproved
  []
  {:status :unapproved})

(defn begin-refresh
  [{:keys [owner-address builder-address network]} request-id]
  {:status :loading
   :request-id request-id
   :owner-address owner-address
   :builder-address builder-address
   :network network})

(defn apply-refresh-response
  [pending identity request-id max-builder-fee]
  (if (and (= :loading (:status pending))
           (= request-id (:request-id pending))
           (same-identity? pending identity)
           (valid-max-builder-fee? max-builder-fee))
    (assoc pending :status :ready :max-builder-fee max-builder-fee)
    (unapproved)))

(defn apply-refresh-error
  [_pending _identity _request-id _error]
  (unapproved))
