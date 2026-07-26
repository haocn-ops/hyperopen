(ns hyperopen.views.referrals-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [goog.object :as gobj]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.referrals-view :as referrals-view]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def referred-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def spectate-address
  "0x7777777777777777777777777777777777777777")

(defn- connected-state
  [overrides]
  (merge {:wallet {:address owner-address}
          :referrals-ui {:active-tab :referrals
                         :sort {:column :total-volume
                                :direction :desc}
                         :form {:code ""
                                :new-code ""}}}
         overrides))

(defn- referral-row-texts
  [view]
  (->> (hiccup/find-all-nodes view #(= "referrals-row" (get-in % [1 :data-role])))
       (mapv (fn [row]
               (mapv hiccup/node-text (hiccup/node-children row))))))

(defn- resolve-global-path
  [path]
  (reduce (fn [acc segment]
            (when acc
              (gobj/get acc segment)))
          (or (some-> js/goog .-global)
              js/globalThis)
          path))

(deftest referrals-route-view-is-exported-for-lazy-route-loader-test
  (is (fn? (resolve-global-path ["hyperopen" "views" "referrals_view" "route_view"]))))

(deftest referrals-view-renders-ready-share-state-and-kpis-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :referrals {:raw {:cumVlm "12000"
                                  :tokenToState {:USDC {:unclaimedRewards "3.5"
                                                        :claimedRewards "1.5"}}
                                  :referrerState {:stage "ready"
                                                  :data {:code "MYCODE"
                                                         :nReferrals 1
                                                         :referralStates [{:user referred-address
                                                                           :cumVlm "1000"
                                                                           :cumReward "5"}]}}}}
               :referrals-ui {:active-tab :referrals
                              :form {:code ""
                                     :new-code ""}}})
        root (hiccup/find-by-parity-id view "referrals-root")
        code-node (hiccup/find-by-data-role view "referrals-own-code")
        join-link (hiccup/find-by-data-role view "referrals-join-link")
        row (hiccup/find-by-data-role view "referrals-row")
        rewards-stat (hiccup/find-by-data-role view "referrals-stat-rewards")
        claimable-stat (hiccup/find-by-data-role view "referrals-stat-claimable")]
    (is (some? root))
    (is (= "MYCODE" (hiccup/node-text code-node)))
    (is (= "/join/MYCODE" (hiccup/node-text join-link)))
    (is (some? row))
    (is (re-find #"\$5" (hiccup/node-text rewards-stat)))
    (is (re-find #"\$3.50" (hiccup/node-text claimable-stat)))))

(deftest referrals-view-renders-hyperliquid-parity-reward-summary-and-actions-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :account-context {:spectate-mode {:active? true
                                                 :address spectate-address}}
               :referrals {:raw {:tokenToState [[0 {:cumVlm "5034741.3799999999"
                                                    :unclaimedRewards "0.08258155"
                                                    :claimedRewards "208.64995482"
                                                    :builderRewards "0.0"}]
                                                [235 {:cumVlm "0.0"
                                                      :unclaimedRewards "0.00081898"
                                                      :claimedRewards "0.0"
                                                      :builderRewards "0.0"}]
                                                [360 {:cumVlm "680.37"
                                                      :unclaimedRewards "0.82911511"
                                                      :claimedRewards "0.0"
                                                      :builderRewards "0.0"}]]
                                  :referrerState {:stage "ready"
                                                  :data {:code "MYCODE"
                                                         :nReferrals 6
                                                         :referralStates [{:cumVlm "6226785.1799999997"
                                                                           :cumRewardedFeesSinceReferred "4428.91413651"
                                                                           :cumFeesRewardedToReferrer "187.35791924"
                                                                           :timeJoined 1761012412763
                                                                           :user referred-address}]}}}}
               :referrals-ui {:active-tab :referrals
                              :form {:code ""
                                     :new-code ""}}})
        hero (hiccup/find-by-data-role view "referrals-hero")
        rewards-stat (hiccup/find-by-data-role view "referrals-stat-rewards")
        claimable-stat (hiccup/find-by-data-role view "referrals-stat-claimable")
        rewards-panel (hiccup/find-by-data-role view "referrals-rewards-panel")
        claim-button (hiccup/find-by-data-role view "referrals-open-claim-rewards")]
    (is (re-find #"\$209.56" (hiccup/node-text rewards-stat)))
    (is (re-find #"\$0.91" (hiccup/node-text claimable-stat)))
    (is (nil? rewards-panel))
    (is (some? claim-button))
    (is (re-find #"Claim Rewards" (hiccup/node-text hero)))))

(defn- expected-local-referral-date
  "Mirrors the private referrals-view/format-referral-date, which renders the
   join time in the JS runtime's local timezone. Reconstructing the expected
   string from the same epoch keeps the assertion timezone-agnostic (the old
   hard-coded EDT literal failed under UTC on CI)."
  [time-ms]
  (let [date (js/Date. time-ms)
        pad2 (fn [n] (let [s (str n)] (if (= 1 (count s)) (str "0" s) s)))]
    (str (inc (.getMonth date)) "/" (.getDate date) "/" (.getFullYear date)
         " - " (pad2 (.getHours date)) ":" (pad2 (.getMinutes date)) ":"
         (pad2 (.getSeconds date)))))

(deftest referrals-view-renders-api-shaped-referral-row-fields-test
  (let [time-joined 1761012412763
        view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :referrals {:raw {:referrerState {:stage "ready"
                                                  :data {:code "MYCODE"
                                                         :nReferrals 1
                                                         :referralStates [{:cumVlm "6226785.1799999997"
                                                                           :cumRewardedFeesSinceReferred "4428.91413651"
                                                                           :cumFeesRewardedToReferrer "187.35791924"
                                                                           :timeJoined time-joined
                                                                           :user referred-address}]}}}}
               :referrals-ui {:active-tab :referrals
                              :form {:code ""
                                     :new-code ""}}})
        row (hiccup/find-by-data-role view "referrals-row")
        cells (vec (hiccup/node-children row))]
    (is (= "0xabcd…abcd" (hiccup/node-text (nth cells 0))))
    (is (= (expected-local-referral-date time-joined) (hiccup/node-text (nth cells 1))))
    (is (= "$6,226,785.18" (hiccup/node-text (nth cells 2))))
    (is (= "$4,428.91" (hiccup/node-text (nth cells 3))))
    (is (= "$187.36" (hiccup/node-text (nth cells 4))))))

(deftest referrals-view-renders-sortable-referral-headers-and-sorted-rows-test
  (let [state {:wallet {:address owner-address}
               :referrals {:raw {:referrerState {:stage "ready"
                                                  :data {:code "MYCODE"
                                                         :nReferrals 3
                                                         :referralStates [{:cumVlm "100"
                                                                           :cumRewardedFeesSinceReferred "5"
                                                                           :cumFeesRewardedToReferrer "1"
                                                                           :timeJoined 1761012412763
                                                                           :user "0x1111111111111111111111111111111111111111"}
                                                                          {:cumVlm "300"
                                                                           :cumRewardedFeesSinceReferred "9"
                                                                           :cumFeesRewardedToReferrer "3"
                                                                           :timeJoined 1764625665863
                                                                           :user "0x3333333333333333333333333333333333333333"}
                                                                          {:cumVlm "200"
                                                                           :cumRewardedFeesSinceReferred "7"
                                                                           :cumFeesRewardedToReferrer "2"
                                                                           :timeJoined 1760709224945
                                                                           :user "0x2222222222222222222222222222222222222222"}]}}}}
               :referrals-ui {:active-tab :referrals
                              :sort {:column :date-joined
                                     :direction :asc}
                              :form {:code ""
                                     :new-code ""}}}
        view (referrals-view/referrals-view state)
        header (hiccup/find-by-data-role view "referrals-table-header")
        header-class-set (hiccup/node-class-set header)
        date-header (hiccup/find-by-data-role view "referrals-sort-date-joined")
        rewards-header (hiccup/find-by-data-role view "referrals-sort-your-rewards")
        row-texts (referral-row-texts view)]
    (is (contains? header-class-set "grid-cols-[minmax(160px,1.25fr)_minmax(190px,0.9fr)_minmax(140px,0.75fr)_minmax(120px,0.7fr)_minmax(130px,0.75fr)]"))
    (is (= "ascending" (get-in date-header [1 :aria-sort])))
    (is (= [[:actions/set-referrals-sort :date-joined]]
           (get-in date-header [1 :on :click])))
    (is (= [[:actions/set-referrals-sort :your-rewards]]
           (get-in rewards-header [1 :on :click])))
    (is (= ["0x2222…2222" "0x1111…1111" "0x3333…3333"]
           (mapv first row-texts)))))

(deftest referrals-view-renders-empty-and-join-code-prefill-state-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :referrals {:raw {:referrerState {:stage "needToCreateCode"
                                                  :data {}}}}
               :referrals-ui {:pending-code "ABC123"
                              :active-modal :enter-code
                              :active-tab :referrals
                              :form {:code "ABC123"
                                     :new-code ""}}})
        code-input (hiccup/find-by-data-role view "referrals-modal-code-input")
        empty-node (hiccup/find-by-data-role view "referrals-empty")
        create-node (hiccup/find-by-data-role view "referrals-open-create-code")]
    (is (= "ABC123" (get-in code-input [1 :value])))
    (is (= "No referrals yet" (hiccup/node-text empty-node)))
    (is (some? create-node))))

(deftest referrals-view-labels-spectated-referral-state-and-blocks-mutations-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :account-context {:spectate-mode {:active? true
                                                 :address spectate-address}}
               :referrals {:raw {:referrerState {:stage "ready"
                                                  :data {:code "WATCHED"
                                                         :nReferrals 2}}}}
               :referrals-ui {:active-tab :referrals
                              :form {:code ""
                                     :new-code ""}}})
        owner-node (hiccup/find-by-data-role view "referrals-owner")
        read-only-node (hiccup/find-by-data-role view "referrals-read-only")
        share-button (hiccup/find-by-data-role view "referrals-open-share-code")]
    (is (nil? owner-node))
    (is (re-find #"Spectate Mode is read-only" (hiccup/node-text read-only-node)))
    (is (re-find #"0x7777…7777" (hiccup/node-text read-only-node)))
    (is (some? (get-in share-button [1 :disabled])))))

(deftest referrals-view-renders-legacy-tab-empty-state-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :referrals {:raw {:referrerState {:stage "ready"
                                                  :data {:code "MYCODE"}}}}
               :referrals-ui {:active-tab :legacy-reward-history
                              :form {:code ""
                                     :new-code ""}}})
        empty-node (hiccup/find-by-data-role view "referrals-legacy-empty")]
    (is (= "No rewards earned" (hiccup/node-text empty-node)))))

(deftest referrals-view-renders-legacy-reward-row-fields-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :referrals {:raw {:referrerState {:stage "ready"
                                                  :data {:code "MYCODE"}}
                                  :rewardHistory [{:time 1761012412763
                                                   :userVlm "1200"
                                                   :referralVlm "3400"
                                                   :totalRewards "12.5"}]}}
               :referrals-ui {:active-tab :legacy-reward-history
                              :form {:code ""
                                     :new-code ""}}})
        row (hiccup/find-by-data-role view "referrals-legacy-row")
        cells (vec (hiccup/node-children row))]
    (is (= "1761012412763" (hiccup/node-text (nth cells 0))))
    (is (= "1200" (hiccup/node-text (nth cells 1))))
    (is (= "3400" (hiccup/node-text (nth cells 2))))
    (is (= "12.5" (hiccup/node-text (nth cells 3))))))

(deftest referrals-view-opens-actions-through-modal-buttons-test
  (let [view (referrals-view/referrals-view
              (connected-state
               {:referrals {:raw {:referrerState {:stage "needToCreateCode"
                                                  :data {}}}}}))
        enter-button (hiccup/find-by-data-role view "referrals-open-enter-code")
        create-button (hiccup/find-by-data-role view "referrals-open-create-code")
        claim-button (hiccup/find-by-data-role view "referrals-open-claim-rewards")]
    (is (= [[:actions/open-referrals-modal :enter-code]]
           (get-in enter-button [1 :on :click])))
    (is (= [[:actions/open-referrals-modal :create-code]]
           (get-in create-button [1 :on :click])))
    (is (= [[:actions/open-referrals-modal :claim-rewards]]
           (get-in claim-button [1 :on :click])))))

(deftest referrals-view-renders-join-confirmation-modal-test
  (let [view (referrals-view/referrals-view
              (connected-state
               {:referrals {:raw {:referrerState {:stage "needToCreateCode"
                                                  :data {}}}}
                :referrals-ui {:pending-code "ABC123"
                               :active-modal :enter-code
                               :active-tab :referrals
                               :form {:code "ABC123"
                                      :new-code ""}}}))
        modal (hiccup/find-by-data-role view "referrals-modal")
        title (hiccup/find-by-data-role view "referrals-modal-title")
        code-node (hiccup/find-by-data-role view "referrals-modal-normalized-code")
        input (hiccup/find-by-data-role view "referrals-modal-code-input")
        submit (hiccup/find-by-data-role view "referrals-modal-submit")]
    (is (= "Confirm Referral Code" (hiccup/node-text title)))
    (is (re-find #"ABC123" (hiccup/node-text modal)))
    (is (= "ABC123" (hiccup/node-text code-node)))
    (is (= "ABC123" (get-in input [1 :value])))
    (is (= [[:actions/submit-set-referrer]]
           (get-in submit [1 :on :click])))))

(deftest referrals-view-renders-create-share-and-claim-modals-test
  (let [create-view (referrals-view/referrals-view
                     (connected-state
                      {:referrals {:raw {:referrerState {:stage "needToCreateCode"
                                                         :data {}}}}
                       :referrals-ui {:active-modal :create-code
                                      :active-tab :referrals
                                      :form {:code ""
                                             :new-code "MYCODE"}}}))
        create-title (hiccup/find-by-data-role create-view "referrals-modal-title")
        create-input (hiccup/find-by-data-role create-view "referrals-modal-new-code-input")
        share-view (referrals-view/referrals-view
                    (connected-state
                     {:referrals {:raw {:referrerState {:stage "ready"
                                                        :data {:code "MYCODE"}}}}
                      :referrals-ui {:active-modal :share-code
                                     :active-tab :referrals
                                     :form {:code ""
                                            :new-code ""}}}))
        share-link (hiccup/find-by-data-role share-view "referrals-modal-join-link")
        claim-view (referrals-view/referrals-view
                    (connected-state
                     {:referrals {:raw {:tokenToState {:USDC {:unclaimedRewards "3.5"
                                                              :claimedRewards "1.5"}}
                                        :referrerState {:stage "ready"
                                                        :data {:code "MYCODE"}}}}
                      :referrals-ui {:active-modal :claim-rewards
                                     :active-tab :referrals
                                     :form {:code ""
                                            :new-code ""}}}))
        claim-title (hiccup/find-by-data-role claim-view "referrals-modal-title")
        claim-total (hiccup/find-by-data-role claim-view "referrals-modal-claim-total")]
    (is (= "Create Referral Code" (hiccup/node-text create-title)))
    (is (= "MYCODE" (get-in create-input [1 :value])))
    (is (= "/join/MYCODE" (hiccup/node-text share-link)))
    (is (= "Claim Rewards" (hiccup/node-text claim-title)))
    (is (re-find #"\$3.50" (hiccup/node-text claim-total)))))
