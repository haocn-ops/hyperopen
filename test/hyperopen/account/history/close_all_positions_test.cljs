(ns hyperopen.account.history.close-all-positions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.history.close-all-positions :as close-all]
            [hyperopen.account.history.position-reduce :as position-reduce]))

(defn- two-position-state
  []
  {:account-info {:positions {:direction-filter :long
                              :coin-search "BTC"}}
   :webdata2 {:clearinghouseState
              {:assetPositions [{:position {:coin "BTC"
                                            :szi "1.25"
                                            :markPx "100"}}
                                {:position {:coin "ZERO"
                                            :szi "0"
                                            :markPx "1"}}
                                {:position {:coin "INVALID"
                                            :szi "not-a-number"
                                            :markPx "1"}}]}}
   :perp-dex-clearinghouse
   {"xyz" {:assetPositions [{:position {:coin "xyz:NVDA"
                                         :szi "-2.5"
                                         :markPx "10"}}
                             {:position {:coin "xyz:FLAT"
                                         :szi 0
                                         :markPx "1"}}]}}
   :asset-selector {:market-by-key
                    {"perp:BTC" {:coin "BTC"
                                   :dex nil
                                   :market-type :perp
                                   :asset-id 1
                                   :mark 100
                                   :szDecimals 2}
                     "perp:xyz:NVDA" {:coin "xyz:NVDA"
                                       :dex "xyz"
                                       :market-type :perp
                                       :asset-id 110001
                                       :mark 10
                                       :szDecimals 2}}}})

(deftest close-all-derives-the-full-unfiltered-snapshot-and-one-reduce-only-ioc-batch-test
  (let [state (two-position-state)
        snapshot (close-all/current-position-snapshot state)
        result (close-all/prepare-submit state snapshot)
        orders-by-asset (into {} (map (juxt :a identity))
                              (get-in result [:request :action :orders]))]
    (is (= [{:position-key "BTC|default"
             :coin "BTC"
             :dex nil
             :szi "1.25"}
            {:position-key "xyz:NVDA|xyz"
             :coin "xyz:NVDA"
             :dex "xyz"
             :szi "-2.5"}]
           snapshot)
        "The confirmation snapshot must ignore table filter/search state and retain default plus named-perp DEX exposure.")
    (is (true? (:ok? result)))
    (is (= snapshot (:snapshot result)))
    (is (= "order" (get-in result [:request :action :type])))
    (is (= 2 (count orders-by-asset)))
    (is (= {:a 1 :b false :r true :s "1.25"}
           (select-keys (get orders-by-asset 1) [:a :b :r :s])))
    (is (= {:a 110001 :b true :r true :s "2.5"}
           (select-keys (get orders-by-asset 110001) [:a :b :r :s])))
    (is (= "Ioc" (get-in orders-by-asset [1 :t :limit :tif])))
    (is (= "Ioc" (get-in orders-by-asset [110001 :t :limit :tif])))))

(deftest close-all-rejects-an-invalid-leg-without-returning-a-subset-request-test
  (let [state (two-position-state)
        snapshot (subvec (close-all/current-position-snapshot state) 0 1)
        valid-order {:a 1 :b false :r true :s "1.25" :t {:limit {:tif "Ioc"}}}
        cases [{:label "malformed candidate"
                :candidate {:ok? false :display-message "Market data is unavailable."}}
               {:label "wrong action type"
                :candidate {:ok? true
                            :request {:action {:type "cancel" :orders [valid-order]}}}}
               {:label "missing asset"
                :candidate {:ok? true
                            :request {:action {:type "order"
                                               :orders [(dissoc valid-order :a)]}}}}
               {:label "same-side leg"
                :candidate {:ok? true
                            :request {:action {:type "order"
                                               :orders [(assoc valid-order :b true)]}}}}
               {:label "wrong size"
                :candidate {:ok? true
                            :request {:action {:type "order"
                                               :orders [(assoc valid-order :s "1.24")]}}}}
               {:label "not reduce-only"
                :candidate {:ok? true
                            :request {:action {:type "order"
                                               :orders [(assoc valid-order :r false)]}}}}
               {:label "missing reduce-only wire flag"
                :candidate {:ok? true
                            :request {:action {:type "order"
                                               :orders [(dissoc valid-order :r)]}}}}
               {:label "not IOC"
                :candidate {:ok? true
                            :request {:action {:type "order"
                                               :orders [(assoc-in valid-order [:t :limit :tif] "Gtc")]}}}}]]
    (doseq [{:keys [label candidate]} cases]
      (with-redefs [position-reduce/prepare-submit (fn [_state _popover] candidate)]
        (let [result (close-all/prepare-submit state snapshot)]
          (is (false? (:ok? result)) label)
          (is (nil? (:request result)) label)
          (is (string? (:display-message result)) label))))
    (let [result (close-all/prepare-submit
                  (assoc-in state [:asset-selector :market-by-key] {})
                  snapshot)]
      (is (false? (:ok? result)) "missing market")
      (is (nil? (:request result)) "missing market"))))

(deftest close-all-rejects-builder-mismatch-for-the-entire-batch-test
  (let [state (two-position-state)
        snapshot (close-all/current-position-snapshot state)
        calls (atom 0)
        valid-order {:a 1 :b false :r true :s "1.25" :t {:limit {:tif "Ioc"}}}]
    (with-redefs [position-reduce/prepare-submit
                  (fn [_state _popover]
                    (let [idx (swap! calls inc)]
                      {:ok? true
                       :request {:action {:type "order"
                                          :orders [(if (= idx 1)
                                                     valid-order
                                                     (assoc valid-order :a 110001 :b true :s "2.5"))]}
                                 :builder {:b "0xbuilder" :f idx}}}))]
      (let [result (close-all/prepare-submit state snapshot)]
        (is (= 2 @calls))
        (is (false? (:ok? result)))
        (is (nil? (:request result)))
        (is (string? (:display-message result)))))))
