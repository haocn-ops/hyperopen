(ns hyperopen.portfolio.optimizer.black-litterman-actions.views
  (:require [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as editor-model]
            [hyperopen.portfolio.optimizer.black-litterman-actions.common :as common]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn view-primary-instrument-id
  [view]
  (editor-model/view-primary-id view))

(defn view-comparator-instrument-id
  [view]
  (editor-model/view-comparator-id view))

(defn- view-direction
  [view]
  (editor-model/view-direction view))

(defn view-instrument-ids
  [view]
  (case (:kind view)
    :absolute (vec (keep common/non-blank-text [(:instrument-id view)]))
    :relative (vec (keep common/non-blank-text [(view-primary-instrument-id view)
                                                (view-comparator-instrument-id view)]))
    []))

(defn- default-view
  [state kind id]
  (editor-model/default-view (common/draft-universe state) kind id))

(defn- rebuild-weights
  [view]
  (editor-model/rebuild-weights view))

(defn- replace-view
  [views view-id f]
  (let [view-id* (common/non-blank-text view-id)
        replaced? (volatile! false)
        views* (mapv (fn [view]
                       (if (= view-id* (:id view))
                         (do
                           (vreset! replaced? true)
                           (f view))
                         view))
                     views)]
    (when @replaced?
      views*)))

(defn- valid-instrument-update?
  [view parameter-key instrument-id]
  (case parameter-key
    (:instrument-id :long-instrument-id)
    (not= instrument-id (view-comparator-instrument-id view))

    (:comparator-instrument-id :short-instrument-id)
    (not= instrument-id (view-primary-instrument-id view))

    true))

(defn- save-views
  [views]
  (common/save-draft-path-values
   [[contracts/draft-return-model-views-path (vec views)]]))

(defn add-portfolio-optimizer-black-litterman-view
  [state view-kind]
  (let [view-kind* (common/normalize-keyword-like view-kind)
        views (common/black-litterman-views state)]
    (if (and (common/black-litterman-return-model? state)
             (contains? common/view-kinds view-kind*))
      (if-let [view (default-view state view-kind* (common/next-view-id views))]
        (save-views (conj views view))
        [])
      [])))

(defn set-portfolio-optimizer-black-litterman-view-parameter
  [state view-id parameter-key value]
  (let [parameter-key* (common/normalize-keyword-like parameter-key)
        views (common/black-litterman-views state)
        replace-one (fn [f]
                      (if-let [views* (replace-view views view-id f)]
                        (save-views views*)
                        []))]
    (cond
      (not (common/black-litterman-return-model? state))
      []

      (= :kind parameter-key*)
      (let [kind* (common/normalize-keyword-like value)]
        (if (contains? common/view-kinds kind*)
          (replace-one
           (fn [view]
             (let [new-view (or (default-view state kind* (:id view))
                                view)
                   confidence (or (:confidence view) 0.5)]
               (-> new-view
                   (assoc :return (or (:return view) 0.0)
                          :confidence-level (or (:confidence-level view) :medium)
                          :confidence confidence
                          :confidence-variance (common/confidence-variance confidence)
                          :horizon (or (:horizon view) :3m))
                   rebuild-weights))))
          []))

      (contains? common/numeric-parameter-keys parameter-key*)
      (let [value* (common/parse-number-value value)]
        (if (some? value*)
          (replace-one
           (fn [view]
             (cond-> (assoc view parameter-key* value*)
               (= :confidence parameter-key*)
               (assoc :confidence-variance (common/confidence-variance value*)))))
          []))

      (contains? common/instrument-parameter-keys parameter-key*)
      (let [instrument-id* (common/non-blank-text value)]
        (if (and (common/valid-instrument-id? state instrument-id*)
                 (valid-instrument-update?
                  (common/view-by-id views (common/non-blank-text view-id))
                  parameter-key*
                  instrument-id*))
          (replace-one
           (fn [view]
             (rebuild-weights
              (assoc view parameter-key* instrument-id*))))
          []))

      :else [])))

(defn remove-portfolio-optimizer-black-litterman-view
  [state view-id]
  (let [view-id* (common/non-blank-text view-id)
        views (common/black-litterman-views state)
        views* (vec (remove #(= view-id* (:id %)) views))]
    (if (and (common/black-litterman-return-model? state)
             view-id*
             (not= views views*))
      (common/save-draft-path-values
       [[contracts/draft-return-model-views-path views*]
        [(conj common/editor-path :editing-view-id)
         (when (not= view-id*
                     (get-in state (conj common/editor-path :editing-view-id)))
           (get-in state (conj common/editor-path :editing-view-id)))]])
      [])))
