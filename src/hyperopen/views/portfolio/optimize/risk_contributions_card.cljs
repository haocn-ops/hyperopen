(ns hyperopen.views.portfolio.optimize.risk-contributions-card
  "Equal Risk results centerpiece: the RISK CONTRIBUTION BALANCE card — a
  horizontal diverging chart of each position's SIGNED Euler share of
  portfolio volatility against the equal 1/n target, built to the designer's
  2026-07-11 spec. One row per asset: recommended share as the sign-colored
  bar (colored by contribution SIGN, never by position side) capped with a
  green recommended dot, the current share as a gray outlined circle joined
  to the bar end by a dashed connector, a purple per-row target tick on the
  continuous dashed purple equal-target line, and purple Target / sign-colored
  Deviation columns. The axis always includes zero: a negative share means the
  position HEDGES the book, and positive shares can exceed 100% when another
  is negative — the signed shares still sum to 100%. No pies, donuts, stacked
  bars, or absolute contributions: those forms all lie once a contribution is
  negative.

  A five-cell KPI strip (Equal target / Status / RMS / Max / Negative
  contributors) sits between the header and the plot; the header's right side
  is a two-tab switcher (RISK CONTRIBUTION / RISK / RETURN) whose state lives
  entirely in the DOM as a pair of visually-hidden radio inputs toggled by
  scoped :has() CSS — no app state, same zero-state constraint the old
  <details> disclosure satisfied.

  Rows come pre-ordered and capped from the equal-risk-results view-model
  (worst deviations survive the cap, display order is signed share descending,
  honest remainder line); the lanes are plain HTML so the zero/target lines
  stay 1px and markers stay round at any width. Replaces the
  efficient-frontier chart for :equal-risk — that objective is not selected
  from a frontier and must not be plotted as if it were."
  (:require [hyperopen.portfolio.optimizer.application.view-model.equal-risk-results
             :as equal-risk-results]
            [hyperopen.views.portfolio.optimize.risk-return-context
             :as risk-return-context]))

(defn- format-pct
  ([value] (format-pct value 1))
  ([value decimals]
   (if (and (number? value) (js/isFinite value))
     (str (.toFixed (* 100 value) decimals) "%")
     "—")))

(def ^:private quality-copy
  {:exact {:label "Exact"
           :note "Risk contributions are balanced within tolerance."}
   :approximate {:label "Approximate"
                 :note "Best solution found under the selected constraints — exposure targets and limits take priority over exact parity."}
   :not-converged {:label "Not converged"
                   :note "The solver stopped at its iteration limit; this is the best feasible portfolio found."}})

;; --- Shared lane scale --------------------------------------------------------

(defn- lane-scale
  "One scale for every lane, fitted to the PRIMARY data — zero, every target,
  and every recommended share — padded and rounded to 5% so the domain reads
  as clean ticks. Current shares only extend the domain when they sit within
  35% of the primary span beyond it: a wildly unbalanced current book (one
  asset carrying ~100% of volatility is exactly why Equal Risk gets run)
  must not squash the recommended bars into a corner. Currents beyond that
  render as off-scale edge chevrons (see contribution-lane). Returns
  {:lo :hi :x (fraction-of-total -> 0..100 percent)}."
  [{:keys [rows target-share]}]
  (let [primary (concat [0 (or target-share 0)]
                        (keep :share rows)
                        (keep :target-share rows))
        lo-p (reduce min 0 primary)
        hi-p (reduce max 0 primary)
        reach (* 0.35 (max 0.05 (- hi-p lo-p)))
        values (concat primary
                       (filter #(<= (- lo-p reach) % (+ hi-p reach))
                               (keep :current-share rows)))
        lo0 (reduce min 0 values)
        hi0 (reduce max 0 values)
        pad (max 0.02 (* 0.08 (- hi0 lo0)))
        lo (* 0.05 (js/Math.floor (/ (- lo0 pad) 0.05)))
        hi (* 0.05 (js/Math.ceil (/ (+ hi0 pad) 0.05)))
        span (max 1e-9 (- hi lo))]
    {:lo lo
     :hi hi
     :x (fn [value]
          (-> (* 100 (/ (- value lo) span))
              (max 0)
              (min 100)))}))

(defn- tick-values
  [{:keys [lo hi]}]
  (let [span (- hi lo)
        step (or (first (filter #(<= (/ span %) 8)
                                [0.05 0.1 0.2 0.25 0.5 1 2 5]))
                 5)]
    (->> (range (js/Math.ceil (/ lo step)) (inc (js/Math.floor (/ hi step))))
         (map #(* step %)))))

;; --- KPI strip ----------------------------------------------------------------

(defn- kpi-cell
  [data-role label value {:keys [value-class tone title]}]
  [:div {:class ["optimizer-risk-balance-kpi"]
         :data-role data-role
         :title title}
   [:p {:class ["optimizer-risk-balance-kpi-label"]} label]
   [:p {:class (into ["optimizer-risk-balance-kpi-value" "font-mono" "tabular-nums"]
                     (when value-class [value-class]))
        :data-tone (some-> tone name)}
    value]])

(defn- kpi-strip
  [{:keys [target-share quality rms-pts max-pts negative-count]}]
  (let [target-pts (when (number? target-share) (* 100 target-share))
        quality* (get quality-copy quality)]
    [:div {:class ["optimizer-risk-balance-kpis"]
           :data-role "portfolio-optimizer-risk-balance-kpis"}
     (kpi-cell "portfolio-optimizer-risk-contributions-target"
               "Equal target"
               (str (format-pct target-share) " per asset")
               {:value-class "optimizer-risk-balance-kpi-value--target"})
     (kpi-cell "portfolio-optimizer-risk-contributions-quality"
               "Status"
               (or (:label quality*) "—")
               {:value-class "optimizer-risk-balance-kpi-value--status"
                :tone (case quality
                        :exact :good
                        :approximate :caution
                        :not-converged :bad
                        nil)
                :title (:note quality*)})
     (kpi-cell "portfolio-optimizer-risk-contributions-rms"
               "RMS deviation"
               (equal-risk-results/format-pts rms-pts)
               {:tone (equal-risk-results/deviation-tone rms-pts target-pts)})
     (kpi-cell "portfolio-optimizer-risk-contributions-max"
               "Max deviation"
               (equal-risk-results/format-pts max-pts)
               {:tone (equal-risk-results/deviation-tone max-pts target-pts)})
     (kpi-cell "portfolio-optimizer-risk-contributions-negative"
               "Negative contributors"
               (str (or negative-count 0))
               {:tone (when (pos? (or negative-count 0)) :bad)})]))

;; --- Plot ---------------------------------------------------------------------

(defn- legend-row
  [target-share current?]
  [:div {:class ["optimizer-risk-balance-row" "optimizer-risk-balance-legend-row"]}
   [:span]
   [:div {:class ["optimizer-risk-balance-legend"]}
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-balance-legend-dash"]}]
     (str "Target (" (format-pct target-share) ")")]
    (when current?
      [:span {:class ["optimizer-risk-balance-legend-item"]}
       [:span {:class ["optimizer-risk-balance-current"]}]
       "Current"])
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-balance-legend-tick"]}]
     "Target (equal)"]
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-balance-dot"]}]
     "Recommended"]]
   [:span {:class ["optimizer-risk-balance-col-head"]} "Target"]
   [:span {:class ["optimizer-risk-balance-col-head"]} "Deviation"]])

(defn- plot-backdrop
  "Gridlines, the zero axis, and the continuous dashed target line, drawn once
  behind the whole row stack in a same-grid overlay so they read as one plot."
  [{:keys [x] :as scale} target-share]
  [:div {:class ["optimizer-risk-balance-row" "optimizer-risk-balance-backdrop"
                 "pointer-events-none"]}
   [:span]
   (into [:div {:class ["relative" "h-full"]}]
         (concat
          (map (fn [tick]
                 [:div {:class ["optimizer-risk-balance-gridline"]
                        :style {:left (str (x tick) "%")}}])
               (remove zero? (tick-values scale)))
          [[:div {:class ["optimizer-risk-balance-zeroline"]
                  :style {:left (str (x 0) "%")}}]]
          (when (number? target-share)
            [[:div {:class ["optimizer-risk-balance-targetline"]
                    :style {:left (str (x target-share) "%")}}]])))
   [:span]
   [:span]])

(defn- contribution-lane
  "The row's plot area: dashed current↔recommended connector, the
  recommended-share bar from zero (sign-colored), the purple per-row target
  tick, the gray current circle, and the green recommended dot. A current
  share beyond the fitted domain (see lane-scale) draws as a gray chevron
  pinned to the plot edge — honest 'off the chart' instead of a circle that
  pretends the value sits at the edge — with the true value in the tooltip."
  [{:keys [x lo hi]} {:keys [label share current-share target-share negative?]}]
  (let [x0 (x 0)
        xs (when (number? share) (x share))
        off-direction (when (number? current-share)
                        (cond
                          (> current-share hi) "right"
                          (< current-share lo) "left"
                          :else nil))
        xc (when (and (number? current-share) (nil? off-direction))
             (x current-share))
        connector-to (or xc (case off-direction "right" 100 "left" 0 nil))]
    [:div {:class ["optimizer-risk-balance-lane" "relative" "min-w-0"]}
     (when (and xs connector-to)
       [:div {:class ["optimizer-risk-balance-connector"]
              :style {:left (str (min xs connector-to) "%")
                      :width (str (js/Math.abs (- xs connector-to)) "%")}}])
     (when xs
       [:div {:class ["optimizer-risk-balance-bar"]
              :data-role "portfolio-optimizer-risk-contribution-bar"
              :data-sign (if negative? "negative" "positive")
              :style {:left (str (min x0 xs) "%")
                      :width (str (max 0.4 (js/Math.abs (- xs x0))) "%")}}])
     (when (number? target-share)
       [:div {:class ["optimizer-risk-balance-tick"]
              :data-role "portfolio-optimizer-risk-contribution-target-tick"
              :style {:left (str (x target-share) "%")}}])
     (when xc
       [:div {:class ["optimizer-risk-balance-current" "optimizer-risk-balance-marker"]
              :data-role "portfolio-optimizer-risk-contribution-current"
              :style {:left (str xc "%")}}])
     (when off-direction
       [:div {:class ["optimizer-risk-balance-current-offscale"]
              :data-role "portfolio-optimizer-risk-contribution-current"
              :data-offscale off-direction
              :title (str label " · current " (format-pct current-share)
                          " — beyond the chart scale")}])
     (when xs
       [:div {:class ["optimizer-risk-balance-dot" "optimizer-risk-balance-marker"]
              :data-role "portfolio-optimizer-risk-contribution-recommended"
              :style {:left (str xs "%")}}])]))

(defn- deviation-sign
  [deviation-pts]
  (cond
    (not (number? deviation-pts)) nil
    (< (js/Math.abs deviation-pts) 0.05) "zero"
    (pos? deviation-pts) "positive"
    :else "negative"))

(defn- contribution-row
  [scale {:keys [instrument-id label share current-share target-share
                 deviation-pts] :as row}]
  [:div {:class ["optimizer-risk-balance-row"]
         :data-role "portfolio-optimizer-risk-contribution-row"
         :data-instrument-id instrument-id
         :title (str label
                     " · recommended " (format-pct share)
                     (when (number? current-share)
                       (str " · current " (format-pct current-share)))
                     " · target " (format-pct target-share)
                     " · " (equal-risk-results/format-signed-pts deviation-pts)
                     " vs target")}
   [:span {:class ["optimizer-risk-balance-label" "truncate"]} label]
   (contribution-lane scale row)
   [:span {:class ["optimizer-risk-balance-target-cell" "font-mono" "tabular-nums"]
           :data-role "portfolio-optimizer-risk-contribution-target"}
    (format-pct target-share)]
   [:span {:class ["optimizer-risk-balance-deviation-cell" "font-mono" "tabular-nums"]
           :data-role "portfolio-optimizer-risk-contribution-deviation"
           :data-sign (deviation-sign deviation-pts)}
    (equal-risk-results/format-signed-pts deviation-pts)]])

(defn- axis-rows
  [{:keys [x] :as scale}]
  [:div
   [:div {:class ["optimizer-risk-balance-row"]}
    [:span]
    (into [:div {:class ["optimizer-risk-balance-axis" "relative"]}]
          (map (fn [tick]
                 [:span {:class ["optimizer-risk-balance-axis-label" "absolute"
                                 "-translate-x-1/2" "font-mono"]
                         :style {:left (str (x tick) "%")}}
                  (format-pct tick 0)])
               (tick-values scale)))
    [:span] [:span]]
   [:div {:class ["optimizer-risk-balance-row"]}
    [:span]
    [:p {:class ["optimizer-risk-balance-axis-title"]}
     "Contribution to Total Volatility (%)"]
    [:span] [:span]]])

;; --- Footnotes ----------------------------------------------------------------

(defn- reading-note
  [{:keys [target-share negative-count]}]
  [:p {:class ["optimizer-risk-balance-reading"]
       :data-role "portfolio-optimizer-risk-contributions-reading"}
   [:span {:class ["optimizer-risk-balance-reading-label"]} "Reading this"]
   [:span {:class ["optimizer-risk-balance-reading-sep"]} "·"]
   (str "Bars show recommended contribution. Gray circle is current. Purple "
        "line marks equal target (" (format-pct target-share) " per asset).")
   (when (and (number? negative-count) (pos? negative-count))
     (str " " negative-count
          (if (= 1 negative-count)
            " position hedges the book (negative risk contribution)."
            " positions hedge the book (negative risk contributions).")))])

(defn- overflow-line
  [{:keys [hidden-count hidden-max-pts]}]
  (when (pos? (or hidden-count 0))
    [:p {:class ["mt-2" "text-xs" "text-trading-muted"]
         :data-role "portfolio-optimizer-risk-contributions-overflow"}
     (str "+ " hidden-count " more within ±"
          (.toFixed (or hidden-max-pts 0) 1)
          " pts of target (the largest deviations are shown).")]))

(defn- exposure-line
  [{:keys [gross-exposure net-exposure long-exposure short-exposure]}]
  (when (number? gross-exposure)
    [:p {:class ["mt-2" "font-mono" "text-xs" "text-trading-muted"]
         :data-role "portfolio-optimizer-risk-contributions-exposure"}
     (str "Realized · gross " (format-pct gross-exposure 0)
          " · net " (format-pct net-exposure 0)
          " · long " (format-pct long-exposure 0)
          " · short " (format-pct short-exposure 0))]))

(defn- solver-footer
  [result]
  (let [solver (:equal-risk-solver result)]
    (when (some? (:converged? solver))
      [:p {:class ["mt-1" "font-mono" "text-[0.6875rem]" "text-trading-muted/70"]
           :data-role "portfolio-optimizer-risk-contributions-solver"}
       (str (if (:converged? solver) "Converged" "Not converged")
            " · " (:iterations solver) " iterations"
            (when-let [start (:selected-initialization solver)]
              (str " · " (name start) " start")))])))

;; --- Tabs ---------------------------------------------------------------------

(defn- tab-label
  "A tab is a label wrapping its own visually-hidden radio: checked state
  lives in the DOM (never controlled by the render), and the scoped :has()
  CSS in optimizer/results.css does the active styling + panel switching."
  [radio-name view text]
  [:label {:class ["optimizer-risk-balance-tab"]
           :data-role (str "portfolio-optimizer-risk-view-tab-" view)}
   [:input {:type "radio"
            :name radio-name
            :class ["sr-only"]
            :data-risk-view view}]
   text])

;; --- Card ---------------------------------------------------------------------

(defn risk-contributions-card
  "Renders nil unless the result carries the :risk-contributions section
  (present only on :equal-risk runs). Degrades gracefully on persisted
  pre-redesign results: no current markers/connectors, no solver footer, and
  the RISK / RETURN tab disappears when no portfolio points exist."
  [result]
  (when-let [model (equal-risk-results/balance-model result)]
    (let [{:keys [rows target-share current]} model
          scale (lane-scale model)
          risk-return-panel (risk-return-context/risk-return-panel result)
          radio-name (str "optimizer-risk-view-" (or (:as-of-ms result) "result"))]
      [:section {:class ["optimizer-risk-balance" "rounded-xl" "border"
                         "border-base-300"]
                 :data-role "portfolio-optimizer-risk-contributions"}
       [:div {:class ["optimizer-risk-balance-header"]}
        [:div {:class ["min-w-0"]}
         [:p {:class ["optimizer-risk-balance-title"]}
          "Risk contribution balance"]
         [:p {:class ["optimizer-risk-balance-subtitle"]}
          "Signed Euler contribution to total portfolio volatility"]]
        (when risk-return-panel
          [:div {:class ["optimizer-risk-balance-tabs"]
                 :data-role "portfolio-optimizer-risk-view-tabs"}
           (tab-label radio-name "contribution" "Risk contribution")
           (tab-label radio-name "risk-return" "Risk / Return")])]
       [:div {:class ["optimizer-risk-balance-panel"
                      "optimizer-risk-balance-panel--contribution"]}
        (kpi-strip model)
        [:div {:class ["optimizer-risk-balance-plot-frame"]
               :data-role "portfolio-optimizer-risk-contribution-chart"}
         (legend-row target-share (some? current))
         [:div {:class ["optimizer-risk-balance-rows" "relative"]}
          (plot-backdrop scale target-share)
          (into [:div {:class ["relative" "space-y-1"]}]
                (map (partial contribution-row scale))
                rows)]
         (axis-rows scale)]
        (reading-note model)
        (overflow-line model)
        (exposure-line (:diagnostics result))
        (solver-footer result)]
       (when risk-return-panel
         [:div {:class ["optimizer-risk-balance-panel"
                        "optimizer-risk-balance-panel--risk-return"]}
          risk-return-panel])])))
