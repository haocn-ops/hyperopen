(ns hyperopen.referrals.effects-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.referrals.effects :as effects]
            [hyperopen.test-support.async :as async-support]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(deftest api-fetch-referral-loads-owner-referral-state-test
  (async done
    (let [store (atom {})
          calls (atom [])
          payload {:referrerState {:stage "ready"}}]
      (-> (effects/api-fetch-referral!
           {:store store
            :address owner-address
            :request-referral! (fn [address opts]
                                 (swap! calls conj [address opts])
                                 (js/Promise.resolve payload))
            :begin-referrals-load (fn [state address]
                                    (assoc-in state [:referrals :loading-for-address] address))
            :apply-referrals-success (fn [state address response]
                                       (assoc-in state [:referrals :success] [address response]))
            :apply-referrals-error (fn [state address err]
                                     (assoc-in state [:referrals :error] [address err]))})
          (.then (fn [response]
                   (is (= payload response))
                   (is (= [[owner-address {:priority :high}]]
                          @calls))
                   (is (= owner-address
                          (get-in @store [:referrals :loading-for-address])))
                   (is (= [owner-address payload]
                          (get-in @store [:referrals :success])))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest api-fetch-referral-skips-blank-address-test
  (async done
    (let [calls (atom [])
          store (atom {})]
      (-> (effects/api-fetch-referral!
           {:store store
            :address nil
            :request-referral! (fn [& _]
                                 (swap! calls inc)
                                 (js/Promise.resolve {:unexpected true}))
            :begin-referrals-load (fn [state _address] state)
            :apply-referrals-success (fn [state _address _response] state)
            :apply-referrals-error (fn [state _address _err] state)})
          (.then (fn [response]
                   (is (nil? response))
                   (is (= [] @calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest referral-submit-success-resets-submitting-and-refreshes-test
  (async done
    (let [store (atom {:wallet {:address owner-address}
                       :referrals-ui {:submitting? :set-referrer
                                      :active-modal :enter-code
                                      :pending-code "ABC123"
                                      :last-error "old"}})
          dispatch-calls (atom [])
          refresh-calls (atom [])
          toast-calls (atom [])]
      (-> (effects/api-submit-referral-mutation!
           {:store store
            :request {:owner owner-address
                      :code "ABC123"}
            :submit-kind :set-referrer
            :submit! (fn [runtime-store owner code]
                       (is (identical? store runtime-store))
                       (is (= owner-address owner))
                       (is (= "ABC123" code))
                       (js/Promise.resolve {:status "ok"}))
            :dispatch! (fn [runtime-store _ctx effects]
                         (swap! dispatch-calls conj [runtime-store effects]))
            :refresh-user-fees! (fn [runtime-store owner]
                                  (swap! refresh-calls conj [:user-fees runtime-store owner])
                                  (js/Promise.resolve :user-fees))
            :show-toast! (fn [runtime-store kind message]
                           (swap! toast-calls conj [runtime-store kind message]))})
          (.then (fn [response]
                   (is (= {:status "ok"} response))
                   (is (nil? (get-in @store [:referrals-ui :submitting?])))
                   (is (nil? (get-in @store [:referrals-ui :active-modal])))
                   (is (nil? (get-in @store [:referrals-ui :pending-code])))
                   (is (nil? (get-in @store [:referrals-ui :last-error])))
                   (is (= [[store [[:effects/api-fetch-referral owner-address]]]]
                          @dispatch-calls))
                   (is (= [[:user-fees store owner-address]]
                          @refresh-calls))
                   (is (= [[store :success "Referral updated."]]
                          @toast-calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest claim-rewards-success-refreshes-user-fees-and-spot-balances-test
  (async done
    (let [store (atom {:wallet {:address owner-address}
                       :referrals-ui {:submitting? :claim-rewards}})
          refresh-calls (atom [])]
      (-> (effects/api-submit-referral-mutation!
           {:store store
            :request {:owner owner-address}
            :submit-kind :claim-rewards
            :submit! (fn [runtime-store owner]
                       (is (identical? store runtime-store))
                       (is (= owner-address owner))
                       (js/Promise.resolve {:status "ok"}))
            :refresh-user-fees! (fn [runtime-store owner]
                                  (swap! refresh-calls conj [:user-fees runtime-store owner])
                                  (js/Promise.resolve :user-fees))
            :refresh-spot-balances! (fn [runtime-store owner]
                                      (swap! refresh-calls conj [:spot runtime-store owner])
                                      (js/Promise.resolve :spot))})
          (.then (fn [response]
                   (is (= {:status "ok"} response))
                   (is (= [[:user-fees store owner-address]
                           [:spot store owner-address]]
                          @refresh-calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest referral-submit-error-clears-submitting-and-shows-error-test
  (async done
    (let [store (atom {:wallet {:address owner-address}
                       :referrals-ui {:submitting? :claim-rewards}})
          toast-calls (atom [])]
      (-> (effects/api-submit-referral-mutation!
           {:store store
            :request {:owner owner-address}
            :submit-kind :claim-rewards
            :submit! (fn [& _]
                       (js/Promise.resolve {:status "err"
                                            :response "not ready"}))
            :exchange-response-error (fn [resp]
                                       (:response resp))
            :show-toast! (fn [runtime-store kind message]
                           (swap! toast-calls conj [runtime-store kind message]))})
          (.then (fn [response]
                   (is (= {:status "err" :response "not ready"} response))
                   (is (nil? (get-in @store [:referrals-ui :submitting?])))
                   (is (= "Claim rewards failed: not ready"
                          (get-in @store [:referrals-ui :last-error])))
                   (is (= [[store :error "Claim rewards failed: not ready"]]
                          @toast-calls))
                   (done)))
          (.catch (async-support/unexpected-error done))))))
