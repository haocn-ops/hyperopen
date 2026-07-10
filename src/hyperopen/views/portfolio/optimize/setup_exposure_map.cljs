(ns hyperopen.views.portfolio.optimize.setup-exposure-map
  "The 2D exposure-map pieces. A trader drags one point on a small pad: the vertical axis is
  gross leverage, the horizontal axis is net (long/short) bias. A shaded box around the point is
  the exact min/max band sent to the solver. A large stacked readout beside the pad echoes the
  targets live. The band sliders, current-portfolio line, and remembered-profile row are exported
  separately — setup-constraint-controls composes them inside its Fine-tune drawer (2026-07-10
  simplified default view; the preset chips, drag caption, and legend were removed with it).

  The pad scale is FIXED while dragging: values clamp to the visible axis, and only the explicit
  zoom buttons (or a profile/reset re-fit) change the scale, so the mapping under the pointer can
  never shift mid-gesture.

  All dispatch goes through the atomic exposure actions; the pad's pointer coordinates are
  resolved by the :event/clientX, :event/clientY, :event.currentTarget/bounds, and
  :event/pointer-buttons placeholders and converted to targets purely in the action handler."
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]))

;; --- formatting ---------------------------------------------------------------------------

(defn- fmt-mult
  [x]
  (if (number? x) (str (.toFixed x 2) "×") "--"))

(defn- fmt-signed-mult
  "Net-bias multiple with an explicit sign so long/short is unambiguous (+1.00×, −0.50×)."
  [x]
  (if (number? x)
    (str (when (pos? x) "+") (.toFixed x 2) "×")
    "--"))

(defn- fmt-axis
  "Compact tick label: whole numbers show without decimals (10×), else one decimal (2.5×)."
  [x]
  (if (number? x)
    (if (== x (js/Math.round x))
      (str (js/Math.round x) "×")
      (str (.toFixed x 1) "×"))
    "--"))

(defn- pct
  "Fraction (0..1) → SVG user-space coordinate on the 0..100 pad."
  [f]
  (* 100 (or f 0)))

(defn- gross-echo
  [{:keys [gross-min gross-max gross-floored?]}]
  (if gross-floored?
    (str "gross " (fmt-mult gross-min) "–" (fmt-mult gross-max))
    (str "gross ≤ " (fmt-mult gross-max))))

(defn- net-echo
  [{:keys [net-min net-max]}]
  (cond
    (and (number? net-min) (number? net-max))
    (str "net " (fmt-mult net-min) "–" (fmt-mult net-max))

    (number? net-max) (str "net ≤ " (fmt-mult net-max))
    (number? net-min) (str "net ≥ " (fmt-mult net-min))
    :else "net unbounded"))

;; --- the SVG pad --------------------------------------------------------------------------

(defn- pad-pointer-action
  "Bake the current fixed axis scale AND its zoom level into the drag dispatch: the pointer maps
  to exactly the values the axis shows, and the handler pins the stored zoom to that level so
  the scale cannot shrink under the pointer when the policy re-fits smaller mid-gesture."
  [{:keys [axis zoom]}]
  [[:actions/set-portfolio-optimizer-exposure-point
    [:event/clientX]
    [:event/clientY]
    [:event.currentTarget/bounds]
    [:event/pointer-buttons]
    (:gross-max axis)
    (:net-extent axis)
    (:level zoom)]])

(defn- exposure-pad
  [{:keys [target-marker band-rect current-marker highlighted policy] :as model}]
  (let [{tx :x ty :y} target-marker
        {bx :x by :y bw :w bh :h} band-rect
        x0 (pct bx)
        x1 (pct (+ bx bw))
        y-top (pct by)
        y-bot (pct (+ by bh))
        target-x (pct tx)
        target-y (pct ty)
        gross-warn? (:gross highlighted)
        net-warn? (:net highlighted)
        aria (str "Exposure map. Gross target " (fmt-mult (:gross-target policy))
                  ", net target " (fmt-mult (:net-target policy))
                  ". Drag the point to set the target, or use the zoom buttons, the"
                  " Fine-tune band sliders, and the advanced fields.")]
    [:svg {:class ["optimizer-exposure-map__pad"]
           :viewBox "0 0 100 100"
           :preserveAspectRatio "none"
           :role "img"
           :aria-label aria
           :data-role "portfolio-optimizer-exposure-pad"
           :data-gross-infeasible (when gross-warn? "true")
           :data-net-infeasible (when net-warn? "true")
           :on {:pointerdown (pad-pointer-action model)
                :pointermove (pad-pointer-action model)}}
     ;; surface + gridlines: horizontals at the quartiles so the mid tick label lines up with a
     ;; drawn line; the vertical centre line is net 0.
     [:rect {:class ["optimizer-exposure-map__surface"] :x 0 :y 0 :width 100 :height 100}]
     [:line {:class ["optimizer-exposure-map__grid"] :x1 50 :y1 0 :x2 50 :y2 100}]
     [:line {:class ["optimizer-exposure-map__grid"] :x1 0 :y1 25 :x2 100 :y2 25}]
     [:line {:class ["optimizer-exposure-map__grid" "optimizer-exposure-map__grid--mid"]
             :x1 0 :y1 50 :x2 100 :y2 50}]
     [:line {:class ["optimizer-exposure-map__grid"] :x1 0 :y1 75 :x2 100 :y2 75}]
     ;; full-length band stripes: the gross range as a full-width horizontal
     ;; stripe, the net range as a full-height vertical stripe. At a wide axis
     ;; (e.g. a 15x book forcing the 0-20x frame) the band box around the dot is
     ;; smaller than the drag handle itself, so the stripes are what make a band
     ;; slider drag visibly expand/contract the allowed region at ANY zoom.
     [:rect {:class ["optimizer-exposure-map__band-stripe"]
             :data-role "portfolio-optimizer-exposure-gross-stripe"
             :x 0 :y y-top :width 100 :height (max 0 (- y-bot y-top))}]
     [:rect {:class ["optimizer-exposure-map__band-stripe"]
             :data-role "portfolio-optimizer-exposure-net-stripe"
             :x x0 :y 0 :width (max 0 (- x1 x0)) :height 100}]
     ;; the allowed-region band box (the stripes' intersection, emphasized)
     [:rect {:class ["optimizer-exposure-map__band"]
             :data-role "portfolio-optimizer-exposure-band-box"
             :x x0 :y y-top :width (max 0 (- x1 x0)) :height (max 0 (- y-bot y-top))}]
     ;; net range bar (horizontal) and gross range bar (vertical) — always legible, even at band 0
     [:line {:class ["optimizer-exposure-map__range"] :x1 x0 :y1 target-y :x2 x1 :y2 target-y}]
     [:line {:class ["optimizer-exposure-map__range"] :x1 target-x :y1 y-top :x2 target-x :y2 y-bot}]
     ;; current portfolio dot
     (when current-marker
       [:circle {:class ["optimizer-exposure-map__current"]
                 :data-role "portfolio-optimizer-exposure-current"
                 :cx (pct (:x current-marker)) :cy (pct (:y current-marker)) :r 2.2}])
     ;; target handle: an outer grab ring around the dot so it reads as a
     ;; draggable control, not a plotted marker.
     [:circle {:class ["optimizer-exposure-map__handle-ring"]
               :data-role "portfolio-optimizer-exposure-handle-ring"
               :cx target-x :cy target-y :r 5.6}]
     [:circle {:class ["optimizer-exposure-map__handle"]
               :data-role "portfolio-optimizer-exposure-handle"
               :cx target-x :cy target-y :r 3.4}]]))

;; --- axis frame + zoom + readout ----------------------------------------------------------

(defn- zoom-button
  "One step of the explicit scale control. `level` is the exact zoom level this button selects,
  baked into the dispatch by the view model; nil means the step is unavailable (disabled)."
  [{:keys [label level role aria-label]}]
  [:button (cond-> {:type "button"
                    :class ["optimizer-exposure-map__zoom-btn"]
                    :aria-label aria-label
                    :data-role role
                    :disabled (nil? level)}
             (some? level)
             (assoc :on {:click [[:actions/set-portfolio-optimizer-exposure-zoom-level level]]}))
   label])

(defn- axis-frame
  [axis zoom pad]
  (let [g-max (:gross-max axis)
        n-ext (:net-extent axis)]
    [:div {:class ["optimizer-exposure-map__frame"]}
     ;; Header row: the Y-axis title left (gross exposure IS leverage — :gross-max renames to
     ;; :gross-leverage for the solver), the X-axis title + the explicit zoom control right.
     ;; The scale NEVER changes from dragging; − widens the visible range, + tightens it back
     ;; down to the policy's fit (the current view range reads off the axis ticks themselves).
     [:div {:class ["optimizer-exposure-map__axis-header"]}
      [:span {:class ["optimizer-exposure-map__axis-title"]
              :data-role "portfolio-optimizer-exposure-y-title"}
       "Gross leverage (×)"]
      [:span {:class ["optimizer-exposure-map__zoom"]
              :data-role "portfolio-optimizer-exposure-zoom"}
       [:span {:class ["optimizer-exposure-map__axis-title"]
               :data-role "portfolio-optimizer-exposure-x-title"}
        "Net bias (×)"]
       (zoom-button {:label "−"
                     :level (:zoom-out-level zoom)
                     :role "portfolio-optimizer-exposure-zoom-out"
                     :aria-label "Zoom out to a higher leverage range"})
       (zoom-button {:label "+"
                     :level (:zoom-in-level zoom)
                     :role "portfolio-optimizer-exposure-zoom-in"
                     :aria-label "Zoom in to a tighter leverage range"})]]
     [:div {:class ["optimizer-exposure-map__yticks"]}
      [:span {:data-role "portfolio-optimizer-exposure-y-max"} (fmt-axis g-max)]
      [:span (fmt-axis (/ g-max 2))]
      [:span "0×"]]
     pad
     ;; Colored end ticks (short red, long green) carry direction on their own —
     ;; the "◄ Short / Long ►" words were chrome (designer mock, 2026-07-10).
     [:div {:class ["optimizer-exposure-map__xaxis"]}
      [:span {:class ["optimizer-exposure-map__axis-end"
                      "optimizer-exposure-map__axis-end--short"]}
       (str "−" (fmt-axis n-ext))]
      [:span {:class ["optimizer-exposure-map__axis-title"
                      "optimizer-exposure-map__axis-title--x"]}
       "0×"]
      [:span {:class ["optimizer-exposure-map__axis-end"
                      "optimizer-exposure-map__axis-end--long"]}
       (str "+" (fmt-axis n-ext))]]]))

(defn readout
  "The large stacked echo of the dragged targets beside the pad — the primary
  numbers of the section (designer mock, 2026-07-10): gross over its label,
  then the net figure tinted by direction."
  [{:keys [policy net-direction]}]
  [:div {:class ["flex" "shrink-0" "flex-col" "justify-center" "gap-4" "pl-1"]
         :data-role "portfolio-optimizer-exposure-readout"}
   [:div
    [:p {:class ["font-mono" "text-[1.375rem]" "font-semibold" "leading-none"
                 "text-trading-text"]
         :data-role "portfolio-optimizer-exposure-readout-gross"}
     (fmt-mult (:gross-target policy))]
    [:p {:class ["mt-1" "font-mono" "text-[0.6875rem]" "uppercase"
                 "tracking-[0.08em]" "text-trading-muted"]}
     "gross"]]
   [:div
    [:p {:class ["font-mono" "text-[1.375rem]" "font-semibold" "leading-none"
                 (case net-direction
                   :long "text-success"
                   :short "text-error"
                   "text-trading-text")]
         :data-role "portfolio-optimizer-exposure-readout-net"
         :data-net-direction (name (or net-direction :neutral))}
     (fmt-signed-mult (:net-target policy))]
    [:p {:class ["mt-1" "font-mono" "text-[0.6875rem]" "uppercase"
                 "tracking-[0.08em]" "text-trading-muted"]}
     (str "net" (case net-direction
                  :long " long"
                  :short " short"
                  ""))]]])

;; --- band sliders + echo + presets + memory ------------------------------------------------

(defn- band-slider
  [{:keys [label axis value max-band role level]}]
  [:label {:class ["optimizer-exposure-map__band-row"]}
   [:span {:class controls/eyebrow-class} label]
   [:input {:type "range"
            :min 0
            :max max-band
            :step 0.01
            :value (str value)
            :class ["optimizer-exposure-band" "w-full" "accent-warning"]
            :aria-label (str label " band")
            :data-role role
            ;; Sliders commit per-input (no free-text decimal problem); the action clamps. The
            ;; current zoom level is baked in so narrowing a band never shrinks the pad scale
            ;; mid-slide (widening may still grow it one step — the box must stay visible).
            :on {:input [[:actions/set-portfolio-optimizer-exposure-band
                          axis [:event.target/value] level]]}}]
   [:span {:class ["optimizer-exposure-map__band-value"]
           :data-role (str role "-value")}
    (str "± " (fmt-mult value))]])

(defn preview-block
  [{:keys [current-exposure on-policy? gross-ok? net-ok?]}]
  (when current-exposure
    [:p {:class ["optimizer-exposure-map__preview"]
         :data-role "portfolio-optimizer-exposure-preview"
         :data-on-policy (str (boolean on-policy?))}
     [:span {:class controls/eyebrow-class} "Current"]
     [:span {:class ["optimizer-exposure-map__preview-value"]}
      (str (fmt-mult (:gross current-exposure)) " gross · "
           (fmt-mult (:net current-exposure)) " net")]
     [:span {:class ["optimizer-exposure-map__preview-verdict"]}
      ;; Inside policy is the quiet state: chip-length, no sentence. Only the
      ;; off-policy states earn a full explanation.
      (cond
        on-policy? "Inside policy"
        (and (not gross-ok?) (not net-ok?)) "Current portfolio is outside this exposure policy: gross and net are out of range."
        (not gross-ok?) "Current portfolio is outside this exposure policy: gross is out of range."
        :else "Current portfolio is outside this exposure policy: net is out of range.")]]))

(defn profile-row
  [{:keys [has-default?]}]
  [:div {:class ["optimizer-exposure-map__profile"]
         :data-role "portfolio-optimizer-exposure-profile"}
   [:span {:class controls/eyebrow-class}
    (if has-default? "Saved default for this universe" "Memory")]
   [:div {:class ["optimizer-exposure-map__profile-actions"]}
    [:button {:type "button"
              :class ["optimizer-exposure-map__profile-btn"]
              :data-role "portfolio-optimizer-exposure-save-default"
              :on {:click [[:actions/save-portfolio-optimizer-constraint-default]]}}
     "Save as default"]
    (when has-default?
      [:button {:type "button"
                :class ["optimizer-exposure-map__profile-btn"]
                :data-role "portfolio-optimizer-exposure-apply-default"
                :on {:click [[:actions/apply-portfolio-optimizer-constraint-default]]}}
       "Use saved"])
    [:button {:type "button"
              :class ["optimizer-exposure-map__profile-btn"]
              :data-role "portfolio-optimizer-exposure-reset-default"
              :on {:click [[:actions/reset-portfolio-optimizer-constraints-to-system]]}}
     "Reset"]]])

(defn solver-echo
  "The exact generated-constraints line ('Sent to solver gross … · net …'). An
  implementation-facing audit detail, so it is rendered inside the Advanced
  solver limits drawer (setup-constraint-controls), not in the primary column."
  [echo]
  [:p {:class ["optimizer-exposure-map__echo"]
       :data-role "portfolio-optimizer-exposure-echo"}
   [:span {:class controls/eyebrow-class} "Sent to solver"]
   [:span {:class ["optimizer-exposure-map__echo-value"]}
    (str (gross-echo echo) " · " (net-echo echo))]])

(defn pad-frame
  "The bounded pad with its axis frame — the one always-visible exposure
  control. Carries the section's exposure-map role."
  [model]
  [:div {:class ["optimizer-exposure-map" "min-w-0" "flex-1"]
         :data-role "portfolio-optimizer-exposure-map"}
   (axis-frame (:axis model) (:zoom model) (exposure-pad model))])

(defn bands-block
  "Both band-tightness sliders, for the Fine-tune drawer."
  [{:keys [gross-band net-band max-band zoom]}]
  [:div {:class ["optimizer-exposure-map__bands"]}
   (band-slider {:label "Gross band" :axis :gross :value gross-band
                 :max-band max-band
                 :level (:level zoom)
                 :role "portfolio-optimizer-exposure-gross-band"})
   (band-slider {:label "Net band" :axis :net :value net-band
                 :max-band max-band
                 :level (:level zoom)
                 :role "portfolio-optimizer-exposure-net-band"})])

(defn policy-warning
  "Off-policy sentence for the DEFAULT view — an actionable violation must not
  hide behind the Fine-tune drawer (the rail's Review-warning anchor lands on
  this panel). The quiet CURRENT/'Inside policy' line stays in preview-block."
  [{:keys [current-exposure on-policy? gross-ok? net-ok?] :as preview}]
  (when (and preview current-exposure (false? on-policy?))
    [:p {:class ["font-mono" "text-[0.6875rem]" "leading-[1.5]" "text-warning"]
         :data-role "portfolio-optimizer-exposure-policy-line"}
     (cond
       (and (not gross-ok?) (not net-ok?))
       "Current portfolio is outside this exposure policy: gross and net are out of range."
       (not gross-ok?)
       "Current portfolio is outside this exposure policy: gross is out of range."
       :else
       "Current portfolio is outside this exposure policy: net is out of range.")]))
