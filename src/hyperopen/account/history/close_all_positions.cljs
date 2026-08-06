(ns hyperopen.account.history.close-all-positions
  (:require [clojure.string :as str]
            [hyperopen.account.history.position-identity :as position-identity]
            [hyperopen.account.history.position-reduce :as position-reduce]
            [hyperopen.asset-selector.markets :as markets]
            [hyperopen.domain.trading :as trading-domain]))

(defn default-confirmation-state
  []
  {:open? false
   :lifecycle :idle
   :snapshot nil
   :trigger-bounds nil
   :error nil
   :accepted-count 0
   :rejected-count 0})

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn- signed-size
  [value]
  (let [number* (trading-domain/parse-num value)]
    (when (and (number? number*)
               (js/isFinite number*))
      number*)))

(defn- nonzero-position-snapshot-entry
  [dex position-row]
  (let [position (:position position-row)
        coin (non-blank-text (:coin position))
        size (signed-size (:szi position))]
    (when (and (map? position-row)
               (map? position)
               coin
               (number? size)
               (not (zero? size)))
      {:position-key (position-identity/position-unique-key
                      (assoc position-row :dex dex))
       :coin coin
       :dex dex
       :szi (:szi position)})))

(defn current-position-snapshot
  [state]
  (let [default-positions (get-in state [:webdata2 :clearinghouseState :assetPositions])
        perp-dex-states (or (:perp-dex-clearinghouse state) {})]
    (into []
          (concat
           (keep #(nonzero-position-snapshot-entry nil %) default-positions)
           (mapcat (fn [[dex clearinghouse-state]]
                     (keep #(nonzero-position-snapshot-entry dex %)
                           (:assetPositions clearinghouse-state)))
                   (sort-by (comp str first) perp-dex-states))))))

(defn confirmation-state
  [snapshot trigger-bounds]
  (assoc (default-confirmation-state)
         :open? true
         :lifecycle :confirming
         :snapshot (vec (or snapshot []))
         :trigger-bounds trigger-bounds))

(defn- normalized-dex
  [dex]
  (some-> dex str str/trim str/lower-case not-empty))

(defn- same-dex?
  [left right]
  (= (normalized-dex left)
     (normalized-dex right)))

(defn- market-for-position
  [market-by-key {:keys [coin dex]}]
  (let [exact (some (fn [market]
                      (when (and (= coin (:coin market))
                                 (same-dex? dex (:dex market)))
                        market))
                    (vals (or market-by-key {})))
        fallback (markets/resolve-market-by-coin market-by-key coin)]
    (or exact
        (when (and fallback
                   (same-dex? dex (:dex fallback)))
          fallback))))

(defn- market-asset-id
  [market]
  (let [value (or (:asset-id market) (:assetId market) (:idx market))
        number* (trading-domain/parse-num value)]
    (when (and (number? number*)
               (js/isFinite number*))
      (js/Math.floor number*))))

(defn- expected-size-text
  [szi]
  (some-> szi signed-size js/Math.abs (trading-domain/number->clean-string 12)))

(defn- expected-buy?
  [szi]
  (neg? (or (signed-size szi) 0)))

(defn- prepared-leg-error
  [position market candidate]
  (let [order (first (get-in candidate [:request :action :orders]))
        expected-asset (market-asset-id market)
        expected-size (expected-size-text (:szi position))]
    (cond
      (not (:ok? candidate))
      (or (:display-message candidate) "Unable to prepare every close order.")

      (not= "order" (get-in candidate [:request :action :type]))
      "Unable to prepare every close order."

      (not= 1 (count (get-in candidate [:request :action :orders])))
      "Unable to prepare every close order."

      (not= expected-asset (:a order))
      "Market data is unavailable for one of the positions."

      (not= (expected-buy? (:szi position)) (:b order))
      "A close order has an invalid direction."

      (not= expected-size (:s order))
      "A close order has an invalid size."

      (not (true? (:r order)))
      "A close order must be reduce-only."

      (not= "Ioc" (get-in order [:t :limit :tif]))
      "A close order must use immediate-or-cancel execution."

      :else
      nil)))

(defn- request-builder
  [request]
  (or (:builder request)
      (get-in request [:action :builder])))

(defn- position-row
  [{:keys [coin dex szi]}]
  {:dex dex
   :position {:coin coin
              :szi szi}})

(defn- valid-snapshot-position?
  [{:keys [coin szi]}]
  (and (non-blank-text coin)
       (let [size (signed-size szi)]
         (and (number? size)
              (not (zero? size))))))

(defn prepare-submit
  [state snapshot]
  (let [snapshot* (vec (or snapshot []))
        market-by-key (get-in state [:asset-selector :market-by-key] {})]
    (cond
      (empty? snapshot*)
      {:ok? false
       :display-message "There are no open positions to close."}

      :else
      (loop [remaining snapshot*
             prepared []
             builder ::unset]
        (if-let [position (first remaining)]
          (if-not (valid-snapshot-position? position)
            {:ok? false
             :display-message "A position has an invalid size."}
            (let [market (market-for-position market-by-key position)]
              (if-not (and market (number? (market-asset-id market)))
              {:ok? false
               :display-message "Market data is unavailable for one of the positions."}
              (let [candidate (position-reduce/prepare-submit
                               state
                               (position-reduce/from-position-row (position-row position)))
                    error (prepared-leg-error position market candidate)
                    candidate-builder (request-builder (:request candidate))]
                (cond
                  error
                  {:ok? false
                   :display-message error}

                  (and (not= ::unset builder)
                       (not= builder candidate-builder))
                  {:ok? false
                   :display-message "Builder fee metadata differs between close orders."}

                  :else
                  (recur (rest remaining)
                         (conj prepared candidate)
                         candidate-builder))))))
          (let [requests (mapv :request prepared)
                action (assoc (:action (first requests))
                              :orders (mapv #(first (get-in % [:action :orders])) requests))
                builder* (request-builder (first requests))
                root-builder? (contains? (first requests) :builder)]
            {:ok? true
             :snapshot snapshot*
             :request (cond-> {:snapshot snapshot*
                                :action action}
                        (and root-builder? (some? builder*)) (assoc :builder builder*))}))))))
