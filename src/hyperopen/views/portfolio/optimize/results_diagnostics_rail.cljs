(ns hyperopen.views.portfolio.optimize.results-diagnostics-rail
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model.results :as results-model]
            [hyperopen.views.portfolio.optimize.format :as opt-format]
            [hyperopen.views.portfolio.optimize.results-summary :as summary]))

(defn- binding-constraint-row
  [labels-by-instrument binding]
  [:div {:class ["rounded-md" "border" "border-warning/40" "bg-warning/10" "p-2" "text-xs" "text-warning"]}
   [:span {:class ["font-semibold"]}
    (results-model/instrument-label labels-by-instrument (:instrument-id binding))]
   [:span {:class ["ml-2"]} (opt-format/keyword-label (:constraint binding))]])

(defn- sensitivity-row
  [labels-by-instrument [instrument-id row]]
  [:div {:class ["optimizer-row"
                 "rounded-md" "border" "border-base-300" "bg-base-200/40" "p-2" "text-xs"]
         :data-role (str "portfolio-optimizer-sensitivity-row-" instrument-id)}
   [:span {:class ["font-semibold"]} (results-model/instrument-label labels-by-instrument instrument-id)]
   [:span {:class ["ml-2" "text-trading-muted"]}
    (str "Base " (opt-format/format-pct (:base-expected-return row))
         " / Down " (opt-format/format-pct (:down-expected-return row))
         " / Up " (opt-format/format-pct (:up-expected-return row)))]])

(defn- replace-all-text
  [text needle replacement]
  (let [needle* (str needle)
        replacement* (str replacement)]
    (if (or (not (string? text))
            (empty? needle*)
            (= needle* replacement*))
      text
      (loop [remaining text
             out ""]
        (if-let [idx (str/index-of remaining needle*)]
          (recur (subs remaining (+ idx (count needle*)))
                 (str out (subs remaining 0 idx) replacement*))
          (str out remaining))))))

(defn- warning-instrument-ids
  [warning]
  (vec (distinct (concat (keep warning
                               [:instrument-id
                                :left-instrument-id
                                :right-instrument-id
                                :comparator-instrument-id
                                :long-instrument-id
                                :short-instrument-id])
                         (:instrument-ids warning)))))

(defn- warning-label
  [labels-by-instrument warning instrument-id]
  (or (when (= instrument-id (:instrument-id warning))
        (:instrument-label warning))
      (results-model/instrument-label labels-by-instrument instrument-id)))

(defn- human-warning-label
  [instrument-id label]
  (when (and (string? label)
             (seq label)
             (not= label (str instrument-id)))
    label))

(defn- warning-primary-label
  [labels-by-instrument warning]
  (when-let [instrument-id (:instrument-id warning)]
    (human-warning-label instrument-id
                         (warning-label labels-by-instrument warning instrument-id))))

(defn- prepend-warning-label
  [message label]
  (if (and (string? message)
           (seq label)
           (not (str/includes? message label)))
    (str label ": " message)
    message))

(defn- warning-message
  [labels-by-instrument warning]
  (let [message (reduce (fn [message instrument-id]
                          (replace-all-text message
                                            instrument-id
                                            (warning-label labels-by-instrument
                                                           warning
                                                           instrument-id)))
                        (:message warning)
                        (warning-instrument-ids warning))]
    (prepend-warning-label message (warning-primary-label labels-by-instrument warning))))

(defn warning-row
  [labels-by-instrument warning]
  [:p {:class ["rounded-md" "border" "border-warning/40" "bg-warning/10" "p-2" "text-xs" "text-warning"]
       :data-role "portfolio-optimizer-result-warning"}
   [:span {:class ["font-semibold"]} (opt-format/keyword-label (:code warning))]
   (when-let [message (warning-message labels-by-instrument warning)]
     [:span {:class ["ml-2"]} (str " " message)])])

(defn warnings-panel
  [result]
  (when (seq (:warnings result))
    (summary/panel-shell
     "portfolio-optimizer-result-warnings"
     "Result Warnings"
     "Warnings explain assumptions or mathematically valid outcomes that may require a rerun with different controls."
     (map (partial warning-row (:labels-by-instrument result)) (:warnings result)))))

(defn diagnostics-panel
  [result]
  (let [diagnostics (:diagnostics result)
        labels-by-instrument (or (:labels-by-instrument result) {})
        bindings (:binding-constraints diagnostics)
        conditioning (:covariance-conditioning diagnostics)
        sensitivity (:weight-sensitivity-by-instrument diagnostics)]
    (summary/panel-shell
     "portfolio-optimizer-diagnostics-panel"
     "Diagnostics"
     "Engine diagnostics are rendered from the run result, not recomputed in the view."
     [:div {:class ["grid" "grid-cols-2" "gap-2" "lg:grid-cols-4"]}
      (summary/summary-card "Gross" (opt-format/format-multiple (:gross-exposure diagnostics)))
      (summary/summary-card "Net" (opt-format/format-multiple (:net-exposure diagnostics)))
      (summary/summary-card "Effective N" (opt-format/format-decimal (:effective-n diagnostics)))
      (summary/summary-card "Turnover" (opt-format/format-pct (:turnover diagnostics)))]
     [:div {:class ["grid" "grid-cols-1" "gap-2" "lg:grid-cols-3"]}
      (summary/summary-card "Condition" (opt-format/keyword-label (:status conditioning)))
      (summary/summary-card "Condition #"
                            (opt-format/format-decimal (:condition-number conditioning)))
      (summary/summary-card "Min Eigen"
                            (opt-format/format-decimal (:min-eigenvalue conditioning)))]
     [:div {:class ["rounded-lg" "border" "border-base-300" "bg-base-200/40" "p-3"]}
      [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.18em]" "text-trading-muted"]}
       "Binding Constraints"]
      (if (seq bindings)
        (into [:div {:class ["mt-2" "space-y-2"]}]
              (map (partial binding-constraint-row labels-by-instrument) bindings))
        [:p {:class ["mt-2" "text-xs" "text-trading-muted"]}
         "No binding constraints reported."])]
     [:div {:class ["rounded-lg" "border" "border-base-300" "bg-base-200/40" "p-3"]
            :data-role "portfolio-optimizer-sensitivity-panel"}
      [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.18em]" "text-trading-muted"]}
       "Weight Sensitivity"]
      (if (seq sensitivity)
        (into [:div {:class ["mt-2" "space-y-2"]}]
              (map (partial sensitivity-row labels-by-instrument) sensitivity))
        [:p {:class ["mt-2" "text-xs" "text-trading-muted"]}
         "No sensitivity diagnostics reported."])])))

(defn- status-token
  [status]
  (case status
    :ok {:label "ok" :class "text-trading-green"}
    :healthy {:label "ok" :class "text-trading-green"}
    :warning {:label "caution" :class "text-warning"}
    :caution {:label "caution" :class "text-warning"}
    :ill-conditioned {:label "caution" :class "text-warning"}
    :singular {:label "bad" :class "text-trading-red"}
    {:label (opt-format/keyword-label status) :class "text-trading-muted"}))

(defn- top-sensitivity
  [sensitivity]
  (when (seq sensitivity)
    (let [row-span (fn [row]
                     (js/Math.abs
                      (- (or (:up-weight row) (:up-expected-return row) 0)
                         (or (:down-weight row) (:down-expected-return row) 0))))
          [instrument-id row] (->> sensitivity
                                   (sort-by (fn [[_ row*]]
                                              (- (row-span row*))))
                                   first)]
      {:instrument-id instrument-id
       :span (row-span row)})))

(defn- pluralize
  [count singular plural]
  (if (= 1 count) singular plural))

(defn- rounded-count
  [value]
  (when (opt-format/finite-number? value)
    (js/Math.round value)))

(defn- history-window-value
  [history-summary]
  (let [observations (rounded-count (:return-observations history-summary))
        return-days (rounded-count (:return-days history-summary))]
    (cond
      (and observations return-days)
      (str (opt-format/format-decimal observations {:maximum-fraction-digits 0})
           " "
           (pluralize observations "return" "returns")
           " · "
           (opt-format/format-decimal return-days {:maximum-fraction-digits 0})
           " "
           (pluralize return-days "day" "days"))

      observations
      (str (opt-format/format-decimal observations {:maximum-fraction-digits 0})
           " "
           (pluralize observations "return" "returns"))

      :else
      "Loaded history")))

(defn- history-window-status
  [history-summary]
  (let [observations (:return-observations history-summary)]
    (if (and (opt-format/finite-number? observations)
             (< observations 30))
      :caution
      :ok)))

(defn- limiter-reason-copy
  [reason]
  (case reason
    :starts-later "starts later than the rest"
    :ends-earlier "ends earlier than the rest"
    :fewest-return-observations "has the fewest usable return observations"
    "sets the shared return window"))

(defn- history-window-subtext
  [result history-summary]
  (let [instrument-id (:limiting-instrument-id history-summary)
        reason (:limiting-reason history-summary)]
    (cond
      instrument-id
      (str (results-model/instrument-label (:labels-by-instrument result)
                                           instrument-id)
           " "
           (limiter-reason-copy reason)
           ".")

      (= :source-coverage-unavailable reason)
      "Limiter unavailable from aligned returns."

      :else
      "Shared return calendar from aligned optimizer history.")))

(defn- trust-row
  [{:keys [label status value subtext]}]
  (let [{status-label :label status-class :class} (status-token status)]
    [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
     [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
      [:span {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]" "text-trading-muted"]}
       label]
      [:span {:class [status-class "text-[0.62rem]" "font-semibold" "uppercase"]}
       (str "● " status-label)]]
     [:p {:class ["mt-1" "font-mono" "text-base" "font-semibold" "tabular-nums" "text-trading-text"]}
      value]
     [:p {:class ["mt-0.5" "text-[0.64rem]" "text-trading-muted/70"]}
      subtext]]))

(defn trust-diagnostics-rail
  [result]
  (let [diagnostics (:diagnostics result)
        conditioning (:covariance-conditioning diagnostics)
        sensitivity (:weight-sensitivity-by-instrument diagnostics)
        sensitivity-top (top-sensitivity sensitivity)
        effective-n (:effective-n diagnostics)
        universe-size (count (:instrument-ids result))
        conditioning-status (or (:status conditioning) :ok)
        weight-stability-status (if sensitivity-top :caution :ok)]
    [:aside {:class ["optimizer-trust-caution-panel"
                     "min-h-0" "border-l" "border-base-300" "bg-base-100/95"]
             :data-role "portfolio-optimizer-trust-caution-panel"}
     [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
      [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
       "How much to trust this"]]
     [:div {:class ["optimizer-diagnostics-list"]
            :data-role "portfolio-optimizer-diagnostics-panel"}
      (trust-row {:label "Conditioning"
                  :status conditioning-status
                  :value (if (= :ok conditioning-status) "Healthy" (opt-format/keyword-label conditioning-status))
                  :subtext "Correlation matrix is checked before weights are accepted."})
      (trust-row {:label "History Used"
                  :status (history-window-status (:history-summary result))
                  :value (history-window-value (:history-summary result))
                  :subtext (history-window-subtext result
                                                   (:history-summary result))})
      (trust-row {:label "Diversification"
                  :status :ok
                  :value (str "Effective N · " (opt-format/format-effective-n effective-n universe-size) " of " universe-size)
                  :subtext "Higher effective N means less concentration in one name."})
      (trust-row {:label "Weight Stability"
                  :status weight-stability-status
                  :value (if sensitivity-top "Moderate" "Stable")
                  :subtext (if sensitivity-top
                            (str (results-model/instrument-label (:labels-by-instrument result)
                                                                 (:instrument-id sensitivity-top))
                                 " is most sensitive (±"
                                 (opt-format/format-pct (/ (:span sensitivity-top) 2))
                                 ").")
                            "No material sensitivity flags reported.")})
      (when (seq (:warnings result))
        [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]
               :data-role "portfolio-optimizer-result-warnings"}
         [:p {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]" "text-warning"]}
          "Warnings"]
         (into [:div {:class ["mt-2" "space-y-2"]}]
               (map (partial warning-row (:labels-by-instrument result))
                    (:warnings result)))])
      [:details {:class ["border-b" "border-base-300"]}
       [:summary {:class ["cursor-pointer" "px-4" "py-3" "font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
        "More Diagnostics"]
       [:div {:class ["space-y-2" "px-4" "pb-4"]}
        (summary/summary-card "Gross" (opt-format/format-multiple (:gross-exposure diagnostics)))
        (summary/summary-card "Net" (opt-format/format-multiple (:net-exposure diagnostics)))
        (summary/summary-card "Turnover" (opt-format/format-pct (:turnover diagnostics)))
        (summary/summary-card "Condition #" (opt-format/format-decimal (:condition-number conditioning)))]]]]))

(defn- quality-status
  [quality]
  (case quality
    :high :ok
    :medium :ok
    :caution))

(defn- stability-status
  [stability]
  (case stability
    :provisional :caution
    :ok))

(defn- confidence-row
  [{:keys [label status value subtext value-class]}]
  (let [{status-label :label status-class :class} (when status (status-token status))]
    [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
     [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
      [:span {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]" "text-trading-muted"]}
       label]
      (when status
        [:span {:class [status-class "text-[0.62rem]" "font-semibold" "uppercase"]}
         (str "● " status-label)])]
     [:p {:class ["mt-1" "font-mono" "text-sm" "font-semibold" "tabular-nums"
                  (or value-class "text-trading-text")]}
      value]
     (when subtext
       [:p {:class ["mt-0.5" "text-[0.6rem]" "text-trading-muted/70"]} subtext])]))

(defn result-confidence-rail
  [refinement]
  (when-let [assessment (:assessment refinement)]
    (let [next-step (:next-step assessment)]
      [:aside {:class ["optimizer-result-confidence-panel"
                       "min-h-0" "border-l" "border-base-300" "bg-base-100/95"]
               :data-role "portfolio-optimizer-result-confidence-panel"}
       [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
        [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
         "Result confidence"]]
       (confidence-row {:label "Frontier quality"
                        :status (quality-status (:frontier-quality assessment))
                        :value (opt-format/refinement-quality-label (:frontier-quality assessment))
                        :subtext "Density of the solved efficient-frontier sample."})
       (confidence-row {:label "Selection stability"
                        :status (stability-status (:selection-stability assessment))
                        :value (opt-format/refinement-stability-label (:selection-stability assessment))
                        :subtext (if (:exact-selection? assessment)
                                   "Selected portfolio is solved exactly for this objective."
                                   "Selected portfolio is sampled from the frontier grid.")})
       (confidence-row {:label "Stop reason"
                        :value (opt-format/refinement-stop-reason-label (:stop-reason assessment))
                        :subtext "Why the current result is where it is."})
       (confidence-row {:label "Next step"
                        :value (opt-format/refinement-next-step-label next-step)
                        :value-class (when (not= :none next-step) "text-primary")})])))
