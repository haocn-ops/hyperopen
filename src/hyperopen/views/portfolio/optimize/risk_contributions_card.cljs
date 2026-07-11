(ns hyperopen.views.portfolio.optimize.risk-contributions-card
  "Equal Risk results card: each position's SIGNED share of portfolio
  volatility against the uniform 1/n target, with a truthful exact /
  approximate / not-converged badge and the realized exposure line. Replaces
  the efficient-frontier chart for :equal-risk runs — that objective produces
  one selected portfolio, not a frontier, and fabricating a curve is
  forbidden. Pure rendering over the payload's :risk-contributions /
  :equal-risk-solver / :diagnostics sections; no optimizer math here.")

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
  (let [{:keys [label] :as copy} (get quality-copy quality)]
    (when copy
      [:span {:class (into ["inline-flex" "items-center" "border" "px-1.5" "py-0.5"
                            "font-mono" "text-[0.625rem]" "font-semibold" "uppercase"
                            "tracking-[0.08em]"]
                           (:class copy))
              :data-role "portfolio-optimizer-risk-contributions-quality"}
       label])))

(defn- contribution-row
  [labels weights-by-instrument target-share
   {:keys [instrument-id relative-contribution]}]
  (let [label (or (get labels instrument-id) instrument-id)
        weight (get weights-by-instrument instrument-id)
        negative? (and (number? relative-contribution)
                       (neg? relative-contribution))]
    [:tr {:data-role "portfolio-optimizer-risk-contribution-row"
          :data-instrument-id instrument-id}
     [:td {:class ["py-1" "pr-2" "text-left" "text-[0.75rem]" "text-trading-text"]}
      label]
     [:td {:class ["py-1" "pr-2" "text-right" "font-mono" "text-[0.75rem]"
                   "text-trading-muted"]}
      (format-pct weight)]
     [:td {:class (cond-> ["py-1" "pr-2" "text-right" "font-mono" "text-[0.75rem]"]
                    negative? (conj "text-error")
                    (not negative?) (conj "text-trading-text"))}
      (format-pct relative-contribution)]
     [:td {:class ["py-1" "text-right" "font-mono" "text-[0.75rem]"
                   "text-trading-muted/80"]}
      (format-pct target-share)]]))

(defn- exposure-line
  [{:keys [gross-exposure net-exposure long-exposure short-exposure]}]
  (when (number? gross-exposure)
    [:p {:class ["mt-2" "font-mono" "text-[0.6875rem]" "text-trading-muted"]
         :data-role "portfolio-optimizer-risk-contributions-exposure"}
     (str "Realized · gross " (format-pct gross-exposure 0)
          " · net " (format-pct net-exposure 0)
          " · long " (format-pct long-exposure 0)
          " · short " (format-pct short-exposure 0))]))

(defn risk-contributions-card
  "Renders nil unless the result carries the :risk-contributions section
  (present only on :equal-risk runs)."
  [result]
  (when-let [contributions (:risk-contributions result)]
    (let [{:keys [instrument-ids relative-contributions
                  target-relative-contributions quality rms-error
                  max-absolute-error negative-contribution-count]} contributions
          labels (:labels-by-instrument result)
          weights (:target-weights-by-instrument result)
          target-share (first target-relative-contributions)
          rows (->> (map (fn [instrument-id relative-contribution]
                           {:instrument-id instrument-id
                            :relative-contribution relative-contribution})
                         instrument-ids
                         relative-contributions)
                    (sort-by :relative-contribution >))
          quality-note (get-in quality-copy [quality :note])]
      [:section {:class ["optimizer-risk-contributions" "rounded-xl" "border"
                         "border-base-300" "bg-base-100/95" "p-4"]
                 :data-role "portfolio-optimizer-risk-contributions"}
       [:div {:class ["flex" "items-center" "justify-between" "gap-2"]}
        [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]"
                     "text-trading-muted/70"]}
         "Risk contributions"]
        (quality-badge quality)]
       [:p {:class ["mt-1.5" "text-[0.75rem]" "leading-[1.4]" "text-trading-muted"]}
        (str "Each position's signed share of portfolio volatility vs the equal "
             (format-pct target-share) " target. Sized by the risk model only — "
             "return forecasts never move these weights.")]
       (when quality-note
         [:p {:class ["mt-1" "text-[0.6875rem]" "leading-[1.4]" "text-trading-muted/80"]
              :data-role "portfolio-optimizer-risk-contributions-quality-note"}
          quality-note])
       [:table {:class ["mt-3" "w-full" "border-collapse"]}
        [:thead
         [:tr {:class ["border-b" "border-base-300"]}
          [:th {:class ["py-1" "pr-2" "text-left" "font-mono" "text-[0.625rem]"
                        "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
           "Asset"]
          [:th {:class ["py-1" "pr-2" "text-right" "font-mono" "text-[0.625rem]"
                        "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
           "Weight"]
          [:th {:class ["py-1" "pr-2" "text-right" "font-mono" "text-[0.625rem]"
                        "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
           "Risk share"]
          [:th {:class ["py-1" "text-right" "font-mono" "text-[0.625rem]"
                        "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
           "Target"]]]
        (into [:tbody]
              (map (partial contribution-row labels weights target-share))
              rows)]
       [:p {:class ["mt-2" "font-mono" "text-[0.6875rem]" "text-trading-muted"]
            :data-role "portfolio-optimizer-risk-contributions-error"}
        (str "Balance error · rms " (format-pct rms-error 2)
             " · max " (format-pct max-absolute-error 2))]
       (when (and (number? negative-contribution-count)
                  (pos? negative-contribution-count))
         [:p {:class ["mt-1" "text-[0.6875rem]" "text-trading-muted"]
              :data-role "portfolio-optimizer-risk-contributions-negative-note"}
          (str negative-contribution-count
               (if (= 1 negative-contribution-count)
                 " position hedges the book (negative risk contribution)."
                 " positions hedge the book (negative risk contributions)."))])
       (exposure-line (:diagnostics result))])))
