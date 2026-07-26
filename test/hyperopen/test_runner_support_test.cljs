(ns hyperopen.test-runner-support-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-runner-support :as support]))

(deftest exit-code-for-results-test
  (is (= 0
         (support/exit-code-for-results nil)))
  (is (= 0
         (support/exit-code-for-results {:fail 0
                                         :error 0})))
  (is (= 1
         (support/exit-code-for-results {:fail 1
                                         :error 0})))
  (is (= 1
         (support/exit-code-for-results {:fail 0
                                         :error 2}))))

(deftest completion-reporting-env-test
  (let [completion-handler (fn [results] results)
        env (support/completion-reporting-env {:summary-heading "=== Summary ==="
                                              :completion-handler completion-handler})]
    (is (= ::support/process-exit
           (:reporter env)))
    (is (= "=== Summary ==="
           (::support/summary-heading env)))
    (is (identical? completion-handler
                    (::support/completion-handler env)))))

(deftest process-exit-reporting-env-installs-exit-handler-test
  (let [env (support/process-exit-reporting-env "=== Summary ===")]
    (is (= ::support/process-exit
           (:reporter env)))
    (is (= "=== Summary ==="
           (::support/summary-heading env)))
    (is (fn? (::support/completion-handler env)))))
