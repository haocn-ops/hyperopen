(ns hyperopen.funding.infrastructure.route-clients-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.funding.infrastructure.route-clients :as route-clients]
            [hyperopen.test-support.async :as async-support]))

(def ^:private from-address
  "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")

(def ^:private to-address
  "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")

(def ^:private token-a
  "0xToken/Source")

(def ^:private token-b
  "0xToken+Target")

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

(deftest across-approval->swap-config-normalizes-transactions-and-filters-invalid-steps-test
  (is (= {:swap-tx {:to "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    :data "0xdeadbeef"}
          :approval-txs [{:to "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                          :data "0xcafe"
                          :value "0xf"}
                         {:to "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                          :data "0xbeef"}]}
         (route-clients/across-approval->swap-config
          {:swapTx {:to from-address
                    :data " 0xDEADBEEF "
                    :value "0"}
           :approvalTxns [{:to to-address
                           :data "0xCAFE"
                           :value "15"}
                          {:to to-address
                           :data "0xBEEF"
                           :value "0x0"}
                          {:to "invalid"
                           :data "0x1"
                           :value "1"}
                          {:to to-address
                           :data "not-hex"
                           :value "3"}]}))))

(deftest lifi-quote->swap-config-requires-complete-valid-shapes-test
  (let [config (route-clients/lifi-quote->swap-config
                {:action {:fromToken {:address from-address}}
                 :estimate {:approvalAddress to-address
                            :fromAmount "42"}
                 :transactionRequest {:to from-address
                                      :data " 0xabc123 "
                                      :value "0x12"}})]
    (is (= "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
           (:approval-address config)))
    (is (= "42"
           (.toString (:from-amount-units config) 10)))
    (is (= "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
           (:swap-token-address config)))
    (is (= "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
           (:swap-to-address config)))
    (is (= "0xabc123" (:swap-data config)))
    (is (= "0x12" (:swap-value config))))
  (is (nil? (route-clients/lifi-quote->swap-config
             {:action {:fromToken {:address "invalid"}}
              :estimate {:approvalAddress to-address
                         :fromAmount "abc"}
              :transactionRequest {:to from-address
                                   :data " "
                                   :value "0"}}))))

(deftest lifi-quote-url-builds-encoded-query-and-defaults-integrator-test
  (is (= (str "https://li.quest/v1/quote?"
              "fromChain=1"
              "&toChain=42161"
              "&fromToken=0xToken%2FSource"
              "&toToken=0xToken%2BTarget"
              "&fromAddress=0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
              "&fromAmount=42"
              "&slippage=0.005"
              "&integrator=hyperopen")
         (@#'hyperopen.funding.infrastructure.route-clients/lifi-quote-url
          {:from-address from-address
           :amount-units (js/BigInt "42")
           :to-token-address token-b
           :from-chain-id 1
           :to-chain-id 42161
           :from-token-address token-a
           :integrator nil}))))

(deftest route-client-private-normalizers-cover-empty-zero-and-invalid-inputs-test
  (is (nil? (@#'hyperopen.funding.infrastructure.route-clients/normalize-address "bad-address")))
  (is (= "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
         (@#'hyperopen.funding.infrastructure.route-clients/normalize-address from-address)))
  (is (nil? (@#'hyperopen.funding.infrastructure.route-clients/maybe-value-field "0x0")))
  (is (= "0x12"
         (@#'hyperopen.funding.infrastructure.route-clients/maybe-value-field " 0x12 ")))
  (is (= "0xab"
         (@#'hyperopen.funding.infrastructure.route-clients/normalize-hex-data " 0xAB ")))
  (is (nil? (@#'hyperopen.funding.infrastructure.route-clients/normalize-hex-data "not-hex")))
  (is (nil? (@#'hyperopen.funding.infrastructure.route-clients/normalize-hex-quantity nil)))
  (is (= "0x0"
         (@#'hyperopen.funding.infrastructure.route-clients/normalize-hex-quantity "0")))
  (is (= "0xff"
         (@#'hyperopen.funding.infrastructure.route-clients/normalize-hex-quantity "0xff")))
  (is (= "0x2a"
         (@#'hyperopen.funding.infrastructure.route-clients/normalize-hex-quantity "42")))
  (is (nil? (@#'hyperopen.funding.infrastructure.route-clients/normalize-hex-quantity "bad")))
  (is (= "3"
         (.toString (@#'hyperopen.funding.infrastructure.route-clients/parse-positive-bigint "3") 10)))
  (is (nil? (@#'hyperopen.funding.infrastructure.route-clients/parse-positive-bigint "0")))
  (is (nil? (@#'hyperopen.funding.infrastructure.route-clients/parse-positive-bigint "bad"))))

(deftest fetch-lifi-quote-builds-url-and-normalizes-success-response-test
  (async done
    (let [calls (atom [])]
      (with-fetch-stub!
        (fn [url init]
          (swap! calls conj [url (js->clj init :keywordize-keys true)])
          (js/Promise.resolve
           (ok-json-response {:id "quote-1"
                              :route {:tool "lifi"}})))
        (fn [restore!]
          (let [fail! (fn [err]
                        (restore!)
                        ((async-support/unexpected-error done) err))]
            (-> (route-clients/fetch-lifi-quote!
                 {:from-address from-address
                  :amount-units (js/BigInt "42")
                  :to-token-address token-b
                  :from-chain-id 1
                  :to-chain-id 42161
                  :from-token-address token-a
                  :integrator nil})
                (.then (fn [result]
                         (is (= [["https://li.quest/v1/quote?fromChain=1&toChain=42161&fromToken=0xToken%2FSource&toToken=0xToken%2BTarget&fromAddress=0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&fromAmount=42&slippage=0.005&integrator=hyperopen"
                                  {:method "GET"}]]
                                @calls))
                         (is (= {:id "quote-1"
                                 :route {:tool "lifi"}}
                                result))
                         (restore!)
                         (done)))
                (.catch fail!))))))))

(deftest fetch-lifi-quote-rejects-with-http-error-copy-test
  (async done
    (with-fetch-stub!
      (fn [_url _init]
        (js/Promise.resolve
         (error-text-response 503 "upstream unavailable")))
      (fn [restore!]
        (-> (route-clients/fetch-lifi-quote!
             {:from-address from-address
              :amount-units (js/BigInt "42")
              :to-token-address token-b
              :from-chain-id 1
              :to-chain-id 42161
              :from-token-address token-a
              :integrator nil})
            (.then (fn [_]
                     (restore!)
                     (is false "Expected LiFi error path to reject")
                     (done)))
            (.catch (fn [err]
                      (restore!)
                      (is (= "LiFi quote request failed (503): upstream unavailable"
                             (.-message err)))
                      (done))))))))

(deftest across-approval-url-builds-encoded-query-test
  (is (= (str "https://across.example/approval"
              "?tradeType=minOutput"
              "&amount=9000"
              "&inputToken=0xToken%2FSource"
              "&originChainId=1"
              "&outputToken=0xToken%2BTarget"
              "&destinationChainId=42161"
              "&depositor=0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
         (@#'hyperopen.funding.infrastructure.route-clients/across-approval-url
          {:base-url "https://across.example/approval"
           :from-address from-address
           :amount-units (js/BigInt "9000")
           :input-token-address token-a
           :origin-chain-id 1
           :output-token-address token-b
           :destination-chain-id 42161}))))

(deftest fetch-across-approval-builds-url-and-normalizes-success-response-test
  (async done
    (let [calls (atom [])]
      (with-fetch-stub!
        (fn [url init]
          (swap! calls conj [url (js->clj init :keywordize-keys true)])
          (js/Promise.resolve
           (ok-json-response {:approved true
                              :steps [{:kind "swap"}]})))
        (fn [restore!]
          (let [fail! (fn [err]
                        (restore!)
                        ((async-support/unexpected-error done) err))]
            (-> (route-clients/fetch-across-approval!
                 {:base-url "https://across.example/approval"
                  :from-address from-address
                  :amount-units (js/BigInt "9000")
                  :input-token-address token-a
                  :origin-chain-id 1
                  :output-token-address token-b
                  :destination-chain-id 42161})
                (.then (fn [result]
                         (is (= [["https://across.example/approval?tradeType=minOutput&amount=9000&inputToken=0xToken%2FSource&originChainId=1&outputToken=0xToken%2BTarget&destinationChainId=42161&depositor=0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                                  {:method "GET"}]]
                                @calls))
                         (is (= {:approved true
                                 :steps [{:kind "swap"}]}
                                result))
                         (restore!)
                         (done)))
                (.catch fail!))))))))

(deftest fetch-across-approval-rejects-with-http-error-copy-test
  (async done
    (with-fetch-stub!
      (fn [_url _init]
        (js/Promise.resolve
         (error-text-response 502 "bridge unavailable")))
      (fn [restore!]
        (-> (route-clients/fetch-across-approval!
             {:base-url "https://across.example/approval"
              :from-address from-address
              :amount-units (js/BigInt "9000")
              :input-token-address token-a
              :origin-chain-id 1
              :output-token-address token-b
              :destination-chain-id 42161})
            (.then (fn [_]
                     (restore!)
                     (is false "Expected Across error path to reject")
                     (done)))
            (.catch (fn [err]
                      (restore!)
                      (is (= "Across approval request failed (502): bridge unavailable"
                             (.-message err)))
                      (done))))))))
