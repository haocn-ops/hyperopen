(ns hyperopen.portfolio.optimizer.application.black-litterman-editor-model.views
  (:require [hyperopen.portfolio.optimizer.application.black-litterman-editor-model.rules :as rules]))

(defn draft->view
  [kind draft view-id]
  (let [kind* (rules/normalize-view-kind kind)
        return-value (rules/parse-percent-text (:return-text draft))
        confidence-level (rules/normalize-confidence-level (:confidence draft))
        confidence (rules/confidence-weight confidence-level)
        horizon (rules/normalize-horizon (:horizon draft))
        notes (rules/non-blank-text (:notes draft))]
    (case kind*
      :relative
      (let [instrument-id (rules/non-blank-text (:instrument-id draft))
            comparator-id (rules/non-blank-text (:comparator-instrument-id draft))
            direction (rules/normalize-direction (:direction draft))]
        (cond-> {:id view-id
                 :kind :relative
                 :instrument-id instrument-id
                 :comparator-instrument-id comparator-id
                 :direction direction
                 :return return-value
                 :confidence-level confidence-level
                 :confidence confidence
                 :confidence-variance (rules/confidence-variance confidence)
                 :horizon horizon
                 :weights (when (and instrument-id comparator-id)
                            (rules/relative-weights instrument-id comparator-id direction))}
          notes (assoc :notes notes)))

      (let [instrument-id (rules/non-blank-text (:instrument-id draft))]
        (cond-> {:id view-id
                 :kind :absolute
                 :instrument-id instrument-id
                 :return return-value
                 :confidence-level confidence-level
                 :confidence confidence
                 :confidence-variance (rules/confidence-variance confidence)
                 :horizon horizon
                 :weights (when instrument-id {instrument-id 1})}
          notes (assoc :notes notes))))))

(defn reset-draft-after-save
  [draft]
  (assoc draft
         :return-text ""
         :return-text-touched? false
         :notes ""))

(defn view->draft
  [view]
  (let [kind (:kind view)]
    (case kind
      :relative {:instrument-id (rules/view-primary-id view)
                 :comparator-instrument-id (rules/view-comparator-id view)
                 :direction (rules/normalize-direction (:direction view))
                 :return-text (rules/decimal->percent-text (:return view))
                 :return-text-touched? true
                 :confidence (rules/normalize-confidence-level
                              (rules/confidence-level-from-view view))
                 :horizon (rules/normalize-horizon (:horizon view))
                 :notes (or (:notes view) "")}
      {:instrument-id (:instrument-id view)
       :return-text (rules/decimal->percent-text (:return view))
       :return-text-touched? true
       :confidence (rules/normalize-confidence-level
                    (rules/confidence-level-from-view view))
       :horizon (rules/normalize-horizon (:horizon view))
       :notes (or (:notes view) "")})))

(defn default-view
  [universe kind id]
  (let [ids (mapv :instrument-id universe)
        kind* (rules/normalize-keyword-like kind)
        confidence (rules/confidence-weight :medium)]
    (case kind*
      :absolute
      (when-let [instrument-id (first ids)]
        {:id id
         :kind :absolute
         :instrument-id instrument-id
         :return 0.0
         :confidence-level :medium
         :confidence confidence
         :confidence-variance (rules/confidence-variance confidence)
         :horizon :3m
         :weights {instrument-id 1}})

      :relative
      (when (<= 2 (count ids))
        (let [[long-id short-id] ids]
          {:id id
           :kind :relative
           :instrument-id long-id
           :comparator-instrument-id short-id
           :direction :outperform
           :long-instrument-id long-id
           :short-instrument-id short-id
           :return 0.0
           :confidence-level :medium
           :confidence confidence
           :confidence-variance (rules/confidence-variance confidence)
           :horizon :3m
           :weights {long-id 1
                     short-id -1}}))

      nil)))

(defn rebuild-weights
  [view]
  (case (:kind view)
    :absolute
    (if-let [instrument-id (rules/non-blank-text (:instrument-id view))]
      (assoc view :weights {instrument-id 1})
      view)

    :relative
    (let [instrument-id (rules/view-primary-id view)
          comparator-id (rules/view-comparator-id view)
          direction (rules/normalize-direction (rules/view-direction view))]
      (if (and instrument-id comparator-id (not= instrument-id comparator-id))
        (assoc view
               :instrument-id instrument-id
               :comparator-instrument-id comparator-id
               :direction direction
               :weights (rules/relative-weights instrument-id comparator-id direction))
        view))

    view))

(defn editing-view-id
  [editor-state]
  (rules/non-blank-text (:editing-view-id editor-state)))

(defn editor-view-result
  [{:keys [black-litterman? universe views editor-state return-inputs-by-instrument]}]
  (let [kind (rules/selected-kind editor-state)
        editing-view-id* (editing-view-id editor-state)
        editing? (boolean editing-view-id*)
        draft (rules/selected-draft universe
                                    editor-state
                                    kind
                                    return-inputs-by-instrument
                                    editing?)
        errors (rules/validate-draft black-litterman? universe views kind draft editing?)]
    (if (seq errors)
      {:status :invalid
       :kind kind
       :draft draft
       :errors errors}
      (let [view-id (or editing-view-id* (rules/next-view-id views))
            view (draft->view kind draft view-id)
            saved-views (if editing?
                          (mapv (fn [existing]
                                  (if (= view-id (:id existing))
                                    view
                                    existing))
                                views)
                          (conj (vec views) view))]
        {:status :valid
         :kind kind
         :draft draft
         :view view
         :views saved-views
         :editing-view-id editing-view-id*}))))

(defn pending-editor-view-result
  [{:keys [black-litterman? universe editor-state] :as opts}]
  (if (not black-litterman?)
    {:status :none}
    (let [kind (rules/selected-kind editor-state)
          editing? (boolean (editing-view-id editor-state))
          draft (rules/editor-draft universe editor-state kind)]
      (if (rules/pending-draft? draft editing?)
        (editor-view-result opts)
        {:status :none
         :kind kind
         :draft draft}))))
