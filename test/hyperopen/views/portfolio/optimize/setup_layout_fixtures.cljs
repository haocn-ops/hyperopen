(ns hyperopen.views.portfolio.optimize.setup-layout-fixtures)

(defn node-children
  [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn find-first-node
  [node pred]
  (cond
    (vector? node)
    (let [children (node-children node)]
      (or (when (pred node) node)
          (some #(find-first-node % pred) children)))

    (seq? node)
    (some #(find-first-node % pred) node)

    :else nil))

(defn collect-strings
  [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings (node-children node))
    (seq? node) (mapcat collect-strings node)
    :else []))

(defn node-by-role
  [node role]
  (find-first-node node #(= role (get-in % [1 :data-role]))))

(defn child-roles
  [node]
  (->> (node-children node)
       (keep #(get-in % [1 :data-role]))
       vec))

(defn node-text
  [node]
  (apply str (collect-strings node)))

(defn click-actions
  [node]
  (get-in node [1 :on :click]))

(defn input-actions
  [node]
  (get-in node [1 :on :input]))

(defn change-actions
  [node]
  (get-in node [1 :on :change]))

(defn keydown-actions
  [node]
  (get-in node [1 :on :keydown]))

(defn day-start-ms
  [day]
  (.getTime (js/Date. (str day "T00:00:00.000Z"))))

(defn summary-from-points
  [points]
  {:accountValueHistory (mapv (fn [[time-ms account-value _pnl-value]]
                                [time-ms account-value])
                              points)
   :pnlHistory (mapv (fn [[time-ms _account-value pnl-value]]
                       [time-ms pnl-value])
                     points)})

(defn class-token-set
  [node]
  (set (get-in node [1 :class])))

(defn count-nodes
  [node pred]
  (cond
    (vector? node)
    (let [children (node-children node)]
      (+ (if (pred node) 1 0)
         (reduce + 0 (map #(count-nodes % pred) children))))

    (seq? node)
    (reduce + 0 (map #(count-nodes % pred) node))

    :else 0))

(def btc-instrument
  {:instrument-id "perp:BTC"
   :market-type :perp
   :coin "BTC"
   :symbol "BTC-USDC"
   :name "Bitcoin"})

(def eth-instrument
  {:instrument-id "perp:ETH"
   :market-type :perp
   :coin "ETH"
   :symbol "ETH-USDC"
   :name "Ethereum"})

(defn black-litterman-ready-readiness
  []
  {:status :ready
   :runnable? true
   :warnings []
   :blocking-warnings []
   :request {:universe [btc-instrument
                        eth-instrument]
             :return-model {:kind :black-litterman
                            :views [{:id "view-1"
                                     :kind :relative
                                     :instrument-id "perp:BTC"
                                     :comparator-instrument-id "perp:ETH"
                                     :direction :outperform
                                     :return 0.1
                                     :confidence 0.5
                                     :confidence-variance 1
                                     :weights {"perp:BTC" 1
                                               "perp:ETH" -1}}]}
             :risk-model {:kind :sample-covariance}
             :periods-per-year 10
             :history {:return-series-by-instrument
                       {"perp:BTC" [0.01 0.03 0.02]
                        "perp:ETH" [0.04 0.01 0.04]}}
             :black-litterman-prior
             {:source :market-cap
              :weights-by-instrument {"perp:BTC" 0.6
                                      "perp:ETH" 0.4}}}})

(defn black-litterman-ready-draft
  []
  {:universe [btc-instrument
              eth-instrument]
   :objective {:kind :max-sharpe}
   :return-model {:kind :black-litterman
                  :views [{:id "view-1"
                           :kind :relative
                           :instrument-id "perp:BTC"
                           :comparator-instrument-id "perp:ETH"
                           :direction :outperform
                           :return 0.1
                           :confidence 0.5
                           :confidence-variance 1
                           :weights {"perp:BTC" 1
                                     "perp:ETH" -1}}]}
   :risk-model {:kind :sample-covariance}
   :constraints {:long-only? false
                 :max-asset-weight 0.4
                 :gross-max 1.5}})

(defn black-litterman-empty-readiness
  []
  (assoc-in (black-litterman-ready-readiness)
            [:request :return-model :views]
            []))

(defn black-litterman-empty-draft
  []
  (assoc-in (black-litterman-ready-draft)
            [:return-model :views]
            []))

(defn candle-rows
  [time-and-close-pairs]
  (mapv (fn [[time-ms close]]
          {:time time-ms
           :close (str close)})
        time-and-close-pairs))
