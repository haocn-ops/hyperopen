(ns hyperopen.wallet.agent-safety
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.api.trading :as trading]
            [hyperopen.order.exchange-errors :as exchange-errors]
            [hyperopen.platform :as platform]
            [hyperopen.wallet.agent-safety-policy :as safety-policy]))

(def ^:private agent-safety-watch-key
  ::agent-safety)

(def ^:private timer-path
  [:timeouts :agent-schedule-cancel-refresh])

(defn- clear-refresh-timeout!
  [runtime clear-timeout-fn]
  (when-let [timer-id (get-in @runtime timer-path)]
    (clear-timeout-fn timer-id)
    (swap! runtime assoc-in timer-path nil)))

(defn stop-agent-safety!
  [{:keys [runtime]
    clear-timeout-fn :clear-timeout-fn
    :or {clear-timeout-fn platform/clear-timeout!
         runtime (atom {})}}]
  (clear-refresh-timeout! runtime clear-timeout-fn)
  true)

(defn- ready-owner-address
  [state]
  (let [wallet-state (:wallet state)
        agent-state (:agent wallet-state)
        owner-address (:address wallet-state)]
    (when (and (:connected? wallet-state)
               (seq owner-address)
               (= :ready (:status agent-state)))
      owner-address)))

(defn- safety-fingerprint
  [state]
  [(boolean (get-in state [:wallet :connected?]))
   (get-in state [:wallet :address])
   (get-in state [:wallet :agent :status])
   (get-in state [:wallet :agent :agent-address])
   (account-context/exchange-vault-address state)
   (safety-policy/mode state)])

(defn- response-text
  [resp]
  (or (:response resp)
      (:error resp)
      (:message resp)))

(defn- handle-schedule-cancel-response!
  [store resp]
  (if-let [volume-gate (exchange-errors/schedule-cancel-volume-gate
                        (response-text resp))]
    (do
      (swap! store assoc-in [:wallet :agent :safety] volume-gate)
      :volume-gated)
    (do
      (when (= "ok" (:status resp))
        (swap! store update-in [:wallet :agent] dissoc :safety))
      :ok)))

(defn- schedule-cancel-options
  [state]
  (if-let [vault-address (account-context/exchange-vault-address state)]
    {:vault-address vault-address}
    {}))

(defn- submit-schedule-cancel!
  [store owner-address cancel-at-ms]
  (let [options (schedule-cancel-options @store)]
    (-> (apply trading/schedule-cancel!
               (cond-> [store owner-address cancel-at-ms]
                 (seq options) (conj options)))
        (.then (partial handle-schedule-cancel-response! store))
        (.catch (fn [_] :error)))))

(defn- refresh-allowed?
  [store disposition policy-fn]
  (and (not= :volume-gated disposition)
       (ready-owner-address @store)
       (:enabled? (policy-fn))))

(defn- schedule-next-refresh!
  [{:keys [now-ms-fn policy-fn runtime set-timeout-fn store] :as deps}]
  (letfn [(refresh! []
            (swap! runtime assoc-in timer-path nil)
            (if-let [owner-address (ready-owner-address @store)]
              (let [{:keys [ahead-ms enabled?]} (policy-fn)]
                (if enabled?
                  (-> (submit-schedule-cancel! store
                                               owner-address
                                               (+ (now-ms-fn) ahead-ms))
                      (.then
                       (fn [disposition]
                         (when (refresh-allowed? store disposition policy-fn)
                           (schedule-next-refresh! deps)))))
                  (submit-schedule-cancel! store owner-address nil)))
              (swap! runtime assoc-in timer-path nil)))]
    (when-let [refresh-ms (:refresh-ms (policy-fn))]
      (let [timer-id (set-timeout-fn refresh! refresh-ms)]
        (swap! runtime assoc-in timer-path timer-id)
        timer-id))))

(defn- ensure-agent-safety!
  [{:keys [clear-timeout-fn now-ms-fn policy-fn runtime set-timeout-fn store]
    :as deps}]
  (clear-refresh-timeout! runtime clear-timeout-fn)
  (when-let [owner-address (ready-owner-address @store)]
    (let [{:keys [ahead-ms enabled?]} (policy-fn)]
      (if enabled?
        (-> (submit-schedule-cancel! store
                                     owner-address
                                     (+ (now-ms-fn) ahead-ms))
            (.then
             (fn [disposition]
               (when (refresh-allowed? store disposition policy-fn)
                 (schedule-next-refresh! deps)))))
        (submit-schedule-cancel! store owner-address nil)))))

(defn install-agent-safety-watch!
  [{:keys [ahead-ms
           clear-timeout-fn
           now-ms-fn
           refresh-ms
           runtime
           set-timeout-fn
           store]
    :or {ahead-ms 60000
         clear-timeout-fn platform/clear-timeout!
         now-ms-fn platform/now-ms
         refresh-ms 30000
         runtime (atom {})
         set-timeout-fn platform/set-timeout!}}]
  (let [policy-fn (fn []
                    (safety-policy/policy
                     @store
                     {:strict-ahead-ms ahead-ms
                      :strict-refresh-ms refresh-ms}))
        deps {:clear-timeout-fn clear-timeout-fn
              :now-ms-fn now-ms-fn
              :policy-fn policy-fn
              :runtime runtime
              :set-timeout-fn set-timeout-fn
              :store store}]
    (ensure-agent-safety! deps)
    (remove-watch store agent-safety-watch-key)
    (add-watch store agent-safety-watch-key
               (fn [_ _ old-state new-state]
                 (when (not= (safety-fingerprint old-state)
                             (safety-fingerprint new-state))
                   (ensure-agent-safety! deps))))))
