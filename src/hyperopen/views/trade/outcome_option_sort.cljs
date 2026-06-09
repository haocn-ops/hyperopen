(ns hyperopen.views.trade.outcome-option-sort
  (:require [clojure.string :as str]))

(def ^:private sort-columns
  #{:label :chance :price :volume :open-interest})

(defn normalize-column
  [column]
  (let [column* (cond
                  (keyword? column) column
                  (string? column) (keyword column)
                  :else nil)]
    (when (contains? sort-columns column*)
      column*)))

(defn normalize-direction
  [direction]
  (let [direction* (cond
                     (keyword? direction) direction
                     (string? direction) (keyword direction)
                     :else nil)]
    (if (contains? #{:asc :desc} direction*)
      direction*
      :asc)))

(defn- safe-number
  [value]
  (cond
    (number? value) value
    (string? value) (let [parsed (js/parseFloat value)]
                      (when-not (js/isNaN parsed)
                        parsed))
    :else nil))

(defn- sort-value
  [option column]
  (case column
    :label (str/lower-case (str (or (:label option) "")))
    :chance (safe-number (:mark option))
    :price (safe-number (:mark option))
    :volume (safe-number (:volume24h option))
    :open-interest (safe-number (:openInterest option))
    (str/lower-case (str (or (:label option) "")))))

(defn- compare-sort-values
  [a b]
  (cond
    (and (nil? a) (nil? b)) 0
    (nil? a) 1
    (nil? b) -1
    :else (compare a b)))

(defn- compare-options
  [column direction a b]
  (let [a-value (sort-value a column)
        b-value (sort-value b column)
        primary (compare-sort-values a-value b-value)
        directed (if (= direction :desc) (- primary) primary)]
    (if (zero? directed)
      (compare-sort-values (sort-value a :label)
                           (sort-value b :label))
      directed)))

(defn sorted-options
  [options column direction]
  (if-let [column* (normalize-column column)]
    (vec
     (sort #(compare-options column*
                             (normalize-direction direction)
                             %1
                             %2)
           options))
    options))

(defn- option-matches-query?
  [query option]
  (let [query* (some-> query str str/trim str/lower-case)]
    (or (not (seq query*))
        (str/includes? (str/lower-case (str (or (:label option) "")))
                       query*))))

(defn filtered-sorted-options
  [options query column direction]
  (sorted-options (filterv #(option-matches-query? query %) options)
                  column
                  direction))

(defn header
  [label column active-column active-direction outcome-handlers]
  (let [active? (= column active-column)
        direction (normalize-direction active-direction)
        indicator (when active?
                    (if (= direction :asc) "↑" "↓"))
        on-sort (:on-sort-option-column outcome-handlers)]
    [:button {:type "button"
              :aria-label (str "Sort outcomes by " (name column))
              :data-role (str "outcome-option-sort-" (name column))
              :class ["flex"
                      "min-w-0"
                      "items-center"
                      "gap-1"
                      "text-left"
                      "text-xs"
                      "font-medium"
                      "text-[#949E9C]"
                      "transition-colors"
                      "hover:text-[#F6FEFD]"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"]
              :on (when on-sort
                    {:click (on-sort column)})}
     [:span {:class ["truncate"]} label]
     (when indicator
       [:span {:class ["text-[#50D2C1]"]} indicator])]))
