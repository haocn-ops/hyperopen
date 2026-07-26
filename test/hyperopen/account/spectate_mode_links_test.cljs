(ns hyperopen.account.spectate-mode-links-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.spectate-mode-links :as spectate-mode-links]))

(def ^:private spectate-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(deftest spectate-address-from-search-normalizes-valid-addresses-and-ignores-invalid-input-test
  (is (= spectate-address
         (spectate-mode-links/spectate-address-from-search
          "?spectate=0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD")))
  (is (= spectate-address
         (spectate-mode-links/spectate-address-from-search
          "?foo=bar&spectate=0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD")))
  (is (nil? (spectate-mode-links/spectate-address-from-search "?spectate=not-an-address")))
  (is (nil? (spectate-mode-links/spectate-address-from-search nil))))

(deftest spectate-url-path-normalizes-paths-and-removes-query-when-address-missing-test
  (is (= "/trade?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
         (spectate-mode-links/spectate-url-path
          nil
          "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD")))
  (is (= "/portfolio?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
         (spectate-mode-links/spectate-url-path "/portfolio" spectate-address)))
  (is (= "/portfolio"
         (spectate-mode-links/spectate-url-path "/portfolio?ignored=true" nil))))

(deftest spectate-navigation-path-preserves-normal-routes-and-suppresses-trader-portfolio-route-test
  (is (= "/portfolio?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
         (spectate-mode-links/spectate-navigation-path
          "/portfolio"
          "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD")))
  (is (= "/trade/ETH?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
         (spectate-mode-links/spectate-navigation-path
          "/trade/ETH"
          spectate-address)))
  (is (= "/vaults/0x9999999999999999999999999999999999999999?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
         (spectate-mode-links/spectate-navigation-path
          "/vaults/0x9999999999999999999999999999999999999999"
          spectate-address)))
  (is (= "/portfolio/trader/0x3333333333333333333333333333333333333333"
         (spectate-mode-links/spectate-navigation-path
          "/portfolio/trader/0x3333333333333333333333333333333333333333"
          spectate-address))))

(deftest internal-route-href-uses-active-spectate-state-only-test
  (let [active-state {:account-context {:spectate-mode {:active? true
                                                        :address spectate-address}}}
        inactive-state {:account-context {:spectate-mode {:active? false
                                                          :address spectate-address}}}]
    (is (= "/portfolio?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
           (spectate-mode-links/internal-route-href active-state "/portfolio")))
    (is (= "/portfolio"
           (spectate-mode-links/internal-route-href inactive-state "/portfolio")))))

(deftest spectate-url-builds-absolute-url-when-origin-is-available-test
  (is (= "https://app.hyperopen.test/trade?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
         (spectate-mode-links/spectate-url
          {:origin "https://app.hyperopen.test"}
          "/trade"
          spectate-address)))
  (is (= "/trade?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
         (spectate-mode-links/spectate-url nil "/trade" spectate-address))))
