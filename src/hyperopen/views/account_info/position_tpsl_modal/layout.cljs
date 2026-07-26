(ns hyperopen.views.account-info.position-tpsl-modal.layout)

(def ^:private panel-gap-px 8)
(def ^:private panel-margin-px 16)
(def ^:private preferred-panel-width-px 500)
(def ^:private fallback-viewport-width 1280)
(def ^:private fallback-viewport-height 800)
(def ^:private fallback-anchor-top 640)
(def ^:private mobile-sheet-breakpoint-px 640)
(def ^:private mobile-sheet-top-offset-px 20)

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

(defn modal-layout-style
  [modal]
  (let [anchor (or (:anchor modal) {})
        viewport-width (max 320
                           (anchor-number anchor :viewport-width fallback-viewport-width)
                           (+ (anchor-number anchor :right 0) panel-margin-px))
        anchor-right (anchor-number anchor :right (- viewport-width panel-margin-px))
        anchor-top (anchor-number anchor :top fallback-anchor-top)
        panel-width (clamp (- viewport-width (* 2 panel-margin-px))
                           280
                           preferred-panel-width-px)
        left (clamp (- anchor-right panel-width)
                    panel-margin-px
                    (- viewport-width panel-width panel-margin-px))
        available-above (max 1 (- anchor-top panel-margin-px panel-gap-px))]
    {:left (str left "px")
     :top (str (- anchor-top panel-gap-px) "px")
     :transform "translateY(-100%)"
     :width (str panel-width "px")
     :max-height (str available-above "px")}))

(defn mobile-sheet?
  [modal]
  (let [anchor (or (:anchor modal) {})
        viewport-width (max 320
                            (anchor-number anchor :viewport-width fallback-viewport-width)
                            (+ (anchor-number anchor :right 0) panel-margin-px))]
    (<= viewport-width mobile-sheet-breakpoint-px)))

(defn mobile-sheet-style
  [modal]
  (let [anchor (or (:anchor modal) {})
        viewport-height (max 320
                             (anchor-number anchor :viewport-height fallback-viewport-height))
        max-height (max 320 (- viewport-height mobile-sheet-top-offset-px))]
    {:max-height (str max-height "px")
     :padding-bottom "max(env(safe-area-inset-bottom), 1rem)"
     :transition "transform 0.16s ease-out, opacity 0.16s ease-out"
     :transform "translateY(0)"
     :opacity 1}))
