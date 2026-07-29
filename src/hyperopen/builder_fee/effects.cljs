(ns hyperopen.builder-fee.effects
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.api.default :as api]
            [hyperopen.api.trading :as trading]
            [hyperopen.builder-fee.approval-state :as approval-state]
            [hyperopen.builder-fee.policy :as policy]
            [hyperopen.config :as app-config]
            [hyperopen.service.tenant-config :as tenant-config]))

(defn- same-identity?
  [left right]
  (= (select-keys left [:owner-address :builder-address :network])
     (select-keys right [:owner-address :builder-address :network])))

(defn- configured-builder-fee
  [state]
  (let [builder-fee (tenant-config/active-builder-fee-config state)]
    (when (= :configured (:status builder-fee))
      builder-fee)))

(defn- approval-identity
  [state]
  (let [owner-address (account-context/owner-address state)
        builder-address (:builder-address (configured-builder-fee state))
        network (get-in app-config/config [:hyperliquid :network])]
    (when (and (string? owner-address)
               (string? builder-address)
               (contains? #{:mainnet :testnet} network))
      {:owner-address owner-address
       :builder-address builder-address
       :network network})))

(defn- current-approval-request?
  [state identity request-id]
  (let [pending (get-in state [:builder-fee :approval])]
    (and (= :loading (:status pending))
         (= request-id (:request-id pending))
         (same-identity? pending identity)
         (same-identity? pending (approval-identity state)))))

(defn- apply-refresh-response!
  [store identity request-id max-builder-fee]
  (let [result (atom nil)]
    (swap! store
           (fn [state]
             (let [pending (get-in state [:builder-fee :approval])]
               (if (current-approval-request? state identity request-id)
                 (let [next-state (approval-state/apply-refresh-response
                                   pending identity request-id max-builder-fee)]
                   (reset! result next-state)
                   (assoc-in state [:builder-fee :approval] next-state))
                 (do
                   (reset! result pending)
                   state)))))
    @result))

(defn- apply-refresh-error!
  [store identity request-id error]
  (swap! store
         (fn [state]
           (let [pending (get-in state [:builder-fee :approval])]
             (if (current-approval-request? state identity request-id)
               (assoc-in state [:builder-fee :approval]
                         (approval-state/apply-refresh-error pending identity request-id error))
               state)))))

(defn- begin-refresh!
  [store identity]
  (let [previous-request-id (get-in @store [:builder-fee :approval :request-id])
        request-id (max (js/Date.now)
                        (if (number? previous-request-id)
                          (inc previous-request-id)
                          0))]
    (swap! store assoc-in [:builder-fee :approval]
           (approval-state/begin-refresh identity request-id))
    request-id))

(defn- fee-approved?
  [state identity approval]
  (let [configured (configured-builder-fee state)]
    (policy/approved? approval
                      (:owner-address identity)
                      (:builder-address identity)
                      (:network identity)
                      (:fee-tenths-bp configured))))

(defn- set-builder-fee-review-error!
  [store error]
  (swap! store assoc-in [:header-ui :builder-fee-review]
         {:status :error :message (str error)}))

(defn refresh-builder-fee-approval!
  ([_ store]
   (refresh-builder-fee-approval!
    nil store {:request-max-builder-fee! api/request-max-builder-fee!}))
  ([_ store {:keys [request-max-builder-fee!]}]
   (if-let [identity (approval-identity @store)]
     (let [approval (get-in @store [:builder-fee :approval])]
       (if (and (contains? #{:loading :ready} (:status approval))
                (same-identity? identity approval))
         (js/Promise.resolve approval)
         (let [request-id (begin-refresh! store identity)]
           (-> (request-max-builder-fee! (:owner-address identity)
                                         (:builder-address identity)
                                         {:priority :high})
               (.then (fn [max-builder-fee]
                        (apply-refresh-response! store identity request-id max-builder-fee)))
               (.catch (fn [error]
                         (apply-refresh-error! store identity request-id error)
                         (js/Promise.reject error)))))))
     (do
       (swap! store assoc-in [:builder-fee :approval] {:status :unapproved})
       (js/Promise.resolve {:status :unapproved})))))

(defn approve-builder-fee!
  [_ store owner-address]
  (let [owner-address* (account-context/normalize-address owner-address)]
    (if-let [identity (approval-identity @store)]
      (if (= owner-address* (:owner-address identity))
      (let [request-id (begin-refresh! store identity)]
        (-> (trading/approve-builder-fee! store owner-address*)
            (.then (fn [_]
                     (api/request-max-builder-fee! (:owner-address identity)
                                                   (:builder-address identity)
                                                   {:priority :high})))
            (.then (fn [max-builder-fee]
                     (let [current-request? (current-approval-request? @store identity request-id)
                           approval (apply-refresh-response! store identity request-id max-builder-fee)]
                       (when current-request?
                         (swap! store assoc-in [:header-ui :builder-fee-review]
                                (if (fee-approved? @store identity approval)
                                  {:status :success}
                                  {:status :error
                                   :message "Builder fee approval was not confirmed by Hyperliquid."}))))))
            (.catch (fn [error]
                      (let [current-request? (current-approval-request? @store identity request-id)]
                        (apply-refresh-error! store identity request-id error)
                        (when current-request?
                          (set-builder-fee-review-error! store error)))))))
        (let [error (js/Error. "Builder fee approval owner changed.")]
          (set-builder-fee-review-error! store error)
          (js/Promise.reject error)))
      (let [error (js/Error. "Builder fee is not configured for this owner.")]
        (set-builder-fee-review-error! store error)
        (js/Promise.reject error)))))
