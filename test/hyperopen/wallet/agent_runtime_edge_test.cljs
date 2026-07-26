(ns hyperopen.wallet.agent-runtime-edge-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.test-support.async :as async-support] [hyperopen.wallet.agent-runtime :as agent-runtime]))

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
  ([] (ok-response {}))
  ([payload]
   #js {:json (fn [] (js/Promise.resolve (clj->js (merge {:status "ok"} payload))))}))

(defn- response [payload]
  #js {:json (fn [] (js/Promise.resolve (clj->js payload)))})

(defn- settled
  [promise]
  (.then promise
         (fn [value] {:status :fulfilled :value value})
         (fn [err] {:status :rejected :message (.-message err)})))

(defn- wallet-store
  [agent-state]
  (atom {:wallet {:address "0xowner" :chain-id "0xwallet" :agent agent-state}}))

(defn- agent-state [status local-protection-mode]
  {:status status :storage-mode :local :local-protection-mode local-protection-mode})

(defn- enable-base-opts
  [store extra]
  (merge
   {:store store
    :options {:storage-mode :local :local-protection-mode :plain :is-mainnet false}
    :create-agent-credentials! (fn [] {:private-key "0xpriv" :agent-address "0xagent"})
    :now-ms-fn (fn [] 1700000000000)
    :normalize-storage-mode identity
    :normalize-local-protection-mode identity
    :ensure-device-label! (fn [] nil)
    :default-signature-chain-id-for-environment (fn [_] "0xdefault")
    :build-approve-agent-action (fn [agent-address nonce & {:keys [signature-chain-id]}]
                                  {:agentAddress agent-address :nonce nonce
                                   :signatureChainId signature-chain-id})
    :approve-agent! (fn [& _] (js/Promise.resolve (ok-response)))
    :persist-agent-session-by-mode! (fn [& _] true)
    :runtime-error-message (fn [err] (or (some-> err .-message) (str err)))
    :exchange-response-error (fn [resp] (pr-str resp))}
   extra))

(deftest approve-agent-request-persist-session-true-persists-once-after-ok-test
  (async done
    (let [approve-deferred (deferred)
          events (atom [])
          persist-calls (atom [])
          store (atom {:wallet {:address "0xowner" :chain-id "0xwallet"}})
          approval (agent-runtime/approve-agent-request!
                    {:store store
                     :owner-address "0xowner"
                     :agent-address "0xagent"
                     :private-key "0xpriv"
                     :storage-mode :local
                     :is-mainnet false
                     :persist-session? true
                     :now-ms-fn (fn [] 1700000000100)
                     :normalize-storage-mode identity
                     :default-signature-chain-id-for-environment (fn [_] "0xdefault")
                     :build-approve-agent-action (fn [agent-address nonce & _]
                                                   {:agentAddress agent-address :nonce nonce})
                     :approve-agent! (fn [_store owner-address action]
                                       (swap! events conj [:approve-called owner-address action])
                                       (:promise approve-deferred))
                     :persist-agent-session-by-mode! (fn [& args]
                                                       (swap! events conj [:persist-called])
                                                       (swap! persist-calls conj (vec args))
                                                       true)
                     :runtime-error-message (fn [err] (str err))
                     :exchange-response-error (fn [resp] (pr-str resp))})]
      (is (empty? @persist-calls)
          "session persistence waits for the exchange response")
      ((:resolve! approve-deferred)
       #js {:json (fn []
                    (swap! events conj [:json-read])
                    (js/Promise.resolve #js {:status "ok"}))})
      (-> approval
          (.then
           (fn [result]
             (is (= [[:approve-called "0xowner" {:agentAddress "0xagent" :nonce 1700000000100}]
                     [:json-read] [:persist-called]]
                    @events))
             (is (= [["0xowner" :local {:agent-address "0xagent"
                                         :private-key "0xpriv"
                                         :last-approved-at 1700000000100
                                         :nonce-cursor 1700000000100}]]
                    @persist-calls))
             (is (= "0xagent" (:agent-address result)))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest approve-agent-request-signature-chain-id-precedence-test
  (async done
    (let [captured-chain-ids (atom [])
          run-approval (fn [{:keys [wallet-chain-id explicit-chain-id is-mainnet]}]
                         (agent-runtime/approve-agent-request!
                          {:store (atom {:wallet {:address "0xowner" :chain-id wallet-chain-id}})
                           :owner-address "0xowner"
                           :agent-address "0xagent"
                           :private-key "0xpriv"
                           :storage-mode :session
                           :is-mainnet is-mainnet
                           :signature-chain-id explicit-chain-id
                           :persist-session? false
                           :now-ms-fn (fn [] 1700000000200)
                           :normalize-storage-mode identity
                           :default-signature-chain-id-for-environment (fn [mainnet?]
                                                                         (if mainnet? "0xdefault-mainnet" "0xdefault-testnet"))
                           :build-approve-agent-action (fn [_ _ & {:keys [signature-chain-id]}]
                                                         (swap! captured-chain-ids conj signature-chain-id)
                                                         {:signatureChainId signature-chain-id})
                           :approve-agent! (fn [& _]
                                             (js/Promise.resolve (ok-response)))
                           :persist-agent-session-by-mode! (fn [& _] true)
                           :runtime-error-message (fn [err] (str err))
                           :exchange-response-error (fn [resp] (pr-str resp))}))]
      (-> (js/Promise.all
           #js [(run-approval {:wallet-chain-id "0xwallet"
                               :explicit-chain-id "0xexplicit"
                               :is-mainnet true})
                (run-approval {:wallet-chain-id "0xwallet"
                               :is-mainnet true})
                (run-approval {:wallet-chain-id nil
                               :is-mainnet false})])
          (.then (fn [_]
                   (is (= ["0xexplicit" "0xdefault-mainnet" "0xdefault-testnet"]
                          @captured-chain-ids))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest approve-agent-request-ignores-unrelated-wallet-chain-id-test
  (async done
    (let [captured-chain-id (atom nil)]
      (-> (agent-runtime/approve-agent-request!
           {:store (atom {:wallet {:address "0xowner"
                                   :chain-id "0x1"}})
            :owner-address "0xowner"
            :agent-address "0xagent"
            :private-key "0xpriv"
            :storage-mode :session
            :is-mainnet true
            :persist-session? false
            :now-ms-fn (fn [] 1700000000250)
            :normalize-storage-mode identity
            :default-signature-chain-id-for-environment (fn [mainnet?]
                                                          (if mainnet?
                                                            "0xa4b1"
                                                            "0x66eee"))
            :build-approve-agent-action (fn [_ _ & {:keys [signature-chain-id]}]
                                          (reset! captured-chain-id signature-chain-id)
                                          {:signatureChainId signature-chain-id})
            :approve-agent! (fn [& _]
                              (js/Promise.resolve (ok-response)))
            :persist-agent-session-by-mode! (fn [& _] true)
            :runtime-error-message (fn [err] (str err))
            :exchange-response-error (fn [resp] (pr-str resp))})
          (.then (fn [_]
                   (is (= "0xa4b1" @captured-chain-id))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest approve-agent-request-rejects-non-ok-and-wraps-unknown-failures-test
  (async done
    (let [persist-calls (atom [])
          base {:owner-address "0xowner"
                :agent-address "0xagent"
                :private-key "0xpriv"
                :storage-mode :local
                :persist-session? true
                :now-ms-fn (fn [] 1700000000300)
                :normalize-storage-mode identity
                :default-signature-chain-id-for-environment (fn [_] "0xdefault")
                :build-approve-agent-action (fn [& _] {})
                :persist-agent-session-by-mode! (fn [& args]
                                                  (swap! persist-calls conj (vec args))
                                                  true)
                :runtime-error-message (fn [err]
                                         (str "wrapped " (.-message err)))
                :exchange-response-error (fn [resp]
                                           (str "exchange " (:response resp)))}
          non-ok (agent-runtime/approve-agent-request!
                  (assoc base
                         :store (atom {:wallet {:address "0xowner"}})
                         :approve-agent! (fn [& _]
                                           (js/Promise.resolve
                                            (response {:status "error" :response "rejected"})))))
          unknown-failure (agent-runtime/approve-agent-request!
                           (assoc base
                                  :store (atom {:wallet {:address "0xowner"}})
                                  :approve-agent! (fn [& _]
                                                    (js/Promise.reject
                                                     (js/Error. "rpc down")))))]
      (-> (js/Promise.all #js [(settled non-ok) (settled unknown-failure)])
          (.then
           (fn [results]
             (is (= {:status :rejected :message "exchange rejected"} (aget results 0)))
             (is (= {:status :rejected :message "wrapped rpc down"} (aget results 1)))
             (is (empty? @persist-calls))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest passkey-local-enable-persists-lockbox-metadata-and-never-raw-key-test
  (async done
    (let [store (wallet-store (agent-state :approving :passkey))
          create-lockbox-calls (atom [])
          metadata-calls (atom [])
          cache-calls (atom [])
          raw-persist-calls (atom [])]
      (-> (agent-runtime/enable-agent-trading!
           (enable-base-opts
            store
            {:options {:storage-mode :local
                       :local-protection-mode :passkey
                       :is-mainnet false}
             :create-agent-credentials! (fn []
                                          {:private-key "0xpasskey-priv" :agent-address "0xpasskey-agent"})
             :now-ms-fn (fn [] 1700000000400)
             :passkey-lock-supported? (fn [] true)
             :create-locked-session! (fn [request]
                                       (swap! create-lockbox-calls conj request)
                                       (js/Promise.resolve
                                        {:metadata {:agent-address "0xpasskey-agent" :credential-id "cred"
                                                    :prf-salt "salt" :last-approved-at 1700000000400
                                                    :nonce-cursor 1700000000400}
                                         :session {:agent-address "0xpasskey-agent" :private-key "0xpasskey-priv"
                                                   :last-approved-at 1700000000400 :nonce-cursor 1700000000400}}))
             :persist-passkey-session-metadata! (fn [& args]
                                                  (swap! metadata-calls conj (vec args))
                                                  true)
             :cache-unlocked-session! (fn [& args]
                                        (swap! cache-calls conj (vec args))
                                        true)
             :persist-agent-session-by-mode! (fn [& args]
                                               (swap! raw-persist-calls conj (vec args))
                                               true)}))
          (.then
           (fn [_]
             (is (= "0xpasskey-priv"
                    (get-in (first @create-lockbox-calls) [:session :private-key])))
             (is (= "cred" (get-in (first @metadata-calls) [1 :credential-id])))
             (is (= :passkey (get-in (first @cache-calls) [1 :local-protection-mode])))
             (is (empty? @raw-persist-calls))
             (is (= :ready (get-in @store [:wallet :agent :status])))
             (is (= :passkey (get-in @store [:wallet :agent :local-protection-mode])))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest passkey-local-enable-unsupported-lock-fails-closed-test
  (async done
    (let [store (wallet-store (agent-state :approving :passkey))
          raw-persist-calls (atom [])]
      (-> (agent-runtime/enable-agent-trading!
           (enable-base-opts
            store
            {:options {:storage-mode :local
                       :local-protection-mode :passkey}
             :passkey-lock-supported? (fn [] false)
             :persist-agent-session-by-mode! (fn [& args]
                                               (swap! raw-persist-calls conj (vec args))
                                               true)}))
          (.then
           (fn [_]
             (is (= :error (get-in @store [:wallet :agent :status])))
             (is (= "Passkey unlock is unavailable in this browser."
                    (get-in @store [:wallet :agent :error])))
             (is (empty? @raw-persist-calls))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest passkey-local-enable-metadata-persist-failure-deletes-lockbox-and-does-not-cache-test
  (async done
    (let [store (wallet-store (agent-state :approving :passkey))
          deleted-lockboxes (atom [])
          cache-calls (atom [])
          raw-persist-calls (atom [])]
      (-> (agent-runtime/enable-agent-trading!
           (enable-base-opts
            store
            {:options {:storage-mode :local
                       :local-protection-mode :passkey}
             :passkey-lock-supported? (fn [] true)
             :create-locked-session! (fn [_]
                                       (js/Promise.resolve
                                        {:metadata {:agent-address "0xagent" :credential-id "cred" :prf-salt "salt"}
                                         :session {:agent-address "0xagent" :private-key "0xpriv"}}))
             :persist-passkey-session-metadata! (fn [_ _] false)
             :delete-locked-session! (fn [owner-address]
                                       (swap! deleted-lockboxes conj owner-address)
                                       (js/Promise.resolve true))
             :cache-unlocked-session! (fn [& args]
                                        (swap! cache-calls conj (vec args))
                                        true)
             :persist-agent-session-by-mode! (fn [& args]
                                               (swap! raw-persist-calls conj (vec args))
                                               true)}))
          (.then
           (fn [_]
             (is (= :error (get-in @store [:wallet :agent :status])))
             (is (= "Unable to persist agent credentials."
                    (get-in @store [:wallet :agent :error])))
             (is (= ["0xowner"] @deleted-lockboxes))
             (is (empty? @cache-calls))
             (is (empty? @raw-persist-calls))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest lock-agent-trading-locks-passkey-ready-only-and-preserves-unlock-metadata-test
  (let [clear-calls (atom [])
        passkey-store (atom {:wallet {:address "0xpasskey-owner"
                                      :agent {:status :ready :storage-mode :local :local-protection-mode :passkey
                                              :agent-address "0xpasskey-agent" :last-approved-at 1700000000500
                                              :nonce-cursor 1700000000500 :credential-id "cred"
                                              :error "old error" :recovery-modal-open? true}}})
        plain-store (atom {:wallet {:address "0xplain-owner"
                                    :agent {:status :ready :storage-mode :local
                                            :local-protection-mode :plain
                                            :agent-address "0xplain-agent"}}})
        missing-wallet-store (atom {:wallet {:address nil
                                             :agent {:status :ready :storage-mode :local
                                                     :local-protection-mode :passkey
                                                     :agent-address "0xmissing-wallet-agent"}}})
        lock! (fn [store]
                (agent-runtime/lock-agent-trading!
                 {:store store
                  :normalize-storage-mode identity
                  :normalize-local-protection-mode identity
                  :clear-unlocked-session! (fn [owner-address]
                                             (swap! clear-calls conj owner-address))}))]
    (lock! passkey-store)
    (lock! plain-store)
    (lock! missing-wallet-store)
    (is (= ["0xpasskey-owner"] @clear-calls))
    (is (= :locked (get-in @passkey-store [:wallet :agent :status])))
    (is (= "0xpasskey-agent" (get-in @passkey-store [:wallet :agent :agent-address])))
    (is (= 1700000000500 (get-in @passkey-store [:wallet :agent :last-approved-at])))
    (is (= 1700000000500 (get-in @passkey-store [:wallet :agent :nonce-cursor])))
    (is (nil? (get-in @passkey-store [:wallet :agent :error])))
    (is (false? (get-in @passkey-store [:wallet :agent :recovery-modal-open?])))
    (is (= :ready (get-in @plain-store [:wallet :agent :status])))
    (is (= "0xplain-agent" (get-in @plain-store [:wallet :agent :agent-address])))
    (is (= :ready (get-in @missing-wallet-store [:wallet :agent :status])))
    (is (= "0xmissing-wallet-agent" (get-in @missing-wallet-store [:wallet :agent :agent-address])))))

(deftest enable-agent-trading-ignores-stale-completion-after-protection-mode-switch-test
  (async done
    (let [approval (deferred)
          raw-persist-calls (atom [])
          cache-calls (atom [])
          protection-mode-prefs (atom [])
          store (wallet-store (agent-state :approving :plain))
          enable-promise (agent-runtime/enable-agent-trading!
                          (enable-base-opts
                           store
                           {:options {:storage-mode :local :local-protection-mode :plain}
                            :create-agent-credentials! (fn []
                                                         {:private-key "0xstale-priv" :agent-address "0xstale-agent"})
                            :approve-agent! (fn [& _] (:promise approval))
                            :persist-agent-session-by-mode! (fn [& args]
                                                              (swap! raw-persist-calls conj (vec args))
                                                              true)
                            :cache-unlocked-session! (fn [& args]
                                                       (swap! cache-calls conj (vec args))
                                                       true)}))]
      (agent-runtime/set-agent-local-protection-mode!
       {:store store
        :local-protection-mode :passkey
        :normalize-local-protection-mode identity
        :normalize-storage-mode identity
        :persist-local-protection-mode-preference! (fn [mode]
                                                     (swap! protection-mode-prefs conj mode)
                                                     true)
        :default-agent-state (fn [& {:keys [storage-mode local-protection-mode]}]
                               {:status :not-ready :storage-mode storage-mode
                                :local-protection-mode local-protection-mode})})
      ((:resolve! approval) (ok-response))
      (-> enable-promise
          (.then
           (fn [_]
             (is (= [:passkey] @protection-mode-prefs))
             (is (= :passkey (get-in @store [:wallet :agent :local-protection-mode])))
             (is (not= "0xstale-agent" (get-in @store [:wallet :agent :agent-address])))
             (is (empty? @raw-persist-calls)
                 "stale plain enable must not raw-persist after passkey preference wins")
             (is (empty? @cache-calls)
                 "stale plain enable must not cache unlocked credentials")
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest duplicate-enable-agent-trading-newer-completion-wins-test
  (async done
    (let [older-approval (deferred)
          newer-approval (deferred)
          credential-queue (atom [{:private-key "0xold-priv" :agent-address "0xold-agent"}
                                  {:private-key "0xnew-priv" :agent-address "0xnew-agent"}])
          nonce-queue (atom [1700000000600 1700000000700])
          persist-calls (atom [])
          cache-calls (atom [])
          store (wallet-store (agent-state :approving :plain))
          enable-once (fn []
                        (agent-runtime/enable-agent-trading!
                         (enable-base-opts
                          store
                          {:options {:storage-mode :local :local-protection-mode :plain}
                           :create-agent-credentials! (fn []
                                                        (let [credential (first @credential-queue)]
                                                          (swap! credential-queue subvec 1)
                                                          credential))
                           :now-ms-fn (fn []
                                        (let [nonce (first @nonce-queue)]
                                          (swap! nonce-queue subvec 1)
                                          nonce))
                           :approve-agent! (fn [_store _owner-address action]
                                             (case (:agentAddress action)
                                               "0xold-agent" (:promise older-approval)
                                               "0xnew-agent" (:promise newer-approval)))
                           :persist-agent-session-by-mode! (fn [& args]
                                                             (swap! persist-calls conj (vec args))
                                                             true)
                           :cache-unlocked-session! (fn [& args]
                                                      (swap! cache-calls conj (vec args))
                                                      true)})))
          older-enable (enable-once)
          newer-enable (enable-once)]
      ((:resolve! newer-approval) (ok-response))
      (-> newer-enable
          (.then
           (fn [_]
             ((:resolve! older-approval) (ok-response))
             older-enable))
          (.then
           (fn [_]
             (is (= "0xnew-agent" (get-in @store [:wallet :agent :agent-address])))
             (is (= 1700000000700 (get-in @store [:wallet :agent :last-approved-at])))
             (is (= 1700000000700 (get-in @store [:wallet :agent :nonce-cursor])))
             (is (= "0xnew-agent" (get-in (last @persist-calls) [2 :agent-address])))
             (is (= "0xnew-agent" (get-in (last @cache-calls) [1 :agent-address])))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest stale-unlock-failure-does-not-revert-new-ready-session-test
  (async done
    (let [unlock (deferred)
          old-metadata {:agent-address "0xold-agent" :credential-id "old-cred"
                        :prf-salt "old-salt" :last-approved-at 1700000000800
                        :nonce-cursor 1700000000800}
          store (wallet-store (assoc (agent-state :locked :passkey)
                                     :agent-address "0xold-agent"
                                     :last-approved-at 1700000000800
                                     :nonce-cursor 1700000000800))
          unlock-promise (agent-runtime/unlock-agent-trading!
                          {:store store
                           :normalize-storage-mode identity
                           :normalize-local-protection-mode identity
                           :load-passkey-session-metadata (fn [_] old-metadata)
                           :unlock-locked-session! (fn [_] (:promise unlock))
                           :runtime-error-message (fn [err] (.-message err))})]
      (swap! store assoc-in [:wallet :agent]
             {:status :ready
              :storage-mode :local
              :local-protection-mode :passkey
              :agent-address "0xnew-agent"
              :last-approved-at 1700000000900
              :nonce-cursor 1700000000900
              :error nil})
      ((:reject! unlock) (js/Error. "old unlock failed"))
      (-> unlock-promise
          (.then
           (fn [_]
             (is (= :ready (get-in @store [:wallet :agent :status])))
             (is (= "0xnew-agent" (get-in @store [:wallet :agent :agent-address])))
             (is (= 1700000000900 (get-in @store [:wallet :agent :last-approved-at])))
             (is (= 1700000000900 (get-in @store [:wallet :agent :nonce-cursor])))
             (is (nil? (get-in @store [:wallet :agent :error])))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest enable-agent-trading-persistence-failure-cleans-stale-session-and-cache-test
  (async done
    (let [persisted-state (atom {"0xowner" {:local {:agent-address "0xstale-agent"
                                                    :private-key "0xstale-priv"}}})
          cached-state (atom {"0xowner" {:agent-address "0xstale-agent" :private-key "0xstale-priv"}})
          persist-calls (atom [])
          clear-persisted-calls (atom [])
          clear-cache-calls (atom [])
          store (wallet-store (agent-state :approving :plain))]
      (-> (agent-runtime/enable-agent-trading!
           (enable-base-opts
            store
            {:options {:storage-mode :local :local-protection-mode :plain}
             :create-agent-credentials! (fn []
                                          {:private-key "0xnew-priv" :agent-address "0xnew-agent"})
             :persist-agent-session-by-mode! (fn [& args]
                                               (swap! persist-calls conj (vec args))
                                               false)
             :clear-agent-session-by-mode! (fn [owner-address storage-mode]
                                             (swap! clear-persisted-calls conj [owner-address storage-mode])
                                             (swap! persisted-state update owner-address dissoc storage-mode)
                                             true)
             :clear-unlocked-session! (fn [owner-address]
                                        (swap! clear-cache-calls conj owner-address)
                                        (swap! cached-state dissoc owner-address)
                                        true)}))
          (.then
           (fn [_]
             (is (= :error (get-in @store [:wallet :agent :status])))
             (is (= "Unable to persist agent credentials."
                    (get-in @store [:wallet :agent :error])))
             (is (= [["0xowner" :local {:agent-address "0xnew-agent"
                                         :private-key "0xnew-priv"
                                         :last-approved-at 1700000000000
                                         :nonce-cursor 1700000000000}]]
                    @persist-calls))
             (is (= [["0xowner" :local]] @clear-persisted-calls))
             (is (= ["0xowner"] @clear-cache-calls))
             (is (nil? (get-in @persisted-state ["0xowner" :local])))
             (is (nil? (get @cached-state "0xowner")))
             (done)))
          (.catch (async-support/unexpected-error done))))))
