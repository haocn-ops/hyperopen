(ns hyperopen.runtime.registry-composition-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.registry-composition :as registry-composition]))

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
