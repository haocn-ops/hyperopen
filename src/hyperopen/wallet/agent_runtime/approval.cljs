(ns hyperopen.wallet.agent-runtime.approval
  (:require [hyperopen.wallet.agent-runtime.errors :as errors]))

(defn approve-agent-request!
  [{:keys [store
           owner-address
           agent-address
           private-key
           storage-mode
           is-mainnet
           agent-name
           days-valid
           signature-chain-id
           server-time-ms
           persist-session?
           missing-owner-error
           persist-session-error
           now-ms-fn
           normalize-storage-mode
           default-signature-chain-id-for-environment
           build-approve-agent-action
           format-agent-name-with-valid-until
           approve-agent!
           persist-agent-session-by-mode!
           runtime-error-message
           exchange-response-error]
    :or {storage-mode :local
         is-mainnet true
         persist-session? false
         missing-owner-error "Connect your wallet before approving an agent."
         persist-session-error "Unable to persist agent credentials."
         normalize-storage-mode identity
         runtime-error-message errors/runtime-error-message
         exchange-response-error errors/exchange-response-error}}]
  (let [owner-address* (or owner-address
                           (get-in @store [:wallet :address]))]
    (if-not (seq owner-address*)
      (js/Promise.reject (errors/known-error missing-owner-error))
      (try
        (let [nonce (now-ms-fn)
              normalized-storage-mode (normalize-storage-mode storage-mode)
              resolved-signature-chain-id (or signature-chain-id
                                              (default-signature-chain-id-for-environment is-mainnet))
              format-agent-name* (or format-agent-name-with-valid-until
                                     (fn [name _server-time-ms _days-valid]
                                       name))
              encoded-agent-name (format-agent-name* agent-name
                                                     server-time-ms
                                                     days-valid)
              action (build-approve-agent-action
                      agent-address
                      nonce
                      :agent-name encoded-agent-name
                      :is-mainnet is-mainnet
                      :signature-chain-id resolved-signature-chain-id)]
          (-> (approve-agent! store owner-address* action)
              (.then #(.json %))
              (.then
               (fn [resp]
                 (let [data (js->clj resp :keywordize-keys true)]
                   (if (= "ok" (:status data))
                     (if persist-session?
                       (if (and (string? private-key)
                                (seq private-key)
                                (persist-agent-session-by-mode!
                                 owner-address*
                                 normalized-storage-mode
                                 {:agent-address agent-address
                                  :private-key private-key
                                  :last-approved-at nonce
                                  :nonce-cursor nonce}))
                         {:owner-address owner-address*
                          :agent-address agent-address
                          :private-key private-key
                          :storage-mode normalized-storage-mode
                          :last-approved-at nonce
                          :nonce-cursor nonce
                          :response data
                          :action action}
                         (js/Promise.reject
                          (errors/known-error persist-session-error)))
                       {:owner-address owner-address*
                        :agent-address agent-address
                        :private-key private-key
                        :storage-mode normalized-storage-mode
                        :last-approved-at nonce
                        :nonce-cursor nonce
                        :response data
                        :action action})
                     (js/Promise.reject
                      (errors/known-error (exchange-response-error data)))))))
              (.catch (fn [err]
                        (js/Promise.reject
                         (if (errors/known-error? err)
                           err
                           (errors/runtime-error runtime-error-message err)))))))
        (catch :default err
          (js/Promise.reject
           (errors/runtime-error runtime-error-message err)))))))
