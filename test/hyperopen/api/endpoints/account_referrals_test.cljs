(ns hyperopen.api.endpoints.account-referrals-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.endpoints.account :as account]
            [hyperopen.test-support.api-stubs :as api-stubs]
            [hyperopen.test-support.async :as async-support]))

(deftest request-referral-builds-info-request-test
  (async done
    (let [calls (atom [])
          address "0xAbCDEF1234567890ABCDEF1234567890abcdef12"
          post-info! (api-stubs/post-info-stub
                      calls
                      {:referrerState {:stage "needToCreateCode"}})]
      (-> (account/request-referral! post-info! address {:priority :low})
          (.then
           (fn [payload]
             (is (= {:referrerState {:stage "needToCreateCode"}}
                    payload))
             (is (= [{"type" "referral"
                      "user" address}
                     {:priority :low
                      :dedupe-key [:referral "0xabcdef1234567890abcdef1234567890abcdef12"]}]
                    (first @calls)))
             (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest request-referral-skips-missing-address-test
  (async done
    (let [calls (atom [])
          post-info! (api-stubs/post-info-stub calls {:unexpected true})]
      (-> (account/request-referral! post-info! nil {})
          (.then
           (fn [payload]
             (is (nil? payload))
             (is (= [] @calls))
             (done)))
          (.catch (async-support/unexpected-error done))))))
