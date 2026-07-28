(ns hyperopen.security.memory-only-agent-edge-cases-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.wallet.agent-lockbox :as agent-lockbox]
            [hyperopen.wallet.agent-runtime :as agent-runtime]
            [hyperopen.wallet.agent-session :as agent-session]
            [hyperopen.wallet.core :as wallet]))

(def wallet-address "0x1234567890abcdef1234567890abcdef12345678")

(defn- fake-storage []
  (let [values (atom {})]
    #js {:getItem (fn [key] (get @values (str key)))
         :setItem (fn [key value] (swap! values assoc (str key) (str value)))
         :removeItem (fn [key] (swap! values dissoc (str key)))}))

(deftest legacy-session-record-is-removed-and-never-cached-test
  (let [original-local (.-localStorage js/globalThis)
        original-session (.-sessionStorage js/globalThis)
        local (fake-storage)
        session (fake-storage)
        storage-key (agent-session/session-storage-key wallet-address)
        legacy {:agent-address "0x9999999999999999999999999999999999999999"
                :private-key "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]
    (set! (.-localStorage js/globalThis) local)
    (set! (.-sessionStorage js/globalThis) session)
    (try
      (.setItem session storage-key (js/JSON.stringify (clj->js legacy)))
      (is (= :legacy-session-raw
             (:persisted-kind
              (agent-session/load-persisted-agent-session-snapshot
               wallet-address :session :plain))))
      (is (nil? (.getItem session storage-key)))
      (is (nil? (agent-lockbox/load-unlocked-session wallet-address)))
      (finally
        (agent-lockbox/clear-all-unlocked-sessions!)
        (set! (.-localStorage js/globalThis) original-local)
        (set! (.-sessionStorage js/globalThis) original-session)))))

(deftest account-switch-disconnect-and-enable-failure-clear-memory-test
  (async done
    (let [new-wallet "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
          store (atom {:wallet {:connected? true
                                :address wallet-address
                                :agent {:status :ready
                                        :storage-mode :session
                                        :local-protection-mode :plain}}})]
      (agent-lockbox/cache-unlocked-session! wallet-address {:private-key "old"})
      (agent-lockbox/cache-unlocked-session!
       new-wallet
       {:agent-address "0x8888888888888888888888888888888888888888"
        :private-key "new"})
      (wallet/set-connected! store new-wallet)
      (is (nil? (agent-lockbox/load-unlocked-session wallet-address)))
      (is (= :ready (get-in @store [:wallet :agent :status])))
      (wallet/set-disconnected! store)
      (is (nil? (agent-lockbox/load-unlocked-session new-wallet)))
      (is (= :not-ready (get-in @store [:wallet :agent :status])))
      (swap! store assoc-in [:wallet :address] wallet-address)
      (swap! store assoc-in [:wallet :connected?] true)
      (agent-lockbox/cache-unlocked-session! wallet-address {:private-key "stale"})
      (-> (agent-runtime/enable-agent-trading!
           {:store store
            :options {:storage-mode :session :local-protection-mode :plain}
            :create-agent-credentials! (fn [] {:private-key "replacement"
                                                :agent-address "0x9999999999999999999999999999999999999999"})
            :now-ms-fn (constantly 1700000000000)
            :normalize-storage-mode identity
            :normalize-local-protection-mode identity
            :default-signature-chain-id-for-environment (constantly "0xa4b1")
            :build-approve-agent-action (fn [& _] {:type "approveAgent"})
            :approve-agent! (fn [& _]
                              (js/Promise.resolve
                               #js {:json (fn []
                                            (js/Promise.resolve
                                             #js {:status "err" :error "rejected"}))}))
            :persist-agent-session-by-mode! (constantly false)
            :clear-agent-session-by-mode! agent-session/clear-agent-session-by-mode!
            :cache-unlocked-session! agent-lockbox/cache-unlocked-session!
            :clear-unlocked-session! agent-lockbox/clear-unlocked-session!
            :runtime-error-message str
            :exchange-response-error (fn [_] "rejected")})
          (.then (fn [_]
                   (is (= :error (get-in @store [:wallet :agent :status])))
                   (is (nil? (agent-lockbox/load-unlocked-session wallet-address)))))
          (.catch (fn [error]
                    (is false (str "Unexpected error: " error))))
          (.finally (fn []
                      (agent-lockbox/clear-all-unlocked-sessions!)
                      (done)))))))
