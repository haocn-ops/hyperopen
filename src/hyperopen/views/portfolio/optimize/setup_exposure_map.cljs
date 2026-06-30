(ns hyperopen.views.portfolio.optimize.setup-exposure-map
  "The 2D exposure-map Positioning control. A trader drags one point on a small pad: the vertical
  axis is gross leverage, the horizontal axis is net (long/short) bias. A shaded box around the
  point is the exact min/max band sent to the solver. Two sliders set how tight each band is,
  preset chips seed sensible policies, and a read-only echo shows the exact gross/net ranges.

  All dispatch goes through the atomic exposure actions; the pad's pointer coordinates are
  resolved by the :event/clientX, :event/clientY, :event.currentTarget/bounds, and
  :event/pointer-buttons placeholders and converted to targets purely in the action handler."
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]))

;; --- formatting ---------------------------------------------------------------------------

(defn- fmt-mult
  [x]
  (if (number? x) (str (.toFixed x 2) "×") "--"))

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

(def ^:private pad-pointer-action
  [[:actions/set-portfolio-optimizer-exposure-point
    [:event/clientX]
    [:event/clientY]
    [:event.currentTarget/bounds]
    [:event/pointer-buttons]]])

(defn- exposure-pad
  [{:keys [target-marker band-rect current-marker highlighted policy]}]
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
                  ". Drag the point, or use the band sliders, presets, and advanced fields.")]
    [:svg {:class ["optimizer-exposure-map__pad"]
           :viewBox "0 0 100 100"
           :preserveAspectRatio "none"
           :role "img"
           :aria-label aria
           :data-role "portfolio-optimizer-exposure-pad"
           :data-gross-infeasible (when gross-warn? "true")
           :data-net-infeasible (when net-warn? "true")
           :on {:pointerdown pad-pointer-action
                :pointermove pad-pointer-action}}
     ;; surface + gridlines
     [:rect {:class ["optimizer-exposure-map__surface"] :x 0 :y 0 :width 100 :height 100}]
     [:line {:class ["optimizer-exposure-map__grid"] :x1 50 :y1 0 :x2 50 :y2 100}]
     [:line {:class ["optimizer-exposure-map__grid"] :x1 0 :y1 33.333 :x2 100 :y2 33.333}]
     [:line {:class ["optimizer-exposure-map__grid"] :x1 0 :y1 66.667 :x2 100 :y2 66.667}]
     ;; the allowed-region band box (visible when both bands are positive)
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
     ;; target handle
     [:circle {:class ["optimizer-exposure-map__handle"]
               :data-role "portfolio-optimizer-exposure-handle"
               :cx target-x :cy target-y :r 3.4}]]))

;; --- axis frame + band sliders + echo + presets -------------------------------------------

(defn- axis-frame
  [pad]
  [:div {:class ["optimizer-exposure-map__frame"]}
   [:span {:class ["optimizer-exposure-map__axis-y-top"]} "more gross"]
   [:span {:class ["optimizer-exposure-map__axis-y-bot"]} "less gross"]
   [:span {:class ["optimizer-exposure-map__axis-x-left"]} "short"]
   [:span {:class ["optimizer-exposure-map__axis-x-right"]} "long"]
   pad])

(defn- band-slider
  [{:keys [label axis value max-band role]}]
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
            ;; Sliders commit per-input (no free-text decimal problem); the action clamps.
            :on {:input [[:actions/set-portfolio-optimizer-exposure-band axis [:event.target/value]]]}}]
   [:span {:class ["optimizer-exposure-map__band-value"]
           :data-role (str role "-value")}
    (str "± " (fmt-mult value))]])

(defn- preview-block
  [{:keys [current-exposure on-policy? gross-ok? net-ok?]}]
  (when current-exposure
    [:p {:class ["optimizer-exposure-map__preview"]
         :data-role "portfolio-optimizer-exposure-preview"
         :data-on-policy (str (boolean on-policy?))}
     [:span {:class controls/eyebrow-class} "Now"]
     [:span {:class ["optimizer-exposure-map__preview-value"]}
      (str (fmt-mult (:gross current-exposure)) " gross · "
           (fmt-mult (:net current-exposure)) " net")]
     [:span {:class ["optimizer-exposure-map__preview-verdict"]}
      (cond
        on-policy? "on policy — no rebalance needed"
        (and (not gross-ok?) (not net-ok?)) "off policy — gross & net out of range"
        (not gross-ok?) "off policy — gross out of range"
        :else "off policy — net out of range")]]))

(defn- preset-chip
  [{:keys [key label active?]}]
  [:button {:type "button"
            :class ["optimizer-exposure-map__preset"]
            :aria-pressed (str (boolean active?))
            :data-role (str "portfolio-optimizer-exposure-preset-" (name key))
            :on {:click [[:actions/apply-portfolio-optimizer-exposure-preset key]]}}
   label])

(defn- profile-row
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

(defn exposure-map
  "Render the Positioning control from the exposure-map view-model."
  [model]
  (let [{:keys [gross-band net-band max-band echo presets preview profile]} model]
    [:div {:class ["optimizer-exposure-map"]
           :data-role "portfolio-optimizer-exposure-map"}
     (profile-row profile)
     (axis-frame (exposure-pad model))
     [:div {:class ["optimizer-exposure-map__bands"]}
      (band-slider {:label "Gross band" :axis :gross :value gross-band
                    :max-band max-band
                    :role "portfolio-optimizer-exposure-gross-band"})
      (band-slider {:label "Net band" :axis :net :value net-band
                    :max-band max-band
                    :role "portfolio-optimizer-exposure-net-band"})]
     [:p {:class ["optimizer-exposure-map__echo"]
          :data-role "portfolio-optimizer-exposure-echo"}
      [:span {:class controls/eyebrow-class} "Sent to solver"]
      [:span {:class ["optimizer-exposure-map__echo-value"]}
       (str (gross-echo echo) " · " (net-echo echo))]]
     (preview-block preview)
     (into [:div {:class ["optimizer-exposure-map__presets"]
                  :data-role "portfolio-optimizer-exposure-presets"}]
           (map preset-chip presets))]))
