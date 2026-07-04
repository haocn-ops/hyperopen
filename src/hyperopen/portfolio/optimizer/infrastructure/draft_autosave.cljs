(ns hyperopen.portfolio.optimizer.infrastructure.draft-autosave
  "Store watchers for the optimizer's opinionated default path.

  Autosave: every draft change persists (debounced) to the per-wallet
  `draft::<address>` IndexedDB record, so edits — including a deliberately cleared
  universe — survive reloads and wallet switches. The write is skipped for
  untouched/default drafts, when the effective address changed between the edit
  and the flush (a wallet switch must never cross-key a draft), and when the draft
  equals the value already persisted (a fresh restore does not re-write itself).

  Holdings arrival: on a cold page-load the /optimize/new route event fires before
  the account feed delivers any holdings, so the route-time preseed no-ops. This
  watcher observes the no-holdings -> holdings transition and re-dispatches the
  restore-or-preseed funnel (IndexedDB still wins over the machine preseed)."
  (:require [nexus.registry :as nxr]
            [hyperopen.account.context :as account-context]
            [hyperopen.platform :as platform]
            [hyperopen.portfolio.optimizer.actions.draft-persistence :as draft-persistence]
            [hyperopen.portfolio.optimizer.application.current-portfolio :as current-portfolio]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]
            [hyperopen.portfolio.optimizer.infrastructure.persistence :as persistence]
            [hyperopen.portfolio.routes :as portfolio-routes]))

(def ^:private autosave-watch-key ::draft-autosave)
(def ^:private preseed-watch-key ::holdings-preseed)
(def ^:private identity-restore-watch-key ::identity-restore)

(def default-debounce-ms
  "One draft edit rarely comes alone (drags, typed digits); a sub-second debounce
  batches a burst into one IndexedDB write without risking a visible loss window."
  800)

(defonce ^:private last-persisted-draft
  (atom nil))

(defn note-persisted!
  "Record `draft` as the value already sitting in IndexedDB so the autosave watcher
  does not immediately re-write it. Called by the flush itself and by the restore
  effect right before it hydrates a loaded record into the store."
  [draft]
  (reset! last-persisted-draft draft))

(defn- complete-draft
  "Persist a spec-complete draft even when the in-store value is a legacy partial
  one (sessions seeded before the holdings seed materialized the full default):
  layer it over a fresh default so the restore-side ::draft validation accepts it."
  [draft]
  (let [default (optimizer-defaults/default-draft)]
    (-> (merge default draft)
        (assoc :metadata (merge (:metadata default) (:metadata draft))))))

(defn install-draft-autosave-watcher!
  [{:keys [store save-draft! now-ms-fn set-timeout-fn clear-timeout-fn debounce-ms]
    :or {save-draft! persistence/save-draft!
         now-ms-fn platform/now-ms
         set-timeout-fn platform/set-timeout!
         clear-timeout-fn platform/clear-timeout!
         debounce-ms default-debounce-ms}}]
  (let [pending (atom nil)
        flush! (fn [address]
                 (let [state @store
                       draft (get-in state contracts/draft-path)]
                   (when (and (seq (or address ""))
                              (= address (account-context/effective-account-address state))
                              (not (optimizer-defaults/untouched-draft? draft))
                              (not= draft @last-persisted-draft))
                     (let [saved-at-ms (now-ms-fn)]
                       (-> (save-draft! address
                                        {:version draft-persistence/draft-record-version
                                         :address address
                                         :draft (complete-draft draft)
                                         :saved-at-ms saved-at-ms})
                           (.then (fn [persisted?]
                                    (when persisted?
                                      (note-persisted! draft)
                                      (swap! store
                                             assoc-in
                                             contracts/draft-persist-path
                                             {:status :saved
                                              :at-ms saved-at-ms}))))
                           (.catch (fn [_err] nil)))))))
        schedule! (fn [address]
                    (when-let [{:keys [timer]} @pending]
                      (clear-timeout-fn timer))
                    (reset! pending
                            {:address address
                             :timer (set-timeout-fn
                                     (fn []
                                       (let [{:keys [address]} @pending]
                                         (reset! pending nil)
                                         (flush! address)))
                                     debounce-ms)}))]
    (remove-watch store autosave-watch-key)
    (add-watch store autosave-watch-key
               (fn [_ _ old-state new-state]
                 (let [old-draft (get-in old-state contracts/draft-path)
                       new-draft (get-in new-state contracts/draft-path)]
                   (when (and (not= old-draft new-draft)
                              (not (optimizer-defaults/untouched-draft? new-draft))
                              (not= new-draft @last-persisted-draft))
                     (when-let [address (account-context/effective-account-address new-state)]
                       (schedule! address))))))
    store))

(defn install-holdings-preseed-watcher!
  [{:keys [store dispatch!]
    :or {dispatch! nxr/dispatch}}]
  (remove-watch store preseed-watch-key)
  (add-watch store preseed-watch-key
             ;; Fire on EACH holdings source's arrival (perp clearinghouse and
             ;; spot balances land independently, spot usually first): a
             ;; single any-source transition would be consumed by the spot
             ;; arrival while the perp book is still in flight and the seed
             ;; would never retry. The untouched-draft gate keeps repeat
             ;; dispatches idempotent.
             (fn [_ _ old-state new-state]
               (when (and (current-portfolio/holdings-source-arrived? old-state new-state)
                          (optimizer-defaults/untouched-draft?
                           (get-in new-state contracts/draft-path))
                          (= :optimize-new
                             (:kind (portfolio-routes/parse-portfolio-route
                                     (get-in new-state [:router :path])))))
                 (dispatch! store
                            nil
                            [[:actions/restore-or-preseed-portfolio-optimizer-draft
                              (get-in new-state [:router :path])]]))))
  store)

(defn install-identity-restore-watcher!
  "Re-dispatch the restore-or-preseed funnel when the EFFECTIVE ACCOUNT IDENTITY
  resolves or changes while the user sits on /optimize/new with an untouched
  draft. The restore effect silently no-ops when the effective address is still
  nil (e.g. a full page reload under spectate mode resolves identity AFTER the
  optimizer route loads), and the holdings watcher only fires on holdings-arrival
  transitions — so without this watcher a reload loses a draft the UI claimed was
  saved, and a late-connecting account never auto-seeds. The untouched-draft gate
  keeps repeat dispatches idempotent, exactly like the holdings watcher."
  [{:keys [store dispatch!]
    :or {dispatch! nxr/dispatch}}]
  (remove-watch store identity-restore-watch-key)
  (add-watch store identity-restore-watch-key
             (fn [_ _ old-state new-state]
               (let [old-address (account-context/effective-account-address old-state)
                     new-address (account-context/effective-account-address new-state)]
                 (when (and (some? new-address)
                            (not= old-address new-address)
                            (optimizer-defaults/untouched-draft?
                             (get-in new-state contracts/draft-path))
                            (= :optimize-new
                               (:kind (portfolio-routes/parse-portfolio-route
                                       (get-in new-state [:router :path])))))
                   (dispatch! store
                              nil
                              [[:actions/restore-or-preseed-portfolio-optimizer-draft
                                (get-in new-state [:router :path])]])))))
  store)

(defn install-optimizer-draft-watchers!
  [deps]
  (install-draft-autosave-watcher! deps)
  (install-holdings-preseed-watcher! deps)
  (install-identity-restore-watcher! deps))
