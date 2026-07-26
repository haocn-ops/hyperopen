(ns hyperopen.wallet.agent-runtime.protection-mode
  (:require [hyperopen.wallet.agent-runtime.errors :as errors]
            [hyperopen.wallet.agent-runtime.state-projection :as state]))

(declare migration-session-for-mode ignore-delete-failure!)

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

(defn- migration-session
  [agent-state storage-mode local-protection-mode session]
  (let [agent-address (or (:agent-address session)
                          (:agent-address agent-state))
        private-key (:private-key session)]
    (when (and (map? session)
               (string? agent-address)
               (seq agent-address)
               (string? private-key)
               (seq private-key))
      {:agent-address agent-address
       :private-key private-key
       :last-approved-at (or (:last-approved-at session)
                             (:last-approved-at agent-state))
       :nonce-cursor (or (:nonce-cursor session)
                         (:nonce-cursor agent-state))
       :storage-mode storage-mode
       :local-protection-mode local-protection-mode})))

(defn- live-migration-session
  [{:keys [agent-state
           wallet-address
           storage-mode
           local-protection-mode
           load-unlocked-session
           load-agent-session-by-mode]}]
  (when (seq wallet-address)
    (migration-session-for-mode
     {:agent-state agent-state
      :wallet-address wallet-address
      :storage-mode storage-mode
      :local-protection-mode local-protection-mode
      :load-unlocked-session load-unlocked-session
      :load-agent-session-by-mode load-agent-session-by-mode})))

(defn- migrated-session-for-mode
  [agent-state live-session storage-mode local-protection-mode session]
  (or (migration-session agent-state
                         storage-mode
                         local-protection-mode
                         session)
      (assoc live-session
             :storage-mode storage-mode
             :local-protection-mode local-protection-mode)))

(defn- reject-known-error
  [message]
  (js/Promise.reject (errors/known-error message)))

(defn- rollback-passkey-migration!
  [{:keys [wallet-address
           restore-mode
           persist-local-protection-mode-preference!
           clear-passkey-session-metadata!
           clear-passkey-metadata?
           delete-locked-session!
           persist-session-error]}]
  (when restore-mode
    (persist-local-protection-mode-preference! restore-mode))
  (when clear-passkey-metadata?
    (clear-passkey-session-metadata! wallet-address))
  (-> (ignore-delete-failure! (delete-locked-session! wallet-address))
      (.then (fn [_]
               (reject-known-error persist-session-error)))))

(defn- finalize-migrated-session!
  [store cache-unlocked-session! wallet-address session current-operation?]
  (when (current-operation?)
    (cache-unlocked-session! wallet-address session)
    (state/apply-migrated-agent-session! store
                                         session
                                         :active-protection-token)
    session))

(defn- passkey-migration-error
  [err missing-session-error]
  (if (errors/known-error? err)
    (or (some-> err .-message str)
        missing-session-error)
    (errors/runtime-error-message err)))

(defn- migrate-ready-plain-session-to-passkey!
  [{:keys [agent-state
           store
           wallet-address
           storage-mode
           current-mode
           next-mode
           live-session
           cache-unlocked-session!
           clear-agent-session-by-mode!
           clear-passkey-session-metadata!
           create-locked-session!
           delete-locked-session!
           persist-local-protection-mode-preference!
           persist-passkey-session-metadata!
           persist-session-error
           missing-session-error
           current-operation?]}]
  (if-not live-session
    (state/set-agent-local-protection-error! store missing-session-error)
    (-> (create-locked-session! {:wallet-address wallet-address
                                 :session live-session})
        (.then
         (fn [{:keys [metadata session]}]
           (let [locked-session (migrated-session-for-mode agent-state
                                                           live-session
                                                           storage-mode
                                                           next-mode
                                                           session)]
             (cond
               (not (current-operation?))
               (ignore-delete-failure! (delete-locked-session! wallet-address))

               (not (persist-passkey-session-metadata! wallet-address metadata))
               (rollback-passkey-migration! {:wallet-address wallet-address
                                             :persist-local-protection-mode-preference! persist-local-protection-mode-preference!
                                             :clear-passkey-session-metadata! clear-passkey-session-metadata!
                                             :delete-locked-session! delete-locked-session!
                                             :persist-session-error persist-session-error})

               (not (persist-local-protection-mode-preference! next-mode))
               (rollback-passkey-migration! {:wallet-address wallet-address
                                             :persist-local-protection-mode-preference! persist-local-protection-mode-preference!
                                             :clear-passkey-session-metadata! clear-passkey-session-metadata!
                                             :clear-passkey-metadata? true
                                             :delete-locked-session! delete-locked-session!
                                             :persist-session-error persist-session-error})

               (and (fn? clear-agent-session-by-mode!)
                    (false? (clear-agent-session-by-mode! wallet-address storage-mode)))
               (rollback-passkey-migration! {:wallet-address wallet-address
                                             :restore-mode current-mode
                                             :persist-local-protection-mode-preference! persist-local-protection-mode-preference!
                                             :clear-passkey-session-metadata! clear-passkey-session-metadata!
                                             :clear-passkey-metadata? true
                                             :delete-locked-session! delete-locked-session!
                                             :persist-session-error persist-session-error})

               :else
               (finalize-migrated-session! store
                                           cache-unlocked-session!
                                           wallet-address
                                           locked-session
                                           current-operation?)))))
        (.catch
         (fn [err]
           (when (current-operation?)
             (state/set-agent-local-protection-error! store
                                                      (passkey-migration-error err
                                                                               missing-session-error))))))))

(defn- migrate-ready-passkey-session-to-plain!
  [{:keys [store
           wallet-address
           storage-mode
           next-mode
           live-session
           cache-unlocked-session!
           clear-agent-session-by-mode!
           clear-passkey-session-metadata!
           delete-locked-session!
           persist-agent-session-by-mode!
           persist-local-protection-mode-preference!
           persist-session-error
           missing-session-error
           current-operation?]}]
  (if-not live-session
    (state/set-agent-local-protection-error! store missing-session-error)
    (cond
      (not (current-operation?))
      (js/Promise.resolve nil)

      (not (persist-agent-session-by-mode!
            wallet-address
            storage-mode
            live-session))
      (state/set-agent-local-protection-error! store persist-session-error)

      (not (persist-local-protection-mode-preference! next-mode))
      (do
        (when (fn? clear-agent-session-by-mode!)
          (clear-agent-session-by-mode! wallet-address storage-mode))
        (state/set-agent-local-protection-error! store persist-session-error))

      :else
      (do
        (clear-passkey-session-metadata! wallet-address)
        (-> (ignore-delete-failure! (delete-locked-session! wallet-address))
            (.then
             (fn [_]
               (finalize-migrated-session! store
                                           cache-unlocked-session!
                                           wallet-address
                                           (assoc live-session
                                                  :storage-mode storage-mode
                                                  :local-protection-mode next-mode)
                                           current-operation?))))))))

(defn- update-local-protection-mode-without-migration!
  [{:keys [store
           current-mode
           next-mode
           storage-mode
           persist-local-protection-mode-preference!
           default-agent-state
           unlock-required-error]}]
  (persist-local-protection-mode-preference! next-mode)
  (if (and (= :passkey current-mode)
           (= :plain next-mode))
    (state/reset-agent-state! store
                              default-agent-state
                              storage-mode
                              current-mode
                              unlock-required-error)
    (swap! store update-in [:wallet :agent] merge
           {:storage-mode storage-mode
            :local-protection-mode next-mode})))

(defn- migration-session-for-mode
  [{:keys [agent-state
           wallet-address
           storage-mode
           local-protection-mode
           load-unlocked-session
           load-agent-session-by-mode]}]
  (or (some->> (when (fn? load-unlocked-session)
                 (load-unlocked-session wallet-address))
               (migration-session agent-state storage-mode local-protection-mode))
      (some->> (when (and (fn? load-agent-session-by-mode)
                          (not= :passkey local-protection-mode))
                 (load-agent-session-by-mode wallet-address storage-mode))
               (migration-session agent-state storage-mode local-protection-mode))))

(defn- ignore-delete-failure!
  [delete-promise]
  (if (some? delete-promise)
    (.catch delete-promise (fn [_] (js/Promise.resolve nil)))
    (js/Promise.resolve nil)))

(defn set-agent-local-protection-mode!
  [{:keys [store
           local-protection-mode
           normalize-local-protection-mode
           normalize-storage-mode
           clear-agent-session-by-mode!
           load-agent-session-by-mode
           load-unlocked-session
           cache-unlocked-session!
           create-locked-session!
           delete-locked-session!
           persist-agent-session-by-mode!
           persist-passkey-session-metadata!
           clear-passkey-session-metadata!
           persist-local-protection-mode-preference!
           default-agent-state
           agent-protection-mode-reset-message
           persist-session-error
           missing-session-error
           unlock-required-error]
    :or {normalize-storage-mode identity
         normalize-local-protection-mode fallback-local-protection-mode
         cache-unlocked-session! noop
         create-locked-session! noop-promise
         delete-locked-session! noop-promise
         persist-passkey-session-metadata! (constantly false)
         clear-passkey-session-metadata! noop
         persist-local-protection-mode-preference! noop
         persist-session-error "Unable to persist agent credentials."
         missing-session-error "Trading session data is unavailable. Enable Trading again."
         unlock-required-error "Unlock trading before turning off passkey protection."}}]
  (let [next-mode (normalize-local-protection-mode local-protection-mode)
        agent-state (get-in @store [:wallet :agent] {})
        current-mode (normalize-local-protection-mode
                      (:local-protection-mode agent-state))
        storage-mode (normalize-storage-mode
                      (:storage-mode agent-state))
        wallet-address (get-in @store [:wallet :address])
        switching? (not= current-mode next-mode)]
    (when switching?
      (state/invalidate-operations! store
                                    :active-enable-token
                                    :active-unlock-token
                                    :active-protection-token)
      (let [operation-token (state/next-operation-token! store :active-protection-token)
            current-operation? #(state/operation-current? store
                                                          :active-protection-token
                                                          operation-token)
            status (:status agent-state)
            live-session (live-migration-session
                          {:agent-state agent-state
                           :wallet-address wallet-address
                           :storage-mode storage-mode
                           :local-protection-mode current-mode
                           :load-unlocked-session load-unlocked-session
                           :load-agent-session-by-mode load-agent-session-by-mode})]
        (cond
          (and (= :passkey current-mode)
               (= :plain next-mode)
               (nil? live-session))
          (state/set-agent-local-protection-error! store
                                                   (if (#{:locked :unlocking} status)
                                                     unlock-required-error
                                                     missing-session-error))

          (and (= :ready status)
               (= :plain current-mode)
               (= :passkey next-mode))
          (migrate-ready-plain-session-to-passkey!
           {:agent-state agent-state
            :store store
            :wallet-address wallet-address
            :storage-mode storage-mode
            :current-mode current-mode
            :next-mode next-mode
            :live-session live-session
            :cache-unlocked-session! cache-unlocked-session!
            :clear-agent-session-by-mode! clear-agent-session-by-mode!
            :clear-passkey-session-metadata! clear-passkey-session-metadata!
            :create-locked-session! create-locked-session!
            :delete-locked-session! delete-locked-session!
            :persist-local-protection-mode-preference! persist-local-protection-mode-preference!
            :persist-passkey-session-metadata! persist-passkey-session-metadata!
            :persist-session-error persist-session-error
            :missing-session-error missing-session-error
            :current-operation? current-operation?})

          (and (= :ready status)
               (= :passkey current-mode)
               (= :plain next-mode))
          (migrate-ready-passkey-session-to-plain!
           {:store store
            :wallet-address wallet-address
            :storage-mode storage-mode
            :next-mode next-mode
            :live-session live-session
            :cache-unlocked-session! cache-unlocked-session!
            :clear-agent-session-by-mode! clear-agent-session-by-mode!
            :clear-passkey-session-metadata! clear-passkey-session-metadata!
            :delete-locked-session! delete-locked-session!
            :persist-agent-session-by-mode! persist-agent-session-by-mode!
            :persist-local-protection-mode-preference! persist-local-protection-mode-preference!
            :persist-session-error persist-session-error
            :missing-session-error missing-session-error
            :current-operation? current-operation?})

          :else
          (update-local-protection-mode-without-migration!
           {:store store
            :current-mode current-mode
            :next-mode next-mode
            :storage-mode storage-mode
            :persist-local-protection-mode-preference! persist-local-protection-mode-preference!
            :default-agent-state default-agent-state
            :unlock-required-error unlock-required-error}))))))
