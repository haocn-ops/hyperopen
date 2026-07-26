(ns hyperopen.views.trading-chart.utils.chart-interop.open-order-overlays
  (:require [hyperopen.views.account-info.shared :as account-shared]
            [hyperopen.views.trading-chart.utils.chart-interop.open-order-overlays.presentation :as presentation]
            [hyperopen.views.trading-chart.utils.chart-interop.open-order-overlays.rows :as rows]
            [hyperopen.views.trading-chart.utils.chart-interop.open-order-overlays.support :as support]))

(def ^:dynamic *schedule-overlay-repaint-frame!*
  (fn [callback]
    (if-let [raf (some-> js/globalThis (aget "requestAnimationFrame"))]
      (raf callback)
      (do
        (callback)
        nil))))

(def ^:dynamic *cancel-overlay-repaint-frame!*
  (fn [frame-id]
    (when-let [cancel-frame (and frame-id
                                 (some-> js/globalThis (aget "cancelAnimationFrame")))]
      (cancel-frame frame-id))))

(def ^:private no-orders [])

(declare render-overlays!)

(defn- cancel-scheduled-repaint!
  [chart-obj]
  (let [{:keys [repaint-frame-id] :as state} (support/overlay-state chart-obj)]
    (when repaint-frame-id
      (*cancel-overlay-repaint-frame!* repaint-frame-id)
      (support/set-overlay-state! chart-obj (dissoc state :repaint-frame-id)))))

(defn- schedule-overlay-repaint!
  [chart-obj]
  (let [{:keys [repaint-frame-id]} (support/overlay-state chart-obj)]
    (when-not repaint-frame-id
      (let [frame-id* (volatile! nil)
            callback (fn []
                       (let [frame-id @frame-id*
                             {:keys [repaint-frame-id] :as state} (support/overlay-state chart-obj)]
                         (when (or (nil? frame-id)
                                   (= frame-id repaint-frame-id))
                           (when frame-id
                             (support/set-overlay-state! chart-obj
                                                         (dissoc state :repaint-frame-id)))
                           (render-overlays! chart-obj))))
            frame-id (*schedule-overlay-repaint-frame!* callback)]
        (vreset! frame-id* frame-id)
        (when frame-id
          (support/set-overlay-state!
           chart-obj
           (assoc (support/overlay-state chart-obj) :repaint-frame-id frame-id)))))))

(defn- teardown-subscription!
  [{:keys [main-series time-scale repaint]}]
  (when repaint
    (support/invoke-method time-scale "unsubscribeVisibleTimeRangeChange" repaint)
    (support/invoke-method time-scale "unsubscribeVisibleLogicalRangeChange" repaint)
    (support/invoke-method time-scale "unsubscribeSizeChange" repaint)
    (support/invoke-method main-series "unsubscribeDataChanged" repaint)))

(defn- subscribe-overlay-repaint!
  [chart-obj chart main-series]
  (let [time-scale (support/invoke-method chart "timeScale")
        repaint (fn [_]
                  (schedule-overlay-repaint! chart-obj))]
    (support/invoke-method time-scale "subscribeVisibleTimeRangeChange" repaint)
    (support/invoke-method time-scale "subscribeVisibleLogicalRangeChange" repaint)
    (support/invoke-method time-scale "subscribeSizeChange" repaint)
    (support/invoke-method main-series "subscribeDataChanged" repaint)
    {:chart chart
     :main-series main-series
     :time-scale time-scale
     :repaint repaint}))

(defn- visible-order-row
  [main-series height order]
  (let [price (support/parse-order-number (:px order))
        y (when (and (support/finite-number? price)
                     (pos? price)
                     (:oid order)
                     (:coin order))
            (support/invoke-method main-series "priceToCoordinate" price))]
    (when (and (support/finite-number? y)
               (or (zero? height)
                   (and (> y -30)
                        (< y (+ height 30)))))
      {:order order
       :line-y y
       :intent (presentation/order-intent order)})))

(defn- visible-order-rows
  [main-series orders document height]
  (if (and main-series
           (sequential? orders)
           (seq orders)
           document)
    (->> orders
         (sort-by (fn [order]
                    (or (support/parse-order-number (:px order)) 0))
                  >)
         (keep #(visible-order-row main-series height %))
         vec)
    []))

(defn render-overlays!
  [chart-obj]
  (let [{:keys [root
                chart
                main-series
                orders
                on-cancel-order
                format-price
                format-size
                document]
         :as state}
        (support/overlay-state chart-obj)]
    (when root
      (let [pane-size (support/invoke-method chart "paneSize" 0)
            pane-width (some-> pane-size (aget "width"))
            width (support/non-negative-number
                   pane-width
                   (support/non-negative-number (.-clientWidth root) 0))
            height (or (.-clientHeight root) 0)
            visible-rows (visible-order-rows main-series orders document height)
            laid-out-rows (presentation/layout-overlapping-badges visible-rows width height)
            next-row-dom-by-key
            (rows/patch-visible-order-rows! root
                                            document
                                            (:row-dom-by-key state)
                                            laid-out-rows
                                            on-cancel-order
                                            format-price
                                            format-size)]
        (support/set-overlay-state!
         chart-obj
         (assoc state :row-dom-by-key next-row-dom-by-key))))))

(defn clear-open-order-overlays!
  [chart-obj]
  (when chart-obj
    (let [{:keys [root subscription]} (support/overlay-state chart-obj)]
      (cancel-scheduled-repaint! chart-obj)
      (teardown-subscription! subscription)
      (when root
        (support/clear-children! root)
        (when-let [parent (.-parentNode root)]
          (.removeChild parent root)))
      (support/delete-overlay-state! chart-obj))))

(defn sync-open-order-overlays!
  ([chart-obj container orders]
   (sync-open-order-overlays! chart-obj container orders {}))
  ([chart-obj container orders {:keys [document on-cancel-order format-price format-size]
                                :or {format-price account-shared/format-trade-price
                                     format-size account-shared/format-currency}}]
   (if-not (and chart-obj container)
     (clear-open-order-overlays! chart-obj)
     (let [chart (.-chart ^js chart-obj)
           main-series (.-mainSeries ^js chart-obj)
           document* (support/resolve-document document)]
       (if (and chart main-series document*)
         (let [state (support/overlay-state chart-obj)
               root (rows/ensure-overlay-root! state container document*)
               current-subscription (:subscription state)
               needs-resubscribe?
               (or (nil? current-subscription)
                   (not (identical? chart (:chart current-subscription)))
                   (not (identical? main-series (:main-series current-subscription))))
               next-subscription (if needs-resubscribe?
                                   (do
                                     (teardown-subscription! current-subscription)
                                     (subscribe-overlay-repaint! chart-obj chart main-series))
                                   current-subscription)
               orders-ref (when (sequential? orders) orders)
               orders* (cond
                         (vector? orders) orders
                         (sequential? orders) (vec orders)
                         :else no-orders)
               unchanged-inputs?
               (and (not needs-resubscribe?)
                    (identical? root (:root state))
                    (identical? chart (:chart state))
                    (identical? main-series (:main-series state))
                    (identical? document* (:document state))
                    (identical? format-price (:format-price state))
                    (identical? format-size (:format-size state))
                    (identical? on-cancel-order (:on-cancel-order state))
                    (identical? orders-ref (:orders-ref state)))]
           (if unchanged-inputs?
             state
             (do
               (cancel-scheduled-repaint! chart-obj)
               (let [state-after-cancel (support/overlay-state chart-obj)]
                 (support/set-overlay-state!
                  chart-obj
                  (assoc state-after-cancel
                         :root root
                         :chart chart
                         :main-series main-series
                         :orders-ref orders-ref
                         :orders orders*
                         :document document*
                         :format-price format-price
                         :format-size format-size
                         :on-cancel-order on-cancel-order
                         :subscription next-subscription)))
               (render-overlays! chart-obj))))
         (clear-open-order-overlays! chart-obj))))))
