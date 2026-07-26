(ns hyperopen.platform.webauthn-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.platform.webauthn :as webauthn]
            [hyperopen.test-support.async :as async-support]))

(def default-pub-key-cred-params
  @#'hyperopen.platform.webauthn/default-pub-key-cred-params)

(defn- uint8-array
  [values]
  (js/Uint8Array. (clj->js values)))

(defn- fake-crypto
  []
  #js {:subtle #js {}
       :getRandomValues (fn [out]
                          (dotimes [idx (.-length out)]
                            (aset out idx (mod (inc idx) 255)))
                          out)})

(defn- with-webauthn-env
  [{:keys [crypto credentials is-secure-context public-key-credential]} f]
  (let [original-crypto (.-crypto js/globalThis)
        original-public-key-credential (.-PublicKeyCredential js/globalThis)
        original-is-secure-context (.-isSecureContext js/globalThis)
        original-navigator (.-navigator js/globalThis)
        navigator* (or original-navigator #js {})
        original-credentials (.-credentials navigator*)]
    (set! (.-navigator js/globalThis) navigator*)
    (set! (.-crypto js/globalThis) crypto)
    (set! (.-PublicKeyCredential js/globalThis) public-key-credential)
    (set! (.-isSecureContext js/globalThis) is-secure-context)
    (set! (.-credentials navigator*) credentials)
    (let [restore! (fn []
                     (set! (.-crypto js/globalThis) original-crypto)
                     (set! (.-PublicKeyCredential js/globalThis) original-public-key-credential)
                     (set! (.-isSecureContext js/globalThis) original-is-secure-context)
                     (set! (.-navigator js/globalThis) original-navigator)
                     (when navigator*
                       (set! (.-credentials navigator*) original-credentials)))]
      (try
        (let [result (f)]
          (if (instance? js/Promise result)
            (.finally result restore!)
            (do
              (restore!)
              result)))
        (catch :default e
          (restore!)
          (throw e))))))

(deftest create-passkey-credential-default-algorithms-test
  (is (= [{:type "public-key"
           :alg -7}
          {:type "public-key"
           :alg -257}]
         default-pub-key-cred-params)))

(deftest passkey-capability-hint-reflects-available-browser-probes-test
  (with-webauthn-env
    {:crypto (fake-crypto)
     :credentials #js {:create (fn [_] nil)
                       :get (fn [_] nil)}
     :is-secure-context true
     :public-key-credential #js {:getClientCapabilities (fn [] (js/Promise.resolve #js {}))}}
    (fn []
      (is (true? (webauthn/passkey-capability-hint?)))))
  (with-webauthn-env
    {:crypto (fake-crypto)
     :credentials #js {:create (fn [_] nil)
                       :get (fn [_] nil)}
     :is-secure-context false
     :public-key-credential #js {:getClientCapabilities (fn [] (js/Promise.resolve #js {}))}}
    (fn []
      (is (false? (webauthn/passkey-capability-hint?))))))

(deftest passkey-capable-returns-false-when-lock-support-is-missing-test
  (async done
    (-> (with-webauthn-env
          {:crypto nil
           :credentials nil
           :is-secure-context false
           :public-key-credential nil}
          (fn []
            (webauthn/passkey-capable?)))
        (.then (fn [supported?]
                 (is (false? supported?))
                 (done)))
        (.catch (async-support/unexpected-error done)))))

(deftest passkey-capable-prefers-client-capabilities-when-available-test
  (async done
    (let [fail! (async-support/unexpected-error done)]
      (-> (with-webauthn-env
            {:crypto (fake-crypto)
             :credentials #js {:create (fn [_] nil)
                               :get (fn [_] nil)}
             :is-secure-context true
             :public-key-credential
             #js {:getClientCapabilities
                  (fn []
                    (js/Promise.resolve
                     #js {"extension:prf" true
                          "passkeyPlatformAuthenticator" true}))}}
            (fn []
              (webauthn/passkey-capable?)))
          (.then (fn [supported?]
                   (is (true? supported?))
                   (-> (with-webauthn-env
                         {:crypto (fake-crypto)
                          :credentials #js {:create (fn [_] nil)
                                            :get (fn [_] nil)}
                          :is-secure-context true
                          :public-key-credential
                          #js {:getClientCapabilities
                               (fn []
                                 (js/Promise.resolve
                                  #js {"extension:prf" false
                                       "passkeyPlatformAuthenticator" true}))}}
                         (fn []
                           (webauthn/passkey-capable?)))
                       (.then (fn [supported?*]
                                (is (false? supported?*))
                                (done)))
                       (.catch fail!))))
          (.catch fail!)))))

(deftest passkey-capable-falls-back-through-capability-errors-and-legacy-api-test
  (async done
    (let [fail! (async-support/unexpected-error done)]
      (-> (with-webauthn-env
            {:crypto (fake-crypto)
             :credentials #js {:create (fn [_] nil)
                               :get (fn [_] nil)}
             :is-secure-context true
             :public-key-credential
             #js {:getClientCapabilities (fn []
                                           (js/Promise.reject (js/Error. "boom")))}}
            (fn []
              (webauthn/passkey-capable?)))
          (.then (fn [supported?]
                   (is (false? supported?))
                   (-> (with-webauthn-env
                         {:crypto (fake-crypto)
                          :credentials #js {:create (fn [_] nil)
                                            :get (fn [_] nil)}
                          :is-secure-context true
                          :public-key-credential
                          #js {:isUserVerifyingPlatformAuthenticatorAvailable
                               (fn []
                                 (js/Promise.resolve true))}}
                         (fn []
                           (webauthn/passkey-capable?)))
                       (.then (fn [legacy-supported?]
                                (is (true? legacy-supported?))
                                (-> (with-webauthn-env
                                      {:crypto (fake-crypto)
                                       :credentials #js {:create (fn [_] nil)
                                                         :get (fn [_] nil)}
                                       :is-secure-context true
                                       :public-key-credential
                                       #js {:isUserVerifyingPlatformAuthenticatorAvailable
                                            (fn []
                                              (js/Promise.reject (js/Error. "legacy boom")))}}
                                      (fn []
                                        (webauthn/passkey-capable?)))
                                    (.then (fn [legacy-rejected?]
                                             (is (false? legacy-rejected?))
                                             (-> (with-webauthn-env
                                                   {:crypto (fake-crypto)
                                                    :credentials #js {:create (fn [_] nil)
                                                                      :get (fn [_] nil)}
                                                    :is-secure-context true
                                                    :public-key-credential #js {}}
                                                   (fn []
                                                     (webauthn/passkey-capable?)))
                                                 (.then (fn [fallback-supported?]
                                                          (is (false? fallback-supported?))
                                                          (done)))
                                                 (.catch fail!))))
                                    (.catch fail!))))
                       (.catch fail!))))
          (.catch fail!)))))

(deftest create-passkey-credential-builds-request-and-returns-prf-result-test
  (async done
    (let [captured-public-key (atom nil)
          raw-id (uint8-array [1 2 3 4])
          prf-first (uint8-array [9 8 7])]
      (-> (with-webauthn-env
            {:crypto (fake-crypto)
             :credentials #js {:create (fn [request]
                                         (reset! captured-public-key (.-publicKey request))
                                         (js/Promise.resolve
                                          #js {:rawId raw-id
                                               :getClientExtensionResults
                                               (fn []
                                                 #js {:prf #js {:results #js {:first prf-first}}})}))
                               :get (fn [_] nil)}
             :is-secure-context true
             :public-key-credential #js {}}
            (fn []
              (webauthn/create-passkey-credential!
               {:display-name "Desk Agent"
                :prf-salt (uint8-array [4 5 6])
                :rp-name "Hyperopen QA"
                :timeout-ms 777
                :user-name "barry"})))
          (.then
           (fn [{:keys [credential-id prf-first extension-results]}]
             (is (= "AQIDBA" credential-id))
             (is (= [9 8 7] (vec prf-first)))
             (is (= [9 8 7]
                    (vec (aget extension-results "prf" "results" "first"))))
             (is (= "Hyperopen QA" (aget @captured-public-key "rp" "name")))
             (is (= "barry" (aget @captured-public-key "user" "name")))
             (is (= "Desk Agent" (aget @captured-public-key "user" "displayName")))
             (is (= 777 (aget @captured-public-key "timeout")))
             (is (= 2 (count (array-seq (aget @captured-public-key "pubKeyCredParams")))))
             (is (= "required"
                    (aget @captured-public-key "authenticatorSelection" "userVerification")))
             (is (= [98 97 114 114 121]
                    (vec (aget @captured-public-key "user" "id"))))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest create-passkey-credential-omits-prf-extension-without-salt-and-rejects-when-unsupported-test
  (async done
    (let [captured-public-key (atom nil)
          fail! (async-support/unexpected-error done)]
      (-> (with-webauthn-env
            {:crypto (fake-crypto)
             :credentials #js {:create (fn [request]
                                         (reset! captured-public-key (.-publicKey request))
                                         (js/Promise.resolve
                                          #js {:rawId (uint8-array [5 6 7])}))
                               :get (fn [_] nil)}
             :is-secure-context true
             :public-key-credential #js {}}
            (fn []
              (webauthn/create-passkey-credential!
               {:display-name "Fallback User"})))
          (.then
           (fn [{:keys [credential-id prf-first]}]
             (is (= "BQYH" credential-id))
             (is (nil? prf-first))
             (is (nil? (aget @captured-public-key "extensions")))
             (is (= [70 97 108 108 98 97 99 107 32 85 115 101 114]
                    (vec (aget @captured-public-key "user" "id"))))
             (-> (with-webauthn-env
                   {:crypto nil
                    :credentials nil
                    :is-secure-context false
                    :public-key-credential nil}
                   (fn []
                     (webauthn/create-passkey-credential! {:display-name "Nope"})))
                 (.then (fn [value]
                          (is false (str "Expected rejection, got: " value))
                          (done)))
                 (.catch (fn [err]
                           (is (= "Passkey locking requires a secure browser with WebAuthn and WebCrypto support."
                                  (.-message err)))
                           (done))))))
          (.catch fail!)))))
