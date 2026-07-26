(ns hyperopen.funding.infrastructure.hyperunit-address-client-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [async deftest is]]
            [hyperopen.funding.infrastructure.hyperunit-address-client :as hyperunit-address-client]
            [hyperopen.test-support.async :as async-support]))

(defn- ok-json-response
  [payload]
  #js {:ok true
       :status 200
       :json (fn []
               (js/Promise.resolve (clj->js payload)))})

(defn- error-text-response
  [status text]
  #js {:ok false
       :status status
       :text (fn []
               (js/Promise.resolve text))})

(defn- with-fetch-stub!
  [fake-fetch f]
  (let [original-fetch (.-fetch js/globalThis)]
    (set! (.-fetch js/globalThis) fake-fetch)
    (f (fn []
         (set! (.-fetch js/globalThis) original-fetch)))))

(deftest fetch-hyperunit-address-builds-url-and-normalizes-success-payload-test
  (async done
    (let [calls (atom [])]
      (with-fetch-stub!
        (fn [url init]
          (swap! calls conj [url (js->clj init :keywordize-keys true)])
          (js/Promise.resolve
           (ok-json-response
            {:address "bc1qgenerated"
             :status "  signed  "
             :signatures {"hl-node" "sig-a"}})))
        (fn [restore!]
          (let [promise (hyperunit-address-client/fetch-hyperunit-address!
                         "https://hyperunit.example"
                         "bitcoin testnet"
                         "hyperliquid"
                         "BTC+"
                         "0xDest/Path")]
            (-> promise
                (.then (fn [result]
                         (let [[url init] (first @calls)]
                           (is (= "https://hyperunit.example/gen/bitcoin%20testnet/hyperliquid/BTC%2B/0xDest%2FPath"
                                  url))
                           (is (= {:method "GET"}
                                  init)))
                         (is (= "bc1qgenerated" (:address result)))
                         (is (= "signed" (:status result)))
                         (is (= "sig-a"
                                (or (get (:signatures result) "hl-node")
                                    (get (:signatures result) :hl-node))))))
                (.catch (async-support/unexpected-error done))
                (.finally (fn []
                            (restore!)
                            (done))))))))))

(deftest fetch-hyperunit-address-rejects-with-http-error-message-test
  (async done
    (with-fetch-stub!
      (fn [_url _init]
        (js/Promise.resolve (error-text-response 502 "  ")))
      (fn [restore!]
        (let [promise (hyperunit-address-client/fetch-hyperunit-address!
                       "https://hyperunit.example"
                       "bitcoin"
                       "hyperliquid"
                       "btc"
                       "0xabc")]
          (-> promise
              (.then (fn [_]
                       (is false "Expected HTTP failure to reject")))
              (.catch (fn [err]
                        (is (= "HyperUnit address request failed (502): Unknown response"
                               (.-message err)))))
              (.finally (fn []
                          (restore!)
                          (done)))))))))

(deftest fetch-hyperunit-address-rejects-when-payload-carries-service-error-test
  (async done
    (with-fetch-stub!
      (fn [_url _init]
        (js/Promise.resolve
         (ok-json-response {:message "Try a different source chain."})))
      (fn [restore!]
        (let [promise (hyperunit-address-client/fetch-hyperunit-address!
                       "https://hyperunit.example"
                       "bitcoin"
                       "hyperliquid"
                       "btc"
                       "0xabc")]
          (-> promise
              (.then (fn [_]
                       (is false "Expected payload error to reject")))
              (.catch (fn [err]
                        (is (= "Try a different source chain."
                               (.-message err)))))
              (.finally (fn []
                          (restore!)
                          (done)))))))))

(deftest fetch-hyperunit-address-rejects-when-address-is-missing-test
  (async done
    (with-fetch-stub!
      (fn [_url _init]
        (js/Promise.resolve
         (ok-json-response {:status "pending"})))
      (fn [restore!]
        (let [promise (hyperunit-address-client/fetch-hyperunit-address!
                       "https://hyperunit.example"
                       "bitcoin"
                       "hyperliquid"
                       "btc"
                       "0xabc")]
          (-> promise
              (.then (fn [_]
                       (is false "Expected missing address payload to reject")))
              (.catch (fn [err]
                        (is (= "HyperUnit address response missing deposit address."
                               (.-message err)))))
              (.finally (fn []
                          (restore!)
                          (done)))))))))

(deftest fetch-hyperunit-address-with-source-fallbacks-retries-source-candidates-test
  (async done
    (let [request-opts (atom nil)
          calls (atom [])]
      (with-fetch-stub!
        (fn [url init]
          (swap! calls conj [url (js->clj init :keywordize-keys true)])
          (if (str/includes? url "/gen/BTC/")
            (js/Promise.resolve (error-text-response 502 "candidate failed"))
            (js/Promise.resolve (ok-json-response {:address "bc1qok"}))))
        (fn [restore!]
          (-> (hyperunit-address-client/fetch-hyperunit-address-with-source-fallbacks!
               {:base-url "https://primary"
                :base-urls ["https://backup"]
                :source-chain "bitcoin"
                :destination-chain "hyperliquid"
                :asset "btc"
                :destination-address "0xabc"
                :source-chain-candidates ["BTC" "bitcoin"]
                :with-base-url-fallbacks! (fn [opts]
                                            (reset! request-opts opts)
                                            ((:request-fn opts) "https://backup"))})
              (.then (fn [result]
                       (is (= {:address "bc1qok"
                               :status nil
                               :signatures nil}
                              result))
                       (is (= "Unable to generate HyperUnit deposit address."
                              (:error-message @request-opts)))
                       (is (= [["https://backup/gen/BTC/hyperliquid/btc/0xabc"
                                {:method "GET"}]
                               ["https://backup/gen/bitcoin/hyperliquid/btc/0xabc"
                                {:method "GET"}]]
                              @calls))))
              (.catch (async-support/unexpected-error done))
              (.finally (fn []
                          (restore!)
                          (done)))))))))

(deftest fetch-hyperunit-address-with-source-fallbacks-derives-canonical-source-when-candidates-missing-test
  (async done
    (let [calls (atom [])]
      (with-fetch-stub!
        (fn [url init]
          (swap! calls conj [url (js->clj init :keywordize-keys true)])
          (js/Promise.resolve (ok-json-response {:address "bc1qderived"})))
        (fn [restore!]
          (-> (hyperunit-address-client/fetch-hyperunit-address-with-source-fallbacks!
               {:base-url "https://primary"
                :source-chain "btc"
                :destination-chain "hyperliquid"
                :asset "btc"
                :destination-address "0xabc"
                :canonical-chain-token (fn [_] nil)
                :canonical-token (fn [chain]
                                   (when (= chain "btc")
                                     "bitcoin"))
                :with-base-url-fallbacks! (fn [opts]
                                            ((:request-fn opts) "https://primary"))})
              (.then (fn [result]
                       (is (= {:address "bc1qderived"
                               :status nil
                               :signatures nil}
                              result))
                       (is (= [["https://primary/gen/bitcoin/hyperliquid/btc/0xabc"
                                {:method "GET"}]]
                              @calls))))
              (.catch (async-support/unexpected-error done))
              (.finally (fn []
                          (restore!)
                          (done)))))))))

(deftest hyperunit-request-error-message-maps-network-errors-to-friendly-copy-test
  (is (= "Unable to reach HyperUnit address service for BTC on Bitcoin. Check your network and retry. If this persists in local dev, route HyperUnit through a same-origin proxy."
         (hyperunit-address-client/hyperunit-request-error-message
          (js/Error. "Failed to fetch")
          {:asset "btc"
           :source-chain "bitcoin"})))
  (is (= "custom failure"
         (hyperunit-address-client/hyperunit-request-error-message
          (js/Error. "custom failure")
          {:asset "btc"
           :source-chain "bitcoin"}))))
