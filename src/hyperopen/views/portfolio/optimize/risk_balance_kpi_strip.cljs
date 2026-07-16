(ns hyperopen.views.portfolio.optimize.risk-balance-kpi-strip
  "The Equal Risk card's five-cell KPI strip, split from
  risk-contributions-card when the mode-dependent fit cells pushed the card
  past the namespace-size cap. One strip renders per tab panel (an optional
  View cell labels the correlation/breakdown panels) so the tabs can never
  drift apart.

  The two middle cells follow the balance model's :display-mode. In
  :deviation mode they grade the recommended book's fit (RMS / Max
  deviation) — the numbers that explain an Approximate verdict. In :shift
  mode (an :exact fit with current shares) those are dead zeros by
  definition, so the cells answer the useful questions instead: how
  unbalanced the CURRENT book is (and that the rebalance flattens it), and
  which position's risk moves most."
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
           :note "Risk contributions are balanced within tolerance."}
   :approximate {:label "Approximate"
                 :note "Best solution found under the selected constraints — exposure targets and limits take priority over exact parity."}
   :not-converged {:label "Not converged"
                   :note "The solver stopped at its iteration limit; this is the best feasible portfolio found."}})

(defn shift-mode?
  [model]
  (= :shift (:display-mode model)))

(defn- kpi-cell
  [data-role label value {:keys [value-class tone title]}]
  [:div {:class ["optimizer-risk-balance-kpi"]
         :data-role data-role
         :data-tone (some-> tone name)
         :title title}
   [:p {:class ["optimizer-risk-balance-kpi-label"]} label]
   [:p {:class (into ["optimizer-risk-balance-kpi-value" "font-mono" "tabular-nums"]
                     (when value-class [value-class]))
        :data-tone (some-> tone name)}
    value]])

(defn- format-pts-arrow
  "\"2.3 → 0.0 pts\" — the scenario KPI strip's current→target arrow idiom."
  [from-pts to-pts]
  (if (and (number? from-pts) (js/isFinite from-pts)
           (number? to-pts) (js/isFinite to-pts))
    (str (.toFixed from-pts 1) " → " (.toFixed to-pts 1) " pts")
    "—"))

(defn- fit-cells
  "The two middle KPI cells (see the namespace docstring for the mode
  semantics)."
  [{:keys [target-share rms-pts max-pts current] :as model}]
  (let [target-pts (when (number? target-share) (* 100 target-share))]
    (if (and (shift-mode? model) current)
      [(kpi-cell "portfolio-optimizer-risk-contributions-imbalance"
                 "Current imbalance"
                 (format-pts-arrow (:rms-pts current) rms-pts)
                 {:tone (equal-risk-results/deviation-tone
                         (:rms-pts current) target-pts)
                  :title (str "Root-mean-square gap between each position's "
                              "risk contribution and the equal target: your "
                              "book today → after this rebalance.")})
       (let [{:keys [label shift-pts]} (:biggest-shift current)]
         (kpi-cell "portfolio-optimizer-risk-contributions-biggest-shift"
                   "Biggest shift"
                   (if label
                     (str label " "
                          (equal-risk-results/format-signed-pts shift-pts))
                     "—")
                   {:tone :neutral
                    :title "The position whose share of portfolio risk changes most if you execute this rebalance."}))]
      [(kpi-cell "portfolio-optimizer-risk-contributions-rms"
                 "RMS deviation"
                 (equal-risk-results/format-pts rms-pts)
                 {:tone (equal-risk-results/deviation-tone rms-pts target-pts)})
       (kpi-cell "portfolio-optimizer-risk-contributions-max"
                 "Max deviation"
                 (equal-risk-results/format-pts max-pts)
                 {:tone (equal-risk-results/deviation-tone max-pts target-pts)})])))

(defn kpi-strip
  "The five shared cells, plus an optional per-tab View cell so the
  correlation/breakdown panels can label what they show without the strips
  drifting apart. The two fit cells are mode-dependent (see fit-cells)."
  ([model] (kpi-strip model nil))
  ([{:keys [target-share asset-count quality negative-count] :as model}
    view-text]
   (let [quality* (get quality-copy quality)
         [fit-a fit-b] (fit-cells model)]
     (cond-> [:div {:class ["optimizer-risk-balance-kpis"]
                    :data-role "portfolio-optimizer-risk-balance-kpis"}
              (kpi-cell "portfolio-optimizer-risk-contributions-target"
                        "Equal target"
                        (str (format-pct target-share) " per asset")
                        {:value-class "optimizer-risk-balance-kpi-value--target"
                         :title (when (and (number? asset-count)
                                           (pos? asset-count))
                                  (str "Every held asset owns the same slice "
                                       "of portfolio risk: 1 ÷ " asset-count
                                       " assets = "
                                       (format-pct target-share) "."))})
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
              fit-a
              fit-b
              (kpi-cell "portfolio-optimizer-risk-contributions-negative"
                        "Negative contributors"
                        (str (or negative-count 0))
                        {:tone :neutral})]
       view-text (conj (kpi-cell "portfolio-optimizer-risk-contributions-view"
                                 "View"
                                 view-text
                                 {}))))))
