(ns hyperopen.views.portfolio.optimize.setup-universe
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.views.portfolio.optimize.setup-history-assumptions :as setup-history-assumptions]))

(def ^:private eyebrow-class
  ["font-mono" "text-[0.6875rem]" "font-semibold" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"])

;; Sentence-case 14px, matching setup-controls/section-title-class — see the
;; type-ladder note there.
(def ^:private section-title-class
  ["text-[0.875rem]" "font-semibold" "text-trading-text"])

(def ^:private input-class
  ["w-full" "border" "border-base-300" "bg-base-100/80" "px-2" "py-1.5"
   "font-mono" "text-[0.8125rem]" "font-medium" "outline-none"
   "transition-shadow" "focus:border-warning/70"
   "focus:shadow-[0_0_0_1px_rgba(212,181,88,0.75)]"])

(defn- tag
  ([label tone]
   (tag label tone nil))
  ([label tone extra-class]
   [:span {:class (cond-> ["optimizer-chip" "border" "px-1.5" "py-[1px]" "font-mono"
                           "text-[0.625rem]" "font-semibold" "uppercase"
                           "tracking-[0.12em]"]
                    (= tone :accent) (conj "border-warning/40" "text-warning")
                    (= tone :info) (conj "border-info/40" "text-info")
                    (= tone :long) (conj "border-success/40" "text-success")
                    (= tone :warn) (conj "border-warning/40" "text-warning")
                    (= tone :muted) (conj "border-base-300" "text-trading-muted")
                    extra-class (conj extra-class))
           :data-optimizer-chip "true"
           :data-tone (name tone)}
    label]))

(defn- market-type-tags
  [market-type]
  [:span {:class ["flex" "items-center" "gap-1"]}
   (when (= :spot market-type)
     (tag "spot" :info))
   (when (= :perp market-type)
     (tag "perp" :accent))
   (when (= :vault market-type)
     (tag "vault" :info))])

(defn- side-button
  [instrument-id selected-side short-selectable? side]
  (let [selected? (= selected-side side)
        enabled? (or (= :long side)
                     short-selectable?)]
    [:button {:type "button"
              :class (cond-> ["h-6" "w-6" "border" "border-base-300"
                              "font-mono" "text-[0.6875rem]" "font-semibold"
                              "uppercase" "leading-none" "transition-colors"]
                       (and selected? (= :long side))
                       (conj "bg-success/70" "text-base-100")
                       (and selected? (= :short side))
                       (conj "bg-error/70" "text-base-100")
                       (and (not selected?) enabled?)
                       (conj "text-trading-muted" "hover:border-warning/60"
                             "hover:text-warning")
                       (not enabled?)
                       (conj "cursor-not-allowed" "text-trading-muted/30"
                             "opacity-50"))
              :aria-label (str "Set " instrument-id " "
                               (name side))
              :aria-pressed (if selected? "true" "false")
              :data-role (str "portfolio-optimizer-universe-side-"
                              (name side)
                              "-"
                              instrument-id)
              :data-selected (when selected? "true")
              :disabled (when-not enabled? true)
              :on (when enabled?
                    {:click [[:actions/set-portfolio-optimizer-universe-instrument-side
                              instrument-id
                              side]]})}
     (case side
       :short "S"
       "L")]))

(defn- side-control
  [instrument-id position-side short-selectable?]
  [:span {:class ["flex" "items-center" "justify-center"]
          :data-role (str "portfolio-optimizer-universe-side-control-"
                          instrument-id)}
   (side-button instrument-id position-side short-selectable? :long)
   (side-button instrument-id position-side short-selectable? :short)])

(defn- remove-aria-label
  [primary-label market-type instrument-id]
  (str "Remove "
       (or primary-label instrument-id "instrument")
       (when-let [market-type-label (some-> market-type name)]
         (str " " market-type-label))))

(defn- assumption-badge-chip
  [instrument-id badge badge-label]
  (when (and badge (not= :ready badge))
    [:span {:class ["optimizer-chip" "border" "px-1.5"
                    "py-[1px]" "font-mono" "text-[0.625rem]" "font-semibold"
                    "uppercase" "tracking-[0.1em]"]
            :data-optimizer-chip "true"
            :data-tone (if (contains? #{:conservative :using-proxy} badge) "accent" "warn")
            :data-role (str "portfolio-optimizer-universe-assumption-badge-" instrument-id)}
     badge-label]))

(defn- history-row-chip
  "By-exception history chip rendered inline on the row (no dedicated column).
  history-label is nil for all-clear rows, so nothing renders; the tone is
  :muted for transient load states and :warn for genuine coverage problems."
  [instrument-id history-label history-tone]
  (when history-label
    [:span {:class (cond-> ["optimizer-chip" "border" "px-1.5" "py-[1px]" "font-mono"
                            "text-[0.625rem]" "font-semibold" "uppercase"
                            "tracking-[0.12em]"]
                     (= history-tone :muted) (conj "border-base-300" "text-trading-muted")
                     (not= history-tone :muted) (conj "border-warning/40" "text-warning"))
            :data-optimizer-chip "true"
            :data-tone (name history-tone)
            :data-role (str "portfolio-optimizer-universe-history-chip-" instrument-id)}
     history-label]))

(defn- row-flags
  "By-exception row flags rendered inline under the asset name (there is no
  dedicated History column). All-clear rows pass history-label = nil and a
  :ready assumption badge, so nothing renders; only load states, genuine
  coverage problems, and non-default assumptions surface a chip."
  [instrument-id history-label history-tone assumption-badge assumption-badge-label]
  (let [history (history-row-chip instrument-id history-label history-tone)
        assumption (assumption-badge-chip instrument-id assumption-badge assumption-badge-label)]
    (when (or history assumption)
      [:span {:class ["mt-0.5" "flex" "flex-wrap" "items-center" "gap-1"]}
       history
       assumption])))

(defn- selected-row
  [{:keys [instrument-id
           market-type
           primary-label
           secondary-label
           history-status
           history-label
           history-tone
           assumption-badge
           assumption-badge-label
           position-side
           short-selectable?]}]
    [:div {:class ["optimizer-universe-row"
                   "grid" "items-center" "gap-2" "border-b" "border-base-300"
                   "px-2" "py-1.5" "last:border-b-0" "hover:bg-base-200/30"]
           :data-role (str "portfolio-optimizer-universe-selected-row-" instrument-id)
           :data-history-status (some-> history-status name)}
     [:span {:class ["text-warning"]} "☑"]
     [:span {:class ["min-w-0"]}
      [:span {:class ["block" "truncate" "font-mono" "text-[0.8125rem]" "font-semibold"]}
       primary-label]
      ;; Demoted a full tier below the symbol: the canonical id/name is context,
      ;; and must not compete with the tradable symbol the user scans for.
      [:span {:class ["block" "truncate" "text-[0.6875rem]" "text-trading-muted/80"]}
       secondary-label]
      (row-flags instrument-id history-label history-tone assumption-badge assumption-badge-label)]
     [:span {:class ["flex" "justify-center"]} (market-type-tags market-type)]
     (side-control instrument-id position-side short-selectable?)
     [:span {:class ["text-right"]}
      [:button {:type "button"
                :class ["font-mono" "text-[0.8125rem]" "text-trading-muted" "hover:text-warning"]
                :aria-label (remove-aria-label primary-label market-type instrument-id)
                :data-role (str "portfolio-optimizer-universe-remove-" instrument-id)
                :on {:click [[:actions/remove-portfolio-optimizer-universe-instrument
                               instrument-id]]}}
       "x"]]])

(defn- market-row
  [{:keys [market-key market-type active? label name]} idx]
    [:div {:class ["optimizer-universe-candidate-row"
                   "grid" "items-center" "gap-2" "border-b" "border-base-300" "cursor-pointer" "px-2"
                   "py-1.5" "last:border-b-0" "hover:bg-base-200/30"]
           :data-role (str "portfolio-optimizer-universe-candidate-row-" market-key)
           :id (str "portfolio-optimizer-universe-candidate-" idx)
           :role "option"
           :aria-selected (if active? "true" "false")
           :data-active (when active? "true")
           :on {:click [[:actions/add-portfolio-optimizer-universe-instrument market-key]]}}
     [:span {:class ["truncate" "font-mono" "text-[0.8125rem]" "font-semibold"]}
      label]
     [:span {:class ["truncate" "text-[0.8125rem]" "text-trading-muted"]}
      name]
     (market-type-tags market-type)
     [:button {:type "button"
               :class ["optimizer-universe-add-button"
                       "text-right" "font-mono" "text-[0.75rem]" "font-semibold"
                       "text-warning" "hover:text-warning"]
               :data-role (str "portfolio-optimizer-universe-add-" market-key)
               :on {:click [[:actions/add-portfolio-optimizer-universe-instrument market-key]]}}
      "+ add"]])

(defn- selected-table-header
  []
  [:div {:class ["optimizer-universe-selected-header"
                 "grid" "items-center" "gap-2" "border-b" "border-base-300"
                 "bg-base-200/40" "px-2" "py-1.5" "font-mono"
                 "text-[0.625rem]" "font-semibold" "uppercase"
                 "tracking-[0.12em]" "text-trading-muted/70"]
         :data-role "portfolio-optimizer-universe-selected-header"}
   [:span ""]
   [:span "Asset"]
   [:span {:class ["text-center"]} "Type"]
   [:span {:class ["text-center"]} "Side"]
   [:span {:class ["sr-only"]} "Remove"]])

(defn- candidate-table-header
  []
  [:div {:class ["optimizer-universe-candidate-header"
                 "grid" "items-center" "gap-2" "border-b" "border-base-300"
                 "bg-base-200/40" "px-2" "py-1.5" "font-mono"
                 "text-[0.625rem]" "font-semibold" "uppercase"
                 "tracking-[0.12em]" "text-trading-muted/70"]
         :data-role "portfolio-optimizer-universe-candidate-header"
         :role "presentation"}
   [:span "Asset"]
   [:span "Name"]
   [:span "Type"]
   [:span {:class ["sr-only"]} "Add"]])

(defn- holdings-loading-block
  "Modeless inline loading state for the auto-seed window: the machine is
  fetching the portfolio on the user's behalf, so the panel says so instead of
  showing the empty-universe copy that asks the user to add assets."
  []
  [:div {:class ["px-2" "py-3"]
         :data-role "portfolio-optimizer-universe-holdings-loading"}
   [:p {:class ["text-[0.8125rem]" "font-medium" "text-trading-text"]}
    "Loading holdings…"]
   [:p {:class ["mt-1" "text-[0.75rem]" "text-trading-muted"]}
    "Fetching the current portfolio for this account."]
   [:div {:class ["mt-3" "space-y-2"]
          :aria-hidden "true"}
    [:div {:class ["optimizer-universe-skeleton-row"]}]
    [:div {:class ["optimizer-universe-skeleton-row"]}]
    [:div {:class ["optimizer-universe-skeleton-row"]}]]])

(defn- selected-table
  [selected-rows universe holdings-loading?]
  [:div {:class ["mt-2" "border" "border-base-300" "bg-base-100/50"]}
   [:div {:class ["flex" "items-center" "justify-between" "gap-2" "border-b"
                  "border-base-300" "px-2" "py-1.5"]}
    [:span {:class ["font-mono" "text-[0.6875rem]" "uppercase" "tracking-[0.12em]"
                    "text-trading-muted"]}
     (if holdings-loading?
       "Loading holdings…"
       (str (count universe) " included"))]
    (when (seq universe)
      [:button {:type "button"
                :class ["font-mono" "text-[0.6875rem]" "uppercase" "tracking-[0.12em]"
                        "text-trading-muted" "hover:text-warning"]
                :data-role "portfolio-optimizer-universe-clear"
                :on {:click [[:actions/clear-portfolio-optimizer-universe]]}}
       "Clear universe"])]
   (cond
     (seq universe)
     (into [:div {:class ["text-xs"]}]
           (cons (selected-table-header)
                 (map selected-row selected-rows)))

     holdings-loading?
     (holdings-loading-block)

     ;; Empty universe: either "no account/holdings" or a deliberately cleared
     ;; set (a clear records a custom source, which ends the loading state).
     :else
     [:div {:class ["flex" "flex-col" "items-start" "gap-1" "px-2" "py-3"]}
      [:p {:class ["text-xs" "text-trading-muted"]}
       "No assets selected yet."]
      [:p {:class ["text-[0.75rem]" "text-trading-muted/80"]}
       "Holdings load automatically when your account is connected — use “My holdings” above to re-import them, or search to build a custom set."]])])

(defn- omission-reason-copy
  [reason]
  (case reason
    :spot-excluded "spot balance — excluded while spot assets are off in constraints"
    :unusable-history "no usable optimizer history"
    "excluded"))

(defn- universe-source-line
  "Visible-automation line under the source strip: says where the universe came
  from and accounts for every asset a holdings load left out (nothing is dropped
  silently)."
  [universe-source universe-count]
  (let [holdings? (= :holdings (:kind universe-source))
        omitted (vec (or (:omitted universe-source) []))]
    (when universe-source
      [:div {:class ["mt-1.5" "font-mono" "text-[0.6875rem]" "leading-4" "text-trading-muted"]
             :data-role "portfolio-optimizer-universe-source-line"
             :data-universe-source (name (or (:kind universe-source) :custom))}
       (if holdings?
         [:p (str "From holdings · " universe-count " included"
                  (when (pos? (count omitted))
                    (str " · " (count omitted) " omitted")))]
         [:p "Custom universe"])
       (when (and holdings? (seq omitted))
         [:details {:class ["mt-0.5"]}
          [:summary {:class ["cursor-pointer" "text-trading-muted/80" "hover:text-warning"]
                     :data-role "portfolio-optimizer-universe-omitted-summary"}
           (str "Show " (count omitted) " omitted")]
          (into [:ul {:class ["mt-1" "space-y-0.5" "pl-3" "text-trading-muted/80"]
                      :data-role "portfolio-optimizer-universe-omitted-list"}]
                (map (fn [{:keys [instrument-id label reason]}]
                       [:li {:data-omitted-instrument instrument-id}
                        (str (or label instrument-id) " — "
                             (omission-reason-copy reason))])
                     omitted))])])))

(defn universe-section
  ([state draft]
   (universe-section state draft nil))
  ([state draft opts]
   (let [{:keys [universe
                 readiness
                 history-load-state
                 history-status-by-id
                 selected-rows
                 search-query
                 searching?
                 markets
                 candidate-rows
                 active-index
                 market-keys
                 universe-source]} (optimizer-view-model/universe-section-model state draft opts)
         holdings-source? (= :holdings (:kind universe-source))
         holdings-loading? (= :holdings-loading (:reason readiness))
         ;; While the auto-seed is pending, "My holdings" is the source in
         ;; flight, so the strip shows it active instead of offering it as a
         ;; manual command the machine is already executing.
         holdings-active? (or holdings-source? holdings-loading?)]
    [:section {:class ["optimizer-universe-panel" "optimizer-setup-panel"
                       "border" "border-base-300" "bg-base-100/90" "p-3" "leading-4"]
               :data-role "portfolio-optimizer-universe-panel"
               :data-holdings-loading (when holdings-loading? "true")}
     [:div {:class ["flex" "items-center" "justify-between" "gap-3" "border-b"
                    "border-base-300" "pb-2"]}
      [:p {:class section-title-class} "Universe"]
      [:span {:class ["font-mono" "text-[0.75rem]" "uppercase" "tracking-[0.08em]"
                      "text-trading-muted/70"]}
       (if holdings-loading?
         "loading…"
         (str (count universe) " included"))]]
     ;; Source strip: "My holdings" is the default path (the universe seeds itself
     ;; from holdings when account data is available) and doubles as the
     ;; load/refresh action; "Custom" lights up once the set is hand-edited or
     ;; cleared. The active segment reflects the recorded universe source.
     [:div {:class ["mt-3" "grid" "grid-cols-2" "border" "border-base-300" "text-center"
                    "text-[0.75rem]" "font-medium"
                    "tracking-[0.04em]" "text-trading-muted"]
            :data-role "portfolio-optimizer-universe-source-strip"}
      [:button {:type "button"
                :class (cond-> ["border-r" "border-base-300" "px-2" "py-2"
                                "hover:text-warning"]
                         holdings-active?
                         (conj "border-warning/60" "bg-warning/10" "text-warning"))
                :data-role "portfolio-optimizer-universe-use-current"
                :data-active (when holdings-active? "true")
                :on {:click [[:actions/set-portfolio-optimizer-universe-from-current]]}}
       (if holdings-active? "My holdings" "Load my holdings")
       [:span {:class ["sr-only"]}
        (if holdings-active?
          "Refresh the universe from your current holdings"
          "Load my current holdings into the universe")]]
      [:span {:class (cond-> ["px-2" "py-2"]
                       (and universe-source (not holdings-source?))
                       (conj "bg-warning/10" "text-warning"))
              :data-role "portfolio-optimizer-universe-source-custom"
              :data-active (when (and universe-source (not holdings-source?)) "true")}
       "Custom"]]
     (universe-source-line universe-source (count universe))
     [:div {:class ["sr-only"]} "Manual Add"]
     [:div {:class ["mt-3" "relative"]}
      [:div {:class ["optimizer-universe-search-shell"
                     "flex" "items-center" "gap-1.5" "border" "px-2"
                     "portfolio-optimizer-universe-search-shell"
                     (if searching?
                       "border-warning/70"
                       "border-base-300")
                     "bg-transparent"]
              :data-role "portfolio-optimizer-universe-search-shell"
              :data-searching (when searching? "true")}
       [:span {:class ["optimizer-universe-search-affordance"
                       "portfolio-optimizer-universe-search-affordance"
                       "font-mono" "text-[0.75rem]" "text-trading-muted"]
               :data-role "portfolio-optimizer-universe-search-icon"}
        "⌕"]
       [:input {:type "search"
                :class (into input-class ["optimizer-universe-search-field"
                                          "portfolio-optimizer-universe-search-field"
                                          "border-0" "bg-transparent" "px-0" "focus:border-0"])
                :placeholder "Search ticker, name, or vault (e.g. TIA, AVAX, Solana, HLP...)"
                :data-role "portfolio-optimizer-universe-search-input"
                :aria-controls "portfolio-optimizer-universe-search-results"
                :aria-activedescendant (when (and searching? (seq markets))
                                         (str "portfolio-optimizer-universe-candidate-" active-index))
                :value search-query
                :on {:input [[:actions/set-portfolio-optimizer-universe-search-query
                              [:event.target/value]]]
                     :keydown [[:actions/handle-portfolio-optimizer-universe-search-keydown
                                [:event/key]
                                market-keys]]}}]
       (when searching?
         [:button {:type "button"
                   :class ["optimizer-universe-search-affordance"
                           "optimizer-universe-search-clear"
                           "portfolio-optimizer-universe-search-affordance"
                           "font-mono" "text-xs" "text-trading-muted" "hover:text-warning"]
                   :aria-label "Clear universe search"
                   :data-role "portfolio-optimizer-universe-search-clear"
                   :on {:click [[:actions/set-portfolio-optimizer-universe-search-query ""]]}}
          "x"])
       [:span {:class ["optimizer-universe-search-add-hint"
                       "portfolio-optimizer-universe-search-add-hint"
                       "border" "border-base-300"
                       "font-mono" "text-[0.625rem]" "text-trading-muted"]
               :data-role "portfolio-optimizer-universe-search-add-hint"}
        "↵ add"]]
      (when searching?
        (if (seq candidate-rows)
          (into [:div {:class ["mt-1" "border" "border-base-300" "bg-base-200/80"
                               "shadow-[0_12px_32px_rgba(0,0,0,0.45)]"]
                       :id "portfolio-optimizer-universe-search-results"
                       :role "listbox"
                       :data-role "portfolio-optimizer-universe-search-results"}]
                (cons (candidate-table-header)
                      (map-indexed (fn [idx row] (market-row row idx)) candidate-rows)))
          [:p {:class ["mt-1" "border" "border-base-300" "bg-base-200/70" "p-2"
                       "text-xs" "text-trading-muted"]
               :data-role "portfolio-optimizer-universe-search-results-empty"}
           "No matching unused instruments found."]))]
     (selected-table selected-rows universe holdings-loading?)
     (setup-history-assumptions/history-assumptions-section
      {:state state
       :draft draft
       :readiness readiness
       :history-load-state history-load-state})
     ;; Prose help, not log output: sans-serif — monospace stays reserved for
     ;; numbers and identifiers per the setup type ladder.
     [:div {:class ["mt-2" "text-[0.6875rem]" "leading-[1.5]"
                    "text-trading-muted/70"]}
      "Search adds tradeable spot, perp, or vault return legs. Symbols with limited history use stabilized covariance with a longer pull toward the market reference."
      [:span {:class ["sr-only"]}
       "History starts loading after assets are included."]]])))
