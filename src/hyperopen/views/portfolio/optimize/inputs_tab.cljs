(ns hyperopen.views.portfolio.optimize.inputs-tab
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- audit-card
  [data-role title & children]
  [:section {:class ["rounded-lg" "border" "border-base-300" "bg-base-200/40" "p-3"]
             :data-role data-role}
   [:p {:class ["text-xs" "font-semibold" "uppercase" "tracking-[0.18em]" "text-trading-muted"]}
    title]
   (into [:div {:class ["mt-3"]}]
         children)])

(defn- universe-audit
  [{:keys [universe-rows]}]
  (audit-card
   "portfolio-optimizer-inputs-universe"
   "Universe"
   [:p {:class ["font-semibold" "tabular-nums"]} (str (count universe-rows) " instruments")]
   (into [:div {:class ["mt-3" "space-y-1"]}]
        (map (fn [instrument]
                [:p {:class ["rounded-md" "border" "border-base-300" "bg-base-100/70" "px-2" "py-1"
                             "text-xs" "font-semibold" "tabular-nums"]}
                 (:audit-label instrument)])
              universe-rows))))

(defn- view-summary
  [view]
  (case (:kind view)
    :relative
    (str (:primary-label view)
         " "
         (if (= :underperform (:direction view)) "<" ">")
         " "
         (:comparator-label view)
         " by "
         (opt-format/format-pct (:return view)
                                {:minimum-fraction-digits 0
                                 :maximum-fraction-digits 2}))
    (str (:primary-label view)
         " expected return "
         (opt-format/format-pct (:return view)
                                {:minimum-fraction-digits 0
                                 :maximum-fraction-digits 2}))))

(defn- models-audit
  [{:keys [objective-kind return-model-kind risk-model-kind view-rows]}]
  (audit-card
   "portfolio-optimizer-inputs-models"
   "Model Stack"
   [:div {:class ["grid" "grid-cols-1" "gap-2" "sm:grid-cols-3"]}
    [:div [:span {:class ["text-xs" "text-trading-muted"]} "Objective"] [:p (opt-format/keyword-label objective-kind)]]
    [:div [:span {:class ["text-xs" "text-trading-muted"]} "Return Model"] [:p (opt-format/keyword-label return-model-kind)]]
    [:div [:span {:class ["text-xs" "text-trading-muted"]} "Risk Model"] [:p (opt-format/keyword-label risk-model-kind)]]]
   [:div {:class ["mt-3" "space-y-1" "text-xs" "text-trading-muted"]}
    [:p (str "Black-Litterman views: " (count view-rows))]
    (if (seq view-rows)
      (into [:div {:class ["space-y-1"]
                   :data-role "portfolio-optimizer-inputs-black-litterman-views"}]
            (map (fn [view]
                   [:p {:class ["rounded-md" "border" "border-base-300" "bg-base-100/70"
                                "px-2" "py-1"]}
                    (view-summary view)])
                 view-rows))
      [:p "Views adjust expected returns only; covariance/risk inputs are unchanged."])]))

(defn- constraints-audit
  [constraints]
  (audit-card
   "portfolio-optimizer-inputs-constraints"
   "Constraints"
   [:div {:class ["grid" "grid-cols-2" "gap-2" "text-xs"]}
    [:p (str "Long-only: " (if (:long-only? constraints) "yes" "no"))]
    [:p (str "Max asset: " (opt-format/format-pct (:max-asset-weight constraints)))]
    [:p (str "Gross: " (opt-format/format-decimal (:gross-max constraints)))]
    [:p (str "Turnover: " (opt-format/format-pct (:max-turnover constraints)))]
    [:p (str "Rebalance tolerance: " (opt-format/format-pct (:rebalance-tolerance constraints)))]
    [:p (str "Dust: " (or (:dust-usdc constraints) "N/A"))]]))

(defn- history-assumption-mode-label
  [mode]
  (case mode
    :conservative "Conservative"
    :proxy "Proxy behavior"
    "--"))

(defn- history-assumption-summary
  [{:keys [mode proxy-labels relationship-strength volatility max-weight]}]
  (case mode
    :conservative (str "conservative · " (opt-format/format-pct volatility) " vol · "
                       (opt-format/format-pct max-weight) " cap")
    :proxy (str "proxy basket "
                (if (seq proxy-labels) (str/join ", " proxy-labels) "--")
                (when relationship-strength
                  (str " · " (name relationship-strength) " relationship"))
                " · " (opt-format/format-pct volatility) " vol · "
                (opt-format/format-pct max-weight) " cap")
    "--"))

(defn- basket-text
  [rows]
  (str/join " / "
            (map (fn [{:keys [label weight]}]
                   (str label " "
                        (opt-format/format-pct weight
                                               {:minimum-fraction-digits 0
                                                :maximum-fraction-digits 0})))
                 rows)))

(def ^:private prior-source-text
  {:user "user-specified weights"
   :equal "equal-weight fallback"})

(defn- proxy-model-disclosure
  "What the solved run modeled for this asset: prior (with source), regression
  estimate when one ran, confidence, and the final basket that drove the
  covariance row."
  [row]
  (when-let [model (:model row)]
    [:div {:class ["mt-1" "space-y-0.5" "text-[0.6875rem]" "text-trading-muted"]
           :data-role (str "portfolio-optimizer-inputs-proxy-model-"
                           (:instrument-id row))}
     [:p (str "Prior: " (basket-text (:prior-rows model))
              (when-let [source (get prior-source-text (:prior-source model))]
                (str " · " source)))]
     (if (seq (:regression-rows model))
       [:p (str "Regression estimate: " (basket-text (:regression-rows model))
                " · R² " (opt-format/format-decimal (:r2 model)
                                                    {:maximum-fraction-digits 2})
                " · " (:sample-count model) " observations")]
       [:p "Regression: not enough overlap — prior only."])
     [:p {:class ["font-semibold" "text-trading-text"]}
      (str "Final modeled basket: " (basket-text (:final-rows model))
           " · confidence q "
           (opt-format/format-pct (:confidence-q model)
                                  {:minimum-fraction-digits 0
                                   :maximum-fraction-digits 0}))]
     (when (:effective-modeled-volatility model)
       [:p (str "Effective modeled volatility: "
                (opt-format/format-pct (:effective-modeled-volatility model)
                                       {:minimum-fraction-digits 0
                                        :maximum-fraction-digits 0}))])]))

(defn- history-assumptions-audit
  [{:keys [history-assumption-rows]}]
  (when (seq history-assumption-rows)
    (audit-card
     "portfolio-optimizer-inputs-history-assumptions"
     "History Assumptions Used"
     (into [:div {:class ["space-y-1"]}]
           (map (fn [row]
                  [:div {:class ["rounded-md" "border" "border-base-300" "bg-base-100/70"
                                 "px-2" "py-1" "text-xs"]
                         :data-role (str "portfolio-optimizer-inputs-history-assumption-"
                                         (:instrument-id row))}
                   [:p {:class ["font-semibold" "tabular-nums"]}
                    (str (:label row) " — " (history-assumption-mode-label (:mode row)))]
                   [:p {:class ["mt-0.5" "text-trading-muted"]}
                    (str "Return " (opt-format/format-pct (:expected-return row))
                         " · " (history-assumption-summary row))]
                   (proxy-model-disclosure row)])
                history-assumption-rows))
     (when (some #(= :proxy (:mode %)) history-assumption-rows)
       [:p {:class ["mt-2" "text-[0.6875rem]" "text-trading-muted"]
            :data-role "portfolio-optimizer-inputs-history-assumption-proxy-note"}
        "Proxy assumptions were used to synthesize covariance for assets without reliable native history. Proxy weights are regression-adjusted and confidence-shrunk — R² is never used as a direct weight."]))))

(defn- execution-audit
  [execution-assumptions]
  (audit-card
   "portfolio-optimizer-inputs-execution-assumptions"
   "Execution Assumptions"
   [:div {:class ["grid" "grid-cols-2" "gap-2" "text-xs"]}
    [:p (str "Manual capital: " (opt-format/format-usdc (:manual-capital-usdc execution-assumptions)
                                                        {:maximum-fraction-digits 0}))]
    [:p (str "Fallback slippage: " (or (:fallback-slippage-bps execution-assumptions) "N/A") " bps")]
    [:p (str "Default order: " (opt-format/keyword-label (:default-order-type execution-assumptions)))]
    [:p (str "Fee mode: " (opt-format/keyword-label (:fee-mode execution-assumptions)))]]))

(defn inputs-tab
  [state]
  (let [{:keys [scenario-id constraints execution-assumptions] :as model}
        (optimizer-view-model/inputs-audit-model state)]
    [:section {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4"]
               :data-role "portfolio-optimizer-inputs-tab"}
     [:div {:class ["flex" "flex-wrap" "items-start" "justify-between" "gap-3"]}
      [:div
       [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.24em]" "text-trading-muted"]}
        "Inputs"]
       [:p {:class ["mt-2" "text-sm" "text-trading-muted"]}
        "Read-only scenario input audit. Duplicate the scenario before editing inputs."]]
      (when scenario-id
        [:button {:type "button"
                  :class ["rounded-lg" "border" "border-base-300" "bg-base-200/50" "px-3" "py-2"
                          "text-xs" "font-semibold" "text-trading-text"]
                  :data-role "portfolio-optimizer-inputs-duplicate"
                  :on {:click [[:actions/duplicate-portfolio-optimizer-scenario scenario-id]]}}
         "Duplicate to Edit"])]
     [:div {:class ["mt-4" "grid" "grid-cols-1" "gap-3" "lg:grid-cols-2"]
            :data-role "portfolio-optimizer-inputs-audit-grid"}
      (universe-audit model)
      (models-audit model)
      (constraints-audit constraints)
      (execution-audit execution-assumptions)
      (history-assumptions-audit model)]]))
