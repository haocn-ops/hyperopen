(ns hyperopen.portfolio.optimizer.infrastructure.run-bridge-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]
            [hyperopen.portfolio.optimizer.infrastructure.run-bridge :as run-bridge]
            [hyperopen.portfolio.optimizer.infrastructure.worker-client :as worker-client]
            [hyperopen.system :as system]))

(defn- fake-worker
  []
  (let [posted (atom [])
        listeners (atom [])
        terminated? (atom false)
        worker #js {}]
    (set! (.-postMessage worker)
          (fn [message]
            (swap! posted conj (js->clj message :keywordize-keys true))))
    (set! (.-addEventListener worker)
          (fn [event-name handler]
            (swap! listeners conj {:event-name event-name
                                   :handler handler})))
    (set! (.-terminate worker)
          (fn []
            (reset! terminated? true)))
    {:worker worker
     :posted posted
     :listeners listeners
     :terminated? terminated?}))

(defn- emit-worker-message!
  [{:keys [listeners]} message]
  (let [handler (:handler (first @listeners))]
    (handler #js {:data (clj->js message)})))

(defn- emit-worker-error!
  [{:keys [listeners]} attrs]
  (let [handler (:handler (first (filter #(= "error" (:event-name %))
                                         @listeners)))
        event #js {}]
    (doseq [[k v] attrs]
      (aset event (name k) v))
    (handler event)))

(deftest request-run-posts-worker-message-and-preserves-existing-success-test
  (let [{:keys [worker posted]} (fake-worker)
        store (atom {:portfolio {:optimizer {:last-successful-run
                                             (fixtures/sample-minimal-last-successful-run
                                              {:request-signature {:seed 0}
                                               :result {:old? true}})
                                             :run-state {:status :idle}}}})
        controller (run-bridge/make-controller {:store store
                                                :worker-ref worker})]
    (with-redefs [system/store store
                  run-bridge/next-run-id (fn [] "run-1")]
      (is (= "run-1"
             (run-bridge/request-run! {:controller controller
                                       :request {:scenario-id "scenario-1"}
                                       :request-signature {:seed 1}
                                       :computed-at-ms 100})))
      (is (= [{:id "run-1"
               :type "run-optimizer"
               :payload {:scenario-id "scenario-1"}}]
             @posted))
      (is (= {:status :running
              :run-id "run-1"
              :scenario-id "scenario-1"
              :request-signature {:seed 1}
              :started-at-ms 100
              :error nil}
             (get-in @store [:portfolio :optimizer :run-state])))
      (is (= {:status :solved
              :old? true}
             (get-in @store [:portfolio :optimizer :last-successful-run :result]))))))

(deftest request-run-preserves-namespaced-instrument-metadata-over-worker-boundary-test
  (let [{:keys [worker posted]} (fake-worker)
        store (atom {:portfolio {:optimizer {:run-state {:status :idle}}}})
        controller (run-bridge/make-controller {:store store
                                                :worker-ref worker})
        vault-id "vault:0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        backend-id (str "hl:" vault-id)]
    (with-redefs [system/store store
                  run-bridge/next-run-id (fn [] "run-1")]
      (run-bridge/request-run! {:controller controller
                                :request {:scenario-id "scenario-1"
                                          :universe [{:instrument-id vault-id
                                                      :market-type :vault
                                                      :coin vault-id
                                                      :vault-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
                                                      :optimizer-history/instrument-id backend-id}]}
                                :request-signature {:seed 1}
                                :computed-at-ms 100})
      (is (= vault-id
             (get-in @posted [0 :payload :universe 0 :instrument-id])))
      (is (= backend-id
             (get-in @posted [0 :payload :universe 0 :optimizer-history/instrument-id]))))))

(deftest request-run-replaces-owned-worker-between-runs-test
  (let [worker-a (fake-worker)
        worker-b (fake-worker)
        workers (atom [(:worker worker-a) (:worker worker-b)])
        store (atom {:portfolio {:optimizer {:run-state {:status :idle}}}})
        controller (run-bridge/make-controller {:store store})
        run-ids (atom ["run-1" "run-2"])]
    (with-redefs [worker-client/make-worker! (fn
                                               ([]
                                                (worker-client/make-worker!
                                                 worker-client/default-worker-url))
                                               ([_url]
                                               (let [worker (first @workers)]
                                                 (swap! workers subvec 1)
                                                 worker)))
                  run-bridge/next-run-id (fn []
                                           (let [run-id (first @run-ids)]
                                             (swap! run-ids subvec 1)
                                             run-id))]
      (is (= "run-1"
             (run-bridge/request-run! {:controller controller
                                       :request {:scenario-id "scenario-1"}
                                       :request-signature {:seed 1}
                                       :computed-at-ms 100})))
      (is (= [{:id "run-1"
               :type "run-optimizer"
               :payload {:scenario-id "scenario-1"}}]
             @(:posted worker-a)))
      (is (= "run-2"
             (run-bridge/request-run! {:controller controller
                                       :request {:scenario-id "scenario-1"}
                                       :request-signature {:seed 2}
                                       :computed-at-ms 200})))
      (is (true? @(:terminated? worker-a)))
      (is (= [{:id "run-2"
               :type "run-optimizer"
               :payload {:scenario-id "scenario-1"}}]
             @(:posted worker-b))))))

(deftest stale-owned-worker-error-does-not-fail-new-run-test
  (let [worker-a (fake-worker)
        worker-b (fake-worker)
        workers (atom [(:worker worker-a) (:worker worker-b)])
        store (atom {:portfolio {:optimizer {:run-state {:status :idle}}}})
        controller (run-bridge/make-controller {:store store})
        run-ids (atom ["run-1" "run-2"])]
    (with-redefs [worker-client/make-worker! (fn
                                               ([]
                                                (worker-client/make-worker!
                                                 worker-client/default-worker-url))
                                               ([_url]
                                                (let [worker (first @workers)]
                                                  (swap! workers subvec 1)
                                                  worker)))
                  run-bridge/next-run-id (fn []
                                           (let [run-id (first @run-ids)]
                                             (swap! run-ids subvec 1)
                                             run-id))]
      (is (= "run-1"
             (run-bridge/request-run! {:controller controller
                                       :request {:scenario-id "scenario-1"}
                                       :request-signature {:seed 1}
                                       :computed-at-ms 100})))
      (is (= "run-2"
             (run-bridge/request-run! {:controller controller
                                       :request {:scenario-id "scenario-1"}
                                       :request-signature {:seed 2}
                                       :computed-at-ms 200})))
      (emit-worker-error! worker-a {:message "Old worker failed."})
      (is (= :running
             (get-in @store [:portfolio :optimizer :run-state :status])))
      (is (= "run-2"
             (get-in @store [:portfolio :optimizer :run-state :run-id])))
      (is (nil?
           (get-in @store [:portfolio :optimizer :run-state :error]))))))

(deftest request-run-fails-current-run-when-worker-unavailable-test
  (let [store (atom {:portfolio {:optimizer {:run-state {:status :idle}}}})
        controller (run-bridge/make-controller {:store store})]
    (with-redefs [worker-client/make-worker! (fn
                                               ([] nil)
                                               ([_url] nil))
                  run-bridge/next-run-id (fn [] "run-missing-worker")]
      (is (= "run-missing-worker"
             (run-bridge/request-run! {:controller controller
                                       :request {:scenario-id "scenario-1"}
                                       :request-signature {:seed 1}
                                       :computed-at-ms 100})))
      (is (= :failed
             (get-in @store [:portfolio :optimizer :run-state :status])))
      (is (= :optimizer-worker-unavailable
             (get-in @store [:portfolio :optimizer :run-state :error :code]))))))

(deftest worker-error-event-fails-current-run-test
  (let [worker (fake-worker)
        store (atom {:portfolio {:optimizer {:run-state {:status :idle}}}})
        controller (run-bridge/make-controller {:store store
                                                :worker-ref (:worker worker)})]
    (with-redefs [run-bridge/next-run-id (fn [] "run-worker-error")]
      (is (= "run-worker-error"
             (run-bridge/request-run! {:controller controller
                                       :request {:scenario-id "scenario-1"}
                                       :request-signature {:seed 1}
                                       :computed-at-ms 100})))
      (emit-worker-error! worker {:message "Worker script failed."
                                  :filename "/js/portfolio_optimizer_worker.js"
                                  :lineno 12})
      (is (= :failed
             (get-in @store [:portfolio :optimizer :run-state :status])))
      (is (= {:code :optimizer-worker-error
              :message "Worker script failed."
              :filename "/js/portfolio_optimizer_worker.js"
              :lineno 12}
             (get-in @store [:portfolio :optimizer :run-state :error]))))))

(deftest request-run-dedupes-identical-in-flight-signature-test
  (let [{:keys [worker posted]} (fake-worker)
        store (atom {:portfolio {:optimizer {:run-state {:status :running
                                                         :run-id "run-1"
                                                         :request-signature {:seed 1}}}}})
        controller (run-bridge/make-controller {:store store
                                                :worker-ref worker})]
    (reset! (:last-run-request controller) {:request-signature {:seed 1}
                                            :run-id "run-1"})
    (with-redefs [system/store store
                  run-bridge/next-run-id (fn [] "run-2")]
      (is (nil? (run-bridge/request-run! {:request {:scenario-id "scenario-1"}
                                          :controller controller
                                          :request-signature {:seed 1}
                                          :computed-at-ms 101})))
      (is (empty? @posted))
      (is (= "run-1"
             (get-in @store [:portfolio :optimizer :run-state :run-id]))))))

(deftest request-run-dedupes-per-controller-not-globally-test
  (let [worker-a (fake-worker)
        worker-b (fake-worker)
        store-a (atom {:portfolio {:optimizer {:run-state {:status :idle}}}})
        store-b (atom {:portfolio {:optimizer {:run-state {:status :idle}}}})
        controller-a (run-bridge/make-controller {:store store-a
                                                  :worker-ref (:worker worker-a)})
        controller-b (run-bridge/make-controller {:store store-b
                                                  :worker-ref (:worker worker-b)})
        run-ids (atom ["run-a-1" "run-a-2" "run-b-1"])]
    (with-redefs [run-bridge/next-run-id (fn []
                                           (let [run-id (first @run-ids)]
                                             (swap! run-ids subvec 1)
                                             run-id))]
      (is (= "run-a-1"
             (run-bridge/request-run! {:controller controller-a
                                       :request {:scenario-id "scenario-a"}
                                       :request-signature {:seed 7}
                                       :computed-at-ms 100})))
      (is (nil?
           (run-bridge/request-run! {:controller controller-a
                                     :request {:scenario-id "scenario-a"}
                                     :request-signature {:seed 7}
                                     :computed-at-ms 101})))
      (is (= "run-b-1"
             (run-bridge/request-run! {:controller controller-b
                                       :request {:scenario-id "scenario-b"}
                                       :request-signature {:seed 7}
                                       :computed-at-ms 102})))
      (is (= ["run-a-1"]
             (mapv :id @(:posted worker-a))))
      (is (= ["run-b-1"]
             (mapv :id @(:posted worker-b))))
      (is (= "run-a-1"
             (get-in @store-a [:portfolio :optimizer :run-state :run-id])))
      (is (= "run-b-1"
             (get-in @store-b [:portfolio :optimizer :run-state :run-id]))))))

(deftest request-run-uses-explicit-runtime-store-when-provided-test
  (let [{:keys [worker posted]} (fake-worker)
        explicit-store (atom {:portfolio {:optimizer {:run-state {:status :idle}}}})
        default-store (atom {:portfolio {:optimizer {:run-state {:status :default}}}})
        controller (run-bridge/make-controller {:store explicit-store
                                                :worker-ref worker})]
    (with-redefs [system/store default-store
                  run-bridge/next-run-id (fn [] "run-explicit")]
      (is (= "run-explicit"
             (run-bridge/request-run! {:controller controller
                                       :request {:scenario-id "scenario-explicit"}
                                       :request-signature {:seed :explicit}
                                       :computed-at-ms 111})))
      (is (= :running
             (get-in @explicit-store [:portfolio :optimizer :run-state :status])))
      (is (= :default
             (get-in @default-store [:portfolio :optimizer :run-state :status])))
      (is (= [{:id "run-explicit"
               :type "run-optimizer"
               :payload {:scenario-id "scenario-explicit"}}]
             @posted)))))

(deftest worker-listener-updates-controller-store-only-test
  (let [worker-a (fake-worker)
        store-a (atom {:portfolio {:optimizer {:draft {:metadata {:dirty? true}}
                                               :run-state {:status :idle}}}})
        default-store (atom {:portfolio {:optimizer {:run-state {:status :default}}}})
        controller-a (run-bridge/make-controller {:store store-a
                                                  :worker-ref (:worker worker-a)})]
    (with-redefs [system/store default-store
                  run-bridge/next-run-id (fn [] "run-a-1")]
      (is (= "run-a-1"
             (run-bridge/request-run! {:controller controller-a
                                       :request {:scenario-id "scenario-a"}
                                       :request-signature {:seed :a}
                                       :computed-at-ms 100})))
      (is (= ["message" "error" "messageerror"]
             (mapv :event-name @(:listeners worker-a))))
      (emit-worker-message! worker-a
                            {:id "run-a-1"
                             :type "optimizer-result"
                             :payload {:status "solved"
                                       :scenario-id "scenario-a"}})
      (is (= :succeeded
             (get-in @store-a [:portfolio :optimizer :run-state :status])))
      (is (= :default
             (get-in @default-store [:portfolio :optimizer :run-state :status]))))))

(deftest worker-result-updates-last-successful-run-and-clears-draft-dirty-flag-test
  (let [store (atom {:portfolio {:optimizer {:draft {:metadata {:dirty? true}}
                                             :run-state {:status :running
                                                         :run-id "run-1"
                                                         :scenario-id "scenario-1"
                                                         :request-signature {:seed 1}}
                                             :last-successful-run {:result {:old? true}}}}})
        controller (run-bridge/make-controller {:store store})]
    (with-redefs [system/store store]
      (run-bridge/handle-worker-message! controller
                                         {:id "run-1"
                                          :type "optimizer-result"
                                          :payload {:status :solved
                                                    :scenario-id "scenario-1"}}
                                         {:computed-at-ms 200})
      (is (= {:status :succeeded
              :run-id "run-1"
              :scenario-id "scenario-1"
              :request-signature {:seed 1}
              :completed-at-ms 200
              :error nil}
             (get-in @store [:portfolio :optimizer :run-state])))
      (is (= {:request-signature {:seed 1}
              :result (fixtures/sample-minimal-solved-result
                       {:scenario-id "scenario-1"})
              :computed-at-ms 200}
             (get-in @store [:portfolio :optimizer :last-successful-run])))
      (is (= :computed
             (get-in @store [:portfolio :optimizer :active-scenario :status])))
      (is (false?
           (get-in @store [:portfolio :optimizer :draft :metadata :dirty?]))))))

(deftest worker-result-on-new-route-reveals-in-place-without-navigating-test
  (let [dispatches (atom [])
        store (atom {:router {:path "/portfolio/optimize/new"}
                     :portfolio {:optimizer {:draft {:metadata {:dirty? true}}
                                              :run-state {:status :running
                                                          :run-id "run-1"
                                                          :request-signature {:seed 1}}}}})
        controller (run-bridge/make-controller
                    {:store store
                     :dispatch! (fn [store* event actions]
                                  (swap! dispatches conj [store* event actions]))})]
    (run-bridge/handle-worker-message! controller
                                       {:id "run-1"
                                        :type "optimizer-result"
                                        :payload {:status :solved}}
                                       {:computed-at-ms 200})
    (is (= [[store
             nil
             [[:effects/refresh-portfolio-optimizer-rebalance-slippage-snapshots]]]]
           @dispatches)
        "A run on /optimize/new no longer dispatches a navigation; the result reveals in-place.")))

(deftest normalized-worker-result-with-string-status-updates-successful-run-test
  (let [store (atom {:portfolio {:optimizer {:run-state {:status :running
                                                         :run-id "run-1"
                                                         :scenario-id "scenario-1"
                                                         :request-signature {:seed 1}}}}})
        message (worker-client/normalize-worker-message
                 (clj->js {:id "run-1"
                           :type "optimizer-result"
                           :payload {:status "solved"
                                     :scenario-id "scenario-1"
                                     :solver {:strategy "single-qp"}
                                     :return-decomposition-by-instrument
                                     {"perp:BTC" {:return-component 0.12
                                                  :funding-component 0.04
                                                  :funding-source "market-funding-history"}}
                                     :current-weights-by-instrument
                                     {"spot:PURR/USDC" 0.2}
                                     :target-weights-by-instrument
                                     {"perp:BTC" 0.35}
                                     :warnings [{:code "missing-funding-history"}]}}))
        controller (run-bridge/make-controller {:store store})]
    (with-redefs [system/store store]
      (run-bridge/handle-worker-message! controller message {:computed-at-ms 250})
      (is (= :succeeded
             (get-in @store [:portfolio :optimizer :run-state :status])))
      (is (= {:status :solved
              :scenario-id "scenario-1"
              :solver {:strategy :single-qp}
              :return-decomposition-by-instrument
              {"perp:BTC" {:return-component 0.12
                           :funding-component 0.04
                           :funding-source :market-funding-history}}
              :current-weights-by-instrument {"spot:PURR/USDC" 0.2}
              :target-weights-by-instrument {"perp:BTC" 0.35}
              :warnings [{:code :missing-funding-history}]}
             (get-in @store [:portfolio :optimizer :last-successful-run :result]))))))

(deftest worker-result-ignores-stale-run-id-and-route-changes-test
  (let [state {:portfolio {:optimizer {:active-scenario {:loaded-id "scenario-2"}
                                       :run-state {:status :running
                                                   :run-id "run-2"
                                                   :scenario-id "scenario-2"
                                                   :request-signature {:seed 2}}
                                       :last-successful-run {:result {:old? true}}}}}
        store (atom state)
        controller (run-bridge/make-controller {:store store})]
    (with-redefs [system/store store]
      (run-bridge/handle-worker-message! controller
                                         {:id "run-1"
                                          :type "optimizer-result"
                                          :payload {:status :solved
                                                    :scenario-id "scenario-1"}}
                                         {:computed-at-ms 300})
      (is (= state @store)))))

(deftest worker-error-preserves-last-successful-result-test
  (let [store (atom {:portfolio {:optimizer {:run-state {:status :running
                                                         :run-id "run-1"
                                                         :scenario-id "scenario-1"
                                                         :request-signature {:seed 1}}
                                             :last-successful-run {:result {:old? true}}}}})
        controller (run-bridge/make-controller {:store store})]
    (with-redefs [system/store store]
      (run-bridge/handle-worker-message! controller
                                         {:id "run-1"
                                          :type "optimizer-error"
                                          :payload {:code :boom}}
                                         {:computed-at-ms 400})
      (is (= {:status :failed
              :run-id "run-1"
              :scenario-id "scenario-1"
              :request-signature {:seed 1}
              :completed-at-ms 400
              :error {:code :boom}}
             (get-in @store [:portfolio :optimizer :run-state])))
      (is (= {:old? true}
             (get-in @store [:portfolio :optimizer :last-successful-run :result]))))))
