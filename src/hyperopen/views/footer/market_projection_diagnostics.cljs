(ns hyperopen.views.footer.market-projection-diagnostics
  (:require [clojure.string :as str]))

(def ^:private market-flush-sparkline-sample-limit
  24)

(def ^:private market-flush-sparkline-width
  144)

(def ^:private market-flush-sparkline-height
  36)

(defn- sparkline-y
  [value max-value]
  (let [height market-flush-sparkline-height
        ratio (if (and (number? value) (pos? max-value))
                (/ (double value) (double max-value))
                0)]
    (-> (* (- 1 ratio) height)
        (max 0)
        (min height))))

(defn- sparkline-points
  [samples]
  (let [samples* (->> samples
                      (filter number?)
                      (take-last market-flush-sparkline-sample-limit)
                      vec)
        sample-count (count samples*)
        max-value (double (max 1 (or (reduce max 0 samples*) 1)))
        step-x (if (> sample-count 1)
                 (/ market-flush-sparkline-width (dec sample-count))
                 0)]
    {:samples samples*
     :max-value max-value
     :polyline-points (str/join
                       " "
                       (map-indexed
                        (fn [idx sample]
                          (let [x (js/Math.round (* idx step-x))
                                y (js/Math.round (sparkline-y sample max-value))]
                            (str x "," y)))
                        samples*))}))

(defn- flush-duration-sparkline
  [samples p95]
  (let [{:keys [samples max-value polyline-points]} (sparkline-points samples)]
    (if (seq samples)
      [:svg {:viewBox (str "0 0 "
                           market-flush-sparkline-width
                           " "
                           market-flush-sparkline-height)
             :preserveAspectRatio "none"
             :class ["h-9" "w-full"]}
       (when (number? p95)
         (let [y (js/Math.round (sparkline-y (min p95 max-value) max-value))]
           [:line {:x1 0
                   :x2 market-flush-sparkline-width
                   :y1 y
                   :y2 y
                   :stroke-width 1
                   :stroke "currentColor"
                   :class ["text-warning/70"]}]))
       [:polyline {:fill "none"
                   :stroke-width 2
                   :stroke "currentColor"
                   :class ["text-info"]
                   :points polyline-points}]]
      [:div {:class ["h-9" "text-xs" "text-base-content/60" "flex" "items-center"]}
       "No flush samples"])))

(defn- store-cell-tooltip
  [store-id-text]
  [:div {:class ["pointer-events-none"
                 "absolute"
                 "left-0"
                 "bottom-full"
                 "z-50"
                 "mb-1"
                 "opacity-0"
                 "transition-opacity"
                 "duration-150"
                 "group-hover:opacity-100"
                 "group-focus-within:opacity-100"]
         :style {:min-width "max-content"}}
   [:div {:class ["max-w-[20rem]"
                  "break-all"
                  "rounded"
                  "border"
                  "border-base-300"
                  "bg-base-100"
                  "px-2"
                  "py-1"
                  "text-xs"
                  "leading-4"
                  "text-base-content"
                  "spectate-lg"]}
    store-id-text]])

(defn- market-metric-label
  [label tooltip]
  [:span {:class ["relative" "group" "inline-flex" "items-center"]}
   [:span {:class ["cursor-help"
                   "border-b"
                   "border-dotted"
                   "border-base-content/30"]
           :title tooltip
           :tabindex 0}
    label]
   [:span {:class ["pointer-events-none"
                   "absolute"
                   "left-0"
                   "bottom-full"
                   "z-50"
                   "mb-1"
                   "max-w-[16rem]"
                   "rounded"
                   "border"
                   "border-base-300"
                   "bg-base-100"
                   "px-2"
                   "py-1"
                   "text-xs"
                   "leading-4"
                   "text-base-content"
                   "spectate-lg"
                   "opacity-0"
                   "transition-opacity"
                   "duration-150"
                   "group-hover:opacity-100"
                   "group-focus-within:opacity-100"]}
    tooltip]])

(defn render-store-section
  [market-projection]
  [:section {:class ["space-y-2"]
             :data-role "market-projection-diagnostics"}
   [:h3 {:class ["text-xs" "font-semibold" "uppercase" "tracking-wide" "text-base-content/70"]}
    "Market projection"]
   (if (seq (:stores market-projection))
     [:div {:class ["grid" "grid-cols-1" "gap-2"]}
      (for [{:keys [key
                    store-id-text
                    store-id-title
                    flush-count
                    metrics
                    durations
                    p95-flush-duration-ms
                    sample-count]} (:stores market-projection)]
        ^{:key key}
        [:article {:class ["rounded"
                           "border"
                           "border-base-300"
                           "bg-base-200/50"
                           "p-2"
                           "space-y-1.5"]}
         [:div {:class ["flex" "items-center" "justify-between" "gap-2"]}
          [:code {:class ["max-w-[11rem]" "truncate" "text-xs" "font-semibold"]
                  :title store-id-title}
           store-id-text]
          [:span {:class ["text-xs" "text-base-content/60"]}
           (str "Flushes: " flush-count)]]
         [:div {:class ["grid" "grid-cols-2" "gap-x-3" "gap-y-1" "text-xs"]}
          (mapcat (fn [{:keys [key label tooltip value-text]}]
                    [^{:key (str key "|label")}
                     (market-metric-label label tooltip)
                     ^{:key (str key "|value")}
                     [:span {:class ["text-right"]} value-text]])
                  metrics)]
         [:div {:class ["border-t" "border-base-300/60" "pt-1"]}
          (flush-duration-sparkline durations p95-flush-duration-ms)
          [:div {:class ["mt-0.5" "text-xs" "text-base-content/60" "flex" "justify-between"]}
           (market-metric-label
            (str "Samples: " sample-count)
            "Count of recent flush durations currently represented in the sparkline window.")
           (market-metric-label
            "Blue=flush ms, amber=p95"
            "Blue line shows each flush duration. Amber line shows the p95 threshold.")]]])]
     [:div {:class ["rounded" "border" "border-base-300" "bg-base-200/50" "p-3" "text-xs" "text-base-content/70"]}
      "No market projection telemetry yet."])])

(defn render-flushes-section
  [market-projection]
  [:section {:class ["space-y-2"]}
   [:h3 {:class ["text-xs" "font-semibold" "uppercase" "tracking-wide" "text-base-content/70"]}
    (str "Recent flushes (" (:flush-count market-projection) ")")]
   (if (seq (:flush-rows market-projection))
     [:div {:class ["rounded" "border" "border-base-300" "bg-base-200/50" "p-3" "overflow-visible"]}
      [:table {:class ["w-full" "text-xs"]}
       [:thead
        [:tr {:class ["text-base-content/60"]}
         [:th {:class ["py-1" "pr-2" "text-left"]} "Age"]
         [:th {:class ["py-1" "pr-2" "text-left"]} "Store"]
         [:th {:class ["py-1" "pr-2" "text-left"]} "Pending"]
         [:th {:class ["py-1" "pr-2" "text-left"]} "Overwrite"]
         [:th {:class ["py-1" "pr-2" "text-left"]} "Flush"]
         [:th {:class ["py-1" "pr-2" "text-left"]} "Queue wait"]]]
       [:tbody
        (for [{:keys [key
                      age-text
                      store-id-text
                      store-id-title
                      pending-count
                      overwrite-count
                      flush-duration-text
                      queue-wait-text]} (:flush-rows market-projection)]
          ^{:key key}
          [:tr {:class ["border-t" "border-base-300/40"]}
           [:td {:class ["py-1" "pr-2"]} age-text]
           [:td {:class ["py-1" "pr-2" "max-w-[10rem]"]}
            [:div {:class ["relative" "group" "max-w-[10rem]"]}
             [:span {:class ["block" "truncate" "cursor-help"]
                     :title store-id-title
                     :tabindex 0}
              store-id-text]
             (store-cell-tooltip store-id-text)]]
           [:td {:class ["py-1" "pr-2"]} (str pending-count)]
           [:td {:class ["py-1" "pr-2"]} (str overwrite-count)]
           [:td {:class ["py-1" "pr-2"]} flush-duration-text]
           [:td {:class ["py-1" "pr-2"]} queue-wait-text]])]]]
     [:div {:class ["rounded" "border" "border-base-300" "bg-base-200/50" "p-3" "text-xs" "text-base-content/70"]}
      "No flush events recorded in the telemetry ring."])])
