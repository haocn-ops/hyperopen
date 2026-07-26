(ns hyperopen.runtime.action-adapters.spectate-mode
  (:require [hyperopen.account.spectate-mode-actions :as spectate-mode-actions]))

(def open-spectate-mode-modal spectate-mode-actions/open-spectate-mode-modal)

(def close-spectate-mode-modal spectate-mode-actions/close-spectate-mode-modal)

(def set-spectate-mode-search spectate-mode-actions/set-spectate-mode-search)

(def set-spectate-mode-label spectate-mode-actions/set-spectate-mode-label)

(def start-spectate-mode spectate-mode-actions/start-spectate-mode)

(def stop-spectate-mode spectate-mode-actions/stop-spectate-mode)

(def add-spectate-mode-watchlist-address
  spectate-mode-actions/add-spectate-mode-watchlist-address)

(def remove-spectate-mode-watchlist-address
  spectate-mode-actions/remove-spectate-mode-watchlist-address)

(def edit-spectate-mode-watchlist-address
  spectate-mode-actions/edit-spectate-mode-watchlist-address)

(def clear-spectate-mode-watchlist-edit
  spectate-mode-actions/clear-spectate-mode-watchlist-edit)

(def copy-spectate-mode-watchlist-address
  spectate-mode-actions/copy-spectate-mode-watchlist-address)

(def copy-spectate-mode-watchlist-link
  spectate-mode-actions/copy-spectate-mode-watchlist-link)

(def start-spectate-mode-watchlist-address
  spectate-mode-actions/start-spectate-mode-watchlist-address)

(def export-spectate-mode-watchlist
  spectate-mode-actions/export-spectate-mode-watchlist)

(def import-spectate-mode-watchlist
  spectate-mode-actions/import-spectate-mode-watchlist)

(def apply-imported-spectate-watchlist
  spectate-mode-actions/apply-imported-spectate-watchlist)
