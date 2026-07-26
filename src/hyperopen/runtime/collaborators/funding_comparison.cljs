(ns hyperopen.runtime.collaborators.funding-comparison
  (:require [hyperopen.funding-comparison.actions :as funding-comparison-actions]))

(defn action-deps []
  {:load-funding-comparison-route funding-comparison-actions/load-funding-comparison-route
   :load-funding-comparison funding-comparison-actions/load-funding-comparison
   :set-funding-comparison-query funding-comparison-actions/set-funding-comparison-query
   :set-funding-comparison-timeframe funding-comparison-actions/set-funding-comparison-timeframe
   :set-funding-comparison-sort funding-comparison-actions/set-funding-comparison-sort})
