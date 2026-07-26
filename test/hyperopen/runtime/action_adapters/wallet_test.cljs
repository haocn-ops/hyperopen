(ns hyperopen.runtime.action-adapters.wallet-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [nexus.registry :as nxr]
            [hyperopen.config :as app-config]
            [hyperopen.platform :as platform]
            [hyperopen.runtime.action-adapters.wallet :as wallet-adapters]
            [hyperopen.trading-crypto-modules :as trading-crypto-modules]
            [hyperopen.wallet.agent-runtime :as agent-runtime]
            [hyperopen.wallet.connection-runtime :as wallet-connection-runtime]))

(deftest enable-agent-trading-injects-platform-now-ms-fn-test
  (let [captured-now-ms (atom nil)]
    (with-redefs [platform/now-ms (fn [] 4242)
                  agent-runtime/enable-agent-trading!
                  (fn [{:keys [now-ms-fn]}]
                    (reset! captured-now-ms (now-ms-fn))
                    nil)]
      (wallet-adapters/enable-agent-trading nil (atom {}) {}))
    (is (= 4242 @captured-now-ms))))

(deftest enable-agent-trading-derives-network-defaults-while-preserving-explicit-options-test
  (let [captured-options (atom [])]
    (with-redefs [app-config/config {:hyperliquid {:is-mainnet false
                                                    :signature-chain-id "0x66eee"}}
                  trading-crypto-modules/resolved-trading-crypto
                  (fn [] {:create-agent-credentials! (fn [] {})})
                  agent-runtime/enable-agent-trading!
                  (fn [{:keys [options]}]
                    (swap! captured-options conj options)
                    :enabled)]
      (is (= :enabled
             (wallet-adapters/enable-agent-trading nil (atom {}) {})))
      (is (= :enabled
             (wallet-adapters/enable-agent-trading nil
                                                   (atom {})
                                                   {:is-mainnet true
                                                    :signature-chain-id "0xa4b1"}))))
    (is (= [false true]
           (mapv :is-mainnet @captured-options)))
    (is (= ["0x66eee" "0xa4b1"]
           (mapv :signature-chain-id @captured-options)))))

(deftest unlock-agent-trading-action-forwards-continuation-payload-test
  (let [state {:wallet {:connected? true
                        :address "0xabc"}}
        payload {:after-success-actions [[:actions/submit-unlocked-order-request
                                          {:action {:type "order"}}]]}]
    (is (= [[:effects/save-many [[[:wallet :agent :status] :unlocking]
                                  [[:wallet :agent :error] nil]]]
            [:effects/unlock-agent-trading payload]]
           (wallet-adapters/unlock-agent-trading-action state payload)))))

(deftest handle-wallet-connected-refreshes-vault-route-when-active-test
  (let [dispatch-calls (atom [])]
    (with-redefs [wallet-connection-runtime/handle-wallet-connected!
                  (fn [_]
                    :handled)
                  nxr/dispatch (fn [store _ctx effects]
                                 (swap! dispatch-calls conj [store effects]))]
      (let [store (atom {:router {:path "/vaults/0x1234567890abcdef1234567890abcdef12345678"}})
            result (wallet-adapters/handle-wallet-connected store "0xabc")]
        (is (= :handled result))
        (is (= [[store [[:effects/record-attribution-event
                         :wallet-connected
                         {:wallet/address "0xabc" :outcome :observed}]]]
                [store [[:actions/load-vault-route "/vaults/0x1234567890abcdef1234567890abcdef12345678"]]]]
               @dispatch-calls))))))

(deftest handle-wallet-connected-refreshes-staking-route-when-active-test
  (let [dispatch-calls (atom [])]
    (with-redefs [wallet-connection-runtime/handle-wallet-connected!
                  (fn [_]
                    :handled)
                  nxr/dispatch (fn [store _ctx effects]
                                 (swap! dispatch-calls conj [store effects]))]
      (let [store (atom {:router {:path "/staking"}})
            result (wallet-adapters/handle-wallet-connected store "0xabc")]
        (is (= :handled result))
        (is (= [[store [[:effects/record-attribution-event
                         :wallet-connected
                         {:wallet/address "0xabc" :outcome :observed}]]]
                [store [[:actions/load-staking-route "/staking"]]]]
               @dispatch-calls))))))

(deftest handle-wallet-connected-refreshes-portfolio-optimizer-route-when-active-test
  (let [dispatch-calls (atom [])]
    (with-redefs [wallet-connection-runtime/handle-wallet-connected!
                  (fn [_]
                    :handled)
                  nxr/dispatch (fn [store _ctx effects]
                                 (swap! dispatch-calls conj [store effects]))]
      (let [store (atom {:router {:path "/portfolio/optimize"}})
            result (wallet-adapters/handle-wallet-connected
                    store
                    "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")]
        (is (= :handled result))
        (is (= [[store [[:effects/record-attribution-event
                         :wallet-connected
                         {:wallet/address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                          :outcome :observed}]]]
                [store [[:actions/load-portfolio-optimizer-route
                         "/portfolio/optimize"]]]]
               @dispatch-calls))))))

(deftest enable-agent-trading-loads-crypto-module-before-delegating-test
  (async done
    (let [captured-creds (atom nil)
          expected-creds {:private-key "0xabc"
                          :agent-address "0xdef"}
          original-resolved trading-crypto-modules/resolved-trading-crypto
          original-load trading-crypto-modules/load-trading-crypto-module!
          original-enable agent-runtime/enable-agent-trading!]
      (set! trading-crypto-modules/resolved-trading-crypto (fn [] nil))
      (set! trading-crypto-modules/load-trading-crypto-module!
            (fn []
              (js/Promise.resolve
               {:create-agent-credentials! (fn []
                                            expected-creds)})))
      (set! agent-runtime/enable-agent-trading!
            (fn [{:keys [create-agent-credentials!]}]
              (reset! captured-creds (create-agent-credentials!))
              :enabled))
      (-> (wallet-adapters/enable-agent-trading nil (atom {:wallet {:address "0xabc"}}) {})
          (.then (fn [result]
                   (is (= :enabled result))
                   (is (= expected-creds @captured-creds))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))
          (.finally (fn []
                      (set! trading-crypto-modules/resolved-trading-crypto original-resolved)
                      (set! trading-crypto-modules/load-trading-crypto-module! original-load)
                      (set! agent-runtime/enable-agent-trading! original-enable)))))))
