(ns hyperopen.portfolio.optimizer.application.view-model.scenario-library
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.portfolio.optimizer.application.scenario-state :as scenario-state]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn- scenario-index
  [state]
  (or (get-in state contracts/scenario-index-path)
      (scenario-state/default-scenario-index)))

(defn- scenario-summaries
  [state]
  (let [{:keys [ordered-ids by-id]} (scenario-index state)]
    (vec (keep #(get by-id %) ordered-ids))))

(defn library-model
  "Projection for the workspace Scenarios menu: the wallet's saved scenarios in
  index order (most recently saved first), with archived rows filtered out.
  Archive is a soft-delete — archived records stay in IndexedDB but leave the
  menu."
  [state]
  {:has-address? (some? (account-context/effective-account-address state))
   :open? (true? (get-in state contracts/ui-scenario-menu-open-path))
   :active-scenario-id (get-in state contracts/active-scenario-loaded-id-path)
   :scenario-summaries (vec (remove #(= :archived (:status %))
                                    (scenario-summaries state)))})
