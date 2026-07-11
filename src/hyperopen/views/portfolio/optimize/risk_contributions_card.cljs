(ns hyperopen.views.portfolio.optimize.risk-contributions-card
  "Equal Risk results centerpiece: the RISK CONTRIBUTION BALANCE chart — a
  horizontal diverging chart of each position's SIGNED Euler share of
  portfolio volatility against the equal 1/n target. One row per asset:
  recommended share as the bar (colored by contribution SIGN, never by
  position side), the current share as a muted dot, the dashed equal-target
  line, and the deviation in points. The axis always includes zero: a
  negative share means the position HEDGES the book, and positive shares can
  exceed 100% when another is negative — the signed shares still sum to 100%.
  No pies, donuts, stacked bars, or absolute contributions: those forms all
  lie once a contribution is negative.

  Rows come pre-sorted and capped from the equal-risk-results view-model
  (worst deviations first, honest remainder line); the lanes are plain HTML
  so the zero/target lines stay 1px and markers stay round at any width.
  Replaces the efficient-frontier chart for :equal-risk — that objective is
  not selected from a frontier and must not be plotted as if it were."
  (:require [hyperopen.portfolio.optimizer.application.view-model.equal-risk-results
             :as equal-risk-results]))

(defn- format-pct
  ([value] (format-pct value 1))
  ([value decimals]
   (if (and (number? value) (js/isFinite value))
     (str (.toFixed (* 100 value) decimals) "%")
     "—")))

(def ^:private quality-copy
  {:exact {:label "Exact"
           :note "Risk contributions are balanced within tolerance."
           :class ["border-success/50" "bg-success/10" "text-success"]}
   :approximate {:label "Approximate"
                 :note "Best solution found under the selected constraints — exposure targets and limits take priority over exact parity."
                 :class ["border-warning/60" "bg-warning/10" "text-warning"]}
   :not-converged {:label "Not converged"
                   :note "The solver stopped at its iteration limit; this is the best feasible portfolio found."
                   :class ["border-error/60" "bg-error/10" "text-error"]}})

(defn- quality-badge
  [quality]
  (when-let [copy (get quality-copy quality)]
    [:span {:class (into ["inline-flex" "items-center" "border" "px-1.5" "py-0.5"
                          "font-mono" "text-[0.625rem]" "font-semibold" "uppercase"
                          "tracking-[0.08em]"]
                         (:class copy))
            :data-role "portfolio-optimizer-risk-contributions-quality"}
     (:label copy)]))

;; --- Shared lane scale --------------------------------------------------------

(defn- lane-scale
  "One scale for every lane: covers zero, the target, and every visible
  recommended/current share, padded and rounded to 5% so the domain reads as
  clean ticks. Returns {:lo :hi :x (fraction-of-total -> 0..100 percent)}."
  [{:keys [rows target-share]}]
  (let [values (concat [0 (or target-share 0)]
                       (keep :share rows)
                       (keep :current-share rows))
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
        step (or (first (filter #(<= (/ span %) 6)
                                [0.05 0.1 0.2 0.25 0.5 1 2 5]))
                 5)]
    (->> (range (js/Math.ceil (/ lo step)) (inc (js/Math.floor (/ hi step))))
         (map #(* step %)))))

;; --- Lanes --------------------------------------------------------------------

(defn- lane-line
  [x-pct classes]
  [:div {:class (into ["absolute" "inset-y-0" "w-0"] classes)
         :style {:left (str x-pct "%")}}])

(defn- contribution-lane
  "The row's plot area: zero line, dashed target line, the recommended-share
  bar from zero (sign-colored), and the muted current-share dot."
  [{:keys [x]} target-share {:keys [share current-share negative?]}]
  (let [x0 (x 0)
        xs (when (number? share) (x share))]
    [:div {:class ["relative" "h-4" "min-w-0" "bg-base-200/30"]}
     (lane-line x0 ["border-l" "border-base-300"])
     (when (number? target-share)
       (lane-line (x target-share) ["border-l" "border-dashed" "border-warning/70"]))
     (when xs
       [:div {:class ["absolute" "top-1" "bottom-1"
                      (if negative? "bg-error/70" "bg-success/70")]
              :data-role "portfolio-optimizer-risk-contribution-bar"
              :style {:left (str (min x0 xs) "%")
                      :width (str (max 0.4 (js/Math.abs (- xs x0))) "%")}}])
     (when (number? current-share)
       [:div {:class ["absolute" "top-1/2" "h-1.5" "w-1.5" "-translate-x-1/2"
                      "-translate-y-1/2" "rounded-full" "border" "border-base-300"
                      "bg-base-100"]
              :data-role "portfolio-optimizer-risk-contribution-current"
              :style {:left (str (x current-share) "%")}}])]))

(def ^:private row-grid
  ["grid" "grid-cols-[minmax(0,7.5rem)_minmax(0,1fr)_3.75rem_4.25rem]"
   "items-center" "gap-2"])

(defn- contribution-row
  [scale target-share {:keys [instrument-id label share deviation-pts negative?] :as row}]
  [:div {:class (into ["py-0.5"] row-grid)
         :data-role "portfolio-optimizer-risk-contribution-row"
         :data-instrument-id instrument-id}
   [:span {:class ["truncate" "text-[0.75rem]" "text-trading-text"]} label]
   (contribution-lane scale target-share row)
   [:span {:class ["text-right" "font-mono" "text-[0.75rem]" "tabular-nums"
                   (if negative? "text-error" "text-trading-text")]}
    (format-pct share)]
   [:span {:class ["text-right" "font-mono" "text-[0.6875rem]" "tabular-nums"
                   "text-trading-muted"]}
    (equal-risk-results/format-signed-pts deviation-pts)]])

(defn- tick-lane
  [{:keys [x] :as scale}]
  [:div {:class (into ["mt-1"] row-grid)}
   [:span]
   (into [:div {:class ["relative" "h-4"]}]
         (map (fn [tick]
                [:span {:class ["absolute" "-translate-x-1/2" "font-mono"
                                "text-[0.5625rem]" "text-trading-muted/70"]
                        :style {:left (str (x tick) "%")}}
                 (format-pct tick 0)])
              (tick-values scale)))
   [:span] [:span]])

(defn- column-headers
  []
  [:div {:class (into ["border-b" "border-base-300" "pb-1"] row-grid)}
   [:span {:class ["font-mono" "text-[0.5625rem]" "uppercase"
                   "tracking-[0.08em]" "text-trading-muted/70"]} "Asset"]
   [:span]
   [:span {:class ["text-right" "font-mono" "text-[0.5625rem]" "uppercase"
                   "tracking-[0.08em]" "text-trading-muted/70"]} "Share"]
   [:span {:class ["text-right" "font-mono" "text-[0.5625rem]" "uppercase"
                   "tracking-[0.08em]" "text-trading-muted/70"]} "Deviation"]])

;; --- Summary strip ------------------------------------------------------------

(defn- summary-fact
  [data-role label value]
  [:div {:data-role data-role}
   [:p {:class ["font-mono" "text-[0.5625rem]" "uppercase" "tracking-[0.08em]"
                "text-trading-muted/70"]}
    label]
   [:p {:class ["mt-0.5" "font-mono" "text-[0.75rem]" "font-semibold"
                "tabular-nums" "text-trading-text"]}
    value]])

(defn- summary-strip
  [{:keys [target-share rms-pts max-pts negative-count]}]
  [:div {:class ["mt-3" "grid" "grid-cols-2" "gap-2" "border" "border-base-300"
                 "bg-base-200/20" "p-2.5" "sm:grid-cols-4"]}
   (summary-fact "portfolio-optimizer-risk-contributions-target"
                 "Equal target" (str (format-pct target-share) " per asset"))
   (summary-fact "portfolio-optimizer-risk-contributions-rms"
                 "RMS deviation" (equal-risk-results/format-pts rms-pts))
   (summary-fact "portfolio-optimizer-risk-contributions-max"
                 "Max deviation" (equal-risk-results/format-pts max-pts))
   (summary-fact "portfolio-optimizer-risk-contributions-negative"
                 "Negative contributors" (str (or negative-count 0)))])

(defn- exposure-line
  [{:keys [gross-exposure net-exposure long-exposure short-exposure]}]
  (when (number? gross-exposure)
    [:p {:class ["mt-2" "font-mono" "text-[0.6875rem]" "text-trading-muted"]
         :data-role "portfolio-optimizer-risk-contributions-exposure"}
     (str "Realized · gross " (format-pct gross-exposure 0)
          " · net " (format-pct net-exposure 0)
          " · long " (format-pct long-exposure 0)
          " · short " (format-pct short-exposure 0))]))

(defn- solver-footer
  [result]
  (let [solver (:equal-risk-solver result)]
    (when (some? (:converged? solver))
      [:p {:class ["mt-1" "font-mono" "text-[0.625rem]" "text-trading-muted/70"]
           :data-role "portfolio-optimizer-risk-contributions-solver"}
       (str (if (:converged? solver) "Converged" "Not converged")
            " · " (:iterations solver) " iterations"
            (when-let [start (:selected-initialization solver)]
              (str " · " (name start) " start")))])))

;; --- Card ---------------------------------------------------------------------

(defn risk-contributions-card
  "Renders nil unless the result carries the :risk-contributions section
  (present only on :equal-risk runs). Degrades gracefully on persisted
  pre-redesign results: no current markers, no solver footer."
  [result]
  (when-let [model (equal-risk-results/balance-model result)]
    (let [{:keys [rows hidden-count hidden-max-pts target-share quality
                  negative-count current]} model
          scale (lane-scale model)
          quality-note (get-in quality-copy [quality :note])]
      [:section {:class ["optimizer-risk-contributions" "rounded-xl" "border"
                         "border-base-300" "bg-base-100/95" "p-4"]
                 :data-role "portfolio-optimizer-risk-contributions"}
       [:div {:class ["flex" "items-center" "justify-between" "gap-2"]}
        [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]"
                     "text-trading-muted/70"]}
         "Risk contribution balance"]
        (quality-badge quality)]
       [:p {:class ["mt-1.5" "text-[0.75rem]" "leading-[1.4]" "text-trading-muted"]}
        "Signed Euler contribution to total portfolio volatility. Sized by the risk model only — return forecasts never move these weights."]
       (when quality-note
         [:p {:class ["mt-1" "text-[0.6875rem]" "leading-[1.4]" "text-trading-muted/80"]
              :data-role "portfolio-optimizer-risk-contributions-quality-note"}
          quality-note])
       (summary-strip model)
       [:div {:class ["mt-3"]
              :data-role "portfolio-optimizer-risk-contribution-chart"}
        (column-headers)
        (into [:div {:class ["mt-1" "space-y-0.5"]}]
              (map (partial contribution-row scale target-share))
              rows)
        (tick-lane scale)
        [:p {:class ["mt-1" "text-[0.625rem]" "text-trading-muted/70"]}
         (str "■ Recommended (green adds risk, red hedges) · "
              (when current "● Current · ")
              "┄ Target (" (format-pct target-share) " per asset). "
              "A negative share means the position reduces total volatility.")]]
       (when (pos? (or hidden-count 0))
         [:p {:class ["mt-2" "text-[0.6875rem]" "text-trading-muted"]
              :data-role "portfolio-optimizer-risk-contributions-overflow"}
          (str "+ " hidden-count " more within ±"
               (.toFixed (or hidden-max-pts 0) 1)
               " pts of target (rows are sorted by deviation).")])
       (when (and (number? negative-count) (pos? negative-count))
         [:p {:class ["mt-2" "text-[0.6875rem]" "text-trading-muted"]
              :data-role "portfolio-optimizer-risk-contributions-negative-note"}
          (str negative-count
               (if (= 1 negative-count)
                 " position hedges the book (negative risk contribution)."
                 " positions hedge the book (negative risk contributions)."))])
       (exposure-line (:diagnostics result))
       (solver-footer result)])))
