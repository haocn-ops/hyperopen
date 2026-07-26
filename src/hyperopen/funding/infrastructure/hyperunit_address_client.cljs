(ns hyperopen.funding.infrastructure.hyperunit-address-client
  (:require [clojure.string :as str]))

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn- hyperunit-gen-url
  [base-url source-chain destination-chain asset destination-address]
  (let [encode js/encodeURIComponent]
    (str base-url
         "/gen/"
         (encode source-chain)
         "/"
         (encode destination-chain)
         "/"
         (encode asset)
         "/"
         (encode destination-address))))

(defn- hyperunit-error-message
  [payload]
  (or (non-blank-text (:error payload))
      (non-blank-text (:message payload))))

(defn fetch-hyperunit-address!
  [base-url source-chain destination-chain asset destination-address]
  (let [url (hyperunit-gen-url base-url
                               source-chain
                               destination-chain
                               asset
                               destination-address)]
    (-> (js/fetch url #js {:method "GET"})
        (.then (fn [resp]
                 (if (.-ok resp)
                   (.json resp)
                   (.then (.text resp)
                          (fn [text]
                            (throw (js/Error.
                                    (str "HyperUnit address request failed ("
                                         (.-status resp)
                                         "): "
                                         (or (non-blank-text text) "Unknown response")))))))))
        (.then (fn [payload]
                 (let [payload* (js->clj payload :keywordize-keys true)
                       address (non-blank-text (:address payload*))
                       error-message (hyperunit-error-message payload*)
                       status (some-> (:status payload*) str str/trim)]
                   (cond
                     (seq address)
                     {:address address
                      :status status
                      :signatures (:signatures payload*)}

                     (seq error-message)
                     (throw (js/Error. error-message))

                     :else
                     (throw (js/Error. "HyperUnit address response missing deposit address.")))))))))

(defn fetch-hyperunit-address-with-source-fallbacks!
  [{:keys [base-url
           base-urls
           source-chain
           destination-chain
           asset
           destination-address
           with-base-url-fallbacks!
           source-chain-candidates
           canonical-chain-token
           canonical-token]}]
  (let [source-candidates (or (seq source-chain-candidates)
                              [(or (canonical-chain-token source-chain)
                                   (canonical-token source-chain)
                                   source-chain)])
        attempt-for-base-url! (fn [candidate-base-url]
                                (letfn [(attempt-source! [remaining last-error]
                                          (if-let [candidate-source (first remaining)]
                                            (-> (fetch-hyperunit-address! candidate-base-url
                                                                          candidate-source
                                                                          destination-chain
                                                                          asset
                                                                          destination-address)
                                                (.catch (fn [err]
                                                          (attempt-source! (rest remaining)
                                                                           (or err last-error)))))
                                            (js/Promise.reject
                                             (or last-error
                                                 (js/Error. "Unable to generate HyperUnit deposit address.")))))]
                                  (attempt-source! source-candidates nil)))]
    (with-base-url-fallbacks!
     {:base-url base-url
      :base-urls base-urls
      :error-message "Unable to generate HyperUnit deposit address."
      :request-fn attempt-for-base-url!})))

(defn hyperunit-request-error-message
  [err {:keys [asset source-chain]}]
  (let [message (or (non-blank-text (some-> err .-message))
                    (non-blank-text (str err))
                    "Unknown HyperUnit error.")
        message-lower (str/lower-case message)
        asset-label (or (some-> asset str str/upper-case)
                        "asset")
        network-label (or (some-> source-chain str str/capitalize)
                          "selected network")]
    (cond
      (or (str/includes? message-lower "failed to fetch")
          (str/includes? message-lower "networkerror")
          (str/includes? message-lower "network request failed"))
      (str "Unable to reach HyperUnit address service for "
           asset-label
           " on "
           network-label
           ". Check your network and retry. If this persists in local dev, route HyperUnit through a same-origin proxy.")

      :else
      message)))
