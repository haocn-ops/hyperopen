(ns hyperopen.schema.runtime-registration.referrals)

(def effect-binding-rows
  [[:effects/api-fetch-referral :api-fetch-referral]
   [:effects/api-set-referrer :api-set-referrer]
   [:effects/api-register-referrer :api-register-referrer]
   [:effects/api-claim-referral-rewards :api-claim-referral-rewards]])

(def effect-order-policy-required-action-ids
  #{:actions/load-referrals-route
    :actions/submit-set-referrer
    :actions/submit-register-referrer
    :actions/submit-claim-referral-rewards})

(def action-binding-rows
  [[:actions/load-referrals-route :load-referrals-route]
   [:actions/set-referrals-active-tab :set-referrals-active-tab]
   [:actions/set-referrals-form-field :set-referrals-form-field]
   [:actions/set-referrals-sort :set-referrals-sort]
   [:actions/open-referrals-modal :open-referrals-modal]
   [:actions/close-referrals-modal :close-referrals-modal]
   [:actions/submit-set-referrer :submit-set-referrer]
   [:actions/submit-register-referrer :submit-register-referrer]
   [:actions/submit-claim-referral-rewards :submit-claim-referral-rewards]])
