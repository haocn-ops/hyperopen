(ns hyperopen.funding.domain.legal-check
  (:require [clojure.string :as str]))

(def allowed-restrictions
  #{"n" "o"})

(def block-actions-restriction "a")
(def uk-restriction "u")

(def jurisdiction-blocked-message
  "Withdrawals and deposits are unavailable in your current jurisdiction.")
(def terms-required-message
  "Accept Hyperliquid's terms before depositing or withdrawing.")
(def user-not-allowed-message
  "This wallet is not allowed to deposit or withdraw.")
(def unavailable-message
  "Unable to verify Hyperliquid access restrictions. Try again.")

(defn default-legal-check-state
  []
  {:status :idle
   :accepted-terms nil
   :user-allowed nil
   :restrictions nil
   :message nil
   :checked-at-ms nil})

(defn- boolean-value
  [value]
  (when (boolean? value)
    value))

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim str/lower-case)]
    (when (seq text)
      text)))

(defn- response-map
  [response]
  (let [response* (if (map? response) response {})]
    (if (map? (:data response*))
      (:data response*)
      response*)))

(defn normalize-response
  "Normalizes the camelCase response returned by Hyperliquid /info."
  [response]
  (let [response* (response-map response)]
    {:accepted-terms (boolean-value (if (contains? response* :accepted-terms)
                                      (:accepted-terms response*)
                                      (:acceptedTerms response*)))
     :user-allowed (boolean-value (if (contains? response* :user-allowed)
                                    (:user-allowed response*)
                                    (:userAllowed response*)))
     :restrictions (non-blank-text (:restrictions response*))}))

(defn normalize-state
  [value]
  (let [state (if (map? value) value {})
        status (cond
                 (keyword? (:status state)) (:status state)
                 (string? (:status state)) (some-> (:status state) str/lower-case keyword)
                 :else :idle)]
    (merge (default-legal-check-state)
           {:status (if (contains? #{:idle :loading :allowed :blocked :error} status)
                      status
                      :idle)
            :accepted-terms (boolean-value (:accepted-terms state))
            :user-allowed (boolean-value (:user-allowed state))
            :restrictions (non-blank-text (:restrictions state))
            :message (some-> (:message state) str str/trim not-empty)
            :checked-at-ms (:checked-at-ms state)})))

(defn assess
  "Returns a fail-closed legal decision for funding mutations."
  [response]
  (let [{:keys [accepted-terms user-allowed restrictions] :as normalized}
        (normalize-response response)]
    (cond
      (not (map? response))
      (assoc normalized :status :error :message unavailable-message)

      (nil? accepted-terms)
      (assoc normalized :status :error :message unavailable-message)

      (false? accepted-terms)
      (assoc normalized :status :blocked :message terms-required-message)

      (nil? user-allowed)
      (assoc normalized :status :error :message unavailable-message)

      (false? user-allowed)
      (assoc normalized :status :blocked :message user-not-allowed-message)

      (nil? restrictions)
      (assoc normalized :status :error :message unavailable-message)

      (contains? #{block-actions-restriction uk-restriction} restrictions)
      (assoc normalized :status :blocked :message jurisdiction-blocked-message)

      (contains? allowed-restrictions restrictions)
      (assoc normalized :status :allowed :message nil)

      :else
      (assoc normalized :status :error :message unavailable-message))))

(defn allowed?
  [legal-check]
  (if (and (map? legal-check)
           (contains? legal-check :status))
    (= :allowed (:status legal-check))
    (= :allowed (:status (assess legal-check)))))
