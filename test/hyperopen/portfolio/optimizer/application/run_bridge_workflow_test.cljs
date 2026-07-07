(ns hyperopen.portfolio.optimizer.application.run-bridge-workflow-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.progress :as progress]
            [hyperopen.portfolio.optimizer.application.run-bridge-workflow :as workflow]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]))

(deftest start-run-plans-worker-commands-and-running-state-test
  (let [state {:portfolio {:optimizer {:run-state {:status :idle}}}}
        result (workflow/start-run {:state state
                                    :last-run-request nil
                                    :request {:scenario-id "scenario-1"}
                                    :request-signature {:seed 1}
                                    :run-id "run-1"
                                    :computed-at-ms 100})]
    (is (= {:status :running
            :run-id "run-1"
            :scenario-id "scenario-1"
            :request-signature {:seed 1}
            :started-at-ms 100
            :error nil}
           (get-in result [:state :portfolio :optimizer :run-state])))
    (is (= {:request-signature {:seed 1}
            :run-id "run-1"}
           (:last-run-request result)))
    (is (= [:optimizer.workflow/install-worker-handler
            :optimizer.workflow/post-worker-run]
           (mapv :command/type (:commands result))))))

(deftest start-run-allows-retry-after-failed-run-with-identical-signature-test
  (let [state {:portfolio {:optimizer {:run-state {:status :failed
                                                   :run-id "run-1"
                                                   :request-signature {:seed 1}
                                                   :error {:code :boom}}}}}
        result (workflow/start-run {:state state
                                    :last-run-request {:request-signature {:seed 1}
                                                       :run-id "run-1"}
                                    :request {:scenario-id "scenario-1"}
                                    :request-signature {:seed 1}
                                    :run-id "run-2"
                                    :computed-at-ms 200})]
    (is (= "run-2" (:run-id result)))
    (is (= :running
           (get-in result [:state :portfolio :optimizer :run-state :status])))
    (is (= :optimizer.workflow/post-worker-run
           (get-in result [:commands 1 :command/type])))))

(deftest start-run-dedupes-identical-in-flight-signature-test
  (let [state {:portfolio {:optimizer {:run-state {:status :running
                                                   :run-id "run-1"
                                                   :request-signature {:seed 1}}}}}
        result (workflow/start-run {:state state
                                    :last-run-request {:request-signature {:seed 1}
                                                       :run-id "run-1"}
                                    :request {:scenario-id "scenario-1"}
                                    :request-signature {:seed 1}
                                    :computed-at-ms 200})]
    (is (nil? (:run-id result)))
    (is (= state (:state result)))
    (is (empty? (:commands result)))))

(deftest handle-worker-message-ignores-stale-progress-and-error-after-scenario-switch-test
  (let [state {:portfolio {:optimizer {:active-scenario {:loaded-id "scenario-2"}
                                       :optimization-progress {:run-id "run-1"
                                                               :status :running
                                                               :steps []}
                                       :run-state {:status :running
                                                   :run-id "run-1"
                                                   :scenario-id "scenario-1"
                                                   :request-signature {:seed 1}}
                                       :last-successful-run {:result {:old? true}}}}}]
    (is (= state
           (:state (workflow/handle-worker-message
                    {:state state
                     :message {:id "run-1"
                               :type "optimizer-progress"
                               :payload {:percent 50}}
                     :computed-at-ms 300}))))
    (is (= state
           (:state (workflow/handle-worker-message
                    {:state state
                     :message {:id "run-1"
                               :type "optimizer-error"
                               :payload {:code :boom}}
                     :computed-at-ms 300}))))))

(deftest handle-worker-message-applies-current-solved-result-test
  (let [state {:portfolio {:optimizer {:draft {:metadata {:dirty? true}}
                                       :optimization-progress
                                       (progress/begin-progress
                                        {:run-id "run-1"
                                         :scenario-id "scenario-1"
                                         :request {:scenario-id "scenario-1"}
                                         :started-at-ms 100})
                                       :run-state {:status :running
                                                   :run-id "run-1"
                                                   :scenario-id "scenario-1"
                                                   :request-signature {:seed 1}}}}}
        result (workflow/handle-worker-message
                {:state state
                 :message {:id "run-1"
                           :type "optimizer-result"
                           :payload {:status :solved
                                     :scenario-id "scenario-1"}}
                 :computed-at-ms 400})]
    (is (= :succeeded
           (get-in result [:state :portfolio :optimizer :run-state :status])))
    (is (= {:request-signature {:seed 1}
            :result (fixtures/sample-minimal-solved-result
                     {:scenario-id "scenario-1"})
            :computed-at-ms 400}
           (get-in result [:state :portfolio :optimizer :last-successful-run])))
    (is (false?
         (get-in result [:state :portfolio :optimizer :draft :metadata :dirty?])))))

(defn- solved-run-commands
  [{:keys [path run-scenario-id loaded-id]}]
  (let [state {:router {:path path}
               :portfolio {:optimizer {:active-scenario {:loaded-id loaded-id}
                                        :draft {:metadata {:dirty? true}}
                                        :optimization-progress
                                        (progress/begin-progress
                                         {:run-id "run-1"
                                          :scenario-id run-scenario-id
                                          :request {:scenario-id run-scenario-id}
                                          :started-at-ms 100})
                                        :run-state {:status :running
                                                    :run-id "run-1"
                                                    :scenario-id run-scenario-id
                                                    :request-signature {:seed 1}}}}}]
    (:commands (workflow/handle-worker-message
                {:state state
                 :message {:id "run-1"
                           :type "optimizer-result"
                           :payload (cond-> {:status :solved}
                                      run-scenario-id
                                      (assoc :scenario-id run-scenario-id))}
                 :computed-at-ms 400}))))

(def ^:private refresh-command
  {:command/type
   :optimizer.workflow/refresh-portfolio-optimizer-rebalance-slippage-snapshots})

(deftest handle-worker-message-on-new-route-reveals-results-test
  ;; A successful solve is the outcome the user asked for: a run watched from
  ;; /optimize/new navigates straight to the draft results surface instead of
  ;; reporting "Succeeded" and waiting for another click.
  (is (= [refresh-command
          {:command/type :optimizer.workflow/reveal-results
           :path "/portfolio/optimize/draft"}]
         (solved-run-commands {:path "/portfolio/optimize/new"
                               :run-scenario-id nil}))))

(deftest handle-worker-message-on-own-scenario-route-stays-in-place-test
  ;; A refine/rerun watched from the run's own scenario surface updates in place —
  ;; no navigation, no toast.
  (is (= [refresh-command]
         (solved-run-commands {:path "/portfolio/optimize/scenario-1"
                               :run-scenario-id "scenario-1"})))
  ;; The draft results surface counts as the draft run's own scenario surface.
  (is (= [refresh-command]
         (solved-run-commands {:path "/portfolio/optimize/draft"
                               :run-scenario-id nil}))))

(deftest handle-worker-message-on-new-route-reveals-save-bound-run-on-draft-alias-test
  ;; After "Save scenario" the workspace draft carries the saved scenario id, and
  ;; the draft alias renders the CURRENT workspace run whatever its id is — so a
  ;; save-bound run reveals on /optimize/draft too. Revealing on /scn-1 would
  ;; re-trigger the scenario route load, which replaces the fresh in-memory run
  ;; with the record's saved-run snapshot (nil for a setup-only save), wiping the
  ;; result the user just watched finish.
  (is (= [refresh-command
          {:command/type :optimizer.workflow/reveal-results
           :path "/portfolio/optimize/draft"}]
         (solved-run-commands {:path "/portfolio/optimize/new"
                               :run-scenario-id "scn-1"
                               :loaded-id "scn-1"})))
  ;; Same when the saved id arrived via the per-wallet draft restore in a FRESH
  ;; session (no scenario loaded before the run) — the regression this pins was
  ;; the draft alias masking that run to an idle N/A shell.
  (is (= [refresh-command
          {:command/type :optimizer.workflow/reveal-results
           :path "/portfolio/optimize/draft"}]
         (solved-run-commands {:path "/portfolio/optimize/new"
                               :run-scenario-id "scn-1"
                               :loaded-id nil}))))

(deftest handle-worker-message-unsaved-synthetic-draft-id-still-reveals-draft-alias-test
  ;; An UNSAVED draft may carry a synthetic id (e.g. "draft-current") with no
  ;; loaded scenario: that run keeps revealing on the draft alias.
  (is (= [refresh-command
          {:command/type :optimizer.workflow/reveal-results
           :path "/portfolio/optimize/draft"}]
         (solved-run-commands {:path "/portfolio/optimize/new"
                               :run-scenario-id "draft-current"
                               :loaded-id nil}))))

(deftest handle-worker-message-on-draft-alias-stays-for-save-bound-run-test
  ;; Watching from /optimize/draft while the workspace is save-bound: the draft
  ;; alias renders the current workspace run in place, so no navigation and no
  ;; toast — exactly like a rerun watched from a scenario's own surface.
  (is (= [refresh-command]
         (solved-run-commands {:path "/portfolio/optimize/draft"
                               :run-scenario-id "scn-1"
                               :loaded-id "scn-1"}))))

(deftest handle-worker-message-elsewhere-announces-instead-of-navigating-test
  ;; The user wandered off mid-run: never teleport them; announce completion via
  ;; the global toast instead.
  (is (= [refresh-command
          {:command/type :optimizer.workflow/announce-run-complete}]
         (solved-run-commands {:path "/trade"
                               :run-scenario-id nil})))
  ;; Viewing a DIFFERENT scenario than the one that finished also announces.
  (is (= [refresh-command
          {:command/type :optimizer.workflow/announce-run-complete}]
         (solved-run-commands {:path "/portfolio/optimize/other-scenario"
                               :run-scenario-id "scenario-1"}))))
