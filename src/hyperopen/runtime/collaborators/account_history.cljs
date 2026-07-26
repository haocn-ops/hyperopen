(ns hyperopen.runtime.collaborators.account-history
  (:require [hyperopen.account.history.actions :as account-history-actions]))

(defn action-deps []
  {:select-account-info-tab account-history-actions/select-account-info-tab
   :select-account-info-twap-subtab account-history-actions/select-account-info-twap-subtab
   :set-funding-history-filters account-history-actions/set-funding-history-filters
   :toggle-funding-history-filter-open account-history-actions/toggle-funding-history-filter-open
   :toggle-funding-history-filter-coin account-history-actions/toggle-funding-history-filter-coin
   :add-funding-history-filter-coin account-history-actions/add-funding-history-filter-coin
   :handle-funding-history-coin-search-keydown
   account-history-actions/handle-funding-history-coin-search-keydown
   :reset-funding-history-filter-draft account-history-actions/reset-funding-history-filter-draft
   :apply-funding-history-filters account-history-actions/apply-funding-history-filters
   :view-all-funding-history account-history-actions/view-all-funding-history
   :export-funding-history-csv account-history-actions/export-funding-history-csv
   :set-funding-history-page-size account-history-actions/set-funding-history-page-size
   :set-funding-history-page account-history-actions/set-funding-history-page
   :next-funding-history-page account-history-actions/next-funding-history-page
   :prev-funding-history-page account-history-actions/prev-funding-history-page
   :set-funding-history-page-input account-history-actions/set-funding-history-page-input
   :apply-funding-history-page-input account-history-actions/apply-funding-history-page-input
   :handle-funding-history-page-input-keydown
   account-history-actions/handle-funding-history-page-input-keydown
   :set-trade-history-page-size account-history-actions/set-trade-history-page-size
   :set-trade-history-page account-history-actions/set-trade-history-page
   :next-trade-history-page account-history-actions/next-trade-history-page
   :prev-trade-history-page account-history-actions/prev-trade-history-page
   :set-trade-history-page-input account-history-actions/set-trade-history-page-input
   :apply-trade-history-page-input account-history-actions/apply-trade-history-page-input
   :handle-trade-history-page-input-keydown
   account-history-actions/handle-trade-history-page-input-keydown
   :sort-trade-history account-history-actions/sort-trade-history
   :toggle-trade-history-direction-filter-open
   account-history-actions/toggle-trade-history-direction-filter-open
   :set-trade-history-direction-filter account-history-actions/set-trade-history-direction-filter
   :sort-positions account-history-actions/sort-positions
   :toggle-positions-direction-filter-open
   account-history-actions/toggle-positions-direction-filter-open
   :set-positions-direction-filter account-history-actions/set-positions-direction-filter
   :sort-balances account-history-actions/sort-balances
   :sort-open-orders account-history-actions/sort-open-orders
   :toggle-open-orders-direction-filter-open
   account-history-actions/toggle-open-orders-direction-filter-open
   :set-open-orders-direction-filter account-history-actions/set-open-orders-direction-filter
   :sort-funding-history account-history-actions/sort-funding-history
   :sort-order-history account-history-actions/sort-order-history
   :toggle-order-history-filter-open account-history-actions/toggle-order-history-filter-open
   :set-order-history-status-filter account-history-actions/set-order-history-status-filter
   :set-account-info-coin-search account-history-actions/set-account-info-coin-search
   :toggle-account-info-mobile-card account-history-actions/toggle-account-info-mobile-card
   :set-order-history-page-size account-history-actions/set-order-history-page-size
   :set-order-history-page account-history-actions/set-order-history-page
   :next-order-history-page account-history-actions/next-order-history-page
   :prev-order-history-page account-history-actions/prev-order-history-page
   :set-order-history-page-input account-history-actions/set-order-history-page-input
   :apply-order-history-page-input account-history-actions/apply-order-history-page-input
   :handle-order-history-page-input-keydown
   account-history-actions/handle-order-history-page-input-keydown
   :refresh-order-history account-history-actions/refresh-order-history
   :set-hide-small-balances account-history-actions/set-hide-small-balances
   :open-position-tpsl-modal account-history-actions/open-position-tpsl-modal
   :close-position-tpsl-modal account-history-actions/close-position-tpsl-modal
   :handle-position-tpsl-modal-keydown account-history-actions/handle-position-tpsl-modal-keydown
   :set-position-tpsl-modal-field account-history-actions/set-position-tpsl-modal-field
   :set-position-tpsl-configure-amount account-history-actions/set-position-tpsl-configure-amount
   :set-position-tpsl-limit-price account-history-actions/set-position-tpsl-limit-price
   :submit-position-tpsl account-history-actions/submit-position-tpsl
   :trigger-close-all-positions account-history-actions/trigger-close-all-positions
   :open-position-reduce-popover account-history-actions/open-position-reduce-popover
   :close-position-reduce-popover account-history-actions/close-position-reduce-popover
   :handle-position-reduce-popover-keydown
   account-history-actions/handle-position-reduce-popover-keydown
   :set-position-reduce-popover-field account-history-actions/set-position-reduce-popover-field
   :set-position-reduce-size-percent account-history-actions/set-position-reduce-size-percent
   :set-position-reduce-limit-price-to-mid
   account-history-actions/set-position-reduce-limit-price-to-mid
   :submit-position-reduce-close account-history-actions/submit-position-reduce-close
   :open-position-margin-modal account-history-actions/open-position-margin-modal
   :close-position-margin-modal account-history-actions/close-position-margin-modal
   :handle-position-margin-modal-keydown account-history-actions/handle-position-margin-modal-keydown
   :set-position-margin-modal-field account-history-actions/set-position-margin-modal-field
   :set-position-margin-amount-percent account-history-actions/set-position-margin-amount-percent
   :set-position-margin-amount-to-max account-history-actions/set-position-margin-amount-to-max
   :submit-position-margin-update account-history-actions/submit-position-margin-update})
