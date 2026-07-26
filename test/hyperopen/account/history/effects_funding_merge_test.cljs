(ns hyperopen.account.history.effects-funding-merge-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [async deftest is]]
            [hyperopen.account.history.effects :as history-effects]
            [hyperopen.account.history.test-support.fixtures :as fixtures]
            [hyperopen.api.default :as api]
            [hyperopen.platform :as platform]))

(deftest fetch-and-merge-funding-history-no-address-is-noop-test
  (let [store (atom (fixtures/base-history-state nil))
        calls (atom 0)]
    (with-redefs [api/request-user-funding-history! (fn
                                                      ([_address]
                                                       (swap! calls inc)
                                                       (js/Promise.resolve []))
                                                      ([_address _opts]
                                                       (swap! calls inc)
                                                       (js/Promise.resolve [])))]
      (is (nil? (history-effects/fetch-and-merge-funding-history! store nil nil)))
      (is (= 0 @calls)))))

(deftest fetch-and-merge-funding-history-prefers-hydrated-stream-test
  (let [address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        row (fixtures/info-funding-row 1700003600000 "BTC" "-0.1250" "-10" "-0.0003")
        calls (atom 0)
        store (atom (-> (fixtures/base-history-state address)
                        (assoc-in [:orders :fundings-raw] [row])
                        (assoc-in [:websocket :health]
                                  {:transport {:state :connected
                                               :freshness :live}
                                   :streams {["userFundings" nil address nil nil]
                                             {:topic "userFundings"
                                              :status :n-a
                                              :subscribed? true
                                              :message-count 1
                                              :descriptor {:type "userFundings"
                                                           :user address}}}})))]
    (with-redefs [api/request-user-funding-history! (fn
                                                      ([_address]
                                                       (swap! calls inc)
                                                       (js/Promise.resolve [row]))
                                                      ([_address _opts]
                                                       (swap! calls inc)
                                                       (js/Promise.resolve [row])))]
      (history-effects/fetch-and-merge-funding-history! store address {:priority :high})
      (is (= 0 @calls))
      (is (nil? (get-in @store [:account-info :funding-history :error])))
      (is (= ["BTC"] (mapv :coin (get-in @store [:orders :fundings])))))))

(deftest fetch-and-merge-funding-history-falls-back-to-rest-when-stream-not-hydrated-test
  (async done
    (let [address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          row (fixtures/info-funding-row 1700003600000 "BTC" "-0.1250" "-10" "-0.0003")
          calls (atom 0)
          store (atom (-> (fixtures/base-history-state address)
                          (assoc-in [:websocket :health]
                                    {:transport {:state :connected
                                                 :freshness :live}
                                     :streams {["userFundings" nil address nil nil]
                                               {:topic "userFundings"
                                                :status :n-a
                                                :subscribed? true
                                                :message-count 0
                                                :descriptor {:type "userFundings"
                                                             :user address}}}})))]
      (with-redefs [platform/set-timeout! (fn [callback _ms]
                                            (callback)
                                            1234)
                    api/request-user-funding-history! (fn
                                                        ([_address]
                                                         (swap! calls inc)
                                                         (js/Promise.resolve [row]))
                                                        ([_address _opts]
                                                         (swap! calls inc)
                                                         (js/Promise.resolve [row])))]
        (-> (history-effects/fetch-and-merge-funding-history! store address {:priority :high})
            (.then (fn [_]
                     (is (= 1 @calls))
                     (is (= ["BTC"] (mapv :coin (get-in @store [:orders :fundings]))))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (done))))))))

(deftest fetch-and-merge-funding-history-skips-rest-when-stream-hydrates-during-grace-window-test
  (async done
    (let [address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          row (fixtures/info-funding-row 1700003600000 "BTC" "-0.1250" "-10" "-0.0003")
          calls (atom 0)
          store (atom (-> (fixtures/base-history-state address)
                          (assoc-in [:orders :fundings-raw] [row])
                          (assoc-in [:websocket :health]
                                    {:transport {:state :connected
                                                 :freshness :live}
                                     :streams {["userFundings" nil address nil nil]
                                               {:topic "userFundings"
                                                :status :n-a
                                                :subscribed? true
                                                :message-count 0
                                                :descriptor {:type "userFundings"
                                                             :user address}}}})))]
      (with-redefs [platform/set-timeout! (fn [callback _ms]
                                            (swap! store assoc-in
                                                   [:websocket :health :streams
                                                    ["userFundings" nil address nil nil]
                                                    :message-count]
                                                   1)
                                            (callback)
                                            1234)
                    api/request-user-funding-history! (fn
                                                        ([_address]
                                                         (swap! calls inc)
                                                         (js/Promise.resolve [row]))
                                                        ([_address _opts]
                                                         (swap! calls inc)
                                                         (js/Promise.resolve [row])))]
        (-> (history-effects/fetch-and-merge-funding-history! store address {:priority :high})
            (.then (fn [_]
                     (is (= 0 @calls))
                     (is (= ["BTC"] (mapv :coin (get-in @store [:orders :fundings]))))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (done))))))))

(deftest fetch-and-merge-funding-history-success-merges-and-projects-current-address-only-test
  (async done
    (let [existing-row (fixtures/info-funding-row 1700000000000 "ETH" "0.0500" "3" "0.0001")
          incoming-row (fixtures/info-funding-row 1700003600000 "BTC" "-0.1250" "-10" "-0.0003")
          filters {:coin-set #{"BTC"}
                   :start-time-ms 0
                   :end-time-ms 2000000000000}
          calls (atom [])
          current-store (atom (-> (fixtures/base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                                  (assoc-in [:account-info :funding-history :filters] filters)
                                  (assoc-in [:orders :fundings-raw] [existing-row])))
          stale-store (atom (-> (fixtures/base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                                (assoc-in [:account-context :spectate-mode]
                                          {:active? true
                                           :address "0xdddddddddddddddddddddddddddddddddddddddd"})
                                (assoc-in [:account-info :funding-history :filters] filters)
                                (assoc-in [:orders :fundings-raw] [existing-row])))
          stale-before @stale-store]
      (with-redefs [api/request-user-funding-history! (fn
                                                        ([_address]
                                                         (js/Promise.resolve [incoming-row]))
                                                        ([_address opts]
                                                         (swap! calls conj [_address opts])
                                                         (js/Promise.resolve [incoming-row])))]
        (-> (js/Promise.all
             #js [(history-effects/fetch-and-merge-funding-history! current-store "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" {:priority :low
                                                                                            :tag :current})
                  (history-effects/fetch-and-merge-funding-history! stale-store "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" {:tag :stale})])
            (.then (fn [_]
                     (is (= 2 (count @calls)))
                     (is (= {:priority :low
                             :coin-set #{"BTC"}
                             :start-time-ms 0
                             :end-time-ms 2000000000000
                             :tag :current}
                            (second (first @calls))))
                     (is (nil? (get-in @current-store [:account-info :funding-history :error])))
                     (is (= ["BTC"]
                            (mapv :coin (get-in @current-store [:orders :fundings]))))
                     (is (= ["BTC" "ETH"]
                            (mapv :coin (get-in @current-store [:orders :fundings-raw]))))
                     (is (= stale-before @stale-store))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (done))))))))

(deftest fetch-and-merge-funding-history-error-sets-current-address-error-only-test
  (async done
    (let [current-store (atom (fixtures/base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
          stale-store (atom (-> (fixtures/base-history-state "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                                (assoc-in [:account-context :spectate-mode]
                                          {:active? true
                                           :address "0xdddddddddddddddddddddddddddddddddddddddd"})))
          stale-before @stale-store]
      (with-redefs [api/request-user-funding-history! (fn
                                                        ([_address]
                                                         (js/Promise.reject (js/Error. "merge-failure")))
                                                        ([_address _opts]
                                                         (js/Promise.reject (js/Error. "merge-failure"))))]
        (-> (js/Promise.all
             #js [(history-effects/fetch-and-merge-funding-history! current-store "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" nil)
                  (history-effects/fetch-and-merge-funding-history! stale-store "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" nil)])
            (.then (fn [_]
                     (is (str/includes?
                          (get-in @current-store [:account-info :funding-history :error])
                          "merge-failure"))
                     (is (= stale-before @stale-store))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (done))))))))
