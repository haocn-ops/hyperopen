(ns hyperopen.websocket.candles
  (:require [clojure.string :as str]
            [hyperopen.telemetry :as telemetry]
            [hyperopen.websocket.client :as ws-client]))

(def ^:private default-owner
  :active-chart)

(def ^:private max-candle-count
  5000)

(def ^:private default-candle-state
  {:subscriptions #{}
   :owners-by-sub {}
   :sub-by-owner {}})

(defonce candle-state
  (atom default-candle-state))

(defn- normalize-owner
  [owner]
  (if (keyword? owner)
    owner
    default-owner))

(defn- normalized-state
  [state]
  (let [state* (merge default-candle-state (or state {}))]
    (assoc state*
           :subscriptions (set (or (:subscriptions state*) #{}))
           :owners-by-sub (or (:owners-by-sub state*) {})
           :sub-by-owner (or (:sub-by-owner state*) {}))))

(defn- normalize-coin
  [coin]
  (let [coin* (some-> coin str str/trim)]
    (when (seq coin*)
      coin*)))

(defn- normalize-interval
  [interval]
  (cond
    (keyword? interval)
    (let [token (-> interval name str/trim)]
      (when (seq token)
        (keyword token)))

    (string? interval)
    (let [token (str/trim interval)]
      (when (seq token)
        (keyword token)))

    :else
    nil))

(defn- parse-number
  [value]
  (cond
    (number? value)
    (when-not (js/isNaN value)
      value)

    (string? value)
    (let [parsed (js/parseFloat value)]
      (when-not (js/isNaN parsed)
        parsed))

    :else
    nil))

(defn- parse-ms
  [value]
  (when-let [n (parse-number value)]
    (js/Math.floor n)))

(defn- subscription->payload
  [[coin interval]]
  {:type "candle"
   :coin coin
   :interval (name interval)})

(defn- send-subscribe!
  [subscription]
  (ws-client/send-message! {:method "subscribe"
                            :subscription (subscription->payload subscription)}))

(defn- send-unsubscribe!
  [subscription]
  (ws-client/send-message! {:method "unsubscribe"
                            :subscription (subscription->payload subscription)}))

(defn- normalized-subscription
  [coin interval]
  (when-let [coin* (normalize-coin coin)]
    (when-let [interval* (normalize-interval interval)]
      [coin* interval*])))

(defn- remove-owner-from-subscription
  [{:keys [subscriptions owners-by-sub sub-by-owner] :as state} owner subscription]
  (if-not subscription
    {:state state}
    (let [remaining-owners (disj (get owners-by-sub subscription #{}) owner)
          last-owner? (empty? remaining-owners)]
      {:state (assoc state
                     :subscriptions (if last-owner?
                                      (disj subscriptions subscription)
                                      subscriptions)
                     :owners-by-sub (if last-owner?
                                      (dissoc owners-by-sub subscription)
                                      (assoc owners-by-sub subscription remaining-owners))
                     :sub-by-owner (dissoc sub-by-owner owner))
       :unsubscribe (when last-owner? subscription)})))

(defn- add-owner-to-subscription
  [{:keys [subscriptions owners-by-sub sub-by-owner] :as state} owner subscription]
  (if-not subscription
    {:state state}
    (let [owners (get owners-by-sub subscription #{})
          first-owner? (empty? owners)]
      {:state (assoc state
                     :subscriptions (conj subscriptions subscription)
                     :owners-by-sub (assoc owners-by-sub subscription (conj owners owner))
                     :sub-by-owner (assoc sub-by-owner owner subscription))
       :subscribe (when first-owner? subscription)})))

(defn- candle-subscription-transition
  [state owner next-sub]
  (let [{:keys [sub-by-owner] :as state*} (normalized-state state)
        prev-sub (get sub-by-owner owner)]
    (if (= prev-sub next-sub)
      {:state state*}
      (let [{state1 :state unsubscribe :unsubscribe}
            (remove-owner-from-subscription state* owner prev-sub)
            {state2 :state subscribe :subscribe}
            (add-owner-to-subscription state1 owner next-sub)]
        {:state state2
         :unsubscribe unsubscribe
         :subscribe subscribe}))))

(defn sync-candle-subscription!
  ([coin interval]
   (sync-candle-subscription! coin interval default-owner))
  ([coin interval owner]
   (let [owner* (normalize-owner owner)
         next-sub (normalized-subscription coin interval)
         unsubscribe-sub (atom nil)
         subscribe-sub (atom nil)]
     (swap! candle-state
            (fn [state]
              (let [{:keys [state unsubscribe subscribe]}
                    (candle-subscription-transition state owner* next-sub)]
                (reset! unsubscribe-sub unsubscribe)
                (reset! subscribe-sub subscribe)
                state)))
     (when-let [subscription @unsubscribe-sub]
       (send-unsubscribe! subscription))
     (when-let [subscription @subscribe-sub]
       (send-subscribe! subscription)))))

(defn clear-owner-subscription!
  ([] (clear-owner-subscription! default-owner))
  ([owner]
   (sync-candle-subscription! nil nil owner)))

(defn get-subscriptions
  []
  (:subscriptions (normalized-state @candle-state)))

(defn- payload-candle-rows
  [payload]
  (let [data (:data payload)]
    (cond
      (sequential? data)
      data

      (map? data)
      [data]

      :else
      [])))

(defn- payload-candle-metadata
  [payload]
  (let [data (when (map? (:data payload))
               (:data payload))]
    {:coin (or (normalize-coin (:s data))
               (normalize-coin (:coin data))
               (normalize-coin (:coin payload)))
     :interval (or (normalize-interval (:i data))
                   (normalize-interval (:interval data))
                   (normalize-interval (:interval payload)))}))

(defn- resolve-candle-coin
  [payload-meta row]
  (or (normalize-coin (:s row))
      (normalize-coin (:coin row))
      (:coin payload-meta)))

(defn- resolve-candle-interval
  [payload-meta row]
  (or (normalize-interval (:i row))
      (normalize-interval (:interval row))
      (:interval payload-meta)))

(defn- candle-row-timestamp
  [row]
  (or (parse-ms (:t row))
      (parse-ms (:time row))
      (parse-ms (:T row))))

(defn- required-candle-prices
  [row]
  (let [open (parse-number (or (:o row) (:open row)))
        high (parse-number (or (:h row) (:high row)))
        low (parse-number (or (:l row) (:low row)))
        close (parse-number (or (:c row) (:close row)))]
    (when (every? number? [open high low close])
      {:o open
       :h high
       :l low
       :c close})))

(defn- required-candle-row
  [row]
  (when-let [t (candle-row-timestamp row)]
    (when-let [prices (required-candle-prices row)]
      (assoc prices :t t))))

(defn- with-optional-candle-fields
  [row candle-row]
  (let [volume (parse-number (or (:v row) (:volume row)))
        close-time (parse-ms (:T row))
        trades (parse-ms (:n row))]
    (cond-> candle-row
      (number? volume) (assoc :v volume)
      (number? close-time) (assoc :T close-time)
      (number? trades) (assoc :n trades))))

(defn- normalize-candle-entry
  [payload-meta row]
  (let [coin (resolve-candle-coin payload-meta row)
        interval (resolve-candle-interval payload-meta row)
        candle-row (required-candle-row row)]
    (when (and coin interval candle-row)
      {:coin coin
       :interval interval
       :row (with-optional-candle-fields row candle-row)})))

(defn- normalize-payload-candle-rows
  [payload]
  (let [payload-meta (payload-candle-metadata payload)]
    (->> (payload-candle-rows payload)
         (keep #(normalize-candle-entry payload-meta %))
       (reduce (fn [acc {:keys [coin interval row]}]
                 (update acc [coin interval] (fnil conj []) row))
               {}))))

(defn- entry-rows-key
  [entry]
  (cond
    (and (map? entry) (contains? entry :candles)) :candles
    (and (map? entry) (contains? entry :rows)) :rows
    (and (map? entry) (contains? entry :data)) :data
    :else nil))

(defn- extract-existing-candle-rows
  [entry]
  (if-let [rows-key (entry-rows-key entry)]
    (let [rows (get entry rows-key)]
      (if (sequential? rows)
        (vec rows)
        []))
    (if (sequential? entry)
      (vec entry)
      [])))

(defn- bounded-dedupe-sorted-rows
  [rows]
  (let [deduped (->> rows
                     (reduce (fn [acc row]
                               (if (number? (:t row))
                                 (assoc acc (:t row) row)
                                 acc))
                             {})
                     vals
                     (sort-by :t)
                     vec)
        count* (count deduped)]
    (if (> count* max-candle-count)
      (subvec deduped (- count* max-candle-count))
      deduped)))

(defn- tail-merged-rows
  "Fast path for the common ws case: incoming rows only touch bars at or after
   the newest existing bar, so the existing vector (kept sorted and deduped by
   construction - every write goes through a dedupe+sort at least once) can be
   patched at the tail instead of re-sorting up to max-candle-count rows per
   message."
  [existing incoming]
  (let [last-t (:t (peek existing))]
    (when (and (seq existing)
               (seq incoming)
               (number? last-t)
               (every? (fn [row]
                         (let [t (:t row)]
                           (and (number? t) (>= t last-t))))
                       incoming))
      (let [incoming* (bounded-dedupe-sorted-rows incoming)
            merged (if (= last-t (:t (first incoming*)))
                     (into (pop existing) incoming*)
                     (into existing incoming*))
            count* (count merged)]
        (if (> count* max-candle-count)
          (subvec merged (- count* max-candle-count))
          merged)))))

(defn- merge-candle-rows
  [entry incoming]
  (let [existing (extract-existing-candle-rows entry)
        incoming* (vec incoming)]
    (or (tail-merged-rows existing incoming*)
        (bounded-dedupe-sorted-rows (into existing incoming*)))))

(defn- clear-candle-entry-errors
  [entry]
  (if (map? entry)
    (dissoc entry :error :error-category)
    entry))

(defn- write-candle-entry
  [entry rows]
  (if-let [rows-key (entry-rows-key entry)]
    (assoc (clear-candle-entry-errors entry) rows-key rows)
    rows))

(defn- update-candle-entry
  [entry rows]
  (write-candle-entry entry (merge-candle-rows entry rows)))

(defn- apply-candle-rows-by-sub
  [state rows-by-sub]
  (reduce-kv (fn [acc [coin interval] rows]
               (update-in acc [:candles coin interval] update-candle-entry rows))
             state
             rows-by-sub))

(defn create-candles-handler
  [store]
  (fn [payload]
    (when (and (map? payload)
               (= "candle" (:channel payload)))
      (let [rows-by-sub (normalize-payload-candle-rows payload)]
        (when (seq rows-by-sub)
          (swap! store apply-candle-rows-by-sub rows-by-sub))))))

(defn init!
  [store]
  (telemetry/log! "Candle subscription module initialized")
  (ws-client/register-handler! "candle" (create-candles-handler store)))
