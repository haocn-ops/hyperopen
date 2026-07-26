(ns hyperopen.wallet.agent-runtime.storage-mode
  (:require [hyperopen.wallet.agent-runtime.state-projection :as state]))

(defn- noop
  [& _]
  nil)

(defn- fallback-local-protection-mode
  [local-protection-mode]
  (if (or (= :passkey local-protection-mode)
          (= "passkey" local-protection-mode))
    :passkey
    :plain))

(defn- current-local-protection-mode
  [store normalize-local-protection-mode]
  (normalize-local-protection-mode
   (get-in @store [:wallet :agent :local-protection-mode])))

(defn- clear-persisted-agent-session-fn
  [clear-persisted-agent-session! clear-agent-session-by-mode!]
  (or clear-persisted-agent-session!
      (fn [wallet-address mode _local-protection-mode]
        (when clear-agent-session-by-mode!
          (clear-agent-session-by-mode! wallet-address mode)))))

(defn set-agent-storage-mode!
  [{:keys [store
           storage-mode
           normalize-storage-mode
           normalize-local-protection-mode
           clear-persisted-agent-session!
           clear-agent-session-by-mode!
           clear-unlocked-session!
           persist-storage-mode-preference!
           default-agent-state
           agent-storage-mode-reset-message]
    :or {normalize-storage-mode identity
         normalize-local-protection-mode fallback-local-protection-mode
         clear-unlocked-session! noop
         persist-storage-mode-preference! noop}}]
  (let [clear-persisted-agent-session!* (clear-persisted-agent-session-fn
                                         clear-persisted-agent-session!
                                         clear-agent-session-by-mode!)
        next-mode (normalize-storage-mode storage-mode)
        current-mode (normalize-storage-mode
                      (get-in @store [:wallet :agent :storage-mode]))
        local-protection-mode (current-local-protection-mode store
                                                             normalize-local-protection-mode)
        wallet-address (get-in @store [:wallet :address])
        switching? (not= current-mode next-mode)]
    (when switching?
      (state/invalidate-operations! store
                                    :active-enable-token
                                    :active-unlock-token
                                    :active-protection-token)
      (when (seq wallet-address)
        (clear-persisted-agent-session!* wallet-address current-mode local-protection-mode)
        (clear-persisted-agent-session!* wallet-address next-mode local-protection-mode)
        (clear-unlocked-session! wallet-address))
      (persist-storage-mode-preference! next-mode)
      (state/reset-agent-state! store
                                default-agent-state
                                next-mode
                                local-protection-mode
                                agent-storage-mode-reset-message))))
