(ns hyperopen.order.exchange-errors
  (:require [clojure.string :as str]))

(def ^:private schedule-cancel-volume-gate-pattern
  #"^Cannot set scheduled cancel time until enough volume traded\. Required: \$([0-9]+(?:\.[0-9]+)?)\. Traded: \$([0-9]+(?:\.[0-9]+)?)\.$")

(defn- text-value
  [value]
  (cond
    (map? value)
    (or (text-value (:data value))
        (text-value (:error value))
        (text-value (:message value))
        (text-value (:response value))
        (pr-str value))

    :else
    (some-> value str)))

(defn- normalize-text
  [value]
  (let [text (some-> value text-value str/trim)]
    (when (seq text)
      text)))

(defn- multi-order-detail?
  [text]
  (boolean (re-find #";\s*Order\s+\d+:" text)))

(defn- remove-single-order-prefix
  [text]
  (if (multi-order-detail? text)
    text
    (if-let [[_ detail] (re-matches #"^Order\s+1:\s*(.+)$" text)]
      detail
      text)))

(defn- decimal-currency?
  [value]
  (str/includes? (str value) "."))

(defn- format-usd
  [value]
  (let [n (js/parseFloat value)]
    (if (js/isNaN n)
      (str "$" value)
      (.format (js/Intl.NumberFormat.
                "en-US"
                #js {:style "currency"
                     :currency "USD"
                     :minimumFractionDigits (if (decimal-currency? value) 2 0)
                     :maximumFractionDigits 2})
               n))))

(defn submit-error-toast-payload
  ([error-detail]
   (submit-error-toast-payload error-detail nil))
  ([error-detail {:keys [partial?]}]
   (let [detail (or (some-> error-detail
                             normalize-text
                             remove-single-order-prefix)
                    "The exchange rejected this order.")
         headline (if partial?
                    "Order partially placed"
                    "Order not placed")
         subline (if partial?
                   "Some order legs were rejected by the exchange."
                   "The exchange rejected this order.")]
     {:headline headline
      :subline subline
      :detail detail
      :message (str headline ": " detail)
      :auto-timeout? false})))

(defn schedule-cancel-volume-gate
  [response-text]
  (when-let [text (normalize-text response-text)]
    (when-let [[_ required traded] (re-matches schedule-cancel-volume-gate-pattern text)]
      (let [required* (format-usd required)
            traded* (format-usd traded)]
        {:status :unavailable
         :reason :volume-gate
         :required required*
         :traded traded*
         :message (str "Safety auto-cancel unavailable until "
                       required*
                       " traded. Current volume: "
                       traded*
                       ".")}))))
