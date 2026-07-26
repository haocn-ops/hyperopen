(ns hyperopen.funding.infrastructure.wallet-rpc-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.funding.infrastructure.wallet-rpc :as wallet-rpc]
            [hyperopen.test-support.async :as async-support]))

(defn- stepping-now-ms
  [values]
  (let [remaining (atom (vec values))
        last-value (atom (or (last values) 0))]
    (fn []
      (if-let [next-value (first @remaining)]
        (do
          (swap! remaining subvec 1)
          (reset! last-value next-value)
          next-value)
        @last-value))))

(deftest provider-request-forwards-method-and-optional-params-test
  (async done
    (let [calls (atom [])
          provider #js {:request (fn [payload]
                                   (swap! calls conj (js->clj payload :keywordize-keys true))
                                   (js/Promise.resolve "ok"))}]
      (-> (js/Promise.all
           #js [(wallet-rpc/provider-request! provider "eth_chainId")
                (wallet-rpc/provider-request! provider
                                              "eth_sendTransaction"
                                              [{:to "0x1"}])])
          (.then (fn [_]
                   (is (= [{:method "eth_chainId"}
                           {:method "eth_sendTransaction"
                            :params [{:to "0x1"}]}]
                          @calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest provider-request-rejects-when-provider-missing-test
  (async done
    (-> (wallet-rpc/provider-request! nil "eth_chainId")
        (.then (fn [_]
                 (is false "Expected missing provider to reject")))
        (.catch (fn [err]
                  (is (= "No wallet provider found. Connect your wallet first."
                         (.-message err)))
                  (done))))))

(deftest ensure-wallet-chain-short-circuits-when-wallet-already-on-target-chain-test
  (async done
    (let [calls (atom [])
          provider #js {:request (fn [payload]
                                   (let [request (js->clj payload :keywordize-keys true)]
                                     (swap! calls conj request)
                                     (js/Promise.resolve "10")))}]
      (-> (wallet-rpc/ensure-wallet-chain!
           provider
           {:chain-id "0xa"
            :chain-name "Optimism"
            :rpc-url "https://optimism.example"
            :explorer-url "https://explorer.optimism.example"})
          (.then (fn [result]
                   (is (= "0xa" result))
                   (is (= [{:method "eth_chainId"}]
                          @calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest ensure-wallet-chain-adds-missing-chain-before-switching-test
  (async done
    (let [calls (atom [])
          switch-attempts (atom 0)
          provider #js {:request (fn [payload]
                                   (let [request (js->clj payload :keywordize-keys true)]
                                     (swap! calls conj request)
                                     (case (:method request)
                                       "eth_chainId" (js/Promise.resolve "0x1")
                                       "wallet_switchEthereumChain"
                                       (if (= 1 (swap! switch-attempts inc))
                                         (js/Promise.reject #js {:code 4902
                                                                 :message "Unknown chain"})
                                         (js/Promise.resolve nil))
                                       "wallet_addEthereumChain" (js/Promise.resolve nil))))}]
      (-> (wallet-rpc/ensure-wallet-chain!
           provider
           {:chain-id "0xa4b1"
            :chain-name "Arbitrum One"
            :rpc-url "https://rpc.arbitrum.example"
            :explorer-url "https://arbiscan.example"})
          (.then (fn [result]
                   (is (= "0xa4b1" result))
                   (is (= [{:method "eth_chainId"}
                           {:method "wallet_switchEthereumChain"
                            :params [{:chainId "0xa4b1"}]}
                           {:method "wallet_addEthereumChain"
                            :params [{:chainId "0xa4b1"
                                      :chainName "Arbitrum One"
                                      :nativeCurrency {:name "Ether"
                                                       :symbol "ETH"
                                                       :decimals 18}
                                      :rpcUrls ["https://rpc.arbitrum.example"]
                                      :blockExplorerUrls ["https://arbiscan.example"]}]}
                           {:method "wallet_switchEthereumChain"
                            :params [{:chainId "0xa4b1"}]}]
                          @calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest ensure-wallet-chain-rejects-non-4902-switch-errors-test
  (async done
    (let [provider #js {:request (fn [payload]
                                   (let [request (js->clj payload :keywordize-keys true)]
                                     (case (:method request)
                                       "eth_chainId" (js/Promise.resolve "0x1")
                                       "wallet_switchEthereumChain" (js/Promise.reject #js {:code 4001
                                                                                             :message "User rejected"})
                                       (js/Promise.resolve nil))))}]
      (-> (wallet-rpc/ensure-wallet-chain!
           provider
           {:chain-id "0xa4b1"
            :chain-name "Arbitrum One"
            :rpc-url "https://rpc.arbitrum.example"
            :explorer-url "https://arbiscan.example"})
          (.then (fn [_]
                   (is false "Expected switch rejection to propagate")))
          (.catch (fn [err]
                    (is (= 4001
                           (or (.-code err)
                               (aget err "code"))))
                    (done)))))))

(deftest wait-for-transaction-receipt-polls-until-successful-confirmation-test
  (async done
    (let [original-set-timeout (.-setTimeout js/globalThis)
          calls (atom 0)
          provider #js {:request (fn [payload]
                                   (let [request (js->clj payload :keywordize-keys true)]
                                     (is (= {:method "eth_getTransactionReceipt"
                                             :params ["0xtx"]}
                                            request))
                                     (let [attempt (swap! calls inc)]
                                       (js/Promise.resolve
                                        (when (= attempt 2)
                                          #js {:status "0x1"
                                               :transactionHash "0xtx"})))))}
          restore! (fn []
                     (set! (.-setTimeout js/globalThis) original-set-timeout))
          fail! (fn [err]
                  (restore!)
                  ((async-support/unexpected-error done) err))]
      (set! (.-setTimeout js/globalThis) (fn [f _ms]
                                           (f)
                                           :timer-id))
      (-> (wallet-rpc/wait-for-transaction-receipt! provider "0xtx")
          (.then (fn [receipt]
                   (is (= 2 @calls))
                   (is (= "0xtx"
                          (aget receipt "transactionHash")))
                   (restore!)
                   (done)))
          (.catch fail!)))))

(deftest wait-for-transaction-receipt-times-out-when-receipt-never-arrives-test
  (async done
    (let [original-set-timeout (.-setTimeout js/globalThis)
          original-now (.-now js/Date)
          provider #js {:request (fn [payload]
                                   (let [request (js->clj payload :keywordize-keys true)]
                                     (is (= {:method "eth_getTransactionReceipt"
                                             :params ["0xtimeout"]}
                                            request))
                                     (js/Promise.resolve nil)))}
          restore! (fn []
                     (set! (.-setTimeout js/globalThis) original-set-timeout)
                     (set! (.-now js/Date) original-now))
          fail! (fn [err]
                  (restore!)
                  ((async-support/unexpected-error done) err))]
      (set! (.-setTimeout js/globalThis) (fn [f _ms]
                                           (f)
                                           :timer-id))
      (set! (.-now js/Date) (stepping-now-ms [0 121001]))
      (-> (wallet-rpc/wait-for-transaction-receipt! provider "0xtimeout")
          (.then (fn [_]
                   (restore!)
                   (is false "Expected receipt polling to time out")
                   (done)))
          (.catch (fn [err]
                    (restore!)
                    (is (= "Timed out waiting for deposit confirmation."
                           (.-message err)))
                    (done)))
          (.catch fail!)))))

(deftest send-and-confirm-evm-transaction-builds-send-payloads-and-returns-hash-test
  (async done
    (let [provider-calls (atom [])
          receipt-calls (atom [])
          provider #js {:request (fn [payload]
                                   (let [request (js->clj payload :keywordize-keys true)]
                                     (swap! provider-calls conj request)
                                     (case (:method request)
                                       "eth_sendTransaction"
                                       (js/Promise.resolve
                                        (str "0xtx-"
                                             (count (filter #(= "eth_sendTransaction"
                                                                (:method %))
                                                            @provider-calls))))
                                       "eth_getTransactionReceipt"
                                       (do
                                         (swap! receipt-calls conj (first (:params request)))
                                         (js/Promise.resolve #js {:status "0x1"})))))}]
      (-> (js/Promise.all
           #js [(wallet-rpc/send-and-confirm-evm-transaction!
                 provider
                 "0xfrom"
                 {:to "0xto"
                  :data "0xabc"
                  :value "0x5"})
                (wallet-rpc/send-and-confirm-evm-transaction!
                 provider
                 "0xfrom"
                 {:to "0xto2"
                  :data "0xdef"
                  :value nil})])
          (.then (fn [results]
                   (is (= ["0xtx-1" "0xtx-2"]
                          (js->clj results)))
                   (is (= [{:method "eth_sendTransaction"
                            :params [{:from "0xfrom"
                                      :to "0xto"
                                      :data "0xabc"
                                      :value "0x5"}]}
                           {:method "eth_sendTransaction"
                            :params [{:from "0xfrom"
                                      :to "0xto2"
                                      :data "0xdef"}]}]
                          (filter #(= "eth_sendTransaction" (:method %))
                                  @provider-calls)))
                   (is (= ["0xtx-1" "0xtx-2"]
                          @receipt-calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))
