(ns hyperopen.portfolio.optimizer.infrastructure.draft-autosave-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]
            [hyperopen.portfolio.optimizer.infrastructure.draft-autosave :as draft-autosave]))

(def ^:private address
  "0x1111111111111111111111111111111111111111")

(def ^:private btc-instrument
  {:instrument-id "perp:BTC"
   :market-type :perp
   :coin "BTC"
   :shortable? true
   :position-side :long})

(defn- touched-draft
  []
  (assoc (optimizer-defaults/default-draft)
         :universe [btc-instrument]))

(defn- fake-timers
  "Deterministic timer seam: scheduling stores the callback; (flush!) runs it."
  []
  (let [scheduled (atom nil)]
    {:set-timeout-fn (fn [f _ms] (reset! scheduled f) ::timer)
     :clear-timeout-fn (fn [_id] (reset! scheduled nil))
     :flush! (fn [] (when-let [f @scheduled]
                      (reset! scheduled nil)
                      (f)))
     :scheduled? (fn [] (some? @scheduled))}))

(defn- install!
  [store timers saves]
  ;; The last-persisted guard is module state (shared with the restore effect);
  ;; reset it so tests are order-independent.
  (draft-autosave/note-persisted! nil)
  (draft-autosave/install-draft-autosave-watcher!
   {:store store
    :save-draft! (fn [addr record]
                   (swap! saves conj [addr record])
                   (js/Promise.resolve true))
    :now-ms-fn (constantly 1700000000000)
    :set-timeout-fn (:set-timeout-fn timers)
    :clear-timeout-fn (:clear-timeout-fn timers)}))

(deftest autosave-persists-a-touched-draft-per-wallet-test
  (async done
    (let [store (atom {:wallet {:address address}})
          timers (fake-timers)
          saves (atom [])]
      (install! store timers saves)
      (swap! store assoc-in [:portfolio :optimizer :draft] (touched-draft))
      (is ((:scheduled? timers)) "edit schedules a debounced flush")
      ((:flush! timers))
      ;; The save promise resolves on a microtask; assert afterwards.
      (-> (js/Promise.resolve)
          (.then (fn []
                   (is (= 1 (count @saves)))
                   (let [[addr record] (first @saves)]
                     (is (= address addr))
                     (is (= 1 (:version record)))
                     (is (= (touched-draft) (:draft record)))
                     (is (= 1700000000000 (:saved-at-ms record))))
                   (is (= {:status :saved :at-ms 1700000000000}
                          (get-in @store [:portfolio :optimizer :draft-persist])))
                   (remove-watch store :hyperopen.portfolio.optimizer.infrastructure.draft-autosave/draft-autosave)
                   (done)))))))

(deftest autosave-skips-untouched-drafts-and-noop-changes-test
  (let [store (atom {:wallet {:address address}})
        timers (fake-timers)
        saves (atom [])]
    (install! store timers saves)
    ;; A pristine default draft never persists.
    (swap! store assoc-in [:portfolio :optimizer :draft]
           (optimizer-defaults/default-draft))
    (is (not ((:scheduled? timers))))
    ;; A draft the restore effect just hydrated is already persisted: no re-write.
    (let [restored (touched-draft)]
      (draft-autosave/note-persisted! restored)
      (swap! store assoc-in [:portfolio :optimizer :draft] restored)
      (is (not ((:scheduled? timers)))))
    (remove-watch store :hyperopen.portfolio.optimizer.infrastructure.draft-autosave/draft-autosave)))

(deftest autosave-skips-flush-after-a-wallet-switch-test
  ;; The address is captured at edit time and re-verified at flush time, so a
  ;; wallet switch mid-debounce can never write wallet A's draft under wallet B.
  (let [store (atom {:wallet {:address address}})
        timers (fake-timers)
        saves (atom [])]
    (install! store timers saves)
    (swap! store assoc-in [:portfolio :optimizer :draft] (touched-draft))
    (is ((:scheduled? timers)))
    (swap! store assoc-in [:wallet :address]
           "0x2222222222222222222222222222222222222222")
    ((:flush! timers))
    (is (= [] @saves))
    (remove-watch store :hyperopen.portfolio.optimizer.infrastructure.draft-autosave/draft-autosave)))

(deftest holdings-arrival-dispatches-restore-or-preseed-test
  ;; Cold load: /optimize/new is entered before account data exists; when a
  ;; holdings slice lands, the watcher re-enters the restore-or-preseed funnel.
  (let [dispatches (atom [])
        store (atom {:router {:path "/portfolio/optimize/new"}
                     :portfolio {:optimizer {:draft nil}}})]
    (draft-autosave/install-holdings-preseed-watcher!
     {:store store
      :dispatch! (fn [_store _event actions]
                   (swap! dispatches conj actions))})
    (swap! store assoc-in [:webdata2 :clearinghouseState]
           {:marginSummary {:accountValue "1000"}})
    (is (= [[[:actions/restore-or-preseed-portfolio-optimizer-draft
              "/portfolio/optimize/new"]]]
           @dispatches))
    ;; Subsequent updates while the same source is already present do not re-fire.
    (swap! store assoc-in [:webdata2 :clearinghouseState :assetPositions] [])
    (is (= 1 (count @dispatches)))
    (remove-watch store :hyperopen.portfolio.optimizer.infrastructure.draft-autosave/holdings-preseed)))

(deftest holdings-arrival-fires-per-source-test
  ;; Spot balances routinely land BEFORE the perp clearinghouse snapshot. A
  ;; spot-first arrival (often unseedable while :include-spot? is off) must not
  ;; consume the only trigger: the later perp arrival re-fires the funnel.
  (let [dispatches (atom [])
        store (atom {:router {:path "/portfolio/optimize/new"}
                     :portfolio {:optimizer {:draft nil}}})]
    (draft-autosave/install-holdings-preseed-watcher!
     {:store store
      :dispatch! (fn [_store _event actions]
                   (swap! dispatches conj actions))})
    (swap! store assoc-in [:spot :clearinghouse-state :balances]
           [{:coin "PURR" :total "10"}])
    (is (= 1 (count @dispatches)) "spot arrival fires once")
    (swap! store assoc-in [:webdata2 :clearinghouseState]
           {:marginSummary {:accountValue "1000"}})
    (is (= 2 (count @dispatches)) "later perp arrival fires again")
    (remove-watch store :hyperopen.portfolio.optimizer.infrastructure.draft-autosave/holdings-preseed)))

(deftest holdings-arrival-respects-route-and-touched-draft-gates-test
  (let [dispatches (atom [])
        install! (fn [store]
                   (draft-autosave/install-holdings-preseed-watcher!
                    {:store store
                     :dispatch! (fn [_ _ actions]
                                  (swap! dispatches conj actions))})
                   store)]
    ;; Wrong route: no dispatch.
    (let [store (install! (atom {:router {:path "/trade"}
                                 :portfolio {:optimizer {:draft nil}}}))]
      (swap! store assoc-in [:webdata2 :clearinghouseState] {:ok true})
      (remove-watch store :hyperopen.portfolio.optimizer.infrastructure.draft-autosave/holdings-preseed))
    ;; Touched draft: no dispatch.
    (let [store (install! (atom {:router {:path "/portfolio/optimize/new"}
                                 :portfolio {:optimizer
                                             {:draft {:universe [btc-instrument]}}}}))]
      (swap! store assoc-in [:webdata2 :clearinghouseState] {:ok true})
      (remove-watch store :hyperopen.portfolio.optimizer.infrastructure.draft-autosave/holdings-preseed))
    (is (= [] @dispatches))))
