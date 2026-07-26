(ns hyperopen.portfolio.optimizer.black-litterman-actions.common
  (:require [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as editor-model]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def view-kinds editor-model/view-kinds)

(def max-active-views editor-model/max-active-views)

(def editor-path
  contracts/ui-black-litterman-editor-path)

(def numeric-parameter-keys editor-model/numeric-parameter-keys)

(def instrument-parameter-keys editor-model/instrument-parameter-keys)

(def confidence-weight-by-level editor-model/confidence-weight-by-level)

(def horizons editor-model/horizons)

(def relative-directions editor-model/relative-directions)

(def normalize-keyword-like coercion/normalize-keyword-like)

(def non-blank-text coercion/non-blank-text)

(def finite-number? coercion/finite-number?)

(def parse-number-value editor-model/parse-number-value)

(def parse-percent-text editor-model/parse-percent-text)

(def decimal->percent-text editor-model/decimal->percent-text)

(defn save-draft-path-values
  [path-values]
  [[:effects/save-many
    (conj (vec path-values)
          [contracts/draft-dirty-path true])]])

(defn save-ui-path-values
  [path-values]
  [[:effects/save-many (vec path-values)]])

(defn draft-universe
  [state]
  (vec (or (get-in state contracts/draft-universe-path)
           [])))

(defn black-litterman-return-model?
  [state]
  (= :black-litterman
     (get-in state (conj contracts/draft-return-model-path :kind))))

(defn black-litterman-views
  [state]
  (vec (or (get-in state contracts/draft-return-model-views-path)
           [])))

(defn universe-instrument-ids
  [state]
  (vec (keep :instrument-id (draft-universe state))))

(defn instrument-present?
  [universe instrument-id]
  (boolean
   (some #(= instrument-id (:instrument-id %)) universe)))

(defn valid-instrument-id?
  [state instrument-id]
  (and (non-blank-text instrument-id)
       (instrument-present? (draft-universe state) instrument-id)))

(defn confidence-variance
  [confidence]
  (editor-model/confidence-variance confidence))

(defn next-view-id
  [views]
  (editor-model/next-view-id views))

(defn normalize-confidence-level
  [value]
  (editor-model/normalize-confidence-level value))

(defn confidence-weight
  [value]
  (editor-model/confidence-weight value))

(defn confidence-level-from-view
  [view]
  (editor-model/confidence-level-from-view view))

(defn normalize-horizon
  [value]
  (editor-model/normalize-horizon value))

(defn normalize-direction
  [value]
  (editor-model/normalize-direction value))

(defn relative-weights
  [instrument-id comparator-id direction]
  (editor-model/relative-weights instrument-id comparator-id direction))

(defn selected-kind
  [state]
  (editor-model/selected-kind (get-in state editor-path)))

(defn draft-defaults
  [state kind]
  (editor-model/draft-defaults (draft-universe state) kind))

(defn editor-draft
  [state kind]
  (editor-model/editor-draft
   (draft-universe state)
   (get-in state editor-path)
   kind))

(defn editor-draft-path
  [kind field]
  (conj editor-path :drafts kind field))

(defn trim-notes
  [value]
  (editor-model/trim-notes value))

(defn normalized-draft-field
  [field value]
  (editor-model/normalized-draft-field field value))

(defn view-by-id
  [views view-id]
  (editor-model/view-by-id views view-id))
