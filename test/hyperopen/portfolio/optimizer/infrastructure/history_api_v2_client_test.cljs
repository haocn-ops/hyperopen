(ns hyperopen.portfolio.optimizer.infrastructure.history-api-v2-client-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.portfolio.optimizer.infrastructure.history-api-v2-client :as client]
            [hyperopen.test-support.async :as async-support]))

(defn- json-response
  [status payload]
  #js {:ok (<= 200 status 299)
       :status status
       :json (fn []
               (js/Promise.resolve (clj->js payload)))})

(deftest request-instruments-sends-request-id-and-keywordizes-response-test
  (async done
    (let [calls (atom [])
          fetch-fn (fn [url init]
                     (swap! calls conj [url (js->clj init)])
                     (js/Promise.resolve
                      (json-response
                       200
                       {:contract_version "optimizer-history-api-v2"
                        :request_id "rid-1"
                        :dataset_version "2026-05-11T00:00:00.000Z"
                        :status "ok"
                        :instruments [{:instrument_id "hl:perp:BTC"
                                       :aliases {:hyperopen_market_key "perp:BTC"}}]
                        :warnings []})))]
      (-> (client/request-instruments!
           {:fetch-fn fetch-fn
            :base-url "https://history.test/"
            :request-id (fn [] "rid-1")})
          (.then
           (fn [body]
             (is (= [["https://history.test/v1/optimizer/instruments"
                      {"method" "GET"
                       "headers" {"x-request-id" "rid-1"}}]]
                    @calls))
             (is (= "optimizer-history-api-v2" (:contract-version body)))
             (is (= "hl:perp:BTC"
                    (get-in body [:instruments 0 :instrument-id])))
             (is (= "perp:BTC"
                    (get-in body [:instruments 0 :aliases :hyperopen-market-key])))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-history-bundle-posts-strict-backend-ids-test
  (async done
    (let [calls (atom [])
          fetch-fn (fn [url init]
                     (swap! calls conj [url (js->clj init)])
                     (js/Promise.resolve
                      (json-response
                       200
                       {:contract_version "optimizer-history-api-v2"
                        :request_id "rid-2"
                        :dataset_version "dv-1"
                        :status "ok"
                        :series_by_instrument {}
                        :warnings []})))]
      (-> (client/request-history-bundle!
           {:fetch-fn fetch-fn
            :base-url "https://history.test"
            :request-id (fn [] "rid-2")
            :proxy-policy :approved-proxy-allowed
            :include-aligned-returns? true}
           {:bars 90
            :interval :1d
            :universe [{:instrument-id "perp:BTC"
                        :market-type :perp
                        :optimizer-history/instrument-id "hl:perp:BTC"}]})
          (.then
           (fn [_body]
             (let [[url init] (first @calls)
                   body (js->clj (js/JSON.parse (get init "body")))]
               (is (= "https://history.test/v1/optimizer/history-bundle" url))
               (is (= "POST" (get init "method")))
               (is (= {"content-type" "application/json"
                       "x-request-id" "rid-2"}
                      (get init "headers")))
               (is (= {"lookback_days" 90
                       "interval" "1d"
                       "proxy_policy" "approved_proxy_allowed"
                       "include_aligned_returns" true
                       "instruments" [{"client_instrument_id" "perp:BTC"
                                       "instrument_id" "hl:perp:BTC"}]}
                      body)))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-history-bundle-uses-default-allowed-trading-calendar-proxy-id-test
  (async done
    (let [calls (atom [])
          fetch-fn (fn [url init]
                     (swap! calls conj [url (js->clj init)])
                     (js/Promise.resolve
                      (json-response
                       200
                       {:contract_version "optimizer-history-api-v2"
                        :request_id "rid-spy"
                        :dataset_version "dv-spy"
                        :status "ok"
                        :series_by_instrument {}
                        :warnings []})))]
      (-> (client/request-history-bundle!
           {:fetch-fn fetch-fn
            :base-url "https://history.test"
            :request-id (fn [] "rid-spy")
            :proxy-policy :approved-proxy-allowed
            :include-aligned-returns? true}
           {:bars 365
            :interval :1d
            :universe [{:instrument-id "perp:xyz:SP500"
                        :market-type :perp
                        :optimizer-history/instrument-id "hl:hip3:xyz:SP500"
                        :optimizer-history/proxy
                        {:mapping-kind :stitched-native-proxy
                         :proxy-instrument-id "external:tiingo:SPY"
                         :provider "tiingo"
                         :optimizer-proxy-policy "default_allowed"}}]})
          (.then
           (fn [_body]
             (let [[_url init] (first @calls)
                   body (js->clj (js/JSON.parse (get init "body")))]
               (is (= [{"client_instrument_id" "perp:xyz:SP500"
                        "instrument_id" "external:tiingo:SPY"}]
                      (get body "instruments"))))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-history-bundle-keeps-target-id-for-native-only-policy-test
  (async done
    (let [calls (atom [])
          fetch-fn (fn [url init]
                     (swap! calls conj [url (js->clj init)])
                     (js/Promise.resolve
                      (json-response
                       200
                       {:contract_version "optimizer-history-api-v2"
                        :request_id "rid-native"
                        :dataset_version "dv-native"
                        :status "ok"
                        :series_by_instrument {}
                        :warnings []})))]
      (-> (client/request-history-bundle!
           {:fetch-fn fetch-fn
            :base-url "https://history.test"
            :request-id (fn [] "rid-native")
            :proxy-policy :native-only
            :include-aligned-returns? true}
           {:bars 365
            :interval :1d
            :universe [{:instrument-id "perp:xyz:SP500"
                        :market-type :perp
                        :optimizer-history/instrument-id "hl:hip3:xyz:SP500"
                        :optimizer-history/proxy
                        {:mapping-kind :stitched-native-proxy
                         :proxy-instrument-id "external:tiingo:SPY"
                         :provider "tiingo"
                         :optimizer-proxy-policy "default_allowed"}}]})
          (.then
           (fn [_body]
             (let [[_url init] (first @calls)
                   body (js->clj (js/JSON.parse (get init "body")))]
               (is (= [{"client_instrument_id" "perp:xyz:SP500"
                        "instrument_id" "hl:hip3:xyz:SP500"}]
                      (get body "instruments"))))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-history-bundle-rejects-http-400-without-retry-test
  (async done
    (let [calls (atom 0)
          fetch-fn (fn [_url _init]
                     (swap! calls inc)
                     (js/Promise.resolve
                      (json-response
                       400
                       {:contract_version "optimizer-history-api-v2"
                        :request_id "rid-400"
                        :error "invalid_request"
                        :message "bad request"})))]
      (-> (client/request-history-bundle!
           {:fetch-fn fetch-fn
            :base-url "https://history.test"
            :request-id (fn [] "rid-400")}
           {:universe [{:instrument-id "perp:BTC"
                        :optimizer-history/instrument-id "hl:perp:BTC"}]})
          (.then (fn [_]
                   (is false "HTTP 400 should reject")
                   (done)))
          (.catch (fn [err]
                    (is (= 1 @calls))
                    (is (= 400 (.-status err)))
                    (is (= "bad request" (.-message err)))
                    (is (= "rid-400" (.-requestId err)))
                    (done)))))))
