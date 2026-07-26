(ns hyperopen.websocket.orderbook-policy
  (:require [hyperopen.utils.formatting :as fmt]))

(def default-max-render-levels-per-side 80)
(def default-mobile-render-levels-per-side 10)

(defn parse-number [value]
  (cond
    (number? value) value
    (string? value) (let [n (js/parseFloat value)]
                      (when-not (js/isNaN n) n))
    :else nil))

(defn level-price [level]
  (or (:px-num level)
      (parse-number (:px level))
      (parse-number (:price level))
      (parse-number (:p level))))

(defn level-size [level]
  (or (:sz-num level)
      (parse-number (:sz level))
      (parse-number (:size level))
      (parse-number (:s level))))

(defn normalize-level [level]
  (if (map? level)
    (assoc level
           :px-num (level-price level)
           :sz-num (level-size level))
    level))

(defn normalize-levels [levels]
  (mapv normalize-level (or levels [])))

(defn- strip-normalized-level [level]
  (if (map? level)
    (dissoc level :px-num :sz-num :cum-size :cum-value)
    level))

(defn- sort-levels [levels comparator]
  (vec (sort-by #(or (level-price %) 0) comparator levels)))

(defn- sorted-normalized-levels [levels comparator]
  (sort-levels (normalize-levels levels) comparator))

(defn- strip-normalized-levels [levels]
  (mapv strip-normalized-level levels))

(defn- take-leading-levels [levels limit*]
  (if (> (count levels) limit*)
    (subvec levels 0 limit*)
    levels))

(defn sort-display-asks [asks]
  (sort-levels (normalize-levels asks) <))

(defn sort-bids [bids]
  (strip-normalized-levels
   (sorted-normalized-levels bids >)))

(defn sort-asks [asks]
  ;; Keep legacy sort direction for compatibility with existing consumers.
  (strip-normalized-levels
   (sorted-normalized-levels asks >)))

(defn format-price
  ([price] (fmt/format-trade-price-plain price price))
  ([price raw] (fmt/format-trade-price-plain price raw)))

(defn format-percent [value decimals]
  (when-some [num-value (parse-number value)]
    (fmt/format-intl-number num-value
                            {:minimumFractionDigits decimals
                             :maximumFractionDigits decimals})))

(defn- normalize-total-decimals [decimals]
  (if (some? decimals) decimals 0))

(defn format-total [total & {:keys [decimals]}]
  (when-some [num-total (parse-number total)]
    (fmt/format-intl-number num-total
                            {:maximumFractionDigits (normalize-total-decimals decimals)})))

(defn calculate-cumulative-totals [orders]
  (if (empty? orders)
    []
    (loop [remaining orders
           cum-size 0
           cum-value 0
           result []]
      (if (empty? remaining)
        result
        (let [order (first remaining)
              price (or (level-price order) 0)
              size (or (level-size order) 0)
              new-cum-size (+ cum-size size)
              new-cum-value (+ cum-value (* price size))
              updated-order (assoc order
                                   :cum-size new-cum-size
                                   :cum-value new-cum-value)]
          (recur (rest remaining)
                 new-cum-size
                 new-cum-value
                 (conj result updated-order)))))))

(defn calculate-spread [best-bid best-ask]
  (when (and best-bid best-ask)
    (let [bid-price (level-price best-bid)
          ask-price (level-price best-ask)]
      (when (and (number? bid-price) (number? ask-price))
        (let [spread-abs (- ask-price bid-price)
              spread-pct (* (/ spread-abs ask-price) 100)]
          {:absolute spread-abs
           :percentage spread-pct})))))

(defn order-size-for-unit [order size-unit]
  (let [price (or (:px-num order) (level-price order) 0)
        size (or (:sz-num order) (level-size order) 0)]
    (if (= size-unit :quote)
      (* price size)
      size)))

(defn order-total-for-unit [order size-unit]
  (if (= size-unit :quote)
    (:cum-value order)
    (:cum-size order)))

(defn get-max-cumulative-total [orders size-unit]
  (when (seq orders)
    (apply max (map #(or (order-total-for-unit % size-unit) 0) orders))))

(defn cumulative-bar-width [cum-size max-cum-size]
  (when (and cum-size max-cum-size (> max-cum-size 0))
    (* (/ cum-size max-cum-size) 100)))

(defn cumulative-bar-width-style [cum-size max-cum-size]
  (str (or (cumulative-bar-width cum-size max-cum-size) 0) "%"))

(defn format-order-size [order size-unit]
  (if (= size-unit :quote)
    (or (format-total (order-size-for-unit order size-unit) :decimals 0) "0")
    (let [raw-size (:sz order)
          size (or (:sz-num order)
                   (level-size order))]
      (if (string? raw-size)
        raw-size
        (or (format-total size :decimals 8) "0")))))

(defn format-order-total [order size-unit]
  (if (= size-unit :quote)
    (or (format-total (:cum-value order) :decimals 0) "0")
    (or (format-total (:cum-size order) :decimals 8) "0")))

(defn- normalized-depth-limit [max-levels]
  (if (and (number? max-levels) (pos? max-levels))
    (int max-levels)
    default-max-render-levels-per-side))

(defn- normalize-visible-branch [visible-branch]
  (if (contains? #{:desktop :mobile} visible-branch)
    visible-branch
    :all))

(defn- build-spread-display [best-bid best-ask]
  (when-let [spread (calculate-spread best-bid best-ask)]
    (assoc spread
           :absolute-label (or (format-price (:absolute spread)) "0.00")
           :percentage-label (str (or (format-percent (:percentage spread) 3) "0.000")
                                  "%"))))

(defn- build-max-total-by-unit [bids-with-totals asks-with-totals]
  {:base (max (or (get-max-cumulative-total asks-with-totals :base) 0)
              (or (get-max-cumulative-total bids-with-totals :base) 0))
   :quote (max (or (get-max-cumulative-total asks-with-totals :quote) 0)
               (or (get-max-cumulative-total bids-with-totals :quote) 0))})

(defn- build-display-values [order max-total-by-unit]
  {:price (or (format-price (:px order) (:px order)) "0.00")
   :size {:base (format-order-size order :base)
          :quote (format-order-size order :quote)}
   :total {:base (format-order-total order :base)
           :quote (format-order-total order :quote)}
   :bar-width {:base (cumulative-bar-width-style (order-total-for-unit order :base)
                                                 (:base max-total-by-unit))
               :quote (cumulative-bar-width-style (order-total-for-unit order :quote)
                                                  (:quote max-total-by-unit))}})

(defn- build-render-row [side order max-total-by-unit]
  (assoc order
         :side side
         :row-key (str (name side) "-" (:px order))
         :display (build-display-values order max-total-by-unit)))

(defn- build-mobile-pairs [bid-rows ask-rows]
  (let [visible-bids (vec (take default-mobile-render-levels-per-side bid-rows))
        visible-asks (vec (take default-mobile-render-levels-per-side ask-rows))
        row-count (max (count visible-bids)
                       (count visible-asks))]
    (mapv (fn [idx]
            (let [bid (get visible-bids idx)
                  ask (get visible-asks idx)]
              {:bid bid
               :ask ask
               :row-key (str "mobile-split-row-"
                             (or (:px bid) "bid-empty")
                             "-"
                             (or (:px ask) "ask-empty"))}))
          (range row-count))))

(defn- build-common-render-snapshot* [sorted-bids sorted-asks limit*]
  (let [display-bids-limited (take-leading-levels sorted-bids limit*)
        display-asks-limited (into [] (take limit* (rseq sorted-asks)))
        bids-with-totals (calculate-cumulative-totals display-bids-limited)
        asks-with-totals (calculate-cumulative-totals display-asks-limited)
        best-bid (first sorted-bids)
        best-ask (peek sorted-asks)
        max-total-by-unit (build-max-total-by-unit bids-with-totals asks-with-totals)]
    {:display-bids display-bids-limited
     :display-asks display-asks-limited
     :bids-with-totals bids-with-totals
     :asks-with-totals asks-with-totals
     :best-bid best-bid
     :best-ask best-ask
     :spread (build-spread-display best-bid best-ask)
     :max-total-by-unit max-total-by-unit}))

(defn- build-render-snapshot* [sorted-bids sorted-asks limit* visible-branch]
  (let [visible-branch* (normalize-visible-branch visible-branch)
        common-snapshot (build-common-render-snapshot* sorted-bids sorted-asks limit*)
        bids-with-totals (:bids-with-totals common-snapshot)
        asks-with-totals (:asks-with-totals common-snapshot)
        max-total-by-unit (:max-total-by-unit common-snapshot)
        bid-rows (mapv #(build-render-row :bid % max-total-by-unit) bids-with-totals)
        ask-rows (mapv #(build-render-row :ask % max-total-by-unit) asks-with-totals)]
    (cond-> common-snapshot
      (not= visible-branch* :mobile)
      (assoc :desktop-bids bid-rows
             :desktop-asks (vec (reverse ask-rows)))

      (not= visible-branch* :desktop)
      (assoc :mobile-pairs (build-mobile-pairs bid-rows ask-rows)))))

(defn build-render-snapshot
  ([bids asks]
   (build-render-snapshot bids asks default-max-render-levels-per-side))
  ([bids asks max-levels]
   (build-render-snapshot bids asks max-levels nil))
  ([bids asks max-levels opts]
   (let [limit* (normalized-depth-limit max-levels)
         sorted-bids (sorted-normalized-levels bids >)
         sorted-asks (sorted-normalized-levels asks >)
         visible-branch (:visible-branch opts)]
     (build-render-snapshot* sorted-bids sorted-asks limit* visible-branch))))

(defn build-book
  ([bids asks]
   (build-book bids asks default-max-render-levels-per-side))
  ([bids asks max-levels]
   (let [limit* (normalized-depth-limit max-levels)
         sorted-bids (sorted-normalized-levels bids >)
         sorted-asks (sorted-normalized-levels asks >)]
     {:bids (strip-normalized-levels sorted-bids)
      :asks (strip-normalized-levels sorted-asks)
      :render (build-common-render-snapshot* sorted-bids sorted-asks limit*)})))

(defn same-render-book?
  [left right]
  (= (dissoc (or left {}) :timestamp)
     (dissoc (or right {}) :timestamp)))

(defn normalize-aggregation-config [aggregation-config]
  (let [n-sig-figs (:nSigFigs aggregation-config)]
    (cond-> {}
      (contains? #{2 3 4 5} n-sig-figs) (assoc :nSigFigs n-sig-figs))))

(defn build-subscription [symbol aggregation-config]
  (merge {:type "l2Book"
          :coin symbol}
         (normalize-aggregation-config aggregation-config)))
