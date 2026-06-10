(ns hyperopen.views.vaults.detail.activity.tables
  (:require [clojure.string :as str]
            [hyperopen.router :as router]
            [hyperopen.views.vaults.detail.activity.table-chrome :as chrome]
            [hyperopen.views.vaults.detail.format :as vf]
            [hyperopen.wallet.core :as wallet]))

(defn- position-row-key
  [{:keys [coin size entry-price]}]
  (str "position-" coin "-" size "-" entry-price))

(defn- position-coin-click-actions
  [coin]
  (when-let [coin* (some-> coin str str/trim not-empty)]
    [[:actions/select-asset coin*]
     [:actions/navigate (router/trade-route-path coin*)]]))

(defn- position-coin-cell
  [{:keys [coin leverage side-key]}]
  [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap"])
        :style (chrome/side-coin-cell-style side-key)}
   (if-let [click-actions (position-coin-click-actions coin)]
     [:button {:type "button"
               :class ["inline-flex"
                       "items-center"
                       "gap-1"
                       "bg-transparent"
                       "p-0"
                       "text-left"
                       "focus:outline-none"
                       "focus:ring-0"
                       "focus:ring-offset-0"]
               :data-role "vault-detail-position-coin-select"
               :on {:click click-actions}}
      [:span {:class [(chrome/side-coin-tone-class side-key)]}
       coin]
      (when (number? leverage)
        [:span {:class [(chrome/side-tone-class side-key)]}
         (str leverage "x")])]
     [:span {:class [(chrome/side-coin-tone-class side-key)]}
      (or coin "—")])])

(defn- position-value-text
  [position-value]
  (if (number? position-value)
    (str (vf/format-currency position-value) " USDC")
    "—"))

(defn- position-pnl-text
  [pnl roe]
  (if (number? pnl)
    (str (vf/format-currency pnl {:missing "—"}) " (" (vf/format-percent roe) ")")
    "—"))

(defn- position-liq-price-text
  [liq-price]
  (if (number? liq-price)
    (vf/format-price liq-price)
    "N/A"))

(defn- position-margin-text
  [margin]
  (if (number? margin)
    (str (vf/format-currency margin) " (Cross)")
    "—"))

(defn- position-row
  [{:keys [size side-key position-value entry-price mark-price pnl roe liq-price margin funding] :as row}]
  [:tr {:class chrome/activity-row-class}
   (position-coin-cell row)
   [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/side-tone-class side-key)])}
    (vf/format-size size)]
   [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])}
    (position-value-text position-value)]
   [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])}
    (vf/format-price entry-price)]
   [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])}
    (vf/format-price mark-price)]
   [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/position-pnl-class pnl)])}
    (position-pnl-text pnl roe)]
   [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])}
    (position-liq-price-text liq-price)]
   [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])}
    (position-margin-text margin)]
   [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/position-pnl-class funding)])}
    (vf/format-currency funding)]])

(defn balances-table [rows sort-state columns]
  [:div {:class ["overflow-auto" "max-h-[540px]"]}
   [:table {:class ["w-full" "min-w-[760px]" "border-collapse"]}
    (chrome/table-header :balances columns sort-state)
    [:tbody
     (if (seq rows)
       (for [{:keys [coin total available usdc-value]} rows]
         ^{:key (str "balance-" coin "-" total)}
         [:tr {:class chrome/activity-row-class}
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" "text-ho-text"])}
           (or coin "—")]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])}
           (vf/format-balance-quantity total)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap"])}
           [:span {:class (into ["text-ho-text"] (chrome/interactive-value-class))}
            (vf/format-balance-quantity available)]]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])}
           (vf/format-currency usdc-value)]])
       (chrome/empty-table-row (count columns) "No balances available."))]]])

(defn positions-table [rows sort-state columns]
  [:div {:class ["overflow-auto" "max-h-[540px]"]}
   [:table {:class ["w-full" "min-w-[1285px]" "border-collapse"]}
    (chrome/table-header :positions columns sort-state)
    [:tbody
     (if (seq rows)
       (for [row rows]
         ^{:key (position-row-key row)}
         (position-row row))
       (chrome/empty-table-row (count columns) "No active positions."))]]])

(defn open-orders-table [rows sort-state columns]
  [:div {:class ["overflow-auto" "max-h-[540px]"]}
   [:table {:class ["w-full" "min-w-[960px]" "border-collapse"]}
    (chrome/table-header :open-orders columns sort-state)
    [:tbody
     (if (seq rows)
       (for [{:keys [time-ms coin side side-key size price trigger-price]} rows]
         ^{:key (str "open-order-" time-ms "-" coin "-" size "-" price)}
         [:tr {:class chrome/activity-row-class}
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap"])} (vf/format-time time-ms)]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap"])
                :style (chrome/side-coin-cell-style side-key)}
           [:span {:class [(chrome/side-coin-tone-class side-key)]}
            (or coin "—")]]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" (chrome/side-tone-class side-key)])} (or side "—")]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/side-tone-class side-key)])} (vf/format-size size)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-price price)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-price trigger-price)]])
       (chrome/empty-table-row (count columns) "No open orders."))]]])

(defn twap-table [rows sort-state columns]
  [:div {:class ["overflow-auto" "max-h-[540px]"]}
   [:table {:class ["w-full" "min-w-[1260px]" "border-collapse"]}
    (chrome/table-header :twap columns sort-state)
    [:tbody
     (if (seq rows)
       (for [{:keys [coin size executed-size average-price running-label reduce-only? creation-time-ms]} rows]
         ^{:key (str "twap-" coin "-" creation-time-ms "-" size)}
         [:tr {:class chrome/activity-row-class}
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" "text-ho-text"])} (or coin "—")]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-size size)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-size executed-size)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-price average-price)]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" "text-ho-text"])} (or running-label "—")]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" (if (true? reduce-only?) "text-ho-sell-hi" "text-[#1fa67d]")])}
           (if (true? reduce-only?) "Yes" "No")]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-time creation-time-ms)]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" "text-[#8f9ea5]"])} "—"]])
       (chrome/empty-table-row (count columns) "No TWAPs yet."))]]])

(defn fills-table [rows loading? error sort-state columns]
  [:div {:class ["overflow-auto" "max-h-[540px]"]}
   [:table {:class ["w-full" "min-w-[1180px]" "border-collapse"]}
    (chrome/table-header :trade-history columns sort-state)
    [:tbody
     (cond
       (seq error)
       (chrome/error-table-row (count columns) error)

       loading?
       (chrome/empty-table-row (count columns) "Loading trade history...")

       (seq rows)
       (for [{:keys [time-ms coin side side-key size price trade-value fee closed-pnl]} rows]
         ^{:key (str "fill-" time-ms "-" coin "-" size "-" price)}
         [:tr {:class chrome/activity-row-class}
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap"])} (vf/format-time time-ms)]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap"])
                :style (chrome/side-coin-cell-style side-key)}
           [:span {:class [(chrome/side-coin-tone-class side-key)]}
            (or coin "—")]]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" (chrome/side-tone-class side-key)])}
           (or side "—")]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-price price)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-size size)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-currency trade-value)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-currency fee)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/position-pnl-class closed-pnl)])}
           (vf/format-currency closed-pnl)]])

       :else
       (chrome/empty-table-row (count columns) "No recent fills."))]]])

(defn funding-history-table [rows loading? error sort-state columns]
  [:div {:class ["overflow-auto" "max-h-[540px]"]}
   [:table {:class ["w-full" "min-w-[920px]" "border-collapse"]}
    (chrome/table-header :funding-history columns sort-state)
    [:tbody
     (cond
       (seq error)
       (chrome/error-table-row (count columns) error)

       loading?
       (chrome/empty-table-row (count columns) "Loading funding history...")

       (seq rows)
       (for [{:keys [time-ms coin funding-rate position-size side-key payment]} rows]
         ^{:key (str "funding-" time-ms "-" coin "-" funding-rate "-" payment)}
         [:tr {:class chrome/activity-row-class}
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap"])} (vf/format-time time-ms)]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap"])
                :style (chrome/side-coin-cell-style side-key)}
           [:span {:class [(chrome/side-coin-tone-class side-key)]}
            (or coin "—")]]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-funding-rate funding-rate)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/side-tone-class side-key)])} (vf/format-size position-size)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/position-pnl-class payment)])}
           (vf/format-currency payment)]])

       :else
       (chrome/empty-table-row (count columns) "No funding history available."))]]])

(defn order-history-table [rows loading? error sort-state columns]
  [:div {:class ["overflow-auto" "max-h-[540px]"]}
   [:table {:class ["w-full" "min-w-[1040px]" "border-collapse"]}
    (chrome/table-header :order-history columns sort-state)
    [:tbody
     (cond
       (seq error)
       (chrome/error-table-row (count columns) error)

       loading?
       (chrome/empty-table-row (count columns) "Loading order history...")

       (seq rows)
       (for [{:keys [time-ms coin side side-key type size price status status-key]} rows]
         ^{:key (str "order-history-" time-ms "-" coin "-" side "-" size)}
         [:tr {:class chrome/activity-row-class}
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap"])} (vf/format-time time-ms)]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap"])
                :style (chrome/side-coin-cell-style side-key)}
           [:span {:class [(chrome/side-coin-tone-class side-key)]}
            (or coin "—")]]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" (chrome/side-tone-class side-key)])} (or side "—")]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" "text-ho-text"])} (or type "—")]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-size size)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-price price)]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" (chrome/status-tone-class status-key)])}
           (or status "—")]])

       :else
       (chrome/empty-table-row (count columns) "No order history available."))]]])

(defn ledger-table [rows loading? error sort-state columns]
  [:div {:class ["overflow-auto" "max-h-[540px]"]}
   [:table {:class ["w-full" "min-w-[880px]" "border-collapse"]}
    (chrome/table-header :deposits-withdrawals columns sort-state)
    [:tbody
     (cond
       (seq error)
       (chrome/error-table-row (count columns) error)

       loading?
       (chrome/empty-table-row (count columns) "Loading deposits and withdrawals...")

       (seq rows)
       (for [{:keys [time-ms type-key type-label amount signed-amount hash]} rows]
         ^{:key (str "ledger-" time-ms "-" hash)}
         [:tr {:class chrome/activity-row-class}
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap"])} (vf/format-time time-ms)]
          [:td {:class (into chrome/activity-cell-class ["whitespace-nowrap" (chrome/ledger-type-tone-class type-key)])}
           (or type-label "—")]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/position-pnl-class signed-amount)])}
           (vf/format-currency (or signed-amount amount))]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-accent-bright"])
                :title hash}
           [:span {:class (chrome/interactive-value-class)}
            (vf/short-hash hash)]]])

       :else
       (chrome/empty-table-row (count columns) "No deposits or withdrawals available."))]]])

(defn depositors-table [rows sort-state columns]
  [:div {:class ["overflow-auto" "max-h-[540px]"]}
   [:table {:class ["w-full" "min-w-[980px]" "border-collapse"]}
    (chrome/table-header :depositors columns sort-state)
    [:tbody
     (if (seq rows)
       (for [{:keys [address vault-amount unrealized-pnl all-time-pnl days-following]} rows]
         ^{:key (str "depositor-" address)}
         [:tr {:class chrome/activity-row-class}
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (or (wallet/short-addr address) "—")]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])} (vf/format-currency vault-amount)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/position-pnl-class unrealized-pnl)])}
           (vf/format-currency unrealized-pnl)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" (chrome/position-pnl-class all-time-pnl)])}
           (vf/format-currency all-time-pnl)]
          [:td {:class (into chrome/activity-cell-num-class ["whitespace-nowrap" "text-ho-text"])}
           (if (number? days-following)
             (str days-following)
             "—")]])
       (chrome/empty-table-row (count columns) "No depositors available."))]]])
