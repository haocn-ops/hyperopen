(ns hyperopen.websocket.diagnostics.policy
  (:require [clojure.string :as str]
            [hyperopen.websocket.diagnostics.catalog :as catalog]))

(def meter-bars-total
  4)

(def ^:private meter-score-max
  100)

(def ^:private meter-status-penalty
  {:idle 0
   :event-driven 0
   :live 0
   :delayed 14
   :reconnecting 24
   :offline 36
   :unknown 20})

(def ^:private meter-transport-state-penalty
  {:connected 0
   :connecting 12
   :reconnecting 20
   :disconnected 32
   :unknown 20
   nil 20})

(def ^:private meter-transport-freshness-penalty
  {:idle 6
   :event-driven 0
   :live 0
   :delayed 18
   :reconnecting 34
   :offline 55
   :unknown 28})

(def ^:private transport-live-threshold-ms
  10000)

(defn display-now-ms
  [generated-at-ms wall-now-ms]
  (if (number? generated-at-ms)
    generated-at-ms
    wall-now-ms))

(defn effective-now-ms
  [generated-at-ms wall-now-ms]
  (cond
    (and (number? generated-at-ms)
         (>= generated-at-ms 1000000000000))
    (max generated-at-ms wall-now-ms)

    (number? generated-at-ms)
    generated-at-ms

    :else
    wall-now-ms))

(defn transport-last-recv-age-ms
  [now-ms health]
  (let [last-recv-at-ms (get-in health [:transport :last-recv-at-ms])]
    (when (and (number? now-ms)
               (number? last-recv-at-ms))
      (max 0 (- now-ms last-recv-at-ms)))))

(defn stream-age-ms
  [now-ms stream]
  (let [last-payload-at-ms (:last-payload-at-ms stream)]
    (when (and (number? now-ms)
               (number? last-payload-at-ms))
      (max 0 (- now-ms last-payload-at-ms)))))

(defn- round-int
  [value]
  (int (js/Math.round value)))

(defn- dominant-status-from-groups
  [health]
  (some (fn [group]
          (let [status (catalog/normalize-status
                        (get-in health [:groups group :worst-status]))]
            (when (catalog/non-neutral-status? status)
              {:source group
               :status status})))
        catalog/group-order))

(defn dominant-status
  [health]
  (or (dominant-status-from-groups health)
      {:source :transport
       :status (catalog/normalize-status
                (get-in health [:transport :freshness]))}))

(defn- weighted-status-penalty
  [group status]
  (let [weight (catalog/group-weight group)
        status* (catalog/normalize-status status)
        base (get meter-status-penalty status*
                  (get meter-status-penalty :unknown 20))]
    (round-int (* weight base))))

(defn- age-threshold-ratio
  [age-ms threshold-ms]
  (when (and (number? age-ms)
             (number? threshold-ms)
             (pos? threshold-ms))
    (/ age-ms threshold-ms)))

(defn- live-stream-headroom-penalty
  [now-ms stream]
  (let [age-ms (or (:age-ms stream)
                   (stream-age-ms now-ms stream))
        ratio (age-threshold-ratio age-ms (:stale-threshold-ms stream))]
    (cond
      (not (:subscribed? stream)) 0
      (not= :live (catalog/normalize-status (:status stream))) 0
      (not (number? ratio)) 0
      (< ratio 0.12) 0
      (< ratio 0.20) 2
      (< ratio 0.32) 4
      (< ratio 0.45) 6
      (< ratio 0.60) 9
      (< ratio 0.80) 12
      :else 16)))

(defn- stream-headroom-component
  [health now-ms]
  (let [penalties (->> (vals (or (:streams health) {}))
                       (map #(live-stream-headroom-penalty now-ms %))
                       (filter pos?)
                       (sort >)
                       vec)
        top-penalty (reduce + (take 3 penalties))
        overflow-penalty (max 0 (- (count penalties) 3))
        penalty (min 22 (+ top-penalty overflow-penalty))]
    {:id :stream-headroom
     :label "Live stream headroom"
     :penalty penalty
     :detail (if (seq penalties)
               (str (count penalties) " live stream(s) are near their stale threshold.")
               "No live stream headroom penalty.")}))

(defn- transport-headroom-component
  [health now-ms]
  (let [transport-state (get-in health [:transport :state])
        transport-freshness (catalog/normalize-status
                             (get-in health [:transport :freshness]))
        age-ms (transport-last-recv-age-ms now-ms health)
        ratio (age-threshold-ratio age-ms transport-live-threshold-ms)
        penalty (cond
                  (not= :connected transport-state) 0
                  (not= :live transport-freshness) 0
                  (not (number? ratio)) 0
                  (< ratio 0.08) 0
                  (< ratio 0.16) 2
                  (< ratio 0.28) 4
                  (< ratio 0.42) 6
                  (< ratio 0.58) 9
                  (< ratio 0.75) 12
                  :else 16)]
    {:id :transport-headroom
     :label "Transport headroom"
     :penalty penalty
     :detail (if (number? age-ms)
               (str "Last transport message age is " age-ms " ms.")
               "Transport last-receive timestamp unavailable.")}))

(defn- delayed-stream-severity-penalty
  [now-ms stream]
  (let [age-ms (or (:age-ms stream)
                   (stream-age-ms now-ms stream))
        stale-threshold-ms (:stale-threshold-ms stream)
        ratio (when (and (= :delayed (catalog/normalize-status (:status stream)))
                         (:subscribed? stream)
                         (number? age-ms)
                         (number? stale-threshold-ms)
                         (pos? stale-threshold-ms))
                (/ age-ms stale-threshold-ms))]
    (cond
      (not (number? ratio)) 0
      (<= ratio 1.25) 3
      (<= ratio 1.50) 6
      (<= ratio 2.00) 10
      (<= ratio 3.00) 14
      :else 18)))

(defn- delayed-stream-component
  [health now-ms]
  (let [penalties (->> (vals (or (:streams health) {}))
                       (map #(delayed-stream-severity-penalty now-ms %))
                       (filter pos?)
                       (sort >)
                       vec)
        top-penalty (reduce + (take 3 penalties))
        overflow-penalty (* 2 (max 0 (- (count penalties) 3)))
        penalty (min 28 (+ top-penalty overflow-penalty))]
    {:id :delayed-streams
     :label "Delayed streams"
     :penalty penalty
     :detail (if (seq penalties)
               (str (count penalties) " stream(s) are already delayed beyond threshold.")
               "No delayed-stream penalty.")}))

(defn any-gap-detected?
  [health]
  (boolean
   (some true?
         (for [group catalog/group-order]
           (get-in health [:groups group :gap-detected?])))))

(defn- gap-component
  [health]
  (let [gap? (any-gap-detected? health)]
    {:id :sequence-gap
     :label "Sequence gaps"
     :penalty (if gap? 8 0)
     :detail (if gap?
               "One or more stream groups reported a sequence gap."
               "No sequence gaps detected.")}))

(defn browser-network-hint-penalty
  [network-hint]
  (let [{:keys [effective-type rtt downlink save-data?]} (or network-hint {})
        effective-type* (some-> effective-type str str/lower-case)
        effective-type-penalty (case effective-type*
                                 "slow-2g" 32
                                 "2g" 24
                                 "3g" 14
                                 0)
        rtt-penalty (cond
                      (not (number? rtt)) 0
                      (> rtt 1200) 12
                      (> rtt 800) 9
                      (> rtt 400) 6
                      (> rtt 250) 3
                      :else 0)
        downlink-penalty (cond
                           (not (number? downlink)) 0
                           (< downlink 0.4) 10
                           (< downlink 0.8) 7
                           (< downlink 1.5) 4
                           :else 0)
        save-data-penalty (if save-data? 4 0)
        detail-parts (cond-> []
                       effective-type* (conj effective-type*)
                       (number? rtt) (conj (str (round-int rtt) " ms RTT"))
                       (number? downlink) (conj (str downlink " Mbps"))
                       save-data? (conj "Save-Data enabled"))
        penalty (min 20 (+ effective-type-penalty
                           rtt-penalty
                           downlink-penalty
                           save-data-penalty))]
    {:id :browser-network
     :label "Browser network hint"
     :penalty penalty
     :detail (if (seq detail-parts)
               (str/join ", " detail-parts)
               "Browser did not report network connection hints.")}))

(defn connection-breakdown
  ([health] (connection-breakdown health {}))
  ([health {:keys [wall-now-ms network-hint]}]
   (let [now-ms (display-now-ms (:generated-at-ms health) wall-now-ms)
         transport-state (get-in health [:transport :state])
         transport-freshness (catalog/normalize-status
                              (get-in health [:transport :freshness]))
         transport-state-component
         {:id :transport-state
          :label "Transport state"
          :penalty (get meter-transport-state-penalty
                        transport-state
                        (get meter-transport-state-penalty :unknown 20))
          :detail (str "State " (catalog/status-token-label transport-state) ".")}
         transport-freshness-component
         {:id :transport-freshness
          :label "Transport freshness"
          :penalty (get meter-transport-freshness-penalty
                        transport-freshness
                        (get meter-transport-freshness-penalty :unknown 28))
          :detail (str "Freshness " (catalog/status-label transport-freshness) ".")}
         group-components
         (mapv (fn [group]
                 (let [status (catalog/normalize-status
                               (get-in health [:groups group :worst-status]))]
                   {:id (keyword (str "group-" (name group)))
                    :label (catalog/group-title group)
                    :penalty (weighted-status-penalty group status)
                    :detail (str "Worst status " (catalog/status-label status) ".")}))
               catalog/group-order)]
     (vec (concat [transport-state-component
                   transport-freshness-component]
                  group-components
                  [(transport-headroom-component health now-ms)
                   (stream-headroom-component health now-ms)
                   (delayed-stream-component health now-ms)
                   (gap-component health)
                   (browser-network-hint-penalty network-hint)])))))

(defn penalty->active-bars
  [penalty]
  (let [score (max 0 (- meter-score-max (or penalty meter-score-max)))]
    (cond
      (>= score 88) 4
      (>= score 68) 3
      (>= score 45) 2
      (>= score 20) 1
      :else 0)))

(defn connection-meter-tone
  [status active-bars]
  (let [status* (catalog/normalize-status status)]
    (cond
      (or (= status* :offline) (zero? active-bars) (= status* :unknown))
      :error

      (= status* :reconnecting)
      :warning

      (or (= status* :delayed)
          (<= active-bars 2))
      :warning

      :else
      :success)))

(defn connection-meter-model
  ([health] (connection-meter-model health {}))
  ([health {:keys [wall-now-ms network-hint]}]
   (let [{:keys [source status]} (dominant-status health)
         breakdown (connection-breakdown health
                                         {:wall-now-ms wall-now-ms
                                          :network-hint network-hint})
         penalty (reduce + (map :penalty breakdown))
         score (max 0 (- meter-score-max penalty))
         active-bars (penalty->active-bars penalty)
         label (catalog/meter-status-label status)
         bar-count meter-bars-total
         latency-ms (or (:rtt network-hint)
                        (transport-last-recv-age-ms (:generated-at-ms health) health))]
     {:source source
      :source-label (catalog/source-label source)
      :status status
      :status-label (catalog/status-label status)
      :active-bars active-bars
      :bar-count bar-count
      :label label
      :latency-label (when (number? latency-ms)
                       (str (round-int latency-ms) "ms"))
      :penalty penalty
      :score score
      :tone (connection-meter-tone status active-bars)
      :breakdown breakdown
      :tooltip (str label
                    " ("
                    active-bars
                    "/"
                    bar-count
                    " bars) - "
                    (catalog/source-label source)
                    " "
                    (str/lower-case (catalog/status-label status)))})))

(defn banner-model
  [state health]
  (let [orders-status (catalog/normalize-status
                       (get-in health [:groups :orders_oms :worst-status]))
        market-status (catalog/normalize-status
                       (get-in health [:groups :market_data :worst-status]))
        market-banner-enabled? (boolean (get-in state [:websocket-ui :show-market-offline-banner?] false))]
    (cond
      (= :reconnecting orders-status)
      {:tone :warning
       :message "Orders/OMS websocket reconnecting. Order lifecycle updates may be delayed."}

      (= :offline orders-status)
      {:tone :error
       :message "Orders/OMS websocket offline. Trading activity status may be stale."}

      (and market-banner-enabled? (= :offline market-status))
      {:tone :info
       :message "Market data websocket offline. Quotes and chart updates may be stale."}

      :else
      nil)))

(defn- cooldown-active?
  [cooldown-until-ms now-ms]
  (and (number? cooldown-until-ms)
       (number? now-ms)
       (> cooldown-until-ms now-ms)))

(defn- remaining-cooldown-ms
  [cooldown-until-ms now-ms]
  (when (cooldown-active? cooldown-until-ms now-ms)
    (max 0 (- cooldown-until-ms now-ms))))

(defn- seconds-label
  [remaining-ms]
  (max 1 (js/Math.ceil (/ (max 0 remaining-ms) 1000))))

(defn reconnect-availability
  [state health now-ms]
  (let [transport-state (get-in health [:transport :state])
        cooldown-until-ms (get-in state [:websocket-ui :reconnect-cooldown-until-ms])
        reconnecting? (contains? #{:connecting :reconnecting} transport-state)
        cooldown-remaining-ms (remaining-cooldown-ms cooldown-until-ms now-ms)
        cooldown-active? (number? cooldown-remaining-ms)]
    {:disabled? (or reconnecting? cooldown-active?)
     :reconnecting? reconnecting?
     :cooldown-active? cooldown-active?
     :cooldown-remaining-ms cooldown-remaining-ms
     :label (cond
              reconnecting? "Reconnecting..."
              cooldown-active? (str "Reconnect in " (seconds-label cooldown-remaining-ms) "s")
              :else "Reconnect now")}))

(defn reconnect-blocked?
  [state health now-ms]
  (:disabled? (reconnect-availability state health now-ms)))

(defn reset-availability
  [state health now-ms]
  (let [transport-state (get-in health [:transport :state])
        in-progress? (boolean (get-in state [:websocket-ui :reset-in-progress?] false))
        cooldown-until-ms (get-in state [:websocket-ui :reset-cooldown-until-ms])
        cooldown-remaining-ms (remaining-cooldown-ms cooldown-until-ms now-ms)
        cooldown-active? (number? cooldown-remaining-ms)
        transport-busy? (contains? #{:connecting :reconnecting} transport-state)]
    {:disabled? (or in-progress? cooldown-active? transport-busy?)
     :in-progress? in-progress?
     :cooldown-active? cooldown-active?
     :cooldown-remaining-ms cooldown-remaining-ms
     :transport-busy? transport-busy?
     :label (cond
              in-progress? "Resetting..."
              cooldown-active? (str "Reset in " (seconds-label cooldown-remaining-ms) "s")
              :else "Reset")}))

(defn reset-blocked?
  [state health now-ms]
  (:disabled? (reset-availability state health now-ms)))

(defn info-rate-limit-model
  "Drawer model for HTTP /info endpoint rate-limiting. Returns nil when the
   session has never been rate-limited; otherwise reports the cumulative count,
   whether a cooldown is currently active, and the remaining retry seconds."
  [state now-ms]
  (let [{:keys [until-ms last-at-ms]
         rate-limited-count :count} (get-in state [:websocket-ui :info-rate-limit])]
    (when (and (number? rate-limited-count)
               (pos? rate-limited-count))
      (let [cooldown-remaining-ms (remaining-cooldown-ms until-ms now-ms)
            active? (number? cooldown-remaining-ms)]
        {:active? active?
         :count rate-limited-count
         :retry-in-seconds (when active? (seconds-label cooldown-remaining-ms))
         :last-at-ms last-at-ms}))))
