(ns hyperopen.views.portfolio.optimize.setup-universe
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]))

(def ^:private eyebrow-class
  ["font-mono" "text-[0.625rem]" "font-semibold" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"])

(def ^:private section-title-class
  ["text-[0.6875rem]" "font-semibold" "uppercase" "tracking-[0.08em]" "text-trading-text"])

(def ^:private input-class
  ["w-full" "border" "border-base-300" "bg-base-100/80" "px-2" "py-1.5"
   "font-mono" "text-[0.6875rem]" "font-medium" "outline-none"
   "transition-shadow" "focus:border-warning/70"
   "focus:shadow-[0_0_0_1px_rgba(212,181,88,0.75)]"])

(defn- tag
  ([label tone]
   (tag label tone nil))
  ([label tone extra-class]
   [:span {:class (cond-> ["optimizer-chip" "border" "px-1.5" "py-[1px]" "font-mono"
                           "text-[0.53125rem]" "font-semibold" "uppercase"
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
                              "font-mono" "text-[0.6rem]" "font-semibold"
                              "uppercase" "leading-none" "transition-colors"]
                       selected? (conj "bg-success/70" "text-base-100")
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
  [:span {:class ["inline-flex" "items-center" "justify-end"]
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

(defn- selected-row
  [{:keys [instrument-id
           market-type
           primary-label
           secondary-label
           history-label
           history-tone
           position-side
           short-selectable?]}]
    [:div {:class ["optimizer-universe-row"
                   "grid" "grid-cols-[18px_minmax(0,1fr)_42px_72px_52px_20px]"
                   "items-center" "gap-2" "border-b" "border-base-300"
                   "px-2" "py-1.5" "last:border-b-0" "hover:bg-base-200/30"]
           :data-role (str "portfolio-optimizer-universe-selected-row-" instrument-id)}
     [:span {:class ["text-warning"]} "☑"]
     [:span {:class ["min-w-0"]}
      [:span {:class ["block" "truncate" "font-mono" "text-[0.6875rem]" "font-semibold"]}
       primary-label]
      [:span {:class ["block" "truncate" "text-[0.65625rem]" "text-trading-muted"]}
       secondary-label]]
     [:span {:class ["min-w-0"]} (market-type-tags market-type)]
     [:span {:class ["min-w-0"]} (tag history-label history-tone)]
     (side-control instrument-id position-side short-selectable?)
     [:span {:class ["text-right"]}
      [:button {:type "button"
                :class ["font-mono" "text-[0.6875rem]" "text-trading-muted" "hover:text-warning"]
                :aria-label (remove-aria-label primary-label market-type instrument-id)
                :data-role (str "portfolio-optimizer-universe-remove-" instrument-id)
                :on {:click [[:actions/remove-portfolio-optimizer-universe-instrument
                               instrument-id]]}}
       "x"]]])

(defn- market-row
  [{:keys [market-key market-type active? label name]} idx]
    [:div {:class ["optimizer-universe-candidate-row"
                   "grid" "grid-cols-[66px_minmax(0,1fr)_58px_44px]"
                   "items-center" "gap-2" "border-b" "border-base-300" "cursor-pointer" "px-2"
                   "py-1.5" "last:border-b-0" "hover:bg-base-200/30"]
           :data-role (str "portfolio-optimizer-universe-candidate-row-" market-key)
           :id (str "portfolio-optimizer-universe-candidate-" idx)
           :role "option"
           :aria-selected (if active? "true" "false")
           :data-active (when active? "true")
           :on {:click [[:actions/add-portfolio-optimizer-universe-instrument market-key]]}}
     [:span {:class ["truncate" "font-mono" "text-[0.6875rem]" "font-semibold"]}
      label]
     [:span {:class ["truncate" "text-[0.6875rem]" "text-trading-muted"]}
      name]
     (market-type-tags market-type)
     [:button {:type "button"
               :class ["optimizer-universe-add-button"
                       "text-right" "font-mono" "text-[0.65625rem]" "font-semibold"
                       "text-warning" "hover:text-warning"]
               :data-role (str "portfolio-optimizer-universe-add-" market-key)
               :on {:click [[:actions/add-portfolio-optimizer-universe-instrument market-key]]}}
      "+ add"]])

(defn- selected-table-header
  []
  [:div {:class ["grid" "grid-cols-[18px_minmax(0,1fr)_42px_72px_52px_20px]"
                 "items-center" "gap-2" "border-b" "border-base-300"
                 "bg-base-200/40" "px-2" "py-1.5" "font-mono"
                 "text-[0.55rem]" "font-semibold" "uppercase"
                 "tracking-[0.12em]" "text-trading-muted/70"]
         :data-role "portfolio-optimizer-universe-selected-header"}
   [:span ""]
   [:span "Asset"]
   [:span "Type"]
   [:span "History"]
   [:span {:class ["text-right"]} "Side"]
   [:span {:class ["sr-only"]} "Remove"]])

(defn- candidate-table-header
  []
  [:div {:class ["grid" "grid-cols-[66px_minmax(0,1fr)_58px_44px]"
                 "items-center" "gap-2" "border-b" "border-base-300"
                 "bg-base-200/40" "px-2" "py-1.5" "font-mono"
                 "text-[0.55rem]" "font-semibold" "uppercase"
                 "tracking-[0.12em]" "text-trading-muted/70"]
         :data-role "portfolio-optimizer-universe-candidate-header"
         :role "presentation"}
   [:span "Asset"]
   [:span "Name"]
   [:span "Type"]
   [:span {:class ["sr-only"]} "Add"]])

(defn- selected-table
  [selected-rows universe]
  [:div {:class ["mt-2" "border" "border-base-300" "bg-base-100/50"]}
   [:div {:class ["flex" "items-center" "border-b" "border-base-300" "px-2" "py-1.5"]}
    [:span {:class ["font-mono" "text-[0.6rem]" "uppercase" "tracking-[0.12em]"
                    "text-trading-muted"]}
     (str (count universe) " included")]
    [:span {:class ["ml-auto" "font-mono" "text-[0.6rem]" "text-trading-muted/70"]}
     "cap: 25 assets"]]
   (if (seq universe)
     (into [:div {:class ["text-xs"]}]
           (cons (selected-table-header)
                 (map selected-row selected-rows)))
     [:p {:class ["px-2" "py-3" "text-xs" "text-trading-muted"]}
      "No instruments selected yet."])])

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
                 market-keys]} (optimizer-view-model/universe-section-model state draft opts)]
    [:section {:class ["optimizer-universe-panel" "optimizer-setup-panel"
                       "border" "border-base-300" "bg-base-100/90" "p-3" "leading-4"]
               :data-role "portfolio-optimizer-universe-panel"}
     [:div {:class ["flex" "items-center" "justify-between" "gap-3" "border-b"
                    "border-base-300" "pb-2"]}
      [:p {:class section-title-class}
       [:span {:class ["mr-2" "font-mono" "text-trading-muted/70"]} "01"]
       "Universe"]
      [:span {:class ["font-mono" "text-[0.65625rem]" "uppercase" "tracking-[0.08em]"
                      "text-trading-muted/70"]}
       (str (count universe) " included")]]
     [:div {:class ["mt-3" "grid" "grid-cols-2" "border" "border-base-300" "text-center"
                    "text-[0.65625rem]" "font-medium" "uppercase"
                    "tracking-[0.04em]" "text-trading-muted"]}
      [:span {:class ["border-r" "border-warning/60" "bg-warning/10" "px-2" "py-2" "text-warning"]}
       "Custom"]
      [:button {:type "button"
                :class ["px-2" "py-2" "uppercase" "hover:text-warning"]
                :data-role "portfolio-optimizer-universe-use-current"
                :on {:click [[:actions/set-portfolio-optimizer-universe-from-current]]}}
       "From holdings"
       [:span {:class ["sr-only"]} "Use Current Holdings"]]]
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
                       "font-mono" "text-[0.65rem]" "text-trading-muted"]
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
                       "font-mono" "text-[0.55rem]" "text-trading-muted"]
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
     (selected-table selected-rows universe)
     [:div {:class ["mt-2" "font-mono" "text-[0.58rem]" "leading-5"
                    "text-trading-muted/70"]}
      "Search adds tradeable spot, perp, or vault return legs. Symbols with limited history use stabilized covariance with a longer pull toward the market reference."
      [:span {:class ["sr-only"]}
       "History starts loading after assets are included."]]])))
