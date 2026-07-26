(ns hyperopen.vaults.detail.types
  (:require [clojure.string :as str]))

(def ^:private vault-benchmark-prefix
  "vault:")

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn normalize-vault-address
  [value]
  (some-> value non-blank-text str/lower-case))

(defn parse-benchmark-id
  [value]
  (let [value* (some-> value non-blank-text)
        value-lower (some-> value* str/lower-case)]
    (cond
      (and (seq value-lower)
           (str/starts-with? value-lower vault-benchmark-prefix))
      (when-let [vault-address (normalize-vault-address (subs value* (count vault-benchmark-prefix)))]
        {:kind :vault
         :address vault-address})

      (seq value*)
      {:kind :market
       :coin value*}

      :else nil)))

(defn benchmark-id->value
  [benchmark-id]
  (when (map? benchmark-id)
    (case (:kind benchmark-id)
      :vault (when-let [vault-address (normalize-vault-address (:address benchmark-id))]
               (str vault-benchmark-prefix vault-address))
      :market (non-blank-text (:coin benchmark-id))
      nil)))

(defn vault-benchmark-value
  [vault-address]
  (benchmark-id->value {:kind :vault
                        :address vault-address}))

(defn vault-benchmark-address
  [value]
  (let [benchmark-id (parse-benchmark-id value)]
    (when (= :vault (:kind benchmark-id))
      (:address benchmark-id))))
