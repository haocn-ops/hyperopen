(ns hyperopen.ui.voice-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.ui.voice :as voice]
            [hyperopen.views.account-info.tab-registry :as tab-registry]
            [hyperopen.views.header.nav :as nav]))

(deftest active-voice-follows-theme-test
  (is (= :default (voice/active-voice {})))
  (is (= :default (voice/active-voice {:ui {:theme "dark"}})))
  (is (= :default (voice/active-voice {:ui {:theme "institutional"}})))
  (is (= :default (voice/active-voice {:ui {:theme "not-a-theme"}})))
  (is (= :degen (voice/active-voice {:ui {:theme "hyperdegen"}}))))

(deftest label-resolves-per-voice-test
  (is (= "Trade" (voice/label {} :nav/trade)))
  (is (= "Trade" (voice/label {:ui {:theme "institutional"}} :nav/trade)))
  (is (= "Trade (Gamble)" (voice/label {:ui {:theme "hyperdegen"}} :nav/trade)))
  (is (= "Buy / Moon 🚀" (voice/label {:ui {:theme "hyperdegen"}} :order-form/buy)))
  (is (= "Order Book" (voice/label {} :orderbook/book)))
  (is (= "Order Book (looks important)"
         (voice/label {:ui {:theme "hyperdegen"}} :orderbook/book)))
  (is (= "Trades (other people's mistakes)"
         (voice/label {:ui {:theme "hyperdegen"}} :orderbook/trades)))
  (is (= "Order Book" (voice/label {} :mobile-surface/orderbook)))
  (is (= "Book (lol)"
         (voice/label {:ui {:theme "hyperdegen"}} :mobile-surface/orderbook)))
  (is (= "Chart (hopium)"
         (voice/label {:ui {:theme "hyperdegen"}} :mobile-surface/chart)))
  (is (= "Trades (pain)"
         (voice/label {:ui {:theme "hyperdegen"}} :mobile-surface/trades))))

(deftest degen-predicate-test
  (is (false? (voice/degen? {})))
  (is (false? (voice/degen? {:ui {:theme "institutional"}})))
  (is (true? (voice/degen? {:ui {:theme "hyperdegen"}}))))

(deftest label-for-falls-back-and-rejects-unknown-test
  (is (= "Trade" (voice/label-for :no-such-voice :nav/trade)))
  (is (nil? (voice/label-for :degen :nav/no-such-item)))
  (is (nil? (voice/label {} :no-such/key))))

(deftest catalog-hygiene-test
  (doseq [[label-key entry] voice/labels]
    (is (keyword? label-key))
    (is (string? (:default entry))
        (str label-key " must have a :default string"))
    (doseq [[voice copy] entry]
      (is (keyword? voice))
      (is (and (string? copy) (not (str/blank? copy)))
          (str label-key " " voice " must be a non-blank string")))))

(deftest nav-default-copy-stays-in-sync-test
  (let [items (mapcat #(nav/items-for-placement "/x" %)
                      [:desktop :mobile-primary :mobile-secondary :more])]
    (is (seq items))
    (doseq [{:keys [id label]} items]
      (is (= label (voice/label-for :default (keyword "nav" (name id))))
          (str "nav item " id " must be cataloged with its canonical label")))))

(deftest account-tab-default-copy-stays-in-sync-test
  (is (seq tab-registry/tab-labels))
  (doseq [[tab label] tab-registry/tab-labels]
    (is (= label (voice/label-for :default (keyword "account-tabs" (name tab))))
        (str "tab " tab " must be cataloged with its canonical label"))))

(deftest account-tab-overrides-test
  (is (nil? (voice/account-tab-overrides {})))
  (is (nil? (voice/account-tab-overrides {:ui {:theme "dark"}})))
  (let [overrides (voice/account-tab-overrides {:ui {:theme "hyperdegen"}})]
    (is (= (set (keys tab-registry/tab-labels))
           (set (keys overrides))))
    (is (= "What's Left" (:balances overrides)))
    (is (= "Hope & Dreams" (:positions overrides)))
    (is (= "Bragging Rights" (:trade-history overrides)))))
