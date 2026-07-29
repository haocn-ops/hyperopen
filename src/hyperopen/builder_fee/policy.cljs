(ns hyperopen.builder-fee.policy
  (:require [clojure.string :as str]))

(def ^:private address-pattern #"^0x[0-9a-f]{40}$")

(defn- valid-address?
  [value]
  (and (string? value)
       (re-matches address-pattern value)))

(defn- configured-fee?
  [{:keys [status builder-address fee-tenths-bp disclosure]}]
  (and (= :configured status)
       (valid-address? builder-address)
       (integer? fee-tenths-bp)
       (<= 1 fee-tenths-bp 100)
       (string? disclosure)
       (seq (str/trim disclosure))))

(defn approved?
  [approval owner-address builder-address network fee-tenths-bp]
  (and (= :ready (:status approval))
       (= owner-address (:owner-address approval))
       (= builder-address (:builder-address approval))
       (= network (:network approval))
       (integer? (:max-builder-fee approval))
       (integer? fee-tenths-bp)
       (>= (:max-builder-fee approval) fee-tenths-bp)))

(defn- eligible-market?
  [market side]
  (or (= :perp market)
      (and (= :spot market) (= :sell side))))

(defn policy-decision
  "Return the original action unless the current main owner has an authoritative,
   sufficient approval for this configured fee. The active path appends the root
   builder map last, preserving Hyperliquid's signed order field ordering."
  ([config approval owner-address target-address market action side]
   (policy-decision config approval owner-address target-address
                    (:network approval) market action side))
  ([config approval owner-address target-address network market action side]
   (let [{:keys [builder-address fee-tenths-bp]} config
         active? (and (configured-fee? config)
                      (valid-address? owner-address)
                      (contains? #{:mainnet :testnet} network)
                      (= owner-address target-address)
                      (eligible-market? market side)
                      (= "order" (:type action))
                      (sequential? (:orders action))
                      (not (contains? action :builder))
                      (approved? approval owner-address builder-address network fee-tenths-bp))]
     (if active?
       {:active? true
        :reason :approved
        :action (assoc action :builder (array-map :b builder-address :f fee-tenths-bp))}
       {:active? false
        :reason :unapproved
        :action action}))))
