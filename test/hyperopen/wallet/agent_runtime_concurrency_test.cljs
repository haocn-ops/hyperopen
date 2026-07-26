(ns hyperopen.wallet.agent-runtime-concurrency-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.test-support.async :as async-support]
            [hyperopen.wallet.agent-runtime :as agent-runtime]))

(defn- deferred
  []
  (let [resolve* (atom nil)
        reject* (atom nil)
        promise (js/Promise. (fn [resolve reject]
                               (reset! resolve* resolve)
                               (reset! reject* reject)))]
    {:promise promise
     :resolve! (fn [value] (@resolve* value))
     :reject! (fn [err] (@reject* err))}))

(defn- ok-response
  []
  #js {:json (fn [] (js/Promise.resolve #js {:status "ok"}))})

(defn- wallet-store
  [agent-state]
  (atom {:wallet {:address "0xowner"
                  :chain-id "0xwallet"
                  :agent agent-state}}))

(defn- enable-base-opts
  [store extra]
  (merge
   {:store store
    :options {:storage-mode :local :local-protection-mode :plain :is-mainnet false}
    :create-agent-credentials! (fn [] {:private-key "0xnew-priv"
                                       :agent-address "0xnew-agent"})
    :now-ms-fn (fn [] 1700000001000)
    :normalize-storage-mode identity
    :normalize-local-protection-mode identity
    :ensure-device-label! (fn [] nil)
    :default-signature-chain-id-for-environment (fn [_] "0xdefault")
    :build-approve-agent-action (fn [agent-address nonce & _]
                                  {:agentAddress agent-address :nonce nonce})
    :approve-agent! (fn [& _] (js/Promise.resolve (ok-response)))
    :persist-agent-session-by-mode! (fn [& _] true)
    :cache-unlocked-session! (fn [& _] true)
    :runtime-error-message (fn [err] (or (some-> err .-message) (str err)))
    :exchange-response-error (fn [resp] (pr-str resp))}
   extra))

(deftest unlock-success-is-stale-after-newer-enable-completes-test
  (async done
    (let [unlock (deferred)
          unlocked-cache (atom nil)
          clear-calls (atom [])
          old-metadata {:agent-address "0xold-agent"
                        :credential-id "old-cred"
                        :prf-salt "old-salt"
                        :last-approved-at 1700000000000
                        :nonce-cursor 1700000000000}
          store (wallet-store {:status :locked
                               :storage-mode :local
                               :local-protection-mode :passkey
                               :agent-address "0xold-agent"
                               :last-approved-at 1700000000000
                               :nonce-cursor 1700000000000})
          unlock-promise (agent-runtime/unlock-agent-trading!
                          {:store store
                           :normalize-storage-mode identity
                           :normalize-local-protection-mode identity
                           :load-passkey-session-metadata (fn [_] old-metadata)
                           :unlock-locked-session! (fn [_] (:promise unlock))
                           :clear-unlocked-session! (fn [_]
                                                      (swap! clear-calls conj :clear)
                                                      (reset! unlocked-cache nil))
                           :cache-unlocked-session! (fn [_ session]
                                                      (reset! unlocked-cache session)
                                                      true)
                           :runtime-error-message (fn [err] (.-message err))})]
      (-> (agent-runtime/enable-agent-trading!
           (enable-base-opts
            store
            {:options {:storage-mode :local
                       :local-protection-mode :passkey
                       :is-mainnet false}
             :passkey-lock-supported? (fn [] true)
             :create-locked-session! (fn [_]
                                       (js/Promise.resolve
                                        {:metadata {:agent-address "0xnew-agent"
                                                    :credential-id "new-cred"}
                                         :session {:agent-address "0xnew-agent"
                                                   :private-key "0xnew-priv"
                                                   :last-approved-at 1700000001000
                                                   :nonce-cursor 1700000001000}}))
             :persist-passkey-session-metadata! (fn [& _] true)
             :delete-locked-session! (fn [_]
                                       (swap! clear-calls conj :delete)
                                       (js/Promise.resolve true))
             :cache-unlocked-session! (fn [_ session]
                                        (reset! unlocked-cache session)
                                        true)}))
          (.then
           (fn [_]
             ((:resolve! unlock) {:agent-address "0xold-agent"
                                  :private-key "0xold-priv"})
             unlock-promise))
          (.then
           (fn [_]
             (is (= :ready (get-in @store [:wallet :agent :status])))
             (is (= "0xnew-agent" (get-in @store [:wallet :agent :agent-address])))
             (is (= 1700000001000 (get-in @store [:wallet :agent :last-approved-at])))
             (is (= 1700000001000 (get-in @store [:wallet :agent :nonce-cursor])))
             (is (nil? (get-in @store [:wallet :agent :active-unlock-token])))
             (is (= "0xnew-agent" (:agent-address @unlocked-cache))
                 "stale unlock must not remove the newer passkey enable cache")
             (is (empty? @clear-calls))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest storage-mode-switch-invalidates-in-flight-enable-before-persistence-test
  (async done
    (let [approval (deferred)
          raw-persist-calls (atom [])
          cache-calls (atom [])
          store (wallet-store {:status :approving
                               :storage-mode :local
                               :local-protection-mode :plain})
          enable-promise (agent-runtime/enable-agent-trading!
                          (enable-base-opts
                           store
                           {:approve-agent! (fn [& _] (:promise approval))
                            :persist-agent-session-by-mode! (fn [& args]
                                                              (swap! raw-persist-calls conj (vec args))
                                                              true)
                            :cache-unlocked-session! (fn [& args]
                                                       (swap! cache-calls conj (vec args))
                                                       true)}))]
      (agent-runtime/set-agent-storage-mode!
       {:store store
        :storage-mode :session
        :normalize-storage-mode identity
        :normalize-local-protection-mode identity
        :default-agent-state (fn [& {:keys [storage-mode local-protection-mode]}]
                               {:status :not-ready
                                :storage-mode storage-mode
                                :local-protection-mode local-protection-mode})
        :agent-storage-mode-reset-message "Storage mode changed."})
      ((:resolve! approval) (ok-response))
      (-> enable-promise
          (.then
           (fn [_]
             (is (= :session (get-in @store [:wallet :agent :storage-mode])))
             (is (= :not-ready (get-in @store [:wallet :agent :status])))
             (is (empty? @raw-persist-calls))
             (is (empty? @cache-calls))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest storage-mode-reset-does-not-reuse-enable-token-for-stale-completion-test
  (async done
    (let [older-approval (deferred)
          newer-approval (deferred)
          credential-queue (atom [{:private-key "0xold-priv"
                                   :agent-address "0xold-agent"}
                                  {:private-key "0xnew-priv"
                                   :agent-address "0xnew-agent"}])
          persist-calls (atom [])
          store (wallet-store {:status :approving
                               :storage-mode :local
                               :local-protection-mode :plain})
          enable-once (fn [storage-mode]
                        (agent-runtime/enable-agent-trading!
                         (enable-base-opts
                          store
                          {:options {:storage-mode storage-mode
                                     :local-protection-mode :plain}
                           :create-agent-credentials! (fn []
                                                        (let [credential (first @credential-queue)]
                                                          (swap! credential-queue subvec 1)
                                                          credential))
                           :approve-agent! (fn [_store _owner-address action]
                                             (case (:agentAddress action)
                                               "0xold-agent" (:promise older-approval)
                                               "0xnew-agent" (:promise newer-approval)))
                           :persist-agent-session-by-mode! (fn [& args]
                                                             (swap! persist-calls conj (vec args))
                                                             true)})))
          older-enable (enable-once :local)]
      (agent-runtime/set-agent-storage-mode!
       {:store store
        :storage-mode :session
        :normalize-storage-mode identity
        :normalize-local-protection-mode identity
        :default-agent-state (fn [& {:keys [storage-mode local-protection-mode]}]
                               {:status :not-ready
                                :storage-mode storage-mode
                                :local-protection-mode local-protection-mode})
        :agent-storage-mode-reset-message "Storage mode changed."})
      (let [newer-enable (enable-once :session)]
        ((:resolve! older-approval) (ok-response))
        (-> older-enable
            (.then
             (fn [_]
               (is (empty? @persist-calls)
                   "stale enable must not become current after a reset and newer enable")
               (is (not= "0xold-agent"
                         (get-in @store [:wallet :agent :agent-address])))
               ((:resolve! newer-approval) (ok-response))
               newer-enable))
            (.then
             (fn [_]
               (is (= [["0xowner" :session {:agent-address "0xnew-agent"
                                             :private-key "0xnew-priv"
                                             :last-approved-at 1700000001000
                                             :nonce-cursor 1700000001000}]]
                      @persist-calls))
               (is (= "0xnew-agent" (get-in @store [:wallet :agent :agent-address])))
               (is (= :session (get-in @store [:wallet :agent :storage-mode])))
               (done)))
            (.catch (async-support/unexpected-error done)))))))

(deftest protection-mode-migration-is-stale-after-storage-mode-switch-test
  (async done
    (let [created-lockbox (deferred)
          create-called (deferred)
          cache-calls (atom [])
          delete-calls (atom [])
          store (wallet-store {:status :ready
                               :storage-mode :local
                               :local-protection-mode :plain
                               :agent-address "0xplain-agent"
                               :last-approved-at 1700000002000
                               :nonce-cursor 1700000002000})
          migration-promise
          (agent-runtime/set-agent-local-protection-mode!
           {:store store
            :local-protection-mode :passkey
            :normalize-local-protection-mode identity
            :normalize-storage-mode identity
            :load-unlocked-session (fn [_]
                                     {:agent-address "0xplain-agent"
                                      :private-key "0xplain-priv"
                                      :last-approved-at 1700000002000
                                      :nonce-cursor 1700000002000})
            :create-locked-session! (fn [_]
                                      ((:resolve! create-called) true)
                                      (:promise created-lockbox))
            :cache-unlocked-session! (fn [& args]
                                       (swap! cache-calls conj (vec args)))
            :delete-locked-session! (fn [owner-address]
                                      (swap! delete-calls conj owner-address)
                                      (js/Promise.resolve true))
            :persist-passkey-session-metadata! (fn [& _] true)
            :persist-local-protection-mode-preference! (fn [_] true)
            :default-agent-state (fn [& {:keys [storage-mode local-protection-mode]}]
                                   {:status :not-ready
                                    :storage-mode storage-mode
                                    :local-protection-mode local-protection-mode})})]
      (-> (:promise create-called)
          (.then
           (fn [_]
             (agent-runtime/set-agent-storage-mode!
              {:store store
               :storage-mode :session
               :normalize-storage-mode identity
               :normalize-local-protection-mode identity
               :default-agent-state (fn [& {:keys [storage-mode local-protection-mode]}]
                                      {:status :not-ready
                                       :storage-mode storage-mode
                                       :local-protection-mode local-protection-mode})
               :agent-storage-mode-reset-message "Storage mode changed."})
             ((:resolve! created-lockbox)
              {:metadata {:agent-address "0xplain-agent" :credential-id "cred"}
               :session {:agent-address "0xplain-agent"
                         :private-key "0xplain-priv"
                         :last-approved-at 1700000002000
                         :nonce-cursor 1700000002000}})
             migration-promise))
          (.then
           (fn [_]
             (is (= :session (get-in @store [:wallet :agent :storage-mode])))
             (is (= :plain (get-in @store [:wallet :agent :local-protection-mode])))
             (is (= :not-ready (get-in @store [:wallet :agent :status])))
             (is (empty? @cache-calls))
             (is (= ["0xowner"] @delete-calls))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest lock-agent-trading-invalidates-in-flight-enable-test
  (async done
    (let [approval (deferred)
          persist-calls (atom [])
          cache-calls (atom [])
          clear-calls (atom [])
          store (wallet-store {:status :ready
                               :storage-mode :local
                               :local-protection-mode :passkey
                               :agent-address "0xold-agent"
                               :last-approved-at 1700000003000
                               :nonce-cursor 1700000003000})
          enable-promise (agent-runtime/enable-agent-trading!
                          (enable-base-opts
                           store
                           {:approve-agent! (fn [& _] (:promise approval))
                            :persist-agent-session-by-mode! (fn [& args]
                                                              (swap! persist-calls conj (vec args))
                                                              true)
                            :cache-unlocked-session! (fn [& args]
                                                       (swap! cache-calls conj (vec args))
                                                       true)}))]
      (agent-runtime/lock-agent-trading!
       {:store store
        :normalize-storage-mode identity
        :normalize-local-protection-mode identity
        :clear-unlocked-session! (fn [owner-address]
                                   (swap! clear-calls conj owner-address))})
      ((:resolve! approval) (ok-response))
      (-> enable-promise
          (.then
           (fn [_]
             (is (= :locked (get-in @store [:wallet :agent :status])))
             (is (= "0xold-agent" (get-in @store [:wallet :agent :agent-address])))
             (is (= ["0xowner"] @clear-calls))
             (is (empty? @persist-calls))
             (is (empty? @cache-calls))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest stale-passkey-enable-deletes-lockbox-after-token-invalidation-test
  (async done
    (let [created-lockbox (deferred)
          create-called (deferred)
          delete-calls (atom [])
          store (wallet-store {:status :approving
                               :storage-mode :local
                               :local-protection-mode :passkey})
          enable-promise (agent-runtime/enable-agent-trading!
                          (enable-base-opts
                           store
                           {:options {:storage-mode :local
                                      :local-protection-mode :passkey
                            :is-mainnet false}
                            :passkey-lock-supported? (fn [] true)
                            :create-locked-session! (fn [_]
                                                      ((:resolve! create-called) true)
                                                      (:promise created-lockbox))
                            :persist-passkey-session-metadata! (fn [& _] true)
                            :delete-locked-session! (fn [owner-address]
                                                      (swap! delete-calls conj owner-address)
                                                      (js/Promise.resolve true))}))]
      (-> (:promise create-called)
          (.then
           (fn [_]
             (agent-runtime/set-agent-storage-mode!
              {:store store
               :storage-mode :session
               :normalize-storage-mode identity
               :normalize-local-protection-mode identity
               :default-agent-state (fn [& {:keys [storage-mode local-protection-mode]}]
                                      {:status :not-ready
                                       :storage-mode storage-mode
                                       :local-protection-mode local-protection-mode})
               :agent-storage-mode-reset-message "Storage mode changed."})
             ((:resolve! created-lockbox)
              {:metadata {:agent-address "0xstale-agent" :credential-id "cred"}
               :session {:agent-address "0xstale-agent"
                         :private-key "0xstale-priv"
                         :last-approved-at 1700000001000
                         :nonce-cursor 1700000001000}})
             enable-promise))
          (.then
           (fn [_]
             (is (= ["0xowner"] @delete-calls))
             (is (not= "0xstale-agent"
                       (get-in @store [:wallet :agent :agent-address])))
             (done)))
          (.catch (async-support/unexpected-error done))))))
