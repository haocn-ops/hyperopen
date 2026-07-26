(ns hyperopen.api.runtime-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api.runtime :as api-runtime]))

(deftest runtime-state-encapsulates-cache-and-flight-tracking-test
  (let [client {:request-info! (fn [& _] nil)}
        runtime (api-runtime/make-runtime {:info-client client})
        promise-a (js/Promise.resolve :a)
        promise-b (js/Promise.resolve :b)]
    (is (identical? client (api-runtime/info-client runtime)))
    (is (nil? (api-runtime/public-webdata2-cache runtime)))
    (is (nil? (api-runtime/ensure-perp-dexs-flight runtime)))

    (api-runtime/set-public-webdata2-cache! runtime {:snapshot true})
    (is (= {:snapshot true} (api-runtime/public-webdata2-cache runtime)))

    (api-runtime/set-ensure-perp-dexs-flight! runtime promise-a)
    (is (identical? promise-a (api-runtime/ensure-perp-dexs-flight runtime)))
    (api-runtime/clear-ensure-perp-dexs-flight-if-tracked! runtime promise-b)
    (is (identical? promise-a (api-runtime/ensure-perp-dexs-flight runtime)))
    (api-runtime/clear-ensure-perp-dexs-flight-if-tracked! runtime promise-a)
    (is (nil? (api-runtime/ensure-perp-dexs-flight runtime)))

    (api-runtime/reset-runtime! runtime)
    (is (nil? (api-runtime/public-webdata2-cache runtime)))
    (is (nil? (api-runtime/ensure-perp-dexs-flight runtime)))))
