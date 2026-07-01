(ns hyperopen.views.portfolio.optimize.setup-readiness-panel
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]))

(defn readiness-panel
  [readiness history-load-state]
  (let [{:keys [copy error-message warnings]}
        (optimizer-view-model/readiness-panel-model readiness history-load-state)]
    [:div {:class ["mt-4" "rounded-lg" "border" "border-base-300" "bg-base-200/40" "p-3"]
           :data-role "portfolio-optimizer-readiness-panel"}
     [:p {:class ["text-[0.65rem]"
                  "font-semibold"
                  "uppercase"
                  "tracking-[0.18em]"
                  "text-trading-muted"]}
      "Readiness"]
     [:p {:class ["mt-2" "text-xs" "text-trading-muted"]}
      copy]
     (when error-message
       [:p {:class ["mt-3"
                    "rounded-md"
                    "border"
                    "border-error/40"
                    "bg-error/10"
                    "px-2"
                    "py-1.5"
                    "text-xs"
                     "text-error"]}
        error-message])
     (when (seq warnings)
       (into
        [:div {:class ["mt-3" "space-y-2"]}]
        (map (fn [{:keys [message code-label count assets]}]
               [:div {:class ["rounded-md"
                              "border"
                              "border-warning/40"
                              "bg-warning/10"
                              "px-2"
                              "py-1.5"
                              "text-xs"
                              "text-warning"]
                      :data-role "portfolio-optimizer-readiness-warning"}
                [:div {:class ["flex" "items-start" "justify-between" "gap-2"]}
                 [:p {:class ["font-semibold"]}
                  message]
                 (when (and (number? count) (> count 1))
                   [:span {:class ["shrink-0" "rounded-full" "border" "border-warning/50"
                                   "px-1.5" "font-mono" "text-[0.625rem]"]
                           :data-role "portfolio-optimizer-readiness-warning-count"}
                    count])]
                ;; Repeated same-code warnings collapse into one row with an expandable list of the
                ;; affected assets, instead of N near-identical rows.
                (when (and (number? count) (> count 1) (seq assets))
                  [:details {:class ["mt-1"]}
                   [:summary {:class ["cursor-pointer" "select-none" "font-mono"
                                      "text-[0.65rem]" "uppercase" "tracking-[0.08em]"
                                      "text-warning/80"]}
                    (str "Show " count " assets")]
                   [:p {:class ["mt-1" "leading-[1.5]" "text-warning/80"]
                        :data-role "portfolio-optimizer-readiness-warning-assets"}
                    (str/join ", " (map :label assets))]])
                (when (and code-label (or (not (number? count)) (<= count 1)))
                  [:p {:class ["mt-1"
                               "font-mono"
                               "text-[0.65rem]"
                               "uppercase"
                               "tracking-[0.08em]"
                               "text-warning/80"]}
                   code-label])])
             warnings)))]))
