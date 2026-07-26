(ns hyperopen.service.tenant-build-config-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.config :as app-config]
            [hyperopen.service.fixtures :as fixtures]
            [hyperopen.service.tenant-config :as tenant-config]))

(def alternate-build-config
  (-> fixtures/alternate-tenant-raw
      (update :features dissoc :advanced-orders)
      (assoc-in [:affiliate :status] :configured)))

(defn- json-encode
  [value]
  (letfn [(json-key [key]
            (if (keyword? key)
              (if-let [key-namespace (namespace key)]
                (str key-namespace "/" (name key))
                (name key))
              (str key)))
          (json-value [item]
            (cond
              (map? item)
              (reduce-kv (fn [object key val]
                           (aset object (json-key key) (json-value val))
                           object)
                         #js {}
                         item)

              (sequential? item)
              (clj->js (mapv json-value item))

              (keyword? item)
              (name item)

              :else item))]
    (js/JSON.stringify (json-value value))))

(deftest build-time-tenant-override-normalizes-the-alternate-tenant-test
  (let [resolved (app-config/parse-tenant-config-json
                  (json-encode alternate-build-config))]
    (is (= "desk-alpha" (:tenant/id resolved)))
    (is (= "Desk Alpha" (:brand/name resolved)))
    (is (= "institutional" (:theme/id resolved)))
    (is (= :configured (get-in resolved [:affiliate :status])))))

(deftest missing-or-invalid-build-time-tenant-override-falls-back-to-default-test
  (let [default (tenant-config/normalize-tenant-config tenant-config/default-tenant-raw)
        malformed (assoc fixtures/malformed-tenant-raw :tenant/id "invalid-build")]
    (is (nil? (app-config/parse-tenant-config-json "")))
    (is (nil? (app-config/parse-tenant-config-json (json-encode malformed))))
    (is (nil? (app-config/parse-tenant-config-json "not-json-or-a-map")))
    (is (= default (tenant-config/normalize-tenant-config nil)))))
