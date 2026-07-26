(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays-lifecycle-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trading-chart.test-support.fake-dom :as fake-dom]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays :as position-overlays]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.presentation :as presentation]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.support :as support]))

(defn- find-pnl-price-chip
  [root]
  (fake-dom/find-dom-node root
                          (fn [node]
                            (= "true" (aget node "data-position-pnl-price-chip")))))

(defn- find-liquidation-price-chip
  [root]
  (fake-dom/find-dom-node root
                          (fn [node]
                            (= "true" (aget node "data-position-liq-price-chip")))))

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
  [{:keys [width price-to-y time-to-x y-to-price]
    :or {width 320
         price-to-y (fn [price] (- 220 price))
         y-to-price (fn [y] (- 220 y))
         time-to-x (fn [time]
                     (case time
                       1700000000 48
                       1700003600 228
                       nil))}}]
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
                        :unsubscribeSizeChange (unsubscribe-fn :size-change)
                        :timeToCoordinate time-to-x}
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
     :subscription-callbacks* subscription-callbacks*
     :window-target window-target
     :width* width*}))

(deftest position-overlays-coalesces-subscription-repaints-per-frame-test
  (let [{:keys [chart-obj document container subscription-callbacks*]}
        (build-chart-fixture {})
        overlay {:side :short
                 :entry-price 100
                 :unrealized-pnl -2.5
                 :abs-size 1.5
                 :liquidation-price 130
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}
        render-calls* (atom 0)
        next-frame-id* (atom 0)
        scheduled-frame* (atom nil)]
    (with-redefs [position-overlays/render-overlays! (fn [_]
                                                       (swap! render-calls* inc))
                  position-overlays/*schedule-overlay-repaint-frame!* (fn [callback]
                                                                        (let [frame-id (swap! next-frame-id* inc)
                                                                              wrapped (fn []
                                                                                        (reset! scheduled-frame* nil)
                                                                                        (callback))]
                                                                          (reset! scheduled-frame* {:id frame-id
                                                                                                    :callback wrapped})
                                                                          frame-id))
                  position-overlays/*cancel-overlay-repaint-frame!* (fn [frame-id]
                                                                      (when (= frame-id (:id @scheduled-frame*))
                                                                        (reset! scheduled-frame* nil)))]
      (position-overlays/sync-position-overlays!
       chart-obj
       container
       overlay
       {:document document})
      (is (= 1 @render-calls*))
      ((get @subscription-callbacks* :visible-time-range) nil)
      ((get @subscription-callbacks* :size-change) nil)
      ((get @subscription-callbacks* :data-changed) nil)
      (is (= 1 (:id @scheduled-frame*)))
      (is (= 1 @render-calls*))
      ((:callback @scheduled-frame*))
      (is (= 2 @render-calls*))
      (is (nil? @scheduled-frame*))
      (is (nil? (:repaint-frame-id (support/overlay-state chart-obj))))
      (position-overlays/clear-position-overlays! chart-obj))))

(deftest position-overlays-sync-cancels-pending-repaint-before-input-change-test
  (let [{:keys [chart-obj document container subscription-callbacks*]}
        (build-chart-fixture {})
        overlay {:side :short
                 :entry-price 100
                 :unrealized-pnl -2.5
                 :abs-size 1.5
                 :liquidation-price 130
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}
        next-frame-id* (atom 0)
        canceled-frames* (atom [])]
    (with-redefs [position-overlays/*schedule-overlay-repaint-frame!* (fn [_callback]
                                                                        (swap! next-frame-id* inc))
                  position-overlays/*cancel-overlay-repaint-frame!* (fn [frame-id]
                                                                      (swap! canceled-frames* conj frame-id))]
      (position-overlays/sync-position-overlays!
       chart-obj
       container
       overlay
       {:document document})
      ((get @subscription-callbacks* :size-change) nil)
      (is (= 1 (:repaint-frame-id (support/overlay-state chart-obj))))
      (position-overlays/sync-position-overlays!
       chart-obj
       container
       (assoc overlay :unrealized-pnl -3.5)
       {:document document})
      (is (= [1] @canceled-frames*))
      (is (nil? (:repaint-frame-id (support/overlay-state chart-obj)))))
    (position-overlays/clear-position-overlays! chart-obj)))

(deftest position-overlays-repaint-frame-fallbacks-run-without-browser-raf-test
  (let [callback-calls* (atom 0)
        canceled-frames* (atom [])
        original-raf (aget js/globalThis "requestAnimationFrame")
        original-cancel-raf (aget js/globalThis "cancelAnimationFrame")]
    (try
      (js-delete js/globalThis "requestAnimationFrame")
      (is (nil? (position-overlays/*schedule-overlay-repaint-frame!*
                 #(swap! callback-calls* inc))))
      (is (= 1 @callback-calls*))
      (aset js/globalThis
            "cancelAnimationFrame"
            (fn [frame-id]
              (swap! canceled-frames* conj frame-id)))
      (position-overlays/*cancel-overlay-repaint-frame!* 42)
      (position-overlays/*cancel-overlay-repaint-frame!* nil)
      (is (= [42] @canceled-frames*))
      (finally
        (if (some? original-raf)
          (aset js/globalThis "requestAnimationFrame" original-raf)
          (js-delete js/globalThis "requestAnimationFrame"))
        (if (some? original-cancel-raf)
          (aset js/globalThis "cancelAnimationFrame" original-cancel-raf)
          (js-delete js/globalThis "cancelAnimationFrame"))))))

(deftest position-overlays-formatters-handle-fallbacks-and-prefixes-test
  (let [one-arg-fallback (fn
                           ([_value _raw] nil)
                           ([value] (str "one-" value)))
        throwing-formatter (fn [& _]
                             (throw (js/Error. "formatter failed")))]
    (is (= "$one-42" (presentation/format-price-text one-arg-fallback 42)))
    (is (= "$0.00" (presentation/format-price-text (fn [& _] "   ") 42)))
    (is (= "$42" (presentation/format-price-text (fn [value _raw] (str value)) 42)))
    (is (= "$0.00" (presentation/format-price-text throwing-formatter "bad")))
    (is (= "<$0.01" (presentation/format-price-text (fn [& _] "<$0.01") 0.01)))
    (is (= "<0.01" (presentation/format-axis-price-text (fn [& _] "<$0.01") 0.01)))
    (is (= "99" (presentation/format-axis-price-text (fn [& _] "$99") 99)))
    (is (= "0.00" (presentation/format-axis-price-text (fn [& _] "   ") 99)))
    (is (= "7.00" (presentation/format-size-text nil 7)))
    (is (= "$0.00" (presentation/format-pnl-text nil)))))

(deftest position-overlays-presentation-geometry-pins-badge-and-visibility-boundaries-test
  (is (= 182 (presentation/clamp-badge-center-x 320 999 100)))
  (is (= 62 (presentation/clamp-badge-center-x 320 0 100)))
  (is (= 100 (presentation/clamp-badge-center-x 200 62 100)))
  (is (= 0 (presentation/clamp-badge-center-x nil 10 20)))
  (is (false? (presentation/visible-overlay-y? 200 -30)))
  (is (true? (presentation/visible-overlay-y? 200 -29.999)))
  (is (true? (presentation/visible-overlay-y? 200 220)))
  (is (false? (presentation/visible-overlay-y? 200 230))))

(deftest position-overlays-support-helpers-move-children-and-invoke-methods-defensively-test
  (let [document (fake-dom/make-fake-document)
        parent-a (fake-dom/make-fake-element "div")
        parent-b (fake-dom/make-fake-element "div")
        child (fake-dom/make-fake-element "span")
        sibling (fake-dom/make-fake-element "span")
        chart-obj #js {}
        text-node #js {:nodeValue "old"}
        calls* (atom [])
        target #js {}]
    (support/set-overlay-state! chart-obj {:root :mounted})
    (is (= {:root :mounted} (support/overlay-state chart-obj)))
    (support/delete-overlay-state! chart-obj)
    (is (= {} (support/overlay-state chart-obj)))
    (aset target
          "record"
          (fn [& args]
            (swap! calls* conj (vec args))
            :recorded))
    (is (= :recorded (support/invoke-method target "record" 1 2)))
    (is (= [[1 2]] @calls*))
    (is (nil? (support/invoke-method target "missing" 1)))
    (support/set-text-node-value! text-node "new")
    (is (= "new" (.-data text-node)))
    (is (= "new" (.-nodeValue text-node)))
    (support/append-child! parent-a child)
    (is (identical? parent-a (.-parentNode child)))
    (support/append-child! parent-b child)
    (is (= 0 (alength (.-children parent-a))))
    (is (= 1 (alength (.-children parent-b))))
    (is (identical? parent-b (.-parentNode child)))
    (support/append-child! parent-b sibling)
    (support/append-child! parent-b child)
    (is (identical? child (aget (.-children parent-b) 0)))
    (is (identical? sibling (aget (.-children parent-b) 1)))
    (support/append-child! parent-b (support/create-text-node! document "x"))
    (is (= 3 (alength (.-childNodes parent-b))))
    (support/clear-children! parent-b)
    (is (= 0 (alength (.-childNodes parent-b))))))

(deftest position-overlays-render-hides-retained-rows-when-state-becomes-invalid-test
  (let [{:keys [chart-obj document container]}
        (build-chart-fixture {})
        overlay {:side :short
                 :entry-price 100
                 :unrealized-pnl -2.5
                 :abs-size 1.5
                 :liquidation-price 130
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document})
    (let [overlay-root (aget (.-children container) 0)
          pnl-chip (find-pnl-price-chip overlay-root)
          liq-chip (find-liquidation-price-chip overlay-root)
          pnl-row (.-parentNode pnl-chip)
          liq-row (.-parentNode liq-chip)]
      (is (= "block" (aget (.-style pnl-row) "display")))
      (is (= "block" (aget (.-style liq-row) "display")))
      (support/set-overlay-state!
       chart-obj
       (assoc (support/overlay-state chart-obj) :overlay nil))
      (position-overlays/render-overlays! chart-obj)
      (is (= "none" (aget (.-style pnl-row) "display")))
      (is (= "none" (aget (.-style liq-row) "display")))
      (is (empty? (fake-dom/collect-text-content pnl-row)))
      (is (empty? (fake-dom/collect-text-content liq-row))))
    (position-overlays/clear-position-overlays! chart-obj)))

(deftest position-overlays-anchors-price-chips-at-zero-when-pane-width-is-invalid-test
  (let [{:keys [chart-obj document container width*]}
        (build-chart-fixture {})
        overlay {:side :long
                 :entry-price 100
                 :unrealized-pnl 2
                 :abs-size 1
                 :liquidation-price 85
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document})
    (let [overlay-root (aget (.-children container) 0)
          pnl-chip (find-pnl-price-chip overlay-root)
          liq-chip (find-liquidation-price-chip overlay-root)]
      (reset! width* -1)
      (set! (.-clientWidth overlay-root) -1)
      (position-overlays/render-overlays! chart-obj)
      (is (= "0px" (aget (.-style pnl-chip) "left")))
      (is (= "0px" (aget (.-style liq-chip) "left"))))
    (position-overlays/clear-position-overlays! chart-obj)))

(deftest position-overlays-treats-missing-root-height-as-unbounded-test
  (let [{:keys [chart-obj document container]}
        (build-chart-fixture {:price-to-y (fn [_price] 31.5)})
        overlay {:side :long
                 :entry-price 100
                 :unrealized-pnl 2
                 :abs-size 1
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document})
    (let [overlay-root (aget (.-children container) 0)
          pnl-chip (find-pnl-price-chip overlay-root)
          pnl-row (.-parentNode pnl-chip)]
      (set! (.-clientHeight overlay-root) nil)
      (position-overlays/render-overlays! chart-obj)
      (is (= "block" (aget (.-style pnl-row) "display"))))
    (position-overlays/clear-position-overlays! chart-obj)))

(deftest position-overlays-remounts-overlay-root-when-container-changes-test
  (let [{:keys [chart-obj document container]}
        (build-chart-fixture {})
        next-container (fake-dom/make-fake-element "div")
        overlay {:side :long
                 :entry-price 100
                 :unrealized-pnl 2
                 :abs-size 1
                 :liquidation-price 85
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (set! (.-clientWidth next-container) 320)
    (set! (.-clientHeight next-container) 240)
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document})
    (is (= 1 (alength (.-children container))))
    (let [first-root (aget (.-children container) 0)]
      (position-overlays/sync-position-overlays!
       chart-obj
       next-container
       (assoc overlay :unrealized-pnl 3)
       {:document document})
      (is (= 0 (alength (.-children container))))
      (is (= 1 (alength (.-children next-container))))
      (is (not (identical? first-root (aget (.-children next-container) 0)))))
    (position-overlays/clear-position-overlays! chart-obj)))

(deftest position-overlays-drag-falls-back-to-document-target-and-client-y-test
  (let [preview-calls* (atom [])
        {:keys [chart-obj document container]}
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
      :on-liquidation-drag-preview (fn [payload]
                                     (swap! preview-calls* conj payload))})
    (let [overlay-root (aget (.-children container) 0)
          drag-hit (find-liquidation-drag-hit overlay-root)]
      (aset overlay-root
            "getBoundingClientRect"
            (fn []
              (throw (js/Error. "layout unavailable"))))
      (support/set-overlay-state!
       chart-obj
       (dissoc (support/overlay-state chart-obj) :window))
      (fake-dom/dispatch-dom-event-with-payload! drag-hit
                                                 "pointerdown"
                                                 #js {:clientX 64
                                                      :clientY 120})
      (is (listener-registered? document "pointermove"))
      (fake-dom/dispatch-dom-event-with-payload! document
                                                 "pointermove"
                                                 #js {:clientX 64
                                                      :clientY 125})
      (let [payload (first @preview-calls*)]
        (is (= :add (:mode payload)))
        (is (= 10 (:amount payload)))
        (is (= 95 (:target-liquidation-price payload))))
      (fake-dom/dispatch-dom-event-with-payload! document
                                                 "pointercancel"
                                                 #js {:clientX 64
                                                      :clientY 125})
      (is (not (listener-registered? document "pointermove"))))
    (position-overlays/clear-position-overlays! chart-obj)))
