(ns hyperopen.funding.application.modal-commands-submit-guard-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.application.modal-commands :as modal-commands]))

(deftest submit-funding-deposit-ignores-a-repeat-while-submitting-test
  (let [preview-calls (atom 0)
        state {:funding-ui {:modal {:open? true
                                    :mode :deposit
                                    :submitting? true}}}
        effects (modal-commands/submit-funding-deposit
                 {:modal-state #(get-in % [:funding-ui :modal])
                  :normalize-mode identity
                  :deposit-preview (fn [& _]
                                     (swap! preview-calls inc)
                                     {:ok? true
                                      :request {:action {:type "bridge2Deposit"}}})
                  :funding-modal-path [:funding-ui :modal]}
                 state)]
    (is (= [] effects))
    (is (= 0 @preview-calls))))
