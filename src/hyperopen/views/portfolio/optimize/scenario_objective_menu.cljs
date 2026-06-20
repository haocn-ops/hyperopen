(ns hyperopen.views.portfolio.optimize.scenario-objective-menu
  (:require [clojure.string :as str]
            [hyperopen.platform :as platform]
            [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as bl-model]
            [hyperopen.portfolio.optimizer.application.return-inputs :as return-inputs]
            [hyperopen.portfolio.optimizer.contracts :as optimizer-contracts]
            [hyperopen.system :as app-system]
            [hyperopen.views.asset-icon :as asset-icon]
            [hyperopen.views.portfolio.optimize.instrument-display :as instrument-display]
            [hyperopen.views.portfolio.optimize.target-sigma :as target-sigma]
            [nexus.registry :as nxr]))

(defn- target-volatility-menu-sigma
  ;; The menu preset is 12%, but applying the option preserves a user-chosen
  ;; sigma when target-volatility is already the active objective — the label
  ;; reflects the value Apply & re-run would actually use.
  [draft]
  (or (when (= :target-volatility (get-in draft [:objective :kind]))
        (get-in draft [:objective :target-volatility]))
      target-sigma/menu-default-sigma))

(defn- objective-menu-options
  []
  [{:key :minimum-volatility
    :title "Minimum volatility"
    :description "Smallest feasible sigma - defensive baseline. Recommended"}
   {:key :max-sharpe
    :title "Maximum Sharpe"
    :description "Best risk-adjusted return, but sensitive to noisy return estimates"}
   {:key :use-my-views
    :title "Use my views"
    :description "Black-Litterman: combine market reference with beliefs"}
   {:key :target-volatility
    :title "Target volatility"
    :description "Pin σ to a fixed level, max return at that σ"}
   {:key :maximum-return
    :title "Maximum return"
    :description "Aggressive. Drives toward the right of the frontier"}])

(defn current-objective-menu-key
  [draft result]
  (let [objective-kind (or (get-in draft [:objective :kind])
                           (get-in result [:solver :objective-kind]))
        return-model-kind (get-in draft [:return-model :kind])]
    (cond
      (= :black-litterman return-model-kind) :use-my-views
      (= :minimum-variance objective-kind) :minimum-volatility
      (= :target-volatility objective-kind) :target-volatility
      (= :target-return objective-kind) :maximum-return
      :else :max-sharpe)))

(defn objective-label
  ([objective-key]
   (objective-label objective-key nil))
  ([objective-key draft]
   (if (= :target-volatility objective-key)
     (str "Target volatility · "
          (or (target-sigma/sigma-label (target-volatility-menu-sigma draft))
              "12%"))
     (or (:title (some #(when (= objective-key (:key %)) %)
                       (objective-menu-options)))
         "Maximum Sharpe"))))

(defn- rendered-objective-menu-options
  [options current-key pending-key]
  (if (and (= :use-my-views pending-key)
           (not= :use-my-views current-key))
    (filter #(= :use-my-views (:key %)) options)
    options))

(defn objective-menu-open?
  [state]
  (true? (get-in state optimizer-contracts/ui-objective-menu-open-path)))

(defn objective-trigger
  [label open?]
  [:button {:type "button"
            :class ["optimizer-provenance-objective-trigger"
                    "group"
                    "mt-0.5"
                    "inline-flex"
                    "items-center"
                    "gap-1"
                    "border-0"
                    "bg-transparent"
                    "p-0"
                    "font-medium"
                    "text-trading-text"
                    "focus:outline-none"
                    "focus:ring-0"
                    "focus:ring-offset-0"]
            :data-role "portfolio-optimizer-objective-menu-trigger"
            :aria-haspopup "true"
            :aria-expanded (if open? "true" "false")
            :on {:click [[:actions/open-portfolio-optimizer-objective-menu]]}}
   [:span {:class ["optimizer-provenance-objective-label"]} label]
   [:span {:class ["text-[0.6rem]" "text-trading-muted"]} "›"]])

(defn- objective-option-row
  [{:keys [key title description]} {:keys [state draft current-key pending-key]}]
  (let [selected? (= key pending-key)
        current? (= key current-key)
        role (str "portfolio-optimizer-objective-menu-option-" (name key))]
    [:button {:type "button"
              :class ["flex"
                      "w-full"
                      "items-start"
                      "gap-3"
                      "border-0"
                      "bg-transparent"
                      "p-0"
                      "text-left"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"]
              :data-role role
              :data-selected (str selected?)
              :aria-pressed (str selected?)
              :on {:click [[:actions/select-portfolio-optimizer-objective-menu-option key]]}}
     [:span {:class ["optimizer-objective-menu-check"
                     "mt-0.5"
                     "inline-flex"
                     "h-3"
                     "w-3"
                     "shrink-0"
                     "items-center"
                     "justify-center"
                     "border"
                     "font-mono"
                     "text-[0.55rem]"
                     "leading-none"]
             :aria-hidden "true"}
      (when selected? "✓")]
     [:span {:class ["min-w-0"]}
      [:span {:class ["block" "text-[0.8125rem]" "font-semibold" "text-trading-text"]}
       title
       (when (and (= :target-volatility key) (not selected?))
         [:span {:class ["ml-1.5" "font-mono" "text-[0.6875rem]" "font-normal"
                         "text-trading-muted"]}
          (str "· " (target-sigma/sigma-label
                     (target-sigma/menu-pending-sigma state draft)))])]
      [:span {:class ["mt-1" "block" "text-[0.6875rem]" "text-trading-muted"]}
       (str description (when current? ". Current."))]]]))

(defn- objective-menu-option
  [{:keys [key] :as option}
   {:keys [state draft pending-key sigma-bounds] :as ctx}]
  (let [selected? (= key pending-key)
        sigma-editor? (and (= :target-volatility key) selected?)]
    ;; The whole padded card selects (per spec); editor clicks bubbling here
    ;; re-select the already-selected option, which is a harmless no-op.
    [:div {:class ["optimizer-objective-menu-option"
                   "cursor-pointer"
                   "border"
                   "p-3"
                   "transition-colors"]
           :data-selected (str selected?)
           :on {:click [[:actions/select-portfolio-optimizer-objective-menu-option key]]}}
     (objective-option-row option ctx)
     ;; Inline σ editor — expands when Target volatility is the pending
     ;; selection so the level is set before the first run (designer spec).
     (when sigma-editor?
       (target-sigma/menu-sigma-editor state draft sigma-bounds))]))

(defn- absolute-view?
  [view]
  (= :absolute (bl-model/normalize-view-kind (:kind view))))

(defn- active-absolute-view
  [views instrument-id]
  (some (fn [view]
          (when (and (absolute-view? view)
                     (= instrument-id (:instrument-id view)))
            view))
        views))

(defn- default-view-order
  [draft state]
  (let [ui-order (vec (keep identity
                            (get-in state optimizer-contracts/ui-objective-menu-view-order-path)))
        views (vec (get-in draft [:return-model :views]))
        view-order (vec (keep (fn [view]
                                (when (absolute-view? view)
                                  (:instrument-id view)))
                              views))
        universe-order (vec (keep :instrument-id (:universe draft)))]
    (vec (distinct (concat universe-order view-order ui-order)))))

(defn- instrument-label
  [universe instrument-id]
  (or (some (fn [instrument]
              (when (= instrument-id (:instrument-id instrument))
                (or (when (instrument-display/vault-instrument? instrument)
                      (instrument-display/primary-label instrument))
                    (:coin instrument)
                    (:symbol instrument)
                    (:name instrument))))
            universe)
      (some-> instrument-id
              (str/split #":")
              last)
      instrument-id
      "Asset"))

(defn- instrument-by-id
  [universe instrument-id]
  (some (fn [instrument]
          (when (= instrument-id (:instrument-id instrument))
            instrument))
        universe))

(defn- inline-view-icon
  [instrument asset-label instrument-id]
  (let [icon-url (asset-icon/market-icon-url instrument)]
    [:span {:class ["optimizer-objective-view-token"
                    "inline-flex"
                    "h-5"
                    "w-5"
                    "shrink-0"
                    "items-center"
                    "justify-center"
                    "overflow-hidden"
                    "rounded-full"
                    "font-mono"
                    "text-[0.625rem]"
                    "font-semibold"]
            :data-role (str "portfolio-optimizer-objective-menu-view-"
                            instrument-id
                            "-icon")
            :aria-hidden "true"}
     (if (seq icon-url)
       [:img {:class ["block" "h-5" "w-5" "rounded-full" "object-contain"]
              :src icon-url
              :alt ""
              :data-role (str "portfolio-optimizer-objective-menu-view-"
                              instrument-id
                              "-icon-img")}]
       (subs asset-label 0 (min 1 (count asset-label))))]))

(defn- result-return-inputs
  [result]
  (or (:expected-returns-by-instrument result) {}))

(defn- readiness-return-inputs
  [readiness]
  (return-inputs/readiness-inputs-by-instrument readiness))

(defn- objective-menu-return-inputs
  [result readiness]
  (merge (readiness-return-inputs readiness)
         (result-return-inputs result)))

(defn- inline-view-draft
  [draft state return-inputs-by-instrument instrument-id]
  (let [views (vec (get-in draft [:return-model :views]))
        view (active-absolute-view views instrument-id)
        ui-draft (get-in state (conj optimizer-contracts/ui-objective-menu-view-drafts-path
                                     (keyword instrument-id)))]
    (merge
     {:return-text (cond
                     (some? (:return view))
                     (bl-model/decimal->percent-text (:return view))

                     (contains? return-inputs-by-instrument instrument-id)
                     (bl-model/decimal->percent-text
                      (get return-inputs-by-instrument instrument-id))

                     :else
                     "")
      :confidence (bl-model/normalize-confidence-level
                   (or (:confidence-level view)
                       (when view (bl-model/confidence-level-from-view view))))}
     ui-draft)))

(defn- confidence-button
  [instrument-id selected-confidence [confidence label]]
  (let [tooltip-label (name confidence)
        short-label (-> label
                        (subs 0 1)
                        str/upper-case)]
    [:button {:type "button"
              :class ["optimizer-objective-view-confidence-button"
                      "border"
                      "border-base-300"
                      "font-mono"
                      "text-[0.58rem]"
                      "font-semibold"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"]
              :data-role (str "portfolio-optimizer-objective-menu-view-"
                              instrument-id
                              "-confidence-"
                              (name confidence))
              :data-selected (str (= confidence selected-confidence))
              :data-tooltip tooltip-label
              :title tooltip-label
              :aria-label (str "Set " tooltip-label " confidence")
              :aria-pressed (str (= confidence selected-confidence))
              :on {:click [[:actions/set-portfolio-optimizer-objective-menu-view-confidence
                            instrument-id
                            confidence]]}}
     short-label]))

(def ^:private return-step-keys #{"ArrowUp" "ArrowDown"})

(defn- return-step-keydown-handler
  [instrument-id]
  (fn [event]
    (let [key (some-> event .-key)]
      (when (contains? return-step-keys key)
        (when (fn? (.-preventDefault event))
          (.preventDefault event))
        (when (fn? (.-stopPropagation event))
          (.stopPropagation event))
        (when app-system/store
          (nxr/dispatch app-system/store
                        nil
                        [[:actions/step-portfolio-optimizer-objective-menu-view-return
                          instrument-id
                          key]]))))))

(defn- inline-view-row
  [universe instrument-id view-draft]
  (let [asset-label (instrument-label universe instrument-id)
        instrument (instrument-by-id universe instrument-id)
        selected-confidence (bl-model/normalize-confidence-level
                             (:confidence view-draft))]
    [:div {:class ["optimizer-objective-view-row"
                   "grid"
                   "items-center"
                   "gap-2"
                   "border"
                   "border-base-300"
                   "px-3"
                   "py-2"]
           :data-role (str "portfolio-optimizer-objective-menu-view-row-"
                           instrument-id)}
     [:div {:class ["flex" "min-w-0" "items-center" "gap-2"]}
      (inline-view-icon instrument asset-label instrument-id)
      [:span {:class ["truncate" "text-[0.75rem]" "font-semibold" "text-trading-text"]}
       asset-label]]
     [:div {:class ["optimizer-objective-view-return-shell"
                    "flex"
                    "items-center"
                    "gap-2"
                    "font-mono"
                    "text-[0.625rem]"
                    "text-trading-muted"]}
      [:span "return"]
      [:span {:class ["optimizer-objective-view-return-input-shell"
                      "relative"
                      "inline-flex"
                      "items-center"]}
       [:input {:type "text"
                :inputmode "decimal"
                :aria-label (str asset-label " return")
                :class ["optimizer-objective-view-return-input"
                        "border"
                        "border-base-300"
                        "py-1"
                        "pl-2"
                        "pr-9"
                        "text-right"
                        "font-mono"
                        "text-[0.6875rem]"
                        "text-trading-text"
                        "focus:outline-none"
                        "focus:ring-0"
                        "focus:ring-offset-0"]
                :data-role (str "portfolio-optimizer-objective-menu-view-"
                                instrument-id
                                "-return")
                :value (str (or (:return-text view-draft) ""))
                :on {:input [[:actions/set-portfolio-optimizer-objective-menu-view-return
                              instrument-id
                              [:event.target/value]]]
                     :keydown (return-step-keydown-handler instrument-id)}}]
       [:span {:class ["optimizer-objective-view-return-suffix"
                       "pointer-events-none"
                       "absolute"
                       "font-mono"
                       "text-[0.625rem]"
                       "text-trading-muted"]}
        "%"]
       [:span {:class ["optimizer-objective-view-stepper"
                       "absolute"
                       "inline-flex"
                       "flex-col"
                       "overflow-hidden"]
               :aria-hidden "false"}
        [:button {:type "button"
                  :class ["optimizer-objective-view-stepper-button"
                          "border-0"
                          "p-0"
                          "font-mono"
                          "focus:outline-none"
                          "focus:ring-0"
                          "focus:ring-offset-0"]
                  :aria-label (str "Increase " asset-label " return")
                  :data-role (str "portfolio-optimizer-objective-menu-view-"
                                  instrument-id
                                  "-step-up")
                  :on {:click [[:actions/step-portfolio-optimizer-objective-menu-view-return
                                instrument-id
                                :up]]}}
         "▲"]
        [:button {:type "button"
                  :class ["optimizer-objective-view-stepper-button"
                          "border-0"
                          "p-0"
                          "font-mono"
                          "focus:outline-none"
                          "focus:ring-0"
                          "focus:ring-offset-0"]
                  :aria-label (str "Decrease " asset-label " return")
                  :data-role (str "portfolio-optimizer-objective-menu-view-"
                                  instrument-id
                                  "-step-down")
                  :on {:click [[:actions/step-portfolio-optimizer-objective-menu-view-return
                                instrument-id
                                :down]]}}
         "▼"]]]]
     (into
      [:div {:class ["optimizer-objective-view-confidence"
                     "grid"
                     "grid-cols-3"]
             :aria-label (str asset-label " confidence")}]
      (map #(confidence-button instrument-id selected-confidence %)
           bl-model/confidence-options))]))

(defn views-editor-section
  ([draft state result readiness]
   (views-editor-section draft state result readiness {}))
  ([draft state result readiness {:keys [container-role title description extra-class
                                         include-apply? apply-role]
                                  :or {container-role
                                       "portfolio-optimizer-objective-menu-use-my-views-editor"
                                       title "Your return views"
                                       description
                                       "Annualized. Confidence sets how strongly each view pulls the posterior."
                                       apply-role
                                       "portfolio-optimizer-objective-menu-apply"}}]
  (let [universe (vec (:universe draft))
        return-inputs-by-instrument (objective-menu-return-inputs result readiness)
        order (default-view-order draft state)]
    [:section {:class (cond-> ["optimizer-objective-views-section"
                               "flex"
                               "min-h-0"
                               "shrink-0"
                               "flex-col"
                               "border-t"
                               "border-base-300"
                               "px-3"
                               "py-3"]
                        extra-class (conj extra-class))
               :data-role container-role}
     [:div {:class ["mb-3" "flex" "items-start" "justify-between" "gap-3"]}
      [:div
       [:p {:class ["font-mono"
                    "text-[0.58rem]"
                    "font-semibold"
                    "uppercase"
                    "tracking-[0.18em]"
                    "text-warning"]}
        title]
       [:p {:class ["mt-1" "text-[0.6875rem]" "leading-[1.35]" "text-trading-muted"]}
        description]]]
     (into
      [:div {:class ["optimizer-objective-view-rows"
                     "min-h-0"
                     "space-y-1.5"
                     "overflow-x-hidden"
                     "overflow-y-auto"]
             :data-role (str container-role "-rows")}]
      (map (fn [instrument-id]
             (inline-view-row universe
                              instrument-id
                              (inline-view-draft
                               draft
                               state
                               return-inputs-by-instrument
                               instrument-id)))
           order))
     (when include-apply?
       [:button {:type "button"
                 :class ["optimizer-primary-action"
                         "mt-2"
                         "w-full"
                         "border"
                         "border-base-300"
                         "px-3"
                         "py-2"
                         "text-left"
                         "text-[0.6875rem]"
                         "font-semibold"
                         "focus:outline-none"
                         "focus:ring-0"
                         "focus:ring-offset-0"]
                 :data-role apply-role
                 :on {:click [[:actions/apply-portfolio-optimizer-objective-menu-selection-and-run]]}}
        "Apply & re-run"])])))

(defn- objective-menu-mount-focus!
  [render-arg]
  (let [node (or (:replicant/node render-arg)
                 render-arg)]
    (platform/queue-microtask!
     (fn []
       (when (and node
                  (.-isConnected node)
                  (fn? (.-focus node))
                  (not (and (fn? (.-contains node))
                            (.contains node (.-activeElement js/document)))))
         (.focus node))))))

(defn objective-menu
  ([state draft result]
   (objective-menu state draft result nil))
  ([state draft result readiness]
  (let [open? (objective-menu-open? state)
        current-key (current-objective-menu-key draft result)
        pending-key (or (get-in state optimizer-contracts/ui-objective-menu-selection-path)
                        current-key)
        rendered-options (rendered-objective-menu-options
                          (objective-menu-options)
                          current-key
                          pending-key)
        option-ctx {:state state
                    :draft draft
                    :current-key current-key
                    :pending-key pending-key
                    :sigma-bounds (target-sigma/frontier-sigma-bounds result)}
        apply-disabled? (and (= current-key pending-key)
                             (not= :use-my-views pending-key)
                             (not (and (= :target-volatility pending-key)
                                       (target-sigma/menu-sigma-changed? state draft))))]
    (when open?
      [:section {:class ["optimizer-objective-menu"
                         "optimizer-objective-popover"
                         "absolute"
                         "left-0"
                         "top-full"
                         "z-50"
                         "mt-2"
                         "border"
                         "shadow-2xl"
                         "flex"
                         "flex-col"
                         "focus:outline-none"
                         "focus:ring-0"
                         "focus:ring-offset-0"]
                 :data-role "portfolio-optimizer-objective-menu"
                 :role "region"
                 :tab-index -1
                 :replicant/on-render objective-menu-mount-focus!
                 :aria-label "Change objective"
                 :on {:keydown [[:actions/handle-portfolio-optimizer-objective-menu-keydown
                                  [:event/key]]]}}
       [:header {:class ["flex" "shrink-0" "items-start" "justify-between" "gap-4" "border-b" "border-base-300" "px-3" "py-3"]}
        [:div
         [:p {:class ["font-mono" "text-[0.58rem]" "uppercase" "tracking-[0.18em]" "text-trading-muted/70"]}
          "Edit"]
         [:h2 {:class ["mt-1" "text-sm" "font-semibold" "text-trading-text"]}
          "Change objective"]
         [:p {:class ["mt-1.5" "text-[0.7rem]" "text-trading-muted"]}
          "Re-runs the solver with the same universe and constraints"]]
        [:button {:type "button"
                  :class ["border-0"
                          "bg-transparent"
                          "px-1"
                          "py-0"
                          "text-sm"
                          "text-trading-muted"
                          "focus:outline-none"
                          "focus:ring-0"
                          "focus:ring-offset-0"]
                  :aria-label "Close objective menu"
                  :data-role "portfolio-optimizer-objective-menu-close"
                  :on {:click [[:actions/close-portfolio-optimizer-objective-menu]]}}
         "x"]]
       [:div {:class ["optimizer-objective-menu-body"
                      "flex"
                      "flex-col"
                      "min-h-0"
                      "overflow-y-auto"]}
        (into
         [:div {:class ["shrink-0" "space-y-2" "px-3" "py-3"]}]
         (map #(objective-menu-option % option-ctx)
              rendered-options))
        (when (= :use-my-views pending-key)
          (views-editor-section draft state result readiness))]
       [:footer {:class ["flex" "shrink-0" "items-center" "justify-between" "gap-3" "border-t" "border-base-300" "px-3" "py-3"]}
        [:span {:class ["font-mono" "text-[0.62rem]" "text-trading-muted"]}
         "Esc to cancel"]
        [:div {:class ["flex" "items-center" "gap-2"]}
         [:button {:type "button"
                   :class ["border" "border-base-300" "bg-base-200/40" "px-3" "py-1.5" "text-[0.7rem]" "font-semibold" "text-trading-text"]
                   :data-role "portfolio-optimizer-objective-menu-cancel"
                   :on {:click [[:actions/close-portfolio-optimizer-objective-menu]]}}
          "Cancel"]
         [:button {:type "button"
                   :class ["optimizer-primary-action"
                           "border"
                           "border-base-300"
                           "px-3"
                           "py-1.5"
                           "text-[0.7rem]"
                           "font-semibold"
                           "disabled:cursor-not-allowed"
                           "disabled:text-trading-muted"]
                   :data-role "portfolio-optimizer-objective-menu-apply"
                   :disabled apply-disabled?
                   :on (when-not apply-disabled?
                         {:click [[:actions/apply-portfolio-optimizer-objective-menu-selection-and-run]]})}
          "Apply & re-run"]]]]))))
