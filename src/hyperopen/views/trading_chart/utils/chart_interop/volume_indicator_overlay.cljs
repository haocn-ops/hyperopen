(ns hyperopen.views.trading-chart.utils.chart-interop.volume-indicator-overlay
  (:require [clojure.string :as str]
            [hyperopen.views.trading-chart.utils.chart-interop.candle-sync-policy :as candle-sync-policy]))

(defonce ^:private volume-indicator-overlay-sidecar (js/WeakMap.))

(def ^:private indicator-label "Volume SMA")
(def ^:private neutral-value-color "#9ca3af")
(def ^:private up-value-color "#22ab94")
(def ^:private down-value-color "#f7525f")

(defn- overlay-state
  [chart-obj]
  (if chart-obj
    (or (.get volume-indicator-overlay-sidecar chart-obj) {})
    {}))

(defn- set-overlay-state!
  [chart-obj state]
  (when chart-obj
    (.set volume-indicator-overlay-sidecar chart-obj state))
  state)

(defn- resolve-document
  [document]
  (or document
      (some-> js/globalThis .-document)))

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))))

(defn- non-negative-number
  [value fallback]
  (if (and (finite-number? value)
           (not (neg? value)))
    value
    fallback))

(defn- invoke-method
  [target method-name & args]
  (let [method (when target
                 (aget target method-name))]
    (when (fn? method)
      (.apply method target (to-array args)))))

(defn- vectorize-candles
  [candles]
  (if (vector? candles) candles (vec candles)))

(defn- normalize-time-key
  [value]
  (let [business-day (cond
                       (and (map? value)
                            (number? (:year value))
                            (number? (:month value))
                            (number? (:day value)))
                       value

                       (and value
                            (not (number? value))
                            (not (string? value))
                            (not (keyword? value)))
                       (let [converted (js->clj value :keywordize-keys true)]
                         (when (and (map? converted)
                                    (number? (:year converted))
                                    (number? (:month converted))
                                    (number? (:day converted)))
                           converted))

                       :else
                       nil)]
    (cond
      (number? value) (str "ts:" value)
      (string? value) (str "txt:" value)
      business-day (str "bd:" (:year business-day) "-" (:month business-day) "-" (:day business-day))
      :else nil)))

(defn- trim-fractional-zeroes
  [text]
  (-> text
      (str/replace #"0+$" "")
      (str/replace #"\.$" "")))

(defn- format-volume-compact
  [value]
  (if-not (finite-number? value)
    "--"
    (let [abs-value (js/Math.abs value)
          [divisor suffix]
          (cond
            (>= abs-value 1.0e12) [1.0e12 "T"]
            (>= abs-value 1.0e9) [1.0e9 "B"]
            (>= abs-value 1.0e6) [1.0e6 "M"]
            (>= abs-value 1.0e3) [1.0e3 "K"]
            :else [1 ""])
          scaled (/ value divisor)
          fixed (.toFixed scaled (if (= divisor 1) 0 2))
          trimmed (trim-fractional-zeroes fixed)]
      (str trimmed suffix))))

(defn- volume-value-color
  [candle]
  (let [open (:open candle)
        close (:close candle)]
    (cond
      (or (not (finite-number? open))
          (not (finite-number? close)))
      neutral-value-color

      (>= close open)
      up-value-color

      :else
      down-value-color)))

(defn- assoc-candle
  [lookup candle]
  (if-let [lookup-key (normalize-time-key (:time candle))]
    (assoc lookup lookup-key candle)
    lookup))

(defn- build-candle-index-state
  [candles]
  (let [candles* (vectorize-candles candles)]
    (loop [remaining candles*
           lookup {}
           latest nil]
      (if-let [candle (first remaining)]
        (recur (rest remaining)
               (assoc-candle lookup candle)
               candle)
        {:candles candles*
         :lookup lookup
         :latest latest}))))

(defn- build-candle-index
  [candles]
  (select-keys (build-candle-index-state candles) [:lookup :latest]))

(defn- reconcile-candle-index-state
  [previous-state candles]
  (let [candles* (vectorize-candles candles)
        previous-state* (select-keys (or previous-state {}) [:candles :lookup :latest])
        previous-candles (:candles previous-state*)
        decision (if (contains? previous-state* :candles)
                   (candle-sync-policy/infer-decision previous-candles candles*)
                   {:mode :full-reset})]
    (case (:mode decision)
      :noop (or previous-state previous-state*)

      :append-last
      (let [next-last (peek candles*)]
        {:candles candles*
         :lookup (assoc-candle (:lookup previous-state*) next-last)
         :latest next-last})

      :update-last
      (let [next-last (peek candles*)
            previous-last (peek previous-candles)
            previous-key (normalize-time-key (:time previous-last))
            lookup* (cond-> (or (:lookup previous-state*) {})
                      previous-key (dissoc previous-key))]
        {:candles candles*
         :lookup (assoc-candle lookup* next-last)
         :latest next-last})

      (build-candle-index-state candles*))))

(defn- set-controls-visible!
  [controls visible?]
  (set! (.-display (.-style controls))
        (if visible? "inline-flex" "none")))

(defn- set-panel-focus-state!
  [panel focused?]
  (let [style (.-style panel)]
    (set! (.-borderColor style)
          (if focused?
            "rgba(56, 189, 248, 0.72)"
            "rgba(148, 163, 184, 0.2)"))
    (set! (.-boxShadow style)
          (if focused?
            "0 0 0 2px rgba(56, 189, 248, 0.28)"
            "none"))))

(defn- make-icon!
  [document path-d]
  (let [icon (.createElementNS document "http://www.w3.org/2000/svg" "svg")
        path (.createElementNS document "http://www.w3.org/2000/svg" "path")]
    (.setAttribute icon "viewBox" "0 0 20 20")
    (.setAttribute icon "width" "16")
    (.setAttribute icon "height" "16")
    (.setAttribute icon "aria-hidden" "true")
    (.setAttribute icon "focusable" "false")
    (.setAttribute icon "fill" "none")
    (.setAttribute icon "stroke" "currentColor")
    (.setAttribute icon "stroke-width" "2.2")
    (.setAttribute icon "stroke-linecap" "round")
    (.setAttribute icon "stroke-linejoin" "round")
    (.setAttribute path "d" path-d)
    (.appendChild icon path)
    icon))

(defn- set-control-button-visual-state!
  [button tone hovered?]
  (let [danger? (= tone :danger)
        border-color (if danger?
                       (if hovered?
                         "rgba(251, 113, 133, 0.92)"
                         "rgba(251, 113, 133, 0.64)")
                       (if hovered?
                         "rgba(96, 165, 250, 0.92)"
                         "rgba(148, 163, 184, 0.62)"))
        background (if danger?
                     (if hovered?
                       "linear-gradient(180deg, rgba(159, 18, 57, 0.95) 0%, rgba(136, 19, 55, 0.93) 100%)"
                       "linear-gradient(180deg, rgba(52, 21, 39, 0.92) 0%, rgba(31, 10, 18, 0.9) 100%)")
                     (if hovered?
                       "linear-gradient(180deg, rgba(30, 58, 138, 0.96) 0%, rgba(30, 64, 175, 0.95) 100%)"
                       "linear-gradient(180deg, rgba(15, 23, 42, 0.95) 0%, rgba(2, 6, 23, 0.93) 100%)"))
        color (if danger?
                (if hovered? "#fff1f2" "#ffe4e6")
                "#f8fafc")
        box-shadow (if hovered?
                     "inset 0 1px 0 rgba(255, 255, 255, 0.24), 0 2px 8px rgba(2, 6, 23, 0.55)"
                     "inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 1px 3px rgba(2, 6, 23, 0.55)")
        style (.-style button)]
    (set! (.-borderColor style) border-color)
    (set! (.-background style) background)
    (set! (.-color style) color)
    (set! (.-boxShadow style) box-shadow)
    (set! (.-transform style) (if hovered? "translateY(-1px)" "translateY(0)"))))

(defn- build-control-button!
  [document {:keys [aria-label title icon-path tone]} on-click]
  (let [button (.createElement document "button")]
    (.setAttribute button "type" "button")
    (.setAttribute button "aria-label" aria-label)
    (.setAttribute button "title" title)
    (let [style (.-style button)]
      (set! (.-width style) "30px")
      (set! (.-height style) "28px")
      (set! (.-padding style) "0")
      (set! (.-display style) "inline-flex")
      (set! (.-alignItems style) "center")
      (set! (.-justifyContent style) "center")
      (set! (.-borderRadius style) "7px")
      (set! (.-border style) "1px solid transparent")
      (set! (.-cursor style) "pointer")
      (set! (.-outline style) "none")
      (set! (.-transition style)
            "background 120ms ease,border-color 120ms ease,color 120ms ease,transform 120ms ease,box-shadow 120ms ease"))
    (set-control-button-visual-state! button tone false)
    (.addEventListener button "mouseenter"
                       (fn [_]
                         (set-control-button-visual-state! button tone true)))
    (.addEventListener button "mouseleave"
                       (fn [_]
                         (set-control-button-visual-state! button tone false)))
    (.addEventListener button "focus"
                       (fn [_]
                         (set-control-button-visual-state! button tone true)))
    (.addEventListener button "blur"
                       (fn [_]
                         (set-control-button-visual-state! button tone false)))
    (.addEventListener button "pointerdown" on-click)
    (.addEventListener button "click" on-click)
    (.appendChild button (make-icon! document icon-path))
    button))

(defn- ensure-relative-container!
  [container]
  (let [container-style (.-style container)]
    (when (or (not (.-position container-style))
              (= (.-position container-style) "static"))
      (set! (.-position container-style) "relative"))))

(defn- create-overlay-root!
  [chart-obj document]
  (let [root (.createElement document "div")
        panel (.createElement document "div")
        label-node (.createElement document "span")
        value-node (.createElement document "span")
        controls (.createElement document "div")
        settings-button
        (build-control-button!
         document
         {:aria-label "Volume settings (coming soon)"
          :title "Volume settings (coming soon)"
          :tone :neutral
          :icon-path "M11.2 2.6l.4 1.9c.5.2 1 .5 1.5.9l1.8-.6 1 1.8-1.5 1.2c.1.6.1 1.2 0 1.8l1.5 1.2-1 1.8-1.8-.6c-.4.4-.9.7-1.5.9l-.4 1.9H8.8l-.4-1.9a5.5 5.5 0 0 1-1.5-.9l-1.8.6-1-1.8 1.5-1.2a5.2 5.2 0 0 1 0-1.8L4.1 6.6l1-1.8 1.8.6c.5-.4 1-.7 1.5-.9l.4-1.9h2.4zM10 7.4a2.6 2.6 0 1 0 0 5.2 2.6 2.6 0 0 0 0-5.2z"}
         (fn [event]
           (.preventDefault event)
           (.stopPropagation event)))
        remove-button
        (build-control-button!
         document
         {:aria-label "Remove volume indicator"
          :title "Remove volume indicator"
          :tone :danger
          :icon-path "M4.5 6h11M7 6V4.8c0-.4.3-.6.6-.6h4.8c.3 0 .6.2.6.6V6M5.6 6l.6 9.5h7.6l.6-9.5M8.8 9.2v4.6M11.2 9.2v4.6"}
         (fn [event]
           (.preventDefault event)
           (.stopPropagation event)
           (when-let [on-remove (:on-remove (overlay-state chart-obj))]
             (on-remove))))]
    (.setAttribute root "data-role" "chart-volume-indicator-overlay")
    (set! (.-cssText (.-style root))
          "position:absolute;left:12px;top:8px;z-index:115;pointer-events:none;")
    (.setAttribute panel "tabindex" "0")
    (set! (.-cssText (.-style panel))
          (str "display:inline-flex;align-items:center;gap:8px;"
               "padding:4px 7px;border-radius:7px;"
               "border:1px solid rgba(148,163,184,0.2);"
               "background:rgba(7,17,25,0.56);"
               "font-size:12px;line-height:1.2;font-weight:500;"
               "color:#e5e7eb;pointer-events:auto;outline:none;"))
    (set-panel-focus-state! panel false)
    (set! (.-textContent label-node) indicator-label)
    (set! (.-cssText (.-style label-node)) "color:#d1d5db;white-space:nowrap;")
    (set! (.-textContent value-node) "--")
    (set! (.-cssText (.-style value-node))
          (str "color:" neutral-value-color ";font-weight:600;white-space:nowrap;"))
    (set! (.-cssText (.-style controls))
          "display:none;align-items:center;gap:6px;margin-left:4px;")
    (.appendChild controls settings-button)
    (.appendChild controls remove-button)
    (.appendChild panel label-node)
    (.appendChild panel value-node)
    (.appendChild panel controls)
    (.appendChild root panel)
    (.addEventListener panel "mouseenter"
                       (fn [_] (set-controls-visible! controls true)))
    (.addEventListener panel "mouseleave"
                       (fn [_] (set-controls-visible! controls false)))
    (.addEventListener panel "focusin"
                       (fn [_]
                         (set-controls-visible! controls true)
                         (set-panel-focus-state! panel true)))
    (.addEventListener panel "focusout"
                       (fn [event]
                         (let [next-focused (.-relatedTarget event)]
                           (when (or (nil? next-focused)
                                     (not (.contains panel next-focused)))
                             (set-controls-visible! controls false)
                             (set-panel-focus-state! panel false)))))
    {:root root
     :value-node value-node
     :controls controls}))

(defn- ensure-overlay-root!
  [chart-obj container document]
  (ensure-relative-container! container)
  (let [{:keys [root]} (overlay-state chart-obj)
        mounted-root? (and root (identical? (.-parentNode root) container))
        fresh-root (when-not mounted-root?
                     (create-overlay-root! chart-obj document))
        next-root (if mounted-root?
                    {:root root
                     :value-node (:value-node (overlay-state chart-obj))
                     :controls (:controls (overlay-state chart-obj))}
                    fresh-root)]
    (when (and root (not mounted-root?))
      (when-let [parent (.-parentNode root)]
        (.removeChild parent root)))
    (when-let [root-node (:root next-root)]
      (when (not (identical? (.-parentNode root-node) container))
        (.appendChild container root-node)))
    next-root))

(defn- set-overlay-position!
  [root chart volume-pane-index]
  (let [pane-count (non-negative-number
                    (or (invoke-method chart "panesCount")
                        (some-> (invoke-method chart "panes") .-length))
                    0)
        safe-pane-index (-> volume-pane-index
                            (non-negative-number 0)
                            (min (max 0 (dec pane-count))))
        pane-top (loop [idx 0
                        y 0]
                   (if (>= idx safe-pane-index)
                     y
                     (let [pane-size (invoke-method chart "paneSize" idx)
                           pane-height (some-> pane-size (aget "height"))]
                       (recur (inc idx)
                              (+ y (non-negative-number pane-height 0))))))
        top (+ pane-top 8)]
    (set! (.-top (.-style root)) (str top "px"))))

(defn- render-entry!
  [value-node candle]
  (let [volume (when candle (:volume candle))]
    (set! (.-textContent value-node) (format-volume-compact volume))
    (set! (.-color (.-style value-node))
          (if candle
            (volume-value-color candle)
            neutral-value-color))))

(defn- subscription-state
  [chart-obj]
  (:subscription (overlay-state chart-obj)))

(defn- teardown-subscription!
  [subscription]
  (when subscription
    (let [{:keys [chart time-scale crosshair-handler pane-handler]} subscription]
      (when crosshair-handler
        (invoke-method chart "unsubscribeCrosshairMove" crosshair-handler))
      (when pane-handler
        (invoke-method time-scale "unsubscribeSizeChange" pane-handler)))))

(defn- subscribe-overlay!
  [chart-obj chart]
  (let [time-scale (invoke-method chart "timeScale")
        crosshair-handler
        (fn [param]
          (let [{:keys [lookup latest value-node]} (overlay-state chart-obj)
                lookup-key (when (and param (some? (.-time param)))
                             (normalize-time-key (.-time param)))
                candle (or (when lookup-key (get lookup lookup-key))
                           latest)]
            (when value-node
              (render-entry! value-node candle))))
        pane-handler
        (fn [_]
          (let [{:keys [root chart volume-pane-index]} (overlay-state chart-obj)]
            (when (and root chart)
              (set-overlay-position! root chart volume-pane-index))))]
    (invoke-method chart "subscribeCrosshairMove" crosshair-handler)
    (invoke-method time-scale "subscribeSizeChange" pane-handler)
    {:chart chart
     :time-scale time-scale
     :crosshair-handler crosshair-handler
     :pane-handler pane-handler}))

(defn clear-volume-indicator-overlay!
  [chart-obj]
  (when chart-obj
    (teardown-subscription! (subscription-state chart-obj))
    (when-let [root (:root (overlay-state chart-obj))]
      (when-let [parent (.-parentNode root)]
        (.removeChild parent root)))
    (.delete volume-indicator-overlay-sidecar chart-obj)))

(defn sync-volume-indicator-overlay!
  ([chart-obj container candles]
   (sync-volume-indicator-overlay! chart-obj container candles {}))
  ([chart-obj container candles {:keys [document on-remove]
                                 :or {on-remove (fn [] nil)}}]
   (if-not (and chart-obj container)
     (clear-volume-indicator-overlay! chart-obj)
     (let [chart (.-chart ^js chart-obj)
           volume-series (.-volumeSeries ^js chart-obj)
           volume-pane-index (.-volumePaneIndex ^js chart-obj)
           document* (resolve-document document)]
       (if (and chart volume-series (finite-number? volume-pane-index) document*)
         (let [state (overlay-state chart-obj)
               {:keys [candles lookup latest]} (reconcile-candle-index-state state
                                                                             (if (sequential? candles)
                                                                               candles
                                                                               []))
               {:keys [root value-node controls]} (ensure-overlay-root! chart-obj container document*)
               current-subscription (:subscription state)
               needs-resubscribe?
               (or (nil? current-subscription)
                   (not (identical? chart (:chart current-subscription))))
               next-subscription (if needs-resubscribe?
                                   (do
                                     (teardown-subscription! current-subscription)
                                     (subscribe-overlay! chart-obj chart))
                                   current-subscription)]
           (set-overlay-state!
            chart-obj
            (assoc state
                   :root root
                   :value-node value-node
                   :controls controls
                   :chart chart
                   :volume-pane-index volume-pane-index
                   :candles candles
                   :lookup lookup
                   :latest latest
                   :on-remove on-remove
                   :subscription next-subscription))
           (set-overlay-position! root chart volume-pane-index)
           (render-entry! value-node latest))
         (clear-volume-indicator-overlay! chart-obj))))))
