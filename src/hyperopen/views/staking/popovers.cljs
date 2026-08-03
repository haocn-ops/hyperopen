(ns hyperopen.views.staking.popovers
  (:require [clojure.string :as str]
            [hyperopen.views.ui.dialog-focus :as dialog-focus]
            [hyperopen.views.staking.shared :as shared]))
(def ^:private popover-margin-px
  12)
(def ^:private popover-gap-px
  10)
(def ^:private minimum-popover-anchor-height-px
  36)
(def ^:private popover-fallback-viewport-width
  1280)
(def ^:private popover-fallback-viewport-height
  800)
(def ^:private action-popover-trigger-data-role-by-kind
  {:transfer "staking-action-transfer-button"
   :unstake "staking-action-unstake-button"
   :stake "staking-action-stake-button"})
(def ^:private action-popover-focus-on-render (dialog-focus/dialog-focus-on-render))
(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))
(defn- anchor-number
  [anchor k default]
  (let [value (get anchor k)]
    (if (number? value)
      value
      default)))
(defn- query-element-anchor
  [data-role]
  (when (and (string? data-role)
             (some? js/document))
    (let [selector (str "[data-role=\"" data-role "\"]")
          element (.querySelector js/document selector)]
      (when (and element
                 (fn? (.-getBoundingClientRect element)))
        (let [rect (.getBoundingClientRect element)]
          {:left (.-left rect)
           :right (.-right rect)
           :top (.-top rect)
           :bottom (.-bottom rect)
           :width (.-width rect)
           :height (.-height rect)
           :viewport-width (some-> js/globalThis .-innerWidth)
           :viewport-height (some-> js/globalThis .-innerHeight)})))))
(defn- action-popover-anchor
  [kind stored-anchor]
  (let [data-role (get action-popover-trigger-data-role-by-kind kind)]
    (or (query-element-anchor data-role)
        stored-anchor)))
(defn- action-popover-layout-style
  [anchor kind]
  (let [anchor* (if (map? anchor) anchor {})
        estimated-height-px (if (= kind :transfer) 440 400)
        viewport-width (max 320
                            (anchor-number anchor* :viewport-width popover-fallback-viewport-width)
                            (+ (anchor-number anchor* :right 0) popover-margin-px))
        viewport-height (max 320
                             (anchor-number anchor* :viewport-height popover-fallback-viewport-height))
        available-width (max 0 (- viewport-width (* 2 popover-margin-px)))
        panel-width (min 560 available-width)
        anchor-right (anchor-number anchor*
                                    :right
                                    (- viewport-width popover-margin-px))
        anchor-top (anchor-number anchor* :top popover-margin-px)
        anchor-height (max minimum-popover-anchor-height-px
                           (anchor-number anchor* :height 0))
        anchor-bottom* (anchor-number anchor*
                                      :bottom
                                      (+ anchor-top anchor-height))
        anchor-bottom (if (>= (- anchor-bottom* anchor-top) 8)
                        anchor-bottom*
                        (+ anchor-top anchor-height))
        preferred-left (- anchor-right panel-width)
        left (clamp preferred-left
                    popover-margin-px
                    (- viewport-width panel-width popover-margin-px))
        preferred-top (+ anchor-bottom popover-gap-px)
        max-top (- viewport-height estimated-height-px popover-margin-px)
        top (if (> max-top popover-margin-px)
              (clamp preferred-top popover-margin-px max-top)
              popover-margin-px)]
    {:left (str left "px")
     :top (str top "px")
     :width (str panel-width "px")}))
(defn- popover-close-button []
  [:button {:type "button"
            :class ["absolute"
                    "right-5"
                    "top-4"
                    "inline-flex"
                    "h-8"
                    "w-8"
                    "items-center"
                    "justify-center"
                    "rounded-lg"
                    "text-ho-text"
                    "transition-colors"
                    "hover:bg-[#16313b]"
                    "focus:outline-none"
                    "focus:ring-0"
                    "focus:ring-offset-0"]
            :aria-label "Close staking action popover"
            :on {:click [[:actions/close-staking-action-popover]]}}
   "x"])
(defn- popover-amount-input
  [{:keys [input-id amount on-change on-max]}]
  [:div {:class ["relative"]}
   [:input {:id input-id
            :type "text"
            :inputmode "decimal"
            :placeholder "Amount"
            :value amount
            :class (into ["h-10"
                          "w-full"
                          "rounded-[10px]"
                          "border"
                          "border-ho-surface"
                          "bg-[#08161f]"
                          "px-3"
                          "pr-16"
                          "text-sm"
                          "text-ho-text"]
                         shared/neutral-input-focus-classes)
            :on {:input [on-change]}}]
   [:button {:type "button"
             :class ["absolute"
                     "right-3"
                     "top-1/2"
                     "-translate-y-1/2"
                     "text-xs"
                     "font-medium"
                     "leading-none"
                     "text-ho-accent"
                     "focus:outline-none"
                     "focus:ring-0"
                     "focus:ring-offset-0"]
             :on {:click [[on-max]]}}
    "MAX"]])
(defn- popover-cta-button
  [{:keys [label submitting? on-submit]}]
  [:button {:type "button"
            :class ["h-10"
                    "w-full"
                    "rounded-[10px]"
                    "bg-[#0f544b]"
                    "text-sm"
                    "font-normal"
                    "text-[#021510]"
                    "transition-colors"
                    "hover:bg-[#1a6f63]"
                    "disabled:cursor-not-allowed"
                    "disabled:opacity-65"]
            :disabled submitting?
            :on {:click [[on-submit]]}}
   (if submitting?
     "Submitting..."
     label)])
(defn- validator-options
  [validators selected-validator]
  (let [validators* (reduce (fn [acc {:keys [validator name stake]}]
                              (if (seq validator)
                                (conj acc {:validator validator
                                           :name (or name validator)
                                           :stake stake})
                                acc))
                            []
                            (or validators []))
        selected-present? (boolean (some #(= selected-validator (:validator %))
                                         validators*))]
    (cond-> validators*
      (and (seq selected-validator)
           (not selected-present?))
      (conj {:validator selected-validator
             :name selected-validator
             :stake nil}))))
(defn- validator-matches-search?
  [search-token {:keys [name validator]}]
  (or (str/includes? (str/lower-case (str (or name ""))) search-token)
      (str/includes? (str/lower-case (str (or validator ""))) search-token)))
(defn- validator-toggle-button
  [open?]
  [:button {:type "button"
            :class ["absolute"
                    "right-2.5"
                    "top-1/2"
                    "-translate-y-1/2"
                    "text-sm"
                    "text-ho-text-secondary"
                    "focus:outline-none"
                    "focus:ring-0"
                    "focus:ring-offset-0"]
            :on {:click (if open?
                          [[:actions/set-staking-form-field :validator-dropdown-open? false]]
                          [[:actions/set-staking-form-field :validator-search-query ""]
                           [:actions/set-staking-form-field :validator-dropdown-open? true]])}}
   (if open? "⌃" "⌄")])
(defn- empty-validator-option
  [selected-validator]
  [:button {:type "button"
            :class (into ["mb-0.5"
                          "flex"
                          "w-full"
                          "items-center"
                          "gap-2"
                          "rounded-[8px]"
                          "px-2"
                          "py-1.5"
                          "text-left"
                          "text-sm"
                          "leading-none"]
                         (if (empty? selected-validator)
                           ["bg-[#122c37]" "text-ho-text"]
                           ["text-[#c8d5d7]" "hover:bg-[#112733]"]))
            :on {:click [[:actions/select-staking-validator ""]]}}
   (when (empty? selected-validator)
     [:span {:class ["text-ho-accent-bright"]} "✓"])
   [:span "Select a Validator"]])
(defn- validator-option-row
  [{:keys [validator name stake]} selected-validator]
  (let [selected? (= validator selected-validator)]
    ^{:key (str "staking-validator-option-" validator)}
    [:button {:type "button"
              :class (into ["mb-0.5"
                            "flex"
                            "w-full"
                            "items-center"
                            "justify-between"
                            "gap-2"
                            "rounded-[8px]"
                            "px-2"
                            "py-1.5"
                            "text-left"
                            "text-sm"
                            "leading-none"]
                           (if selected?
                             ["bg-[#122c37]" "text-ho-text"]
                             ["text-[#c8d5d7]" "hover:bg-[#112733]"]))
              :on {:click [[:actions/select-staking-validator validator]]}}
     [:span {:class ["truncate"]}
      (str (when selected? "✓ ")
           name)]
     [:span {:class ["num" "shrink-0" "text-xs" "text-ho-text-secondary"]}
      (if (number? stake)
        (str (shared/format-table-hype stake) " HYPE")
        "")]]))
(defn- popover-validator-options
  [filtered-options selected-validator]
  [:div {:class ["absolute"
                 "left-0"
                 "right-0"
                 "top-[calc(100%+6px)]"
                 "z-[12]"
                 "max-h-64"
                 "overflow-y-auto"
                 "rounded-[10px]"
                 "border"
                 "border-[#1d3540]"
                 "bg-ho-bg"
                 "p-1"
                 "shadow-[0_16px_34px_rgba(0,0,0,0.45)]"]}
   (empty-validator-option selected-validator)
   (if (seq filtered-options)
     (for [option filtered-options]
       (validator-option-row option selected-validator))
     [:div {:class ["px-2" "py-2" "text-sm" "text-ho-text-secondary"]}
      "No validators found"])])
(defn- popover-validator-select
  [{:keys [selected-validator validators search-query dropdown-open?]}]
  (let [options (validator-options validators selected-validator)
        selected-option (some #(when (= selected-validator (:validator %))
                                 %)
                              options)
        search-token (-> (or search-query "")
                         str
                         str/trim
                         str/lower-case)
        filtered-options (if (seq search-token)
                           (filterv #(validator-matches-search? search-token %)
                                    options)
                           options)
        open? (true? dropdown-open?)]
    [:div {:class ["relative"]}
     [:div {:class ["relative"]}
      [:input {:type "text"
               :value (or search-query "")
               :placeholder (or (:name selected-option) "Select a Validator")
               :class (into ["h-10"
                             "w-full"
                             "rounded-[10px]"
                             "border"
                             "border-ho-surface"
                             "bg-[#08161f]"
                             "px-3"
                             "pr-9"
                             "text-sm"
                             "text-[#c8d5d7]"
                             "placeholder:text-ho-text-secondary"]
                            shared/neutral-input-focus-classes)
               :on {:focus [[:actions/set-staking-form-field :validator-dropdown-open? true]]
                    :input [[:actions/set-staking-form-field :validator-search-query [:event.target/value]]
                            [:actions/set-staking-form-field :validator-dropdown-open? true]]}}]
      (validator-toggle-button open?)]
     (when open?
       (popover-validator-options filtered-options selected-validator))]))
(defn- transfer-direction-toggle
  [direction]
  (let [spot->staking? (= direction :spot->staking)
        from-label (if spot->staking?
                     "Spot Balance"
                     "Staking Balance")
        to-label (if spot->staking?
                   "Staking Balance"
                   "Spot Balance")
        next-direction (if spot->staking?
                         :staking->spot
                         :spot->staking)]
    [:div {:class ["flex" "justify-center"]}
     [:button {:type "button"
               :class ["h-9"
                       "inline-flex"
                       "items-center"
                       "gap-2"
                       "rounded-[10px]"
                       "bg-[#13242d]"
                       "px-3"
                       "text-[18px]"
                       "font-normal"
                       "leading-none"
                       "text-[#c8d5d7]"
                       "transition-colors"
                       "hover:bg-[#1a3039]"
                       "focus:outline-none"
                       "focus:ring-0"
                       "focus:ring-offset-0"]
               :data-role "staking-transfer-direction-toggle"
               :on {:click [[:actions/set-staking-transfer-direction next-direction]]}}
      [:span from-label]
      [:span {:class ["text-[16px]" "text-ho-accent"]}
       "->"]
      [:span to-label]]]))
(defn- transfer-popover-content
  [{:keys [form submitting balances transfer-direction]}]
  (let [spot->staking? (= transfer-direction :spot->staking)
        amount (if spot->staking?
                 (:deposit-amount form)
                 (:withdraw-amount form))
        on-change (if spot->staking?
                    [:actions/set-staking-form-field :deposit-amount [:event.target/value]]
                    [:actions/set-staking-form-field :withdraw-amount [:event.target/value]])
        on-max (if spot->staking?
                 :actions/set-staking-deposit-amount-to-max
                 :actions/set-staking-withdraw-amount-to-max)
        on-submit (if spot->staking?
                    :actions/submit-staking-deposit
                    :actions/submit-staking-withdraw)
        submitting? (if spot->staking?
                      (true? (:deposit? submitting))
                      (true? (:withdraw? submitting)))
        source-label (if spot->staking?
                       "Available to Transfer to Staking Balance"
                       "Available to Transfer to Spot Balance")
        source-value (if spot->staking?
                       (shared/format-balance-hype (:available-transfer balances))
                       (shared/format-balance-hype (:available-stake balances)))]
    [:div {:class ["space-y-3"]}
     [:div {:class ["space-y-1" "text-center"]}
      [:p {:class ["text-sm" "text-ho-text-secondary"]}
       "Transfer HYPE between your staking and spot balances."]
      [:p {:class ["text-sm" "text-ho-text-secondary"]}
       "Transfers from Staking Balance to Spot Balance are locked for 7 days."]]
     (transfer-direction-toggle transfer-direction)
     (popover-amount-input {:input-id "staking-transfer-amount"
                            :amount amount
                            :on-change on-change
                            :on-max on-max})
     [:div {:class ["space-y-1.5"]}
      (shared/key-value-row source-label source-value)
      (shared/key-value-row "Available to Stake" (shared/format-balance-hype (:available-stake balances)))
      (shared/key-value-row "Pending Transfers to Spot Balance"
                            (shared/format-balance-hype (:pending-withdrawals balances)))]
     (popover-cta-button {:label "Transfer"
                          :submitting? submitting?
                          :on-submit on-submit})]))
(defn- stake-popover-content
  [{:keys [form
           submitting
           balances
           selected-validator
           validators
           validator-search-query
           validator-dropdown-open?]}]
  [:div {:class ["space-y-3"]}
   (popover-amount-input {:input-id "staking-delegate-amount"
                          :amount (:delegate-amount form)
                          :on-change [:actions/set-staking-form-field :delegate-amount [:event.target/value]]
                          :on-max :actions/set-staking-delegate-amount-to-max})
   (popover-validator-select {:selected-validator selected-validator
                              :validators validators
                              :search-query validator-search-query
                              :dropdown-open? validator-dropdown-open?})
   [:div {:class ["space-y-1.5"]}
    (shared/key-value-row "Available to Stake" (shared/format-balance-hype (:available-stake balances)))
    (shared/key-value-row "Total Staked" (shared/format-balance-hype (:total-staked balances)))]
   [:p {:class ["text-sm" "text-ho-text-secondary"]}
    "The staking lockup period is 1 day."]
   (popover-cta-button {:label "Stake"
                        :submitting? (true? (:delegate? submitting))
                        :on-submit :actions/submit-staking-delegate})])
(defn- unstake-popover-content
  [{:keys [form
           submitting
           error
           selected-validator
           validators
           validator-search-query
           validator-dropdown-open?]}]
  [:div {:class ["space-y-3"]}
   (popover-amount-input {:input-id "staking-undelegate-amount"
                          :amount (:undelegate-amount form)
                          :on-change [:actions/set-staking-form-field :undelegate-amount [:event.target/value]]
                          :on-max :actions/set-staking-undelegate-amount-to-max})
   (popover-validator-select {:selected-validator selected-validator
                              :validators validators
                              :search-query validator-search-query
                              :dropdown-open? validator-dropdown-open?})
   (when (seq error)
     [:div {:class ["rounded-lg" "border" "border-ho-border-sell" "bg-ho-sell-soft-deep"
                    "px-3" "py-2" "text-sm" "text-ho-sell-tint"]
            :data-role "staking-unstake-error"}
      error])
   (popover-cta-button {:label "Unstake"
                        :submitting? (true? (:undelegate? submitting))
                        :on-submit :actions/submit-staking-undelegate})])
(defn action-popover-layer
  [{:keys [action-popover
           form
           submitting
           balances
           error
           selected-validator
           validator-search-query
           validator-dropdown-open?
           validators]}]
  (when (:open? action-popover)
    (let [kind (:kind action-popover)
          anchor (action-popover-anchor kind (:anchor action-popover))
          panel-style (action-popover-layout-style anchor kind)
          title (case kind
                  :transfer "Transfer HYPE"
                  :unstake "Unstake"
                  "Stake")]
      [:div {:class ["fixed" "inset-0" "z-[230]" "pointer-events-none"]
             :data-role "staking-action-popover-layer"}
       [:button {:type "button"
                 :class ["absolute" "inset-0" "pointer-events-auto" "bg-transparent"]
                 :aria-label "Close staking action popover"
                 :on {:click [[:actions/close-staking-action-popover]]}}]
       [:div {:class ["absolute"
                      "pointer-events-auto"
                      "staking-action-popover-surface"
                      "rounded-[22px]"
                      "border"
                      "border-[#1d3540]"
                      "p-4"
                      "pt-5"
                      "shadow-[0_24px_58px_rgba(0,0,0,0.55)]"
                      "space-y-3"]
              :style panel-style
              :tab-index 0
              :role "dialog"
              :aria-modal true :data-role "staking-action-popover"
              :replicant/on-render action-popover-focus-on-render
              :on {:keydown [[:actions/handle-staking-action-popover-keydown [:event/key]]]}}
        (popover-close-button)
        [:h2 {:class ["text-[42px]" "font-normal" "leading-none" "text-ho-text" "text-center"]}
         title]
        (case kind
          :transfer
          (transfer-popover-content {:form form
                                     :submitting submitting
                                     :balances balances
                                     :transfer-direction (:transfer-direction action-popover)})
          :unstake
          (unstake-popover-content {:form form
                                    :submitting submitting
                                    :error error
                                    :selected-validator selected-validator
                                    :validator-search-query validator-search-query
                                    :validator-dropdown-open? validator-dropdown-open?
                                    :validators validators})
          (stake-popover-content {:form form
                                  :submitting submitting
                                  :balances balances
                                  :selected-validator selected-validator
                                  :validator-search-query validator-search-query
                                  :validator-dropdown-open? validator-dropdown-open?
                                  :validators validators}))]])))
