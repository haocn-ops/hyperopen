(ns hyperopen.views.portfolio.optimize.setup-objective-controls
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.target-sigma :as target-sigma]))

(declare objective-card)

(defn objective-section
  ([draft highlighted-controls]
   (objective-section draft highlighted-controls nil))
  ([draft highlighted-controls sigma-bounds]
   (let [objective-kind (get-in draft [:objective :kind])]
     (controls/panel
      "portfolio-optimizer-objective-panel"
      (controls/section-heading "Objective" (controls/labelize objective-kind))
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
           (controls/number-input "Target Return"
                                  (or (get-in draft [:objective :target-return]) 0.15) "portfolio-optimizer-objective-target-return-input"
                                  [:actions/set-portfolio-optimizer-objective-parameter :target-return [:event.target/value]]
                                  (contains? highlighted-controls :target-return)))])))))

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
   [:p {:class ["text-[0.6875rem]" "font-medium" "text-trading-text"]}
    [:span {:class (if selected? "text-warning" "text-trading-muted")} (if selected? "◉ " "○ ")]
    title
    [:span {:class ["sr-only"]} title]]
   [:p {:class ["mt-1" "text-[0.65625rem]" "text-trading-muted"]} subtitle]])
