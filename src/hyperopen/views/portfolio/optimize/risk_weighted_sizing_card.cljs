(ns hyperopen.views.portfolio.optimize.risk-weighted-sizing-card
  "Risk-weighted sizing results centerpiece: the SIZING FIDELITY card — one
  row per asset showing |weight| × σ as a bar against the shared equal ideal,
  proving the objective's promise (every free asset carries the same
  standalone risk; a row a cap, lock, or turnover budget moved off its 1/σ
  seed wears a \"capped\" tag instead of silently breaking the pattern).
  Replaces the efficient-frontier chart for :inverse-volatility — the
  objective is deterministic (closed-form seed + one projection), not
  selected from a frontier, and must not be plotted as if it were. Reuses the
  risk-balance card's visual grammar (header, row grid, reading note) so the
  two covariance-only centerpieces read as siblings."
  (:require [hyperopen.portfolio.optimizer.application.view-model.inverse-volatility-results
             :as inverse-volatility-results]))

(defn- format-pct
  ;; Deliberately local, not optimize.format/format-pct: this card takes a
  ;; positional decimals arg (that helper takes an options map, defaults to 2
  ;; digits, and falls back to "N/A" where every placeholder here is "—").
  ([value] (format-pct value 1))
  ([value decimals]
   (if (and (number? value) (js/isFinite value))
     (str (.toFixed (* 100 value) decimals) "%")
     "—")))

(defn- sizing-lane
  "The row's plot area: the |w|·σ bar from zero, over a shared dashed ideal
  line drawn per-lane (the rows are plain HTML, so each lane carries its own
  1px marker at the same x — they read as one continuous line)."
  [x {:keys [label risk-weight moved-off-seed?]} ideal]
  [:div {:class ["relative" "h-3" "min-w-0" "bg-base-200/30"]}
   (when (number? risk-weight)
     [:div {:class ["absolute" "inset-y-0" "left-0"
                    (if moved-off-seed? "bg-warning/60" "bg-primary/50")]
            :data-role "portfolio-optimizer-sizing-bar"
            :data-moved (str (boolean moved-off-seed?))
            :title (str label " · |w|·σ " (format-pct risk-weight))
            :style {:width (str (max 0.4 (x risk-weight)) "%")}}])
   (when (number? ideal)
     [:div {:class ["absolute" "inset-y-0" "w-px" "bg-warning"]
            :data-role "portfolio-optimizer-sizing-ideal-line"
            :style {:left (str (x ideal) "%")}}])])

(defn- sizing-row
  [x ideal {:keys [instrument-id label weight sigma risk-weight short?
                   moved-off-seed? turnover-limited?] :as row}]
  [:div {:class ["optimizer-risk-balance-row"]
         :data-role "portfolio-optimizer-sizing-row"
         :data-instrument-id instrument-id
         :title (str label
                     " · " (if short? "short" "long") " " (format-pct weight)
                     " · σ " (format-pct sigma 0)
                     " · |w|·σ " (format-pct risk-weight))}
   [:span {:class ["optimizer-risk-balance-label" "truncate"]} label]
   (sizing-lane x row ideal)
   [:span {:class ["font-mono" "tabular-nums" "text-right" "text-xs"
                   "text-trading-text"]
           :data-role "portfolio-optimizer-sizing-risk-weight"}
    (format-pct risk-weight)]
   [:span {:class ["text-right"]}
    (when moved-off-seed?
      [:span {:class ["border" "border-warning/50" "px-1.5" "py-0.5"
                      "font-mono" "text-[0.5rem]" "font-semibold" "uppercase"
                      "tracking-[0.08em]" "text-warning"]
              :data-role "portfolio-optimizer-sizing-capped-tag"}
       "capped"])
    (when turnover-limited?
      [:span {:class ["border" "border-warning/50" "px-1.5" "py-0.5"
                      "font-mono" "text-[0.5rem]" "font-semibold" "uppercase"
                      "tracking-[0.08em]" "text-warning"]
              :data-role "portfolio-optimizer-sizing-turnover-limited-tag"}
       "turnover-limited"])]])

(defn- legend-row
  [ideal]
  [:div {:class ["optimizer-risk-balance-row" "optimizer-risk-balance-legend-row"]}
   [:span]
   [:div {:class ["optimizer-risk-balance-legend"]}
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-balance-legend-dash"]}]
     (str "Equal ideal (" (format-pct ideal) ")")]
    [:span {:class ["optimizer-risk-balance-legend-item"]}
     [:span {:class ["optimizer-risk-balance-dot"]}]
     "|weight| × σ"]]
   [:span {:class ["optimizer-risk-balance-col-head"]} "|w|·σ"]
   [:span {:class ["optimizer-risk-balance-col-head"]} "Bound"]])

(defn- reading-note
  [{:keys [moved-count]}]
  [:p {:class ["optimizer-risk-balance-reading"]
       :data-role "portfolio-optimizer-risk-weighted-sizing-reading"}
   [:span {:class ["optimizer-risk-balance-reading-label"]} "Reading this"]
   [:span {:class ["optimizer-risk-balance-reading-sep"]} "·"]
   (str "Bars show each asset's standalone risk share (|weight| × its own "
        "volatility) against the equal ideal. Risk-weighted sizing sizes by "
        "standalone volatility only and ignores correlations — that is what "
        "keeps every selected asset in the book.")
   (when (pos? (or moved-count 0))
     (str " " moved-count
          (if (= 1 moved-count)
            " row was moved off its ideal size by a limit (tagged)."
            " rows were moved off their ideal sizes by limits (tagged).")))])

(defn risk-weighted-sizing-card
  "Renders nil unless the result carries the :inverse-volatility section
  (present only on Risk-weighted sizing runs)."
  [result]
  (when-let [model (inverse-volatility-results/sizing-model result)]
    (let [{:keys [rows ideal-risk-weight max-risk-weight
                  max-sizing-deviation]} model
          ;; Fixed headroom past the largest bar so the ideal line never sits
          ;; on the plot edge.
          span (* 1.1 (max 1e-9 (or max-risk-weight 0)))
          x (fn [value] (-> (* 100 (/ value span)) (max 0) (min 100)))]
      [:section {:class ["optimizer-risk-balance" "rounded-xl" "border"
                         "border-base-300"]
                 :data-role "portfolio-optimizer-risk-weighted-sizing-card"}
       [:div {:class ["optimizer-risk-balance-header"]}
        [:div {:class ["min-w-0"]}
         [:p {:class ["optimizer-risk-balance-title"]}
          "Sizing fidelity"]
         [:p {:class ["optimizer-risk-balance-subtitle"]}
          "|weight| × volatility per asset vs the equal ideal"]]
        [:span {:class ["font-mono" "text-[0.62rem]" "uppercase"
                        "tracking-[0.08em]" "text-trading-muted/70"]
                :data-role "portfolio-optimizer-risk-weighted-sizing-status"}
         "Deterministic · closed form + projection"]]
       ;; Plain body div, NOT .optimizer-risk-balance-panel: that class is
       ;; display-gated by the sibling card's tab radios (:has() CSS) and this
       ;; card has no tabs.
       [:div {:class ["px-4" "pb-4"]}
        [:div {:class ["optimizer-risk-balance-plot-frame"]
               :data-role "portfolio-optimizer-risk-weighted-sizing-chart"}
         (legend-row ideal-risk-weight)
         (into [:div {:class ["relative" "space-y-1"]}]
               (map (partial sizing-row x ideal-risk-weight))
               rows)]
        [:p {:class ["mt-2" "font-mono" "text-xs" "text-trading-muted"]
             :data-role "portfolio-optimizer-risk-weighted-sizing-deviation"}
         (str "Max sizing deviation among free assets · "
              (format-pct max-sizing-deviation))]
        (reading-note model)]])))
