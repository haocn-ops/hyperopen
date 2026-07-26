(ns hyperopen.views.portfolio.account-tabs
  (:require [hyperopen.domain.account-ledger :as account-ledger]
            [hyperopen.portfolio.actions :as portfolio-actions]
            [hyperopen.views.account-info.shared :as account-shared]
            [hyperopen.views.account-info.table :as account-table]
            [hyperopen.views.account-info-view :as account-info-view]
            [hyperopen.views.portfolio.montecarlo.panel :as montecarlo-panel]
            [hyperopen.views.portfolio.performance-metrics-view :as performance-metrics-view]
            [hyperopen.views.portfolio.vm.montecarlo :as montecarlo-vm]))

(def ^:private performance-metrics-panel-height
  "min(44rem, calc(100dvh - 24rem))")

(def ^:private portfolio-account-panel-style
  {:height performance-metrics-panel-height
   :max-height performance-metrics-panel-height})

(def ^:private portfolio-account-tab-click-actions-by-tab
  (into
   {:deposits-withdrawals [[:actions/set-portfolio-account-info-tab :deposits-withdrawals]]
    :performance-metrics [[:actions/set-portfolio-account-info-tab :performance-metrics]]
    :monte-carlo [[:actions/set-portfolio-account-info-tab :monte-carlo]]}
   (map (fn [tab]
          [tab
           [[:actions/set-portfolio-account-info-tab tab]
            [:actions/select-account-info-tab tab]]])
        account-info-view/available-tabs)))

(def ^:private portfolio-account-tab-order
  [:performance-metrics
   :monte-carlo
   :balances
   :positions
   :open-orders
   :funding-history
   :deposits-withdrawals
   :trade-history
   :order-history
   :twap
   :outcomes])

(def ^:private portfolio-account-tab-label-overrides
  {:funding-history "Interest"})

(def ^:private deposits-withdrawals-grid-style
  {:grid-template-columns "minmax(9rem,1.3fr) minmax(6rem,.8fr) minmax(8rem,1fr) minmax(8rem,1fr) minmax(8rem,1fr) minmax(11rem,1fr) minmax(5rem,.7fr)"})

(def ^:private deposits-withdrawals-header-classes
  ["grid" "min-w-[880px]" "gap-2" "bg-base-200" "px-3" "py-1" "text-sm" "font-medium"])

(def ^:private deposits-withdrawals-row-classes
  ["grid" "min-w-[880px]" "gap-2" "px-3" "py-px" "text-sm" "hover:bg-base-300"])

(defn- signed-amount-class
  [signed-amount]
  (cond
    (and (number? signed-amount) (pos? signed-amount)) "text-success"
    (and (number? signed-amount) (neg? signed-amount)) "text-error"
    :else "text-trading-text"))

(defn- empty-ledger-state [message]
  [:div.flex.flex-col.items-center.justify-center.py-12.text-base-content
   [:div.text-lg.font-medium message]
   [:div {:class ["mt-2" "text-sm" "text-trading-text-secondary"]} "No data available"]])

(defn- transaction-link
  [{:keys [explorer-url hash]}]
  (when (and explorer-url hash)
    [:a {:href explorer-url
         :target "_blank"
         :rel "noreferrer"
         :class ["inline-flex"
                 "items-center"
                 "text-trading-green"
                 "transition-colors"
                 "hover:text-trading-green/80"
                 "focus:outline-none"
                 "focus:ring-0"
                 "focus:ring-offset-0"]
         :aria-label "Open transaction in Hyperliquid explorer"
         :title hash}
     (account-shared/external-link-icon ["h-3" "w-3" "shrink-0"])]))

(defn- ledger-row
  [{:keys [id time-ms status-label action-label source-label destination-label
           amount-text fee-text signed-amount] :as row}]
  ^{:key id}
  [:div {:class deposits-withdrawals-row-classes
         :style deposits-withdrawals-grid-style}
   [:div {:class ["flex" "items-center" "gap-1.5" "min-w-0"]}
    [:span {:class ["truncate"]} (account-shared/format-funding-history-time time-ms)]
    (transaction-link row)]
   [:div {:class ["text-left" "text-trading-text"]} (or status-label "--")]
   [:div {:class ["text-left" "font-semibold" "text-trading-text"]} (or action-label "--")]
   [:div {:class ["text-left" "text-trading-text"]} (or source-label "--")]
   [:div {:class ["text-left" "text-trading-text"]} (or destination-label "--")]
   [:div {:class ["text-left" "num" (signed-amount-class signed-amount)]} (or amount-text "--")]
   [:div {:class ["text-left" "num" "text-trading-text"]} (or fee-text "--")]])

(defn- deposits-withdrawals-table [state]
  (let [rows (account-ledger/merge-ledger-rows
              (get-in state [:portfolio :ledger-updates])
              (get-in state [:orders :ledger]))
        loading? (boolean (get-in state [:portfolio :ledger-loading?]))
        error (get-in state [:portfolio :ledger-error])
        header [:div {:class deposits-withdrawals-header-classes
                      :style deposits-withdrawals-grid-style}
                [:div (account-table/non-sortable-header "Time")]
                [:div.text-left (account-table/non-sortable-header "Status")]
                [:div.text-left (account-table/non-sortable-header "Action")]
                [:div.text-left (account-table/non-sortable-header "Source")]
                [:div.text-left (account-table/non-sortable-header "Destination")]
                [:div.text-left (account-table/non-sortable-header "Account Value Change")]
                [:div.text-left (account-table/non-sortable-header "Fee")]]
        body-rows (cond
                    error
                    [(empty-ledger-state (str error))]

                    loading?
                    [(empty-ledger-state "Loading deposits and withdrawals...")]

                    (seq rows)
                    (map ledger-row rows)

                    :else
                    [(empty-ledger-state "No deposits or withdrawals")])]
    [:div {:class ["h-full" "min-h-0"]
           :data-role "portfolio-deposits-withdrawals-table"}
     (account-table/tab-table-content
      header
      body-rows)]))

(defn account-info-options [state view-model trader-portfolio-route?]
  (let [extra-tabs (-> (cond-> [{:id :performance-metrics
                                 :label "Performance Metrics"
                                 :panel-classes ["min-h-0"]
                                 :panel-style portfolio-account-panel-style
                                 :render (fn [_]
                                           (performance-metrics-view/performance-metrics-card
                                            (assoc (:performance-metrics view-model)
                                                   :time-range-selector (get-in view-model [:selectors :performance-metrics-time-range]))))}]
                         (not trader-portfolio-route?)
                         (into [{:id :deposits-withdrawals
                                 :label "Account Activity"
                                 :render (fn [_]
                                           (deposits-withdrawals-table state))}]))
                       (conj {:id :monte-carlo
                              :label "Monte Carlo"
                              :panel-classes ["min-h-0"]
                              :panel-style {}
                              :render (fn [_]
                                        (montecarlo-panel/monte-carlo-card
                                         (montecarlo-vm/montecarlo-model
                                          state
                                          (:monte-carlo view-model))))}))]
    {:extra-tabs extra-tabs
     :default-panel-classes ["min-h-0"]
     :default-panel-style portfolio-account-panel-style
     :selected-tab-override (get-in state [:portfolio-ui :account-info-tab] portfolio-actions/default-account-info-tab)
     :default-selected-tab portfolio-actions/default-account-info-tab
     :tab-click-actions-by-tab portfolio-account-tab-click-actions-by-tab
     :tab-label-overrides portfolio-account-tab-label-overrides
     :tab-order portfolio-account-tab-order}))
