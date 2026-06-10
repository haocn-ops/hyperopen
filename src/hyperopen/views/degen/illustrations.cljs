(ns hyperopen.views.degen.illustrations
  "Original line-art mascots for the degen experience, drawn as inline SVG.

   Single-color stroke art on purpose: every character inherits
   `currentColor` from a `text-ho-*` token class on its wrapper, so the
   cast restyles with the theme and the color-literal ratchet stays
   clean. No binary assets, no third-party artwork.")

(def ^:private stroke-attrs
  {:stroke "currentColor"
   :stroke-width "4"
   :stroke-linecap "round"
   :stroke-linejoin "round"
   :fill "none"})

(defn pepe
  "Wide-mouthed frog face. Wrap in a text-ho-buy (or similar) element."
  [size-class]
  [:svg {:viewBox "0 0 100 90"
         :class [size-class]
         :role "img"
         :aria-label "smug frog mascot"
         :fill "none"}
   ;; eye domes
   [:path (merge stroke-attrs {:d "M14 34 C 14 18, 42 18, 42 32"})]
   [:path (merge stroke-attrs {:d "M46 32 C 46 16, 76 16, 76 32"})]
   ;; pupils, heavy lids
   [:circle {:cx "30" :cy "29" :r "3.5" :fill "currentColor"}]
   [:circle {:cx "62" :cy "28" :r "3.5" :fill "currentColor"}]
   [:path (merge stroke-attrs {:d "M18 26 L 40 24"})]
   [:path (merge stroke-attrs {:d "M50 24 L 72 22"})]
   ;; head outline
   [:path (merge stroke-attrs
                 {:d "M14 34 C 4 44, 6 64, 18 72 C 34 82, 66 82, 82 72 C 94 64, 94 44, 76 32"})]
   ;; wide lips
   [:path (merge stroke-attrs {:d "M16 58 C 40 70, 64 70, 86 56"})]
   [:path (merge stroke-attrs {:d "M20 64 C 42 74, 62 74, 82 62"})]
   ;; nostrils
   [:circle {:cx "44" :cy "44" :r "1.8" :fill "currentColor"}]
   [:circle {:cx "54" :cy "44" :r "1.8" :fill "currentColor"}]])

(defn doge
  "Shiba face; `:shades?` adds dealer sunglasses (Shill of the Day)."
  ([size-class] (doge size-class {}))
  ([size-class {:keys [shades?]}]
   [:svg {:viewBox "0 0 100 96"
          :class [size-class]
          :role "img"
          :aria-label "shiba mascot"
          :fill "none"}
    ;; ears
    [:path (merge stroke-attrs {:d "M22 30 L 16 6 L 40 18"})]
    [:path (merge stroke-attrs {:d "M78 30 L 84 6 L 60 18"})]
    ;; head
    [:path (merge stroke-attrs
                  {:d "M22 30 C 8 44, 10 70, 28 82 C 42 90, 58 90, 72 82 C 90 70, 92 44, 78 30 C 64 18, 36 18, 22 30"})]
    (if shades?
      ;; one-bar dealer shades
      [:path (merge stroke-attrs
                    {:d "M16 42 L 84 40 M26 42 L 28 52 C 28 56, 42 56, 42 50 L 42 42 M58 41 L 58 50 C 58 56, 72 55, 72 51 L 71 41"})]
      [:g
       [:circle {:cx "36" :cy "46" :r "3.5" :fill "currentColor"}]
       [:circle {:cx "64" :cy "46" :r "3.5" :fill "currentColor"}]
       [:path (merge stroke-attrs {:d "M28 38 L 42 40 M58 40 L 72 38"})]])
    ;; snout + cheeks
    [:circle {:cx "50" :cy "62" :r "3" :fill "currentColor"}]
    [:path (merge stroke-attrs {:d "M50 66 C 46 72, 42 72, 40 70 M50 66 C 54 72, 58 72, 60 70"})]
    [:circle {:cx "26" :cy "62" :r "1.6" :fill "currentColor"}]
    [:circle {:cx "74" :cy "62" :r "1.6" :fill "currentColor"}]]))

(defn whale
  "Round whale with a spout and shades (Whale Watch)."
  [size-class]
  [:svg {:viewBox "0 0 120 90"
         :class [size-class]
         :role "img"
         :aria-label "whale mascot"
         :fill "none"}
   ;; spout
   [:path (merge stroke-attrs {:d "M44 18 C 40 10, 32 8, 26 10 M44 18 C 44 8, 52 4, 58 6"})]
   ;; body
   [:path (merge stroke-attrs
                 {:d "M16 50 C 14 30, 36 22, 56 26 C 84 30, 96 44, 98 56 C 99 66, 92 74, 80 76 C 56 80, 24 76, 16 62 Z"})]
   ;; tail
   [:path (merge stroke-attrs {:d "M98 56 C 106 50, 112 48, 116 50 C 113 56, 113 62, 116 68 C 108 68, 102 64, 98 60"})]
   ;; belly lines
   [:path (merge stroke-attrs {:d "M22 62 C 36 70, 56 72, 74 70" :stroke-width "3"})]
   ;; shades
   [:path (merge stroke-attrs
                 {:d "M28 40 L 62 38 M32 40 L 33 47 C 33 51, 44 51, 44 46 L 44 39 M50 39 L 50 46 C 50 51, 60 50, 60 46 L 59 38"})]
   ;; smile
   [:path (merge stroke-attrs {:d "M30 54 C 36 58, 44 58, 50 55" :stroke-width "3"})]])

(defn gauge-angle
  "Needle angle in degrees for unrealized PNL; +/-75 full scale at +/-$500,
   0 for nil/zero. Pure for tests."
  [unrealized-pnl]
  (if-not (number? unrealized-pnl)
    0
    (-> (* (/ unrealized-pnl 500) 75)
        (max -75)
        (min 75))))

(defn feeling-gauge-dial
  "Semicircular sell->warn->buy gauge; needle position derives from real
   PNL via gauge-angle. Token classes color the arc segments."
  [unrealized-pnl]
  (let [angle (gauge-angle unrealized-pnl)]
    [:svg {:viewBox "0 0 100 58"
           :class ["w-24"]
           :role "img"
           :aria-label "feeling gauge"
           :fill "none"
           :data-role "degen-feeling-dial"}
     [:path {:d "M10 50 A 40 40 0 0 1 26 18"
             :class ["text-ho-sell"]
             :stroke "currentColor"
             :stroke-width "8"
             :stroke-linecap "round"}]
     [:path {:d "M31 14 A 40 40 0 0 1 69 14"
             :class ["text-ho-warn"]
             :stroke "currentColor"
             :stroke-width "8"
             :stroke-linecap "round"}]
     [:path {:d "M74 18 A 40 40 0 0 1 90 50"
             :class ["text-ho-buy"]
             :stroke "currentColor"
             :stroke-width "8"
             :stroke-linecap "round"}]
     [:g {:class ["text-ho-text"]
          :transform (str "rotate(" angle " 50 50)")}
      [:path {:d "M50 50 L 50 20"
              :stroke "currentColor"
              :stroke-width "3.5"
              :stroke-linecap "round"}]
      [:circle {:cx "50" :cy "50" :r "4" :fill "currentColor"}]]]))
