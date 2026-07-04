(ns hyperopen.views.portfolio.optimize.setup-header
  (:require [hyperopen.portfolio.optimizer.application.return-views :as return-views]
            [hyperopen.portfolio.optimizer.domain.exposure-policy :as exposure-policy]))

(defn- clock-label
  "Local wall-clock label for the autosave note (\"10:42 PM\"). Rendered from the
  epoch with local getters; tests assert presence, not the exact string, so the
  label stays timezone-safe."
  [at-ms]
  (when (number? at-ms)
    (.toLocaleTimeString (js/Date. at-ms)
                         js/undefined
                         #js {:hour "numeric" :minute "2-digit"})))

(def ^:private eyebrow-class
  ["font-mono" "text-[0.6875rem]" "font-semibold" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"])

(defn- route-title
  [route]
  (case (:kind route)
    :optimize-new "Untitled scenario"
    :optimize-scenario (str "Scenario " (:scenario-id route))
    "Optimizer scenario"))

(defn- active-preset
  ;; Two presets: the objective choice. A views-aware (Black-Litterman) return
  ;; model belongs to Maximum Sharpe — views are an input, not a strategy.
  [draft]
  (let [objective-kind (get-in draft [:objective :kind])
        return-kind (get-in draft [:return-model :kind])]
    (if (or (= :black-litterman return-kind)
            (= :max-sharpe objective-kind))
      :max-sharpe
      :conservative)))

(defn- max-sharpe-kicker
  ;; Live provenance counts ("2 YOUR VIEWS · 12 IMPLIED") once the views-aware
  ;; model is active, so the card shows the user their views are involved without
  ;; pretending views are a separate strategy.
  [draft]
  (if (= :black-litterman (get-in draft [:return-model :kind]))
    (return-views/returns-contract-label
     (return-views/summary
      (return-views/rows {:universe (:universe draft)
                          :views (get-in draft [:return-model :views])})))
    "Uses your return views"))

(defn setup-header
  [{:keys [draft route draft-persist]}]
  [:header {:class ["optimizer-setup-header" "border" "border-base-300" "bg-base-100/90" "px-3" "py-2"]
            :data-role "portfolio-optimizer-setup-header"}
   [:div {:class ["flex" "items-center" "justify-between" "gap-4"]}
    [:div {:class ["min-w-0"]}
     [:p {:class eyebrow-class} "Portfolio Optimizer"]
     [:div {:class ["mt-1" "flex" "flex-wrap" "items-center" "gap-2"]}
      [:h1 {:class ["text-lg" "font-medium" "tracking-[-0.01em]" "text-trading-text"]}
       (route-title route)]
      [:span {:class ["text-[0.8125rem]" "text-trading-muted"]}
       "- configure your target portfolio"]
      [:span {:class ["optimizer-status-tag" "border" "border-base-300" "bg-base-200/40" "px-2" "py-0.5"
                      "font-mono" "text-[0.6875rem]" "font-semibold" "uppercase"
                      "tracking-[0.12em]" "text-trading-muted/70"]
              :data-role "portfolio-optimizer-setup-status-tag"}
       (if (= :computed (:status draft)) "computed" "draft")]
      ;; Modeless autosave feedback: the draft persists per wallet on every edit,
      ;; so the header reports it instead of asking for a manual "save draft".
      (when-let [saved-label (clock-label (:at-ms draft-persist))]
        [:span {:class ["font-mono" "text-[0.6875rem]" "uppercase" "tracking-[0.12em]"
                        "text-trading-muted/60"]
                :data-role "portfolio-optimizer-draft-autosave-note"}
         (str "Saved " saved-label)])]
     [:span {:class ["sr-only"]
             :data-role "portfolio-optimizer-draft-state"}
      (if (get-in draft [:metadata :dirty?])
        "Draft has unsaved changes"
        "Draft clean")]]]])

(defn- holdings-custom-constraints?
  "True when a holdings import seeded the constraint envelope (gross floor,
  caps, and net bias mirrored from the current book). The glowing preset card
  must disclose that, or 'Conservative · active' silently implies the preset's
  default envelope while e.g. a 152% single-asset cap and a net-short floor are
  actually in force."
  [draft]
  (and (= :holdings (get-in draft [:metadata :universe-source :kind]))
       (= :custom (exposure-policy/active-preset (:constraints draft)))))

(defn- preset-card
  [draft preset title subtitle kicker]
  (let [selected? (= preset (active-preset draft))]
    [:button {:type "button"
              :class (cond-> ["optimizer-choice-card" "optimizer-preset-card"
                              "border" "border-base-300" "bg-base-100/70" "px-3" "py-2.5" "text-left"
                              "transition-colors" "hover:border-warning/50"]
                       selected? (conj "border-warning/70" "bg-warning/10"))
              :aria-pressed (str selected?)
              :data-role (str "portfolio-optimizer-setup-preset-" (name preset))
              :on {:click [[:actions/apply-portfolio-optimizer-setup-preset preset]]}}
     [:div {:class ["flex" "items-start" "justify-between" "gap-3"]}
      [:div
       [:p {:class ["text-[0.8125rem]" "font-medium" "text-trading-text"]}
        [:span {:class (if selected? "text-warning" "text-trading-muted")} (if selected? "◉ " "○ ")]
        title]
       [:p {:class ["mt-1.5" "text-[0.75rem]" "text-trading-muted"]} subtitle]
       [:p {:class ["mt-1.5" "font-mono" "text-[0.625rem]" "uppercase" "tracking-[0.16em]"
                    "text-trading-muted/70"]}
        kicker]
       (when (and selected? (holdings-custom-constraints? draft))
         [:p {:class ["mt-1.5" "text-[0.6875rem]" "text-warning"]
              :data-role "portfolio-optimizer-preset-holdings-constraints-note"}
          "Constraints were seeded from your holdings — review them in Constraints below."])]
      (when selected?
        [:span {:class ["border" "border-base-300" "px-1.5" "py-0.5" "font-mono"
                        "text-[0.625rem]" "uppercase" "tracking-[0.12em]" "text-trading-muted/70"]}
         "active"])]]))

(defn preset-row
  [draft]
  [:section {:class ["optimizer-setup-preset-row" "border" "border-base-300" "bg-base-100/80" "px-3" "py-2.5"]
             :data-role "portfolio-optimizer-setup-preset-row"}
   [:div {:class ["grid" "grid-cols-1" "gap-2" "xl:grid-cols-[82px_minmax(0,1fr)]"]}
    [:p {:class (conj eyebrow-class "pt-1.5")} "Start with"]
    [:div {:class ["grid" "grid-cols-1" "gap-2" "lg:grid-cols-2"]}
     (preset-card draft :conservative "Conservative"
                  "Minimum variance - no return forecast needed"
                  "Recommended for first runs")
     (preset-card draft :max-sharpe "Maximum Sharpe"
                  "Best risk-adjusted return - your saved views where available, implied returns otherwise"
                  (max-sharpe-kicker draft))]]])
