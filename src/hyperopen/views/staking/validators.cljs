(ns hyperopen.views.staking.validators
  (:require [hyperopen.views.staking.shared :as shared]))

(defn- sortable-validator-header
  [label column validator-sort]
  (let [active? (= column (:column validator-sort))
        direction (:direction validator-sort)
        indicator (if active?
                    (if (= :asc direction) "↑" "↓")
                    "↕")]
    [:th {:class ["px-3" "py-2" "text-left" "font-normal"]}
     [:button {:type "button"
               :class (into ["inline-flex"
                             "items-center"
                             "gap-1.5"
                             "text-xs"
                             "font-normal"
                             "transition-colors"
                             "focus:outline-none"
                             "focus:ring-0"
                             "focus:ring-offset-0"]
                            (if active?
                              ["text-ho-text"]
                              ["text-[#949e9c]" "hover:text-[#c5d0ce]"]))
               :data-role (str "staking-sort-header-" (name column))
               :on {:click [[:actions/set-staking-validator-sort column]]}}
      [:span label]
      [:span {:class ["num"
                      (if active?
                        "text-ho-accent-bright"
                        "text-[#5f6d70]")]}
       indicator]]]))

(defn- validator-description-cell
  [{:keys [description]}]
  (let [description-text (or description "--")]
    [:td {:class ["px-3" "py-1.5" "text-[#9aa3a4]"]}
     [:div {:class ["group" "relative" "max-w-[260px]"]}
      [:span {:class ["block" "truncate"]
              :title description-text}
       description-text]
      (when (seq description)
        [:div {:class ["pointer-events-none"
                       "absolute"
                       "left-0"
                       "bottom-full"
                       "z-[120]"
                       "mb-1"
                       "w-max"
                       "max-w-[520px]"
                       "rounded-lg"
                       "border"
                       "border-[#2b3b42]"
                       "bg-[#202b32]"
                       "px-3"
                       "py-2"
                       "text-xs"
                       "leading-tight"
                       "text-ho-text"
                       "whitespace-normal"
                       "opacity-0"
                       "transition-opacity"
                       "duration-150"
                       "group-hover:opacity-100"]
               :data-role "staking-validator-description-tooltip"}
         description])]]))

(defn- validator-row
  [{:keys [validator
           name
           description
           stake
           your-stake
           uptime-fraction
           predicted-apr
           status
           commission]}
   selected-validator]
  (let [selected? (and (seq validator)
                       (= validator selected-validator))]
    [:tr {:class (into ["border-b"
                        "border-ho-surface"
                        "text-xs"
                        "cursor-pointer"
                        "transition-colors"
                        "hover:bg-[#1d2a30]"]
                       (when selected?
                         ["bg-[#1a2c31]"]))
          :on {:click [[:actions/select-staking-validator validator]]}
          :data-role "staking-validator-row"}
     [:td {:class ["px-3" "py-1.5" "font-normal" "text-ho-text"]}
      name]
     (validator-description-cell {:description description})
     [:td {:class ["px-3" "py-1.5" "num" "text-ho-text"]} (shared/format-table-hype stake)]
     [:td {:class ["px-3" "py-1.5" "num" "text-ho-text"]}
      (if (pos? (or your-stake 0))
        (shared/format-table-hype your-stake)
        "-")]
     [:td {:class ["px-3" "py-1.5" "num" "text-ho-text"]} (shared/format-percent uptime-fraction)]
     [:td {:class ["px-3" "py-1.5" "num" "text-ho-text"]} (shared/format-percent predicted-apr)]
     [:td {:class ["px-3" "py-1.5"]} (shared/status-pill status)]
     [:td {:class ["px-3" "py-1.5" "num" "text-ho-text"]} (shared/format-percent commission)]]))

(defn- validator-pagination
  [{:keys [validator-page
           validator-page-count
           validator-show-all?
           validators-total-count
           validator-page-range-start
           validator-page-range-end]}]
  (when (pos? validators-total-count)
    (let [can-prev? (pos? validator-page)
          can-next? (< validator-page (dec validator-page-count))
          prev-page (max 0 (dec validator-page))
          next-page (min (dec validator-page-count) (inc validator-page))
          toggle-label (if validator-show-all?
                         "Paginated View"
                         "View All")
          toggle-target (not validator-show-all?)]
      [:div {:class ["flex"
                     "items-center"
                     "justify-between"
                     "px-3"
                     "py-2"
                     "text-sm"
                     "border-t"
                     "border-ho-surface"
                     "bg-ho-bg"]
             :data-role "staking-validator-pagination"}
       [:button {:type "button"
                 :class ["text-[#5ecfc1]"
                         "transition-colors"
                         "hover:text-[#7ee7d8]"
                         "focus:outline-none"
                         "focus:ring-0"
                         "focus:ring-offset-0"]
                 :data-role "staking-validator-toggle-view-all"
                 :on {:click [[:actions/set-staking-validator-show-all toggle-target]]}}
        toggle-label]
       [:div {:class ["flex" "items-center" "gap-2" "text-ho-text"]}
        [:span {:class ["num" "text-sm" "text-ho-text"]}
         (str validator-page-range-start "-" validator-page-range-end " of " validators-total-count)]
        [:button {:type "button"
                  :class (into ["h-7"
                                "min-w-7"
                                "px-2"
                                "inline-flex"
                                "items-center"
                                "justify-center"
                                "rounded-md"
                                "border"
                                "transition-colors"
                                "focus:outline-none"
                                "focus:ring-0"
                                "focus:ring-offset-0"]
                               (if can-prev?
                                 ["border-[#2a3b42]" "text-ho-text" "hover:bg-[#14252e]"]
                                 ["border-ho-surface" "text-[#4f5a5d]" "cursor-not-allowed"]))
                  :disabled (not can-prev?)
                  :data-role "staking-validator-page-prev"
                  :on {:click [[:actions/set-staking-validator-page prev-page]]}}
         "Prev"]
        [:button {:type "button"
                  :class (into ["h-7"
                                "min-w-7"
                                "px-2"
                                "inline-flex"
                                "items-center"
                                "justify-center"
                                "rounded-md"
                                "border"
                                "transition-colors"
                                "focus:outline-none"
                                "focus:ring-0"
                                "focus:ring-offset-0"]
                               (if can-next?
                                 ["border-[#2a3b42]" "text-ho-text" "hover:bg-[#14252e]"]
                                 ["border-ho-surface" "text-[#4f5a5d]" "cursor-not-allowed"]))
                  :disabled (not can-next?)
                  :data-role "staking-validator-page-next"
                  :on {:click [[:actions/set-staking-validator-page next-page]]}}
         "Next"]]])))

(defn validator-timeframe-menu
  [selected-timeframe timeframe-options open?]
  (let [selected-label (or (some (fn [{:keys [value label]}]
                                   (when (= value selected-timeframe)
                                     label))
                                 timeframe-options)
                           "7D")]
    [:div {:class ["relative"]
           :data-role "staking-timeframe-toggle"}
     (when open?
       [:button {:type "button"
                 :class ["fixed" "inset-0" "z-[9]" "cursor-default"]
                 :aria-label "Close staking timeframe menu"
                 :on {:click [[:actions/close-staking-validator-timeframe-menu]]}}])
     [:button {:type "button"
               :class (into ["relative"
                             "z-[10]"
                             "h-8"
                             "min-w-[58px]"
                             "inline-flex"
                             "items-center"
                             "justify-between"
                             "gap-1.5"
                             "rounded-lg"
                             "border"
                             "border-ho-surface"
                             "bg-ho-bg"
                             "px-2.5"
                             "text-xs"
                             "font-normal"
                             "text-ho-text"
                             "transition-colors"
                             "hover:border-ho-text-dim"]
                            shared/neutral-input-focus-classes)
               :aria-expanded (boolean open?)
               :data-role "staking-timeframe-menu-trigger"
               :on {:click [[:actions/toggle-staking-validator-timeframe-menu]]}}
      [:span selected-label]
      [:svg {:class (into ["h-3.5"
                           "w-3.5"
                           "text-[#949e9c]"
                           "transition-transform"
                           "duration-150"]
                          (when open?
                            ["rotate-180"]))
             :fill "none"
             :stroke "currentColor"
             :viewBox "0 0 24 24"}
       [:path {:stroke-linecap "round"
               :stroke-linejoin "round"
               :stroke-width 2
               :d "M6 9l6 6 6-6"}]]]
     [:div {:class (into ["absolute"
                          "right-0"
                          "top-[calc(100%+6px)]"
                          "z-[11]"
                          "min-w-[72px]"
                          "overflow-hidden"
                          "rounded-[10px]"
                          "border"
                          "border-ho-surface"
                          "bg-ho-bg"
                          "shadow-[0_14px_28px_rgba(0,0,0,0.45)]"
                          "transition-[opacity,transform]"
                          "duration-100"]
                         (if open?
                           ["opacity-100" "translate-y-0" "pointer-events-auto"]
                           ["opacity-0" "-translate-y-1" "pointer-events-none"]))
            :data-role "staking-timeframe-menu"}
      (for [{:keys [value label]} timeframe-options]
        ^{:key (str "staking-timeframe-option-" (name value))}
        [:button {:type "button"
                  :class (into ["block"
                                "w-full"
                                "px-3"
                                "py-2"
                                "text-left"
                                "text-xs"
                                "font-normal"
                                "transition-colors"]
                               (if (= value selected-timeframe)
                                 ["bg-[#122c37]" "text-ho-text"]
                                 ["text-[#c8d5d7]" "hover:bg-[#112733]"]))
                  :data-role (str "staking-timeframe-option-" (name value))
                  :on {:click [[:actions/set-staking-validator-timeframe value]]}}
         label])]]))

(defn validator-performance-panel
  [{:keys [loading?
           validators
           selected-validator
           validator-page
           validator-show-all?
           validator-page-count
           validators-total-count
           validator-page-range-start
           validator-page-range-end
           validator-sort]}]
  [:div {:class ["flex" "min-h-0" "flex-col"]
         :style {:max-height "52vh"}
         :data-role "staking-validator-panel"}
   [:div {:class ["min-h-0" "flex-1" "overflow-x-auto" "overflow-y-auto"]
          :data-role "staking-validator-table-scroll-region"}
    [:table {:class ["min-w-full" "bg-ho-bg"]
             :data-role "staking-validator-table"}
     [:thead
      [:tr {:class ["border-b" "border-ho-surface" "text-xs" "text-[#949e9c]"]}
       (sortable-validator-header "Name" :name validator-sort)
       (sortable-validator-header "Description" :description validator-sort)
       (sortable-validator-header "Stake" :stake validator-sort)
       (sortable-validator-header "Your Stake" :your-stake validator-sort)
       (sortable-validator-header "Uptime" :uptime validator-sort)
       (sortable-validator-header "Est. APR" :apr validator-sort)
       (sortable-validator-header "Status" :status validator-sort)
       (sortable-validator-header "Commission" :commission validator-sort)]]
     [:tbody
      (if (seq validators)
        (for [row validators]
          ^{:key (:validator row)}
          (validator-row row selected-validator))
        [:tr
         [:td {:col-span 8
               :class ["px-3" "py-6" "text-center" "text-sm" "text-[#949e9c]"]}
          (if loading?
            "Loading validators..."
            "No validator data available.")]])]]]
   (validator-pagination {:validator-page validator-page
                          :validator-show-all? validator-show-all?
                          :validator-page-count validator-page-count
                          :validators-total-count validators-total-count
                          :validator-page-range-start validator-page-range-start
                          :validator-page-range-end validator-page-range-end})])
