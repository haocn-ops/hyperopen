(ns hyperopen.portfolio.optimizer.application.view-model.scenario
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.rebalance-preview :as rebalance-preview]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.application.view-model.refinement :as refinement-vm]
            [hyperopen.portfolio.optimizer.application.view-model.workspace :as workspace]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]
            [hyperopen.portfolio.optimizer.ids :as ids]
            [hyperopen.portfolio.optimizer.query-state :as optimizer-query-state]))

(def ^:private normalized-text coercion/non-blank-text)

(defn- active-tab
  [state]
  (optimizer-query-state/normalize-results-tab
   (get-in state contracts/ui-results-tab-path)))

(defn- loaded-scenario-matches-route?
  [state scenario-id]
  (= scenario-id
     (get-in state contracts/active-scenario-loaded-id-path)))

(defn- pending-route-load?
  [state scenario-id]
  (let [load-state (get-in state contracts/scenario-load-state-path)]
    (and (= scenario-id (:scenario-id load-state))
         (= :loading (:status load-state)))))

(defn- retained-unsaved-run?
  [state]
  (let [loaded-id (get-in state contracts/active-scenario-loaded-id-path)
        draft-id (get-in state contracts/draft-id-path)
        draft-status (get-in state contracts/draft-status-path)]
    (and (some? (get-in state contracts/last-successful-run-path))
         (contains? #{nil :draft} draft-status)
         (or (nil? loaded-id)
             (= loaded-id draft-id)))))

(defn- retained-unsaved-route?
  [state scenario-id]
  (and (retained-unsaved-run? state)
       (contains? #{"draft"}
                  scenario-id)))

(defn route-mismatched?
  [state scenario-id]
  (let [loaded-id (get-in state contracts/active-scenario-loaded-id-path)]
    (and (not (retained-unsaved-route? state scenario-id))
         (or (and (some? loaded-id)
                  (not= loaded-id scenario-id))
             (and (nil? loaded-id)
                  (or (pending-route-load? state scenario-id)
                      (retained-unsaved-run? state)))))))

(defn scenario-scoped-state
  [state scenario-id]
  (if (route-mismatched? state scenario-id)
    (-> state
        (assoc-in contracts/draft-path (optimizer-defaults/default-draft))
        (assoc-in contracts/last-successful-run-path nil)
        (assoc-in contracts/tracking-path (optimizer-defaults/default-tracking-state))
        (assoc-in contracts/active-scenario-path
                  {:loaded-id nil
                   :status :loading
                   :read-only? true}))
    state))

(defn scenario-name
  [state scenario-id]
  (or (when (loaded-scenario-matches-route? state scenario-id)
        (or (get-in state contracts/active-scenario-name-path)
            (get-in state contracts/draft-name-path)))
      (when (retained-unsaved-route? state scenario-id)
        (or (get-in state contracts/draft-name-path)
            "Unsaved Optimization"))
      (when (= scenario-id (get-in state contracts/draft-id-path))
        (get-in state contracts/draft-name-path))
      (str "Scenario " scenario-id)))

(defn scenario-detail-model
  [state route]
  (let [scenario-id (:scenario-id route)
        loading? (route-mismatched? state scenario-id)
        state* (scenario-scoped-state state scenario-id)
        selected-tab (active-tab state)
        readiness (setup-readiness/build-readiness state*)
        last-successful-run (rebalance-preview/last-successful-run-with-rebalance-preview
                             (:request readiness)
                             (get-in state* contracts/last-successful-run-path))
        current-result?* (workspace/current-result? state* readiness)
        stale? (workspace/scenario-stale? state* readiness)
        optimization-progress (get-in state* contracts/optimization-progress-path)
        progress-running? (= :running (:status optimization-progress))
        run-state (get-in state* contracts/run-state-path)
        running? (or (= :running (:status run-state))
                     progress-running?)]
    {:state state*
     :route route
     :scenario-id scenario-id
     :loading? loading?
     :selected-tab selected-tab
     :readiness readiness
     :current-result? (boolean current-result?*)
     :stale? (boolean stale?)
     :scenario-name (scenario-name state* scenario-id)
     :draft (workspace/optimizer-draft state*)
     :result (:result last-successful-run)
     :last-successful-run last-successful-run
     :active-scenario (get-in state* contracts/active-scenario-path)
     :run-state run-state
     :optimization-progress optimization-progress
     :progress-running? progress-running?
     :running? running?
     :scenario-save-state (get-in state* contracts/scenario-save-state-path)
     :frontier-overlay-mode (get-in state* contracts/ui-frontier-overlay-mode-path)
     :constrain-frontier? (get-in state* contracts/ui-constrain-frontier-path)
     :refinement (refinement-vm/refinement-model
                  {:state state*
                   :result (:result last-successful-run)
                   :run-state run-state
                   :running? running?
                   :progress optimization-progress})}))

(defn- vault-label
  [instrument]
  (or (normalized-text (:name instrument))
      (normalized-text (:symbol instrument))
      (ids/normalize-vault-address (:vault-address instrument))
      (ids/vault-address-from-value (:coin instrument))
      (ids/vault-address-from-value (:instrument-id instrument))
      (normalized-text (:instrument-id instrument))))

(defn- universe-audit-label
  [instrument]
  (if (ids/vault-instrument? instrument)
    (vault-label instrument)
    (:instrument-id instrument)))

(defn- draft-instrument-label
  [draft instrument-id]
  (or (some (fn [instrument]
              (when (= instrument-id (:instrument-id instrument))
                (or (when (ids/vault-instrument? instrument)
                      (vault-label instrument))
                    (normalized-text (:coin instrument))
                    (normalized-text (:symbol instrument))
                    (normalized-text (:name instrument)))))
            (:universe draft))
      (some-> instrument-id
              (str/split #":")
              last)
      instrument-id))

(defn- audit-view-primary-id
  [view]
  (or (:instrument-id view)
      (:long-instrument-id view)))

(defn- audit-view-comparator-id
  [view]
  (or (:comparator-instrument-id view)
      (:short-instrument-id view)))

(defn- audit-view-row
  [draft view]
  (assoc view
         :primary-label (draft-instrument-label draft
                                                (audit-view-primary-id view))
         :comparator-label (draft-instrument-label draft
                                                   (audit-view-comparator-id view))))

(defn inputs-audit-model
  [state]
  (let [draft (workspace/optimizer-draft state)
        views (vec (or (get-in draft [:return-model :views]) []))]
    {:draft draft
     :scenario-id (or (get-in state contracts/active-scenario-loaded-id-path)
                      (:id draft))
     :universe (vec (or (:universe draft) []))
     :universe-rows (mapv #(assoc % :audit-label (universe-audit-label %))
                          (or (:universe draft) []))
     :objective-kind (get-in draft [:objective :kind])
     :return-model-kind (get-in draft [:return-model :kind])
     :risk-model-kind (get-in draft [:risk-model :kind])
     :constraints (:constraints draft)
     :execution-assumptions (:execution-assumptions draft)
     :views views
     :view-rows (mapv (partial audit-view-row draft) views)}))
