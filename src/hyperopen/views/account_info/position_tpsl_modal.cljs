(ns hyperopen.views.account-info.position-tpsl-modal
  (:require [hyperopen.account.history.position-tpsl :as position-tpsl]
            [hyperopen.views.account-info.position-tpsl-modal.fields :as modal-fields]
            [hyperopen.views.account-info.position-tpsl-modal.layout :as modal-layout]
            [hyperopen.views.account-info.shared :as shared]))

(defn position-tpsl-modal-view
  [modal]
  (let [modal* (or modal (position-tpsl/default-modal-state))]
    (when (position-tpsl/open? modal*)
      (let [preview (position-tpsl/validate-modal modal*)
            submitting? (boolean (:submitting? modal*))
            submit-label (if submitting?
                           "Submitting..."
                           (:display-message preview))
            submit-disabled? (or submitting?
                               (not (:is-ok preview)))
            coin (modal-fields/coin-label (:coin modal*))
            position-size (:position-size modal*)
            gain (position-tpsl/estimated-gain-usd modal*)
            loss (position-tpsl/estimated-loss-usd modal*)
            gain-roe-percent (position-tpsl/estimated-gain-roe-percent modal*)
            loss-roe-percent (position-tpsl/estimated-loss-roe-percent modal*)
            gain-position-percent (position-tpsl/estimated-gain-position-percent modal*)
            loss-position-percent (position-tpsl/estimated-loss-position-percent modal*)
            gain-mode (position-tpsl/tp-gain-mode modal*)
            loss-mode (position-tpsl/sl-loss-mode modal*)
            configure-size-percent (position-tpsl/configured-size-percent modal*)
            gain-input-value (if (not= gain-mode :usd)
                               (modal-fields/percent-text (position-tpsl/estimated-gain-percent-for-mode modal* gain-mode))
                               (modal-fields/usd-input-text modal* gain))
            loss-input-value (if (not= loss-mode :usd)
                               (modal-fields/percent-text (position-tpsl/estimated-loss-percent-for-mode modal* loss-mode))
                               (modal-fields/usd-input-text modal* loss))
            expected-profit-value (modal-fields/expected-pnl-text gain-mode gain gain-roe-percent gain-position-percent)
            expected-loss-value (modal-fields/expected-pnl-text loss-mode loss loss-roe-percent loss-position-percent)
            mobile-sheet? (modal-layout/mobile-sheet? modal*)
            layout-style (if mobile-sheet?
                           (modal-layout/mobile-sheet-style modal*)
                           (modal-layout/modal-layout-style modal*))
            panel-children
            [[:div {:class ["flex" "items-center" "justify-between"]}
              [:h2 {:class ["text-2xl" "font-semibold" "text-gray-100"]}
               "TP/SL for Position"]
              [:button {:type "button"
                        :class ["inline-flex"
                                "h-8"
                                "w-8"
                                "items-center"
                                "justify-center"
                                "rounded-lg"
                                "border"
                                "border-ho-border-accent-muted"
                                (if mobile-sheet? "bg-[#0b181d]" "bg-transparent")
                                "text-gray-300"
                                "transition-colors"
                                "hover:bg-base-300"
                                "hover:text-gray-100"
                                "focus:outline-none"
                                "focus:ring-1"
                                "focus:ring-ho-accent-hi/40"
                                "focus:ring-offset-0"
                                "focus:shadow-none"]
                        :aria-label "Close TP/SL sheet"
                        :on {:click [[:actions/close-position-tpsl-modal]]}}
               "x"]]

             [:div {:class ["space-y-1.5"]}
              (modal-fields/metric-row "Coin" coin)
              (modal-fields/metric-row "Position" (str (modal-fields/amount-text position-size) " " coin))
              (modal-fields/metric-row "Entry Price" (shared/format-trade-price (:entry-price modal*)))
              (modal-fields/metric-row "Mark Price" (shared/format-trade-price (:mark-price modal*)))]

             [:div {:class ["grid" "grid-cols-2" "gap-2"]}
              (modal-fields/input-row "TP Price"
                                      (:tp-price modal*)
                                      [[:actions/set-position-tpsl-modal-field [:tp-price] [:event.target/value]]])
              (modal-fields/input-row "Gain"
                                      gain-input-value
                                      [[:actions/set-position-tpsl-modal-field [:tp-gain] [:event.target/value]]]
                                      {:unit-control (modal-fields/pnl-mode-select gain-mode
                                                                                   [:tp-gain-mode]
                                                                                   "Gain unit")
                                       :select-on-focus? true})]

             (when (pos? gain)
               [:div {:class ["flex" "justify-end" "pr-1" "text-sm"]}
                [:span {:class ["text-gray-400"]} "Expected profit:"]
                [:span {:class ["ml-1" "font-semibold" "text-gray-100" "num"]}
                 expected-profit-value]])

             (when (boolean (:limit-price? modal*))
               [:div {:class ["grid" "grid-cols-2" "gap-2"]}
                (modal-fields/input-row "TP Limit"
                                        (:tp-limit modal*)
                                        [[:actions/set-position-tpsl-modal-field [:tp-limit] [:event.target/value]]])
                [:div]])

             [:div {:class ["grid" "grid-cols-2" "gap-2"]}
              (modal-fields/input-row "SL Price"
                                      (:sl-price modal*)
                                      [[:actions/set-position-tpsl-modal-field [:sl-price] [:event.target/value]]])
              (modal-fields/input-row "Loss"
                                      loss-input-value
                                      [[:actions/set-position-tpsl-modal-field [:sl-loss] [:event.target/value]]]
                                      {:unit-control (modal-fields/pnl-mode-select loss-mode
                                                                                   [:sl-loss-mode]
                                                                                   "Loss unit")
                                       :select-on-focus? true})]

             (when (pos? loss)
               [:div {:class ["flex" "justify-end" "pr-1" "text-sm"]}
                [:span {:class ["text-gray-400"]} "Expected loss:"]
                [:span {:class ["ml-1" "font-semibold" "text-gray-100" "num"]}
                 expected-loss-value]])

             (when (boolean (:limit-price? modal*))
               [:div {:class ["grid" "grid-cols-2" "gap-2"]}
                (modal-fields/input-row "SL Limit"
                                        (:sl-limit modal*)
                                        [[:actions/set-position-tpsl-modal-field [:sl-limit] [:event.target/value]]])
                [:div]])

             [:div {:class ["space-y-1"]}
              (modal-fields/checkbox-row "position-tpsl-configure-amount"
                                         "Configure Amount"
                                         (:configure-amount? modal*)
                                         [[:actions/set-position-tpsl-configure-amount [:event.target/checked]]])]

             (when (boolean (:configure-amount? modal*))
               (modal-fields/configure-amount-controls modal* coin configure-size-percent))

             (when-not (boolean (:configure-amount? modal*))
               (modal-fields/checkbox-row "position-tpsl-limit-price"
                                          "Limit Price"
                                          (:limit-price? modal*)
                                          [[:actions/set-position-tpsl-limit-price [:event.target/checked]]]))

             (when (seq (:error modal*))
               [:div {:class ["text-xs" "text-ho-sell-hi"]} (:error modal*)])

             [:div {:class ["grid" "grid-cols-2" "gap-3" "pt-1"]}
              [:button {:type "button"
                        :class ["h-11"
                                "rounded-lg"
                                "bg-[#74808F]"
                                "text-sm"
                                "font-semibold"
                                "text-[#1A212B]"
                                "hover:bg-[#8893a0]"
                                "disabled:cursor-not-allowed"
                                "disabled:opacity-50"]
                        :disabled submit-disabled?
                        :on {:click [[:actions/submit-position-tpsl]]}}
               submit-label]
              [:button {:type "button"
                        :class ["h-11"
                                "rounded-lg"
                                "border"
                                "border-base-300"
                                "bg-base-200"
                                "text-sm"
                                "font-semibold"
                                "text-gray-100"
                                "hover:bg-base-300"]
                        :on {:click [[:actions/close-position-tpsl-modal]]}}
               "Close"]]]]
        (if mobile-sheet?
          [:div {:class ["fixed" "inset-0" "z-[260]"]
                 :data-role "position-tpsl-mobile-sheet-layer"}
           [:button {:type "button"
                     :class ["absolute" "inset-0" "bg-black/55" "backdrop-blur-[1px]"]
                     :style {:transition "opacity 0.14s ease-out"
                             :opacity 1}
                     :replicant/mounting {:style {:opacity 0}}
                     :replicant/unmounting {:style {:opacity 0}}
                     :on {:click [[:actions/close-position-tpsl-modal]]}
                     :aria-label "Close TP/SL sheet backdrop"
                     :data-role "position-tpsl-mobile-sheet-backdrop"}]
           (into [:div {:class ["absolute"
                                "inset-x-0"
                                "bottom-0"
                                "w-full"
                                "overflow-y-auto"
                                "rounded-t-[22px]"
                                "border"
                                "border-ho-border-accent-muted"
                                "bg-ho-bg-deep"
                                "px-4"
                                "pt-4"
                                "text-sm"
                                "shadow-[0_-24px_60px_rgba(0,0,0,0.45)]"
                                "space-y-3"]
                        :style layout-style
                        :replicant/mounting {:style {:transform "translateY(18px)"
                                                     :opacity 0}}
                        :replicant/unmounting {:style {:transform "translateY(18px)"
                                                       :opacity 0}}
                        :role "dialog"
                        :aria-modal true
                        :aria-label "TP/SL for Position"
                        :data-position-tpsl-surface "true"
                        :on {:keydown [[:actions/handle-position-tpsl-modal-keydown [:event/key]]]}}]
                 (keep identity panel-children))]
          (into [:div {:class ["fixed"
                               "z-[260]"
                               "rounded-[10px]"
                               "border"
                               "border-base-300"
                               "bg-base-100"
                               "p-4"
                               "text-sm"
                               "spectate-[0_24px_60px_rgba(0,0,0,0.45)]"
                               "space-y-3"
                               "overflow-y-auto"]
                       :style layout-style
                       :role "dialog"
                       :aria-label "TP/SL for Position"
                       :data-position-tpsl-surface "true"
                       :on {:keydown [[:actions/handle-position-tpsl-modal-keydown [:event/key]]]}}]
                (keep identity panel-children)))))))
