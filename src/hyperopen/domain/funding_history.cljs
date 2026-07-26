(ns hyperopen.domain.funding-history)

(def default-window-ms (* 7 24 60 60 1000))

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- parse-decimal
  [value]
  (cond
    (number? value)
    (when (finite-number? value) value)

    (string? value)
    (let [num (js/parseFloat value)]
      (when (finite-number? num) num))

    :else nil))

(defn- parse-ms
  [value]
  (when-let [num (parse-decimal value)]
    (js/Math.floor num)))

(defn funding-position-side
  [signed-size]
  (cond
    (pos? signed-size) :long
    (neg? signed-size) :short
    :else :flat))

(defn funding-history-row-id
  [time-ms coin signed-size payment-usdc funding-rate]
  (str time-ms "|" coin "|" signed-size "|" payment-usdc "|" funding-rate))

(defn- normalize-funding-row
  [{:keys [time-ms coin signed-size payment-usdc funding-rate source]}]
  (let [time-ms* (parse-ms time-ms)
        signed-size* (parse-decimal signed-size)
        payment-usdc* (parse-decimal payment-usdc)
        funding-rate* (parse-decimal funding-rate)
        coin* (when (string? coin) coin)]
    (when (and time-ms*
               coin*
               (number? signed-size*)
               (number? payment-usdc*)
               (number? funding-rate*))
      {:id (funding-history-row-id time-ms* coin* signed-size* payment-usdc* funding-rate*)
       :time-ms time-ms*
       :time time-ms*
       :coin coin*
       :size-raw (js/Math.abs signed-size*)
       :position-size-raw signed-size*
       :positionSize signed-size*
       :position-side (funding-position-side signed-size*)
       :payment-usdc-raw payment-usdc*
       :payment payment-usdc*
       :funding-rate-raw funding-rate*
       :fundingRate funding-rate*
       :source source})))

(defn normalize-info-funding-row
  [row]
  (let [delta (:delta row)
        funding-delta? (or (nil? (:type delta))
                           (= "funding" (:type delta)))
        payload (cond
                  (and (map? delta) funding-delta?)
                  {:time-ms (:time row)
                   :coin (:coin delta)
                   :signed-size (:szi delta)
                   :payment-usdc (:usdc delta)
                   :funding-rate (:fundingRate delta)}

                  (map? row)
                  {:time-ms (:time row)
                   :coin (:coin row)
                   :signed-size (:szi row)
                   :payment-usdc (:usdc row)
                   :funding-rate (:fundingRate row)}

                  :else nil)]
    (when payload
      (normalize-funding-row (assoc payload :source :info)))))

(defn normalize-info-funding-rows
  [rows]
  (into []
        (comp
          (map normalize-info-funding-row)
          (keep identity))
        rows))

(defn normalize-ws-funding-row
  [row]
  (normalize-funding-row {:time-ms (:time row)
                          :coin (:coin row)
                          :signed-size (:szi row)
                          :payment-usdc (:usdc row)
                          :funding-rate (:fundingRate row)
                          :source :ws}))

(defn normalize-ws-funding-rows
  [rows]
  (into []
        (comp
          (map normalize-ws-funding-row)
          (keep identity))
        rows))

(defn sort-funding-history-rows
  [rows]
  (->> rows
       (sort-by (fn [row]
                  [(- (or (:time-ms row) 0))
                   (or (:id row) "")]))
       vec))

(defn merge-funding-history-rows
  [existing incoming]
  (->> (concat (or existing []) (or incoming []))
       (reduce (fn [acc row]
                 (if (and (map? row) (seq (:id row)))
                   (assoc acc (:id row) row)
                   acc))
               {})
       vals
       sort-funding-history-rows
       vec))

(defn normalize-funding-history-filters
  ([filters now]
   (normalize-funding-history-filters filters now default-window-ms))
  ([filters now window-ms]
   (let [coin-set (->> (or (:coin-set filters) #{})
                       (keep (fn [coin]
                               (when (and (string? coin)
                                          (seq coin))
                                 coin)))
                       set)
         default-end (or (parse-ms now) 0)
         default-start (max 0 (- default-end (or window-ms default-window-ms)))
         start-candidate (parse-ms (:start-time-ms filters))
         end-candidate (parse-ms (:end-time-ms filters))
         start-time-ms (or start-candidate default-start)
         end-time-ms (or end-candidate default-end)
         [start-ms end-ms] (if (> start-time-ms end-time-ms)
                             [end-time-ms start-time-ms]
                             [start-time-ms end-time-ms])]
     {:coin-set coin-set
      :start-time-ms start-ms
      :end-time-ms end-ms})))

(defn filter-funding-history-rows
  [rows filters]
  (let [{:keys [coin-set start-time-ms end-time-ms]} filters
        use-coin-filter? (seq coin-set)]
    (-> (into []
              (filter (fn [row]
                        (let [time-ms (:time-ms row)
                              coin (:coin row)]
                          (and (number? time-ms)
                               (>= time-ms start-time-ms)
                               (<= time-ms end-time-ms)
                               (or (not use-coin-filter?)
                                   (contains? coin-set coin))))))
              (or rows []))
        sort-funding-history-rows)))
