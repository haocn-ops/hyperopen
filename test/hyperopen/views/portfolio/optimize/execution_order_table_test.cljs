(ns hyperopen.views.portfolio.optimize.execution-order-table-test
  "Order-list honesty: skipped (no-order) rows live in a collapsed section instead of
  padding the order table, the venue is stated once instead of per row, recommended
  routes carry a by-exception rationale, and the passive type is named Passive maker."
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.test-support.hiccup :as h]
            [hyperopen.views.portfolio.optimize.execution-tab :as execution-tab]))

(def ^:private labels
  {"perp:EWZ" "EWZ" "perp:NOW" "NOW" "perp:REZ" "REZ" "spot:USDH" "USDH"})

(def ^:private plan
  {:status :ready
   :execution-disabled? false
   :summary {:ready-count 3 :blocked-count 0 :skipped-count 1
             :gross-ready-notional-usd 135
             :margin {:after-utilization 0.1 :after-gross-leverage 1.1
                      :before-gross-leverage 1.0 :free-margin-usd 300
                      :capital-usd 320 :warning :none}}
   :rows [{:row-id "perp:EWZ" :instrument-id "perp:EWZ" :instrument-type :perp
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
          {:row-id "spot:USDH" :instrument-id "spot:USDH" :instrument-type :spot
           :status :ready :side :sell :quantity 8.28 :order-type :market
           :delta-notional-usd -8.28
           :cost {:source :snapshot :slippage-bps 2 :estimated-slippage-usd 0.002
                  :fee-bps 4.5 :estimated-fee-usd 0.004
                  :maker-fee-bps 1.5 :maker-fee-usd 0.001}}
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

(deftest skipped-rows-collapse-out-of-the-order-list-test
  ;; 3 orders will be sent; REZ is within tolerance. It must not read as a 4th order:
  ;; it renders (with its ledger # and plain-language reason) only inside the collapsed
  ;; "Skipped" section, and the list headline counts the sendable rows.
  (let [node (view nil)
        order-list (h/find-by-data-role node "portfolio-optimizer-execution-order-list")
        skipped (h/find-by-data-role node "portfolio-optimizer-execution-skipped")
        rez (h/find-by-data-role node "portfolio-optimizer-execution-order-row-perp-REZ")]
    (is (some? skipped))
    (is (str/includes? (h/node-text skipped) "Skipped — 1 asset"))
    (is (str/includes? (h/node-text skipped) "no orders will be sent"))
    (is (some? (h/find-by-data-role skipped "portfolio-optimizer-execution-order-row-perp-REZ"))
        "the skipped row lives inside the skipped section")
    (is (str/includes? (h/node-text rez) "within 3 pp band"))
    (is (str/includes? (h/node-text order-list) "Order list — 3 to send"))))

(deftest venue-stated-once-not-per-row-test
  ;; Every optimizer order routes to Hyperliquid — a Venue column repeating it on each
  ;; row wasted the width the type/cost data needs. One line above the table carries it,
  ;; with the perp/spot mix; the per-row perp/spot badge stays (it differs by row).
  (let [node (view nil)
        order-list (h/find-by-data-role node "portfolio-optimizer-execution-order-list")
        text (h/node-text order-list)
        ewz (h/find-by-data-role node "portfolio-optimizer-execution-order-row-perp-EWZ")]
    (is (str/includes? text "All orders route to Hyperliquid — 2 perp · 1 spot."))
    (is (nil? (h/find-first-node order-list #(and (vector? %) (= :th (first %))
                                                  (= "Venue" (last %)))))
        "no Venue column header remains")
    (is (some? (h/find-first-node ewz #(= "perp" (get-in % [1 :data-kind]))))
        "the kind badge moved into the asset cell")))

(deftest route-hint-marks-recommended-non-market-routes-test
  ;; By-exception rationale: the 45.4bp EWZ row routes passive under Recommended and
  ;; says why in the Type cell; the cheap NOW market route stays quiet.
  (let [node (view nil)
        ewz (h/find-by-data-role node "portfolio-optimizer-execution-order-row-perp-EWZ")
        now (h/find-by-data-role node "portfolio-optimizer-execution-order-row-perp-NOW")
        hint (h/find-by-data-role ewz "portfolio-optimizer-execution-route-hint")]
    (is (some? hint))
    (is (str/includes? (h/node-text hint) "45.4 bp as market — rests instead"))
    (is (str/includes? (h/node-text ewz) "Passive maker")
        "the passive type is labeled Passive maker, never a bare Limit synonym")
    (is (nil? (h/find-by-data-role now "portfolio-optimizer-execution-route-hint")))))

(deftest route-hint-absent-for-user-override-test
  ;; A user override is a choice, not an algo route — no rationale hint is claimed.
  (let [node (view {:overrides {"perp:EWZ" :twap}})
        ewz (h/find-by-data-role node "portfolio-optimizer-execution-order-row-perp-EWZ")]
    (is (nil? (h/find-by-data-role ewz "portfolio-optimizer-execution-route-hint")))))

(deftest read-only-view-keeps-order-rows-editable-test
  ;; Spectate / read-only: a staged order row still opens its per-order type/param editor (a pure
  ;; cost simulation — it only writes modal state). The row carries the toggle action and the list
  ;; header invites the edit, even though the plan itself can't be armed or sent.
  (let [read-only-plan (assoc plan
                              :execution-disabled? true
                              :disabled-reason :read-only
                              :disabled-message "Spectate Mode is read-only.")
        node (view {:plan read-only-plan})
        order-list (h/find-by-data-role node "portfolio-optimizer-execution-order-list")
        ewz (h/find-by-data-role node "portfolio-optimizer-execution-order-row-perp-EWZ")]
    (is (= [[:actions/toggle-portfolio-optimizer-execution-row "perp:EWZ"]]
           (get-in ewz [1 :on :click]))
        "the row is clickable to open its type editor")
    (is (str/includes? (h/node-text order-list) "Click any order to change its type"))))
