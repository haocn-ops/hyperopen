(ns hyperopen.views.staking.history
  (:require [hyperopen.utils.formatting :as fmt]
            [hyperopen.views.staking.shared :as shared]))

(defn- history-table
  [rows columns empty-text row-render]
  [:div {:class ["overflow-x-auto" "rounded-[10px]" "border" "border-ho-surface"]}
   [:table {:class ["min-w-full" "bg-ho-bg"]}
    [:thead
     [:tr {:class ["text-xs" "text-ho-text-secondary"]}
      (for [column columns]
        ^{:key column}
        [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]}
         column])]]
    [:tbody
     (if (seq rows)
       (map row-render rows)
       [:tr
        [:td {:col-span (count columns)
              :class ["px-3" "py-6" "text-center" "text-sm" "text-ho-text-secondary"]}
         empty-text]])]]])

(defn rewards-history-panel
  [{:keys [rewards loading?]}]
  (history-table
   rewards
   ["Time" "Source" "Amount"]
   (if loading?
     "Loading staking rewards..."
     "No staking rewards found.")
   (fn [{:keys [time-ms source total-amount]}]
     ^{:key (str "reward-" time-ms "-" source)}
     [:tr {:class ["border-b" "border-base-300/50" "text-sm"]}
      [:td {:class ["px-3" "py-2.5" "num"]}
       (or (fmt/format-local-date-time time-ms) "--")]
      [:td {:class ["px-3" "py-2.5"]}
       (or source "--")]
      [:td {:class ["px-3" "py-2.5" "num"]}
       (shared/format-balance-hype total-amount)]])))

(defn action-history-panel
  [{:keys [history loading?]}]
  (history-table
   history
   ["Time" "Action" "Amount" "Status" "Tx"]
   (if loading?
     "Loading staking action history..."
     "No staking actions found.")
   (fn [{:keys [time-ms kind amount status hash]}]
     ^{:key (str "history-" time-ms "-" hash)}
     [:tr {:class ["border-b" "border-base-300/50" "text-sm"]}
      [:td {:class ["px-3" "py-2.5" "num"]}
       (or (fmt/format-local-date-time time-ms) "--")]
      [:td {:class ["px-3" "py-2.5"]}
       kind]
      [:td {:class ["px-3" "py-2.5" "num"]}
       (shared/format-balance-hype amount)]
      [:td {:class ["px-3" "py-2.5"]}
       (or status "--")]
      [:td {:class ["px-3" "py-2.5" "num"]}
       (or (some-> hash (subs 0 (min 10 (count hash)))) "--")]])))
