(ns hyperopen.account.history.position-modal-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.history.actions :as history-actions]
            [hyperopen.account.history.position-margin :as position-margin]
            [hyperopen.account.history.position-reduce :as position-reduce]
            [hyperopen.account.history.position-tpsl :as position-tpsl]
            [hyperopen.views.account-info.test-support.fixtures :as fixtures]))

(deftest position-tpsl-modal-actions-open-update-close-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        open-effects (history-actions/open-position-tpsl-modal {} row)
        opened-modal (get-in (first open-effects) [1 0 1])
        reset-reduce-popover (get-in (first open-effects) [1 1 1])
        reset-margin-modal (get-in (first open-effects) [1 2 1])
        updated-effects (history-actions/set-position-tpsl-modal-field
                         {:positions-ui {:tpsl-modal opened-modal}}
                         [:tp-price]
                         "20.25")
        closed-effects (history-actions/close-position-tpsl-modal {})]
    (is (= :effects/save-many
           (ffirst open-effects)))
    (is (true? (:open? opened-modal)))
    (is (= "xyz:NVDA" (:coin opened-modal)))
    (is (= (position-reduce/default-popover-state) reset-reduce-popover))
    (is (= (position-margin/default-modal-state) reset-margin-modal))
    (is (= "20.25"
           (get-in (nth (first updated-effects) 2) [:tp-price])))
    (is (= [[:effects/save [:positions-ui :tpsl-modal]
             (position-tpsl/default-modal-state)]]
           closed-effects))
    (is (= [[:effects/save [:positions-ui :tpsl-modal]
             (position-tpsl/default-modal-state)]]
           (history-actions/handle-position-tpsl-modal-keydown {} "Escape")))
    (is (= []
           (history-actions/handle-position-tpsl-modal-keydown {} "Enter")))))

(deftest position-modal-open-actions-propagate-ui-locale-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        state {:ui {:locale "fr-FR"}}
        tpsl-effects (history-actions/open-position-tpsl-modal state row)
        margin-effects (history-actions/open-position-margin-modal state row)
        reduce-effects (history-actions/open-position-reduce-popover state row)
        tpsl-modal (get-in (first tpsl-effects) [1 0 1])
        margin-modal (get-in (first margin-effects) [1 0 1])
        reduce-popover (get-in (first reduce-effects) [1 0 1])]
    (is (= "fr-FR" (:locale tpsl-modal)))
    (is (= "fr-FR" (:locale margin-modal)))
    (is (= "fr-FR" (:locale reduce-popover)))))

(deftest position-overlay-open-actions-normalize-js-anchor-objects-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        js-anchor #js {:left 390
                       :right 414
                       :top 884
                       :bottom 908
                       :width 24
                       :height 24
                       "viewport-width" 430
                       "viewport-height" 932}
        tpsl-effects (history-actions/open-position-tpsl-modal {} row js-anchor)
        margin-effects (history-actions/open-position-margin-modal {} row js-anchor)
        reduce-effects (history-actions/open-position-reduce-popover {} row js-anchor)
        tpsl-anchor (get-in (first tpsl-effects) [1 0 1 :anchor])
        margin-anchor (get-in (first margin-effects) [1 0 1 :anchor])
        reduce-anchor (get-in (first reduce-effects) [1 0 1 :anchor])]
    (is (= {:left 390
            :right 414
            :top 884
            :bottom 908
            :width 24
            :height 24
            :viewport-width 430
            :viewport-height 932}
           tpsl-anchor))
    (is (= tpsl-anchor margin-anchor))
    (is (= tpsl-anchor reduce-anchor))))

(deftest position-reduce-popover-actions-open-update-close-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        open-effects (history-actions/open-position-reduce-popover {} row)
        opened-popover (get-in (first open-effects) [1 0 1])
        reset-tpsl-modal (get-in (first open-effects) [1 1 1])
        reset-margin-modal (get-in (first open-effects) [1 2 1])
        updated-effects (history-actions/set-position-reduce-popover-field
                         {:positions-ui {:reduce-popover opened-popover}}
                         [:size-percent-input]
                         "75")
        preset-effects (history-actions/set-position-reduce-size-percent
                        {:positions-ui {:reduce-popover opened-popover}}
                        25)
        mid-effects (history-actions/set-position-reduce-limit-price-to-mid
                     {:positions-ui {:reduce-popover (assoc opened-popover :limit-price "")}})
        closed-effects (history-actions/close-position-reduce-popover {})
        submit-effects (history-actions/submit-position-reduce-close {})]
    (is (= [:effects/save-many [:effects/fetch-asset-selector-markets]]
           [(ffirst open-effects) (second open-effects)]))
    (is (true? (:open? opened-popover)))
    (is (= "xyz:NVDA" (:coin opened-popover)))
    (is (= "10" (:mid-price opened-popover)))
    (is (= (position-tpsl/default-modal-state) reset-tpsl-modal))
    (is (= (position-margin/default-modal-state) reset-margin-modal))
    (is (= "75"
           (get-in (nth (first updated-effects) 2) [:size-percent-input])))
    (is (= "25"
           (get-in (nth (first preset-effects) 2) [:size-percent-input])))
    (is (= (:mid-price opened-popover)
           (get-in (nth (first mid-effects) 2) [:limit-price])))
    (is (= [[:effects/save [:positions-ui :reduce-popover]
             (position-reduce/default-popover-state)]]
           closed-effects))
    (is (= [[:effects/save [:positions-ui :reduce-popover]
             (position-reduce/default-popover-state)]]
           (history-actions/handle-position-reduce-popover-keydown {} "Escape")))
    (is (= []
           (history-actions/handle-position-reduce-popover-keydown {} "Enter")))
    (is (= [[:effects/save
             [:positions-ui :reduce-popover]
             (assoc (position-reduce/default-popover-state)
                    :error "Place Order")]]
           submit-effects))
    ))

(deftest close-all-positions-trigger-opens-a-confirmation-with-a-current-snapshot-test
  (let [state {:webdata2 {:clearinghouseState
                          {:assetPositions [{:position {:coin "BTC"
                                                        :szi "1.25"}}]}}
               :perp-dex-clearinghouse {"xyz" {:assetPositions [{:position {:coin "xyz:NVDA"
                                                                             :szi "-2.5"}}]}}}
        bounds {:left 120 :right 196 :top 32 :bottom 56
                :width 76 :height 24 :viewport-width 1440 :viewport-height 900}
        effects (history-actions/trigger-close-all-positions state bounds)
        confirmation (nth (first effects) 2)]
    (is (= :effects/save (ffirst effects)))
    (is (= [:positions-ui :close-all-confirmation]
           (second (first effects))))
    (is (true? (:open? confirmation)))
    (is (= :confirming (:lifecycle confirmation)))
    (is (= bounds (:trigger-bounds confirmation)))
    (is (= [{:position-key "BTC|default" :coin "BTC" :dex nil :szi "1.25"}
            {:position-key "xyz:NVDA|xyz" :coin "xyz:NVDA" :dex "xyz" :szi "-2.5"}]
           (:snapshot confirmation)))
    (is (= 1 (count effects))
        "Opening confirmation is local only and must never submit an order.")))

(deftest close-all-positions-confirmation-dismisses-on-cancel-or-escape-test
  (let [confirmation {:open? true
                      :lifecycle :confirming
                      :snapshot [{:position-key "BTC|default" :coin "BTC" :dex nil :szi "1"}]
                      :trigger-bounds {:left 12 :right 36 :top 8 :bottom 32
                                       :width 24 :height 24 :viewport-width 1440 :viewport-height 900}
                      :error nil
                      :accepted-count 0
                      :rejected-count 0}
        state {:positions-ui {:close-all-confirmation confirmation}}
        dismiss-effects (history-actions/dismiss-close-all-positions-confirmation state)
        escape-effects (history-actions/handle-close-all-positions-confirmation-keydown state "Escape")]
    (doseq [effects [dismiss-effects escape-effects]]
      (let [next-confirmation (nth (first effects) 2)]
        (is (= :effects/save (ffirst effects)))
        (is (= [:positions-ui :close-all-confirmation]
               (second (first effects))))
        (is (false? (:open? next-confirmation)))
        (is (nil? (:snapshot next-confirmation)))
        (is (= 1 (count effects)))))
    (is (= []
           (history-actions/handle-close-all-positions-confirmation-keydown state "Enter")))))

(deftest close-all-positions-submission-rejects-stale-read-only-spectate-empty-and-duplicate-states-test
  (let [snapshot [{:position-key "BTC|default" :coin "BTC" :dex nil :szi "1"}]
        confirming {:open? true :lifecycle :confirming :snapshot snapshot
                    :trigger-bounds nil :error nil :accepted-count 0 :rejected-count 0}
        current-state {:webdata2 {:clearinghouseState {:assetPositions [{:position {:coin "BTC" :szi "2"}}]}}
                       :positions-ui {:close-all-confirmation confirming}}
        stale-effects (history-actions/submit-close-all-positions-confirmation current-state)
        stale-confirmation (nth (first stale-effects) 2)
        submitting-state (assoc-in current-state [:positions-ui :close-all-confirmation :lifecycle] :submitting)
        read-only-state (assoc current-state :account-context {:spectate-mode {:active? true
                                                                                :address "0x1234567890abcdef1234567890abcdef12345678"}})
        empty-state {:positions-ui {:close-all-confirmation confirming}
                     :webdata2 {:clearinghouseState {:assetPositions [{:position {:coin "BTC" :szi "0"}}]}}}]
    (is (= :effects/save (ffirst stale-effects)))
    (is (= :error (:lifecycle stale-confirmation)))
    (is (= "Positions changed. Review current positions before closing."
           (:error stale-confirmation)))
    (is (not-any? #(= :effects/api-submit-close-all-positions (first %)) stale-effects))
    (is (= []
           (history-actions/submit-close-all-positions-confirmation submitting-state)))
    (is (= []
           (history-actions/dismiss-close-all-positions-confirmation submitting-state)))
    (is (= []
           (history-actions/handle-close-all-positions-confirmation-keydown submitting-state "Escape")))
    (doseq [state [read-only-state empty-state]]
      (let [effects (history-actions/submit-close-all-positions-confirmation state)]
        (is (not-any? #(= :effects/api-submit-close-all-positions (first %)) effects))))))

(deftest close-all-positions-trigger-is-unavailable-for-read-only-spectate-and-empty-accounts-test
  (let [position-state {:webdata2 {:clearinghouseState {:assetPositions [{:position {:coin "BTC" :szi "1"}}]}}}
        spectate-state (assoc position-state :account-context {:spectate-mode {:active? true
                                                                                 :address "0x1234567890abcdef1234567890abcdef12345678"}})
        read-only-state (assoc position-state :account-info {:positions {:read-only? true}})
        empty-state {:webdata2 {:clearinghouseState {:assetPositions [{:position {:coin "BTC" :szi "0"}}]}}}]
    (doseq [state [spectate-state read-only-state empty-state]]
      (is (= [] (history-actions/trigger-close-all-positions state))))))

(deftest position-reduce-popover-parses-localized-input-values-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        state {:ui {:locale "fr-FR"}}
        open-effects (history-actions/open-position-reduce-popover state row)
        opened-popover (get-in (first open-effects) [1 0 1])
        percent-effects (history-actions/set-position-reduce-popover-field
                         {:ui {:locale "fr-FR"}
                          :positions-ui {:reduce-popover opened-popover}}
                         [:size-percent-input]
                         "25,5")
        percent-popover (get-in (first percent-effects) [2])
        market-state {:asset-selector {:market-by-key {"perp:xyz:NVDA"
                                                       {:coin "xyz:NVDA"
                                                        :market-type :perp
                                                        :asset-id 123
                                                        :mark 10}}}}
        submit-effects (history-actions/submit-position-reduce-close
                        (assoc market-state
                               :trading-settings {:confirm-close-position? false}
                               :ui {:locale "fr-FR"}
                               :positions-ui {:reduce-popover (assoc percent-popover
                                                                     :close-type :limit
                                                                     :limit-price "11,5")}))
        submitted-order (get-in submit-effects [1 1 :action :orders 0])]
    (is (= "fr-FR" (:locale opened-popover)))
    (is (= "25.5" (:size-percent-input percent-popover)))
    (is (= :effects/api-submit-order
           (first (second submit-effects))))
    (is (= "11.5" (:p submitted-order)))
    (is (= "0.1275" (:s submitted-order)))))

(deftest submit-position-tpsl-validates-and-emits-submit-effect-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        modal (-> (position-tpsl/from-position-row row)
                  (assoc :tp-price "11.0"))
        state {:positions-ui {:tpsl-modal modal}
               :asset-selector {:market-by-key {"perp:xyz:NVDA"
                                                {:coin "xyz:NVDA"
                                                 :market-type :perp
                                                 :asset-id 123}}}}
        valid-effects (history-actions/submit-position-tpsl state)
        invalid-effects (history-actions/submit-position-tpsl
                         {:positions-ui {:tpsl-modal (position-tpsl/from-position-row row)}
                          :asset-selector {:market-by-key {"perp:xyz:NVDA"
                                                           {:coin "xyz:NVDA"
                                                            :market-type :perp
                                                            :asset-id 123}}}})]
    (is (= :effects/save-many
           (ffirst valid-effects)))
    (is (= [[:positions-ui :tpsl-modal :submitting?] true]
           (first (second (first valid-effects)))))
    (is (= :effects/api-submit-position-tpsl
           (first (second valid-effects))))
    (is (= "order"
           (get-in (second (second valid-effects)) [:action :type])))
    (is (= "tp"
           (get-in (second (second valid-effects))
                   [:action :orders 0 :t :trigger :tpsl])))
    (is (= [[:effects/save-many [[[:positions-ui :tpsl-modal :submitting?] false]
                                 [[:positions-ui :tpsl-modal :error] "Place Order"]]]]
           invalid-effects))))

(deftest submit-position-reduce-close-validates-and-emits-submit-effect-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        popover (-> (position-reduce/from-position-row row)
                    (assoc :close-type :limit
                           :limit-price "11"))
        market-state {:asset-selector {:market-by-key {"perp:xyz:NVDA"
                                                       {:coin "xyz:NVDA"
                                                        :market-type :perp
                                                        :asset-id 123
                                                        :mark 10}}}}
        valid-effects (history-actions/submit-position-reduce-close
                       (assoc market-state
                              :trading-settings {:confirm-close-position? false}
                              :positions-ui {:reduce-popover popover}))
        invalid-effects (history-actions/submit-position-reduce-close
                         (assoc market-state
                                :positions-ui {:reduce-popover (assoc popover :limit-price "")}))]
    (is (= :effects/save
           (ffirst valid-effects)))
    (is (nil? (get-in (first valid-effects) [2 :error])))
    (is (= :effects/api-submit-order
           (first (second valid-effects))))
    (is (= "order"
           (get-in (second (second valid-effects)) [:action :type])))
    (is (= true
           (get-in (second (second valid-effects)) [:action :orders 0 :r])))
    (is (= false
           (get-in (second (second valid-effects)) [:action :orders 0 :b])))
    (is (= [[:effects/save
             [:positions-ui :reduce-popover]
            (assoc popover
                    :limit-price ""
                    :error "Price is required for limit orders.")]]
           invalid-effects))))

(deftest submit-position-reduce-close-emits-confirm-effect-when-enabled-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        popover (-> (position-reduce/from-position-row row)
                    (assoc :close-type :limit
                           :limit-price "11"))
        state {:trading-settings {:confirm-close-position? true}
               :positions-ui {:reduce-popover popover}
               :asset-selector {:market-by-key {"perp:xyz:NVDA"
                                                {:coin "xyz:NVDA"
                                                 :market-type :perp
                                                 :asset-id 123
                                                 :mark 10}}}}
        effects (history-actions/submit-position-reduce-close state)
        confirm-effect (first effects)
        payload (second confirm-effect)]
    (is (= 1 (count effects)))
    (is (= :effects/confirm-api-submit-order (first confirm-effect)))
    (is (= :close-position
           (:variant payload)))
    (is (= "Submit this close order?\n\nDisable close-position confirmation in Trading settings if you prefer one-click closes."
           (:message payload)))
    (is (= [[:positions-ui :reduce-popover] (assoc popover :error nil)]
           (first (:path-values payload))))
    (is (= "order"
           (get-in payload [:request :action :type])))))

(deftest position-margin-modal-actions-open-update-close-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        open-effects (history-actions/open-position-margin-modal {} row)
        opened-modal (get-in (first open-effects) [1 0 1])
        reset-tpsl-modal (get-in (first open-effects) [1 1 1])
        reset-reduce-popover (get-in (first open-effects) [1 2 1])
        updated-effects (history-actions/set-position-margin-modal-field
                         {:positions-ui {:margin-modal opened-modal}}
                         [:amount-input]
                         "1.5")
        percent-effects (history-actions/set-position-margin-amount-percent
                         {:positions-ui {:margin-modal opened-modal}}
                         25)
        max-effects (history-actions/set-position-margin-amount-to-max
                     {:positions-ui {:margin-modal (assoc opened-modal :available-to-add 5)}})
        closed-effects (history-actions/close-position-margin-modal {})]
    (is (= :effects/save-many
           (ffirst open-effects)))
    (is (true? (:open? opened-modal)))
    (is (= "xyz:NVDA" (:coin opened-modal)))
    (is (= (position-tpsl/default-modal-state) reset-tpsl-modal))
    (is (= (position-reduce/default-popover-state) reset-reduce-popover))
    (is (= "1.5"
           (get-in (nth (first updated-effects) 2) [:amount-input])))
    (is (= "25"
           (get-in (nth (first percent-effects) 2) [:amount-percent-input])))
    (is (= "100"
           (get-in (nth (first max-effects) 2) [:amount-percent-input])))
    (is (= [[:effects/save [:positions-ui :margin-modal]
             (position-margin/default-modal-state)]]
           closed-effects))
    (is (= [[:effects/save [:positions-ui :margin-modal]
             (position-margin/default-modal-state)]]
           (history-actions/handle-position-margin-modal-keydown {} "Escape")))
    (is (= []
           (history-actions/handle-position-margin-modal-keydown {} "Enter")))))

(deftest submit-position-margin-update-validates-and-emits-submit-effect-test
  (let [row (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
        modal (-> (position-margin/from-position-row {} row)
                  (assoc :available-to-add 10
                         :amount-input "1.25"))
        state {:positions-ui {:margin-modal modal}
               :asset-selector {:market-by-key {"perp:xyz:NVDA"
                                                {:coin "xyz:NVDA"
                                                 :market-type :perp
                                                 :asset-id 123}}}}
        valid-effects (history-actions/submit-position-margin-update state)
        invalid-effects (history-actions/submit-position-margin-update
                         {:positions-ui {:margin-modal (assoc (position-margin/from-position-row {} row)
                                                              :available-to-add 5)}
                          :asset-selector {:market-by-key {"perp:xyz:NVDA"
                                                           {:coin "xyz:NVDA"
                                                            :market-type :perp
                                                            :asset-id 123}}}})]
    (is (= :effects/save-many
           (ffirst valid-effects)))
    (is (= [[:positions-ui :margin-modal :submitting?] true]
           (first (second (first valid-effects)))))
    (is (= :effects/api-submit-position-margin
           (first (second valid-effects))))
    (is (= "updateIsolatedMargin"
           (get-in (second (second valid-effects)) [:action :type])))
    (is (= 123
           (get-in (second (second valid-effects)) [:action :asset])))
    (is (= 1250000
           (get-in (second (second valid-effects)) [:action :ntli])))
    (is (= true
           (get-in (second (second valid-effects)) [:action :isBuy])))
    (is (= [[:effects/save-many [[[:positions-ui :margin-modal :submitting?] false]
                                 [[:positions-ui :margin-modal :error] "Select an amount"]]]]
           invalid-effects))))

(deftest open-position-margin-modal-preserves-chart-drag-prefill-fields-test
  (let [row (assoc (fixtures/sample-position-row "xyz:NVDA" 10 "0.500")
                   :prefill-source :chart-liquidation-drag
                   :prefill-margin-mode :add
                   :prefill-margin-amount 1.75
                   :prefill-liquidation-current-price 4.2
                   :prefill-liquidation-target-price 2.1)
        state {:webdata2 {:clearinghouseState {:marginSummary {:accountValue "10"
                                                                :totalMarginUsed "1"}}}}
        effects (history-actions/open-position-margin-modal state row)
        modal (get-in (first effects) [1 0 1])]
    (is (= :chart-liquidation-drag (:prefill-source modal)))
    (is (= :add (:mode modal)))
    (is (= "1.75" (:amount-input modal)))
    (is (= 4.2 (:prefill-liquidation-current-price modal)))
    (is (= 2.1 (:prefill-liquidation-target-price modal)))))
