(ns hyperopen.schema.runtime-registration.spectate-mode)

(def effect-binding-rows
  [[:effects/copy-spectate-link :copy-spectate-link]
   [:effects/clear-disconnected-account-lifecycle :clear-disconnected-account-lifecycle]
   [:effects/download-spectate-watchlist-file :download-spectate-watchlist-file]
   [:effects/pick-spectate-watchlist-file :pick-spectate-watchlist-file]
   [:effects/spectate-watchlist-feedback :spectate-watchlist-feedback]])

(def action-binding-rows
  [[:actions/open-spectate-mode-mobile-header-menu :open-spectate-mode-mobile-header-menu]
   [:actions/open-spectate-mode-modal :open-spectate-mode-modal]
   [:actions/close-spectate-mode-modal :close-spectate-mode-modal]
   [:actions/set-spectate-mode-search :set-spectate-mode-search]
   [:actions/set-spectate-mode-label :set-spectate-mode-label]
   [:actions/start-spectate-mode :start-spectate-mode]
   [:actions/stop-spectate-mode :stop-spectate-mode]
   [:actions/add-spectate-mode-watchlist-address :add-spectate-mode-watchlist-address]
   [:actions/remove-spectate-mode-watchlist-address :remove-spectate-mode-watchlist-address]
   [:actions/edit-spectate-mode-watchlist-address :edit-spectate-mode-watchlist-address]
   [:actions/clear-spectate-mode-watchlist-edit :clear-spectate-mode-watchlist-edit]
   [:actions/copy-spectate-mode-watchlist-address :copy-spectate-mode-watchlist-address]
   [:actions/copy-spectate-mode-watchlist-link :copy-spectate-mode-watchlist-link]
   [:actions/start-spectate-mode-watchlist-address :start-spectate-mode-watchlist-address]
   [:actions/export-spectate-mode-watchlist :export-spectate-mode-watchlist]
   [:actions/import-spectate-mode-watchlist :import-spectate-mode-watchlist]
   [:actions/apply-imported-spectate-watchlist :apply-imported-spectate-watchlist]])
