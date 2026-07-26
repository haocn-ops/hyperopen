(ns hyperopen.wallet.agent-runtime.unlock
  (:require [hyperopen.wallet.agent-runtime.errors :as errors]
            [hyperopen.wallet.agent-runtime.state-projection :as state]))

(defn- noop
  [& _]
  nil)

(defn- noop-promise
  [& _]
  (js/Promise.resolve nil))

(defn- fallback-local-protection-mode
  [local-protection-mode]
  (if (or (= :passkey local-protection-mode)
          (= "passkey" local-protection-mode))
    :passkey
    :plain))

(defn- passkey-local-mode?
  [storage-mode local-protection-mode]
  (and (= :local storage-mode)
       (= :passkey local-protection-mode)))

(defn- unlock-context
  [{:keys [store
           normalize-storage-mode
           normalize-local-protection-mode
           load-passkey-session-metadata]}]
  (let [owner-address (get-in @store [:wallet :address])
        agent-state (get-in @store [:wallet :agent] {})
        storage-mode (normalize-storage-mode (:storage-mode agent-state))
        local-protection-mode (normalize-local-protection-mode
                               (:local-protection-mode agent-state))
        metadata (when (seq owner-address)
                   (load-passkey-session-metadata owner-address))]
    {:owner-address owner-address
     :storage-mode storage-mode
     :local-protection-mode local-protection-mode
     :metadata metadata}))

(defn- classify-unlock-request
  [{:keys [owner-address storage-mode local-protection-mode metadata]}]
  (cond
    (not (seq owner-address)) :missing-wallet
    (not (passkey-local-mode? storage-mode local-protection-mode)) :not-passkey-session
    (not (map? metadata)) :missing-metadata
    :else :unlockable))

(defn- apply-unlock-precondition-error!
  [store unlock-classification]
  (case unlock-classification
    :missing-wallet
    (swap! store update-in [:wallet :agent] merge
           {:status :locked
            :error "Connect your wallet before unlocking trading."})

    :not-passkey-session
    (swap! store update-in [:wallet :agent] merge
           {:status :error
            :error "Trading unlock is available only for remembered passkey sessions."})

    :missing-metadata
    (state/set-agent-error! store "Enable Trading before unlocking.")

    nil))

(defn- apply-unlock-error!
  [store metadata storage-mode local-protection-mode error operation-key]
  (swap! store update-in [:wallet :agent]
         (fn [agent-state]
           (dissoc (merge agent-state
                          (assoc (state/metadata->ready-session metadata
                                                                storage-mode
                                                                local-protection-mode)
                                 :status :locked
                                 :error error))
                   operation-key))))

(defn unlock-agent-trading!
  [{:keys [store
           normalize-storage-mode
           normalize-local-protection-mode
           load-passkey-session-metadata
           unlock-locked-session!
           cache-unlocked-session!
           runtime-error-message]
    :or {normalize-storage-mode identity
         normalize-local-protection-mode fallback-local-protection-mode
         load-passkey-session-metadata (constantly nil)
         unlock-locked-session! noop-promise
         cache-unlocked-session! noop
         runtime-error-message errors/runtime-error-message}}]
  (let [{:keys [metadata owner-address storage-mode local-protection-mode]
         :as context}
        (unlock-context {:store store
                         :normalize-storage-mode normalize-storage-mode
                         :normalize-local-protection-mode normalize-local-protection-mode
                         :load-passkey-session-metadata load-passkey-session-metadata})
        unlock-classification (classify-unlock-request context)]
    (if (not= :unlockable unlock-classification)
      (apply-unlock-precondition-error! store unlock-classification)
      (do
        (state/invalidate-operations! store
                                      :active-enable-token
                                      :active-protection-token)
        (let [operation-token (state/next-operation-token! store :active-unlock-token)
              current-operation? #(state/operation-current? store
                                                            :active-unlock-token
                                                            operation-token)]
          (-> (unlock-locked-session! {:metadata metadata
                                       :wallet-address owner-address})
              (.then
               (fn [session]
                 (when (current-operation?)
                   (cache-unlocked-session! owner-address session)
                   (state/apply-ready-agent-session!
                    store
                    (assoc (state/metadata->ready-session metadata
                                                          storage-mode
                                                          local-protection-mode)
                           :status :ready)
                    :active-unlock-token))))
              (.catch
               (fn [err]
                 (when (current-operation?)
                   (apply-unlock-error! store
                                        metadata
                                        storage-mode
                                        local-protection-mode
                                        (errors/known-or-runtime-error-message
                                         runtime-error-message
                                         err)
                                        :active-unlock-token))))))))))

(defn lock-agent-trading!
  [{:keys [store
           normalize-storage-mode
           normalize-local-protection-mode
           clear-unlocked-session!]
    :or {normalize-storage-mode identity
         normalize-local-protection-mode fallback-local-protection-mode
         clear-unlocked-session! noop}}]
  (let [owner-address (get-in @store [:wallet :address])
        agent-state (get-in @store [:wallet :agent] {})
        storage-mode (normalize-storage-mode (:storage-mode agent-state))
        local-protection-mode (normalize-local-protection-mode
                               (:local-protection-mode agent-state))]
    (when (and (seq owner-address)
               (= :ready (:status agent-state))
               (passkey-local-mode? storage-mode local-protection-mode))
      (state/invalidate-operations! store
                                    :active-enable-token
                                    :active-unlock-token
                                    :active-protection-token)
      (clear-unlocked-session! owner-address)
      (swap! store update-in [:wallet :agent] merge
             {:status :locked
              :error nil
              :recovery-modal-open? false}))))
