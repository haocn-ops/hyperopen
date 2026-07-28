(ns hyperopen.service.attribution
  "Pure, redacted attribution contracts. Provider settlement is never inferred locally."
  (:require [clojure.string :as str]
            [goog.crypt :as crypt]
            [goog.crypt.Sha256]
            [hyperopen.service.tenant-config :as tenant-config]))

(def ^:private secret-key-pattern
  #"(?i)(private[-_ ]?key|seed[-_ ]?phrase|api[-_ ]?secret|access[-_ ]?token|raw[-_ ]?signature|mnemonic)")
(def ^:private secret-value-pattern
  #"(?i)(sk_(?:live|test)_[A-Za-z0-9_-]+|0x[0-9a-f]{32,}|signed[-_ ]?secret)")
(def ^:private outcome-values #{:observed :submitted :accepted :rejected :unavailable :unknown :settled})
(def ^:private event-types #{:tenant-loaded :affiliate-attribution-seen :wallet-connected
                            :trade-submit-requested :trade-submit-result :analytics-viewed})
(def ^:private event-fields
  #{:event/id :event/type :tenant/id :affiliate/id :venue/id :session/id
    :wallet/address-hash :occurred-at-ms :market :range :outcome :provider-event-id
    :rebate-amount :settled-at-ms})

(defn- finite-number?
  [value]
  (and (number? value) (js/isFinite value)))

(defn- public-string?
  [value]
  (and (string? value)
       (seq (str/trim value))
       (not (re-find secret-key-pattern value))
       (not (re-find secret-value-pattern value))))

(defn- key-name
  [key]
  (if (keyword? key) (name key) (str key)))

(defn- canonical-key-sort-key
  [key]
  (cond
    (keyword? key) [0 (or (namespace key) "") (name key)]
    (string? key) [1 "" key]
    :else [2 "" (str key)]))

(defn contains-secret?
  [value]
  (cond
    (map? value)
    (or (some #(re-find secret-key-pattern (key-name %)) (keys value))
        (some contains-secret? (vals value)))
    (sequential? value) (boolean (some contains-secret? value))
    (string? value) (boolean (re-find secret-value-pattern value))
    :else false))

(defn canonical-serialize
  "Insertion-order independent serialization shared by event ids and dedupe keys." 
  [value]
  (letfn [(canonical [item]
            (cond
              (map? item)
              (->> item
                   (map (fn [[key val]]
                          [key (canonical val)]))
                   (sort-by (fn [[key _]] (canonical-key-sort-key key)))
                   vec)
              (set? item) (vec (sort-by str (map canonical item)))
              (sequential? item) (mapv canonical item)
              :else item))]
    (pr-str (canonical value))))

(defn sha256-hex
  "Synchronous SHA-256 over UTF-8 text for stable pseudonymous identifiers."
  [text]
  (let [digest (goog.crypt.Sha256.)]
    (.update digest (crypt/stringToUtf8ByteArray (str (or text ""))))
    (str/lower-case (crypt/byteArrayToHex (.digest digest)))))

(defn- digest-string
  [text]
  (str "sha256-" (sha256-hex text)))

(defn- public-wallet-hash
  [address]
  (when (and (string? address) (seq (str/trim address)))
    (digest-string (str/lower-case (str/trim address)))))

(defn- valid-context?
  [context]
  (and (map? context)
       (public-string? (:tenant/id context))
       (some? (:venue/id context))
       (public-string? (:wallet/address-hash context))
       (finite-number? (:occurred-at-ms context))))

(defn build-attribution-context
  [tenant context]
  (let [tenant* (tenant-config/normalize-tenant-config tenant)
        context* (if (map? context) context {})]
    (let [result {:tenant/id (:tenant/id tenant*)
                  :affiliate/id (get-in tenant* [:affiliate :id])
                  :venue/id (get-in tenant* [:venue :id])
                  :session/id (:session/id context*)
                  :wallet/address-hash (public-wallet-hash (:wallet/address context*))
                  :occurred-at-ms (:occurred-at-ms context*)}]
      (when (valid-context? result) result))))

(defn- complete-settlement-evidence?
  [event]
  (let [evidence (:provider/evidence event)
        marker? (true? (:settlement/verified? event))
        verification-id (or (:verification-id evidence)
                            (:verification/id evidence))
        response-proof (or (:response-digest evidence)
                           (:adapter/provenance evidence))
        subject-match? (and (or (nil? (:tenant/id evidence))
                                (= (:tenant/id evidence) (:tenant/id event)))
                            (or (nil? (:affiliate/id evidence))
                                (= (:affiliate/id evidence) (:affiliate/id event)))
                            (or (nil? (:venue/id evidence))
                                (= (:venue/id evidence) (:venue/id event))))]
    (and (map? evidence)
         (or (true? (:verified? evidence))
             (= :verified (:verification/status evidence))
             (= :verified (:verification/status event)))
         (public-string? verification-id)
         marker?
         (public-string? response-proof)
         subject-match?
         (public-string? (:provider-event-id event))
         (finite-number? (:occurred-at-ms event))
         (finite-number? (:settled-at-ms event))
         (>= (:settled-at-ms event) (:occurred-at-ms event))
         (public-string? (:tenant/id event))
         (public-string? (:affiliate/id event))
         (some? (:venue/id event))
         (or (nil? (:rebate-amount event))
             (finite-number? (:rebate-amount event))))))

(defn- safe-event-fields
  [event]
  (reduce-kv
   (fn [safe key value]
     (cond
       (contains? #{:event/id :tenant/id :affiliate/id :session/id :wallet/address-hash
                    :market :provider-event-id} key)
       (if (public-string? value) (assoc safe key value) safe)

       (= :venue/id key)
       (if (or (keyword? value) (public-string? value)) (assoc safe key value) safe)

       (= :event/type key)
       (assoc safe key (if (contains? event-types value) value :unknown))

       (= :outcome key)
       (assoc safe key (if (contains? outcome-values value) value :unknown))

       (= :range key)
       (if (or (keyword? value) (public-string? value)) (assoc safe key value) safe)

       (contains? #{:occurred-at-ms :settled-at-ms :rebate-amount} key)
       (if (finite-number? value) (assoc safe key value) safe)

       :else safe))
   {}
   event))

(defn redact-attribution-event
  [event]
  (let [event* (if (map? event) event {})
        settled? (and (= :settled (:outcome event*))
                      (complete-settlement-evidence? event*))]
    (-> (apply dissoc event*
               (cond-> [:wallet/address :private-key :seed-phrase :api-secret :access-token
                        :raw-signature :authenticated?]
                 (not settled?) (conj :rebate-amount :settled-at-ms)))
        (cond-> (not settled?)
          (assoc :outcome (if (= :settled (:outcome event*)) :unknown (:outcome event*))))
        (select-keys event-fields)
        safe-event-fields)))

(defn- normalize-outcome
  [value]
  (if (and (keyword? value) (contains? outcome-values value))
    value
    :unknown))

(defn- normalize-provider-outcome
  [value]
  (let [normalized (normalize-outcome value)]
    (if (= :settled normalized) :unknown normalized)))

(defn build-attribution-event
  [context event-type attrs]
  (let [context-raw (if (map? context) context {})
        attrs* (if (map? attrs) attrs {})
        context* (cond-> context-raw
                   (contains? context-raw :wallet/address)
                   (assoc :wallet/address-hash (public-wallet-hash (:wallet/address context-raw)))
                   (contains? context-raw :wallet/address) (dissoc :wallet/address))
        authoritative (merge (select-keys context* [:tenant/id :affiliate/id :venue/id :session/id
                                                    :wallet/address-hash :occurred-at-ms])
                             {:event/type (if (contains? event-types event-type) event-type :unknown)})
        attrs-safe (cond-> {}
                     (public-string? (:market attrs*)) (assoc :market (:market attrs*))
                     (or (keyword? (:range attrs*)) (public-string? (:range attrs*)))
                     (assoc :range (:range attrs*))
                     (public-string? (:provider-event-id attrs*))
                     (assoc :provider-event-id (:provider-event-id attrs*)))
        base (merge authoritative attrs-safe
                     {:outcome (normalize-outcome (:outcome attrs*))})
        safe (redact-attribution-event base)]
    (if (valid-context? context*)
      (assoc safe :event/id (digest-string (canonical-serialize (dissoc safe :event/id))))
      (assoc safe :outcome :unknown))))

(defn idempotency-key
  [event]
  (digest-string (canonical-serialize (dissoc (or event {}) :event/id))))

(defn normalize-provider-result
  [context result]
  (let [result* (if (map? result) result {})
        occurred-at-ms (:occurred-at-ms result*)
        settled-at-ms (:settled-at-ms result*)
        evidence (:provider/evidence result*)
        verification-id (or (:verification/id result*)
                            (get-in result* [:verification :id])
                            (:verification-id evidence))
        response-digest (or (:verification/response-digest result*)
                            (:response-digest evidence)
                            (:response-digest result*))
        adapter-provenance (or (:adapter/provenance result*)
                               (:adapter/provenance evidence)
                               (:adapter-provenance evidence))
        verified? (and (or (= :verified (:verification/status result*))
                          (and (map? evidence) (true? (:verified? evidence))))
                       (public-string? verification-id)
                       (or (public-string? response-digest)
                           (public-string? adapter-provenance)))
        authenticated? (and (= :settled (:outcome result*))
                            (string? (:provider-event-id result*))
                            (seq (:provider-event-id result*))
                            (finite-number? occurred-at-ms)
                            (finite-number? settled-at-ms)
                            (>= settled-at-ms occurred-at-ms)
                            (string? (:tenant/id context))
                            (string? (:affiliate/id context))
                            (some? (:venue/id context))
                            (= (:tenant/id context) (:tenant/id result*))
                            (= (:affiliate/id context) (:affiliate/id result*))
                            (= (:venue/id context) (:venue/id result*))
                            verified?
                            (or (nil? (:rebate-amount result*))
                                (finite-number? (:rebate-amount result*))))]
    (if authenticated?
      (merge (select-keys context [:tenant/id :affiliate/id :venue/id :session/id :wallet/address-hash])
             (select-keys result* [:provider-event-id :occurred-at-ms :rebate-amount :settled-at-ms
                                   :provider/evidence])
             {:outcome :settled
              :provider/evidence (merge {:verified? true
                                         :verification-id verification-id}
                                        (when (public-string? response-digest)
                                          {:response-digest response-digest})
                                        (when (public-string? adapter-provenance)
                                          {:adapter/provenance adapter-provenance}))
              :settlement/verified? true})
      {:tenant/id (:tenant/id context)
       :affiliate/id (:affiliate/id context)
       :venue/id (:venue/id context)
       :outcome (normalize-provider-outcome (:outcome result*))})))

(defn normalize-send-result
  [result]
  (let [status-raw (:status (if (map? result) result {}))
        status (if (keyword? status-raw)
                 status-raw
                 (some-> status-raw str keyword))]
    {:outcome (case status
                :accepted :accepted
                :submitted :submitted
                :rejected :rejected
                :network-error :unavailable
                :timeout :unavailable
                :unavailable :unavailable
                :unknown)}))
