(ns hyperopen.views.trading-chart.utils.chart-options
  (:require [hyperopen.ui.fonts :as fonts]
            [hyperopen.views.trading-chart.utils.theme-colors :as theme-colors]))

(def default-right-offset-bars 4)

(def chart-visual-profile-local-storage-key "chart-visual-profile")
(def default-chart-visual-profile :subtle-v1)

(def supported-chart-visual-profiles
  #{:legacy :subtle-v1})

(defn- chart-visual-profile-tokens
  "Profile palettes resolved from the active theme's --ho-chart-* tokens.
   :legacy uses hard grid lines, :subtle-v1 the soft alpha grid."
  [profile]
  (case profile
    :legacy {:text-color (theme-colors/token "--ho-chart-text")
             :background-color (theme-colors/token "--ho-chart-bg")
             :grid-line-color (theme-colors/token "--ho-chart-grid")
             :scale-border-color (theme-colors/token "--ho-chart-grid")
             :pane-separator-color (theme-colors/token "--ho-chart-grid")
             :pane-separator-hover-color (theme-colors/token "--ho-chart-grid-strong")}
    {:text-color (theme-colors/token "--ho-chart-text")
     :background-color (theme-colors/token "--ho-chart-bg")
     :grid-line-color (theme-colors/token "--ho-chart-grid-soft")
     :scale-border-color (theme-colors/token "--ho-chart-border-soft")
     :pane-separator-color (theme-colors/token "--ho-chart-separator")
     :pane-separator-hover-color (theme-colors/token "--ho-chart-separator-hover")}))

(defn normalize-chart-visual-profile [profile]
  (let [candidate (cond
                    (keyword? profile) profile
                    (string? profile) (keyword profile)
                    :else nil)]
    (if (contains? supported-chart-visual-profiles candidate)
      candidate
      default-chart-visual-profile)))

(defn- resolve-local-storage-chart-visual-profile []
  (if (exists? js/window)
    (try
      (normalize-chart-visual-profile
        (.getItem ^js (.-localStorage js/window)
                  chart-visual-profile-local-storage-key))
      (catch :default _
        default-chart-visual-profile))
    default-chart-visual-profile))

(defn- effective-chart-visual-profile [profile]
  (if (some? profile)
    (normalize-chart-visual-profile profile)
    (resolve-local-storage-chart-visual-profile)))

(defn resolve-chart-font-family []
  (fonts/resolve-ui-font-family))

(defn- common-chart-options [profile]
  (let [{:keys [text-color
                background-color
                grid-line-color
                scale-border-color
                pane-separator-color
                pane-separator-hover-color]}
        (chart-visual-profile-tokens (effective-chart-visual-profile profile))]
    {:layout {:textColor text-color
              :fontFamily (resolve-chart-font-family)
              :background {:type "solid"
                           :color background-color}
              :panes {:separatorColor pane-separator-color
                      :separatorHoverColor pane-separator-hover-color}}
     :grid {:vertLines {:color grid-line-color}
            :horzLines {:color grid-line-color}}
     :crosshair {:mode 0}
     :rightPriceScale {:borderColor scale-border-color}
     :timeScale {:borderColor scale-border-color
                 :rightOffset default-right-offset-bars}}))

(defn base-chart-options
  ([] (base-chart-options nil))
  ([profile]
   (assoc (common-chart-options profile) :autoSize true)))

(defn fixed-height-chart-options
  ([height] (fixed-height-chart-options height nil))
  ([height profile]
   (assoc (common-chart-options profile) :height height)))
