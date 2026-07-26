(ns hyperopen.views.portfolio.optimize.execution-strategy-band-test
  "The staged-phase execution-strategy surface: honest tiles (Passive maker is a
  first-class strategy, Limit is not a bulk posture), live per-strategy cost
  projections, the high-cost-crossing warning with its one-click passive fix, and the
  counted Arm CTA."
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.test-support.hiccup :as h]
            [hyperopen.views.portfolio.optimize.execution-tab :as execution-tab]))

(def ^:private labels
  {"perp:EWZ" "EWZ" "perp:NOW" "NOW" "perp:REZ" "REZ"})

(def ^:private plan
  {:status :ready
   :execution-disabled? false
   :summary {:ready-count 2 :blocked-count 0 :skipped-count 1
             :gross-ready-notional-usd 127
             :margin {:after-utilization 0.1 :after-gross-leverage 1.1
                      :before-gross-leverage 1.0 :free-margin-usd 300
                      :capital-usd 320 :warning :none}}
   :rows [;; the motivating case: tiny clip, expensive crossing (45.4bp)
          {:row-id "perp:EWZ" :instrument-id "perp:EWZ" :instrument-type :perp
           :status :ready :side :buy :quantity 2.47 :order-type :market
           :delta-notional-usd 87
           :cost {:source :snapshot :slippage-bps 45.4 :estimated-slippage-usd 0.4
                  :fee-bps 4.5 :estimated-fee-usd 0.04
                  :maker-fee-bps 1.5 :maker-fee-usd 0.01}}
          {:row-id "perp:NOW" :instrument-id "perp:NOW" :instrument-type :perp
           :status :ready :side :buy :quantity 0.38 :order-type :market
           :delta-notional-usd 40
           :cost {:source :snapshot :slippage-bps 1 :estimated-slippage-usd 0.01
                  :fee-bps 4.5 :estimated-fee-usd 0.02
                  :maker-fee-bps 1.5 :maker-fee-usd 0.005}}
          {:row-id "perp:REZ" :instrument-id "perp:REZ" :instrument-type :perp
           :status :skipped :side :sell :reason :within-tolerance :tolerance 0.03
           :quantity 2680.5 :delta-notional-usd -8.4}]})

(defn- view
  [modal-overrides]
  (execution-tab/execution-tab
   {:portfolio {:optimizer
                {:last-successful-run {:result {:labels-by-instrument labels}}
                 :execution-modal (merge {:open? true :plan plan :phase :staged
                                          :default-order-type :recommended
                                          :overrides {} :params {}}
                                         modal-overrides)
                 :execution {:status :idle :history []}}}}))

(deftest strategy-tiles-are-recommended-market-passive-twap-test
  ;; "Limit" is an order type with a price, not a bulk execution posture — the strategy
  ;; row offers Passive maker (post-only) instead; Limit stays a per-row override.
  (let [node (view nil)
        band (h/find-by-data-role node "portfolio-optimizer-execution-control-band")]
    (is (some? (h/find-by-data-role band "portfolio-optimizer-execution-mode-recommended")))
    (is (some? (h/find-by-data-role band "portfolio-optimizer-execution-mode-market")))
    (is (some? (h/find-by-data-role band "portfolio-optimizer-execution-mode-passive")))
    (is (some? (h/find-by-data-role band "portfolio-optimizer-execution-mode-twap")))
    (is (nil? (h/find-by-data-role band "portfolio-optimizer-execution-mode-limit")))
    (is (str/includes? (h/node-text band) "Execution strategy"))
    (is (not (str/includes? (h/node-text band) "Default order type")))))

(deftest strategy-tiles-project-live-costs-test
  ;; Each tile states the est. all-in cost of the plan under that strategy — the same
  ;; type-aware recompute the KPI strip uses — so the selector is a tradeoff comparison.
  ;; Market: 0.40+0.01 slippage + 0.04+0.02 taker = $0.47. Passive: 0.015 maker = $0.02.
  ;; Recommended mixes: EWZ passive (0.01) + NOW market (0.01+0.02) = $0.04.
  (let [node (view nil)
        consequence (fn [id]
                      (h/node-text
                       (h/find-by-data-role
                        node (str "portfolio-optimizer-execution-mode-" id "-consequence"))))]
    (is (str/includes? (consequence "market") "$0.47"))
    (is (str/includes? (consequence "passive") "$0.02"))
    (is (str/includes? (consequence "recommended") "$0.04"))
    ;; the recommended tile also discloses its routing mix
    (is (str/includes? (consequence "recommended") "1 market"))
    (is (str/includes? (consequence "recommended") "1 passive maker"))))

(deftest high-cost-warning-absent-under-recommended-test
  ;; Under Recommended the 45.4bp row routes passive — nothing high-cost crosses, so
  ;; there is nothing to warn about.
  (is (nil? (h/find-by-data-role (view nil)
                                 "portfolio-optimizer-execution-high-cost-warning"))))

(deftest high-cost-warning-under-market-all-offers-passive-fix-test
  ;; A bulk Market default exposes the expensive crossing: the band names the row and
  ;; its bp cost, and the one-click fix emits exactly the per-row passive overrides.
  (let [node (view {:default-order-type :market})
        warning (h/find-by-data-role node "portfolio-optimizer-execution-high-cost-warning")
        fix (h/find-by-data-role node "portfolio-optimizer-execution-rest-high-cost")]
    (is (some? warning))
    (is (str/includes? (h/node-text warning) "EWZ 45.4 bp"))
    ;; the fix is quantified: est. all-in falls from market-all $0.47 to $0.04
    ;; (EWZ resting pays only its $0.01 maker fee; NOW stays market at $0.01 + $0.02)
    (is (str/includes? (h/node-text warning) "cuts est. all-in cost from $0.47 to $0.04"))
    (is (some? fix))
    (is (= [[:actions/set-portfolio-optimizer-execution-row-order-type "perp:EWZ" :passive]]
           (get-in fix [1 :on :click])))))

(deftest high-cost-warning-respects-existing-passive-override-test
  ;; A market-all default with the expensive row already overridden passive has no
  ;; high-cost crossing left — no warning, no dead fix button.
  (is (nil? (h/find-by-data-role (view {:default-order-type :market
                                        :overrides {"perp:EWZ" :passive}})
                                 "portfolio-optimizer-execution-high-cost-warning"))))

(deftest arm-cta-counts-the-orders-it-arms-test
  ;; The last button before live money says exactly how many orders it covers.
  (let [arm (h/find-by-data-role (view nil) "portfolio-optimizer-execution-arm")]
    (is (some? arm))
    (is (str/includes? (h/node-text arm) "Arm 2 orders"))))

(def ^:private read-only-plan
  ;; Spectate / read-only: the plan is commit-blocked (:read-only), but the tab is still a cost
  ;; simulator — the strategy tiles must stay interactive.
  (assoc plan
         :execution-disabled? true
         :disabled-reason :read-only
         :disabled-message "Spectate Mode is read-only."))

(deftest read-only-view-keeps-strategy-tiles-interactive-test
  ;; The motivating fix: a spectator can compare Market/Passive/TWAP to see their cost impact.
  ;; Each tile carries its click action and is not :disabled; the notice explains that only
  ;; arming/sending is blocked; Arm itself stays disabled (the commit gate is intact).
  (let [node (view {:plan read-only-plan})
        band (h/find-by-data-role node "portfolio-optimizer-execution-control-band")
        market (h/find-by-data-role band "portfolio-optimizer-execution-mode-market")
        notice (h/find-by-data-role band "portfolio-optimizer-execution-readonly")
        arm (h/find-by-data-role node "portfolio-optimizer-execution-arm")]
    (is (= [[:actions/set-portfolio-optimizer-execution-default-order-type :market]]
           (get-in market [1 :on :click]))
        "the tile dispatches a strategy change")
    (is (nil? (get-in market [1 :disabled]))
        "the tile is not disabled in a read-only view")
    (is (some? notice))
    (is (str/includes? (h/node-text notice) "Spectate Mode is read-only."))
    (is (str/includes? (h/node-text notice) "still model execution strategies"))
    (is (true? (get-in arm [1 :disabled]))
        "Arm stays blocked — nothing can be sent from a read-only view")))

(deftest read-only-view-keeps-high-cost-passive-fix-clickable-test
  ;; The one-click "rest passively" shortcut only rewrites overrides so the KPI strip re-projects
  ;; — it sends nothing, so it stays available in read-only views under a bulk Market default.
  (let [node (view {:plan read-only-plan :default-order-type :market})
        fix (h/find-by-data-role node "portfolio-optimizer-execution-rest-high-cost")]
    (is (some? fix))
    (is (= [[:actions/set-portfolio-optimizer-execution-row-order-type "perp:EWZ" :passive]]
           (get-in fix [1 :on :click])))))

;; ── TWAP cost model + depth-floor honesty on the strategy tiles ────────────

(def ^:private depth-labels {"perp:BIG" "BIG"})

(def ^:private depth-plan
  ;; One $100k sell with a REAL spread/impact split (2 bp spread + 100 bp impact =
  ;; 102 bp / $1,020 one-shot; taker fee $40, maker $10). Worked as its default
  ;; 20-minute TWAP (41 venue clips) the sliced model prices
  ;; 2 + 100/41 + 0.3*100*40/82 = 19.07 bp ≈ $190.73 — so the tiles must diverge.
  {:status :ready
   :execution-disabled? false
   :summary {:ready-count 1 :blocked-count 0 :skipped-count 0
             :gross-ready-notional-usd 100000
             :margin {:after-utilization 0.1 :after-gross-leverage 1.1
                      :before-gross-leverage 1.0 :free-margin-usd 300
                      :capital-usd 320 :warning :none}}
   :rows [{:row-id "perp:BIG" :instrument-id "perp:BIG" :instrument-type :perp
           :status :ready :side :sell :quantity 1000 :order-type :market
           :delta-notional-usd -100000
           :cost {:source :snapshot :slippage-bps 102 :estimated-slippage-usd 1020
                  :spread-bps 2 :spread-usd 20 :impact-bps 100 :impact-usd 1000
                  :notional-usd 100000
                  :fee-bps 4 :estimated-fee-usd 40
                  :maker-fee-bps 1 :maker-fee-usd 10}}]})

(defn- depth-view
  [modal-overrides]
  (execution-tab/execution-tab
   {:portfolio {:optimizer
                {:last-successful-run {:result {:labels-by-instrument depth-labels}}
                 :execution-modal (merge {:open? true :plan depth-plan :phase :staged
                                          :default-order-type :recommended
                                          :overrides {} :params {}}
                                         modal-overrides)
                 :execution {:status :idle :history []}}}}))

(deftest twap-tile-projects-below-market-on-splittable-books-test
  ;; THE motivating fix: before the TWAP cost model existed, Market and TWAP both
  ;; projected the identical one-shot walk ($1,060 all-in here) — making the strategy
  ;; comparison meaningless. TWAP must now price the sliced estimate.
  (let [node (depth-view nil)
        consequence (fn [id]
                      (h/node-text
                       (h/find-by-data-role
                        node (str "portfolio-optimizer-execution-mode-" id "-consequence"))))]
    (is (str/includes? (consequence "market") "$1,060"))
    (is (str/includes? (consequence "twap") "$230.73"))
    (is (not (str/includes? (consequence "twap") "$1,060")))))

(deftest high-cost-warning-judges-twap-rows-on-the-sliced-cost-test
  ;; Under a bulk Market default the 102 bp crossing is flagged; under TWAP the same
  ;; row works at 19.07 bp — below the 20 bp bar, so there is nothing to warn about.
  (is (some? (h/find-by-data-role (depth-view {:default-order-type :market})
                                  "portfolio-optimizer-execution-high-cost-warning")))
  (is (nil? (h/find-by-data-role (depth-view {:default-order-type :twap})
                                 "portfolio-optimizer-execution-high-cost-warning"))))

(deftest floor-estimates-mark-tiles-as-lower-bounds-test
  ;; A depth-overrun estimate is a capped floor, not a point estimate — every tile
  ;; projecting from it must carry the "≥" lower-bound marker.
  (let [floor-plan (update-in depth-plan [:rows 0 :cost] merge
                              {:estimate-floor? true
                               :depth-status :insufficient-visible-depth
                               :depth-overrun 409
                               :depth-coverage 0.0024
                               :visible-notional-usd 244})
        node (depth-view {:plan floor-plan})
        consequence (fn [id]
                      (h/node-text
                       (h/find-by-data-role
                        node (str "portfolio-optimizer-execution-mode-" id "-consequence"))))]
    (is (str/includes? (consequence "market") "≥ $1,060"))
    (is (str/includes? (consequence "twap") "≥ $"))))
