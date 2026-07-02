(ns hyperopen.views.portfolio.optimize.infeasible-panel
  (:require [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(def ^:private violation-control-keys
  {:sum-upper-below-target #{:max-asset-weight}
   :sum-upper-below-net-min #{:net-min :max-asset-weight}
   :sum-lower-above-target #{:held-locks}
   :target-return-above-feasible-maximum #{:target-return}
   :gross-floor-above-gross-max #{:gross-min :gross-max}
   :gross-floor-exceeds-capacity #{:gross-min :max-asset-weight}
   :solver-result-gross-exposure-violation #{:gross-max}
   :solver-result-turnover-violation #{:max-turnover}})

(def ^:private violation-constraint-control-keys
  {:gross-exposure #{:gross-max}
   :net-exposure #{:net-min :net-max}
   :turnover #{:max-turnover}})

(def ^:private control-labels
  {:max-asset-weight "Max Asset Weight"
   :gross-min "Gross Exposure Min"
   :gross-max "Gross Exposure"
   :held-locks "Held Position Locks"
   :max-turnover "Turnover Cap"
   :net-max "Net Exposure Max"
   :net-min "Net Exposure Min"
   :target-return "Target Return"})

(defn infeasible-result
  [run-state]
  (when (= :infeasible (:status run-state))
    (or (:result run-state)
        run-state)))

(defn- violation-codes
  [result]
  (let [violations (get-in result [:details :violations])]
    (cond
      (seq violations) (->> violations
                            (keep :code)
                            distinct
                            vec)
      (:reason result) [(:reason result)]
      :else [])))

(defn- structured-violation-messages
  [violation]
  (case (:code violation)
    :sum-upper-below-net-min
    (when (and (opt-format/finite-number? (:sum-upper violation))
               (opt-format/finite-number? (:net-min violation)))
      [(str "Maximum possible net exposure is "
            (opt-format/format-decimal (:sum-upper violation))
            ", below the minimum of "
            (opt-format/format-decimal (:net-min violation))
            ".")
       "Lower Net Exposure Min, add eligible long assets, or raise Max Asset Weight."])

    :gross-floor-above-gross-max
    (when (and (opt-format/finite-number? (:gross-floor violation))
               (opt-format/finite-number? (:gross-max violation)))
      [(str "Gross Exposure Min of "
            (opt-format/format-decimal (:gross-floor violation))
            " is above the maximum of "
            (opt-format/format-decimal (:gross-max violation))
            ".")
       "Raise Gross Exposure (max) or lower Gross Exposure Min."])

    :gross-floor-exceeds-capacity
    (when (and (opt-format/finite-number? (:gross-floor violation))
               (opt-format/finite-number? (:gross-capacity violation)))
      [(str "Gross Exposure Min of "
            (opt-format/format-decimal (:gross-floor violation))
            " is higher than the "
            (opt-format/format-decimal (:gross-capacity violation))
            " the selected assets can reach.")
       "Lower Gross Exposure Min, add eligible assets, or raise Max Asset Weight."])
    nil))

(defn- violation-messages
  [result]
  (->> (get-in result [:details :violations])
       (mapcat (fn [violation]
                 (cons (:message violation)
                       (structured-violation-messages violation))))
       (remove str/blank?)
       distinct
       vec))

(defn- violation->control-keys
  [violation]
  (concat (get violation-control-keys (:code violation))
          (get violation-constraint-control-keys (:constraint-code violation))))

(defn highlighted-control-keys
  [result]
  (let [violations (get-in result [:details :violations])]
    (if (seq violations)
      (set (mapcat violation->control-keys violations))
      (set (mapcat violation-control-keys (violation-codes result))))))

(defn infeasible-banner
  [result highlighted-controls]
  (when result
    (let [codes (violation-codes result)
          messages (violation-messages result)
          labels (keep control-labels highlighted-controls)]
      [:section {:class ["rounded-xl"
                         "border"
                         "border-warning/50"
                         "bg-warning/10"
                         "p-4"
                         "text-warning"]
                 :data-role "portfolio-optimizer-infeasible-banner"}
       [:p {:class ["text-[0.75rem]"
                    "font-semibold"
                    "uppercase"
                    "tracking-[0.24em]"]}
        "Infeasible Optimization"]
       [:p {:class ["mt-2" "text-sm"]}
        (str "Reason: " (opt-format/keyword-label (:reason result) "unknown"))]
       (when-not (str/blank? (:message result))
         [:p {:class ["mt-2" "text-sm" "text-warning"]}
          (:message result)])
       (when (seq messages)
         (into [:ul {:class ["mt-3" "space-y-1" "text-xs"]}]
               (map (fn [message]
                      [:li message])
                    messages)))
       (when (seq codes)
         (into [:div {:class ["mt-3" "flex" "flex-wrap" "gap-2"]}]
               (map (fn [code]
                      [:span {:class ["rounded-full"
                                      "border"
                                      "border-warning/40"
                                      "px-2"
                                      "py-1"
                                      "text-xs"
                                      "font-semibold"]}
                       (opt-format/keyword-label code "unknown")])
                    codes)))
       (when (seq labels)
         [:p {:class ["mt-3" "text-xs"]}
          (str "Affected controls: " (str/join ", " labels))])])))
