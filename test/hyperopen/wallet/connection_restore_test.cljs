(ns hyperopen.wallet.connection-restore-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.wallet.core :as wallet]))

(deftest check-connection-restores-authorized-account-from-non-default-provider-test
  (async done
    (let [store (atom {:wallet {:connected? false
                                :address nil
                                :connecting? false
                                :error nil
                                :agent {:status :not-ready
                                        :storage-mode :local}}})
          original-window (aget js/globalThis "window")
          original-global-provider (aget js/globalThis "ethereum")
          window* (or original-window #js {})
          original-window-provider (aget window* "ethereum")
          connected-address "0x2222222222222222222222222222222222222222"
          metamask-calls (atom [])
          coinbase-calls (atom [])
          metamask #js {:isMetaMask true
                        :request (fn [payload]
                                   (let [method (aget payload "method")]
                                     (swap! metamask-calls conj method)
                                     (case method
                                       "eth_accounts" (js/Promise.resolve #js [])
                                       "eth_chainId" (js/Promise.resolve "0x1")
                                       (js/Promise.resolve nil))))}
          coinbase #js {:isCoinbaseWallet true
                        :request (fn [payload]
                                   (let [method (aget payload "method")]
                                     (swap! coinbase-calls conj method)
                                     (case method
                                       "eth_accounts" (js/Promise.resolve #js [connected-address])
                                       "eth_chainId" (js/Promise.resolve "0xa4b1")
                                       (js/Promise.resolve nil))))}]
      (try
        (aset js/globalThis "window" window*)
        (aset js/globalThis "ethereum" metamask)
        (aset metamask "providers" #js [metamask coinbase])
        (aset window* "ethereum" metamask)
        (wallet/reset-provider-registry!)
        (wallet/check-connection! store)
        (js/setTimeout
         (fn []
           (try
             (is (= ["eth_accounts"] @metamask-calls))
             (is (= ["eth_accounts" "eth_chainId"] @coinbase-calls))
             (is (= true (get-in @store [:wallet :connected?])))
             (is (= connected-address
                    (get-in @store [:wallet :address])))
             (is (= "0xa4b1" (get-in @store [:wallet :chain-id])))
             (is (= "legacy:coinbase"
                    (get-in @store [:wallet :selected-provider-id])))
             (finally
               (wallet/reset-provider-registry!)
               (aset window* "ethereum" original-window-provider)
               (if (some? original-window)
                 (aset js/globalThis "window" original-window)
                 (js-delete js/globalThis "window"))
               (if (some? original-global-provider)
                 (aset js/globalThis "ethereum" original-global-provider)
                 (js-delete js/globalThis "ethereum"))
               (done))))
         0)
        (catch :default err
          (wallet/reset-provider-registry!)
          (aset window* "ethereum" original-window-provider)
          (if (some? original-window)
            (aset js/globalThis "window" original-window)
            (js-delete js/globalThis "window"))
          (if (some? original-global-provider)
            (aset js/globalThis "ethereum" original-global-provider)
            (js-delete js/globalThis "ethereum"))
          (is false (str "Unexpected error: " err))
          (done))))))
