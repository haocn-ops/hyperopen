(ns hyperopen.views.portfolio.montecarlo.chart.model)

(def palette
  {:accent "#00d4aa"
   :red "#ff6b6b"
   :gold "#d4b558"
   :border "#21333b"
   :text-2 "#8b9ba2"
   :text-3 "#5d6f76"})

(def mono-font "\"JetBrains Mono\", ui-monospace, monospace")

(def spaghetti-padding
  {:pad-l 64
   :pad-r 16
   :pad-t 14
   :pad-b 26})

(defn axis-time-label
  "Compact elapsed-time tick label (the x-axis is calendar time, not a day count
  — vault points are sparse and irregular)."
  [y]
  (cond
    (<= y 0) "0"
    (< y (/ 1.0 12)) (str (js/Math.round (* y 365)) "d")
    (< y 1) (str (js/Math.round (* y 12)) "mo")
    :else (str (.toFixed y 1) "y")))

(defn point
  [x y]
  {:x x :y y})

(defn- style-colors
  [{:keys [accent band bust-color]}]
  (let [accent* (or accent (:accent palette))]
    {:accent accent*
     :band-col (or band accent*)
     :bust-col (or bust-color (:red palette))}))

(defn- spaghetti-domain
  [result]
  (let [start (get-in result [:meta :start-equity])
        bust (get-in result [:meta :bust])
        goal (get-in result [:meta :goal])
        {:keys [p5 p95]} (:band result)
        bust-eq (* start (+ 1 bust))
        goal-eq (* start (+ 1 goal))
        lo0 (reduce min (-> (vec p5) (conj bust-eq) (conj start)))
        hi0 (reduce max (-> (vec p95) (conj goal-eq) (conj start)))
        range0 (max 1e-9 (- hi0 lo0))
        pad-range (* range0 0.06)]
    {:start start
     :horizon (get-in result [:meta :horizon])
     :span-years (or (get-in result [:meta :span-years]) 0)
     :bust bust
     :goal goal
     :bust-eq bust-eq
     :goal-eq goal-eq
     :lo (- lo0 pad-range)
     :hi (+ hi0 pad-range)}))

(defn- plot-geometry
  [{:keys [w h]}]
  (let [{:keys [pad-l pad-r pad-t pad-b]} spaghetti-padding]
    (assoc spaghetti-padding
           :w w
           :h h
           :plot-w (- w pad-l pad-r)
           :plot-h (- h pad-t pad-b))))

(defn- scale-fns
  [{:keys [pad-l pad-t plot-w plot-h]} {:keys [horizon lo hi]}]
  {:x-of (fn [t] (+ pad-l (* (/ t horizon) plot-w)))
   :y-of (fn [v] (+ pad-t (* (- 1 (/ (- v lo) (- hi lo))) plot-h)))})

(defn- visible-window
  [times horizon progress]
  (let [n (count times)
        progress* (if (nil? progress) 1 progress)]
    {:n n
     :progress progress*
     :max-i (max 1 (js/Math.floor (* progress* (dec n))))
     :last-t (js/Math.floor (* progress* horizon))}))

(defn- time-point
  [times values max-i x-of y-of]
  (mapv (fn [i]
          (point (x-of (nth times i))
                 (y-of (nth values i))))
        (range 0 (inc max-i))))

(defn- reversed-time-point
  [times values max-i x-of y-of]
  (mapv (fn [i]
          (point (x-of (nth times i))
                 (y-of (nth values i))))
        (range max-i -1 -1)))

(defn- grid-lines
  [{:keys [start lo hi]} y-of]
  (mapv (fn [i]
          (let [v (+ lo (* (- hi lo) (/ i 5)))
                ret-pct (* (- (/ v start) 1) 100)]
            {:y (y-of v)
             :label (str (when (>= ret-pct 0) "+")
                         (.toFixed ret-pct 0)
                         "%")}))
        (range 6)))

(defn- x-axis-labels
  [{:keys [horizon span-years]} x-of plot]
  (mapv (fn [i]
          (let [frac (/ i 4)
                t (js/Math.round (* frac horizon))]
            {:x (x-of t)
             :y (+ (:pad-t plot) (:plot-h plot) 8)
             :label (axis-time-label (* frac span-years))}))
        (range 5)))

(defn- sampled-paths
  [result {:keys [horizon]} {:keys [last-t]} {:keys [x-of y-of]} {:keys [show-paths? path-count]}]
  (if show-paths?
    (let [paths (:draw-paths result)
          shown (min (count paths) (or path-count 120))]
      (mapv (fn [path]
              (mapv (fn [t]
                      (point (x-of t) (y-of (aget path t))))
                    (range 0 (inc last-t))))
            (take shown paths)))
    []))

(defn- endpoint-dots
  [band final-i colors x y-of progress]
  (when (>= progress 0.999)
    (mapv (fn [[edge col r]]
            (assoc (point x (y-of (nth edge final-i)))
                   :color col
                   :radius r))
          [[(:p95 band) (:band-col colors) 2.3]
           [(:p50 band) (:accent colors) 3.2]
           [(:p5 band) (:band-col colors) 2.3]])))

(defn spaghetti-model
  [box {:keys [result progress] :as spec}]
  (let [colors (style-colors spec)
        plot (plot-geometry box)
        domain (spaghetti-domain result)
        scales (scale-fns plot domain)
        {:keys [x-of y-of]} scales
        times (:times result)
        band (:band result)
        window (visible-window times (:horizon domain) progress)
        max-i (:max-i window)
        final-i (dec (count times))
        end-x (x-of (nth times final-i))]
    (merge window
           {:colors colors
            :plot plot
            :domain domain
            :scales scales
            :x-axis (x-axis-labels domain x-of plot)
            :grid-lines (grid-lines domain y-of)
            :outer-band-points (into (time-point times (:p95 band) max-i x-of y-of)
                                     (reversed-time-point times (:p5 band) max-i x-of y-of))
            :inner-band-points (into (time-point times (:p75 band) max-i x-of y-of)
                                     (reversed-time-point times (:p25 band) max-i x-of y-of))
            :median-points (time-point times (:p50 band) max-i x-of y-of)
            :p95-points (time-point times (:p95 band) max-i x-of y-of)
            :p5-points (time-point times (:p5 band) max-i x-of y-of)
            :sampled-paths (sampled-paths result domain window scales spec)
            :bust-y (y-of (:bust-eq domain))
            :goal-y (y-of (:goal-eq domain))
            :start-y (y-of (:start domain))
            :endpoint-dots (or (endpoint-dots band final-i colors end-x y-of (:progress window))
                               [])
            :hover-geo {:pad-l (:pad-l plot)
                        :plot-w (:plot-w plot)
                        :horizon (:horizon domain)
                        :start (:start domain)
                        :span-years (:span-years domain)}})))
