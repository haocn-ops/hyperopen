(ns hyperopen.portfolio.optimizer.actions.draft
  (:require [hyperopen.portfolio.optimizer.actions.common :as common]
            [hyperopen.portfolio.optimizer.actions.draft-options :as draft-options]
            [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as bl-model]
            [hyperopen.portfolio.optimizer.application.return-inputs :as return-inputs]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.actions.run :as run-actions]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn- set-draft-model
  [path models value]
  (if-let [model (get models (common/normalize-keyword-like value))]
    (common/save-draft-path-values [[path model]])
    []))

(defn- current-objective-menu-option
  [state]
  (let [objective-kind (get-in state (conj contracts/draft-objective-path :kind))
        return-model-kind (get-in state (conj contracts/draft-return-model-path :kind))]
    (cond
      (= :black-litterman return-model-kind) :use-my-views
      (= :minimum-variance objective-kind) :minimum-volatility
      (= :target-volatility objective-kind) :target-volatility
      (= :target-return objective-kind) :maximum-return
      :else :max-sharpe)))

(defn- objective-menu-model
  [value]
  (get draft-options/objective-menu-options (common/normalize-keyword-like value)))

(defn- black-litterman-view-confidence-level
  [view]
  (bl-model/normalize-confidence-level
   (or (:confidence-level view)
       (bl-model/confidence-level-from-view view))))

(defn- absolute-view?
  [view]
  (= :absolute (common/normalize-keyword-like (:kind view))))

(defn- existing-absolute-view-by-instrument
  [views instrument-id]
  (some (fn [view]
          (when (and (absolute-view? view)
                     (= instrument-id (:instrument-id view)))
            view))
        views))

(defn- objective-menu-inline-order
  [state]
  (let [ui-order (vec (keep common/non-blank-text
                            (get-in state contracts/ui-objective-menu-view-order-path)))
        views (vec (or (get-in state contracts/draft-return-model-views-path) []))
        existing-order (vec (keep (fn [view]
                                    (when (absolute-view? view)
                                      (common/non-blank-text (:instrument-id view))))
                                  views))
        universe-order (vec (keep (comp common/non-blank-text :instrument-id)
                                  (get-in state contracts/draft-universe-path)))]
    (vec (distinct (concat universe-order existing-order ui-order)))))

(defn- objective-menu-inline-draft
  [state views return-inputs-by-instrument instrument-id]
  (let [view (existing-absolute-view-by-instrument views instrument-id)
        ui-draft (get-in state (conj contracts/ui-objective-menu-view-drafts-path
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
      :confidence (black-litterman-view-confidence-level view)}
     ui-draft)))

(defn- result-return-inputs
  [state]
  (or (get-in state (conj contracts/last-successful-run-result-path
                          :expected-returns-by-instrument))
      {}))

(defn- readiness-return-inputs
  [state]
  (return-inputs/readiness-inputs-by-instrument
   (setup-readiness/build-readiness state)))

(defn- objective-menu-return-inputs
  [state]
  (merge (readiness-return-inputs state)
         (result-return-inputs state)))

(defn- next-inline-view-id
  [views]
  (bl-model/next-view-id views))

(defn- inline-draft->absolute-view
  [existing-views id-views instrument-id draft]
  (let [return-value (bl-model/parse-percent-text (:return-text draft))]
    (when (some? return-value)
      (let [existing (existing-absolute-view-by-instrument existing-views instrument-id)
            confidence-level (bl-model/normalize-confidence-level
                              (:confidence draft))
            confidence (bl-model/confidence-weight confidence-level)]
        {:id (or (:id existing)
                 (next-inline-view-id id-views))
         :kind :absolute
         :instrument-id instrument-id
         :return return-value
         :confidence-level confidence-level
         :confidence confidence
         :confidence-variance (bl-model/confidence-variance confidence)
         :horizon (or (:horizon existing) :3m)
         :weights {instrument-id 1}}))))

(defn- objective-menu-inline-views
  [state]
  (let [views (vec (or (get-in state contracts/draft-return-model-views-path) []))
        return-inputs-by-instrument (objective-menu-return-inputs state)
        order (objective-menu-inline-order state)
        edited-instruments (set order)
        preserved-views (vec (remove (fn [view]
                                       (and (absolute-view? view)
                                            (contains? edited-instruments
                                                       (:instrument-id view))))
                                     views))]
    (reduce (fn [acc instrument-id]
              (if-let [view (inline-draft->absolute-view
                             views
                             acc
                             instrument-id
                             (objective-menu-inline-draft
                              state
                              views
                              return-inputs-by-instrument
                              instrument-id))]
                (conj acc view)
                acc))
            preserved-views
            order)))

(defn- objective-menu-model-for-state
  [state value]
  (when-let [model (objective-menu-model value)]
    (if (= :black-litterman (:return-model-kind model))
      (-> model
          (dissoc :return-model-kind)
          (assoc :return-model
                 {:kind :black-litterman
                  :views (objective-menu-inline-views state)}))
      (cond-> model
        (= :black-litterman
           (get-in state (conj contracts/draft-return-model-path :kind)))
        (assoc :return-model {:kind :historical-mean})))))

(defn open-portfolio-optimizer-objective-menu
  [state]
  [[:effects/save-many
    [[contracts/ui-objective-menu-open-path true]
     [contracts/ui-objective-menu-selection-path
      (current-objective-menu-option state)]]]])

(defn close-portfolio-optimizer-objective-menu
  [_state]
  [[:effects/save-many
    [[contracts/ui-objective-menu-open-path false]
     [contracts/ui-objective-menu-selection-path nil]]]])

(defn handle-portfolio-optimizer-objective-menu-keydown
  [state key]
  (if (= "Escape" (some-> key str))
    (close-portfolio-optimizer-objective-menu state)
    []))

(defn select-portfolio-optimizer-objective-menu-option
  [_state option-key]
  (if (objective-menu-model option-key)
    [[:effects/save
      contracts/ui-objective-menu-selection-path
      (common/normalize-keyword-like option-key)]]
    []))

(defn set-portfolio-optimizer-objective-menu-view-return
  [_state instrument-id value]
  (if-let [instrument-id* (common/non-blank-text instrument-id)]
    [[:effects/save
      (conj contracts/ui-objective-menu-view-drafts-path
            (keyword instrument-id*)
            :return-text)
      (str (or value ""))]]
    []))

(def ^:private inline-return-step-decimal
  0.005)

(defn- objective-menu-return-step-direction
  [direction]
  (case (common/normalize-keyword-like direction)
    :up 1
    :arrow-up 1
    :down -1
    :arrow-down -1
    nil))

(defn step-portfolio-optimizer-objective-menu-view-return
  [state instrument-id direction]
  (if-let [instrument-id* (common/non-blank-text instrument-id)]
    (if-let [step-direction (objective-menu-return-step-direction direction)]
      (let [views (vec (or (get-in state contracts/draft-return-model-views-path)
                           []))
            draft (objective-menu-inline-draft
                   state
                   views
                   (objective-menu-return-inputs state)
                   instrument-id*)
            current (or (bl-model/parse-percent-text (:return-text draft))
                        0)
            next-value (+ current (* step-direction inline-return-step-decimal))]
        [[:effects/save
          (conj contracts/ui-objective-menu-view-drafts-path
                (keyword instrument-id*)
                :return-text)
          (bl-model/decimal->percent-text next-value)]])
      [])
    []))

(defn set-portfolio-optimizer-objective-menu-view-confidence
  [_state instrument-id confidence]
  (if-let [instrument-id* (common/non-blank-text instrument-id)]
    [[:effects/save
      (conj contracts/ui-objective-menu-view-drafts-path
            (keyword instrument-id*)
            :confidence)
      (bl-model/normalize-confidence-level confidence)]]
    []))

(defn remove-portfolio-optimizer-objective-menu-view
  [state instrument-id]
  (if-let [instrument-id* (common/non-blank-text instrument-id)]
    (let [order (vec (remove #(= instrument-id* %)
                             (objective-menu-inline-order state)))
          drafts (dissoc (or (get-in state contracts/ui-objective-menu-view-drafts-path)
                             {})
                         (keyword instrument-id*))]
      [[:effects/save-many
        [[contracts/ui-objective-menu-view-order-path order]
         [contracts/ui-objective-menu-view-drafts-path drafts]]]])
    []))

(defn add-portfolio-optimizer-objective-menu-view
  [state]
  (let [order (objective-menu-inline-order state)
        present (set order)
        next-id (some (fn [instrument]
                        (let [instrument-id (:instrument-id instrument)]
                          (when (and (common/non-blank-text instrument-id)
                                     (not (contains? present instrument-id)))
                            instrument-id)))
                      (get-in state contracts/draft-universe-path))]
    (if next-id
      [[:effects/save contracts/ui-objective-menu-view-order-path
        (conj (vec order) next-id)]]
      [])))

(defn- state-after-save-effect
  [state effect]
  (case (first effect)
    :effects/save
    (assoc-in state (second effect) (nth effect 2))

    :effects/save-many
    (reduce (fn [state* [path value]]
              (assoc-in state* path value))
            state
            (second effect))

    state))

(defn- projected-state-after-save-effects
  [state effects]
  (reduce state-after-save-effect state effects))

(defn apply-portfolio-optimizer-objective-menu-selection-and-run
  [state]
  (let [selection (or (get-in state contracts/ui-objective-menu-selection-path)
                      (current-objective-menu-option state))]
    (if-let [{:keys [objective return-model]} (objective-menu-model-for-state state selection)]
      (let [path-values (cond-> [[contracts/draft-objective-path objective]]
                          return-model
                          (conj [contracts/draft-return-model-path return-model])

                          :always
                          (conj [contracts/ui-objective-menu-open-path false]
                                [contracts/ui-objective-menu-selection-path nil]))
            effects (common/save-draft-path-values path-values)
            state* (projected-state-after-save-effects state effects)]
        (into effects
              (run-actions/run-portfolio-optimizer-from-draft state*)))
      [])))

(defn set-portfolio-optimizer-objective-kind
  [_state kind]
  (set-draft-model contracts/draft-objective-path
                   draft-options/objective-models
                   kind))

(defn set-portfolio-optimizer-return-model-kind
  [_state kind]
  (set-draft-model contracts/draft-return-model-path
                   draft-options/return-models
                   kind))

(defn set-portfolio-optimizer-risk-model-kind
  [_state kind]
  (set-draft-model contracts/draft-risk-model-path
                   draft-options/risk-models
                   kind))

(defn apply-portfolio-optimizer-setup-preset
  [_state preset]
  (if-let [{:keys [objective return-model]} (get draft-options/setup-presets
                                                 (common/normalize-keyword-like preset))]
    (common/save-draft-path-values
     [[contracts/draft-objective-path objective]
      [contracts/draft-return-model-path return-model]])
    []))

(defn set-portfolio-optimizer-constraint
  [_state constraint-key value]
  (let [constraint-key* (common/normalize-keyword-like constraint-key)
        clear? (and (nil? value)
                    (contains? draft-options/clearable-numeric-constraint-keys
                               constraint-key*))
        value* (cond
                 clear?
                 nil

                 (contains? draft-options/numeric-constraint-keys constraint-key*)
                 (common/parse-number-value value)

                 (contains? draft-options/boolean-constraint-keys constraint-key*)
                 (common/parse-boolean-value value)

                 :else nil)]
    (if (or clear? (some? value*))
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path constraint-key*) value*]])
      [])))

(defn set-portfolio-optimizer-objective-parameter
  [_state parameter-key value]
  (let [parameter-key* (common/normalize-keyword-like parameter-key)
        value* (when (contains? draft-options/numeric-objective-parameter-keys
                                parameter-key*)
                 (common/parse-number-value value))]
    (if (some? value*)
      (common/save-draft-path-values
       [[(conj contracts/draft-objective-path parameter-key*) value*]])
      [])))

(defn set-portfolio-optimizer-execution-assumption
  [_state assumption-key value]
  (let [assumption-key* (common/normalize-keyword-like assumption-key)
        manual-capital-clear? (and (= :manual-capital-usdc assumption-key*)
                                   (nil? (common/non-blank-text value)))
        value* (cond
                 manual-capital-clear?
                 nil

                 (contains? draft-options/numeric-execution-assumption-keys
                            assumption-key*)
                 (common/parse-number-value value)

                 (contains? draft-options/keyword-execution-assumption-keys
                            assumption-key*)
                 (common/normalize-keyword-like value)

                 :else nil)]
    (if (or (some? value*)
            manual-capital-clear?)
      (common/save-draft-path-values
       [[(conj contracts/draft-execution-assumptions-path assumption-key*) value*]])
      [])))

(defn set-portfolio-optimizer-instrument-filter
  [state filter-key instrument-id enabled?]
  (let [filter-key* (common/normalize-keyword-like filter-key)
        instrument-id* (common/non-blank-text instrument-id)
        enabled?* (common/parse-boolean-value enabled?)]
    (if (and (contains? draft-options/instrument-filter-keys filter-key*)
             instrument-id*
             (some? enabled?*))
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path filter-key*)
         (common/set-membership
          (common/constraint-list state filter-key*)
          instrument-id*
          enabled?*)]])
      [])))

(defn- instrument-cap-map
  ;; The save-many effect contract only accepts keyword paths, so instrument
  ;; keyed maps must be saved whole rather than assoc'ed via a string segment.
  [state map-key instrument-id max-weight]
  (-> (or (get-in state (conj contracts/draft-constraints-path map-key)) {})
      (assoc-in [instrument-id :max-weight] max-weight)))

(defn set-portfolio-optimizer-asset-override
  [state override-key instrument-id value]
  (let [override-key* (common/normalize-keyword-like override-key)
        instrument-id* (common/non-blank-text instrument-id)
        numeric-value (when (contains? draft-options/numeric-asset-override-keys
                                       override-key*)
                        (common/parse-number-value value))
        boolean-value (when (contains? draft-options/boolean-asset-override-keys
                                       override-key*)
                        (common/parse-boolean-value value))]
    (cond
      (and instrument-id*
           (= :max-weight override-key*)
           (some? numeric-value))
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path :asset-overrides)
         (instrument-cap-map state :asset-overrides instrument-id* numeric-value)]])

      (and instrument-id*
           (= :perp-max-weight override-key*)
           (= :perp (common/instrument-market-type state instrument-id*))
           (some? numeric-value))
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path :perp-leverage)
         (instrument-cap-map state :perp-leverage instrument-id* numeric-value)]])

      (and instrument-id*
           (= :held-lock? override-key*)
           (some? boolean-value))
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path :held-locks)
         (common/set-membership
          (common/constraint-list state :held-locks)
          instrument-id*
          boolean-value)]])

      :else [])))
