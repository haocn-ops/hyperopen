(ns hyperopen.schema.order-form-ownership-contracts
  (:require [hyperopen.state.trading.order-form-key-policy :as key-policy]
            [hyperopen.state.trading :as trading]
            [hyperopen.trading.order-form-ownership :as ownership]))

(def ^:private transition-result-keys
  #{:keys
    :has-order-form?
    :has-order-form-ui?
    :has-order-form-runtime?
    :runtime-submitting?
    :runtime-error})

(def ^:private persisted-form-projection-keys
  #{:keys
    :ui-owned-keys
    :legacy-keys
    :deprecated-keys})

(def ^:private effective-ui-projection-keys
  (conj (set (keys (trading/default-order-form-ui)))
        :type))

(def ^:private order-form-ui-state-projection-keys
  (conj effective-ui-projection-keys :cross-margin-allowed?))

(defn- exact-keys?
  [value expected]
  (and (map? value)
       (= expected (set (keys value)))))

(defn- vector-of?
  [predicate value]
  (and (vector? value)
       (every? predicate value)))

(defn- keyword-vector?
  [value]
  (vector-of? keyword? value))

(defn persisted-form-projection
  [form]
  (let [keys* (vec (sort (keys form)))]
    {:keys keys*
     :ui-owned-keys (vec (filter key-policy/ui-owned-order-form-key? keys*))
     :legacy-keys (vec (filter key-policy/legacy-order-form-compatibility-key? keys*))
     :deprecated-keys (vec (filter key-policy/deprecated-canonical-order-form-key? keys*))}))

(defn transition-projection
  [transition]
  {:keys (vec (sort (keys transition)))
   :has-order-form? (map? (:order-form transition))
   :has-order-form-ui? (map? (:order-form-ui transition))
   :has-order-form-runtime? (map? (:order-form-runtime transition))
   :runtime-submitting? (boolean (get-in transition [:order-form-runtime :submitting?]))
   :runtime-error (get-in transition [:order-form-runtime :error])})

(defn effective-ui-projection
  [form ui]
  (assoc (ownership/effective-order-form-ui form ui)
         :type (:type form)))

(defn order-form-ui-state-projection
  [state]
  (assoc (ownership/order-form-ui-state state)
         :type (:type (trading/order-form-draft state))
         :cross-margin-allowed? (trading/cross-margin-allowed? state)))

(defn persisted-form-projection-valid?
  [projection]
  (and (exact-keys? projection persisted-form-projection-keys)
       (keyword-vector? (:keys projection))
       (keyword-vector? (:ui-owned-keys projection))
       (keyword-vector? (:legacy-keys projection))
       (keyword-vector? (:deprecated-keys projection))
       (empty? (:ui-owned-keys projection))
       (empty? (:legacy-keys projection))
       (empty? (:deprecated-keys projection))))

(defn transition-projection-valid?
  [projection]
  (and (exact-keys? projection transition-result-keys)
       (keyword-vector? (:keys projection))
       (boolean? (:has-order-form? projection))
       (boolean? (:has-order-form-ui? projection))
       (boolean? (:has-order-form-runtime? projection))
       (boolean? (:runtime-submitting? projection))
       (or (nil? (:runtime-error projection))
           (string? (:runtime-error projection)))))

(defn effective-ui-projection-valid?
  [projection]
  (let [order-type (:type projection)]
    (and (exact-keys? projection effective-ui-projection-keys)
         (contains? #{:market :limit :pro :stop-market :stop-limit :take-market :take-limit :scale :twap}
                    order-type)
         (= (trading/entry-mode-for-type order-type)
            (:entry-mode projection))
         (boolean? (:pro-order-type-dropdown-open? projection))
         (boolean? (:margin-mode-dropdown-open? projection))
         (boolean? (:leverage-popover-open? projection))
         (boolean? (:size-unit-dropdown-open? projection))
         (boolean? (:tpsl-unit-dropdown-open? projection))
         (boolean? (:tif-dropdown-open? projection))
         (boolean? (:tpsl-panel-open? projection))
         (boolean? (:price-input-focused? projection))
         (contains? #{:market :limit :pro} (:entry-mode projection))
         (pos? (:ui-leverage projection))
         (pos? (:leverage-draft projection))
         (contains? #{:cross :isolated} (:margin-mode projection))
         (contains? #{:quote :base} (:size-input-mode projection))
         (contains? #{:manual :percent} (:size-input-source projection))
         (string? (:size-display projection))
         (if (trading/limit-like-type? order-type)
           true
           (and (false? (:price-input-focused? projection))
                (false? (:tif-dropdown-open? projection))))
         (if (= :scale order-type)
           (and (false? (:tpsl-panel-open? projection))
                (false? (:tpsl-unit-dropdown-open? projection)))
           true)
         (if (false? (:tpsl-panel-open? projection))
           (false? (:tpsl-unit-dropdown-open? projection))
           true))))

(defn order-form-ui-state-projection-valid?
  [projection]
  (and (exact-keys? projection order-form-ui-state-projection-keys)
       (boolean? (:cross-margin-allowed? projection))
       (effective-ui-projection-valid? (dissoc projection :cross-margin-allowed?))
       (if (false? (:cross-margin-allowed? projection))
         (and (= :isolated (:margin-mode projection))
              (false? (:margin-mode-dropdown-open? projection)))
         true)))

(defn assert-persisted-form-projection!
  [projection context]
  (when-not (persisted-form-projection-valid? projection)
    (throw (js/Error.
            (str "order-form ownership persisted-form projection contract validation failed. "
                 "context=" (pr-str context)
                 " projection=" (pr-str projection)))))
  projection)

(defn assert-transition-projection!
  [projection context]
  (when-not (transition-projection-valid? projection)
    (throw (js/Error.
            (str "order-form ownership transition projection contract validation failed. "
                 "context=" (pr-str context)
                 " projection=" (pr-str projection)))))
  projection)

(defn assert-effective-ui-projection!
  [projection context]
  (when-not (effective-ui-projection-valid? projection)
    (throw (js/Error.
            (str "order-form ownership effective-ui projection contract validation failed. "
                 "context=" (pr-str context)
                 " projection=" (pr-str projection)))))
  projection)

(defn assert-order-form-ui-state-projection!
  [projection context]
  (when-not (order-form-ui-state-projection-valid? projection)
    (throw (js/Error.
            (str "order-form ownership ui-state projection contract validation failed. "
                 "context=" (pr-str context)
                 " projection=" (pr-str projection)))))
  projection)
