(ns hyperopen.views.ui.toggle-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.ui.toggle :as toggle]))

(deftest toggle-renders-shared-switch-contract-test
  (let [view (toggle/toggle {:on? true
                             :aria-label "Show freshness labels"
                             :data-role "surface-freshness-toggle"
                             :on-change [[:actions/toggle-show-surface-freshness-cues]]})
        attrs (second view)]
    (is (toggle/props? {:on? true
                        :aria-label "Show freshness labels"
                        :data-role "surface-freshness-toggle"
                        :on-change [[:actions/toggle-show-surface-freshness-cues]]}))
    (is (= :button (first view)))
    (is (= "switch" (:role attrs)))
    (is (= "true" (:aria-checked attrs)))
    (is (= [[:actions/toggle-show-surface-freshness-cues]]
           (get-in attrs [:on :click])))
    (is (contains? (set (:class attrs)) "hx-toggle"))
    (is (contains? (set (:class attrs)) "on"))))
