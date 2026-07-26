(ns hyperopen.api.projections.funding-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api.projections.funding :as funding]))

(deftest funding-comparison-projections-track-loading-success-and-error-test
  (let [state {:funding-comparison-ui {:loading? false}
               :funding-comparison {:predicted-fundings []
                                    :error "stale"
                                    :error-category :unexpected
                                    :loaded-at-ms nil}}
        loading (funding/begin-funding-comparison-load state)
        success (funding/apply-funding-comparison-success loading [["BTC" []]])
        failed (funding/apply-funding-comparison-error loading (js/Error. "funding-fail"))]
    (is (= true (get-in loading [:funding-comparison-ui :loading?])))
    (is (nil? (get-in loading [:funding-comparison :error])))
    (is (= [["BTC" []]] (get-in success [:funding-comparison :predicted-fundings])))
    (is (= false (get-in success [:funding-comparison-ui :loading?])))
    (is (= nil (get-in success [:funding-comparison :error])))
    (is (= nil (get-in success [:funding-comparison :error-category])))
    (is (number? (get-in success [:funding-comparison :loaded-at-ms])))
    (is (= false (get-in failed [:funding-comparison-ui :loading?])))
    (is (= "Error: funding-fail" (get-in failed [:funding-comparison :error])))
    (is (= :unexpected (get-in failed [:funding-comparison :error-category])))))
