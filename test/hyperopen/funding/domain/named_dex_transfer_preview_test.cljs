(ns hyperopen.funding.domain.named-dex-transfer-preview-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.domain.availability :as availability]
            [hyperopen.funding.domain.policy :as policy]))

(defn- named-dex-state
  []
  {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
   :spot {:meta {:tokens [{:index 0
                           :name "USDC"
                           :tokenId "0x6d1e7cde53ba9467b783cb7c530ce054"}]}
          :clearinghouse-state {:balances [{:coin "USDC"
                                            :available "78.22"
                                            :total "78.22"
                                            :hold "0"}]}}
   :webdata2 {:clearinghouseState {:withdrawable "0.0"
                                   :marginSummary {:accountValue "0.0"
                                                   :totalMarginUsed "0.0"}}}
   :perp-dex-clearinghouse {"xyz" {:withdrawable "1923.97"
                                   :marginSummary {:accountValue "1923.97"
                                                   :totalMarginUsed "0.0"}}}})

(deftest transfer-max-amount-reads-named-dex-withdrawable-for-perps-to-spot-test
  (let [state (named-dex-state)]
    ;; Default perps -> spot still reads the (zero) default clearinghouse state.
    (is (= 0 (availability/transfer-max-amount state {:to-perp? false})))
    ;; Named DEX perps -> spot reads the xyz withdrawable, not the default state.
    (is (= 1923.97 (availability/transfer-max-amount state {:to-perp? false
                                                            :transfer-dex "xyz"})))
    ;; Spot -> perps reads spot USDC regardless of dex.
    (is (= 78.22 (availability/transfer-max-amount state {:to-perp? true
                                                          :transfer-dex "xyz"})))))

(deftest transfer-max-amount-pooled-named-dex-reads-default-perps-not-named-bucket-test
  ;; Pooled accounts submit a named-DEX perps -> spot from the default perps DEX, so the
  ;; max must read the default pooled balance, not the (here empty) named bucket.
  (let [state (-> (named-dex-state)
                  (assoc :account {:mode :classic :abstraction-raw "dexAbstraction"})
                  (assoc :perp-dex-clearinghouse {})
                  (assoc-in [:webdata2 :clearinghouseState]
                            {:withdrawable "500.0"
                             :marginSummary {:accountValue "500.0"
                                             :totalMarginUsed "0.0"}}))]
    (is (= 500.0 (availability/transfer-max-amount state {:to-perp? false
                                                          :transfer-dex "xyz"})))))

(deftest transfer-dex-name-normalizes-default-and-spot-aliases-test
  (is (nil? (availability/transfer-dex-name nil)))
  (is (nil? (availability/transfer-dex-name "")))
  (is (nil? (availability/transfer-dex-name "  ")))
  (is (nil? (availability/transfer-dex-name "spot")))
  (is (nil? (availability/transfer-dex-name "SPOT")))
  (is (= "xyz" (availability/transfer-dex-name "xyz")))
  (is (= "xyz" (availability/transfer-dex-name "  xyz "))))

(deftest transfer-preview-builds-named-dex-send-asset-for-perps-to-spot-test
  (is (= {:ok? true
          :request {:action {:type "sendAsset"
                             :destination "0x1234567890abcdef1234567890abcdef12345678"
                             :sourceDex "xyz"
                             :destinationDex "spot"
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "100"
                             :fromSubAccount ""}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "100"
                                   :to-perp? false
                                   :transfer-dex "xyz"}))))

(deftest transfer-preview-builds-named-dex-send-asset-for-spot-to-perps-test
  (is (= {:ok? true
          :request {:action {:type "sendAsset"
                             :destination "0x1234567890abcdef1234567890abcdef12345678"
                             :sourceDex "spot"
                             :destinationDex "xyz"
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "50"
                             :fromSubAccount ""}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "50"
                                   :to-perp? true
                                   :transfer-dex "xyz"}))))

(deftest transfer-preview-keeps-usd-class-transfer-for-default-perps-test
  (is (= {:ok? true
          :request {:action {:type "usdClassTransfer"
                             :amount "25"
                             :toPerp true}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "25"
                                   :to-perp? true})))
  (is (= {:ok? true
          :request {:action {:type "usdClassTransfer"
                             :amount "25"
                             :toPerp true}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "25"
                                   :to-perp? true
                                   :transfer-dex ""}))))

(deftest transfer-preview-requires-wallet-address-for-named-dex-send-asset-test
  (is (= {:ok? false
          :display-message "Connect your wallet before transferring funds."}
         (policy/transfer-preview (assoc (named-dex-state) :wallet {})
                                  {:amount-input "100"
                                   :to-perp? false
                                   :transfer-dex "xyz"}))))

(deftest pooled-perps-collateral-classifies-dex-abstraction-and-unified-test
  ;; Standard / manual accounts keep the siloed per-DEX `sendAsset` path.
  (is (false? (boolean (availability/pooled-perps-collateral? (named-dex-state)))))
  (is (false? (boolean (availability/pooled-perps-collateral?
                        (assoc (named-dex-state)
                               :account {:mode :classic :abstraction-raw "disabled"})))))
  (is (false? (boolean (availability/pooled-perps-collateral?
                        (assoc (named-dex-state)
                               :account {:mode :classic :abstraction-raw "default"})))))
  ;; Deprecated DEX Abstraction folds into :classic but pools USDC.
  (is (true? (boolean (availability/pooled-perps-collateral?
                       (assoc (named-dex-state)
                              :account {:mode :classic :abstraction-raw "dexAbstraction"})))))
  ;; Unified / portfolio-margin accounts are pooled via :mode.
  (is (true? (boolean (availability/pooled-perps-collateral?
                       (assoc (named-dex-state)
                              :account {:mode :unified :abstraction-raw "unifiedAccount"}))))))

(deftest transfer-preview-collapses-pooled-named-dex-to-default-perp-send-asset-test
  ;; Unified account: the exchange rejects a per-DEX `sendAsset`, so a named-DEX
  ;; perps -> spot move collapses to the default perps DEX ("") and stays a
  ;; `sendAsset` (sourceDex:""/destinationDex:"spot"). Matches the reporting
  ;; account's live 2026 ledger.
  ;; Pooled USDC lives in the default perps balance, so seed it nonzero: the pooled
  ;; perps -> spot max now reads the default bucket (matching the collapsed sourceDex "").
  (let [state (-> (named-dex-state)
                  (assoc :account {:mode :unified :abstraction-raw "unifiedAccount"})
                  (assoc-in [:webdata2 :clearinghouseState]
                            {:withdrawable "2000.0"
                             :marginSummary {:accountValue "2000.0"
                                             :totalMarginUsed "0.0"}}))]
    (is (= {:ok? true
            :request {:action {:type "sendAsset"
                               :destination "0x1234567890abcdef1234567890abcdef12345678"
                               :sourceDex ""
                               :destinationDex "spot"
                               :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                               :amount "100"
                               :fromSubAccount ""}}}
           (policy/transfer-preview state
                                    {:amount-input "100"
                                     :to-perp? false
                                     :transfer-dex "xyz"})))
    ;; Spot -> perps on the same pooled account: sourceDex:"spot"/destinationDex:"".
    (is (= {:ok? true
            :request {:action {:type "sendAsset"
                               :destination "0x1234567890abcdef1234567890abcdef12345678"
                               :sourceDex "spot"
                               :destinationDex ""
                               :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                               :amount "50"
                               :fromSubAccount ""}}}
           (policy/transfer-preview state
                                    {:amount-input "50"
                                     :to-perp? true
                                     :transfer-dex "xyz"})))))

(deftest transfer-preview-routes-pooled-default-modal-through-default-perp-send-asset-test
  ;; Even without a named dex selected, a pooled account's default perps<->spot
  ;; modal must use the default-perp `sendAsset` (not `usdClassTransfer`), since the
  ;; exchange has moved these accounts off the class-transfer path. (Spot -> perps
  ;; here, since the fixture's default perps withdrawable is 0.)
  (let [state (assoc (named-dex-state)
                     :account {:mode :classic :abstraction-raw "dexAbstraction"})]
    (is (= {:ok? true
            :request {:action {:type "sendAsset"
                               :destination "0x1234567890abcdef1234567890abcdef12345678"
                               :sourceDex "spot"
                               :destinationDex ""
                               :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                               :amount "25"
                               :fromSubAccount ""}}}
           (policy/transfer-preview state
                                    {:amount-input "25"
                                     :to-perp? true})))))

(deftest transfer-preview-pooled-dex-abstraction-empty-named-bucket-uses-default-perps-test
  ;; dexAbstraction folds to :classic, so named rows are NOT hidden by the unified-row
  ;; gating; `pooled-perps-collateral?` is still true. Its USDC pools in the default perps
  ;; balance, and the named `xyz` bucket can be empty/missing. A `Perps (xyz) -> Spot` must
  ;; build `sourceDex ""` AND validate against the default pooled balance, not the empty
  ;; named bucket (which would otherwise fail with "No perps balance available").
  (let [state (-> (named-dex-state)
                  (assoc :account {:mode :classic :abstraction-raw "dexAbstraction"})
                  (assoc :perp-dex-clearinghouse {})
                  (assoc-in [:webdata2 :clearinghouseState]
                            {:withdrawable "500.0"
                             :marginSummary {:accountValue "500.0"
                                             :totalMarginUsed "0.0"}}))]
    (is (= {:ok? true
            :request {:action {:type "sendAsset"
                               :destination "0x1234567890abcdef1234567890abcdef12345678"
                               :sourceDex ""
                               :destinationDex "spot"
                               :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                               :amount "100"
                               :fromSubAccount ""}}}
           (policy/transfer-preview state
                                    {:amount-input "100"
                                     :to-perp? false
                                     :transfer-dex "xyz"})))))

(def ^:private subaccount-address "0xbce774ef2382a4eb9376ea6f20408b318b10b63e")

(deftest transfer-preview-uses-selected-subaccount-source-for-named-dex-perps-to-spot-test
  ;; The visible xyz balance belongs to a selected classic subaccount under a unified
  ;; master. The sendAsset must source from that subaccount (fromSubAccount) and land
  ;; in its own spot. Mirrors the subaccount's live 2026-05-28 ledger (reverse direction).
  (is (= {:ok? true
          :request {:action {:type "sendAsset"
                             :destination subaccount-address
                             :sourceDex "xyz"
                             :destinationDex "spot"
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "100"
                             :fromSubAccount subaccount-address}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "100"
                                   :to-perp? false
                                   :transfer-dex "xyz"
                                   :transfer-destination-address subaccount-address
                                   :transfer-from-subaccount subaccount-address}))))

(deftest transfer-preview-uses-selected-subaccount-source-for-spot-to-named-dex-test
  (is (= {:ok? true
          :request {:action {:type "sendAsset"
                             :destination subaccount-address
                             :sourceDex "spot"
                             :destinationDex "xyz"
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "50"
                             :fromSubAccount subaccount-address}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "50"
                                   :to-perp? true
                                   :transfer-dex "xyz"
                                   :transfer-destination-address subaccount-address
                                   :transfer-from-subaccount subaccount-address}))))

(deftest transfer-preview-uses-subaccount-source-for-default-perp-not-usd-class-transfer-test
  ;; Regression: a selected classic subaccount's DEFAULT-perp USDC (no named dex) must
  ;; still source from the subaccount via sendAsset(fromSubAccount). usdClassTransfer has
  ;; no subaccount field and no posted vaultAddress, so it would move the signing owner's
  ;; USDC instead. Perps -> spot needs a nonzero default-perp withdrawable to pass the max
  ;; guard, so seed one on the (effective subaccount's) default clearinghouse.
  (let [state (assoc-in (named-dex-state) [:webdata2 :clearinghouseState]
                        {:withdrawable "500.0"
                         :marginSummary {:accountValue "500.0"
                                         :totalMarginUsed "0.0"}})]
    (is (= {:ok? true
            :request {:action {:type "sendAsset"
                               :destination subaccount-address
                               :sourceDex ""
                               :destinationDex "spot"
                               :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                               :amount "100"
                               :fromSubAccount subaccount-address}}}
           (policy/transfer-preview state
                                    {:amount-input "100"
                                     :to-perp? false
                                     :transfer-dex ""
                                     :transfer-destination-address subaccount-address
                                     :transfer-from-subaccount subaccount-address}))))
  ;; Spot -> default perp on the same subaccount also routes through sendAsset.
  (is (= {:ok? true
          :request {:action {:type "sendAsset"
                             :destination subaccount-address
                             :sourceDex "spot"
                             :destinationDex ""
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "50"
                             :fromSubAccount subaccount-address}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "50"
                                   :to-perp? true
                                   :transfer-dex ""
                                   :transfer-destination-address subaccount-address
                                   :transfer-from-subaccount subaccount-address}))))

(deftest transfer-preview-requires-wallet-address-for-pooled-default-perp-send-asset-test
  (is (= {:ok? false
          :display-message "Connect your wallet before transferring funds."}
         (policy/transfer-preview (-> (named-dex-state)
                                      (assoc :wallet {})
                                      (assoc :account {:mode :unified
                                                       :abstraction-raw "unifiedAccount"})
                                      (assoc-in [:webdata2 :clearinghouseState]
                                                {:withdrawable "2000.0"
                                                 :marginSummary {:accountValue "2000.0"
                                                                 :totalMarginUsed "0.0"}}))
                                  {:amount-input "100"
                                   :to-perp? false
                                   :transfer-dex "xyz"}))))
