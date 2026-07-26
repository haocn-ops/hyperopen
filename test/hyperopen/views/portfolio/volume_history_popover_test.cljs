(ns hyperopen.views.portfolio.volume-history-popover-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.views.chart.d3.hover-state :as chart-hover-state]
            [hyperopen.views.portfolio-view :as portfolio-view]))

(use-fixtures :each
  (fn [f]
    (chart-hover-state/clear-hover-state!)
    (f)
    (chart-hover-state/clear-hover-state!)))

(defn- node-children [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn- find-first-node [node pred]
  (cond
    (vector? node)
    (let [children (node-children node)]
      (or (when (pred node) node)
          (some #(find-first-node % pred) children)))

    (seq? node)
    (some #(find-first-node % pred) node)

    :else nil))

(defn- collect-strings [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings (node-children node))
    (seq? node) (mapcat collect-strings node)
    :else []))

(defn- collect-classes [node]
  (cond
    (vector? node)
    (let [attrs (when (map? (second node)) (second node))
          own-classes (get attrs :class)]
      (concat own-classes
              (mapcat collect-classes (node-children node))))

    (seq? node)
    (mapcat collect-classes node)

    :else []))

(defn- button-with-text [node text]
  (find-first-node node #(and (= :button (first %))
                              (contains? (set (collect-strings %)) text))))

(def ^:private sample-state
  {:account {:mode :classic}
   :router {:path "/portfolio"}
   :portfolio-ui {:summary-scope :all
                  :summary-time-range :month
                  :chart-tab :account-value
                  :summary-scope-dropdown-open? false
                  :summary-time-range-dropdown-open? false
                  :performance-metrics-time-range-dropdown-open? false
                  :volume-history-anchor nil}
   :portfolio {:summary-by-key {:month {:pnlHistory [[1 10] [2 15]]
                                        :accountValueHistory [[1 100] [2 100]]
                                        :vlm 2255561.85}}
               :user-fees {:userCrossRate 0.00045
                           :userAddRate 0.00015
                           :dailyUserVlm [{:date "2026-04-15"
                                           :exchange 30000000000
                                           :userCross 1000000000
                                           :userAdd 2000000000}
                                          {:date "2026-04-16"
                                           :exchange 34490000000
                                           :userCross 2860000000
                                           :userAdd 2920000000}
                                          {:date "2026-04-17"
                                           :exchange 1000000000
                                           :userCross 1
                                           :userAdd 1}]}}
   :account-info {:selected-tab :balances
                  :loading false
                  :error nil
                  :hide-small-balances? false
                  :balances-sort {:column nil :direction :asc}
                  :positions-sort {:column nil :direction :asc}
                  :open-orders-sort {:column "Time" :direction :desc}}
   :orders {:open-orders []
            :open-orders-snapshot []
            :open-orders-snapshot-by-dex {}
            :fills []
            :fundings []
            :order-history []}
   :webdata2 {}
   :borrow-lend {:total-supplied-usd 0}
   :spot {:meta nil
          :clearinghouse-state nil}
   :perp-dex-clearinghouse {}})

(def ^:private sample-anchor
  {:left 100
   :right 160
   :top 240
   :bottom 264
   :width 60
   :height 24
   :viewport-width 800
   :viewport-height 700})

(deftest portfolio-volume-history-button-opens-anchored-popover-test
  (let [closed-view-node (portfolio-view/portfolio-view sample-state)
        closed-button (button-with-text closed-view-node "View Volume")
        closed-popover (find-first-node closed-view-node #(= "portfolio-volume-history-popover"
                                                             (get-in % [1 :data-role])))
        open-state (-> sample-state
                       (assoc-in [:portfolio-ui :volume-history-open?] true)
                       (assoc-in [:portfolio-ui :volume-history-anchor] sample-anchor))
        open-view-node (portfolio-view/portfolio-view open-state)
        popover (find-first-node open-view-node #(= "portfolio-volume-history-popover"
                                                    (get-in % [1 :data-role])))
        backdrop (find-first-node open-view-node #(= "portfolio-volume-history-backdrop"
                                                     (get-in % [1 :data-role])))
        close-button (find-first-node open-view-node #(= "portfolio-volume-history-close"
                                                         (get-in % [1 :data-role])))
        table (find-first-node open-view-node #(= "portfolio-volume-history-table"
                                                  (get-in % [1 :data-role])))
        colgroup (find-first-node table #(= :colgroup (first %)))
        exchange-header (find-first-node table #(= "portfolio-volume-history-header-exchange"
                                                   (get-in % [1 :data-role])))
        exchange-cell (find-first-node table #(= "portfolio-volume-history-cell-exchange"
                                                 (get-in % [1 :data-role])))
        table-frame (find-first-node open-view-node #(= "portfolio-volume-history-table-frame"
                                                        (get-in % [1 :data-role])))
        day-row (find-first-node open-view-node #(= "portfolio-volume-history-day-row"
                                                    (get-in % [1 :data-role])))
        maker-share (find-first-node open-view-node #(= "portfolio-volume-history-maker-share"
                                                        (get-in % [1 :data-role])))
        note (find-first-node open-view-node #(= "portfolio-volume-history-note"
                                                 (get-in % [1 :data-role])))
        total-row (find-first-node open-view-node #(= "portfolio-volume-history-total-row"
                                                      (get-in % [1 :data-role])))
        all-text (set (collect-strings popover))]
    (is (= [[:actions/open-portfolio-volume-history :event.currentTarget/bounds]]
           (get-in closed-button [1 :on :click])))
    (is (nil? closed-popover))
    (is (some? popover))
    (is (= "dialog" (get-in popover [1 :role])))
    (is (nil? (get-in popover [1 :aria-modal])))
    (is (= {:left "170px"
            :top "168px"
            :width "520px"
            :max-height "calc(100vh - 72px)"
            :overflow-y "auto"}
           (get-in popover [1 :style])))
    (is (= [[:actions/handle-portfolio-volume-history-keydown [:event/key]]]
           (get-in popover [1 :on :keydown])))
    (is (= [[:actions/close-portfolio-volume-history]]
           (get-in backdrop [1 :on :click])))
    (is (= [[:actions/close-portfolio-volume-history]]
           (get-in close-button [1 :on :click])))
    (is (some? table))
    (is (= ["w-full" "border-collapse"]
           (get-in table [1 :class])))
    (is (= [:colgroup
            [:col {:style {:width "30%"}}]
            [:col {:style {:width "22%"}}]
            [:col {:style {:width "24%"}}]
            [:col {:style {:width "24%"}}]]
           colgroup))
    (is (= ["px-1.5" "py-1.5" "font-normal" "leading-snug" "sm:px-2.5" "text-right"]
           (get-in exchange-header [1 :class])))
    (is (= ["px-1.5" "py-1" "text-right" "align-middle" "sm:px-2.5"]
           (get-in exchange-cell [1 :class])))
    (is (some? table-frame))
    (is (some? day-row))
    (is (some? total-row))
    (is (contains? (set (collect-classes total-row)) "text-trading-green"))
    (is (not (contains? (set (collect-classes total-row)) "text-warning")))
    (is (some? maker-share))
    (is (nil? note))
    (is (contains? all-text "Your Volume History"))
    (is (contains? all-text "Date (UTC)"))
    (is (contains? all-text "Exchange Volume"))
    (is (contains? all-text "Your Weighted Maker Volume"))
    (is (contains? all-text "Your Weighted Taker Volume"))
    (is (contains? all-text "Thu. 16. Apr. 2026"))
    (is (contains? all-text "Wed. 15. Apr. 2026"))
    (is (contains? all-text "Total"))
    (is (contains? all-text "$34.49b"))
    (is (contains? all-text "$64.49b"))
    (is (contains? all-text "$4.92b"))
    (is (contains? all-text "$3.86b"))
    (is (contains? all-text "Your 14 day maker volume share is 7.63%"))
    (is (not-any? #(str/includes? % "Dates do not include the current day")
                  all-text))))

(deftest portfolio-volume-history-cache-does-not-retain-open-popover-after-close-test
  (chart-hover-state/set-surface-hover-active! :portfolio true)
  (let [open-view-node (portfolio-view/portfolio-view
                        (-> sample-state
                            (assoc-in [:portfolio-ui :volume-history-open?] true)
                            (assoc-in [:portfolio-ui :volume-history-anchor] sample-anchor)))
        closed-view-node (portfolio-view/portfolio-view sample-state)]
    (is (some? (find-first-node open-view-node #(= "portfolio-volume-history-popover"
                                                   (get-in % [1 :data-role])))))
    (is (nil? (find-first-node closed-view-node #(= "portfolio-volume-history-popover"
                                                    (get-in % [1 :data-role])))))))
