(ns hyperopen.security.memory-only-agent-acceptance-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.trading :as trading]
            [hyperopen.api.trading.http :as http]
            [hyperopen.trading-crypto-modules :as trading-crypto-modules]
            [hyperopen.wallet.agent-lockbox :as agent-lockbox]
            [hyperopen.wallet.agent-runtime :as agent-runtime]
            [hyperopen.wallet.agent-session :as agent-session]
            [hyperopen.wallet.core :as wallet]))

(def wallet-address "0x1234567890abcdef1234567890abcdef12345678")
(def session-record
  {:agent-address "0x9999999999999999999999999999999999999999"
   :private-key "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
   :last-approved-at 1700000000000
   :nonce-cursor 1700000000000})

(def ^:private baseline-persist-agent-session-by-mode!
  agent-session/persist-agent-session-by-mode!)

(defn- fake-storage []
  (let [values (atom {})]
    #js {:getItem (fn [key] (get @values (str key)))
         :setItem (fn [key value] (swap! values assoc (str key) (str value)))
         :removeItem (fn [key] (swap! values dissoc (str key)))}))

(deftest non-passkey-agent-enables-and-signs-only-in-the-current-process-test
  (async done
   (let [original-local (.-localStorage js/globalThis)
        original-session (.-sessionStorage js/globalThis)
        original-fetch (.-fetch js/globalThis)
        original-load-crypto trading-crypto-modules/load-trading-crypto-module!
        local (fake-storage)
        session (fake-storage)
        storage-key (agent-session/session-storage-key wallet-address)
        sign-calls (atom [])
        store (atom {:wallet {:connected? true
                              :address wallet-address
                              :chain-id "0xa4b1"
                              :agent {:status :approving
                                      :storage-mode :session
                                      :local-protection-mode :plain}}})
        crypto {:private-key->agent-address (constantly (:agent-address session-record))
                :sign-l1-action-with-private-key!
                (fn [private-key action nonce options]
                  (swap! sign-calls conj [private-key action nonce options])
                  (js/Promise.resolve #js {:r "0x01" :s "0x02" :v 27}))}]
    (set! (.-localStorage js/globalThis) local)
    (set! (.-sessionStorage js/globalThis) session)
    (set! trading-crypto-modules/load-trading-crypto-module!
          (fn [] (js/Promise.resolve crypto)))
    (set! (.-fetch js/globalThis)
          (fn [& _]
            (js/Promise.resolve
             #js {:ok true
                  :json (fn [] (js/Promise.resolve #js {:status "ok"}))})))
    (with-redefs [http/trading-enabled? (constantly true)]
      (-> (agent-runtime/enable-agent-trading!
           {:store store
            :options {:storage-mode :session :local-protection-mode :plain}
            :create-agent-credentials! (constantly session-record)
            :now-ms-fn (constantly 1700000000000)
            :normalize-storage-mode agent-session/normalize-storage-mode
            :normalize-local-protection-mode agent-session/normalize-local-protection-mode
            :default-signature-chain-id-for-environment (constantly "0xa4b1")
            :build-approve-agent-action (fn [agent-address nonce & _]
                                          {:agentAddress agent-address :nonce nonce})
            :approve-agent! (fn [& _]
                              (js/Promise.resolve
                               #js {:json (fn []
                                            (js/Promise.resolve #js {:status "ok"}))}))
            :persist-agent-session-by-mode! baseline-persist-agent-session-by-mode!
            :clear-agent-session-by-mode! agent-session/clear-agent-session-by-mode!
            :cache-unlocked-session! agent-lockbox/cache-unlocked-session!
            :clear-unlocked-session! agent-lockbox/clear-unlocked-session!
            :runtime-error-message str
            :exchange-response-error pr-str})
          (.then (fn [_]
                   (is (= :ready (get-in @store [:wallet :agent :status])))
                   (is (= (:agent-address session-record)
                          (get-in @store [:wallet :agent :agent-address])))
                   (is (nil? (.getItem local storage-key)))
                   (is (nil? (.getItem session storage-key)))
                   (trading/submit-order! store wallet-address
                                          {:type "order" :orders [] :grouping "na"})))
          (.then (fn [response]
                   (is (= "ok" (:status response)))
                   (is (= (:private-key session-record) (ffirst @sign-calls)))
                   (is (= (:agent-address session-record)
                          (:agent-address (agent-lockbox/load-unlocked-session wallet-address))))
                   (agent-lockbox/clear-unlocked-session! wallet-address)
                   (let [reloaded-store
                         (atom {:wallet {:connected? false
                                         :address nil
                                         :agent (agent-session/default-agent-state
                                                 :storage-mode :session
                                                 :local-protection-mode :plain)}})]
                     (wallet/set-connected! reloaded-store wallet-address)
                     (is (= :not-ready (get-in @reloaded-store [:wallet :agent :status])))
                     (agent-lockbox/cache-unlocked-session! wallet-address session-record)
                     (wallet/set-disconnected! reloaded-store)
                     (is (nil? (agent-lockbox/load-unlocked-session wallet-address))))
                   (is (nil? (.getItem local storage-key)))
                   (is (nil? (.getItem session storage-key)))))
          (.catch (fn [error]
                    (is false (str "Unexpected error: " error))))
          (.finally (fn []
                      (agent-lockbox/clear-all-unlocked-sessions!)
                      (set! trading-crypto-modules/load-trading-crypto-module! original-load-crypto)
                      (set! (.-fetch js/globalThis) original-fetch)
                      (set! (.-localStorage js/globalThis) original-local)
                      (set! (.-sessionStorage js/globalThis) original-session)
                      (done))))))))
