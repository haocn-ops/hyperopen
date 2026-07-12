(ns hyperopen.views.account-info.margin-recommendation-panel-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.account-info.margin-recommendation-panel :as panel]
            [hyperopen.views.account-info.positions-vm :as positions-vm]
            [hyperopen.views.account-info.tabs.positions.desktop :as positions-desktop]))

(defn- node-children
  [node]
  (if (map? (second node))
    (drop 2 node)
    (rest node)))

(defn- all-nodes
  [root]
  (tree-seq vector? node-children root))

(defn- node-attrs
  [node]
  (when (map? (second node))
    (second node)))

(defn- node-by-role
  [root role]
  (some #(when (and (vector? %)
                    (= role (:data-role (node-attrs %))))
           %)
        (all-nodes root)))

(defn- collect-strings
  [root]
  (set (filter string? (all-nodes root))))

(defn- click-actions
  [node]
  (get-in (node-attrs node) [:on :click]))

(def rec-result
  {:status :ok
   :coin "xyz:TSM"
   :dex "xyz"
   :risk-mode :balanced
   :alpha 0.02
   :horizon {:hours 72 :source :per-coin :samples 22 :bars 72}
   :as-of {:mark 437.51 :equity 12.42 :liquidation-px 424.2
           :notional 157.5 :side :long}
   :sigma {:hourly 0.017 :daily 0.083 :distance-frac 0.0304 :buffer-sigmas 0.74}
   :p-now 0.146
   :p-after 0.021
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

(def rec-entry
  {:status :ok :result rec-result :computed-at 1})

(def xyz-position
  {:coin "xyz:TSM"
   :szi "0.36"
   :entryPx "446.441"
   :positionValue "157.5"
   :liquidationPx "424.20"
   :marginUsed "12.42"
   :maxLeverage 10
   :leverage {:type "isolated" :value 10}})

(def position-data
  {:position xyz-position :dex "xyz"})

(def row-vm
  (positions-vm/position-row-vm position-data))

(defn- render-panel
  [& [overrides]]
  (panel/margin-recommendation-panel
   (merge {:position-key "xyz:TSM|xyz"
           :rec rec-entry
           :row-vm row-vm
           :read-only? false
           :risk-mode :balanced
           :anchor {:left 400 :right 460 :top 500
                    :viewport-width 1440 :viewport-height 900}}
          overrides)))

(deftest panel-renders-tiles-breakdown-and-actions
  (let [tree (render-panel)
        strings (collect-strings tree)]
    (is (some? (node-by-role tree "margin-rec-panel")))
    (testing "headline and tiles"
      (is (contains? strings "Isolated margin recommendation"))
      (doseq [role ["margin-rec-tile-current" "margin-rec-tile-distance"
                    "margin-rec-tile-horizon" "margin-rec-tile-recommended"
                    "margin-rec-tile-new-liq" "margin-rec-tile-leverage"]]
        (is (some? (node-by-role tree role)) role))
      (is (contains? strings "$18.64"))
      (testing "probabilities render (via the risk-compare block, not duplicate tiles)"
        (is (contains? strings "14.6%"))
        (is (contains? strings "2.1%"))
        (is (nil? (node-by-role tree "margin-rec-tile-p-now")))
        (is (nil? (node-by-role tree "margin-rec-tile-p-after"))))
      (is (contains? strings "0.74σ buffer"))
      (is (contains? strings "3 days"))
      (is (contains? strings "based on 22 prior position episodes")))
    (testing "breakdown pins every named buffer"
      (is (some? (node-by-role tree "margin-rec-breakdown")))
      (doseq [label ["Maintenance requirement" "Adverse-path protection"
                     "Funding buffer (3d)" "Exit / slippage buffer (1.0% notional)"
                     "Model uncertainty buffer" "Total recommended margin"]]
        (is (contains? strings label) label)))
    (testing "renders as an anchored popover positioned against the trigger"
      (let [panel (node-by-role tree "margin-rec-panel")
            style (:style (node-attrs panel))]
        (is (= "dialog" (:role (node-attrs panel))))
        (is (contains? style :left))
        (is (contains? style :top))
        (is (contains? style :width))))
    (testing "apply opens the prefilled margin modal then dismisses the panel"
      (let [apply-node (node-by-role tree "margin-rec-apply")
            [[action-id payload placeholder] close-action] (click-actions apply-node)]
        (is (contains? strings "Add recommended margin $6.22"))
        (is (= :actions/open-position-margin-modal action-id))
        (is (= 6.22 (:prefill-margin-amount payload)))
        (is (= :add (:prefill-margin-mode payload)))
        (is (= xyz-position (:position payload)))
        (is (= :event.currentTarget/bounds placeholder))
        (is (= [:actions/close-margin-rec-panel] close-action))))
    (testing "reduce dismisses the panel; close button and Escape close it"
      (is (= [[:actions/open-position-reduce-popover position-data
               :event.currentTarget/bounds]
              [:actions/close-margin-rec-panel]]
             (click-actions (node-by-role tree "margin-rec-reduce"))))
      (is (= [[:actions/close-margin-rec-panel]]
             (click-actions (node-by-role tree "margin-rec-panel-close"))))
      (is (= [[:actions/handle-margin-rec-panel-keydown [:event/key]]]
             (get-in (node-attrs (node-by-role tree "margin-rec-panel"))
                     [:on :keydown]))))
    (testing "risk-mode control marks the active mode"
      (is (= "true" (:aria-pressed (node-attrs (node-by-role tree "margin-rec-risk-mode-balanced")))))
      (is (= "false" (:aria-pressed (node-attrs (node-by-role tree "margin-rec-risk-mode-conservative")))))
      (is (= [[:actions/set-margin-rec-risk-mode :conservative]]
             (click-actions (node-by-role tree "margin-rec-risk-mode-conservative")))))))

(deftest panel-read-only-hides-actions
  (let [tree (render-panel {:read-only? true})]
    (is (nil? (node-by-role tree "margin-rec-apply")))
    (is (nil? (node-by-role tree "margin-rec-reduce")))
    (is (some? (node-by-role tree "margin-rec-breakdown")))))

(deftest panel-terminal-states
  (testing "insufficient history"
    (let [tree (render-panel {:rec {:status :insufficient-history
                                    :result {:status :insufficient-history
                                             :n-bars 20}}})]
      (is (some? (node-by-role tree "margin-rec-insufficient")))))
  (testing "still computing"
    (is (some? (node-by-role (render-panel {:rec nil}) "margin-rec-computing"))))
  (testing "error"
    (is (some? (node-by-role (render-panel {:rec {:status :error :error "boom"}})
                             "margin-rec-error")))))

(deftest desktop-row-chip-and-suggestion
  (let [positions-state {:margin-rec {:recs {"xyz:TSM|xyz" rec-entry}
                                      :panel nil
                                      :risk-mode :balanced}}
        row (positions-desktop/position-row-from-vm row-vm nil nil nil false positions-state)
        strings (collect-strings row)
        chip (node-by-role row "margin-rec-risk-chip")
        suggestion (node-by-role row "margin-rec-row-suggestion")]
    (is (some? chip))
    (is (contains? strings "Liq. risk high"))
    (testing "chip opens the popover anchored to itself"
      (is (= [[:actions/toggle-margin-rec-panel "xyz:TSM|xyz" :event.currentTarget/bounds]]
             (click-actions chip))))
    (is (some? suggestion))
    (is (contains? strings "Recommended: $18.64"))
    (testing "no chip or suggestion without a recommendation"
      (let [bare (positions-desktop/position-row-from-vm row-vm nil nil nil false {})]
        (is (nil? (node-by-role bare "margin-rec-risk-chip")))
        (is (nil? (node-by-role bare "margin-rec-row-suggestion")))))))
