(ns hyperopen.runtime.registry-composition
  (:require [hyperopen.schema.runtime-registration-catalog :as runtime-registration-catalog]))

(defn- merge-handler-leaf
  [acc handler-key handler-value handler-path]
  (if (contains? acc handler-key)
    (throw (js/Error.
            (str "Duplicate runtime handler key in dependency graph: "
                 handler-key
                 " paths="
                 (pr-str [(get-in acc [handler-key :path]) handler-path]))))
    (assoc acc handler-key {:value handler-value
                            :path handler-path})))

(defn- collect-handler-leaves
  [node path acc]
  (reduce-kv (fn [acc k v]
               (let [next-path (conj path k)]
                 (if (map? v)
                   (collect-handler-leaves v next-path acc)
                   (merge-handler-leaf acc k v next-path))))
             acc
             (or node {})))

(defn- flatten-handler-deps
  [deps]
  (->> (collect-handler-leaves deps [] {})
       (reduce-kv (fn [acc handler-key {:keys [value]}]
                    (assoc acc handler-key value))
                  {})))

(defn- build-runtime-handlers
  [handler-keys deps]
  (let [available (flatten-handler-deps deps)]
    (reduce (fn [acc handler-key]
              (assoc acc handler-key (get available handler-key)))
            {}
            handler-keys)))

(defn runtime-effect-handlers
  [effect-deps]
  (build-runtime-handlers
   (runtime-registration-catalog/effect-handler-keys)
   effect-deps))

(defn runtime-action-handlers
  [action-deps]
  (build-runtime-handlers
   (runtime-registration-catalog/action-handler-keys)
   action-deps))

(defn runtime-registration-deps
  [{:keys [register-effects!
           register-actions!
           register-system-state!
           register-placeholders!
           register-interceptors!]}
   {:keys [effect-deps action-deps]}]
  (cond-> {:register-effects! register-effects!
           :effect-handlers (runtime-effect-handlers effect-deps)
           :register-actions! register-actions!
           :action-handlers (runtime-action-handlers action-deps)
           :register-system-state! register-system-state!
           :register-placeholders! register-placeholders!}
    register-interceptors! (assoc :register-interceptors! register-interceptors!)))
