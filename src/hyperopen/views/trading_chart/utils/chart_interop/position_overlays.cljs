(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays
  (:require [hyperopen.views.account-info.shared :as account-shared]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.liquidation-drag :as liquidation-drag]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.presentation :as presentation]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.rows :as rows]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.support :as support]))

(declare begin-liquidation-drag!)

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

(defn- begin-current-liquidation-drag!
  [chart-obj source-node event]
  (when event
    (.preventDefault event)
    (.stopPropagation event))
  (let [{:keys [drag rendered-overlay overlay]} (support/overlay-state chart-obj)
        overlay-for-drag (or rendered-overlay overlay)]
    (when (and (not drag)
               (map? overlay-for-drag))
      (begin-liquidation-drag! chart-obj
                               overlay-for-drag
                               source-node
                               event))))

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
                             (support/set-overlay-state! chart-obj (dissoc state :repaint-frame-id)))
                           (render-overlays! chart-obj))))
            frame-id (*schedule-overlay-repaint-frame!* callback)]
        (vreset! frame-id* frame-id)
        (when frame-id
          (support/set-overlay-state! chart-obj
                              (assoc (support/overlay-state chart-obj) :repaint-frame-id frame-id)))))))

(defn- teardown-subscription!
  [{:keys [time-scale main-series repaint]}]
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

(defn- remove-event-listener!
  [target event-name handler]
  (when (and target
             (fn? (aget target "removeEventListener"))
             (fn? handler))
    (.removeEventListener target event-name handler)))

(defn- add-event-listener!
  [target event-name handler]
  (when (and target
             (fn? (aget target "addEventListener"))
             (fn? handler))
    (.addEventListener target event-name handler)))

(defn- teardown-drag-listeners!
  [state]
  (let [{:keys [target on-move on-up on-cancel]} (:drag-listeners state)]
    (remove-event-listener! target "pointermove" on-move)
    (remove-event-listener! target "pointerup" on-up)
    (remove-event-listener! target "pointercancel" on-cancel)
    (remove-event-listener! target "mousemove" on-move)
    (remove-event-listener! target "mouseup" on-up)
    (remove-event-listener! target "touchmove" on-move)
    (remove-event-listener! target "touchend" on-up)
    (remove-event-listener! target "touchcancel" on-cancel))
  (dissoc state :drag-listeners))

(defn- event-root-y
  [root event]
  (let [client-y (liquidation-drag/event-client-y event)
        rect (try
               (when-let [method (some-> root (aget "getBoundingClientRect"))]
                 (when (fn? method)
                   (.call method root)))
               (catch :default _ nil))
        top (some-> rect (aget "top") support/parse-number)]
    (when (support/finite-number? client-y)
      (if (support/finite-number? top)
        (- client-y top)
        client-y))))

(defn- y->liq-price
  [main-series y]
  (when (support/finite-number? y)
    (let [price (support/parse-number (support/invoke-method main-series "coordinateToPrice" y))]
      (when (and (support/finite-number? price)
                 (pos? price))
        price))))

(defn- liq-price-from-event
  [state event]
  (let [root (:root state)
        main-series (:main-series state)
        y (event-root-y root event)]
    (y->liq-price main-series y)))

(defn- update-liquidation-drag-preview!
  [chart-obj event]
  (let [state (support/overlay-state chart-obj)]
    (when (and (map? (:drag state))
               (:root state)
               (:main-series state))
      (when-let [preview-price (liq-price-from-event state event)]
        (let [state* (assoc-in state [:drag :preview-liquidation-price] preview-price)
              drag (:drag state*)
              start-price (support/parse-number (:start-liquidation-price drag))
              overlay-for-preview (:overlay-for-confirm drag)
              source-node (:source-node drag)
              on-liquidation-drag-preview (:on-liquidation-drag-preview state*)]
          (support/set-overlay-state! chart-obj state*)
          (render-overlays! chart-obj)
          (when (and (support/finite-number? start-price)
                     (support/finite-number? preview-price)
                     (fn? on-liquidation-drag-preview)
                     (map? overlay-for-preview))
            (when-let [suggestion (liquidation-drag/liquidation-drag-suggestion overlay-for-preview
                                                                                start-price
                                                                                preview-price)]
              (on-liquidation-drag-preview
               (assoc suggestion
                      :anchor (liquidation-drag/event-anchor (:overlay state*)
                                                             source-node
                                                             event))))))))))

(defn- finalize-liquidation-drag!
  [chart-obj event canceled?]
  (let [state (support/overlay-state chart-obj)
        drag (:drag state)
        start-price (support/parse-number (:start-liquidation-price drag))
        preview-price (or (liq-price-from-event state event)
                          (support/parse-number (:preview-liquidation-price drag))
                          start-price)
        on-liquidation-drag-confirm (:on-liquidation-drag-confirm state)
        overlay-for-confirm (:overlay-for-confirm drag)
        source-node (:source-node drag)
        next-state (-> state
                       teardown-drag-listeners!
                       (dissoc :drag))]
    (support/set-overlay-state! chart-obj next-state)
    (render-overlays! chart-obj)
    (when (and (not canceled?)
               (support/finite-number? start-price)
               (support/finite-number? preview-price)
               (fn? on-liquidation-drag-confirm)
               (map? overlay-for-confirm))
      (when-let [suggestion (liquidation-drag/liquidation-drag-suggestion overlay-for-confirm
                                                                          start-price
                                                                          preview-price)]
        (on-liquidation-drag-confirm
         (assoc suggestion
                :anchor (liquidation-drag/event-anchor (:overlay next-state)
                                                       source-node
                                                       event)))))))

(defn begin-liquidation-drag!
  [chart-obj overlay-for-confirm source-node event]
  (let [state (support/overlay-state chart-obj)
        start-price (support/parse-number (:liquidation-price overlay-for-confirm))
        target (or (:window state)
                   (some-> (:document state) .-defaultView)
                   (:document state)
                   js/globalThis)]
    (when (and (support/finite-number? start-price)
               (pos? start-price)
               (map? overlay-for-confirm))
      (let [on-move (fn [drag-event]
                      (when drag-event
                        (.preventDefault drag-event))
                      (update-liquidation-drag-preview! chart-obj drag-event))
            on-up (fn [drag-event]
                    (when drag-event
                      (.preventDefault drag-event)
                      (.stopPropagation drag-event))
                    (finalize-liquidation-drag! chart-obj drag-event false))
            on-cancel (fn [drag-event]
                        (when drag-event
                          (.preventDefault drag-event))
                        (finalize-liquidation-drag! chart-obj drag-event true))
            state* (-> state
                       teardown-drag-listeners!
                       (assoc :drag {:source-node source-node
                                     :overlay-for-confirm overlay-for-confirm
                                     :start-liquidation-price start-price
                                     :preview-liquidation-price start-price}
                              :drag-listeners {:target target
                                               :on-move on-move
                                               :on-up on-up
                                               :on-cancel on-cancel}))]
        (add-event-listener! target "pointermove" on-move)
        (add-event-listener! target "pointerup" on-up)
        (add-event-listener! target "pointercancel" on-cancel)
        (add-event-listener! target "mousemove" on-move)
        (add-event-listener! target "mouseup" on-up)
        (add-event-listener! target "touchmove" on-move)
        (add-event-listener! target "touchend" on-up)
        (add-event-listener! target "touchcancel" on-cancel)
        (support/set-overlay-state! chart-obj state*)
        (render-overlays! chart-obj)))))

(defn render-overlays!
  [chart-obj]
  (let [state (support/overlay-state chart-obj)
        {:keys [root chart main-series overlay drag]} state
        document (:document overlay)
        format-price (:format-price overlay)
        format-size (:format-size overlay)]
    (when root
      (if (and (map? overlay)
               main-series
               document)
        (let [[state* overlay-dom] (rows/ensure-overlay-dom!
                                    state
                                    document
                                    root
                                    (fn [source-node event]
                                      (begin-current-liquidation-drag! chart-obj source-node event)))
              entry-price (support/parse-number (:entry-price overlay))
              entry-y (when (and (support/finite-number? entry-price)
                                 (pos? entry-price))
                        (support/invoke-method main-series "priceToCoordinate" entry-price))
              base-liq-price (support/parse-number (:liquidation-price overlay))
              drag-preview-price (some-> drag :preview-liquidation-price support/parse-number)
              current-liq-price (or (some-> drag :start-liquidation-price support/parse-number)
                                    (some-> overlay :current-liquidation-price support/parse-number)
                                    base-liq-price)
              liq-price (if (support/finite-number? drag-preview-price)
                          drag-preview-price
                          base-liq-price)
              liq-y (when (and (support/finite-number? liq-price)
                               (pos? liq-price))
                      (support/invoke-method main-series "priceToCoordinate" liq-price))
              pane-size (support/invoke-method chart "paneSize" 0)
              pane-width (some-> pane-size (aget "width"))
              width (support/non-negative-number pane-width
                                                 (support/non-negative-number (.-clientWidth root) 0))
              height (or (.-clientHeight root) 0)
              overlay* (assoc overlay
                              :document document
                              :format-price format-price
                              :format-size format-size
                              :current-liquidation-price current-liq-price)]
          (support/set-overlay-state! chart-obj (assoc state* :rendered-overlay overlay*))
          (if (presentation/visible-overlay-y? height entry-y)
            (rows/patch-pnl-row! (:pnl overlay-dom) overlay* entry-y width)
            (rows/hide-pnl-row! (:pnl overlay-dom)))
          (if (presentation/visible-overlay-y? height liq-y)
            (rows/patch-liquidation-row! (:liquidation overlay-dom) overlay* liq-y width)
            (rows/hide-liquidation-row! (:liquidation overlay-dom))))
        (when-let [overlay-dom (:overlay-dom state)]
          (rows/hide-pnl-row! (:pnl overlay-dom))
          (rows/hide-liquidation-row! (:liquidation overlay-dom))
          (support/set-overlay-state! chart-obj (dissoc state :rendered-overlay)))))))

(defn clear-position-overlays!
  [chart-obj]
  (when chart-obj
    (let [{:keys [root subscription] :as state} (support/overlay-state chart-obj)]
      (cancel-scheduled-repaint! chart-obj)
      (teardown-subscription! subscription)
      (teardown-drag-listeners! state)
      (when root
        (support/clear-children! root)
        (when-let [parent (.-parentNode root)]
          (.removeChild parent root)))
      (support/delete-overlay-state! chart-obj))))

(defn sync-position-overlays!
  ([chart-obj container overlay]
   (sync-position-overlays! chart-obj container overlay {}))
  ([chart-obj container overlay {:keys [document window format-price format-size on-liquidation-drag-preview on-liquidation-drag-confirm]
                                 :or {format-price account-shared/format-trade-price
                                      format-size account-shared/format-currency}}]
   (if-not (and chart-obj container)
     (clear-position-overlays! chart-obj)
     (let [chart (.-chart ^js chart-obj)
           main-series (.-mainSeries ^js chart-obj)
           document* (support/resolve-document document)
           window* (or window
                       (some-> document* .-defaultView)
                       (some-> js/globalThis .-window)
                       js/globalThis)
           overlay-ref (when (map? overlay) overlay)]
       (if (and chart main-series document* overlay-ref)
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
               unchanged-inputs?
               (and (not needs-resubscribe?)
                    (identical? root (:root state))
                    (identical? chart (:chart state))
                    (identical? main-series (:main-series state))
                    (identical? document* (:document state))
                    (identical? window* (:window state))
                    (identical? format-price (:format-price state))
                    (identical? format-size (:format-size state))
                    (identical? on-liquidation-drag-preview (:on-liquidation-drag-preview state))
                    (identical? on-liquidation-drag-confirm (:on-liquidation-drag-confirm state))
                    (identical? overlay-ref (:overlay-ref state)))]
           (if unchanged-inputs?
             state
             (do
               (cancel-scheduled-repaint! chart-obj)
               (let [state-after-cancel (support/overlay-state chart-obj)]
                 (support/set-overlay-state!
                  chart-obj
                  (cond-> (assoc state-after-cancel
                                 :root root
                                 :chart chart
                                 :main-series main-series
                                 :overlay-ref overlay-ref
                                 :overlay (assoc overlay-ref
                                                 :document document*
                                                 :window window*
                                                 :format-price format-price
                                                 :format-size format-size)
                                 :document document*
                                 :window window*
                                 :format-price format-price
                                 :format-size format-size
                                 :on-liquidation-drag-preview on-liquidation-drag-preview
                                 :on-liquidation-drag-confirm on-liquidation-drag-confirm
                                 :subscription next-subscription)
                    (not (identical? document* (get-in state [:overlay-dom :document])))
                    (dissoc :overlay-dom :rendered-overlay))))
               (render-overlays! chart-obj))))
         (clear-position-overlays! chart-obj))))))
