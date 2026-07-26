(ns hyperopen.websocket.acl.subscription-errors
  "Classify Hyperliquid websocket `error` channel frames. Pure text->data;
  callers decide what to do with the classification.

  Observed provider error shapes (2026-07):
  - \"Already subscribed: {subscription-json}\" (benign duplicate)
  - \"Already unsubscribed: {subscription-json}\" (benign duplicate)
  - \"Error parsing JSON into valid websocket request: {full-request-json}\"
    (schema-level rejection; how the webData2 removal surfaced)"
  (:require [clojure.string :as str]))

(def ^:private rejected-prefixes
  ["Error parsing JSON into valid websocket request"
   "Invalid subscription"])

(def ^:private benign-prefixes
  ["Already subscribed"
   "Already unsubscribed"])

(defn- parse-embedded-json
  [text]
  (let [idx (.indexOf text "{")]
    (when (>= idx 0)
      (try
        (js->clj (js/JSON.parse (subs text idx)) :keywordize-keys true)
        (catch :default _ nil)))))

(defn- embedded-subscription
  "Extract {:method .. :subscription ..} from a parsed error echo. Full-request
  echoes nest the subscription; duplicate-subscription echoes are the
  subscription map itself."
  [parsed]
  (cond
    (map? (:subscription parsed))
    {:method (:method parsed)
     :subscription (:subscription parsed)}

    (and (map? parsed) (string? (:type parsed)))
    {:method nil
     :subscription parsed}

    :else nil))

(defn classify-error-text
  "Returns {:kind :rejected|:benign|:unknown, :subscription map-or-nil}.

  :rejected only when the error is schema-level, the offending subscription is
  recoverable from the echo, and the request was a subscribe (an unsubscribe
  echo must never downgrade a live stream)."
  [text]
  (if-not (string? text)
    {:kind :unknown :subscription nil}
    (let [{:keys [method subscription]} (embedded-subscription (parse-embedded-json text))
          starts-with-any? (fn [prefixes]
                             (boolean (some #(str/starts-with? text %) prefixes)))]
      (cond
        (starts-with-any? benign-prefixes)
        {:kind :benign :subscription subscription}

        (and (starts-with-any? rejected-prefixes)
             (map? subscription)
             (or (nil? method)
                 (= "subscribe" (some-> method str str/lower-case))))
        {:kind :rejected :subscription subscription}

        :else
        {:kind :unknown :subscription subscription}))))
