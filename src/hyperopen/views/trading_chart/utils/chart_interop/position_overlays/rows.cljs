(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays.rows
  (:require [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.liquidation-drag :as liquidation-drag]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.presentation :as presentation]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays.support :as support]))

(defn create-overlay-root!
  [document]
  (let [root (.createElement document "div")]
    (set! (.-className root) "chart-position-overlays")
    (support/apply-inline-style!
     root
     {"position" "absolute"
      "inset" "0px"
      "pointerEvents" "none"
      "zIndex" "13"
      "overflow" "hidden"})
    root))

(defn ensure-overlay-root!
  [state container document]
  (let [{:keys [root]} state
        mounted-root? (and root (identical? (.-parentNode root) container))
        next-root (if mounted-root?
                    root
                    (create-overlay-root! document))]
    (when (and root (not mounted-root?))
      (when-let [parent (.-parentNode root)]
        (.removeChild parent root)))
    (support/append-child! container next-root)
    next-root))

(defn create-pnl-row-nodes!
  [document]
  (let [row (.createElement document "div")
        line (.createElement document "div")
        badge (.createElement document "div")
        badge-text (.createElement document "span")
        badge-text-node (support/create-text-node! document "")
        chip (.createElement document "div")
        chip-text (.createElement document "span")
        chip-text-node (support/create-text-node! document "")]
    (.setAttribute row "data-position-pnl-row" "true")
    (.setAttribute chip "data-position-pnl-price-chip" "true")
    (.appendChild badge-text badge-text-node)
    (.appendChild chip-text chip-text-node)
    (.appendChild badge badge-text)
    (.appendChild chip chip-text)
    (.appendChild row line)
    (.appendChild row badge)
    (.appendChild row chip)
    {:row row
     :line line
     :badge badge
     :badge-text badge-text
     :badge-text-node badge-text-node
     :chip chip
     :chip-text chip-text
     :chip-text-node chip-text-node}))

(defn hide-pnl-row!
  [{:keys [row badge-text-node chip-text-node]}]
  (support/apply-inline-style! row {"display" "none"})
  (support/set-text-node-value! badge-text-node "")
  (support/set-text-node-value! chip-text-node ""))

(defn patch-pnl-row!
  [{:keys [row line badge badge-text badge-text-node chip chip-text chip-text-node]} overlay y width]
  (let [pnl-text (presentation/format-pnl-text (:unrealized-pnl overlay))
        size-text (presentation/format-size-text (:format-size overlay) (:abs-size overlay))
        pnl-label-text (str "PNL " pnl-text " | " size-text)
        estimated-badge-width (presentation/estimate-badge-width-px 56 pnl-label-text)
        center-x (presentation/clamp-badge-center-x width
                                                    (presentation/preferred-pnl-badge-x width)
                                                    estimated-badge-width)
        chip-color (presentation/pnl-line-color overlay)
        safe-width (support/non-negative-number width 0)]
    (support/apply-inline-style!
     row
     {"position" "absolute"
      "display" "block"
      "left" "0px"
      "right" "0px"
      "top" (str y "px")
      "height" "0px"
      "pointerEvents" "none"})
    (support/apply-inline-style!
     line
     {"position" "absolute"
      "left" "0px"
      "right" "0px"
      "top" "0px"
      "borderTop" (str "1px dashed " chip-color)
      "opacity" "0.88"
      "pointerEvents" "none"})
    (support/apply-inline-style!
     badge
     {"position" "absolute"
      "left" (str center-x "px")
      "top" "0px"
      "transform" "translate(-50%, -50%)"
      "display" "inline-flex"
      "alignItems" "center"
      "padding" "2px 7px"
      "fontSize" "11px"
      "lineHeight" "16px"
      "fontWeight" "600"
      "borderRadius" "3px"
      "border" (str "1px solid " chip-color)
      "background" (presentation/pnl-badge-color overlay)
      "backdropFilter" "blur(0.5px)"
      "color" (presentation/pnl-text-color overlay)
      "pointerEvents" "none"})
    (support/apply-inline-style!
     badge-text
     {"whiteSpace" "nowrap"
      "userSelect" "none"})
    (support/set-text-node-value! badge-text-node pnl-label-text)
    (support/apply-inline-style!
     chip
     {"position" "absolute"
      "left" (str safe-width "px")
      "top" "0px"
      "transform" "translate(2px, -50%)"
      "display" "inline-flex"
      "alignItems" "center"
      "padding" "1px 6px"
      "fontSize" "11px"
      "lineHeight" "16px"
      "fontWeight" "600"
      "borderRadius" "2px"
      "border" (str "1px solid " chip-color)
      "background" chip-color
      "color" presentation/pnl-chip-text-color
      "whiteSpace" "nowrap"
      "pointerEvents" "none"})
    (support/apply-inline-style!
     chip-text
     {"whiteSpace" "nowrap"})
    (support/set-text-node-value!
     chip-text-node
     (presentation/format-axis-price-text (:format-price overlay)
                                          (:entry-price overlay)))))

(defn- start-liquidation-drag!
  [on-liquidation-drag-start source-node event]
  (when (fn? on-liquidation-drag-start)
    (on-liquidation-drag-start source-node event)))

(defn create-liquidation-row-nodes!
  [document on-liquidation-drag-start]
  (let [row (.createElement document "div")
        hit-area (.createElement document "div")
        line (.createElement document "div")
        badge (.createElement document "div")
        label (.createElement document "span")
        label-text-node (support/create-text-node! document "")
        price (.createElement document "span")
        price-text-node (support/create-text-node! document "")
        drag-note (.createElement document "span")
        drag-note-text-node (support/create-text-node! document "")
        chip (.createElement document "div")
        chip-text (.createElement document "span")
        chip-text-node (support/create-text-node! document "")
        on-hit-pointer-down (fn [event]
                              (start-liquidation-drag! on-liquidation-drag-start hit-area event))
        on-badge-pointer-down (fn [event]
                                (start-liquidation-drag! on-liquidation-drag-start badge event))]
    (.setAttribute row "data-position-liq-row" "true")
    (.setAttribute hit-area "data-position-liq-drag-hit" "true")
    (.setAttribute hit-area "data-position-margin-trigger" "true")
    (.setAttribute badge "title" "Drag to adjust liquidation target")
    (.setAttribute badge "data-position-liq-drag-handle" "true")
    (.setAttribute badge "data-position-margin-trigger" "true")
    (.setAttribute chip "data-position-liq-price-chip" "true")
    (.addEventListener hit-area "pointerdown" on-hit-pointer-down)
    (.addEventListener hit-area "mousedown" on-hit-pointer-down)
    (.addEventListener hit-area "touchstart" on-hit-pointer-down)
    (.addEventListener badge "pointerdown" on-badge-pointer-down)
    (.addEventListener badge "mousedown" on-badge-pointer-down)
    (.addEventListener badge "touchstart" on-badge-pointer-down)
    (.appendChild label label-text-node)
    (.appendChild price price-text-node)
    (.appendChild drag-note drag-note-text-node)
    (.appendChild chip-text chip-text-node)
    (.appendChild badge label)
    (.appendChild badge price)
    (.appendChild badge drag-note)
    (.appendChild chip chip-text)
    (.appendChild row hit-area)
    (.appendChild row line)
    (.appendChild row badge)
    (.appendChild row chip)
    {:row row
     :hit-area hit-area
     :line line
     :badge badge
     :label label
     :label-text-node label-text-node
     :price price
     :price-text-node price-text-node
     :drag-note drag-note
     :drag-note-text-node drag-note-text-node
     :chip chip
     :chip-text chip-text
     :chip-text-node chip-text-node}))

(defn hide-liquidation-row!
  [{:keys [row label-text-node price-text-node drag-note-text-node chip-text-node]}]
  (support/apply-inline-style! row {"display" "none"})
  (support/set-text-node-value! label-text-node "")
  (support/set-text-node-value! price-text-node "")
  (support/set-text-node-value! drag-note-text-node "")
  (support/set-text-node-value! chip-text-node ""))

(defn patch-liquidation-row!
  [{:keys [row hit-area line badge label label-text-node price price-text-node drag-note drag-note-text-node chip chip-text chip-text-node]} overlay y width]
  (let [liq-price-text (presentation/format-price-text (:format-price overlay)
                                                       (:liquidation-price overlay))
        drag-label (liquidation-drag/liquidation-drag-label overlay
                                                            (:current-liquidation-price overlay)
                                                            (:liquidation-price overlay))
        liq-label-text (str "Liq. Price " liq-price-text)
        full-label-text (if (seq drag-label)
                          (str liq-label-text " | " drag-label)
                          liq-label-text)
        estimated-badge-width (presentation/estimate-badge-width-px 52 full-label-text)
        badge-x (presentation/clamp-badge-center-x width
                                                   (+ presentation/chart-edge-padding-px
                                                      (/ estimated-badge-width 2)
                                                      10)
                                                   estimated-badge-width)
        hit-half-height (/ presentation/liquidation-drag-hit-height-px 2)
        safe-width (support/non-negative-number width 0)]
    (support/apply-inline-style!
     row
     {"position" "absolute"
      "display" "block"
      "left" "0px"
      "right" "0px"
      "top" (str y "px")
      "height" "0px"
      "pointerEvents" "none"})
    (support/apply-inline-style!
     hit-area
     {"position" "absolute"
      "left" "0px"
      "right" "0px"
      "top" (str (- 0 hit-half-height) "px")
      "height" (str presentation/liquidation-drag-hit-height-px "px")
      "background" "transparent"
      "cursor" "ns-resize"
      "pointerEvents" "auto"})
    (support/apply-inline-style!
     line
     {"position" "absolute"
      "left" "0px"
      "right" "0px"
      "top" "0px"
      "borderTop" (str "1px dashed " presentation/liq-line-color)
      "opacity" "0.84"
      "pointerEvents" "none"})
    (support/apply-inline-style!
     badge
     {"position" "absolute"
      "left" (str badge-x "px")
      "top" "0px"
      "transform" "translate(-50%, -50%)"
      "display" "inline-flex"
      "alignItems" "center"
      "gap" "6px"
      "padding" "2px 6px"
      "fontSize" "11px"
      "lineHeight" "16px"
      "fontWeight" "600"
      "borderRadius" "3px"
      "border" (str "1px solid " presentation/liq-line-color)
      "background" presentation/liq-badge-color
      "backdropFilter" "blur(0.5px)"
      "color" presentation/liq-text-color
      "cursor" "ns-resize"
      "pointerEvents" "auto"})
    (support/apply-inline-style!
     label
     {"whiteSpace" "nowrap"})
    (support/set-text-node-value! label-text-node "Liq. Price")
    (support/apply-inline-style!
     price
     {"whiteSpace" "nowrap"})
    (support/set-text-node-value! price-text-node liq-price-text)
    (support/set-text-node-value! drag-note-text-node drag-label)
    (support/apply-inline-style!
     drag-note
     {"color" presentation/liq-drag-text-color
      "whiteSpace" "nowrap"
      "display" (if (seq drag-label) "inline" "none")})
    (support/apply-inline-style!
     chip
     {"position" "absolute"
      "left" (str safe-width "px")
      "top" "0px"
      "transform" "translate(2px, -50%)"
      "display" "inline-flex"
      "alignItems" "center"
      "padding" "1px 6px"
      "fontSize" "11px"
      "lineHeight" "16px"
      "fontWeight" "600"
      "borderRadius" "2px"
      "border" (str "1px solid " presentation/liq-line-color)
      "background" presentation/liq-line-color
      "color" presentation/pnl-chip-text-color
      "whiteSpace" "nowrap"
      "pointerEvents" "none"})
    (support/apply-inline-style!
     chip-text
     {"whiteSpace" "nowrap"})
    (support/set-text-node-value!
     chip-text-node
     (presentation/format-axis-price-text (:format-price overlay)
                                          (:liquidation-price overlay)))))

(defn create-overlay-dom!
  [document on-liquidation-drag-start]
  {:document document
   :pnl (create-pnl-row-nodes! document)
   :liquidation (create-liquidation-row-nodes! document on-liquidation-drag-start)})

(defn ensure-overlay-dom!
  [state document root on-liquidation-drag-start]
  (let [overlay-dom (if (identical? document (get-in state [:overlay-dom :document]))
                      (:overlay-dom state)
                      (create-overlay-dom! document on-liquidation-drag-start))]
    (support/append-child! root (get-in overlay-dom [:pnl :row]))
    (support/append-child! root (get-in overlay-dom [:liquidation :row]))
    [(assoc state :overlay-dom overlay-dom) overlay-dom]))
