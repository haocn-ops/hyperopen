(ns hyperopen.runtime.collaborators.leaderboard
  (:require [hyperopen.leaderboard.actions :as leaderboard-actions]
            [hyperopen.runtime.action-adapters :as action-adapters]))

(defn action-deps []
  {:load-leaderboard-route action-adapters/load-leaderboard-route-action
   :load-leaderboard leaderboard-actions/load-leaderboard
   :set-leaderboard-query action-adapters/set-leaderboard-query-action
   :set-leaderboard-timeframe action-adapters/set-leaderboard-timeframe-action
   :set-leaderboard-sort action-adapters/set-leaderboard-sort-action
   :set-leaderboard-page-size action-adapters/set-leaderboard-page-size-action
   :toggle-leaderboard-page-size-dropdown
   action-adapters/toggle-leaderboard-page-size-dropdown-action
   :close-leaderboard-page-size-dropdown
   action-adapters/close-leaderboard-page-size-dropdown-action
   :set-leaderboard-page action-adapters/set-leaderboard-page-action
   :next-leaderboard-page action-adapters/next-leaderboard-page-action
   :prev-leaderboard-page action-adapters/prev-leaderboard-page-action})
