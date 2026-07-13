(ns hyperopen.views.ui.anchored-popover)

(def ^:private panel-gap-px
  10)

(def ^:private panel-margin-px
  12)

(def ^:private fallback-viewport-width
  1280)

(def ^:private fallback-viewport-height
  800)

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- anchor-number
  [anchor k default]
  (let [value (get anchor k)]
    (if (number? value)
      value
      default)))

(defn complete-anchor?
  [anchor]
  (and (map? anchor)
       (number? (:left anchor))
       (number? (:right anchor))
       (number? (:top anchor))))

(defn anchored-popover-layout-style
  [{:keys [anchor preferred-width-px estimated-height-px]
    :or {preferred-width-px 448
         estimated-height-px 560}}]
  (let [anchor* (if (map? anchor) anchor {})
        viewport-width (max 320
                            (anchor-number anchor* :viewport-width fallback-viewport-width)
                            (+ (anchor-number anchor* :right 0) panel-margin-px))
        viewport-height (max 320
                             (anchor-number anchor* :viewport-height fallback-viewport-height))
        anchor-left (anchor-number anchor* :left (- viewport-width panel-margin-px))
        anchor-right (anchor-number anchor* :right anchor-left)
        anchor-top (anchor-number anchor* :top (- viewport-height panel-margin-px))
        available-width (max 0 (- viewport-width (* 2 panel-margin-px)))
        panel-width (min preferred-width-px available-width)
        left-of-anchor (- anchor-left panel-gap-px panel-width)
        right-of-anchor (+ anchor-right panel-gap-px)
        fits-left? (>= left-of-anchor panel-margin-px)
        fits-right? (<= (+ right-of-anchor panel-width panel-margin-px)
                        viewport-width)
        left (cond
               fits-left? left-of-anchor
               fits-right? right-of-anchor
               :else (clamp left-of-anchor
                            panel-margin-px
                            (- viewport-width panel-width panel-margin-px)))
        preferred-top (- anchor-top 20)
        max-top (- viewport-height estimated-height-px panel-margin-px)
        top (if (> max-top panel-margin-px)
              (clamp preferred-top panel-margin-px max-top)
              panel-margin-px)]
    {:left (str left "px")
     :top (str top "px")
     :width (str panel-width "px")}))

(defn centered-overlay-layout-style
  "Layout for a large, wide popover that should sit centred over the main
  content area rather than hugging a small trigger. It centres horizontally and
  biases the top toward the upper third of the viewport (`vertical-bias`, a
  fraction of viewport height) so the panel covers the chart region instead of
  the panels above and below it. Clamped within the viewport; on a viewport too
  short for the estimated height it pins to the top margin and relies on the
  panel's own internal scroll."
  [{:keys [anchor preferred-width-px preferred-height-px vertical-bias]
    :or {preferred-width-px 780
         preferred-height-px 560
         vertical-bias 0.16}}]
  (let [anchor* (if (map? anchor) anchor {})
        viewport-width (max 320
                            (anchor-number anchor* :viewport-width fallback-viewport-width))
        viewport-height (max 320
                             (anchor-number anchor* :viewport-height fallback-viewport-height))
        available-width (max 0 (- viewport-width (* 2 panel-margin-px)))
        panel-width (min preferred-width-px available-width)
        left (clamp (/ (- viewport-width panel-width) 2)
                    panel-margin-px
                    (max panel-margin-px (- viewport-width panel-width panel-margin-px)))
        max-top (- viewport-height preferred-height-px panel-margin-px)
        top (if (> max-top panel-margin-px)
              (clamp (* vertical-bias viewport-height) panel-margin-px max-top)
              panel-margin-px)]
    {:left (str (js/Math.round left) "px")
     :top (str (js/Math.round top) "px")
     :width (str (js/Math.round panel-width) "px")}))
