(ns hyperopen.core-bootstrap.test-support.browser-mocks
  (:require [hyperopen.platform.indexed-db :as indexed-db]))

(defn- dissoc-in
  [m [k & ks]]
  (if k
    (if ks
      (let [child (dissoc-in (get m k) ks)]
        (if (seq child)
          (assoc m k child)
          (dissoc m k)))
      (dissoc m k))
    m))

(defn with-test-local-storage [f]
  (let [original-local-storage (.-localStorage js/globalThis)
        storage (atom {})]
    (set! (.-localStorage js/globalThis)
          #js {:setItem (fn [key value]
                          (swap! storage assoc (str key) (str value)))
               :getItem (fn [key]
                          (get @storage (str key)))
               :removeItem (fn [key]
                             (swap! storage dissoc (str key)))
               :clear (fn []
                        (reset! storage {}))})
    (let [restore! (fn []
                     (set! (.-localStorage js/globalThis) original-local-storage))]
      (try
        (let [result (f)]
          (if (instance? js/Promise result)
            (.finally result restore!)
            (do
              (restore!)
              result)))
        (catch :default e
          (restore!)
          (throw e))))))

(defn with-test-indexed-db [f]
  (let [original-indexed-db (.-indexedDB js/globalThis)
        databases (atom {})]
    (letfn [(request-success!
              [request result]
              (set! (.-result request) result)
              (js/setTimeout
               (fn []
                 (when-let [handler (.-onsuccess request)]
                   (handler #js {:target #js {:result result}})))
               0))
            (make-object-store-names
              [db-name]
              #js {:contains (fn [store-name]
                               (contains? (set (keys (get-in @databases [db-name :stores] {})))
                                          store-name))})
            (make-store
              [db-name store-name]
              #js {:get (fn [key]
                          (let [request #js {}]
                            (request-success! request (get-in @databases [db-name :stores store-name key]))
                            request))
                   :getAll (fn []
                             (let [request #js {}
                                   values (->> (get-in @databases [db-name :stores store-name] {})
                                               vals
                                               vec
                                               clj->js)]
                               (request-success! request values)
                               request))
                   :put (fn [value key]
                          (let [request #js {}]
                            (swap! databases assoc-in [db-name :stores store-name key] value)
                            (request-success! request key)
                            request))
                   :delete (fn [key]
                             (let [request #js {}]
                               (swap! databases dissoc-in [db-name :stores store-name key])
                               (request-success! request nil)
                               request))})
            (make-transaction
              [db-name]
              #js {:objectStore (fn [store-name]
                                  (make-store db-name store-name))})
            (make-db
              [db-name]
              (let [db #js {}]
                (aset db "close" (fn [] nil))
                (aset db "createObjectStore" (fn [store-name]
                                               (swap! databases update-in [db-name :stores]
                                                      (fnil #(if (contains? % store-name)
                                                               %
                                                               (assoc % store-name {}))
                                                            {}))
                                               (make-store db-name store-name)))
                (aset db "transaction" (fn [_store-names _mode]
                                         (make-transaction db-name)))
                (aset db "objectStoreNames" (make-object-store-names db-name))
                db))
            (open-db
              [db-name version]
              (let [request #js {}]
                (js/setTimeout
                 (fn []
                   (let [existing (get @databases db-name {:version 0 :stores {}})
                         db (make-db db-name)
                         requested-version (or version 1)
                         upgrade? (> requested-version (:version existing))]
                     (swap! databases update db-name
                            (fn [current]
                              (-> (or current {:version 0 :stores {}})
                                  (assoc :version requested-version)
                                  (update :stores #(or % {})))))
                     (when upgrade?
                       (set! (.-result request) db)
                       (when-let [handler (.-onupgradeneeded request)]
                         (handler #js {:target #js {:result db}})))
                     (request-success! request db)))
                 0)
                request))]
      (set! (.-indexedDB js/globalThis)
            #js {:open open-db})
      (indexed-db/clear-open-db-cache!)
      (let [restore! (fn []
                       (indexed-db/clear-open-db-cache!)
                       (set! (.-indexedDB js/globalThis) original-indexed-db))]
        (try
          (let [result (f)]
            (if (instance? js/Promise result)
              (.finally result restore!)
              (do
                (restore!)
                result)))
          (catch :default e
            (restore!)
            (throw e)))))))

(defn with-test-navigator [navigator-value f]
  (let [navigator-prop "navigator"
        original-navigator-descriptor (js/Object.getOwnPropertyDescriptor js/globalThis navigator-prop)]
    (js/Object.defineProperty js/globalThis navigator-prop
                              #js {:value navigator-value
                                   :configurable true})
    (let [restore! (fn []
                     (if original-navigator-descriptor
                       (js/Object.defineProperty js/globalThis navigator-prop original-navigator-descriptor)
                       (js/Reflect.deleteProperty js/globalThis navigator-prop)))]
      (try
        (let [result (f)]
          (if (instance? js/Promise result)
            (.finally result restore!)
            (do
              (restore!)
              result)))
        (catch :default e
          (restore!)
          (throw e))))))
