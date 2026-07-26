(ns hyperopen.portfolio.optimizer.black-litterman-actions.editor
  (:require [hyperopen.portfolio.optimizer.black-litterman-actions.common :as common]
            [hyperopen.portfolio.optimizer.black-litterman-actions.editor-model :as editor-model]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn set-portfolio-optimizer-black-litterman-editor-type
  [_state view-kind]
  (let [kind (common/normalize-keyword-like view-kind)]
    (if (contains? common/view-kinds kind)
      (common/save-ui-path-values [[(conj common/editor-path :selected-kind) kind]
                                   [(conj common/editor-path :errors) {}]])
      [])))

(defn set-portfolio-optimizer-black-litterman-editor-field
  [state field value]
  (let [kind (common/selected-kind state)
        field* (common/normalize-keyword-like field)]
    (common/save-ui-path-values
     (cond-> [[(common/editor-draft-path kind field*)
               (common/normalized-draft-field field* value)]
              [(conj common/editor-path :errors field*) nil]]
       (= :return-text field*)
       (conj [(common/editor-draft-path kind :return-text-touched?) true])))))

(defn save-portfolio-optimizer-black-litterman-editor-view
  [state]
  (let [{:keys [status errors] :as result}
        (editor-model/editor-view-result state)]
    (if (= :invalid status)
      (common/save-ui-path-values
       (editor-model/error-path-values errors))
      (common/save-draft-path-values
       (editor-model/materialized-view-path-values result)))))

(defn edit-portfolio-optimizer-black-litterman-view
  [state view-id]
  (let [view-id* (common/non-blank-text view-id)
        view (common/view-by-id (common/black-litterman-views state) view-id*)]
    (if view
      (let [kind (or (:kind view) :absolute)]
        (common/save-ui-path-values [[(conj common/editor-path :selected-kind) kind]
                                     [(conj common/editor-path :drafts kind)
                                      (editor-model/view->draft view)]
                                     [(conj common/editor-path :editing-view-id) view-id*]
                                     [(conj common/editor-path :errors) {}]]))
      [])))

(defn cancel-portfolio-optimizer-black-litterman-edit
  [state]
  (let [kind (common/selected-kind state)]
    (common/save-ui-path-values [[(conj common/editor-path :editing-view-id) nil]
                                 [(conj common/editor-path :drafts kind)
                                  (editor-model/reset-draft-after-save
                                   (common/editor-draft state kind))]
                                 [(conj common/editor-path :errors) {}]])))

(defn request-clear-portfolio-optimizer-black-litterman-views
  [_state]
  (common/save-ui-path-values [[(conj common/editor-path :clear-confirmation-open?) true]]))

(defn cancel-clear-portfolio-optimizer-black-litterman-views
  [_state]
  (common/save-ui-path-values [[(conj common/editor-path :clear-confirmation-open?) false]]))

(defn confirm-clear-portfolio-optimizer-black-litterman-views
  [state]
  (if (and (common/black-litterman-return-model? state)
           (seq (common/black-litterman-views state)))
    (common/save-draft-path-values
     [[contracts/draft-return-model-views-path []]
      [(conj common/editor-path :clear-confirmation-open?) false]
      [(conj common/editor-path :editing-view-id) nil]
      [(conj common/editor-path :errors) {}]])
    (common/save-ui-path-values [[(conj common/editor-path :clear-confirmation-open?) false]
                                 [(conj common/editor-path :editing-view-id) nil]])))
