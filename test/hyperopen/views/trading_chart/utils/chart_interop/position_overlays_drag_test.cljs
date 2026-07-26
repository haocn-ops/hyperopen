(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays-drag-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.views.trading-chart.test-support.fake-dom :as fake-dom]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays :as position-overlays]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.liquidation-drag :as liquidation-drag]))

(defn- find-liquidation-drag-hit
  [root]
  (fake-dom/find-dom-node root
                          (fn [node]
                            (= "true" (aget node "data-position-liq-drag-hit")))))

(defn- listener-registered?
  [target event-name]
  (let [listeners (.-listeners ^js target)]
    (fn? (when listeners
           (aget listeners event-name)))))

(defn- build-chart-fixture
  [{:keys [width price-to-y y-to-price]
    :or {width 320
         price-to-y (fn [price] (- 220 price))
         y-to-price (fn [y] (- 220 y))}}]
  (let [subscription-callbacks* (atom {})
        subscribe-fn (fn [k]
                       (fn [callback]
                         (swap! subscription-callbacks* assoc k callback)))
        unsubscribe-fn (fn [k]
                         (fn [callback]
                           (swap! subscription-callbacks*
                                  (fn [callbacks]
                                    (if (identical? callback (get callbacks k))
                                      (dissoc callbacks k)
                                      callbacks)))))
        width* (atom width)
        time-scale #js {:subscribeVisibleTimeRangeChange (subscribe-fn :visible-time-range)
                        :unsubscribeVisibleTimeRangeChange (unsubscribe-fn :visible-time-range)
                        :subscribeVisibleLogicalRangeChange (subscribe-fn :visible-logical-range)
                        :unsubscribeVisibleLogicalRangeChange (unsubscribe-fn :visible-logical-range)
                        :subscribeSizeChange (subscribe-fn :size-change)
                        :unsubscribeSizeChange (unsubscribe-fn :size-change)}
        main-series #js {:priceToCoordinate price-to-y
                         :coordinateToPrice y-to-price
                         :subscribeDataChanged (subscribe-fn :data-changed)
                         :unsubscribeDataChanged (unsubscribe-fn :data-changed)}
        chart #js {:timeScale (fn [] time-scale)
                   :paneSize (fn [_pane-index]
                               #js {:width @width*})}
        chart-obj #js {:chart chart
                       :mainSeries main-series}
        document (fake-dom/make-fake-document)
        container (fake-dom/make-fake-element "div")
        window-target (fake-dom/make-fake-element "window")]
    (set! (.-clientWidth container) width)
    (set! (.-clientHeight container) 240)
    {:chart-obj chart-obj
     :document document
     :container container
     :window-target window-target}))

(deftest position-overlays-liquidation-margin-direction-matrix-test
  (let [long-overlay {:side :long
                      :abs-size 2}
        short-overlay {:side :short
                       :abs-size 2}]
    (is (= {:mode :add
            :amount 10
            :current-liquidation-price 100
            :target-liquidation-price 95}
           (liquidation-drag/liquidation-drag-suggestion long-overlay 100 95)))
    (is (= {:mode :remove
            :amount 10
            :current-liquidation-price 100
            :target-liquidation-price 105}
           (liquidation-drag/liquidation-drag-suggestion long-overlay 100 105)))
    (is (= {:mode :add
            :amount 10
            :current-liquidation-price 100
            :target-liquidation-price 105}
           (liquidation-drag/liquidation-drag-suggestion short-overlay 100 105)))
    (is (= {:mode :remove
            :amount 10
            :current-liquidation-price 100
            :target-liquidation-price 95}
           (liquidation-drag/liquidation-drag-suggestion short-overlay 100 95)))
    (doseq [[label overlay current target]
            [["unsupported side" {:side :flat :abs-size 2} 100 95]
             ["zero size" {:side :long :abs-size 0} 100 95]
             ["missing size" {:side :long} 100 95]
             ["zero current" {:side :long :abs-size 2} 0 95]
             ["negative current" {:side :long :abs-size 2} -1 95]
             ["zero target" {:side :long :abs-size 2} 100 0]
             ["negative target" {:side :long :abs-size 2} 100 -1]
             ["nonnumeric current" {:side :long :abs-size 2} "bad" 95]
             ["nonnumeric target" {:side :long :abs-size 2} 100 "bad"]]]
      (is (nil? (liquidation-drag/liquidation-drag-suggestion overlay current target))
          label))))

(deftest position-overlays-liquidation-drag-anchor-uses-dom-rect-and-viewport-test
  (let [window-target #js {:innerWidth 900
                           :innerHeight 700}
        source-node (fake-dom/make-fake-element "div")]
    (aset source-node
          "getBoundingClientRect"
          (fn []
            #js {:left 5
                 :top 7
                 :width 11
                 :height 13}))
    (is (= {:left 5
            :right 16
            :top 7
            :bottom 20
            :width 11
            :height 13
            :viewport-width 900
            :viewport-height 700}
           (liquidation-drag/event-anchor {:window window-target}
                                          source-node
                                          #js {:clientX 50
                                               :clientY 60})))))

(deftest position-overlays-liquidation-drag-anchor-falls-back-to-event-coordinates-test
  (let [source-node (fake-dom/make-fake-element "div")]
    (aset source-node
          "getBoundingClientRect"
          (fn []
            (throw (js/Error. "layout unavailable"))))
    (is (= {:left 50
            :right 50
            :top 60
            :bottom 60
            :width 0
            :height 0
            :viewport-width 1280
            :viewport-height 800}
           (liquidation-drag/event-anchor {}
                                          source-node
                                          #js {:clientX 50
                                               :clientY 60})))))

(deftest position-overlays-liquidation-drag-anchor-falls-back-to-origin-without-coordinates-test
  (let [source-node (fake-dom/make-fake-element "div")]
    (aset source-node
          "getBoundingClientRect"
          (fn []
            (throw (js/Error. "layout unavailable"))))
    (is (= {:left 0
            :right 0
            :top 0
            :bottom 0
            :width 0
            :height 0
            :viewport-width 1280
            :viewport-height 800}
           (liquidation-drag/event-anchor {}
                                          source-node
                                          #js {})))))

(deftest position-overlays-liquidation-margin-suggestion-includes-minimum-threshold-test
  (is (= {:mode :add
          :amount 0.000001
          :current-liquidation-price 2
          :target-liquidation-price 1}
         (liquidation-drag/liquidation-drag-suggestion {:side :long
                                                        :abs-size 0.000001}
                                                       2
                                                       1))))

(deftest position-overlays-liquidation-drag-cancel-removes-listeners-and-suppresses-confirm-test
  (let [preview-calls* (atom [])
        confirm-calls* (atom [])
        pointerdown-defaults* (atom 0)
        pointerdown-propagation* (atom 0)
        move-defaults* (atom 0)
        cancel-defaults* (atom 0)
        {:keys [chart-obj document container window-target]}
        (build-chart-fixture {})
        overlay {:side :long
                 :entry-price 100
                 :unrealized-pnl 2.0
                 :abs-size 2
                 :liquidation-price 100
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document
      :window window-target
      :on-liquidation-drag-preview (fn [payload]
                                     (swap! preview-calls* conj payload))
      :on-liquidation-drag-confirm (fn [payload]
                                     (swap! confirm-calls* conj payload))})
    (let [overlay-root (aget (.-children container) 0)
          drag-hit (find-liquidation-drag-hit overlay-root)]
      (fake-dom/dispatch-dom-event-with-payload! drag-hit
                                                 "pointerdown"
                                                 #js {:clientX 64
                                                      :clientY 120
                                                      :preventDefault (fn []
                                                                        (swap! pointerdown-defaults* inc))
                                                      :stopPropagation (fn []
                                                                         (swap! pointerdown-propagation* inc))})
      (is (= 1 @pointerdown-defaults*))
      (is (= 1 @pointerdown-propagation*))
      (is (listener-registered? window-target "pointermove"))
      (is (listener-registered? window-target "pointercancel"))
      (fake-dom/dispatch-dom-event-with-payload! window-target
                                                 "pointermove"
                                                 #js {:clientX 64
                                                      :clientY 125
                                                      :preventDefault (fn []
                                                                        (swap! move-defaults* inc))})
      (is (= 1 (count @preview-calls*)))
      (is (= 1 @move-defaults*))
      (fake-dom/dispatch-dom-event-with-payload! window-target
                                                 "pointercancel"
                                                 #js {:clientX 64
                                                      :clientY 125
                                                      :preventDefault (fn []
                                                                        (swap! cancel-defaults* inc))})
      (is (= 1 @cancel-defaults*))
      (doseq [event-name ["pointermove"
                          "pointerup"
                          "pointercancel"
                          "mousemove"
                          "mouseup"
                          "touchmove"
                          "touchend"
                          "touchcancel"]]
        (is (not (listener-registered? window-target event-name))
            event-name))
      (is (empty? @confirm-calls*))
      (let [text (str/join " " (fake-dom/collect-text-content overlay-root))]
        (is (str/includes? text "$100"))
        (is (not (str/includes? text "Add $10.00 Margin"))))
      (fake-dom/dispatch-dom-event-with-payload! window-target
                                                 "pointerup"
                                                 #js {:clientX 64
                                                      :clientY 125})
      (is (empty? @confirm-calls*)))
    (position-overlays/clear-position-overlays! chart-obj)))

(deftest position-overlays-liquidation-drag-finalize-with-missing-up-coordinate-uses-last-preview-test
  (let [confirm-calls* (atom [])
        up-defaults* (atom 0)
        up-propagation* (atom 0)
        {:keys [chart-obj document container window-target]}
        (build-chart-fixture {})
        overlay {:side :long
                 :entry-price 100
                 :unrealized-pnl 2.0
                 :abs-size 2
                 :liquidation-price 100
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document
      :window window-target
      :on-liquidation-drag-confirm (fn [payload]
                                     (swap! confirm-calls* conj payload))})
    (let [overlay-root (aget (.-children container) 0)
          drag-hit (find-liquidation-drag-hit overlay-root)]
      (fake-dom/dispatch-dom-event-with-payload! drag-hit
                                                 "pointerdown"
                                                 #js {:clientX 64
                                                      :clientY 120})
      (fake-dom/dispatch-dom-event-with-payload! window-target
                                                 "pointermove"
                                                 #js {:clientX 64
                                                      :clientY 125})
      (fake-dom/dispatch-dom-event-with-payload! window-target
                                                 "pointerup"
                                                 #js {:clientX 64
                                                      :preventDefault (fn []
                                                                        (swap! up-defaults* inc))
                                                      :stopPropagation (fn []
                                                                         (swap! up-propagation* inc))}))
    (is (= 1 @up-defaults*))
    (is (= 1 @up-propagation*))
    (let [payload (first @confirm-calls*)]
      (is (= :add (:mode payload)))
      (is (= 10 (:amount payload)))
      (is (= 100 (:current-liquidation-price payload)))
      (is (= 95 (:target-liquidation-price payload))))
    (position-overlays/clear-position-overlays! chart-obj)))

(deftest position-overlays-liquidation-drag-preview-subtracts-overlay-root-top-test
  (let [preview-calls* (atom [])
        {:keys [chart-obj document container window-target]}
        (build-chart-fixture {})
        overlay {:side :long
                 :entry-price 100
                 :unrealized-pnl 2.0
                 :abs-size 2
                 :liquidation-price 100
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document
      :window window-target
      :on-liquidation-drag-preview (fn [payload]
                                     (swap! preview-calls* conj payload))})
    (let [overlay-root (aget (.-children container) 0)
          drag-hit (find-liquidation-drag-hit overlay-root)]
      (aset overlay-root
            "getBoundingClientRect"
            (fn []
              #js {:top 100
                   :left 0
                   :width 320
                   :height 240}))
      (fake-dom/dispatch-dom-event-with-payload! drag-hit
                                                 "pointerdown"
                                                 #js {:clientX 64
                                                      :clientY 120})
      (fake-dom/dispatch-dom-event-with-payload! window-target
                                                 "pointermove"
                                                 #js {:clientX 64
                                                      :clientY 125}))
    (let [payload (first @preview-calls*)]
      (is (= :remove (:mode payload)))
      (is (= 190 (:amount payload)))
      (is (= 195 (:target-liquidation-price payload))))
    (position-overlays/clear-position-overlays! chart-obj)))
