(ns hyperopen.views.portfolio.optimize.execution-order-table
  "The dense per-order table for the Execution tab, with its inline per-order type editor
  (Market/Limit/TWAP/Passive) and per-row execution-cost breakdown (spread crossing + book
  impact = price cost, + fees = all-in; a resting Limit/Passive row pays neither spread nor
  impact). Extracted verbatim from hyperopen.views.portfolio.optimize.execution-tab to keep
  each namespace within the size budget; shared pure helpers live in
  hyperopen.views.portfolio.optimize.execution-shared. Only `order-table` is public."
  (:require [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.execution-shared :as shared]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

;; ── row helpers ─────────────────────────────────────────────────────────

(defn- data-role-token
  "Selector-safe data-role suffix (instrument ids carry colons/slashes)."
  [value]
  (-> (str value)
      (str/replace #"[^A-Za-z0-9_-]+" "-")
      (str/replace #"(^-+|-+$)" "")))

(defn- rec-reason
  [order-type]
  (case order-type
    :twap "large clip — slice over time to limit market impact"
    :limit "liquid spot sell — rest at mid and capture the spread"
    :market "small clip — immediacy outweighs impact"
    "medium position — post passively, avoid crossing the spread"))

(defn- editable?
  [{:keys [phase read-only?]}]
  (and (contains? #{:staged :armed} phase) (not read-only?)))

(defn- venue-label
  [row]
  (case (:instrument-type row)
    (:perp :spot) "Hyperliquid"
    "—"))

(def ^:private state-glyph
  {:filled "✓"
   :resting "○"
   :failed "✕"
   :blocked "–"
   :skipped "–"
   :working "◐"
   :queued "○"
   :staged "○"})

(defn- row-display-state
  [{:keys [phase]} row]
  (case (:status row)
    :submitted :filled
    ;; Accepted and live on the book, but NOT filled — an open order.
    :resting :resting
    :failed :failed
    :blocked :blocked
    :skipped :skipped
    :working :working
    :ready (if (contains? #{:armed :running} phase) :queued :staged)
    :staged))

(defn- side-tone
  [side]
  (case side :buy :long :sell :short :muted))

(defn- cost-source-label
  [row]
  (let [cost (:cost row)]
    (->> [(when-let [source (:source cost)] (opt-format/keyword-label source))
          (case (:depth-status cost)
            :insufficient-visible-depth "depth limited"
            nil)]
         (remove nil?)
         (str/join " · ")
         not-empty)))

(defn- state-cell
  [display-state row]
  (case display-state
    :filled [:span {:class ["text-trading-green"]} "filled"]
    :resting [:span {:class ["text-info"]} "open"]
    :failed [:span {:class ["text-trading-red"]}
             (str "rejected" (when (:reason row) (str " · " (opt-format/keyword-label (:reason row)))))]
    :blocked [:span {:class ["text-trading-muted"]} (opt-format/keyword-label (:reason row))]
    :skipped [:span {:class ["text-trading-muted"]} "skipped"]
    :working [:span {:class ["text-warning"]} "sending…"]
    :queued [:span {:class ["text-warning"]} "queued"]
    [:span {:class ["text-trading-muted"]} "staged"]))

(defn- cost-stat
  "One term in the execution-cost equation: a small uppercase label, the bp value (large for
  glanceability), and the $ underneath. `emphasis` tunes the hierarchy — :input for the
  spread/impact/fee inputs (muted), :total for the price-cost subtotal (bright), :allin for
  the boxed accent total."
  [label bps usd emphasis]
  [:div {:class ["optimizer-exec-cost-stat"] :data-emphasis (name emphasis)}
   [:span {:class ["optimizer-exec-cost-stat-label"]} label]
   [:span {:class ["optimizer-exec-cost-stat-bp"]}
    (if (shared/finite bps) (shared/format-bps bps) "—")]
   [:span {:class ["optimizer-exec-cost-stat-usd"]}
    (if (shared/finite usd) (opt-format/format-usdc usd) "—")]])

(defn- cost-op
  [glyph]
  [:span {:class ["optimizer-exec-cost-op"]} glyph])

(defn- cost-breakdown
  "Per-row execution-cost components for the row's effective type. Crossing (market/twap):
  spread crossing + book impact = price cost, + taker fee = all-in. Resting (limit/passive):
  no spread/impact (rests), + maker fee = all-in. A crossing row whose book can't be split
  (untrusted snapshot / flat fallback / prebaked) is `splittable?`=false: spread/impact are
  unknown (nil, not a deceptive 0), and the strip collapses them into a single honest note."
  [model row]
  (let [t (shared/effective-type model row)
        crossing? (shared/crossing-type? t)
        cost (:cost row)
        splittable? (and crossing? (some? (:spread-usd cost)))
        price-cost-usd (if crossing? (or (:estimated-slippage-usd cost) 0) 0)
        price-cost-bps (if crossing? (or (:slippage-bps cost) 0) 0)
        fee-usd (if crossing? (or (:estimated-fee-usd cost) 0) (or (:maker-fee-usd cost) 0))
        fee-bps (if crossing? (or (:fee-bps cost) 0) (or (:maker-fee-bps cost) 0))]
    {:crossing? crossing?
     :splittable? splittable?
     :spread-bps (when splittable? (:spread-bps cost))
     :spread-usd (when splittable? (:spread-usd cost))
     :impact-bps (when splittable? (:impact-bps cost))
     :impact-usd (when splittable? (:impact-usd cost))
     :price-cost-bps price-cost-bps :price-cost-usd price-cost-usd
     :fee-bps fee-bps :fee-usd fee-usd
     :all-in-bps (+ price-cost-bps fee-bps) :all-in-usd (+ price-cost-usd fee-usd)}))

(defn- cost-breakdown-strip
  "The right-hand column of the expanded editor: the execution-cost equation laid out across
  the full width — spread crossing + book impact = price cost, + fees = all-in (each in bp and
  $). A resting Limit/Passive row pays neither spread nor impact, so those two terms collapse
  into a single \"rests\" note and the price cost reads ~0."
  [model row]
  (let [{:keys [crossing? splittable? spread-bps spread-usd impact-bps impact-usd
                price-cost-bps price-cost-usd fee-bps fee-usd all-in-bps all-in-usd]}
        (cost-breakdown model row)]
    [:div {:class ["optimizer-exec-cost-panel"]
           :data-role "portfolio-optimizer-execution-cost-breakdown"}
     [:p {:class ["optimizer-exec-cost-head"]}
      [:span "Execution cost breakdown (est.)"]
      [:span {:class ["optimizer-exec-cost-info"]
              :title (str "Price cost = crossing the spread + walking the book (impact). "
                          "All-in adds exchange fees. Resting Limit/Passive orders pay "
                          "neither spread nor impact and earn the lower maker fee.")}
       "ⓘ"]]
     [:div {:class ["optimizer-exec-cost-eq"]}
      (cond
        ;; A crossing row with a real book: show the spread vs impact split.
        splittable?
        (list (cost-stat "Spread crossing" spread-bps spread-usd :input)
              (cost-op "+")
              (cost-stat "Book impact" impact-bps impact-usd :input))

        ;; A crossing row whose book can't be split (untrusted snapshot / flat fallback):
        ;; the spread is unknown — say so honestly instead of rendering a deceptive 0 bp.
        crossing?
        [:div {:class ["optimizer-exec-cost-rests"]
               :data-role "portfolio-optimizer-execution-cost-unsplit"}
         [:span {:class ["optimizer-exec-cost-stat-label"]} "Spread + impact"]
         [:span {:class ["optimizer-exec-cost-rests-note"]}
          "Not separable — flat estimate (no live book)"]]

        ;; A resting Limit/Passive row pays neither.
        :else
        [:div {:class ["optimizer-exec-cost-rests"]}
         [:span {:class ["optimizer-exec-cost-stat-label"]} "Resting order"]
         [:span {:class ["optimizer-exec-cost-rests-note"]} "No spread or market impact"]])
      (cost-op "=")
      (cost-stat "Price cost" price-cost-bps price-cost-usd :total)
      (cost-op "+")
      (cost-stat "Fees" fee-bps fee-usd :input)
      (cost-op "=")
      (cost-stat "All-in" all-in-bps all-in-usd :allin)]]))

(defn- order-editor-row
  [model row colspan]
  (let [t (shared/effective-type model row)
        rec (shared/recommend-exec-type row)
        params (shared/row-params model row)
        buy? (= :buy (:side row))
        row-id (:row-id row)
        source (cost-source-label row)]
    [:tr {:data-role (str "portfolio-optimizer-execution-order-editor-" (data-role-token (:instrument-id row)))}
     [:td {:colspan colspan :class ["optimizer-exec-order-editor"]}
      [:div {:class ["optimizer-exec-editor-grid"]}
       ;; LEFT — order-type controls + plain-English consequence
       [:div {:class ["optimizer-exec-editor-controls"]}
        [:div {:class ["flex" "flex-wrap" "items-center" "gap-3"]}
         [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted"]}
          (str "Order type · " (:instrument-label row))]
         [:div {:class ["optimizer-exec-toggle" "inline-flex"]}
          (for [ot shared/order-types]
            [:button {:type "button"
                      :class (cond-> ["px-2.5" "py-1" "text-[0.65rem]" "font-medium"]
                               (= t ot) (conj "is-on"))
                      :data-active (str (= t ot))
                      :on {:click [[:actions/set-portfolio-optimizer-execution-row-order-type row-id ot]]}}
             (shared/order-type-labels ot)])]
         (if (not= t rec)
           [:button {:type "button"
                     :class ["font-mono" "text-[0.65rem]" "text-warning"]
                     :on {:click [[:actions/set-portfolio-optimizer-execution-row-order-type row-id :recommended]]}}
            (str "↺ use recommended (" (shared/order-type-labels rec) ")")]
           (shared/chip "recommended" :accent))]
        [:div {:class ["flex" "flex-wrap" "items-center" "gap-2" "text-xs" "text-trading-muted"]}
         (case t
           :market
           [:span "Crosses the spread immediately — full size as one marketable order."]
           :limit
           [:span {:class ["flex" "flex-wrap" "items-center" "gap-2"]}
            [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]}
             "Limit price"]
            (for [[label bp] [["At mid" 0]
                              [(str (if buy? "−" "+") "2 bp") (if buy? -2 2)]
                              [(str (if buy? "−" "+") "5 bp") (if buy? -5 5)]]]
              [:button {:type "button"
                        :class (cond-> ["border" "border-base-300" "px-2" "py-0.5" "text-[0.65rem]"]
                                 (= (:limit-bps params) bp) (conj "optimizer-primary-action" "font-semibold"))
                        :on {:click [[:actions/set-portfolio-optimizer-execution-row-param row-id :limit-bps bp]]}}
               label])
            [:span {:class ["font-mono" "text-trading-muted/70"]}
             (str "rests " (if buy? "below" "above") " mark · GTC")]]
           :twap
           [:span {:class ["flex" "flex-wrap" "items-center" "gap-2"]}
            [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]}
             "Duration"]
            (for [m [5 10 20]]
              [:button {:type "button"
                        :class (cond-> ["border" "border-base-300" "px-2" "py-0.5" "text-[0.65rem]"]
                                 (= (:twap-min params) m) (conj "optimizer-primary-action" "font-semibold"))
                        :on {:click [[:actions/set-portfolio-optimizer-execution-row-param row-id :twap-min m]]}}
               (str m " min")])
            [:span {:class ["font-mono" "text-trading-muted/70"]}
             (str (max 2 (js/Math.round (/ (:twap-min params) 2))) " slices · even spacing")]]
           [:span "Post-only at the best price — never crosses the spread, re-pegs as the book moves."])]
        [:p {:class ["font-mono" "text-[0.6rem]" "text-trading-muted/70"]}
         (str "Recommended: " (shared/order-type-labels rec) " — " (rec-reason rec))]
        (when source
          ;; Cost-basis provenance lifted off the lowest 50% opacity / 0.6rem floor so the
          ;; trust signal (snapshot / orderbook / proxy) is actually readable.
          [:p {:class ["font-mono" "text-[0.65rem]" "text-trading-muted/70"]}
           (str "Cost basis · " source)])]
       ;; RIGHT — execution-cost equation across the remaining width
       (cost-breakdown-strip model row)]]]))

(defn- slip-cell
  "Type-aware slippage display. After a fill the realized slippage (vs the same mark the
  estimate used) takes over. Pre-fill: resting orders (limit/passive) read \"rests\" rather
  than the book-crossing market-impact estimate — which would badly overstate their cost;
  crossing orders (market/twap) show the impact estimate; non-ready rows show \"—\"."
  [order-type status est-slip realized-slip]
  (cond
    (shared/finite realized-slip)
    [:span {:title "Realized fill vs the mark the estimate used."} (shared/format-bps realized-slip)]

    (and (= :ready status) (shared/resting-type? order-type))
    [:span {:title "Resting order — pays the spread/offset, not market impact; may not fully fill."}
     "rests"]

    :else (shared/format-bps est-slip)))

(defn- order-row
  [{:keys [open-row] :as model} index row]
  (let [editable (editable? model)
        open? (and editable (= open-row (:row-id row)))
        display-state (row-display-state model row)
        t (shared/effective-type model row)
        overridden? (contains? (:overrides model) (:row-id row))
        side (:side row)
        buy? (= :buy side)
        notional (:delta-notional-usd row)
        row-tr
        [:tr {:class (cond-> ["optimizer-exec-order-row"]
                       (= :failed display-state) (conj "is-failed")
                       (contains? #{:blocked :skipped} display-state) (conj "is-muted")
                       open? (conj "is-open"))
              :data-role (str "portfolio-optimizer-execution-order-row-" (data-role-token (:instrument-id row)))
              :data-exec-state (name display-state)
              :on (when editable
                    {:click [[:actions/toggle-portfolio-optimizer-execution-row (:row-id row)]]})}
         [:td {:class ["optimizer-exec-glyph"] :data-state (name display-state)}
          (state-glyph display-state)]
         [:td {:class ["num" "text-trading-muted/70"]} (opt-format/format-decimal (or (:order-no row) (inc index)) {:maximum-fraction-digits 0})]
         [:td [:span {:class ["font-mono" "font-semibold" "text-trading-text"]} (:instrument-label row)]]
         [:td (shared/chip (name (or side :flat)) (side-tone side))]
         [:td
          [:span {:class ["text-[0.7rem]" "text-trading-muted"]} (venue-label row)]
          (when (:instrument-type row)
            [:span {:class ["optimizer-table-kind-badge" "ml-1.5"]
                    :data-kind (name (:instrument-type row))}
             (name (:instrument-type row))])]
         [:td
          [:span {:class ["inline-flex" "items-center" "gap-1.5"]}
           (when overridden? [:span {:class ["optimizer-exec-override-dot"]}])
           [:span {:class (cond-> ["optimizer-exec-type-chip"] overridden? (conj "is-overridden"))}
            (shared/order-type-labels t)]
           (when editable [:span {:class ["text-[0.6rem]" "text-trading-muted/70"]} (if open? "▾" "▸")])]]
         [:td {:class ["num" "right"]} (opt-format/format-decimal (:quantity row) {:maximum-fraction-digits 4})]
         ;; Exact dollar notional (not $Nk-rounded) on the row the trader vets before arming,
         ;; matching the rebalance preview's precision so the same order reads the same in both.
         [:td {:class ["num" "right" (if buy? "text-trading-green" "text-trading-red")]
               :title "Order notional"}
          (str (if buy? "+" "−") (opt-format/format-usdc (shared/abs-num notional)))]
         [:td {:class ["num" "right" "text-trading-muted"]}
          (slip-cell t (:status row) (get-in row [:cost :slippage-bps]) (get-in row [:realized :slippage-bps]))]
         [:td {:class ["text-[0.7rem]"]} (state-cell display-state row)]]]
    (if open?
      [row-tr (order-editor-row model row 10)]
      [row-tr])))

(defn- row-visible?
  [order-filter display-state]
  (case order-filter
    ;; A resting order is a live, working order on the book — surface it under "Working".
    :working (contains? #{:queued :working :resting} display-state)
    :filled (= :filled display-state)
    true))

(defn- order-filter-toggle
  [active]
  (into [:div {:class ["optimizer-exec-toggle" "inline-flex"]
               :data-role "portfolio-optimizer-execution-order-filter"}]
        (for [[id label] [[:all "All"] [:working "Working"] [:filled "Filled"]]]
          [:button {:type "button"
                    :class (cond-> ["px-2.5" "py-1" "text-[0.65rem]" "font-medium"]
                             (= active id) (conj "is-on"))
                    :data-active (str (= active id))
                    :on {:click [[:actions/set-portfolio-optimizer-execution-order-filter id]]}}
           label])))

(defn order-table
  [{:keys [order-filter] :as model} rows]
  (let [active-filter (or order-filter :all)
        any-visible? (some #(row-visible? active-filter (row-display-state model %)) rows)]
    [:section {:class ["flex" "flex-col" "min-h-0" "xl:border-r" "border-base-300"]
               :data-role "portfolio-optimizer-execution-order-list"}
     [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
      [:div {:class ["flex" "flex-wrap" "items-center" "justify-between" "gap-2"]}
       (shared/eyebrow "Order list")
       (order-filter-toggle active-filter)]
      [:p {:class ["mt-1" "text-xs" "text-trading-muted"]}
       (if (editable? model)
         "Click any order to change its type. Blocked rows stay visible with their reason."
         "Order types are locked once execution is armed.")]]
     (cond
       (not (seq rows))
       [:p {:class ["px-4" "py-4" "text-sm" "text-trading-muted"]}
        "No orders are staged for this run."]

       (not any-visible?)
       [:p {:class ["px-4" "py-4" "text-sm" "text-trading-muted"]}
        (str "No " (name active-filter) " orders to show.")]

       :else
       [:div {:class ["overflow-x-auto"]}
        (into
         [:table {:class ["optimizer-table" "optimizer-exec-table"]}
          [:thead
           [:tr
            [:th {:class ["w-8"]}]
            [:th {:class ["w-10"]} "#"]
            [:th "Asset"]
            [:th "Side"]
            [:th "Venue"]
            [:th "Type"]
            [:th {:class ["right"]} "Qty"]
            [:th {:class ["right"]} "Notional"]
            [:th {:class ["right"]} "Cost"]
            [:th "State"]]]]
         ;; index over the full row set so the # column keeps stable order numbers
         ;; even when a filter hides rows.
         (mapcat (fn [index row]
                   (if (row-visible? active-filter (row-display-state model row))
                     (order-row model index row)
                     []))
                 (range)
                 rows))])]))
