(ns hyperopen.views.portfolio.optimize.setup-header)

(defn- clock-label
  "Local wall-clock label for the autosave note (\"10:42 PM\"). Rendered from the
  epoch with local getters; tests assert presence, not the exact string, so the
  label stays timezone-safe."
  [at-ms]
  (when (number? at-ms)
    (.toLocaleTimeString (js/Date. at-ms)
                         js/undefined
                         #js {:hour "numeric" :minute "2-digit"})))

(def ^:private eyebrow-class
  ["font-mono" "text-[0.6875rem]" "font-semibold" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"])

(defn- route-title
  [route]
  (case (:kind route)
    :optimize-new "Untitled scenario"
    :optimize-scenario (str "Scenario " (:scenario-id route))
    "Optimizer scenario"))

(defn setup-header
  [{:keys [draft route draft-persist]}]
  [:header {:class ["optimizer-setup-header" "border" "border-base-300" "bg-base-100/90" "px-3" "py-2"]
            :data-role "portfolio-optimizer-setup-header"}
   [:div {:class ["flex" "items-center" "justify-between" "gap-4"]}
    [:div {:class ["min-w-0"]}
     [:p {:class eyebrow-class} "Portfolio Optimizer"]
     [:div {:class ["mt-1" "flex" "flex-wrap" "items-center" "gap-2"]}
      [:h1 {:class ["text-lg" "font-medium" "tracking-[-0.01em]" "text-trading-text"]}
       (route-title route)]
      [:span {:class ["text-[0.8125rem]" "text-trading-muted"]}
       "- configure your target portfolio"]
      [:span {:class ["optimizer-status-tag" "border" "border-base-300" "bg-base-200/40" "px-2" "py-0.5"
                      "font-mono" "text-[0.6875rem]" "font-semibold" "uppercase"
                      "tracking-[0.12em]" "text-trading-muted/70"]
              :data-role "portfolio-optimizer-setup-status-tag"}
       (if (= :computed (:status draft)) "computed" "draft")]
      ;; Modeless autosave feedback: the draft persists per wallet on every edit,
      ;; so the header reports it instead of asking for a manual "save draft".
      (when-let [saved-label (clock-label (:at-ms draft-persist))]
        [:span {:class ["font-mono" "text-[0.6875rem]" "uppercase" "tracking-[0.12em]"
                        "text-trading-muted/60"]
                :data-role "portfolio-optimizer-draft-autosave-note"}
         (str "Saved " saved-label)])]
     [:span {:class ["sr-only"]
             :data-role "portfolio-optimizer-draft-state"}
      (if (get-in draft [:metadata :dirty?])
        "Draft has unsaved changes"
        "Draft clean")]]]])
