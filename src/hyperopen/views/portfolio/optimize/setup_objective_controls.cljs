(ns hyperopen.views.portfolio.optimize.setup-objective-controls
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.target-sigma :as target-sigma]))

(declare objective-card)

(defn- objective-summary
  "Header one-liner for the collapsed Objective panel: the selected objective plus
  what choosing it means, so the panel reads without opening it."
  [objective-kind]
  (case objective-kind
    :max-sharpe "Maximum Sharpe — best risk-adjusted return"
    :target-volatility "Target volatility — max return at a pinned σ"
    :target-return "Target return — lowest risk at a required return"
    "Minimum variance — lowest risk, no return assumption"))

(defn objective-section
  ([draft highlighted-controls]
   (objective-section draft highlighted-controls nil))
  ([draft highlighted-controls sigma-bounds]
   (let [objective-kind (get-in draft [:objective :kind])]
     ;; A disclosure (open by default) rather than a fixed section: after the
     ;; choice is made the header line summarizes it, and the user can collapse
     ;; the 2x2 cards to keep the center column a scannable policy contract.
     (controls/disclosure-panel-open
      "portfolio-optimizer-objective-panel"
      (controls/disclosure-heading "Objective" (objective-summary objective-kind))
      [:div {:class ["mt-3" "grid" "grid-cols-1" "gap-1.5" "sm:grid-cols-2"]}
       (objective-card "Minimum Variance" "Lowest risk - no return assumption. Recommended"
                       (= :minimum-variance objective-kind)
                       "portfolio-optimizer-objective-minimum-variance"
                       [:actions/set-portfolio-optimizer-objective-kind :minimum-variance])
       (objective-card "Maximum Sharpe" "Best risk-adjusted return, but sensitive to noisy return estimates"
                       (= :max-sharpe objective-kind)
                       "portfolio-optimizer-objective-max-sharpe"
                       [:actions/set-portfolio-optimizer-objective-kind :max-sharpe])
       (objective-card "Target Volatility" "Pin σ to a fixed level, max return at that σ"
                       (= :target-volatility objective-kind)
                       "portfolio-optimizer-objective-target-volatility"
                       [:actions/set-portfolio-optimizer-objective-kind :target-volatility])
       (objective-card "Target Return" "Aim for a specific return"
                       (= :target-return objective-kind)
                       "portfolio-optimizer-objective-target-return"
                       [:actions/set-portfolio-optimizer-objective-kind :target-return])]
      (when (#{:target-volatility :target-return} objective-kind)
        [:div {:class ["mt-2"]}
         (case objective-kind
           :target-volatility
           (target-sigma/objective-parameter-block draft sigma-bounds)
           :target-return
           ;; Percent-entry to match Target σ and Black-Litterman: the user types 15 to mean
           ;; 15% (the percent handler divides by 100), instead of the old fraction-entry where
           ;; 0.15 meant 15% and a typed "15" silently requested a 1500% return.
           (controls/percent-input "Target Return"
                                   (controls/decimal->percent-text
                                    (or (get-in draft [:objective :target-return]) 0.15))
                                   "portfolio-optimizer-objective-target-return-input"
                                   [:actions/set-portfolio-optimizer-objective-parameter-percent
                                    :target-return [:event.target/value]]
                                   (contains? highlighted-controls :target-return)
                                   "Type a percent — 15 = 15%"))])))))

(defn- objective-card
  [title subtitle selected? role action]
  [:button {:type "button"
            :class (cond-> ["optimizer-choice-card" "optimizer-objective-card"
                            "border" "border-base-300" "bg-base-200/20" "p-2"
                            "text-left" "transition-colors" "hover:border-warning/50"]
                     selected? (conj "border-warning/60" "bg-warning/10"))
            :aria-pressed (str selected?)
            :data-role role
            :on {:click [action]}}
   [:p {:class ["text-[0.8125rem]" "font-medium" "text-trading-text"]}
    [:span {:class (if selected? "text-warning" "text-trading-muted")} (if selected? "◉ " "○ ")]
    title
    [:span {:class ["sr-only"]} title]]
   [:p {:class ["mt-1" "text-[0.75rem]" "text-trading-muted"]} subtitle]])
