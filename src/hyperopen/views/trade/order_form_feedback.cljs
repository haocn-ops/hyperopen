(ns hyperopen.views.trade.order-form-feedback
  (:require [hyperopen.state.trading :as trading]
            [hyperopen.trading.order-form-tpsl-policy :as tpsl-policy]))

(defn- twap-runtime-label [total-minutes]
  (if (number? total-minutes)
    (let [hours (quot total-minutes 60)
          minutes (mod total-minutes 60)]
      (cond
        (and (pos? hours) (pos? minutes)) (str hours "h " minutes "m")
        (pos? hours) (str hours "h")
        :else (str minutes "m")))
    "--"))

(defn twap-preview [state form base-symbol]
  (let [total-minutes (trading/twap-total-minutes (get-in form [:twap]))
        order-count (trading/twap-suborder-count total-minutes)
        suborder-size (trading/twap-suborder-size (:size form) total-minutes)]
    {:runtime (twap-runtime-label total-minutes)
     :frequency (str trading/twap-frequency-seconds "s")
     :order-count (if (number? order-count) (str order-count) "--")
     :size-per-suborder (if (number? suborder-size)
                          (str (trading/base-size-string state suborder-size)
                               " "
                               base-symbol)
                          "--")}))

(defn tpsl-panel-model
  [state form side ui-leverage controls]
  (let [ui-state (trading/order-form-ui-state state)
        pricing-policy (trading/order-price-policy state form ui-state)
        limit-like? (boolean (:show-limit-like-controls? controls))
        unit (tpsl-policy/normalize-unit (get-in form [:tpsl :unit]))
        baseline (tpsl-policy/baseline-price form pricing-policy limit-like?)
        size (trading/parse-num (:size form))
        leverage (trading/parse-num ui-leverage)
        tp-inverse (tpsl-policy/inverse-for-leg side :tp)
        sl-inverse (tpsl-policy/inverse-for-leg side :sl)]
    {:form form
     :unit unit
     :unit-dropdown-open? (boolean (:tpsl-unit-dropdown-open? ui-state))
     :tp-offset (tpsl-policy/offset-display {:offset-input (get-in form [:tp :offset-input])
                                             :trigger (get-in form [:tp :trigger])
                                             :baseline baseline
                                             :size size
                                             :leverage leverage
                                             :inverse tp-inverse
                                             :unit unit})
     :sl-offset (tpsl-policy/offset-display {:offset-input (get-in form [:sl :offset-input])
                                             :trigger (get-in form [:sl :trigger])
                                             :baseline baseline
                                             :size size
                                             :leverage leverage
                                             :inverse sl-inverse
                                             :unit unit})}))

(defn- spectate-mode-icon [size-classes]
  [:svg {:viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "1.9"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class size-classes
         :aria-hidden "true"}
   [:path {:d "M9 10h.01"}]
   [:path {:d "M15 10h.01"}]
   [:path {:d "M12 2a7 7 0 0 0-7 7v10l2-2 2 2 2-2 2 2 2-2 2 2V9a7 7 0 0 0-7-7z"}]])

(defn spectate-mode-stop-affordance []
  [:div {:data-role "order-form-spectate-mode-stop"}
   [:button {:type "button"
             :class ["flex"
                     "h-9"
                     "w-full"
                     "items-center"
                     "justify-between"
                     "gap-2"
                     "rounded-lg"
                     "border"
                     "border-[#2f7067]"
                     "bg-[#0f433d]/25"
                     "px-3"
                     "text-sm"
                     "font-medium"
                     "text-[#d6f1ed]"
                     "transition-colors"
                     "hover:bg-[#0f433d]/45"
                     "focus:outline-none"
                     "focus:ring-1"
                     "focus:ring-[#87c8c0]/40"
                     "focus:ring-offset-0"]
             :on {:click [[:actions/stop-spectate-mode]]}
             :data-role "order-form-spectate-mode-stop-button"}
    [:span {:class ["inline-flex" "min-w-0" "items-center" "gap-2"]}
     (spectate-mode-icon ["h-5" "w-5" "shrink-0"])
     [:span {:class ["truncate"]} "Stop Spectate Mode"]]
    [:span {:class ["shrink-0"
                    "rounded-[4px]"
                    "border"
                    "border-[#2f7067]"
                    "bg-[#0f433d]"
                    "px-1.5"
                    "py-0.5"
                    "text-xs"
                    "font-semibold"
                    "uppercase"
                    "leading-none"
                    "tracking-[0.04em]"
                    "text-[#c2e5e0]"]}
     "⌘⇧X"]]])
