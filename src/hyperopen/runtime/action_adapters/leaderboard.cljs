(ns hyperopen.runtime.action-adapters.leaderboard
  (:require [hyperopen.leaderboard.actions :as leaderboard-actions]))

(defn load-leaderboard-route-action
  [state path]
  (leaderboard-actions/load-leaderboard-route state path))

(defn set-leaderboard-query-action
  [state query]
  (leaderboard-actions/set-leaderboard-query state query))

(defn set-leaderboard-timeframe-action
  [state timeframe]
  (leaderboard-actions/set-leaderboard-timeframe state timeframe))

(defn set-leaderboard-sort-action
  [state sort-column]
  (leaderboard-actions/set-leaderboard-sort state sort-column))

(defn set-leaderboard-page-size-action
  [state page-size]
  (leaderboard-actions/set-leaderboard-page-size state page-size))

(defn toggle-leaderboard-page-size-dropdown-action
  [state]
  (leaderboard-actions/toggle-leaderboard-page-size-dropdown state))

(defn close-leaderboard-page-size-dropdown-action
  [state]
  (leaderboard-actions/close-leaderboard-page-size-dropdown state))

(defn set-leaderboard-page-action
  [state page max-page]
  (leaderboard-actions/set-leaderboard-page state page max-page))

(defn next-leaderboard-page-action
  [state max-page]
  (leaderboard-actions/next-leaderboard-page state max-page))

(defn prev-leaderboard-page-action
  [state max-page]
  (leaderboard-actions/prev-leaderboard-page state max-page))
