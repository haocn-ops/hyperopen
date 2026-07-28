(ns hyperopen.service.portfolio-analytics
  "Pure account analytics view model built from Hyperopen's native history projections."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.metrics.normalization :as normalization]
            [hyperopen.portfolio.metrics.parsing :as parsing]
            [hyperopen.service.attribution :as attribution]
            [hyperopen.service.tenant-config :as tenant-config]))

(defn- finite-number?
  [value]
  (some? (parsing/optional-number value)))

(defn- native-points
  [history]
  (mapv (fn [{:keys [time-ms account-value pnl-value]}]
          {:at-ms time-ms :value account-value :pnl pnl-value})
        (normalization/aligned-account-pnl-points history)))

(defn- legacy-points
  [rows]
  (->> (or rows [])
       (keep (fn [row]
               (let [at-ms (parsing/optional-number
                            (or (:at-ms row) (:time-ms row) (:timestamp row)))
                     value (parsing/optional-number
                            (or (:value row) (:equity row) (:account-value row)))]
                 (when (and (some? at-ms) (some? value))
                   {:at-ms at-ms :value value}))))
       (sort-by :at-ms)
       vec))

(defn- account-points
  [history]
  (if (or (contains? history :accountValueHistory)
          (contains? history :pnlHistory))
    (native-points history)
    (legacy-points (:equity history))))

(defn- fill-number
  [fill keys]
  (some (fn [key]
          (parsing/optional-number (get fill key)))
        keys))

(defn- fill-volume
  [fill]
  (or (when-let [notional (fill-number fill [:notional :volume :usd])]
        notional)
      (when-let [px (fill-number fill [:px :price])]
        (when-let [size (fill-number fill [:sz :size :quantity])]
          (* px size)))))

(defn- drawdown-pct
  [points]
  (when (>= (count points) 2)
    (let [pnl-path? (every? #(finite-number? (:pnl %)) points)
          start-equity (:value (first points))
          start-pnl (:pnl (first points))
          path-value (fn [point]
                       (if (and pnl-path? (pos? start-equity))
                         (+ 1 (/ (- (:pnl point) start-pnl) start-equity))
                         (:value point)))]
      (when (and (finite-number? start-equity) (pos? start-equity))
        (loop [remaining (rest points)
               peak (path-value (first points))
               worst 0]
          (if-let [point (first remaining)]
            (let [value (path-value point)
                  peak* (max peak value)
                  dd (if (pos? peak*) (* 100 (- (/ value peak*) 1)) 0)]
              (recur (rest remaining) peak* (min worst dd)))
            worst))))))

(defn- freshness-live?
  [freshness]
  (and (map? freshness)
       (finite-number? (:fetched-at-ms freshness))
       (finite-number? (:now-ms freshness))
       (finite-number? (:max-age-ms freshness))
       (<= (:fetched-at-ms freshness) (:now-ms freshness))
       (<= (- (:now-ms freshness) (:fetched-at-ms freshness))
           (:max-age-ms freshness))))

(defn- safe-message
  [value fallback]
  (let [message (when (string? value)
                  (str/trim value))
        message* (when message
                   (str/replace message #"0x[0-9a-fA-F]{40}" "the selected account"))]
    (if (and (seq message*)
             (<= (count message*) 160)
             (re-matches #"[A-Za-z0-9 .,;:!?()'/-]+" message*)
             (not (re-find #"(?i)(api[_ -]?secret|access[_ -]?token|authorization|bearer|x[_ -]?(api[_ -]?key|session)|api[_ -]?key|session|token|secret|signature|seed|password|credential|private[_ -]?key)"
                           message*)))
      message*
      fallback)))

(defn- complete-history?
  [points]
  (and (>= (count points) 2)
       (every? #(finite-number? (:value %)) points)
       (every? #(finite-number? (:pnl %)) points)))

(defn- valid-fee-rates
  [fee-rates]
  (let [maker (parsing/optional-number (:maker fee-rates))
        taker (parsing/optional-number (:taker fee-rates))]
    (when (and (some? maker) (some? taker))
      {:maker maker
       :taker taker})))

(defn- field-status
  [value]
  (if (some? value) :available :unavailable))

(defn- fee-rates-fresh?
  [history]
  (let [fee-rates-state (:fee-rates-state history)]
    (or (not (map? fee-rates-state))
        (true? (:fresh? fee-rates-state)))))

(defn- fee-rates-status
  [history fee-rates valid-account?]
  (let [fee-rates-state (:fee-rates-state history)]
    (cond
      (and fee-rates (fee-rates-fresh? history)) :available
      (and valid-account?
           (map? fee-rates-state)
           (or (:loaded? fee-rates-state)
               (:error? fee-rates-state))) :stale
      :else :unavailable)))

(defn- incomplete-fee-rates?
  [history fee-rates]
  (and (map? (:fee-rates-state history))
       (or (nil? fee-rates)
           (not (fee-rates-fresh? history)))))

(defn- data-quality
  [account history points retained? complete? fresh?]
  (let [status (:status history)
        provider-error? (= :error status)
        loading? (= :loading status)]
    (cond
      (not (string? account)) :unavailable
      (= :demo (:source history)) :demo
      (and retained? provider-error?) :stale
      (and retained? (not fresh?)) :stale
      (not retained?) (cond
                        loading? :loading
                        provider-error? :provider-error
                        :else :empty)
      (not complete?) :partial
      :else :live)))

(defn build-portfolio-view-model
  [tenant history options]
  (let [tenant* (tenant-config/normalize-tenant-config tenant)
        history* (if (map? history) history {})
        options* (if (map? options) options {})
        points (account-points history*)
        account (:account options*)
        fills-present? (contains? history* :userFills)
        fills (when fills-present? (:userFills history*))
        fills* (when (sequential? fills) (vec fills))
        fill-volumes (when fills-present?
                       (vec (keep fill-volume fills*)))
        fill-fees (when fills-present?
                    (vec (keep #(fill-number % [:fee :fees]) fills*)))
        first-point (first points)
        last-point (last points)
        first-equity (:value first-point)
        last-equity (:value last-point)
        native-pnl? (some? (:pnl last-point))
        pnl (cond
              (and native-pnl? (some? (:pnl first-point)))
              (- (:pnl last-point) (:pnl first-point))

              (and (finite-number? first-equity) (finite-number? last-equity))
              (- last-equity first-equity)

              :else nil)
        fills-complete? (and fills-present? (sequential? fills))
        summary-volume (some (fn [key]
                               (parsing/optional-number (get history* key)))
                             [:vlm :volume])
        volume (or summary-volume
                   (when fills-complete?
                     (reduce + 0 fill-volumes)))
        complete-fill-fees? (and (seq fills*)
                                 (= (count fills*) (count fill-fees)))
        fees (when complete-fill-fees?
               (reduce + 0 fill-fees))
        historical-fees (when complete-fill-fees?
                          (reduce + 0 fill-fees))
        fee-rates (valid-fee-rates (:fee-rates history*))
        complete? (complete-history? points)
        retained? (or (seq points)
                      (some? summary-volume)
                      (seq fill-volumes))
        fresh? (freshness-live? (:freshness history*))
        initial-quality (data-quality account history* points retained? complete? fresh?)
        quality (if (and (= :live initial-quality)
                         (incomplete-fee-rates? history* fee-rates))
                  :partial
                  initial-quality)
        valid-account? (string? account)
        available? (not (contains? #{:loading :provider-error :empty :unavailable} quality))
        equity (when available? last-equity)
        pnl* (when available? pnl)
        return-pct (when (and available? (finite-number? pnl)
                              (finite-number? first-equity) (pos? first-equity))
                     (* 100 (/ pnl first-equity)))
        max-drawdown-pct (when (and available? (= :live quality) complete?)
                           (drawdown-pct points))
        volume* (when available? volume)
        fees* (when available? fees)
        historical-fees* (when available? historical-fees)
        fee-rates* (when (and valid-account? (fee-rates-fresh? history*))
                     fee-rates)
        as-of-ms (let [last-point-at-ms (:at-ms last-point)
                       fetched-at-ms (get-in history* [:freshness :fetched-at-ms])]
                   (cond
                     (finite-number? last-point-at-ms) last-point-at-ms
                     (finite-number? fetched-at-ms) fetched-at-ms
                     :else nil))
        message (case quality
                  :loading "Loading portfolio analytics"
                  :empty "No portfolio activity is available"
                  :live "Live provider data"
                  :stale (safe-message (:error history*)
                                       "Showing the last known portfolio result while refresh is unavailable")
                  :partial "Some portfolio analytics are not supplied"
                  :provider-error (safe-message (:error history*)
                                                "Portfolio provider is unavailable")
                  :demo "Demo portfolio data"
                  :unavailable "Connect a wallet to view portfolio analytics")]
    {:tenant/id (:tenant/id tenant*)
     :account account
     :range (:range options*)
     :equity equity
     :pnl pnl*
     :return-pct return-pct
     :max-drawdown-pct max-drawdown-pct
     :volume volume*
     :fees fees*
     :historical-fees historical-fees*
     :fee-rates fee-rates*
     :fee-rates-as-of-ms (get-in history* [:fee-rates-state :as-of-ms])
     :timeseries (if available? points [])
     :data-quality quality
     :as-of-ms as-of-ms
     :message message
     :retry? (contains? #{:stale :provider-error} quality)
     :field-status {:equity (field-status equity)
                    :pnl (field-status pnl*)
                    :return-pct (field-status return-pct)
                    :max-drawdown-pct (field-status max-drawdown-pct)
                    :volume (field-status volume*)
                    :historical-fees (field-status historical-fees*)
                    :fee-rates (fee-rates-status history* fee-rates* valid-account?)}}))

(defn build-analytics-viewed-event
  [tenant options]
  (let [tenant* (tenant-config/normalize-tenant-config tenant)
        options* (if (map? options) options {})
        context (attribution/build-attribution-context
                 tenant*
                 {:session/id (:session/id options*)
                  :wallet/address (:account options*)
                  :occurred-at-ms (:occurred-at-ms options*)})]
    (attribution/build-attribution-event
     context
     :analytics-viewed
     {:range (:range options*)})))
