(ns hyperopen.views.portfolio.optimize.setup-model-controls
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]))

(def ^:private model-help
  {:historical-mean "Uses the arithmetic mean of historical returns for each selected asset."
   :ew-mean "Uses exponentially weighted historical returns so recent observations count more."
   :black-litterman "Combines market-implied returns with your Black-Litterman views and confidence inputs."
   :diagonal-shrink "Shrinks the covariance estimate toward a diagonal model to reduce noisy cross-asset correlations."
   :ledoit-wolf-dense "Estimates the optimal shrinkage intensity from your data, raising it automatically as assets grow relative to available history."
   :mixed-frequency "Keeps dense assets on daily history while aggregating them over sparse asset intervals when needed."
   :sample-covariance "Uses the raw historical covariance matrix from the selected asset return history."})

(defn model-section
  [draft]
  (let [return-kind (get-in draft [:return-model :kind])
        risk-kind (get-in draft [:risk-model :kind])]
    (controls/disclosure-panel
     "portfolio-optimizer-return-risk-panel"
     (controls/disclosure-heading "Return / Risk Model" (controls/labelize return-kind))
     [:div {:class ["mt-3" "space-y-3"] :data-role "portfolio-optimizer-setup-model-grid"}
      [:div {:class ["optimizer-model-panel"]
             :data-role "portfolio-optimizer-return-model-panel"}
       [:p {:class controls/eyebrow-class} "Expected returns"]
       [:div {:class ["optimizer-model-segment-group" "mt-2" "grid" "grid-cols-3" "border"
                      "border-base-300"]}
        (controls/segmented-button "Historical" "Historical Mean" (:historical-mean model-help) :start (= :historical-mean return-kind)
                                   "portfolio-optimizer-return-model-historical-mean"
                                   [:actions/set-portfolio-optimizer-return-model-kind :historical-mean])
        (controls/segmented-button "EW Mean" nil (:ew-mean model-help) :center (= :ew-mean return-kind)
                                   "portfolio-optimizer-return-model-ew-mean"
                                   [:actions/set-portfolio-optimizer-return-model-kind :ew-mean])
        (controls/segmented-button "Use my views" nil (:black-litterman model-help) :end (= :black-litterman return-kind)
                                   "portfolio-optimizer-return-model-black-litterman"
                                   [:actions/set-portfolio-optimizer-return-model-kind :black-litterman])
        [:span {:class ["sr-only"]} "Black-Litterman"]]
       [:p {:class ["mt-2" "text-[0.6875rem]" "text-trading-muted"]}
        (case return-kind
          :black-litterman "Black-Litterman stays here as a return-model mode, not an objective."
          :ew-mean "Exponentially weighted returns emphasize recent history."
          "Average of past returns. Simple and auditable for first runs.")]]
      [:div {:class ["optimizer-model-column"]
             :data-role "portfolio-optimizer-setup-model-column"}
       [:div {:class ["optimizer-model-panel"]
              :data-role "portfolio-optimizer-risk-model-panel"}
        [:p {:class controls/eyebrow-class} "Risk model"]
        [:div {:class ["optimizer-model-segment-group" "mt-2" "grid" "grid-cols-4" "border"
                       "border-base-300"]}
         (controls/segmented-button "Stabilized Covariance" "Diagonal Shrink" (:diagonal-shrink model-help) :start (= :diagonal-shrink risk-kind)
                                    "portfolio-optimizer-risk-model-diagonal-shrink"
                                    [:actions/set-portfolio-optimizer-risk-model-kind :diagonal-shrink])
         (controls/segmented-button "Ledoit-Wolf" "Ledoit-Wolf Dense" (:ledoit-wolf-dense model-help) :center (= :ledoit-wolf-dense risk-kind)
                                    "portfolio-optimizer-risk-model-ledoit-wolf-dense"
                                    [:actions/set-portfolio-optimizer-risk-model-kind :ledoit-wolf-dense])
         (controls/segmented-button "Mixed Frequency" nil (:mixed-frequency model-help) :center (= :mixed-frequency risk-kind)
                                    "portfolio-optimizer-risk-model-mixed-frequency"
                                    [:actions/set-portfolio-optimizer-risk-model-kind :mixed-frequency])
         (controls/segmented-button "Sample Covariance" nil (:sample-covariance model-help) :end (= :sample-covariance risk-kind)
                                    "portfolio-optimizer-risk-model-sample-covariance"
                                    [:actions/set-portfolio-optimizer-risk-model-kind :sample-covariance])]]]])))
