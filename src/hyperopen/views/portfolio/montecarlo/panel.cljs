(ns hyperopen.views.portfolio.montecarlo.panel
  "Top-level layout for the Monte Carlo surface. Assembles the intro, controls,
  equity-paths chart, probability/percentile rail, distribution histograms, and
  method footnote from the `montecarlo-model` view model, and renders explanatory
  states when there is too little realized history to resample.

  The surface is shared by the portfolio and vault detail tabs: per-surface
  copy, data-role prefixes, the root class, and the control/re-run action ids
  arrive in the model under `:chrome`, so one implementation drives both."
  (:require [hyperopen.views.portfolio.montecarlo.chart :as chart]
            [hyperopen.views.portfolio.montecarlo.controls :as controls]
            [hyperopen.views.portfolio.montecarlo.distributions :as distributions]
            [hyperopen.views.portfolio.montecarlo.format :as fmt]
            [hyperopen.views.portfolio.montecarlo.summary :as summary]))

(defn- intro
  [{:keys [method-tag chrome]}]
  [:div {:class ["mc-intro"]}
   [:div
    [:div {:class ["mc-eyebrow"]} "Probabilistic Forecast"]
    [:h2 {:class ["mc-title"]} "Monte Carlo Simulation"]
    [:p {:class ["mc-lede"]} (:lede chrome)]]
   [:span {:class ["mc-method-tag"]}
    [:i {:class ["mc-dot"]}]
    method-tag]])

(defn- chart-card
  [{:keys [result run-key chrome]}]
  (let [span-years (get-in result [:meta :span-years])
        shuffle? (= (get-in result [:meta :method]) :shuffle)
        title (if shuffle?
                (str "Reshuffled  paths · over ~" (fmt/years-label span-years))
                (str "Simulated paths · ~" (fmt/years-label span-years) " forecast"))]
    [:div {:class ["mc-card" "mc-chart-card"]}
     [:div {:class ["mc-chart-head"]}
      [:h3 {:class ["mc-chart-title"]} title]
      [:div {:class ["mc-legend"]}
       [:span [:i {:class ["mc-swatch" "mc-swatch-accent"]}] "Median (P50)"]
       [:span [:i {:class ["mc-swatch" "mc-swatch-band"]}] "P5–P95 band"]
       [:span [:i {:class ["mc-swatch" "mc-swatch-gold"]}] "Goal"]
       [:span [:i {:class ["mc-swatch" "mc-swatch-red"]}] "Bust"]]]
     [:div {:class ["mc-canvas-wrap"]}
      [:div {:class ["mc-canvas-host"]
             :data-role (str (:data-role-prefix chrome) "-equity-canvas")
             :replicant/on-render (chart/spaghetti-on-render
                                   {:result result
                                    :show-paths? true
                                    :path-count 120
                                    :height 420
                                    :update-key run-key})}]]]))

(defn- footnote
  [{:keys [controls result chrome]}]
  (let [{:keys [horizon bust goal]} controls
        span-years (get-in result [:meta :span-years])
        total-years (get-in result [:meta :total-years])
        shuffle? (= (get-in result [:meta :method]) :shuffle)]
    (if shuffle?
      [:div {:class ["mc-foot"]}
       [:b "Method. "]
       (str "Each path reorders " (:history-owner chrome)
            " realized return intervals (spanning ~" (fmt/years-label total-years)
            ") into a fresh random sequence. Sharpe, volatility and CAGR are "
            "annualized by each interval's real elapsed time the same way the Performance Metrics "
            "tab computes them, so they match the tearsheet rather than assuming one point per day. "
            "The same returns reordered give the same product, so total return and CAGR are identical "
            "on every path. ")
       (str "Bust counts orderings whose worst peak-to-trough drawdown breaches " bust "%. ")
       "Sequence-risk view, not a forward prediction."]
      [:div {:class ["mc-foot"]}
       [:b "Method. "]
       (str "Each path resamples " (:history-owner chrome)
            " realized return intervals at random (with replacement) to fill a ~"
            (fmt/years-label span-years) " calendar forecast"
            (when (> (/ horizon 12) (or total-years 0))
              " (clamped to the realized history — a forecast cannot cover more calendar time than was observed)")
            ". Sharpe, volatility and CAGR are annualized by elapsed time, matching the Performance "
            "Metrics tab. ")
       [:b "Bust "]
       (str "counts paths whose worst peak-to-trough drawdown breaches " bust "%; ")
       [:b "goal "]
       (str "counts paths ending at or above " goal
            "%. Past distribution is not a guarantee of future results — this is a range-of-outcomes tool, not a prediction.")])))

(defn- notice
  [{:keys [title body data-role-prefix]}]
  [:div {:class ["mc-card" "mc-notice"]
         :data-role (str data-role-prefix "-notice")}
   [:div {:class ["mc-notice-title"]} title]
   [:p {:class ["mc-notice-body"]} body]])

(defn monte-carlo-card
  [{:keys [status sample-size min-sample chrome] :as model}]
  [:div {:class ["monte-carlo" (:root-class chrome)]
         :data-role (:data-role-prefix chrome)}
   (intro model)
   (controls/controls-bar model)
   (case status
     :ready
     (let [{:keys [result run-key controls live-equity method]} model]
       [:div {:class ["mc-body"]}
        [:div {:class ["mc-grid"]}
         (chart-card {:result result :run-key run-key :chrome chrome})
         [:div {:class ["mc-rail"]}
          (summary/prob-card {:result result :controls controls :method method :chrome chrome})
          (when-not (= method :shuffle)
            (summary/percentile-table {:result result :controls controls
                                       :live-equity live-equity :chrome chrome}))]]
        (distributions/distributions {:result result :controls controls :method method
                                      :run-key run-key :chrome chrome})
        (footnote model)])

     :insufficient-history
     (notice {:title "Not enough history yet"
              :body (str "Monte Carlo needs at least " min-sample
                         " realized return intervals to resample from. This " (:subject chrome) " has "
                         sample-size
                         " so far — check back as more history accrues, or widen the time range.")
              :data-role-prefix (:data-role-prefix chrome)})

     (notice {:title "No realized returns yet"
              :body (str "There is no realized return history for the selected scope and time range, so there is nothing to resample. Once this "
                         (:subject chrome)
                         " has trading history, the forecast will populate here.")
              :data-role-prefix (:data-role-prefix chrome)}))])
