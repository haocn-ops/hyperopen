(ns hyperopen.wallet.agent-lockbox-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.platform.indexed-db :as indexed-db]
            [hyperopen.platform.webauthn :as webauthn]
            [hyperopen.test-support.async :as async-support]
            [hyperopen.wallet.agent-lockbox :as agent-lockbox]))

(def ^:private baseline-passkey-lock-supported?
  agent-lockbox/passkey-lock-supported?)

(def ^:private baseline-random-bytes
  webauthn/random-bytes)

(def ^:private baseline-create-passkey-credential!
  webauthn/create-passkey-credential!)

(def ^:private baseline-eval-prf!
  webauthn/eval-prf!)

(def ^:private baseline-put-json!
  indexed-db/put-json!)

(def ^:private baseline-get-json!
  indexed-db/get-json!)

(defn- uint8-array
  [values]
  (js/Uint8Array. (clj->js values)))

(defn- repeat-bytes
  [size]
  (uint8-array (map #(mod % 251) (range 1 (inc size)))))

(defn- with-lockbox-stubs
  [{:keys [passkey-lock-supported?
           random-bytes
           create-passkey-credential!
           eval-prf!
           put-json!
           get-json!]} f]
  (let [restore! (fn []
                   (set! agent-lockbox/passkey-lock-supported? baseline-passkey-lock-supported?)
                   (set! webauthn/random-bytes baseline-random-bytes)
                   (set! webauthn/create-passkey-credential! baseline-create-passkey-credential!)
                   (set! webauthn/eval-prf! baseline-eval-prf!)
                   (set! indexed-db/put-json! baseline-put-json!)
                   (set! indexed-db/get-json! baseline-get-json!)
                   (agent-lockbox/clear-all-unlocked-sessions!))]
    (set! agent-lockbox/passkey-lock-supported?
          (or passkey-lock-supported? baseline-passkey-lock-supported?))
    (set! webauthn/random-bytes (or random-bytes baseline-random-bytes))
    (set! webauthn/create-passkey-credential!
          (or create-passkey-credential! baseline-create-passkey-credential!))
    (set! webauthn/eval-prf! (or eval-prf! baseline-eval-prf!))
    (set! indexed-db/put-json! (or put-json! baseline-put-json!))
    (set! indexed-db/get-json! (or get-json! baseline-get-json!))
    (agent-lockbox/clear-all-unlocked-sessions!)
    (try
      (let [result (f)]
        (if (instance? js/Promise result)
          (.finally result restore!)
          (do
            (restore!)
            result)))
      (catch :default e
        (restore!)
        (throw e)))))

(defn- expect-rejection-message!
  [promise expected-message]
  (-> promise
      (.then (fn [value]
               (is false (str "Expected rejection, got: " value))
               nil))
      (.catch (fn [err]
                (is (= expected-message (.-message err)))
                nil))))

(deftest create-locked-session-validates-wallet-session-and-browser-support-test
  (async done
    (let [session {:agent-address "0x9999999999999999999999999999999999999999"
                   :private-key "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
          fail! (async-support/unexpected-error done)]
      (-> (with-lockbox-stubs
            {:passkey-lock-supported? (constantly true)}
            (fn []
              (agent-lockbox/create-locked-session! {:wallet-address nil
                                                     :session session})))
          (.then (fn [value]
                   (is false (str "Expected rejection, got: " value))
                   (done)))
          (.catch
           (fn [err]
             (is (= "Connect your wallet before enabling passkey lock."
                    (.-message err)))
             (-> (with-lockbox-stubs
                   {:passkey-lock-supported? (constantly true)}
                   (fn []
                     (agent-lockbox/create-locked-session!
                      {:wallet-address "0x1234567890abcdef1234567890abcdef12345678"
                       :session {:agent-address "0x9999999999999999999999999999999999999999"}})))
                 (.then (fn [value]
                          (is false (str "Expected rejection, got: " value))
                          (done)))
                 (.catch
                  (fn [err*]
                    (is (= "Trading session data is unavailable for passkey lock."
                           (.-message err*)))
                    (-> (with-lockbox-stubs
                          {:passkey-lock-supported? (constantly false)}
                          (fn []
                            (agent-lockbox/create-locked-session!
                             {:wallet-address "0x1234567890abcdef1234567890abcdef12345678"
                              :session session})))
                        (.then (fn [value]
                                 (is false (str "Expected rejection, got: " value))
                                 (done)))
                        (.catch
                         (fn [err**]
                           (is (= "Passkey locking is unavailable in this browser."
                                  (.-message err**)))
                           (done)))
                        (.catch fail!))))
                 (.catch fail!))))
          (.catch fail!)))))

(deftest create-locked-session-surfaces-blank-credential-id-and-persist-failures-test
  (async done
    (let [session {:agent-address "0x9999999999999999999999999999999999999999"
                   :private-key "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
          fail! (async-support/unexpected-error done)]
      (-> (with-lockbox-stubs
            {:passkey-lock-supported? (constantly true)
             :random-bytes repeat-bytes
             :create-passkey-credential! (fn [_]
                                           (js/Promise.resolve {:credential-id "   "
                                                                :prf-first (repeat-bytes 32)}))}
            (fn []
              (agent-lockbox/create-locked-session! {:wallet-address "0x1234567890abcdef1234567890abcdef12345678"
                                                     :session session})))
          (.then (fn [value]
                   (is false (str "Expected rejection, got: " value))
                   (done)))
          (.catch
           (fn [err]
             (is (= "Unable to create a passkey credential for trading unlock."
                    (.-message err)))
             (-> (with-lockbox-stubs
                   {:passkey-lock-supported? (constantly true)
             :random-bytes repeat-bytes
             :create-passkey-credential! (fn [_]
                                           (js/Promise.resolve {:credential-id "cred"
                                                                :prf-first (repeat-bytes 32)}))
             :put-json! (fn
                          ([_store _key _record]
                           (js/Promise.resolve false))
                          ([_store _key _record _opts]
                           (js/Promise.resolve false)))}
                   (fn []
                     (agent-lockbox/create-locked-session!
                      {:wallet-address "0x1234567890abcdef1234567890abcdef12345678"
                       :session session})))
                 (.then (fn [value]
                          (is false (str "Expected rejection, got: " value))
                          (done)))
                 (.catch
                  (fn [err*]
                    (is (= "Unable to persist the passkey trading lockbox."
                           (.-message err*)))
                    (done)))
                 (.catch fail!))))
          (.catch fail!)))))

(deftest create-and-unlock-locked-session-roundtrip-test
  (async done
    (let [wallet-address "0x1234567890abcdef1234567890abcdef12345678"
          session {:agent-address "0x9999999999999999999999999999999999999999"
                   :private-key "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                   :last-approved-at 1700000000001
                   :nonce-cursor 1700000000002}
          stored-record (atom nil)
          eval-calls (atom [])
          prf-output (repeat-bytes 32)
          promise
          (with-lockbox-stubs
            {:passkey-lock-supported? (constantly true)
             :random-bytes repeat-bytes
             :create-passkey-credential! (fn [_]
                                           (js/Promise.resolve {:credential-id "cred-123"
                                                                :prf-first nil}))
             :eval-prf! (fn [request]
                          (swap! eval-calls conj request)
                          (js/Promise.resolve {:prf-first prf-output}))
             :put-json! (fn
                          ([_store key record]
                           (reset! stored-record {:key key
                                                  :record record})
                           (js/Promise.resolve true))
                          ([_store key record _opts]
                           (reset! stored-record {:key key
                                                  :record record})
                           (js/Promise.resolve true)))
             :get-json! (fn
                          ([_store key]
                           (is (= wallet-address key))
                           (js/Promise.resolve (:record @stored-record)))
                          ([_store key _opts]
                           (is (= wallet-address key))
                           (js/Promise.resolve (:record @stored-record))))}
            (fn []
              (-> (agent-lockbox/create-locked-session! {:wallet-address wallet-address
                                                         :session session
                                                         :now-ms-fn (constantly 1700000000999)})
                  (.then
                   (fn [{:keys [metadata session] :as result}]
                     (is (= wallet-address (:key @stored-record)))
                     (is (= :locked (:record-kind metadata)))
                     (is (= 1 (:record-version metadata)))
                     (is (= "cred-123" (:credential-id metadata)))
                     (is (= 1700000000999 (:saved-at-ms metadata)))
                     (is (= (:agent-address session) (:agent-address metadata)))
                     (is (= session (:session result)))
                     (is (= 1 (count @eval-calls)))
                     (-> (agent-lockbox/unlock-locked-session! {:metadata metadata
                                                                :wallet-address wallet-address})
                         (.then
                          (fn [unlocked]
                            (is (= (:agent-address session) (:agent-address unlocked)))
                            (is (= (:private-key session) (:private-key unlocked)))
                            (is (= session
                                   (agent-lockbox/load-unlocked-session wallet-address)))
                            unlocked))))))))]
      (-> promise
          (.then (fn [_]
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest unlock-locked-session-validates-metadata-and-missing-records-test
  (async done
    (let [wallet-address "0x1234567890abcdef1234567890abcdef12345678"]
      (-> (expect-rejection-message!
           (with-lockbox-stubs
             {:passkey-lock-supported? (constantly true)}
             (fn []
               (agent-lockbox/unlock-locked-session! {:wallet-address nil
                                                      :metadata {:credential-id "cred"
                                                                 :prf-salt "salt"}})))
           "Connect your wallet before unlocking trading.")
          (.then
           (fn [_]
             (expect-rejection-message!
              (with-lockbox-stubs
                {:passkey-lock-supported? (constantly true)}
                (fn []
                  (agent-lockbox/unlock-locked-session! {:wallet-address wallet-address
                                                         :metadata {:credential-id ""
                                                                    :prf-salt "salt"}})))
              "Passkey credential data is unavailable.")))
          (.then
           (fn [_]
             (expect-rejection-message!
              (with-lockbox-stubs
                {:passkey-lock-supported? (constantly true)}
                (fn []
                  (agent-lockbox/unlock-locked-session! {:wallet-address wallet-address
                                                         :metadata {:credential-id "cred"
                                                                    :prf-salt nil}})))
              "Passkey lockbox data is unavailable.")))
          (.then
           (fn [_]
             (expect-rejection-message!
              (with-lockbox-stubs
                {:passkey-lock-supported? (constantly true)
                 :eval-prf! (fn [_]
                              (js/Promise.resolve {:prf-first (repeat-bytes 32)}))
                 :get-json! (fn
                              ([_store _key]
                               (js/Promise.resolve nil))
                              ([_store _key _opts]
                               (js/Promise.resolve nil)))}
                (fn []
                  (agent-lockbox/unlock-locked-session!
                   {:wallet-address wallet-address
                    :metadata {:agent-address "0x9999999999999999999999999999999999999999"
                               :credential-id "cred"
                               :prf-salt "AQIDBA"}})))
              "No passkey lockbox was found for this wallet.")))
          (.then (fn [_]
                   (done)))
          (.catch (async-support/unexpected-error done))))))
