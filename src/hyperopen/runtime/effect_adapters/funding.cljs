(ns hyperopen.runtime.effect-adapters.funding
  (:require [hyperopen.active-asset.funding-policy :as funding-policy]
            [hyperopen.asset-selector.markets :as markets]
            [hyperopen.api.default :as api]
            [hyperopen.api.projections :as api-projections]
            [hyperopen.funding.history-cache :as funding-cache]
            [hyperopen.funding.predictability :as funding-predictability]
            [hyperopen.funding-comparison.effects :as funding-effects]
            [hyperopen.platform :as platform]
            [hyperopen.ui.dialog-focus-runtime :as dialog-focus-runtime]))

(defn- funding-predictability-path
  [bucket coin]
  [:active-assets :funding-predictability bucket coin])

(defn- set-funding-predictability-loading!
  [store coin loading?]
  (swap! store
         (fn [state]
           (-> state
               (assoc-in (funding-predictability-path :loading-by-coin coin)
                         loading?)
               (assoc-in (funding-predictability-path :error-by-coin coin)
                         nil)))))

(defn- set-funding-predictability-success!
  [store coin summary loaded-at-ms]
  (swap! store
         (fn [state]
           (-> state
               (assoc-in (funding-predictability-path :loading-by-coin coin)
                         false)
               (assoc-in (funding-predictability-path :error-by-coin coin)
                         nil)
               (assoc-in (funding-predictability-path :by-coin coin)
                         summary)
               (assoc-in (funding-predictability-path :loaded-at-ms-by-coin coin)
                         loaded-at-ms)))))

(defn- set-funding-predictability-error!
  [store coin error-message loaded-at-ms]
  (swap! store
         (fn [state]
           (-> state
               (assoc-in (funding-predictability-path :loading-by-coin coin)
                         false)
               (assoc-in (funding-predictability-path :error-by-coin coin)
                         error-message)
               (assoc-in (funding-predictability-path :loaded-at-ms-by-coin coin)
                         loaded-at-ms)))))

(defn- active-related-market
  [state]
  (let [active-asset (:active-asset state)
        active-market (:active-market state)
        market-by-key (get-in state [:asset-selector :market-by-key] {})]
    (cond
      (markets/market-matches-coin? active-market active-asset)
      active-market

      active-asset
      (markets/resolve-or-infer-market-by-coin market-by-key active-asset)

      :else
      nil)))

(defn- active-asset-funding-tooltip-open?
  [state coin]
  (let [active-market (active-related-market state)
        related-coins (->> (markets/coin-aliases (:active-asset state)
                                                 active-market)
                           (keep funding-cache/normalize-coin)
                           vec)
        tooltip-ui (get-in state [:funding-ui :tooltip] {})
        active-tooltip-ids (set (map funding-policy/funding-tooltip-pin-id
                                     related-coins))]
    (and (some #{coin} related-coins)
         (or (contains? active-tooltip-ids (:visible-id tooltip-ui))
             (contains? active-tooltip-ids (:pinned-id tooltip-ui))))))

(defn sync-active-asset-funding-predictability
  [_ store coin]
  (if-let [coin* (funding-cache/normalize-coin coin)]
    (if (active-asset-funding-tooltip-open? @store coin*)
      (do
        (set-funding-predictability-loading! store coin* true)
        (-> (apply funding-cache/sync-market-funding-history-cache!
                   [coin*])
            (.then (fn [{:keys [rows]}]
                     (let [now-ms (platform/now-ms)
                           rows* (funding-cache/rows-for-window rows
                                                                now-ms
                                                                funding-predictability/thirty-day-window-ms)
                           summary (funding-predictability/compute-30d-summary rows* now-ms)]
                       (set-funding-predictability-success! store coin* summary now-ms))))
            (.catch (fn [error]
                      (let [now-ms (platform/now-ms)
                            error-message (or (some-> error .-message)
                                              (str error))]
                        (set-funding-predictability-error! store coin* error-message now-ms))))))
      (js/Promise.resolve nil))
    (js/Promise.resolve nil)))

(defn api-fetch-predicted-fundings-effect
  [_ store]
  (funding-effects/api-fetch-predicted-fundings!
   {:store store
    :request-predicted-fundings! api/request-predicted-fundings!
    :begin-funding-comparison-load api-projections/begin-funding-comparison-load
    :apply-funding-comparison-success api-projections/apply-funding-comparison-success
    :apply-funding-comparison-error api-projections/apply-funding-comparison-error}))

(defn restore-dialog-focus-effect
  [_ _]
  (dialog-focus-runtime/restore-remembered-focus!))
