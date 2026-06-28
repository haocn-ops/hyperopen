(ns hyperopen.runtime.effect-adapters.portfolio-optimizer.execution
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.portfolio.optimizer.application.execution :as execution]
            [hyperopen.portfolio.optimizer.application.execution-workflow :as execution-workflow]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn- mark-row-failed
  [row err]
  (assoc row
         :status :failed
         :error {:message (execution-workflow/error-message err)}))

(defn- submit-action!
  [submit-order! store target action]
  (apply submit-order!
         (cond-> [store (:owner-address target) action]
           (seq (:options target)) (conj (:options target)))))

(defn- submit-actions!
  [submit-order! store target actions]
  (reduce
   (fn [promise action]
     (.then promise
            (fn [responses]
              (-> (submit-action! submit-order! store target action)
                  (.then (fn [resp]
                           (conj responses resp)))))))
   (js/Promise.resolve [])
   actions))

(defn- failed-pre-action-response
  [responses]
  (some #(when-not (execution/response-ok? %) %) responses))

(def ^:private post-only-reprice-attempts
  ;; A post-only order can be rejected for crossing if the book moved between when its price was
  ;; computed and when it landed. Each retry reprices to the live touch from the rejection's own
  ;; bbo; a couple of retries absorb a fast-moving thin book without looping forever.
  2)

(defn- submit-with-reprice!
  "Submits one order action. If Hyperliquid rejects a post-only order because it would have
  immediately matched, reprices it to rest at the live touch carried in the rejection bbo and
  resubmits (up to `attempts-left` times). Any other outcome (ok, or a non-cross error) resolves
  as-is. This makes a passive order self-correct to the current book instead of hard-failing when
  the book moved since the plan was built."
  [submit-order! store target action attempts-left]
  (-> (submit-action! submit-order! store target action)
      (.then (fn [resp]
               (if (and (pos? attempts-left)
                        (not (execution/response-ok? resp)))
                 (if-let [bbo (execution/post-only-cross-bbo resp)]
                   (if-let [action* (execution/reprice-post-only-action action bbo)]
                     (submit-with-reprice! submit-order! store target action*
                                           (dec attempts-left))
                     (js/Promise.resolve resp))
                   (js/Promise.resolve resp))
                 (js/Promise.resolve resp))))))

(defn- submit-execution-row!
  [submit-order! store target row]
  (if-not (= :ready (:status row))
    (js/Promise.resolve row)
    (do
      ;; Mark this row in-flight so the running view animates it as "sending" before
      ;; the order resolves.
      (swap! store execution-workflow/set-run-attempt-row-status
             (:row-id row) {:status :working})
      (let [request (:request row)
            pre-actions (->> (:pre-actions request)
                             (filter map?)
                             vec)
            action (:action request)
            result-promise
            (if-not (map? action)
              (js/Promise.resolve
               (assoc row
                      :status :failed
                      :error {:message "Execution row is missing an order action."}))
              (let [submit-promise
                    (.then (submit-actions! submit-order! store target pre-actions)
                           (fn [pre-responses]
                             (if-let [failed-pre-action (failed-pre-action-response pre-responses)]
                               (assoc row
                                      :status :failed
                                      :pre-action-responses pre-responses
                                      :error {:message (str "Pre-submit action failed: "
                                                            (pr-str failed-pre-action))})
                               (.then (submit-with-reprice! submit-order! store target action
                                                            post-only-reprice-attempts)
                                      (fn [resp]
                                        (if (execution/response-ok? resp)
                                          ;; Classify the accepted order: a crossing order
                                          ;; fills (:submitted); a passive/post-only one that
                                          ;; does not cross rests open on the book (:resting).
                                          (let [status (execution/settled-row-status resp)
                                                realized (execution/realized-fill row resp)]
                                            (cond-> (assoc row
                                                           :status status
                                                           :response resp)
                                              (some? realized) (assoc :realized realized)))
                                          (assoc row
                                                 :status :failed
                                                 :response resp
                                                 :error {:message (str "Order submit failed: "
                                                                       (pr-str resp))})))))))]
                (.catch submit-promise
                        (fn [err]
                          (mark-row-failed row err)))))]
        ;; Reflect the settled per-row result into the running view as it lands.
        (.then result-promise
               (fn [result-row]
                 (swap! store execution-workflow/set-run-attempt-row-status
                        (:row-id result-row)
                        (select-keys result-row [:status :error]))
                 result-row))))))

(defn- submit-execution-rows!
  [submit-order! store target rows]
  (reduce
   (fn [promise row]
     (.then promise
            (fn [submitted-rows]
              (if (and (= :ready (:status row))
                       (get-in @store contracts/execution-abort-requested-path))
                ;; Pause/abort requested mid-run: skip remaining ready rows without
                ;; sending. In-flight orders already settle; nothing new is released.
                (let [aborted (assoc row :status :skipped :reason :aborted)]
                  (swap! store execution-workflow/set-run-attempt-row-status
                         (:row-id aborted) {:status :skipped :reason :aborted})
                  (js/Promise.resolve (conj submitted-rows aborted)))
                (-> (submit-execution-row! submit-order! store target row)
                    (.then (fn [submitted-row]
                             (conj submitted-rows submitted-row))))))))
   (js/Promise.resolve [])
   rows))

(defn- refresh-after-execution!
  [dispatch! store address ledger]
  ;; A filled (:submitted) order changes positions; a resting one adds an open order. Either
  ;; way, pull fresh user data so the new state surfaces in the account panels.
  (when (and address
             (some #(contains? #{:submitted :resting} (:status %)) (:rows ledger)))
    (dispatch! store nil [[:actions/load-user-data address]
                          [:actions/refresh-order-history]])))

(defn- apply-persistence-result!
  [store result]
  (reset! store (:state result))
  result)

(declare interpret-ledger-persistence-result!)

(defn- fail-ledger-persistence!
  [store ledger err]
  (apply-persistence-result!
   store
   (execution-workflow/fail-ledger-persistence
    {:state @store
     :error err}))
  (js/Promise.resolve ledger))

(defn- advance-command-result
  [result]
  (update result :commands #(vec (rest %))))

(defn- merge-result-context
  [operation result]
  (merge operation
         (select-keys result [:scenario-record :scenario-index])))

(defn- interpret-ledger-persistence-command!
  [env store ledger operation result command]
  (let [{:keys [load-scenario!
                load-scenario-index!
                save-scenario!
                save-scenario-index!]} env]
    (case (:command/type command)
      :optimizer.workflow/load-scenario
      (-> (load-scenario! (:scenario-id command))
          (.then (fn [scenario-record]
                   (let [result* (execution-workflow/continue-ledger-persistence-after-record
                                  {:state @store
                                   :address (:address operation)
                                   :ledger ledger
                                   :scenario-record scenario-record})]
                     (interpret-ledger-persistence-result!
                      env
                      store
                      ledger
                      (merge-result-context operation result*)
                      result*))))
          (.catch (fn [err]
                    (fail-ledger-persistence! store ledger err))))

      :optimizer.workflow/load-scenario-index
      (-> (load-scenario-index! (:address command))
          (.then (fn [loaded-index]
                   (let [result* (execution-workflow/continue-ledger-persistence-after-index
                                  {:state @store
                                   :address (:address operation)
                                   :ledger ledger
                                   :scenario-record (:scenario-record operation)
                                   :loaded-index loaded-index})]
                     (interpret-ledger-persistence-result!
                      env
                      store
                      ledger
                      (merge-result-context operation result*)
                      result*))))
          (.catch (fn [err]
                    (fail-ledger-persistence! store ledger err))))

      :optimizer.workflow/save-scenario
      (-> (save-scenario! (:scenario-id command)
                          (:scenario-record command))
          (.then (fn [_]
                   (interpret-ledger-persistence-result!
                    env
                    store
                    ledger
                    operation
                    (advance-command-result result))))
          (.catch (fn [err]
                    (fail-ledger-persistence! store ledger err))))

      :optimizer.workflow/save-scenario-index
      (let [operation* (assoc operation
                              :scenario-index (:scenario-index command))]
        (-> (save-scenario-index! (:address command)
                                  (:scenario-index command))
            (.then (fn [_]
                     (interpret-ledger-persistence-result!
                      env
                      store
                      ledger
                      operation*
                      (execution-workflow/complete-ledger-persistence
                       {:state @store
                        :scenario-index (:scenario-index operation*)
                        :scenario-record (:scenario-record operation*)}))))
            (.catch (fn [err]
                      (fail-ledger-persistence! store ledger err)))))

      (js/Promise.resolve ledger))))

(defn- interpret-ledger-persistence-result!
  [env store ledger operation result]
  (let [result* (apply-persistence-result! store result)]
    (if-let [command (first (:commands result*))]
      (interpret-ledger-persistence-command! env
                                             store
                                             ledger
                                             operation
                                             result*
                                             command)
      (js/Promise.resolve ledger))))

(defn- persist-execution-ledger!
  [env store address ledger]
  (interpret-ledger-persistence-result!
   env
   store
   ledger
   {:address address}
   (execution-workflow/begin-ledger-persistence
    {:state @store
     :address address
     :ledger ledger})))

(defn- execution-mutation-target
  [state]
  (let [owner-address (or (account-context/owner-address state)
                          (get-in state [:wallet :address]))
        account-address (or (account-context/active-trading-account-address state)
                            owner-address)
        vault-address (account-context/exchange-vault-address state)]
    {:owner-address owner-address
     :account-address account-address
     :options (cond-> {}
                vault-address (assoc :vault-address vault-address))}))

(defn execute-portfolio-optimizer-plan-effect
  [env _ store plan]
  (let [now-ms-fn (:now-ms env)
        submit-order! (:submit-order! env)
        dispatch! (:dispatch! env)
        persistence-env (select-keys env
                                     [:load-scenario!
                                      :load-scenario-index!
                                      :save-scenario!
                                     :save-scenario-index!])
        state @store
        {:keys [owner-address account-address] :as target} (execution-mutation-target state)
        started-at-ms (now-ms-fn)
        attempt (execution/build-execution-attempt
                 {:plan plan
                  :market-by-key (get-in state [:asset-selector :market-by-key])
                  :orderbooks (:orderbooks state)})]
    (if-not owner-address
      (let [completed-at-ms (now-ms-fn)
            rows (mapv #(if (= :ready (:status %))
                          (assoc % :status :failed
                                 :error {:message "Connect your wallet before executing."})
                          %)
                       (:rows attempt))
            ledger (execution-workflow/execution-ledger attempt
                                                        started-at-ms
                                                        completed-at-ms
                                                        rows)]
        (swap! store execution-workflow/apply-execution-ledger ledger)
        (js/Promise.resolve ledger))
      (do
        (swap! store assoc-in
               contracts/execution-path
               (execution-workflow/begin-execution-state attempt started-at-ms))
        (-> (submit-execution-rows! submit-order! store target (:rows attempt))
            (.then (fn [rows]
                     (let [completed-at-ms (now-ms-fn)
                           ledger (execution-workflow/execution-ledger
                                   attempt
                                   started-at-ms
                                   completed-at-ms
                                   rows)]
                       (swap! store execution-workflow/apply-execution-ledger ledger)
                       (refresh-after-execution! dispatch! store account-address ledger)
                       (persist-execution-ledger! persistence-env
                                                  store
                                                  account-address
                                                  ledger)))))))))
