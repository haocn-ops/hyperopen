(ns hyperopen.funding.infrastructure.wallet-rpc
  (:require [clojure.string :as str]))

(defn provider-request!
  [provider method & [params]]
  (if-not provider
    (js/Promise.reject (js/Error. "No wallet provider found. Connect your wallet first."))
    (.request provider
              (clj->js (cond-> {:method method}
                         (some? params) (assoc :params params))))))

(defn- normalize-chain-id
  [value]
  (let [raw (some-> value str str/trim)]
    (when (seq raw)
      (let [hex? (str/starts-with? raw "0x")
            source (if hex? (subs raw 2) raw)
            base (if hex? 16 10)
            parsed (js/parseInt source base)]
        (when (and (number? parsed)
                   (not (js/isNaN parsed)))
          (str "0x" (.toString (js/Math.floor parsed) 16)))))))

(defn- wallet-add-chain-params
  [{:keys [chain-id chain-name rpc-url explorer-url]}]
  {:chainId chain-id
   :chainName chain-name
   :nativeCurrency {:name "Ether"
                    :symbol "ETH"
                    :decimals 18}
   :rpcUrls [rpc-url]
   :blockExplorerUrls [explorer-url]})

(defn ensure-wallet-chain!
  [provider chain-config]
  (let [target-chain-id (:chain-id chain-config)]
    (-> (provider-request! provider "eth_chainId")
        (.then (fn [current-chain-id]
                 (if (= (normalize-chain-id current-chain-id) target-chain-id)
                   (js/Promise.resolve target-chain-id)
                   (-> (provider-request! provider
                                           "wallet_switchEthereumChain"
                                           [{:chainId target-chain-id}])
                       (.catch (fn [err]
                                 (let [code (or (some-> err .-code)
                                                (some-> err (aget "code")))]
                                   (if (= code 4902)
                                     (-> (provider-request! provider
                                                             "wallet_addEthereumChain"
                                                             [(wallet-add-chain-params chain-config)])
                                         (.then (fn [_]
                                                  (provider-request! provider
                                                                     "wallet_switchEthereumChain"
                                                                     [{:chainId target-chain-id}])))
                                         (.then (fn [_]
                                                  target-chain-id)))
                                     (js/Promise.reject err))))))))))))

(defn wait-for-transaction-receipt!
  [provider tx-hash]
  (let [poll-ms 1200
        timeout-ms 120000
        started-at (js/Date.now)]
    (js/Promise.
     (fn [resolve reject]
       (letfn [(poll []
                 (-> (provider-request! provider "eth_getTransactionReceipt" [tx-hash])
                     (.then (fn [receipt]
                              (if receipt
                                (let [status (-> (or (aget receipt "status") "")
                                                 str
                                                 str/lower-case)]
                                  (if (= status "0x1")
                                    (resolve receipt)
                                    (reject (js/Error. "Deposit transaction reverted on-chain."))))
                                (if (> (- (js/Date.now) started-at) timeout-ms)
                                  (reject (js/Error. "Timed out waiting for deposit confirmation."))
                                  (js/setTimeout poll poll-ms)))))
                     (.catch reject)))]
         (poll))))))

(defn send-and-confirm-evm-transaction!
  [provider from-address {:keys [to data value]}]
  (let [tx (cond-> {:from from-address
                    :to to
                    :data data}
             (seq value) (assoc :value value))]
    (-> (provider-request! provider
                           "eth_sendTransaction"
                           [tx])
        (.then (fn [tx-hash]
                 (-> (wait-for-transaction-receipt! provider tx-hash)
                     (.then (fn [_]
                              tx-hash))))))))
