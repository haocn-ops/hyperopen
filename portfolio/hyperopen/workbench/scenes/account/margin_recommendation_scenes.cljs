(ns hyperopen.workbench.scenes.account.margin-recommendation-scenes
  "The isolated-margin recommendation popover rendered over explicit result
  fixtures, matching the designer's card (2026-07-12): current-state stats,
  green recommendation block, probability-vs-collateral curve, methods +
  buffers columns, risk selector, and the apply / custom actions."
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.views.account-info.margin-recommendation-panel :as panel]
            [hyperopen.views.account-info.positions-vm :as positions-vm]
            [hyperopen.workbench.support.layout :as layout]))

(portfolio/configure-scenes
  {:title "Margin recommendation"
   :collection :account})

(def ^:private position-data
  {:position {:coin "xyz:TSM"
              :szi "0.36"
              :entryPx "446.441"
              :positionValue "157.5"
              :liquidationPx "424.20"
              :marginUsed "12.42"
              :maxLeverage 10
              :leverage {:type "isolated" :value 10}}
   :dex "xyz"})

(def ^:private row-vm
  (positions-vm/position-row-vm position-data))

(defn- fixture-curve
  "Smooth declining p_liq(E) fixture shaped like the real engine output."
  []
  (let [x-max 40
        n 50
        step (/ x-max (dec n))]
    {:x-max x-max
     :points (mapv (fn [i]
                     (let [e (* step i)]
                       {:e e
                        :p (min 1 (* 0.98 (js/Math.exp
                                           (- (js/Math.pow (/ e 7.2) 1.35)))))}))
                   (range n))}))

(def ^:private rec-result
  {:status :ok
   :coin "xyz:TSM"
   :dex "xyz"
   :risk-mode :balanced
   :alpha 0.02
   :horizon {:hours 72 :source :per-coin :samples 22 :bars 72}
   :as-of {:mark 437.51 :equity 12.42 :liquidation-px 424.2
           :notional 157.5 :side :long}
   :sigma {:hourly 0.0093 :daily 0.0456 :annualized 0.87
           :distance-frac 0.082 :buffer-sigmas 0.74}
   :p-now 0.146
   :p-after 0.021
   :paths-count 4000
   :curve (fixture-curve)
   :risk-level :high
   :recommended {:equity 18.64
                 :additional 6.22
                 :new-liquidation-px 403.1
                 :new-liq-change-frac 0.0497
                 :effective-leverage 8.4}
   :breakdown [{:key :maintenance :label "Maintenance requirement" :amount 5.41}
               {:key :adverse-path :label "Adverse-path protection" :amount 7.87}
               {:key :funding :label "Funding buffer (3d)" :amount 2.08}
               {:key :exit :label "Exit / slippage buffer (1.0% notional)" :amount 1.82}
               {:key :model :label "Model uncertainty buffer" :amount 1.46}]
   :confidence {:tier :high :n-bars 1080}})

(def ^:private anchor
  {:left 620 :right 680 :top 900
   :viewport-width 1440 :viewport-height 960})

(defn- scene-shell
  [rec-entry & [overrides]]
  (layout/page-shell
   ;; Tailwind only scans src/, so scene-only arbitrary classes never exist in
   ;; the built CSS; inline style keeps the canvas iframe tall enough for the
   ;; fixed-position popover.
   {:style {:min-height "1120px"}}
   [:div
    (panel/margin-recommendation-panel
     (merge {:position-key "xyz:TSM|xyz"
             :rec rec-entry
             :row-vm row-vm
             :read-only? false
             :risk-mode :balanced
             :anchor anchor}
            overrides))]))

(portfolio/defscene elevated-risk-recommendation
  []
  (scene-shell {:status :ok :result rec-result :computed-at 1}))

(portfolio/defscene within-target
  []
  ;; Already safe: recommended ≈ current, nothing to add, probability already
  ;; under the target on both sides.
  (scene-shell {:status :ok
                :result (-> rec-result
                            (assoc :status :within-target :p-now 0.008 :p-after 0.006)
                            (assoc-in [:as-of :equity] 18.64)
                            (assoc :recommended {:equity 18.64
                                                 :additional 0
                                                 :new-liquidation-px 403.1
                                                 :new-liq-change-frac 0.0497
                                                 :effective-leverage 8.4}))
                :computed-at 1}))

(portfolio/defscene cached-result-without-curve
  []
  (scene-shell {:status :ok
                :result (dissoc rec-result :curve)
                :computed-at 1}))

(portfolio/defscene still-computing
  []
  (scene-shell nil))
