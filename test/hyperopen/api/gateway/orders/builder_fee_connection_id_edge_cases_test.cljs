(ns hyperopen.api.gateway.orders.builder-fee-connection-id-edge-cases-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.builder-fee.policy :as policy]
            [hyperopen.utils.hl-signing :as signing]))

(def owner "0x1111111111111111111111111111111111111111")
(def builder "0x36a47878219fb346e031f6cf82cbfc8c77e35932")
(def nonce 1700000007777)
(def baseline-action
  (array-map :type "order"
             :orders [(array-map :a 5 :b true :p "100" :s "1" :r false
                                 :t {:limit {:tif "Gtc"}})]
             :grouping "na"))

(defn configured [fee]
  {:status :configured
   :builder-address builder
   :fee-tenths-bp fee
   :disclosure "A disclosed builder fee."})

(defn approved [fee]
  {:status :ready
   :owner-address owner
   :builder-address builder
   :network :testnet
   :max-builder-fee fee})

(deftest eligible-builder-actions-pin-root-ordering-nested-order-preservation-and-connection-ids-test
  (doseq [[fee expected-connection-id]
          [[1 "0xd5be1f28a68486580180337254b49a8dfcb31dd63d2a11d357ee6ebe80b3d53c"]
           [10 "0x3ed2be894f3d5832d1ff3dd1cdb3ff448df7c1d0247cc20f6d308813876d0740"]
           [100 "0x2fb211205c32a63606775690dda6ee699dea227428cb9c908911a1d6ee120ee0"]]]
    (testing (str "f=" fee)
      (let [decorated (:action (policy/policy-decision (configured fee)
                                                       (approved fee)
                                                       owner owner :perp baseline-action :buy))]
        (is (= [:type :orders :grouping :builder] (keys decorated)))
        (is (= [:b :f] (keys (:builder decorated))))
        (is (= (:orders baseline-action) (:orders decorated)))
        (is (= expected-connection-id (signing/compute-connection-id decorated nonce)))))))

(deftest inactive-and-already-decorated-actions-keep-their-original-connection-id-test
  (let [baseline-id (signing/compute-connection-id baseline-action nonce)
        inactive (:action (policy/policy-decision (configured 10)
                                                  (assoc (approved 10) :max-builder-fee 9)
                                                  owner owner :perp baseline-action :buy))
        preexisting (assoc baseline-action :builder (array-map :b builder :f 10))
        preserved (:action (policy/policy-decision (configured 10)
                                                   (approved 10)
                                                   owner owner :perp preexisting :buy))]
    (is (identical? baseline-action inactive))
    (is (= baseline-id (signing/compute-connection-id inactive nonce)))
    (is (identical? preexisting preserved))
    (is (= (signing/compute-connection-id preexisting nonce)
           (signing/compute-connection-id preserved nonce)))))
