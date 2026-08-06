(ns hyperopen.runtime.registry-composition-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.app.effects :as app-effects]
            [hyperopen.runtime.collaborators.account-history :as account-history-collaborators]
            [hyperopen.runtime.registry-composition :as registry-composition]
            [hyperopen.schema.runtime-registration-catalog :as registration-catalog]))

(deftest runtime-registration-deps-builds-effect-and-action-handler-maps-test
  (let [effect-export (fn [& _] :effect-export)
        action-export (fn [& _] :action-export)
        deps (registry-composition/runtime-registration-deps
              {:register-effects! :register-effects
               :register-actions! :register-actions
               :register-system-state! :register-system
               :register-placeholders! :register-placeholders}
              {:effect-deps {:storage {:save (fn [& _] :save)}
                             :api {:export-funding-history-csv effect-export}}
               :action-deps {:core {:navigate (fn [& _] :navigate)}
                             :account-history {:export-funding-history-csv action-export}}})]
    (is (= :register-effects (:register-effects! deps)))
    (is (= :register-actions (:register-actions! deps)))
    (is (= :register-system (:register-system-state! deps)))
    (is (= :register-placeholders (:register-placeholders! deps)))
    (is (= effect-export
           (get-in deps [:effect-handlers :export-funding-history-csv])))
    (is (= action-export
           (get-in deps [:action-handlers :export-funding-history-csv])))))

(deftest runtime-registration-deps-preserves-core-handler-entries-test
  (let [save-fn (fn [& _] :save)
        navigate-fn (fn [& _] :navigate)
        deps (registry-composition/runtime-registration-deps
              {:register-effects! identity
               :register-actions! identity
               :register-system-state! identity
               :register-placeholders! identity}
              {:effect-deps {:storage {:save save-fn}}
               :action-deps {:core {:navigate navigate-fn}}})]
    (is (= save-fn (get-in deps [:effect-handlers :save])))
    (is (= navigate-fn (get-in deps [:action-handlers :navigate])))))

(deftest runtime-action-handlers-rejects-duplicate-leaf-keys-across-domains-test
  (is (thrown-with-msg?
       js/Error
       #"Duplicate runtime handler key"
       (registry-composition/runtime-action-handlers
         {:first {:duplicate-handler (fn [& _] :first)}
         :second {:duplicate-handler (fn [& _] :second)}}))))

(deftest close-all-positions-has-complete-runtime-registration-for-its-actions-and-single-effect-test
  (let [submit-close-all (fn [& _] :submit-close-all)
        trigger (fn [& _] :trigger)
        dismiss (fn [& _] :dismiss)
        keydown (fn [& _] :keydown)
        confirm (fn [& _] :confirm)
        deps (registry-composition/runtime-registration-deps
              {:register-effects! identity
               :register-actions! identity
               :register-system-state! identity
               :register-placeholders! identity}
              {:effect-deps {:orders {:api-submit-close-all-positions submit-close-all}}
               :action-deps {:account-history {:trigger-close-all-positions trigger
                                                :dismiss-close-all-positions-confirmation dismiss
                                                :handle-close-all-positions-confirmation-keydown keydown
                                                :submit-close-all-positions-confirmation confirm}}})]
    (is (contains? (registration-catalog/effect-ids) :effects/api-submit-close-all-positions))
    (is (= submit-close-all (get-in deps [:effect-handlers :api-submit-close-all-positions])))
    (doseq [[action-id handler] [[:actions/trigger-close-all-positions trigger]
                                 [:actions/dismiss-close-all-positions-confirmation dismiss]
                                 [:actions/handle-close-all-positions-confirmation-keydown keydown]
                                 [:actions/submit-close-all-positions-confirmation confirm]]]
      (is (contains? (registration-catalog/action-ids) action-id))
      (is (= handler (get-in deps [:action-handlers (keyword (name action-id))]))))))

(deftest close-all-positions-is-wired-through-account-history-and-the-orders-effect-group-test
  (let [action-deps (account-history-collaborators/action-deps)
        effect-deps (app-effects/runtime-effect-deps (atom {:timeouts {:order-toast {}}}))]
    (doseq [handler-key [:trigger-close-all-positions
                         :dismiss-close-all-positions-confirmation
                         :handle-close-all-positions-confirmation-keydown
                         :submit-close-all-positions-confirmation]]
      (is (fn? (get action-deps handler-key))))
    (is (fn? (get-in effect-deps [:orders :api-submit-close-all-positions])))))
