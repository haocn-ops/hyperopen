(ns hyperopen.api.endpoints.account.referrals
  (:require [clojure.string :as str]
            [hyperopen.api.request-policy :as request-policy]))

(defn request-referral!
  [post-info! address opts]
  (if-not address
    (js/Promise.resolve nil)
    (let [requested-address (some-> address str str/lower-case)
          opts* (request-policy/apply-info-request-policy
                 :referral
                 (merge {:priority :high
                         :dedupe-key [:referral requested-address]}
                        opts))]
      (post-info! {"type" "referral"
                   "user" address}
                  opts*))))
