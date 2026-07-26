(ns hyperopen.funding.application.lifecycle-guards
  (:require [clojure.string :as str]))

(defn lifecycle-poll-key
  [store direction asset-key]
  [store direction asset-key])

(defn install-lifecycle-poll-token!
  [tokens-atom poll-key token]
  (swap! tokens-atom assoc poll-key token))

(defn clear-lifecycle-poll-token!
  [tokens-atom poll-key token]
  (swap! tokens-atom
         (fn [tokens]
           (if (= token (get tokens poll-key))
             (dissoc tokens poll-key)
             tokens))))

(defn lifecycle-poll-token-active?
  [tokens-atom poll-key token]
  (= token (get @tokens-atom poll-key)))

(defn- canonical-token
  [value]
  (some-> value str str/trim str/lower-case))

(defn modal-active-for-lifecycle?
  [store direction asset-key protocol-address]
  (let [modal (get-in @store [:funding-ui :modal])
        lifecycle (:hyperunit-lifecycle modal)
        mode (:mode modal)
        selected-asset-key (case direction
                             :deposit (:deposit-selected-asset-key modal)
                             :withdraw (:withdraw-selected-asset-key modal)
                             nil)
        generated-address (case direction
                            :deposit (:deposit-generated-address modal)
                            :withdraw (:withdraw-generated-address modal)
                            nil)]
    (and (true? (:open? modal))
         (= mode direction)
         (or (not= direction :deposit)
             (= :amount-entry (:deposit-step modal)))
         (= asset-key selected-asset-key)
         (= direction (:direction lifecycle))
         (= asset-key (:asset-key lifecycle))
         (or (not (seq (canonical-token protocol-address)))
             (= (canonical-token protocol-address)
                (canonical-token generated-address))))))

(defn modal-active-for-fee-estimate?
  [store]
  (let [modal (get-in @store [:funding-ui :modal])]
    (and (true? (:open? modal))
         (contains? #{:deposit :withdraw} (:mode modal)))))

(defn- normalize-modal-asset-key
  [value]
  (cond
    (keyword? value) value
    (string? value) (some-> value str/trim str/lower-case keyword)
    :else nil))

(defn modal-active-for-withdraw-queue?
  ([store]
   (modal-active-for-withdraw-queue? store nil))
  ([store expected-asset-key]
   (let [modal (get-in @store [:funding-ui :modal])
         selected-asset-key (normalize-modal-asset-key
                             (:withdraw-selected-asset-key modal))
         expected-asset-key* (normalize-modal-asset-key expected-asset-key)]
     (and (true? (:open? modal))
          (= :withdraw (:mode modal))
          (keyword? selected-asset-key)
          (not= :usdc selected-asset-key)
          (or (nil? expected-asset-key*)
              (= expected-asset-key* selected-asset-key))))))
