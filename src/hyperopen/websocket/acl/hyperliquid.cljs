(ns hyperopen.websocket.acl.hyperliquid
  (:require [hyperopen.runtime.validation :as validation]
            [hyperopen.websocket.domain.model :as model]))

(def ^:private channel-regex
  #"\"channel\"\s*:\s*\"([^\"]+)\"")

(def ^:private coin-regex
  #"\"coin\"\s*:\s*\"([^\"]+)\"")

(def ^:private symbol-regex
  #"\"symbol\"\s*:\s*\"([^\"]+)\"")

(def ^:private asset-regex
  #"\"asset\"\s*:\s*\"([^\"]+)\"")

(def ^:private deferred-payload-key
  ::deferred-market-payload?)

(def ^:private deferred-raw-key
  ::raw-json)

(defn- extract-first-capture [pattern raw]
  (some-> (re-find pattern raw)
          second))

(defn- extract-channel-fast [raw]
  (extract-first-capture channel-regex raw))

(defn- extract-market-coin-fast [raw]
  (or (extract-first-capture coin-regex raw)
      (extract-first-capture symbol-regex raw)
      (extract-first-capture asset-regex raw)))

(defn- parse-provider-message [raw]
  (js->clj (js/JSON.parse raw) :keywordize-keys true))

(defn deferred-market-payload? [payload]
  (and (map? payload)
       (true? (get payload deferred-payload-key))))

(defn- make-deferred-market-payload [raw topic]
  {:channel topic
   :coin (extract-market-coin-fast raw)
   deferred-payload-key true
   deferred-raw-key raw})

(defn hydrate-envelope
  "Convert deferred market payloads into full keywordized provider maps.
   Non-deferred envelopes are returned unchanged."
  [envelope]
  (let [payload (:payload envelope)]
    (if (deferred-market-payload? payload)
      (try
        (let [provider-message (parse-provider-message (get payload deferred-raw-key))
              provider-message* (if (string? (:channel provider-message))
                                  provider-message
                                  (assoc provider-message :channel (:topic envelope)))]
          (assoc envelope :payload provider-message*))
        (catch :default _
          (assoc envelope
                 :payload (-> payload
                              (dissoc deferred-payload-key deferred-raw-key)
                              (assoc :channel (:topic envelope))))))
      envelope)))

(defn parse-raw-envelope
  "Anti-corruption layer: map provider websocket raw payload into domain envelope.
   Returns {:ok envelope} or {:error ex}."
  [{:keys [raw socket-id now-ms topic->tier source]}]
  (try
    (let [topic-fast (extract-channel-fast raw)
          validation-enabled? (validation/validation-enabled?)
          tier-fast (when (string? topic-fast)
                      (topic->tier topic-fast))]
      (if (and (string? topic-fast)
               (not validation-enabled?)
               (= :market tier-fast))
        (let [envelope (model/make-domain-message-envelope
                         {:topic topic-fast
                          :tier tier-fast
                          :ts (now-ms)
                          :payload (make-deferred-market-payload raw topic-fast)
                          :source (or source :hyperliquid/ws)
                          :socket-id socket-id})]
          {:ok envelope})
        (let [provider-message (parse-provider-message raw)
              _ (when validation-enabled?
                  (validation/assert-provider-message!
                   provider-message
                   {:boundary :ws-acl/parse-raw-envelope}))
              topic (:channel provider-message)]
          (if (string? topic)
            (let [tier (topic->tier topic)
                  envelope (model/make-domain-message-envelope
                             {:topic topic
                              :tier tier
                              :ts (now-ms)
                              :payload provider-message
                              :source (or source :hyperliquid/ws)
                              :socket-id socket-id})]
              {:ok envelope})
            {:error (js/Error. "Provider payload missing :channel")}))))
    (catch :default e
      {:error e})))
