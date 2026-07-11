(ns hyperopen.views.portfolio.optimize.risk-return-context
  "Secondary vol/return scatter for objectives that are NOT selected from an
  efficient frontier (Equal Risk): the current portfolio, the recommended
  portfolio, and the per-asset standalone points already computed into
  :frontier-overlays :standalone — with an explicit note that expected
  returns are informational and did not affect the weights. Deliberately a
  collapsed disclosure under the primary chart, and deliberately NOT titled
  or drawn as a frontier: no curve was calculated and none is implied."
  (:require [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- finite?*
  [value]
  (and (number? value) (js/isFinite value)))

(defn- scatter-points
  [result]
  (let [standalone (->> (get-in result [:frontier-overlays :standalone])
                        (filter #(and (finite?* (:volatility %))
                                      (finite?* (:expected-return %)))))
        target (when (and (finite?* (:volatility result))
                          (finite?* (:expected-return result)))
                 {:kind :target
                  :label "Recommended"
                  :volatility (:volatility result)
                  :expected-return (:expected-return result)})
        current (when (and (finite?* (:current-volatility result))
                           (finite?* (:current-expected-return result)))
                  {:kind :current
                   :label "Current"
                   :volatility (:current-volatility result)
                   :expected-return (:current-expected-return result)})]
    {:assets (mapv #(assoc % :kind :asset) standalone)
     :target target
     :current current}))

(defn- scatter-scale
  [{:keys [assets target current]}]
  (let [points (concat assets (keep identity [target current]))
        vols (map :volatility points)
        returns (map :expected-return points)
        vol-hi (* 1.1 (reduce max 0.0001 vols))
        return-lo (reduce min 0 returns)
        return-hi (reduce max 0 returns)
        return-pad (max 0.01 (* 0.1 (- return-hi return-lo)))
        return-lo* (- return-lo return-pad)
        return-hi* (+ return-hi return-pad)]
    {:x (fn [vol] (-> (* 100 (/ vol vol-hi)) (max 0) (min 100)))
     :y (fn [ret] (-> (* 100 (/ (- ret return-lo*)
                                (max 1e-9 (- return-hi* return-lo*))))
                      (max 0)
                      (min 100)))
     :vol-hi vol-hi
     :return-lo return-lo*
     :return-hi return-hi*}))

(defn- dot
  [{:keys [x y]} point]
  (let [left (str (x (:volatility point)) "%")
        bottom (str (y (:expected-return point)) "%")]
    (case (:kind point)
      :target
      [:div {:class ["absolute" "h-2.5" "w-2.5" "-translate-x-1/2" "translate-y-1/2"
                     "rounded-full" "border-2" "border-warning" "bg-warning/40"]
             :data-role "portfolio-optimizer-risk-return-target"
             :title (str "Recommended · vol " (opt-format/format-pct (:volatility point))
                         " · return " (opt-format/format-pct (:expected-return point)))
             :style {:left left :bottom bottom}}]

      :current
      [:div {:class ["absolute" "h-2.5" "w-2.5" "-translate-x-1/2" "translate-y-1/2"
                     "rounded-full" "border-2" "border-base-300" "bg-base-100"]
             :data-role "portfolio-optimizer-risk-return-current"
             :title (str "Current · vol " (opt-format/format-pct (:volatility point))
                         " · return " (opt-format/format-pct (:expected-return point)))
             :style {:left left :bottom bottom}}]

      [:div {:class ["absolute" "h-1.5" "w-1.5" "-translate-x-1/2" "translate-y-1/2"
                     "rounded-full" "bg-base-300"]
             :data-role "portfolio-optimizer-risk-return-asset"
             :title (str (:label point) " · vol "
                         (opt-format/format-pct (:volatility point))
                         " · return " (opt-format/format-pct (:expected-return point)))
             :style {:left left :bottom bottom}}])))

(defn- sharpe-line
  [result]
  (let [current (get-in result [:current-performance :in-sample-sharpe])
        target (get-in result [:performance :in-sample-sharpe])]
    (when (or (finite?* current) (finite?* target))
      [:p {:class ["mt-2" "font-mono" "text-[0.6875rem]" "text-trading-muted"]
           :data-role "portfolio-optimizer-risk-return-sharpe"}
       (str "Sharpe · current " (opt-format/format-decimal current)
            " → recommended " (opt-format/format-decimal target))])))

(defn risk-return-context
  "Collapsed by default; renders nil when neither portfolio point exists
  (e.g. return metrics omitted because the return model was invalid)."
  [result]
  (let [{:keys [target current] :as points} (scatter-points result)]
    (when (or target current)
      (let [scale (scatter-scale points)]
        [:details {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95"]
                   :data-role "portfolio-optimizer-risk-return-context"}
         [:summary {:class ["cursor-pointer" "select-none" "px-4" "py-3" "font-mono"
                            "text-[0.62rem]" "uppercase" "tracking-[0.08em]"
                            "text-trading-muted/70" "focus:outline-none"
                            "focus:text-warning"]}
          "Risk / Return Context"]
         [:div {:class ["px-4" "pb-4"]}
          [:p {:class ["text-[0.6875rem]" "leading-[1.4]" "text-trading-muted"]
               :data-role "portfolio-optimizer-risk-return-note"}
           "Expected returns are shown for context and did not affect the Equal Risk allocation. No efficient frontier was calculated."]
          [:div {:class ["relative" "mt-3" "h-56" "border-b" "border-l"
                         "border-base-300" "bg-base-200/20"]}
           (into [:div {:class ["absolute" "inset-0"]}]
                 (concat (map (partial dot scale) (:assets points))
                         (keep #(when % (dot scale %)) [current target])))]
          [:div {:class ["mt-1" "flex" "justify-between" "font-mono"
                         "text-[0.5625rem]" "text-trading-muted/70"]}
           [:span "Volatility 0%"]
           [:span (str "→ " (opt-format/format-pct (:vol-hi scale)))]]
          [:p {:class ["mt-1" "text-[0.625rem]" "text-trading-muted/70"]}
           (str "Return axis " (opt-format/format-pct (:return-lo scale))
                " → " (opt-format/format-pct (:return-hi scale))
                " · ◉ Recommended · ○ Current · • assets standalone")]
          (sharpe-line result)]]))))
