(ns hyperopen.funding.application.deposit-submit-error-feedback-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.funding.application.deposit-submit :as deposit-submit]
            [hyperopen.funding.application.submit-effects :as effects]
            [hyperopen.funding.effects.common :as common]
            [hyperopen.funding.test-support.effects :as effects-support]
            [hyperopen.test-support.async :as async-support]))

(defn- submit-deps
  [overrides]
  (merge (effects-support/base-submit-effect-deps) overrides))

(deftest submit-usdc-bridge2-deposit-preflights-current-token-balance-test
  (async done
    (let [provider-calls (atom [])
          testnet-config {:chain-id "0x66eee"
                          :network-label "Arbitrum Sepolia"
                          :usdc-address "0x1baAbB04529D43a73232B713C0FE471f7c7334d5"
                          :bridge-address "0x08cfc1B6b2dCF36A1480b99353A354AA8AC56f89"}]
      (-> (deposit-submit/submit-usdc-bridge2-deposit-tx!
           {:wallet-provider-fn (fn [] :provider)
            :normalize-address identity
            :resolve-deposit-chain-config (fn [_store _action] testnet-config)
            :parse-usdc-units (fn [_amount] (js/BigInt "5000000"))
            :ensure-wallet-chain! (fn [_provider _config] (js/Promise.resolve "0x66eee"))
            :read-erc20-balance-units! (fn [provider token-address owner-address]
                                         (is (= :provider provider))
                                         (is (= (:usdc-address testnet-config) token-address))
                                         (is (= "0xowner" owner-address))
                                         (js/Promise.resolve (js/BigInt "4000000")))
            :provider-request! (fn [& args]
                                 (swap! provider-calls conj args)
                                 (js/Promise.resolve "0xtx"))
            :wait-for-transaction-receipt! (fn [& _]
                                             (js/Promise.resolve {:status "ok"}))
            :encode-erc20-transfer-call-data (fn [& _] "0xtransfer")
            :deposit-wallet-error-feedback common/deposit-wallet-error-feedback}
           (atom {})
           "0xowner"
           {:amount "5" :chainId "0x66eee"})
          (.then (fn [resp]
                   (is (= "err" (:status resp)))
                   (is (= :insufficient-usdc2 (get-in resp [:error-feedback :kind])))
                   (is (empty? @provider-calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest submit-usdc-bridge2-deposit-preflight-preserves-single-send-success-paths-test
  (async done
    (let [provider-calls (atom [])
          receipt-calls (atom [])
          make-deps (fn [balance-result]
                      {:wallet-provider-fn (fn [] :provider)
                       :normalize-address identity
                       :resolve-deposit-chain-config
                       (fn [_store _action]
                         {:network-label "Arbitrum Sepolia"
                          :usdc-address "0xusdc2"
                          :bridge-address "0xbridge2"})
                       :parse-usdc-units (fn [_amount] (js/BigInt "5000000"))
                       :ensure-wallet-chain! (fn [& _] (js/Promise.resolve "0x66eee"))
                       :read-erc20-balance-units! (fn [& _] balance-result)
                       :provider-request! (fn [_provider method _params]
                                            (swap! provider-calls conj method)
                                            (js/Promise.resolve "0xtx"))
                       :wait-for-transaction-receipt! (fn [_provider tx-hash]
                                                        (swap! receipt-calls conj tx-hash)
                                                        (js/Promise.resolve {:status "ok"}))
                       :encode-erc20-transfer-call-data (fn [& _] "0xtransfer")
                       :wallet-error-message (fn [err] (str err))
                       :deposit-wallet-error-feedback common/deposit-wallet-error-feedback})
          submit! (fn [balance-result]
                    (deposit-submit/submit-usdc-bridge2-deposit-tx!
                     (make-deps balance-result)
                     (atom {})
                     "0xowner"
                     {:amount "5" :chainId "0x66eee"}))]
      (-> (js/Promise.all
           #js[(submit! (js/Promise.resolve (js/BigInt "5000000")))
               (submit! (js/Promise.resolve nil))
               (submit! (js/Promise.resolve "not-a-bigint"))])
          (.then (fn [responses]
                   (let [responses* (js->clj responses :keywordize-keys true)]
                     (is (= ["ok" "ok" "ok"] (mapv :status responses*)))
                     (is (= ["eth_sendTransaction" "eth_sendTransaction" "eth_sendTransaction"]
                            @provider-calls))
                     (is (= ["0xtx" "0xtx" "0xtx"] @receipt-calls))
                     (done))))
          (.catch (async-support/unexpected-error done))))))

(deftest api-submit-funding-deposit-preserves-structured-wallet-feedback-test
  (async done
    (let [store (atom {:wallet {:address "0xabc"}
                       :funding-ui {:modal (effects-support/seed-modal :deposit)}})
          toasts (atom [])
          feedback {:kind :transaction-reverted
                    :message "The network rejected this USDC2 deposit before submission."
                    :toast {:headline "Deposit could not be submitted"
                            :subline "Check the Testnet wallet balances."
                            :detail "Confirm current USDC2 and Arbitrum Sepolia test ETH, then try again."
                            :auto-timeout? false}}]
      (-> (effects/api-submit-funding-deposit!
           (submit-deps
            {:store store
             :request {:action {:type "bridge2Deposit"
                                :asset "usdc"
                                :amount "5"
                                :chainId "0x66eee"}}
             :submit-usdc-bridge2-deposit! (fn [& _]
                                             (js/Promise.resolve
                                              {:status "err"
                                               :error (:message feedback)
                                               :error-feedback feedback}))
             :show-toast! (effects-support/capture-toast! toasts)}))
          (.then (fn [_]
                   (is (= false (get-in @store [:funding-ui :modal :submitting?])))
                   (is (= "Deposit failed: The network rejected this USDC2 deposit before submission."
                          (get-in @store [:funding-ui :modal :error])))
                   (is (= [[:error (:toast feedback)]] @toasts))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest submit-usdc-bridge2-deposit-keeps-mainnet-on-legacy-error-path-test
  (async done
    (let [balance-reads (atom 0)
          provider-calls (atom [])]
      (-> (deposit-submit/submit-usdc-bridge2-deposit-tx!
           {:wallet-provider-fn (fn [] :provider)
            :normalize-address identity
            :resolve-deposit-chain-config
            (fn [_store _action]
              {:chain-id "0xa4b1"
               :network-label "Arbitrum"
               :usdc-address "0xusdc"
               :bridge-address "0xbridge"})
            :parse-usdc-units (fn [_] (js/BigInt "5000000"))
            :ensure-wallet-chain! (fn [& _] (js/Promise.resolve "0xa4b1"))
            :read-erc20-balance-units! (fn [& _]
                                         (swap! balance-reads inc)
                                         (js/Promise.resolve (js/BigInt "0")))
            :provider-request! (fn [_provider method _params]
                                 (swap! provider-calls conj method)
                                 (if (= method "eth_sendTransaction")
                                   (js/Promise.reject (js/Error. "mainnet provider failure"))
                                   (js/Promise.resolve nil)))
            :wait-for-transaction-receipt! (fn [& _]
                                             (js/Promise.resolve {:status "ok"}))
            :encode-erc20-transfer-call-data (fn [& _] "0xtransfer")
            :wallet-error-message (fn [_] "Legacy provider failure")
            :deposit-wallet-error-feedback (fn [& _]
                                             (throw (js/Error. "Testnet classifier must not run on Mainnet")))}
           (atom {})
           "0xowner"
           {:amount "5" :chainId "0xa4b1"})
          (.then (fn [resp]
                   (is (= "err" (:status resp)))
                   (is (= "Legacy provider failure" (:error resp)))
                   (is (= 0 @balance-reads))
                   (is (= ["eth_sendTransaction"] @provider-calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest submit-usdc-bridge2-deposit-distinguishes-post-submit-revert-test
  (async done
    (-> (deposit-submit/submit-usdc-bridge2-deposit-tx!
         {:wallet-provider-fn (fn [] :provider)
          :normalize-address identity
          :resolve-deposit-chain-config
          (fn [_store _action]
            {:chain-id "0x66eee"
             :network-label "Arbitrum Sepolia"
             :usdc-address "0xusdc2"
             :bridge-address "0xbridge2"})
          :parse-usdc-units (fn [_] (js/BigInt "5000000"))
          :ensure-wallet-chain! (fn [& _] (js/Promise.resolve "0x66eee"))
          :read-erc20-balance-units! (fn [& _]
                                       (js/Promise.resolve (js/BigInt "6000000")))
          :provider-request! (fn [_provider method _params]
                               (if (= method "eth_sendTransaction")
                                 (js/Promise.resolve "0xtx")
                                 (js/Promise.resolve nil)))
          :wait-for-transaction-receipt! (fn [& _]
                                           (js/Promise.reject
                                            (js/Error. "Deposit transaction reverted on-chain.")))
          :encode-erc20-transfer-call-data (fn [& _] "0xtransfer")
          :wallet-error-message (fn [err] (str err))
          :deposit-wallet-error-feedback common/deposit-wallet-error-feedback}
         (atom {})
         "0xowner"
         {:amount "5" :chainId "0x66eee"})
        (.then (fn [resp]
                 (is (= "err" (:status resp)))
                 (is (= :transaction-reverted (get-in resp [:error-feedback :kind])))
                 (is (= "The USDC2 deposit transaction was submitted but reverted on-chain."
                        (:error resp)))
                 (is (not (re-find #"before submission" (:error resp))))
                 (done)))
        (.catch (async-support/unexpected-error done)))))
