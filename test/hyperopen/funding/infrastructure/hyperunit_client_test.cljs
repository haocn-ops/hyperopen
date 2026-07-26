(ns hyperopen.funding.infrastructure.hyperunit-client-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.gateway.funding-hyperunit :as funding-hyperunit-gateway]
            [hyperopen.funding.infrastructure.hyperunit-client :as hyperunit-client]
            [hyperopen.test-support.async :as async-support]))

(deftest with-hyperunit-base-url-fallbacks-retries-unique-candidates-after-failures-test
  (async done
    (let [calls (atom [])]
      (-> (hyperunit-client/with-hyperunit-base-url-fallbacks!
           {:base-url " https://primary "
            :base-urls [nil "" "https://primary" "https://backup" "https://final"]
            :error-message "Unable to load HyperUnit resource."
            :request-fn (fn [candidate]
                          (swap! calls conj candidate)
                          (case candidate
                            "https://primary" (throw (js/Error. "sync boom"))
                            "https://backup" (js/Promise.reject (js/Error. "async boom"))
                            (js/Promise.resolve {:base-url candidate})))})
          (.then (fn [result]
                   (is (= {:base-url "https://final"} result))
                   (is (= ["https://primary" "https://backup" "https://final"]
                          @calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest with-hyperunit-base-url-fallbacks-rejects-when-no-usable-candidates-exist-test
  (async done
    (-> (hyperunit-client/with-hyperunit-base-url-fallbacks!
         {:base-url "  "
          :base-urls [nil ""]
          :error-message "No HyperUnit base URL."
          :request-fn (fn [_]
                        (js/Promise.resolve :unexpected))})
        (.then (fn [_]
                 (is false "Expected empty candidate list to reject")))
        (.catch (fn [err]
                  (is (= "No HyperUnit base URL."
                         (.-message err)))
                  (done))))))

(deftest hyperunit-client-wrappers-pass-fetch-and-opts-to-gateway-test
  (async done
    (let [original-fetch (.-fetch js/globalThis)
          fake-fetch (fn [_url _init]
                       (js/Promise.resolve #js {}))
          calls (atom [])]
      (set! (.-fetch js/globalThis) fake-fetch)
      (with-redefs [funding-hyperunit-gateway/request-hyperunit-operations!
                    (fn [deps opts]
                      (swap! calls conj [:operations deps opts])
                      (js/Promise.resolve {:kind :operations}))
                    funding-hyperunit-gateway/request-hyperunit-estimate-fees!
                    (fn [deps opts]
                      (swap! calls conj [:fees deps opts])
                      (js/Promise.resolve {:kind :fees}))
                    funding-hyperunit-gateway/request-hyperunit-withdrawal-queue!
                    (fn [deps opts]
                      (swap! calls conj [:queue deps opts])
                      (js/Promise.resolve {:kind :queue}))
                    funding-hyperunit-gateway/request-hyperunit-generate-address!
                    (fn [deps opts]
                      (swap! calls conj [:generate deps opts])
                      (js/Promise.resolve {:kind :generate}))]
        (let [promise (js/Promise.all
                       #js [(hyperunit-client/request-hyperunit-operations!
                             {:base-url "https://ops"
                              :base-urls ["https://backup"]
                              :address "0xabc"})
                            (hyperunit-client/request-hyperunit-estimate-fees!
                             {:base-url "https://fees"})
                            (hyperunit-client/request-hyperunit-withdrawal-queue!
                             {:base-url "https://queue"})
                            (hyperunit-client/request-hyperunit-generate-address!
                             {:base-url "https://generate"
                              :source-chain "bitcoin"
                              :destination-chain "hyperliquid"
                              :asset "btc"
                              :destination-address "0xabc"})])]
          (-> promise
              (.then (fn [results]
                       (is (= [{:kind :operations}
                               {:kind :fees}
                               {:kind :queue}
                               {:kind :generate}]
                              (js->clj results :keywordize-keys true)))
                       (is (= [[:operations
                                {:hyperunit-base-url "https://ops"
                                 :fetch-fn fake-fetch}
                                {:address "0xabc"}]
                               [:fees
                                {:hyperunit-base-url "https://fees"
                                 :fetch-fn fake-fetch}
                                {}]
                               [:queue
                                {:hyperunit-base-url "https://queue"
                                 :fetch-fn fake-fetch}
                                {}]
                               [:generate
                                {:hyperunit-base-url "https://generate"
                                 :fetch-fn fake-fetch}
                                {:source-chain "bitcoin"
                                 :destination-chain "hyperliquid"
                                 :asset "btc"
                                 :destination-address "0xabc"}]]
                              @calls))))
              (.catch (async-support/unexpected-error done))
              (.finally (fn []
                          (set! (.-fetch js/globalThis) original-fetch)
                          (done)))))))))
