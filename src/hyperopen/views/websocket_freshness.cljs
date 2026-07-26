(ns hyperopen.views.websocket-freshness
  (:require [clojure.string :as str]
            [hyperopen.websocket.domain.model :as model]))

(defn- age-ms
  [generated-at-ms at-ms]
  (when (and (number? generated-at-ms) (number? at-ms))
    (max 0 (- generated-at-ms at-ms))))

(defn format-age-ms
  [value]
  (cond
    (not (number? value))
    nil

    (< value 1000)
    (str value "ms")

    (< value 60000)
    (str (quot value 1000) "s")

    :else
    (let [minutes (quot value 60000)
          seconds (quot (mod value 60000) 1000)]
      (str minutes "m " seconds "s"))))

(defn- address-like?
  [value]
  (and (string? value)
       (re-matches #"0x[0-9a-fA-F]+" value)))

(defn- selector-value=
  [a b]
  (cond
    (and (address-like? a) (address-like? b))
    (= (str/lower-case a) (str/lower-case b))

    :else
    (= a b)))

(defn- descriptor-matches-selector?
  [descriptor selector]
  (every? (fn [[k v]]
            (if (nil? v)
              true
              (selector-value= v (get descriptor k))))
          selector))

(defn- stream-topic
  [stream]
  (or (:topic stream)
      (get-in stream [:descriptor :type])))

(defn- active-topic-entries
  [streams topic]
  (->> (or streams {})
       (filter (fn [[_ stream]]
                 (and (:subscribed? stream)
                      (= topic (stream-topic stream)))))
       vec))

(defn- exact-key-entry
  [streams topic selector]
  (let [sub-key (model/subscription-key (merge {:type topic} (or selector {})))
        stream (get streams sub-key)]
    (when (and stream (= topic (stream-topic stream)))
      [sub-key stream])))

(defn find-stream-entry
  [health {:keys [topic selector]}]
  (let [streams (or (:streams health) {})
        active-entries (active-topic-entries streams topic)
        selector* (or selector {})]
    (or (exact-key-entry streams topic selector*)
        (when (seq selector*)
          (let [selector-matches (->> active-entries
                                      (filter (fn [[_ stream]]
                                                (descriptor-matches-selector?
                                                  (or (:descriptor stream) {})
                                                  selector*)))
                                      vec)]
            (when (= 1 (count selector-matches))
              (first selector-matches))))
        (when (= 1 (count active-entries))
          (first active-entries)))))

(defn- delayed-thresholded?
  [status stale-threshold-ms]
  (and (= :delayed status)
       (number? stale-threshold-ms)))

(defn- with-age-ago
  [prefix age-ms fallback-text]
  (if-let [age-label (format-age-ms age-ms)]
    (str prefix " " age-label " ago")
    fallback-text))

(defn surface-cue
  [health {:keys [topic selector live-prefix na-prefix idle-text]
           :or {live-prefix "Updated"
                na-prefix "Last update"
                idle-text "Waiting for first update..."}}]
  (let [generated-at-ms (:generated-at-ms health)
        [sub-key stream] (find-stream-entry health {:topic topic :selector selector})
        status (if stream (:status stream) :idle)
        stale-threshold-ms (:stale-threshold-ms stream)
        age (age-ms generated-at-ms (:last-payload-at-ms stream))
        delayed? (delayed-thresholded? status stale-threshold-ms)
        tone (cond
               delayed? :warning
               (= :live status) :success
               :else :neutral)
        text (cond
               (= :idle status)
               idle-text

               (= :n-a status)
               (with-age-ago na-prefix age na-prefix)

               delayed?
               (if-let [age-label (format-age-ms age)]
                 (str "Stale " age-label)
                 "Stale")

               (= :live status)
               (with-age-ago live-prefix age live-prefix)

               :else
               (with-age-ago na-prefix age na-prefix))]
    {:topic topic
     :status status
     :tone tone
     :text text
     :age-ms age
     :stale-threshold-ms stale-threshold-ms
     :delayed? delayed?
     :stream-key sub-key
     :stream stream}))
