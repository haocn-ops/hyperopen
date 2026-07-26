(ns hyperopen.account.spectate-mode-actions-search-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.spectate-mode-actions :as spectate-mode-actions]))

(def ^:private editing-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def ^:private different-address
  "0x1111111111111111111111111111111111111111")

(deftest set-spectate-mode-search-preserves-active-watchlist-edit-test
  (let [state {:account-context {:spectate-ui {:search ""
                                               :label "Assistance"
                                               :editing-watchlist-address editing-address
                                               :search-error "old error"}}}]
    (is (= [[:effects/save-many [[[:account-context :spectate-ui :search]
                                 "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD"]
                                 [[:account-context :spectate-ui :label]
                                  "Assistance"]
                                 [[:account-context :spectate-ui :editing-watchlist-address]
                                  editing-address]
                                 [[:account-context :spectate-ui :search-auto-prefilled?]
                                  false]
                                 [[:account-context :spectate-ui :search-error]
                                  nil]]]]
           (spectate-mode-actions/set-spectate-mode-search
            state
            "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD")))))

(deftest set-spectate-mode-search-clears-stale-watchlist-edit-test
  (let [state {:account-context {:spectate-ui {:search ""
                                               :label "Assistance"
                                               :editing-watchlist-address editing-address
                                               :search-error "old error"}}}]
    (is (= [[:effects/save-many [[[:account-context :spectate-ui :search]
                                  different-address]
                                 [[:account-context :spectate-ui :label]
                                  ""]
                                 [[:account-context :spectate-ui :editing-watchlist-address]
                                  nil]
                                 [[:account-context :spectate-ui :search-auto-prefilled?]
                                  false]
                                 [[:account-context :spectate-ui :search-error]
                                  nil]]]]
           (spectate-mode-actions/set-spectate-mode-search state different-address)))))
