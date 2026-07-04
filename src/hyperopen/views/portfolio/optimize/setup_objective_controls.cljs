(ns hyperopen.views.portfolio.optimize.setup-objective-controls
  (:require [hyperopen.portfolio.optimizer.application.return-views :as return-views]
            [hyperopen.portfolio.optimizer.domain.exposure-policy :as exposure-policy]
            [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.target-sigma :as target-sigma]))

(declare primary-goal-card secondary-goal-card)

(defn- goal-summary
  "Header one-liner for the collapsed Optimization goal panel: the selected goal
  plus what choosing it means, so the panel reads without opening it."
  [objective-kind]
  (case objective-kind
    :max-sharpe "Maximum Sharpe — best risk-adjusted return, uses your views"
    :target-volatility "Target volatility — max return at a pinned σ"
    :target-return "Target return — lowest risk at a required return"
    "Minimum risk — lowest volatility, no return forecast needed"))

(defn- max-sharpe-kicker
  ;; Live provenance counts ("2 your views · 12 implied") once the views-aware
  ;; model is active, so the card shows the user their views are involved without
  ;; pretending views are a separate strategy.
  [draft]
  (if (= :black-litterman (get-in draft [:return-model :kind]))
    (return-views/returns-contract-label
     (return-views/summary
      (return-views/rows {:universe (:universe draft)
                          :views (get-in draft [:return-model :views])})))
    "Uses your saved views where available"))

(defn- holdings-custom-constraints?
  "True when a holdings import seeded the constraint envelope (gross floor, caps,
  and net bias mirrored from the current book). The selected Minimum risk card
  must disclose that, or the goal silently implies the preset's default envelope
  while e.g. a 152% single-asset cap and a net-short floor are actually in force."
  [draft]
  (and (= :holdings (get-in draft [:metadata :universe-source :kind]))
       (= :custom (exposure-policy/active-preset (:constraints draft)))))

(defn objective-section
  ([draft highlighted-controls]
   (objective-section draft highlighted-controls nil))
  ([draft highlighted-controls sigma-bounds]
   (let [objective-kind (get-in draft [:objective :kind])]
     ;; The single authoritative place the user picks what the optimizer solves
     ;; for. A disclosure (open by default) rather than a fixed section: after the
     ;; choice is made the header line summarizes it, and the user can collapse
     ;; the cards to keep the center column a scannable policy contract.
     ;;
     ;; The two canonical paths — Minimum risk and Maximum Sharpe — lead as rich
     ;; cards that carry the return-model pairing the old top-of-page presets did
     ;; (Maximum Sharpe activates the wallet's return views). The advanced targets
     ;; sit below under "More goals" so they never visually compete with them.
     (controls/disclosure-panel-open
      "portfolio-optimizer-objective-panel"
      (controls/disclosure-heading "Optimization goal" (goal-summary objective-kind))
      [:p {:class ["mt-2" "text-[0.8125rem]" "text-trading-muted"]}
       "Choose what the optimizer should prioritize."]
      [:div {:class ["mt-3" "grid" "grid-cols-1" "gap-1.5" "sm:grid-cols-2"]}
       (primary-goal-card
        "Minimum risk" "Minimum variance · no return forecast needed"
        "Recommended for first runs"
        (= :minimum-variance objective-kind)
        "portfolio-optimizer-objective-minimum-variance"
        [:actions/apply-portfolio-optimizer-setup-preset :conservative]
        (when (and (= :minimum-variance objective-kind)
                   (holdings-custom-constraints? draft))
          [:p {:class ["mt-1.5" "text-[0.6875rem]" "text-warning"]
               :data-role "portfolio-optimizer-preset-holdings-constraints-note"}
           "Constraints were seeded from your holdings — review them in Portfolio exposure below."]))
       (primary-goal-card
        "Maximum Sharpe" "Best risk-adjusted return · sensitive to noisy return estimates"
        (max-sharpe-kicker draft)
        (= :max-sharpe objective-kind)
        "portfolio-optimizer-objective-max-sharpe"
        [:actions/apply-portfolio-optimizer-setup-preset :max-sharpe]
        nil)]
      [:p {:class (conj controls/eyebrow-class "mt-3")} "More goals"]
      [:div {:class ["mt-2" "grid" "grid-cols-1" "gap-1.5" "sm:grid-cols-2"]}
       (secondary-goal-card "Target volatility" "Pin σ to a fixed level, max return at that σ"
                            (= :target-volatility objective-kind)
                            "portfolio-optimizer-objective-target-volatility"
                            [:actions/set-portfolio-optimizer-objective-kind :target-volatility])
       (secondary-goal-card "Target return" "Aim for a specific return"
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

(defn- primary-goal-card
  "Rich goal card for the two canonical paths (Minimum risk, Maximum Sharpe): a
  title, a plain-language subtitle, a mono kicker line, an active badge, and an
  optional disclosure slot (holdings-seeded-constraints note / live views count).
  Applies the matching setup preset so the objective AND its return-model pairing
  move together — this is what the retired top-of-page preset cards did."
  [title subtitle kicker selected? role action extra]
  [:button {:type "button"
            :class (cond-> ["optimizer-choice-card" "optimizer-goal-card" "optimizer-goal-card-primary"
                            "border" "border-base-300" "bg-base-100/70" "px-3" "py-2.5" "text-left"
                            "transition-colors" "hover:border-warning/50"]
                     selected? (conj "border-warning/70" "bg-warning/10"))
            :aria-pressed (str selected?)
            :data-role role
            :on {:click [action]}}
   [:div {:class ["flex" "items-start" "justify-between" "gap-3"]}
    [:div
     [:p {:class ["text-[0.8125rem]" "font-medium" "text-trading-text"]}
      [:span {:class (if selected? "text-warning" "text-trading-muted")} (if selected? "◉ " "○ ")]
      title]
     [:p {:class ["mt-1.5" "text-[0.75rem]" "text-trading-muted"]} subtitle]
     [:p {:class ["mt-1.5" "font-mono" "text-[0.625rem]" "uppercase" "tracking-[0.16em]"
                  "text-trading-muted/70"]}
      kicker]
     extra]
    (when selected?
      [:span {:class ["border" "border-base-300" "px-1.5" "py-0.5" "font-mono"
                      "text-[0.625rem]" "uppercase" "tracking-[0.12em]" "text-trading-muted/70"]}
       "active"])]])

(defn- secondary-goal-card
  "Compact goal card for the advanced targets (Target volatility / Target return):
  sets only the objective, leaving the return model untouched. Rendered smaller
  and under a 'More goals' label so it never competes with the canonical paths."
  [title subtitle selected? role action]
  [:button {:type "button"
            :class (cond-> ["optimizer-choice-card" "optimizer-goal-card" "optimizer-goal-card-secondary"
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
