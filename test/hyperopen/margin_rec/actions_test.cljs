(ns hyperopen.margin-rec.actions-test
  (:require [cljs.spec.alpha :as s]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.margin-rec.actions :as actions]
            [hyperopen.margin-rec.state :as state]
            [hyperopen.schema.contracts.common :as common]
            [hyperopen.trading-settings :as trading-settings]))

(defn- assert-save-effects-valid!
  "Every emitted :effects/save must satisfy the runtime arg contract —
  notably keyword-only path segments (coin strings belong in values). The
  runtime wrapper enforces this in dev; unit tests bypass it, so pin it here."
  [effects]
  (doseq [[effect-id & args] effects
          :when (= :effects/save effect-id)]
    (is (s/valid? ::common/save-args (vec args))
        (str "invalid save args: " (pr-str args)))))

(def now 1780000000000)

(def xyz-position
  {:coin "xyz:TSM"
   :szi "0.36"
   :entryPx "446.441"
   :positionValue "157.5"
   :liquidationPx "424.20"
   :marginUsed "12.42"
   :maxLeverage 10
   :leverage {:type "isolated" :value 10}})

(defn base-state
  []
  {:webdata2 {:clearinghouseState {:assetPositions []
                                   :withdrawable "800"}}
   :perp-dex-clearinghouse {"xyz" {:assetPositions [{:position xyz-position}]
                                   :withdrawable "500"}}
   :asset-selector {:market-by-key
                    {"perp:xyz:TSM" {:market-type :perp
                                     :coin "xyz:TSM"
                                     :dex "xyz"
                                     :asset-id 100001
                                     :fundingRate 0.0000125
                                     :maxLeverage 10}}}
   :trading-settings {}
   :margin-rec (state/default-state)
   :candles {}})

(defn- fresh-candles
  [n]
  (mapv (fn [i]
          (let [t (- now (* (- n i) 3600000))]
            {:t t :o 440 :c 440 :l 438 :h 442}))
        (range n)))

(deftest sync-emits-projection-before-heavy-io
  (testing "missing candles: stamp save precedes the candle fetch"
    (let [effects (actions/margin-rec-sync (base-state) now)
          ids (mapv first effects)]
      (assert-save-effects-valid! effects)
      (is (= [:effects/save :effects/fetch-candle-snapshot] ids))
      (testing "request stamps keep coin strings in the value, not the path"
        (let [[_ path value] (first effects)]
          (is (= state/candle-requests-path path))
          (is (= {"xyz:TSM" now} value))))
      (is (= [:effects/fetch-candle-snapshot
              :coin "xyz:TSM"
              :interval state/candle-interval
              :bars state/candle-fetch-bars]
             (second effects)))))
  (testing "ready candles: computing save precedes the compute effect"
    (let [ready (assoc-in (base-state)
                          [:candles "xyz:TSM" state/candle-interval]
                          (fresh-candles 80))
          effects (actions/margin-rec-sync ready now)
          ids (mapv first effects)]
      (assert-save-effects-valid! effects)
      (is (= [:effects/save :effects/margin-rec-compute] ids))
      (let [[_ path value] (first effects)]
        (is (= state/computing-path path))
        (is (= "xyz:TSM|xyz" (:key value))))
      (let [[_ job] (second effects)]
        (is (= "xyz:TSM|xyz" (:key job)))
        (is (map? (:inputs job))))))
  (testing "nothing to do emits nothing"
    (let [idle (assoc (base-state) :perp-dex-clearinghouse {})]
      (is (= [] (actions/margin-rec-sync idle now))))))

(deftest toggle-panel-toggles-and-closes
  (is (= [[:effects/save state/panel-path "xyz:TSM|xyz"]]
         (actions/toggle-margin-rec-panel (base-state) "xyz:TSM|xyz")))
  (let [open (assoc-in (base-state) state/panel-path "xyz:TSM|xyz")]
    (is (= [[:effects/save state/panel-path nil]]
           (actions/toggle-margin-rec-panel open "xyz:TSM|xyz")))
    (is (= [[:effects/save state/panel-path "other"]]
           (actions/toggle-margin-rec-panel open "other")))))

(deftest close-and-keydown-dismiss-panel
  (is (= [[:effects/save state/panel-path nil]]
         (actions/close-margin-rec-panel (base-state))))
  (testing "Escape closes; other keys are inert"
    (is (= [[:effects/save state/panel-path nil]]
           (actions/handle-margin-rec-panel-keydown (base-state) "Escape")))
    (is (= [] (actions/handle-margin-rec-panel-keydown (base-state) "Enter")))
    (is (= [] (actions/handle-margin-rec-panel-keydown (base-state) "Tab")))))

(deftest settings-setters-persist-normalized-state
  (let [effects (actions/set-margin-rec-risk-mode (base-state) "conservative")
        [[_ path saved] [_ storage-key stored]] effects]
    (is (= [:trading-settings] path))
    (is (= :conservative (:margin-rec-risk-mode saved)))
    (is (= trading-settings/storage-key storage-key))
    (is (= saved stored)))
  (let [effects (actions/set-margin-rec-auto-topup (base-state) true)
        [[_ _ saved]] effects]
    (is (true? (:margin-rec-auto-topup? saved))))
  (testing "unknown mode falls back to balanced"
    (let [[[_ _ saved]] (actions/set-margin-rec-risk-mode (base-state) "wild")]
      (is (= :balanced (:margin-rec-risk-mode saved))))))

(defn- with-intent
  [state intent]
  (assoc-in state (conj state/intents-path (:position-key intent)) intent))

(defn- pending-intent
  [& [overrides]]
  (state/make-intent (merge {:position-key "xyz:TSM|xyz"
                             :coin "xyz:TSM"
                             :dex "xyz"
                             :expected-size 0.36
                             :target-equity 18.64
                             :source :trade}
                            overrides)
                     now))

(deftest process-intents-submits-matched-topup
  (let [state (with-intent (base-state) (pending-intent))
        effects (actions/margin-rec-process-intents state (+ now 5000))
        [save submit] effects]
    (assert-save-effects-valid! effects)
    (is (= 2 (count effects)))
    (is (= :effects/save (first save)))
    (let [saved-intent (get (nth save 2) "xyz:TSM|xyz")]
      (is (= :submitted (:status saved-intent)))
      (is (< (js/Math.abs (- 6.22 (:submitted-amount saved-intent))) 1e-9)))
    (is (= :effects/api-submit-position-margin (first submit)))
    (is (= {:action {:type "updateIsolatedMargin"
                     :asset 100001
                     :isBuy true
                     :ntli 6220000}}
           (second submit)))))

(deftest process-intents-guards
  (testing "expired intents resolve without submitting"
    (let [state (with-intent (base-state) (pending-intent))
          effects (actions/margin-rec-process-intents
                   state
                   (+ now state/intent-ttl-ms 1))
          [save & rest-effects] effects]
      (is (= 1 (count effects)))
      (is (empty? rest-effects))
      (is (= :expired (:status (get (nth save 2) "xyz:TSM|xyz"))))))
  (testing "size mismatch waits (no effects)"
    (let [state (with-intent (base-state)
                             (pending-intent {:expected-size 5}))]
      (is (= [] (actions/margin-rec-process-intents state (+ now 5000))))))
  (testing "no pending intents is a no-op"
    (is (= [] (actions/margin-rec-process-intents (base-state) now))))
  (testing "already-funded positions skip"
    (let [state (with-intent (base-state)
                             (pending-intent {:target-equity 12.5}))
          effects (actions/margin-rec-process-intents state (+ now 5000))
          [save] effects]
      (is (= 1 (count effects)))
      (is (= :skipped (:status (get (nth save 2) "xyz:TSM|xyz"))))
      (is (= :already-funded (:skip-reason (get (nth save 2) "xyz:TSM|xyz"))))))
  (testing "draft intents (no clock at creation) are stamped on first pass"
    (let [draft (state/make-intent-draft {:position-key "xyz:TSM|xyz"
                                          :coin "xyz:TSM"
                                          :dex "xyz"
                                          :expected-size 0.36
                                          :target-equity 18.64
                                          :source :trade})
          state* (with-intent (base-state) draft)
          effects (actions/margin-rec-process-intents state* now)
          [save submit] effects]
      (is (= 2 (count effects)))
      (is (= (+ now state/intent-ttl-ms)
             (:expires-at (get (nth save 2) "xyz:TSM|xyz"))))
      (is (= :effects/api-submit-position-margin (first submit)))))
  (testing "waits for a recommendation when no explicit target"
    (let [state (with-intent (base-state)
                             (pending-intent {:target-equity nil}))]
      (is (= [] (actions/margin-rec-process-intents state (+ now 5000))))
      (let [with-rec (assoc-in state
                               (conj state/recs-path "xyz:TSM|xyz")
                               {:status :ok
                                :result {:recommended {:equity 20.42}}})
            effects (actions/margin-rec-process-intents with-rec (+ now 5000))]
        (is (= 2 (count effects)))
        (is (= 8000000 (get-in (second (second effects))
                               [:action :ntli])))))))
