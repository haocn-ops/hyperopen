(ns hyperopen.views.account-info.tabs.positions.test-support
  (:require [hyperopen.views.account-info.tabs.positions :as positions-tab]))

(defn reset-positions-sort-cache-fixture
  [f]
  (positions-tab/reset-positions-sort-cache!)
  (f)
  (positions-tab/reset-positions-sort-cache!))

(defn with-viewport
  [width height f]
  (let [original-inner-width (.-innerWidth js/globalThis)
        original-inner-height (.-innerHeight js/globalThis)]
    (set! (.-innerWidth js/globalThis) width)
    (set! (.-innerHeight js/globalThis) height)
    (try
      (f)
      (finally
        (set! (.-innerWidth js/globalThis) original-inner-width)
        (set! (.-innerHeight js/globalThis) original-inner-height)))))

(defn with-phone-viewport
  [f]
  (with-viewport 430 932 f))

(defn render-positions-tab-from-rows
  ([rows sort-state]
   (render-positions-tab-from-rows rows sort-state nil nil nil {}))
  ([rows sort-state tpsl-modal positions-state]
   (render-positions-tab-from-rows rows sort-state tpsl-modal nil nil positions-state))
  ([rows sort-state tpsl-modal reduce-popover margin-modal positions-state]
   (positions-tab/positions-tab-content {:positions rows
                                         :sort-state sort-state
                                         :tpsl-modal tpsl-modal
                                         :reduce-popover reduce-popover
                                         :margin-modal margin-modal
                                         :positions-state positions-state})))

(defn render-positions-tab-from-webdata
  [webdata2 sort-state perp-dex-states]
  (positions-tab/positions-tab-content {:webdata2 webdata2
                                        :sort-state sort-state
                                        :perp-dex-states perp-dex-states}))
