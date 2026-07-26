(ns hyperopen.funding.application.lifecycle-polling)

(defn- lifecycle-polling-eligible?
  [{:keys [request-ops!
           direction
           wallet-address
           asset-key
           non-blank-text]}]
  (and request-ops!
       (contains? #{:deposit :withdraw} direction)
       (seq (non-blank-text wallet-address))
       (keyword? asset-key)))

(defn- update-active-lifecycle!
  [store normalize-hyperunit-lifecycle should-continue? lifecycle]
  (when (should-continue?)
    (swap! store assoc-in
           [:funding-ui :modal :hyperunit-lifecycle]
           (normalize-hyperunit-lifecycle lifecycle))))

(defn- refresh-active-withdraw-queue!
  [{:keys [direction
           request-queue!
           should-continue?
           fetch-hyperunit-withdrawal-queue!
           store
           base-url
           base-urls
           now-ms!*
           runtime-error-message
           asset-key]}]
  (when (and (= direction :withdraw)
             request-queue!
             (should-continue?))
    (fetch-hyperunit-withdrawal-queue!
     {:store store
      :base-url base-url
      :base-urls base-urls
      :request-hyperunit-withdrawal-queue! request-queue!
      :now-ms-fn now-ms!*
      :runtime-error-message runtime-error-message
      :expected-asset-key asset-key
      :transition-loading? false})))

(defn- schedule-next-poll!
  [should-continue? timeout! delay-ms poll-fn]
  (when (should-continue?)
    (timeout! poll-fn delay-ms)))

(defn- notify-terminal-lifecycle!
  [terminal-callback! lifecycle]
  (when terminal-callback!
    (try
      (terminal-callback! lifecycle)
      (catch :default _ nil))))

(defn- select-polled-operation
  [{:keys [select-operation
           direction
           asset-key
           protocol-address
           destination-address
           wallet-address]} response]
  (let [source-address (if (= direction :withdraw)
                         wallet-address
                         nil)
        destination-address* (if (= direction :withdraw)
                               destination-address
                               wallet-address)]
    (select-operation (:operations (or response {}))
                      {:asset-key asset-key
                       :protocol-address protocol-address
                       :source-address source-address
                       :destination-address destination-address*})))

(defn- successful-poll-lifecycle
  [{:keys [direction
           asset-key
           operation->lifecycle
           awaiting-lifecycle]} operation now-ms]
  (if operation
    (operation->lifecycle operation direction asset-key now-ms)
    (awaiting-lifecycle direction asset-key now-ms)))

(defn- poll-error-message
  [non-blank-text err]
  (or (non-blank-text (some-> err .-message))
      "Unable to refresh lifecycle status right now."))

(defn- error-poll-lifecycle
  [{:keys [store
           direction
           asset-key
           awaiting-lifecycle
           non-blank-text]} err now-ms]
  (let [previous (get-in @store [:funding-ui :modal :hyperunit-lifecycle])]
    (assoc (merge (awaiting-lifecycle direction asset-key now-ms)
                  (if (map? previous) previous {}))
           :direction direction
           :asset-key asset-key
           :last-updated-ms now-ms
           :error (poll-error-message non-blank-text err))))

(defn- handle-poll-success!
  [{:keys [should-continue?
           now-ms!*
           update-lifecycle!
           refresh-withdraw-queue!
           hyperunit-lifecycle-terminal?
           clear-lifecycle-poll-token!
           poll-key
           token
           notify-terminal!
           lifecycle-next-delay-ms
           schedule-next!
           poll!]
    :as ctx}
   response]
  (when (should-continue?)
    (let [now-ms (now-ms!*)
          operation (select-polled-operation ctx response)
          lifecycle (successful-poll-lifecycle ctx operation now-ms)]
      (update-lifecycle! lifecycle)
      (refresh-withdraw-queue!)
      (if (hyperunit-lifecycle-terminal? lifecycle)
        (do
          (clear-lifecycle-poll-token! poll-key token)
          (notify-terminal! lifecycle))
        (schedule-next! (lifecycle-next-delay-ms now-ms lifecycle) poll!)))))

(defn- handle-poll-error!
  [{:keys [should-continue?
           now-ms!*
           update-lifecycle!
           refresh-withdraw-queue!
           default-poll-delay-ms
           schedule-next!
           poll!]
    :as ctx}
   err]
  (when (should-continue?)
    (let [now-ms (now-ms!*)
          lifecycle (error-poll-lifecycle ctx err now-ms)]
      (update-lifecycle! lifecycle)
      (refresh-withdraw-queue!)
      (schedule-next! default-poll-delay-ms poll!))))

(defn- callable-or-nil
  [value]
  (when (fn? value)
    value))

(defn- default-timeout!
  [f delay-ms]
  (js/setTimeout f delay-ms))

(defn- default-now-ms! []
  (js/Date.now))

(defn- resolve-poll-runtime
  [{:keys [request-hyperunit-operations!
           request-hyperunit-withdrawal-queue!
           set-timeout-fn
           now-ms-fn
           on-terminal-lifecycle!]}]
  {:request-ops! (callable-or-nil request-hyperunit-operations!)
   :request-queue! (callable-or-nil request-hyperunit-withdrawal-queue!)
   :terminal-callback! (callable-or-nil on-terminal-lifecycle!)
   :timeout! (or set-timeout-fn default-timeout!)
   :now-ms!* (or now-ms-fn default-now-ms!)})

(defn- build-poll-context
  [{:keys [store
           direction
           wallet-address
           asset-key
           protocol-address
           destination-address
           base-url
           base-urls
           lifecycle-poll-key-fn
           install-lifecycle-poll-token!
           clear-lifecycle-poll-token!
           lifecycle-poll-token-active?
           modal-active-for-lifecycle?
           normalize-hyperunit-lifecycle
           select-operation
           operation->lifecycle
           awaiting-lifecycle
           lifecycle-next-delay-ms
           hyperunit-lifecycle-terminal?
           fetch-hyperunit-withdrawal-queue!
           non-blank-text
           runtime-error-message
           default-poll-delay-ms]}
   {:keys [request-ops!
           request-queue!
           terminal-callback!
           timeout!
           now-ms!*]}]
  (let [poll-key (lifecycle-poll-key-fn store direction asset-key)
        token (str (random-uuid))
        should-continue? (fn []
                           (and (lifecycle-poll-token-active? poll-key token)
                                (modal-active-for-lifecycle? store direction asset-key protocol-address)))
        update-lifecycle! (fn [lifecycle]
                            (update-active-lifecycle! store
                                                      normalize-hyperunit-lifecycle
                                                      should-continue?
                                                      lifecycle))
        refresh-withdraw-queue! (fn []
                                  (refresh-active-withdraw-queue!
                                   {:direction direction
                                    :request-queue! request-queue!
                                    :should-continue? should-continue?
                                    :fetch-hyperunit-withdrawal-queue! fetch-hyperunit-withdrawal-queue!
                                    :store store
                                    :base-url base-url
                                    :base-urls base-urls
                                    :now-ms!* now-ms!*
                                    :runtime-error-message runtime-error-message
                                    :asset-key asset-key}))
        schedule-next! (fn [delay-ms poll-fn]
                         (schedule-next-poll! should-continue? timeout! delay-ms poll-fn))
        notify-terminal! (fn [lifecycle]
                           (notify-terminal-lifecycle! terminal-callback! lifecycle))]
    {:request-ops! request-ops!
     :store store
     :direction direction
     :wallet-address wallet-address
     :asset-key asset-key
     :protocol-address protocol-address
     :destination-address destination-address
     :base-url base-url
     :base-urls base-urls
     :poll-key poll-key
     :token token
     :install-lifecycle-poll-token! install-lifecycle-poll-token!
     :clear-lifecycle-poll-token! clear-lifecycle-poll-token!
     :select-operation select-operation
     :operation->lifecycle operation->lifecycle
     :awaiting-lifecycle awaiting-lifecycle
     :hyperunit-lifecycle-terminal? hyperunit-lifecycle-terminal?
     :default-poll-delay-ms default-poll-delay-ms
     :lifecycle-next-delay-ms lifecycle-next-delay-ms
     :non-blank-text non-blank-text
     :should-continue? should-continue?
     :now-ms!* now-ms!*
     :update-lifecycle! update-lifecycle!
     :refresh-withdraw-queue! refresh-withdraw-queue!
     :schedule-next! schedule-next!
     :notify-terminal! notify-terminal!}))

(defn- poll-success-context
  [ctx poll!]
  {:select-operation (:select-operation ctx)
   :direction (:direction ctx)
   :asset-key (:asset-key ctx)
   :protocol-address (:protocol-address ctx)
   :destination-address (:destination-address ctx)
   :wallet-address (:wallet-address ctx)
   :operation->lifecycle (:operation->lifecycle ctx)
   :awaiting-lifecycle (:awaiting-lifecycle ctx)
   :should-continue? (:should-continue? ctx)
   :now-ms!* (:now-ms!* ctx)
   :update-lifecycle! (:update-lifecycle! ctx)
   :refresh-withdraw-queue! (:refresh-withdraw-queue! ctx)
   :hyperunit-lifecycle-terminal? (:hyperunit-lifecycle-terminal? ctx)
   :clear-lifecycle-poll-token! (:clear-lifecycle-poll-token! ctx)
   :poll-key (:poll-key ctx)
   :token (:token ctx)
   :notify-terminal! (:notify-terminal! ctx)
   :lifecycle-next-delay-ms (:lifecycle-next-delay-ms ctx)
   :schedule-next! (:schedule-next! ctx)
   :poll! poll!})

(defn- poll-error-context
  [ctx poll!]
  {:store (:store ctx)
   :direction (:direction ctx)
   :asset-key (:asset-key ctx)
   :awaiting-lifecycle (:awaiting-lifecycle ctx)
   :non-blank-text (:non-blank-text ctx)
   :should-continue? (:should-continue? ctx)
   :now-ms!* (:now-ms!* ctx)
   :update-lifecycle! (:update-lifecycle! ctx)
   :refresh-withdraw-queue! (:refresh-withdraw-queue! ctx)
   :default-poll-delay-ms (:default-poll-delay-ms ctx)
   :schedule-next! (:schedule-next! ctx)
   :poll! poll!})

(defn- poll-lifecycle!
  [ctx]
  (letfn [(poll! []
            (if-not ((:should-continue? ctx))
              ((:clear-lifecycle-poll-token! ctx) (:poll-key ctx) (:token ctx))
              (-> ((:request-ops! ctx) {:base-url (:base-url ctx)
                                        :base-urls (:base-urls ctx)
                                        :address (:wallet-address ctx)})
                  (.then (fn [response]
                           (handle-poll-success!
                            (poll-success-context ctx poll!)
                            response)))
                  (.catch (fn [err]
                            (handle-poll-error!
                             (poll-error-context ctx poll!)
                             err))))))]
    (poll!)))

(defn start-hyperunit-lifecycle-polling!
  [{:keys [store
           direction
           wallet-address
           asset-key
           protocol-address
           destination-address
           base-url
           base-urls
           request-hyperunit-operations!
           request-hyperunit-withdrawal-queue!
           set-timeout-fn
           now-ms-fn
           runtime-error-message
           on-terminal-lifecycle!
           lifecycle-poll-key-fn
           install-lifecycle-poll-token!
           clear-lifecycle-poll-token!
           lifecycle-poll-token-active?
           modal-active-for-lifecycle?
           normalize-hyperunit-lifecycle
           select-operation
           operation->lifecycle
           awaiting-lifecycle
           lifecycle-next-delay-ms
           hyperunit-lifecycle-terminal?
           fetch-hyperunit-withdrawal-queue!
           non-blank-text
           default-poll-delay-ms]}]
  (let [{:keys [request-ops!] :as runtime}
        (resolve-poll-runtime
         {:request-hyperunit-operations! request-hyperunit-operations!
          :request-hyperunit-withdrawal-queue! request-hyperunit-withdrawal-queue!
          :set-timeout-fn set-timeout-fn
          :now-ms-fn now-ms-fn
          :on-terminal-lifecycle! on-terminal-lifecycle!})]
    (when (lifecycle-polling-eligible? {:request-ops! request-ops!
                                        :direction direction
                                        :wallet-address wallet-address
                                        :asset-key asset-key
                                        :non-blank-text non-blank-text})
      (let [ctx (build-poll-context
                 {:store store
                  :direction direction
                  :wallet-address wallet-address
                  :asset-key asset-key
                  :protocol-address protocol-address
                  :destination-address destination-address
                  :base-url base-url
                  :base-urls base-urls
                  :lifecycle-poll-key-fn lifecycle-poll-key-fn
                  :install-lifecycle-poll-token! install-lifecycle-poll-token!
                  :clear-lifecycle-poll-token! clear-lifecycle-poll-token!
                  :lifecycle-poll-token-active? lifecycle-poll-token-active?
                  :modal-active-for-lifecycle? modal-active-for-lifecycle?
                  :normalize-hyperunit-lifecycle normalize-hyperunit-lifecycle
                  :select-operation select-operation
                  :operation->lifecycle operation->lifecycle
                  :awaiting-lifecycle awaiting-lifecycle
                  :lifecycle-next-delay-ms lifecycle-next-delay-ms
                  :hyperunit-lifecycle-terminal? hyperunit-lifecycle-terminal?
                  :fetch-hyperunit-withdrawal-queue! fetch-hyperunit-withdrawal-queue!
                  :non-blank-text non-blank-text
                  :runtime-error-message runtime-error-message
                  :default-poll-delay-ms default-poll-delay-ms}
                 runtime)]
        (install-lifecycle-poll-token! (:poll-key ctx) (:token ctx))
        (poll-lifecycle! ctx)))))

(defn start-hyperunit-deposit-lifecycle-polling!
  [opts]
  (start-hyperunit-lifecycle-polling!
   (assoc opts :direction :deposit)))

(defn start-hyperunit-withdraw-lifecycle-polling!
  [opts]
  (start-hyperunit-lifecycle-polling!
   (assoc opts :direction :withdraw)))
