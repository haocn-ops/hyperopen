(ns hyperopen.views.portfolio.optimize.setup-history-assumptions
  "Proxy workflow cards for assets that lack optimizer-safe history. Views render
  the view-model cards and dispatch the carried action ids only - no history
  math, no raw payload branching. The per-card editable field controls live in
  setup-history-assumption-fields (namespace-size split, 2026-07-10)."
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.views.portfolio.optimize.setup-controls :as controls]
            [hyperopen.views.portfolio.optimize.setup-history-assumption-fields :as fields]
            [hyperopen.views.portfolio.optimize.setup-history-assumption-recommendations :as recommendations]
            [hyperopen.views.portfolio.optimize.setup-history-assumptions-io :as assumptions-io]))

(def ^:private card-subtext
  "Insufficient native history. Model behavior using proxies.")

(defn- status-chip
  "Status pill. While this card's proxy history is still fetching, ANY status
  verdict is provisional (completeness judged against a not-yet-loaded usable
  set can even read Configured), so the chip reads a pulsing \"Loading
  history…\" until the fetch settles."
  [card]
  (let [id (:instrument-id card)
        configured? (= :configured (:status card))
        loading? (boolean (:history-loading? card))]
    [:span (cond-> {:class ["border" "px-1.5" "py-0.5" "font-mono" "text-[0.625rem]"
                            "font-semibold" "uppercase" "tracking-[0.1em]"
                            (cond loading? "border-base-300"
                                  configured? "border-success/50"
                                  :else "border-warning/50")
                            (cond loading? "text-trading-muted"
                                  configured? "text-success"
                                  :else "text-warning")
                            (cond loading? "bg-base-200/40"
                                  configured? "bg-success/10"
                                  :else "bg-warning/10")
                            (when loading? "animate-pulse")]
                    :data-role (str "portfolio-optimizer-history-assumption-status-" id)
                    :data-status (some-> (:status card) name)}
             loading? (assoc :data-loading "true"))
     (if loading? "Loading history…" (:status-label card))]))

(defn- card-errors
  [card]
  (when (seq (:errors card))
    (into [:ul {:class ["space-y-1" "text-[0.75rem]" "text-warning"]
                :data-role (str "portfolio-optimizer-history-assumption-errors-"
                                (:instrument-id card))}]
          (map (fn [message] [:li message]) (:errors card)))))

(defn- card-actions
  "Reset re-seeds the mode defaults; Apply acknowledges the configuration (green
  Configured status). Run readiness never waits on Apply - a complete assumption
  is engine-backed as soon as its fields are valid."
  [card]
  (let [id (:instrument-id card)
        loading? (boolean (:history-loading? card))]
    [:div {:class ["flex" "items-center" "justify-end" "gap-2" "border-t"
                   "border-base-300" "pt-2"]}
     ;; Hold Apply while history is fetching: completeness judged against a
     ;; not-yet-loaded proxy set flips on its own moments later, and the copy
     ;; makes the disabled button read as "wait", not "broken".
     (when loading?
       [:p {:class ["mr-auto" "animate-pulse" "text-[0.6875rem]" "text-trading-muted"]
            :data-role (str "portfolio-optimizer-history-assumption-apply-loading-" id)}
        "Waiting for proxy history to load…"])
     [:button {:type "button"
               :class ["border" "border-base-300" "px-3" "py-1.5" "text-[0.75rem]"
                       "font-semibold" "text-trading-muted" "hover:text-trading-text"]
               :data-role (str "portfolio-optimizer-history-assumption-reset-" id)
               :on {:click [[(get-in card [:actions :reset]) id]]}}
      "Reset"]
     [:button (cond-> {:type "button"
                       :class ["optimizer-primary-action" "border" "border-warning/70"
                               "bg-warning/80" "px-3" "py-1.5" "text-[0.75rem]"
                               "font-semibold" "text-base-100"
                               "disabled:cursor-not-allowed" "disabled:opacity-40"]
                       :data-role (str "portfolio-optimizer-history-assumption-apply-" id)
                       :on {:click [[(get-in card [:actions :apply]) id]]}}
                (or (not (:engine-applied? card)) loading?)
                (assoc :disabled true))
      (if (:configured? card) "Applied" "Apply assumptions")]]))

(defn- collapsed-assumption-card
  "One-line summary row for a collapsed card: label, status, the configured
  summary (when complete), and an Edit control that expands it. Keeps a stack
  of configured assets to a glance instead of a long scroll."
  [card]
  (let [id (:instrument-id card)]
    [:section {:class ["border" "bg-base-100/70" "p-3"
                       (if (= :configured (:status card))
                         "border-success/40"
                         "border-warning/40")]
               :data-role (:role card)
               :data-collapsed "true"
               :replicant/key (str "history-assumption-card-" id)}
     [:div {:class ["flex" "items-center" "justify-between" "gap-2"]}
      [:div {:class ["min-w-0" "flex" "items-baseline" "gap-2"]}
       [:p {:class controls/section-title-class} (:label card)]
       [:p {:class ["truncate" "text-[0.75rem]" "text-trading-muted"]
            :data-role (str "portfolio-optimizer-history-assumption-summary-" id)}
        (or (:summary card) "Configuration unfinished")]]
      [:div {:class ["flex" "shrink-0" "items-center" "gap-2"]}
       (status-chip card)
       [:button {:type "button"
                 :class ["text-[0.6875rem]" "uppercase" "tracking-[0.08em]"
                         "text-trading-muted" "hover:text-warning"]
                 :data-role (str "portfolio-optimizer-history-assumption-expand-" id)
                 :on {:click [[(get-in card [:actions :set-card-collapsed]) id false]]}}
        "Edit"]]]]))

(defn- assumption-card
  [card]
  (let [id (:instrument-id card)]
    [:section {:class ["border" "bg-base-100/70" "p-3" "space-y-2"
                       (if (= :configured (:status card))
                         "border-success/40"
                         "border-warning/40")]
               :data-role (:role card)
               :replicant/key (str "history-assumption-card-" id)}
     [:div {:class ["flex" "items-start" "justify-between" "gap-2"]}
      [:div
       [:p {:class controls/section-title-class}
        (if (= :configured (:status card))
          (:label card)
          (str (:label card) " needs assumptions"))]
       [:p {:class ["mt-1" "text-[0.75rem]" "leading-[1.45]" "text-trading-muted"]}
        card-subtext]]
      [:div {:class ["flex" "items-center" "gap-2"]}
       (status-chip card)
       (when (:collapsible? card)
         [:button {:type "button"
                   :class ["text-[0.6875rem]" "uppercase" "tracking-[0.08em]"
                           "text-trading-muted" "hover:text-warning"]
                   :data-role (str "portfolio-optimizer-history-assumption-collapse-" id)
                   :on {:click [[(get-in card [:actions :set-card-collapsed]) id true]]}}
          "Collapse"])
       (when (:mode card)
         [:button {:type "button"
                   :class ["text-[0.6875rem]" "uppercase" "tracking-[0.08em]"
                           "text-trading-muted" "hover:text-warning"]
                   :data-role (str "portfolio-optimizer-history-assumption-clear-" id)
                   :on {:click [[(get-in card [:actions :clear]) id]]}}
          "Clear"])]]
     ;; The backend's recommendation leads while no mode is chosen: one click
     ;; beats hand-picking, and choosing a mode by hand dismisses it.
     (when (and (nil? (:mode card)) (:recommendation card))
       (recommendations/recommendation-panel card))
     (fields/mode-tabs card)
     ;; The agent's stated reason for an imported configuration — the trust
     ;; artifact that lets the user audit a file-authored basket at a glance.
     (when (:rationale card)
       [:p {:class ["border" "border-base-300" "bg-base-200/20" "p-2"
                    "text-[0.75rem]" "leading-[1.45]" "text-trading-muted"]
            :data-role (str "portfolio-optimizer-history-assumption-rationale-" id)}
        (str "Agent rationale: " (:rationale card))])
     (case (:mode card)
       :proxy (fields/proxy-fields card)
       :conservative (fields/conservative-fields card)
       [:p {:class ["text-[0.75rem]" "leading-[1.5]" "text-trading-muted"]}
        "Choose an approach. Until configured, this asset stays excluded from the run."])
     (when (:note card)
       [:p {:class ["border" "border-warning/40" "bg-warning/10" "p-2"
                    "text-[0.75rem]" "text-warning"]
            :data-role (str "portfolio-optimizer-history-assumption-note-" id)}
        (:note card)])
     (card-errors card)
     (when (:summary card)
       [:p {:class ["text-[0.75rem]" "text-trading-text"]
            :data-role (str "portfolio-optimizer-history-assumption-summary-" id)}
        (str (:label card) ": " (:summary card))])
     (when (:mode card)
       (card-actions card))]))

(defn- assumption-card-or-summary
  [card]
  (if (:collapsed? card)
    (collapsed-assumption-card card)
    (assumption-card card)))

(defn- add-asset-select
  "Manual entry point: bring ANY selected asset into the proxy workflow. The
  thresholds only auto-flag egregious cases; the user may judge an asset
  statistically unsound (a young listing, a stubby return stream) on their own
  and factor-load it regardless. Choosing an asset seeds proxy mode - its card
  appears immediately below."
  [addable-assets]
  (when (seq addable-assets)
    ;; Full-width own row (see history-assumptions-section) so the placeholder
    ;; "+ Model an asset with proxies…" is never clipped horizontally. py-0 +
    ;; leading-none: optimizer surfaces pin inputs/selects to a fixed 26px
    ;; border-box (optimizer/base.css) while @tailwindcss/forms adds 0.5rem
    ;; vertical padding to every <select> - left alone it clips the text, so
    ;; py-0 overrides it; pr-7 leaves room for the native chevron.
    (into [:select {:class ["w-full" "border" "border-base-300" "bg-base-100/80"
                            "py-0" "pl-2" "pr-7" "font-mono" "text-[0.75rem]" "leading-none"
                            "text-trading-muted"]
                    :value ""
                    :data-role "portfolio-optimizer-history-assumption-workflow-add"
                    :on {:change [[:actions/set-portfolio-optimizer-history-assumption-mode
                                   [:event.target/value] :proxy]]}}
           [:option {:value ""} "+ Model an asset with proxies…"]]
          ;; Options arrive sorted ascending by native return-day count with the
          ;; count in the label, so the assets limiting the shared covariance
          ;; window sit at the top of the list.
          (map (fn [{:keys [instrument-id label option-label]}]
                 [:option {:value instrument-id} (or option-label label)]))
          addable-assets)))

(defn- loading-banner
  "Aggregate \"background work in progress\" strip: while any card's proxy
  history is still fetching, tell the user the cards below will fill in on
  their own so they don't start re-editing provisional state."
  [loading-count]
  (when (pos? loading-count)
    [:div {:class ["flex" "items-center" "gap-2" "border" "border-base-300"
                   "bg-base-200/40" "px-2" "py-1.5"]
           :data-role "portfolio-optimizer-history-assumptions-loading-banner"}
     [:span {:class ["h-1.5" "w-1.5" "shrink-0" "animate-pulse" "rounded-full"
                     "bg-warning"]
             :aria-hidden "true"}]
     [:span {:class ["font-mono" "text-[0.6875rem]" "text-trading-muted"]}
      (str "Loading proxy history for " loading-count
           (if (= 1 loading-count) " asset" " assets")
           " — cards update automatically when it finishes.")]]))

(defn- section-trailing-status
  "Collapsed-header status for the section: the one line that must carry the
  whole story while the disclosure rests closed. Wrapped in
  `optimizer-section-trailing` by the caller so it hides while open (the body
  then shows the same facts in full)."
  [{:keys [card-count configured-count loading-count]}]
  (cond
    (pos? loading-count)
    {:label (str "Loading proxy history for " loading-count
                 (if (= 1 loading-count) " asset…" " assets…"))
     :tone "text-trading-muted"}

    (zero? card-count)
    {:label "None needed"
     :tone "text-trading-muted/70"}

    (= configured-count card-count)
    {:label (str configured-count " of " card-count " configured")
     :tone "text-success"}

    :else
    {:label (str (- card-count configured-count)
                 (if (= 1 (- card-count configured-count))
                   " asset needs setup"
                   " assets need setup"))
     :tone "text-warning"}))

(defn history-assumptions-section
  "Collapsed-by-exception disclosure panel. When every workflow asset is
  configured the section rests as one summary line (\"History assumptions —
  N of N configured\"); it forces itself :open whenever anything needs the
  user (unconfigured cards, card errors, proxy history still loading) — a
  machine condition, so re-asserting :open against the user's toggle is the
  point, same shape as the More-goals drawer. Everything inside (agent IO
  toolbar, picker, cards) is demoted with the body, never removed."
  [{:keys [state draft readiness history-load-state]}]
  (let [{:keys [cards addable-assets applicable? history-loading-count]
         :as cards-model}
        (optimizer-view-model/history-assumption-cards
         state draft readiness history-load-state
         {:percent-label controls/percent-label})
        card-count (count cards)
        configured-count (count (filter #(= :configured (:status %)) cards))
        loading-count (or history-loading-count 0)
        needs-attention? (boolean (or (pos? loading-count)
                                      (some #(not= :configured (:status %)) cards)
                                      (some #(seq (:errors %)) cards)))
        trailing (section-trailing-status {:card-count card-count
                                           :configured-count configured-count
                                           :loading-count loading-count})]
    (when (or applicable? (seq addable-assets))
      [:details (cond-> {:class ["optimizer-setup-panel" "border" "border-base-300"
                                 "bg-base-100/90" "p-3"]
                         :data-role "portfolio-optimizer-history-assumptions-section"
                         :id "portfolio-optimizer-history-assumptions-section"
                         :replicant/key "history-assumptions-section"}
                  needs-attention? (assoc :open true))
       [:summary {:class ["cursor-pointer" "select-none" "focus:outline-none"
                          "focus:text-warning"]}
        [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
         ;; Result vocabulary in the title ("History assumptions" — same concept
         ;; the rail panel summarizes); the mechanism word "proxy" stays in the
         ;; body (subtitle, "Proxy assets" field, picker).
         [:p {:class controls/section-title-class} "History assumptions"]
         ;; The count role only exists while assets are in the workflow (pinned
         ;; contract); the zero-card resting label stays role-less.
         [:span (cond-> {:class ["optimizer-section-trailing" "shrink-0" "whitespace-nowrap"
                                 "font-mono" "text-[0.6875rem]" (:tone trailing)]}
                  (pos? card-count)
                  (assoc :data-role "portfolio-optimizer-history-assumptions-count"))
          (:label trailing)]]]
       (into [:div {:class ["mt-3" "space-y-3"]}
              [:p {:class ["text-[0.75rem]" "leading-[1.45]" "text-trading-muted"]}
               "Define assumptions for assets whose return history you don't trust."]
              (loading-banner loading-count)
              (recommendations/recommended-banner cards-model)
              (assumptions-io/io-toolbar
               {:asset-count card-count
                :universe-count (+ card-count (count addable-assets))})
              (assumptions-io/io-note
               (get-in state contracts/ui-history-assumptions-io-note-path))
              ;; The picker gets its OWN full-width row so the placeholder text is
              ;; never clipped by the header's title/description competing for width.
              (add-asset-select addable-assets)]
             (concat
              (map assumption-card-or-summary cards)
              (when (empty? cards)
                [[:p {:class ["text-[0.75rem]" "leading-[1.5]" "text-trading-muted"]
                      :data-role "portfolio-optimizer-history-assumptions-empty"}
                  "Nothing needs assumptions right now. Pick an asset above to model it as a basket of assets it behaves like - its returns are then driven by the basket plus specific risk instead of its own short history."]])))])))
