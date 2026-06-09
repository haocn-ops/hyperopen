(ns hyperopen.views.portfolio.optimize.target-exposure-table
  (:require [clojure.string :as str]
            [hyperopen.platform :as platform]
            [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.portfolio.optimizer.application.view-model.rebalance :as rebalance-view-model]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.views.asset-icon :as asset-icon]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(def ^:private add-asset-input-class
  ["w-full" "border" "border-base-300" "bg-base-100/80" "px-2" "py-1.5"
   "font-mono" "text-[0.6875rem]" "font-medium" "outline-none"
   "transition-shadow" "focus:border-warning/70"])

(defn- search-input-mount-focus!
  [{:keys [:replicant/life-cycle :replicant/node]}]
  (when (= :replicant.life-cycle/mount life-cycle)
    (platform/queue-microtask!
     (fn []
       (when (and node
                  (.-isConnected node)
                  (fn? (.-focus node)))
         (.focus node)
         (when (fn? (.-select node))
           (.select node)))))))

(defn- format-delta-pct
  [value]
  (opt-format/format-pct-delta value
                               {:minimum-fraction-digits 1
                                :maximum-fraction-digits 1
                                :suffix "%"}))

(defn- format-compact-usdc
  [value]
  (if (opt-format/finite-number? value)
    (let [abs-value (js/Math.abs value)
          sign (cond
                 (pos? value) "+"
                 (neg? value) "-"
                 :else "")]
      (cond
        (>= abs-value 1000000)
        (str sign "$"
             (.toLocaleString (/ abs-value 1000000)
                               "en-US"
                               #js {:maximumFractionDigits 1})
             "m")

        (>= abs-value 1000)
        (str sign "$"
             (.toLocaleString (/ abs-value 1000)
                               "en-US"
                               #js {:maximumFractionDigits 0})
             "k")

        :else
        (str sign "$"
             (.toLocaleString abs-value
                               "en-US"
                               #js {:maximumFractionDigits 0}))))
    "N/A"))

(defn- data-role-token
  [value]
  (-> (str value)
      (str/replace #"[^A-Za-z0-9_-]+" "-")
      (str/replace #"(^-+|-+$)" "")))

(defn- allocation-asset-icon
  [asset {:keys [icon-kind market]}]
  (let [vault? (= :vault icon-kind)
        icon-url (when-not vault?
                   (asset-icon/market-icon-url market))]
    [:span {:class ["inline-flex" "h-4" "w-4" "shrink-0" "items-center" "justify-center"]
            :data-role (str "portfolio-optimizer-target-exposure-icon-"
                            (data-role-token asset))
            :aria-hidden true}
     (if vault?
       [:span {:class ["block" "h-2.5" "w-2.5" "rotate-45" "border" "border-cyan-300/70" "bg-cyan-300/15" "shadow-[0_0_6px_rgba(34,211,238,0.24)]"]
               :data-role (str "portfolio-optimizer-target-exposure-vault-diamond-"
                               (data-role-token asset))}]
       (if (seq icon-url)
         [:img {:class ["block" "h-4" "w-4" "rounded-full" "object-contain"]
                :src icon-url
                :alt ""
                :data-role (str "portfolio-optimizer-target-exposure-asset-icon-img-"
                                (data-role-token asset))}]
         [:span {:class ["block" "h-3" "w-3" "rounded-full" "border" "border-base-300" "bg-base-200"]}]))]))

(defn- side-button
  [scope role-id instrument-id selected-side short-selectable? side]
  (let [selected? (= selected-side side)
        enabled? (and instrument-id
                      (or (= :long side)
                          short-selectable?))
        token (data-role-token role-id)
        label-id (or instrument-id role-id "allocation group")]
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
              :aria-label (str "Set " label-id " "
                               (name side))
              :aria-pressed (if selected? "true" "false")
              :data-role (str scope
                              "-side-"
                              (name side)
                              "-"
                              token)
              :data-selected (when selected? "true")
              :disabled (when-not enabled? true)
              :on (when enabled?
                    {:click [[:actions/set-portfolio-optimizer-universe-instrument-side-and-run
                              instrument-id
                              side]]})}
     (case side
       :short "S"
       "L")]))

(defn- side-control
  [scope {:keys [asset instrument-id position-side short-selectable?]}]
  (let [role-id (or instrument-id asset "allocation-group")]
    [:span {:class ["inline-flex" "items-center" "justify-end"]
            :data-role (str scope
                            "-side-control-"
                            (data-role-token role-id))}
     (side-button scope role-id instrument-id position-side short-selectable? :long)
     (side-button scope role-id instrument-id position-side short-selectable? :short)]))

(defn- exposure-row
  [{:keys [idx binding? hidden? current-sign target-sign leg-label current-weight
           target-weight delta delta-notional excluded? status-label instrument-id asset]
     :as row}]
  [:tr {:class (cond-> ["optimizer-target-exposure-row" "optimizer-exposure-row"]
                binding? (conj "bg-warning/10")
                excluded? (conj "optimizer-target-exposure-row--excluded")
                hidden? (conj "hidden"))
        :data-role (str "portfolio-optimizer-target-exposure-row-" idx)
        :data-binding (when binding? "true")
        :data-excluded (when excluded? "true")
        :data-current-sign current-sign
        :data-target-sign target-sign}
   [:td {:class ["text-trading-muted"]} ""]
   [:td {:class ["pl-8" "text-trading-muted"]}
    [:span {:class (cond-> ["inline-flex" "items-center" "gap-2"]
                     excluded? (conj "line-through" "decoration-trading-muted/70"))}
     leg-label]
    (when excluded?
      [:span {:class ["optimizer-target-exposure-status" "ml-2"]}
       status-label])]
   [:td {:class ["px-2" "text-right"]}
    (side-control "portfolio-optimizer-target-exposure-row" row)]
   [:td {:class (cond-> ["font-mono" "text-right" "tabular-nums"]
                  excluded? (conj "line-through" "decoration-trading-muted/70"))}
    (opt-format/format-pct current-weight {:minimum-fraction-digits 1 :maximum-fraction-digits 1})]
   [:td {:class (cond-> ["font-mono" "text-right" "tabular-nums"]
                  excluded? (conj "line-through" "decoration-trading-muted/70"))}
    (opt-format/format-pct target-weight (if excluded?
                                           {:minimum-fraction-digits 2
                                            :maximum-fraction-digits 2}
                                           {:minimum-fraction-digits 1
                                            :maximum-fraction-digits 1}))]
   [:td {:class [(if (neg? delta) "text-trading-red" "text-trading-green")
                 "font-mono" "text-right" "tabular-nums"]}
    (format-delta-pct delta)]
   [:td {:class [(if (neg? delta) "text-trading-red" "text-trading-green")
                 "font-mono" "text-right" "tabular-nums"]}
    (format-compact-usdc delta-notional)]])

(defn- exposure-group-row
  [{:keys [asset current-weight target-weight delta delta-notional binding?
           expandable? target-sign excluded? status-label instrument-id] :as group}]
  (let [token (data-role-token (or instrument-id asset))]
    [:tr {:class (cond-> ["optimizer-target-exposure-asset" "optimizer-exposure-row" "cursor-pointer"]
                  excluded? (conj "optimizer-target-exposure-row--excluded"))
        :data-role (str "portfolio-optimizer-target-exposure-asset-"
                        (data-role-token asset))
        :data-excluded (when excluded? "true")
        :data-target-sign target-sign}
   [:td {:class ["w-8" "font-mono" "text-trading-muted/70"]}
    (if instrument-id
      [:button {:type "button"
                :class ["optimizer-target-exposure-exclude-button"]
                :aria-label (str (if excluded? "Include " "Exclude ")
                                 asset
                                 " and rerun")
                :title (str (if excluded? "Include " "Exclude ") asset)
                :data-role (str "portfolio-optimizer-target-exposure-exclude-"
                                token)
                :data-excluded (when excluded? "true")
                :on {:click [[:actions/toggle-portfolio-optimizer-universe-instrument-exclusion-and-run
                              instrument-id]]}}
       "⌀"]
      (when expandable? "▾"))]
   [:td {:class ["font-mono" "font-semibold" "text-trading-text"]}
    [:span {:class ["inline-flex" "min-w-0" "items-center" "gap-2"]}
     (allocation-asset-icon asset group)
     [:span {:class (cond-> []
                      excluded? (conj "line-through" "decoration-trading-muted/70"))
             :data-role (str "portfolio-optimizer-target-exposure-group-"
                             (data-role-token asset))}
      asset]
     (when excluded?
       [:span {:class ["optimizer-target-exposure-excluded-tag"]}
        "excluded"])]
    (when binding?
      [:span {:class ["ml-2" "border" "border-warning/50" "px-1.5" "py-0.5"
                      "font-mono" "text-[0.5rem]" "font-semibold" "uppercase"
                      "tracking-[0.08em]" "text-warning"]}
       "capped"])
    (when excluded?
      [:span {:class ["optimizer-target-exposure-status" "ml-2"]}
       status-label])]
   [:td {:class ["px-2" "text-right"]}
    (side-control "portfolio-optimizer-target-exposure" group)]
   [:td {:class (cond-> ["font-mono" "text-right" "font-semibold" "tabular-nums"]
                  excluded? (conj "line-through" "decoration-trading-muted/70"))}
    (opt-format/format-pct current-weight {:minimum-fraction-digits 1 :maximum-fraction-digits 1})]
   [:td {:class (cond-> ["font-mono" "text-right" "font-semibold" "tabular-nums"]
                  excluded? (conj "line-through" "decoration-trading-muted/70"))}
    (opt-format/format-pct target-weight (if excluded?
                                           {:minimum-fraction-digits 2
                                            :maximum-fraction-digits 2}
                                           {:minimum-fraction-digits 1
                                            :maximum-fraction-digits 1}))]
   [:td {:class [(if (neg? delta) "text-trading-red" "text-trading-green")
                 "font-mono" "text-right" "font-semibold" "tabular-nums"]}
    (format-delta-pct delta)]
   [:td {:class [(if (neg? delta) "text-trading-red" "text-trading-green")
                 "font-mono" "text-right" "font-semibold" "tabular-nums"]}
    (format-compact-usdc delta-notional)]]))

(defn- candidate-row
  [{:keys [market-key label name]} idx active-index]
  [:div {:class ["optimizer-draft-add-asset-candidate-row"
                 "grid" "grid-cols-[58px_minmax(0,1fr)_44px]"
                 "items-center" "gap-2" "border-b" "border-base-300" "px-2"
                 "py-1.5" "last:border-b-0" "hover:bg-base-200/40"]
         :data-role (str "portfolio-optimizer-draft-add-asset-candidate-row-"
                         market-key)
         :id (str "portfolio-optimizer-draft-add-asset-candidate-" idx)
         :role "option"
         :aria-selected (if (= idx active-index) "true" "false")
         :data-active (when (= idx active-index) "true")
         :on {:click [[:actions/add-portfolio-optimizer-universe-instrument-and-run
                       market-key]]}}
   [:span {:class ["truncate" "font-mono" "text-[0.6875rem]" "font-semibold"]}
    label]
   [:span {:class ["truncate" "text-[0.6875rem]" "text-trading-muted"]}
    name]
   [:button {:type "button"
             :class ["text-right" "font-mono" "text-[0.65625rem]" "font-semibold"
                     "text-warning" "hover:text-warning"]
             :data-role (str "portfolio-optimizer-draft-add-asset-add-"
                             market-key)
             :on {:click [[:actions/add-portfolio-optimizer-universe-instrument-and-run
                           market-key]]}}
    "+ add"]])

(defn- candidate-table-header
  []
  [:div {:class ["grid" "grid-cols-[58px_minmax(0,1fr)_44px]"
                 "items-center" "gap-2" "border-b" "border-base-300"
                 "bg-base-200/40" "px-2" "py-1.5" "font-mono"
                 "text-[0.55rem]" "font-semibold" "uppercase"
                 "tracking-[0.12em]" "text-trading-muted/70"]
         :data-role "portfolio-optimizer-draft-add-asset-candidate-header"
         :role "presentation"}
   [:span "Asset"]
   [:span "Name"]
   [:span {:class ["sr-only"]} "Add"]])

(defn- add-asset-popover
  [state draft]
  (let [{:keys [search-query markets candidate-rows active-index market-keys]}
        (optimizer-view-model/universe-panel-model state draft)
        searching? (seq search-query)]
    [:div {:class ["optimizer-draft-add-asset-popover"
                   "optimizer-draft-add-asset-popover--dark"
                   "fixed" "inset-x-2" "top-16" "z-30" "max-h-[calc(100vh-5rem)]"
                   "overflow-hidden" "border" "border-base-300" "p-3"
                   "shadow-[0_12px_32px_rgba(0,0,0,0.45)]"
                   "md:absolute" "md:inset-auto" "md:right-0" "md:top-[calc(100%+4px)]"
                   "md:w-[380px]" "md:max-h-[360px]"]
           :data-role "portfolio-optimizer-draft-add-asset-popover"}
     [:div {:class ["flex" "items-start" "justify-between" "gap-3"]}
      [:div
       [:p {:class ["font-mono" "text-[0.625rem]" "font-semibold" "uppercase" "tracking-[0.08em]"
                    "text-trading-muted/70"]}
        "Add to universe"]
       [:p {:class ["mt-1" "text-[0.6875rem]" "font-semibold" "uppercase" "tracking-[0.08em]"
                    "text-trading-text"]}
        "Search a tradable asset"]
       [:p {:class ["mt-1" "text-[0.6875rem]" "leading-4" "text-trading-muted"]}
        "Adds to the scenario's universe and re-solves. Targets and frontier will refresh."]]
      [:button {:type "button"
                :class ["font-mono" "text-xs" "text-trading-muted" "hover:text-warning"]
                :aria-label "Close add asset"
                :data-role "portfolio-optimizer-draft-add-asset-close"
                :on {:click [[:actions/set-portfolio-optimizer-draft-add-asset-open false]]}}
       "x"]]
     [:div {:class ["optimizer-universe-search-shell"
                    "mt-3" "flex" "items-center" "gap-1.5" "border" "px-2"
                    "portfolio-optimizer-universe-search-shell"
                    (if searching?
                      "border-warning/70"
                      "border-base-300")
                    "bg-transparent"]
            :data-role "portfolio-optimizer-draft-add-asset-search-shell"
            :data-searching (when searching? "true")}
      [:span {:class ["optimizer-universe-search-affordance"
                      "portfolio-optimizer-universe-search-affordance"
                      "font-mono" "text-[0.65rem]" "text-trading-muted"]
              :data-role "portfolio-optimizer-draft-add-asset-search-icon"}
       "⌕"]
      [:input {:type "search"
               :class (into add-asset-input-class ["optimizer-universe-search-field"
                                                   "portfolio-optimizer-universe-search-field"
                                                   "border-0" "bg-transparent" "px-0"
                                                   "focus:border-0"])
               :placeholder "Ticker or name (e.g. TIA, Injective)..."
               :data-role "portfolio-optimizer-draft-add-asset-search-input"
               :aria-controls "portfolio-optimizer-draft-add-asset-search-results"
               :aria-activedescendant (when (seq markets)
                                        (str "portfolio-optimizer-draft-add-asset-candidate-"
                                             active-index))
               :value search-query
               :replicant/on-render search-input-mount-focus!
               :on {:input [[:actions/set-portfolio-optimizer-universe-search-query
                             [:event.target/value]]]
                    :keydown [[:actions/handle-portfolio-optimizer-draft-add-asset-keydown
                               [:event/key]
                               market-keys]]}}]]
     (if (seq candidate-rows)
       (into [:div {:class ["optimizer-draft-add-asset-results"
                            "mt-2" "max-h-[260px]" "overflow-auto" "border"
                            "border-base-300" "bg-base-200/80"]
                    :id "portfolio-optimizer-draft-add-asset-search-results"
                    :role "listbox"
                    :data-role "portfolio-optimizer-draft-add-asset-search-results"}]
             (cons (candidate-table-header)
                   (map-indexed (fn [idx row]
                                  (candidate-row row idx active-index))
                                candidate-rows)))
       [:p {:class ["mt-2" "border" "border-base-300" "bg-base-200/70" "p-2"
                    "text-xs" "text-trading-muted"]
            :data-role "portfolio-optimizer-draft-add-asset-search-results-empty"}
        "No matching unused instruments found."])]))

(defn- add-asset-controls
  [state draft]
  (when (and state draft)
    (let [open? (true? (get-in state contracts/ui-draft-add-asset-open-path))]
      [:div {:class ["relative" "flex" "items-center" "gap-2"]}
       [:button {:type "button"
                 :class ["border" "border-base-300" "bg-base-200/40" "px-2.5" "py-1"
                         "font-mono" "text-[0.65625rem]" "font-semibold"
                         "text-trading-text" "hover:border-warning/60"
                         "hover:text-warning"]
                 :data-role "portfolio-optimizer-draft-add-asset"
                 :aria-expanded (if open? "true" "false")
                 :on {:click [[:actions/set-portfolio-optimizer-draft-add-asset-open
                               true]]}}
        "+ Add asset"]
       (when open?
         (add-asset-popover state draft))])))

(defn target-exposure-table
  ([result]
   (target-exposure-table result nil))
  ([result {:keys [state draft]}]
  (let [{:keys [groups]} (rebalance-view-model/target-exposure-table-model result
                                                                          {:draft draft})]
    [:section {:class ["optimizer-target-exposure-table"
                       "min-h-0" "border-r" "border-base-300" "bg-base-100/95" "leading-4"]
               :data-role "portfolio-optimizer-target-exposure-table"}
     [:div {:class ["flex" "items-center" "justify-between" "gap-3" "border-b"
                    "border-base-300" "px-4" "py-3"]}
      [:div
       [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
        "Allocation"]
       [:p {:class ["mt-1" "text-xs" "text-trading-text"]}
        "By asset · click to expand legs"]]
      [:div {:class ["flex" "items-center" "gap-2"]}
       [:div {:class ["flex" "border" "border-base-300" "text-[0.62rem]" "font-semibold"
                      "uppercase" "tracking-[0.06em]"]}
        [:button {:type "button"
                  :class ["border-r" "border-base-300" "bg-base-200/60" "px-3" "py-1" "text-trading-text"]}
         "By Asset"]
        [:button {:type "button"
                  :class ["px-3" "py-1" "text-trading-muted"]}
         "By Leg"]]
       (add-asset-controls state draft)]]
     [:div {:class ["overflow-auto"]}
      [:table {:class ["w-full" "border-collapse" "text-[0.6875rem]"]}
       [:thead
        [:tr
         [:th {:class ["sticky" "top-0" "w-5" "border-b" "border-base-300" "bg-base-100" "px-2" "py-1.5" "text-left" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} ""]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-left" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Asset"]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-2" "py-2" "text-right" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Side"]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-right" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Current"]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-right" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Target"]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-right" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Δ %"]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-right" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Δ $"]]]
	       (into
	        [:tbody]
	        (mapcat
	         (fn [{:keys [rows] :as group}]
	            (concat
	             [(exposure-group-row group)]
	             (map exposure-row rows)))
	          groups))]]])))
