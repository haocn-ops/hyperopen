(ns hyperopen.portfolio.optimizer.application.view-model.universe
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.application.universe-candidates :as universe-candidates]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]
            [hyperopen.portfolio.optimizer.domain.history-assumptions :as history-assumptions]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def ^:private normalized-text coercion/non-blank-text)
(def ^:private finite-number coercion/parse-number)

(def ^:private missing-history-warning-codes
  #{:missing-history-coin
    :missing-candle-history
    :missing-return-history
    :missing-vault-address
    :missing-vault-history})

(def ^:private insufficient-history-warning-codes
  #{:insufficient-candle-history
    :insufficient-return-history
    :insufficient-vault-history})

(def ^:private history-status-labels
  {:queued "queued"
   :loading "loading"
   :shared-gap "shared gap"
   :sufficient "sufficient"
   :stale "stale"
   :insufficient "insufficient"
   :rejected "rejected"
   :missing "missing"
   :pending "pending"})

(def ^:private all-clear-history-statuses
  "Statuses that need no user attention, so the history chip is hidden (the
  column is shown by-exception). :stale is treated as all-clear: a one-to-two
  day stale tail is immaterial to the covariance estimate and is dominated by
  upstream refresh lag, so it is not worth a per-row badge. Genuine coverage
  problems still surface as :insufficient / :shared-gap / :missing / :rejected."
  #{:sufficient :stale})

(def ^:private in-progress-history-statuses
  "Transient load states; shown with a neutral tone rather than a warning."
  #{:queued :loading :pending})

(defn- history-chip-display
  "By-exception history chip. Returns nil for all-clear statuses (healthy rows
  render no chip), a neutral chip for in-progress load states, and an amber
  warning chip for the genuine coverage problems."
  [history-status]
  (when-not (contains? all-clear-history-statuses history-status)
    {:label (get history-status-labels history-status "pending")
     :tone (if (contains? in-progress-history-statuses history-status)
             :muted
             :warn)}))

(defn- instrument-ids
  [instruments]
  (into #{} (keep :instrument-id) instruments))

(defn- warning-by-instrument-id
  [readiness instrument-id]
  (some (fn [warning]
          (when (= instrument-id (:instrument-id warning))
            warning))
        (:blocking-warnings readiness)))

(defn- history-rows
  [state instrument]
  (if (= :vault (:market-type instrument))
    (get-in state (conj contracts/history-data-path
                        :vault-details-by-address
                        (:vault-address instrument)))
    (get-in state (conj contracts/history-data-path
                        :candle-history-by-coin
                        (:coin instrument)))))

(defn- compact-usd
  [value]
  (if-let [n (finite-number value)]
    (cond
      (>= n 1000000000) (str "$" (.toFixed (/ n 1000000000) 1) "B")
      (>= n 1000000) (str "$" (.toFixed (/ n 1000000) 0) "M")
      (>= n 1000) (str "$" (.toFixed (/ n 1000) 0) "K")
      :else (str "$" (.toFixed n 0)))
    "--"))

(defn- raw-asset-id?
  [value]
  (let [text (normalized-text value)]
    (boolean
     (and text
          (or (str/starts-with? text "@")
              (re-matches #"\d+" text))))))

(defn- vault-instrument?
  [instrument]
  (ids/vault-instrument? instrument))

(defn- vault-address
  [instrument]
  (or (ids/normalize-vault-address (:vault-address instrument))
      (ids/vault-address-from-value (:coin instrument))
      (ids/vault-address-from-value (:instrument-id instrument))))

(defn- hip3-instrument?
  [instrument]
  (boolean
   (or (:dex instrument)
       (:hip3? instrument)
       (:hip3-eligible? instrument))))

(defn- spot-instrument?
  [instrument]
  (= :spot (ids/normalize-market-type (:market-type instrument))))

(defn- symbol-first?
  [instrument]
  (or (spot-instrument? instrument)
      (hip3-instrument? instrument)
      (raw-asset-id? (:coin instrument))))

(defn- base-from-symbol
  [symbol]
  (let [symbol* (normalized-text symbol)]
    (cond
      (and symbol* (str/includes? symbol* "/"))
      (normalized-text (first (str/split symbol* #"/" 2)))

      (and symbol* (str/includes? symbol* "-"))
      (normalized-text (first (str/split symbol* #"-" 2)))

      :else nil)))

(defn instrument-primary-label
  [instrument]
  (or (when (vault-instrument? instrument)
        (or (normalized-text (:name instrument))
            (normalized-text (:symbol instrument))
            (vault-address instrument)))
      (when (symbol-first? instrument)
        (normalized-text (:symbol instrument)))
      (when (raw-asset-id? (:coin instrument))
        (or (normalized-text (:base instrument))
            (base-from-symbol (:symbol instrument))))
      (normalized-text (:coin instrument))
      (normalized-text (:symbol instrument))
      (normalized-text (:instrument-id instrument))
      "--"))

(defn instrument-base-label
  [instrument]
  (or (when (vault-instrument? instrument)
        (vault-address instrument))
      (normalized-text (:base instrument))
      (base-from-symbol (:symbol instrument))
      (when-not (raw-asset-id? (:coin instrument))
        (normalized-text (:coin instrument)))))

(defn- adv-label
  [market]
  (compact-usd (or (:volume24h market)
                   (:volume market)
                   (:openInterest market)
                   (:tvl market))))

(defn- liquidity-label
  [market-or-instrument]
  (let [value (or (:liquidity market-or-instrument)
                  (:liquidity-label market-or-instrument)
                  (:depth market-or-instrument))]
    (or (when (= :vault (:market-type market-or-instrument))
          "vault")
        (normalized-text value)
        (if-let [volume (finite-number (or (:volume24h market-or-instrument)
                                           (:volume market-or-instrument)))]
          (if (>= volume 50000000) "deep" "medium")
          "medium"))))

(defn- position-side
  [instrument]
  (case (coercion/normalize-keyword-like (:position-side instrument))
    :short :short
    :long))

(defn- short-selectable?
  [instrument]
  (cond
    (contains? instrument :shortable?)
    (true? (:shortable? instrument))

    (= :perp (ids/normalize-market-type
              (or (:market-type instrument)
                  (:instrument-type instrument))))
    true

    :else false))

(declare selected-history-status)

(def ^:private assumption-badge-labels
  {:ready "Ready"
   :short-history "Short history"
   :no-history "No history"
   :needs-assumptions "Needs assumptions"
   :using-proxy "Using proxy"
   :conservative "Conservative"})

(defn- native-observation-count
  [state instrument]
  (let [rows (history-rows state instrument)]
    (when (sequential? rows)
      (count rows))))

(defn history-adequacy
  "Card/badge adequacy: layers the user-facing short-history threshold on top of the
  load-aware history status. The engine's own minimum is only 1-2 observations, so a
  thin-but-present asset (e.g. 75 daily candles) reads as :sufficient there; here it
  is reclassified :short when its native history is below the threshold."
  [history-status state instrument]
  (case history-status
    (:missing :rejected) :none
    (:insufficient :shared-gap) :short
    (:queued :loading :pending) :pending
    ;; :sufficient / :stale -> adequate unless the native history is below the bar.
    (let [observations (native-observation-count state instrument)]
      (if (and observations
               (< observations history-assumptions/short-history-min-observations))
        :short
        :ok))))

(def ^:private adequacy-badges
  {:none :no-history
   :short :short-history
   :ok :ready})

(defn- assumption-badge
  [entry adequacy return-required?]
  (cond
    (and entry
         (history-assumptions/conservative? entry)
         (history-assumptions/conservative-assumption-complete? entry return-required?))
    :conservative

    (some? (:behavior entry))
    :needs-assumptions

    :else
    (get adequacy-badges adequacy)))

(defn selected-row-model
  ([state readiness history-load-state history-status-by-id instrument]
   (selected-row-model state readiness history-load-state history-status-by-id
                       instrument nil))
  ([state readiness history-load-state history-status-by-id instrument assumption-context]
   (let [instrument-id (:instrument-id instrument)
         history-status (selected-history-status state
                                                 readiness
                                                 history-load-state
                                                 history-status-by-id
                                                 instrument)
         history-chip (history-chip-display history-status)
         primary-label (instrument-primary-label instrument)
         {:keys [name base-label]} (universe-candidates/market-display instrument)
         secondary-label (or (normalized-text (:name instrument))
                             (normalized-text (:full-name instrument))
                             (when (symbol-first? instrument)
                               base-label)
                             name)
         entry (get (:assumptions assumption-context) instrument-id)
         adequacy (history-adequacy history-status state instrument)
         badge (assumption-badge entry
                                 adequacy
                                 (:return-required? assumption-context))]
     (cond-> {:instrument instrument
              :instrument-id instrument-id
              :coin (:coin instrument)
              :market-type (:market-type instrument)
              :primary-label primary-label
              :secondary-label secondary-label
              :history-status history-status
              :liquidity-label (liquidity-label instrument)
              :position-side (position-side instrument)
              :short-selectable? (short-selectable? instrument)}
       history-chip
       (assoc :history-label (:label history-chip)
              :history-tone (:tone history-chip))
       badge
       (assoc :assumption-badge badge
              :assumption-badge-label (get assumption-badge-labels badge))))))

(defn candidate-row-model
  [market idx active-index]
  (let [market-key (:key market)
        {:keys [label name]} (universe-candidates/market-display market)]
    {:market market
     :market-key market-key
     :market-type (:market-type market)
     :active? (= idx active-index)
     :label label
     :name name
     :adv-label (adv-label market)}))

(defn selected-history-status
  [state readiness history-load-state history-status-by-id instrument]
  (let [instrument-id (:instrument-id instrument)
        prefetch-status (get-in state
                                (conj contracts/history-prefetch-path
                                      :by-instrument-id
                                      instrument-id
                                      :status))
        loading-ids (instrument-ids (get-in history-load-state
                                            [:request-signature :universe]))
        eligible-ids (instrument-ids (get-in readiness [:request :universe]))
        readiness-status (get history-status-by-id instrument-id)
        warning (warning-by-instrument-id readiness instrument-id)
        warning-code (:code warning)
        load-validated? (and (= :succeeded (:status history-load-state))
                             (contains? loading-ids instrument-id))
        cached-history? (seq (history-rows state instrument))]
    (cond
      (= :queued prefetch-status)
      :queued

      (= :loading prefetch-status)
      :loading

      (and (= :loading (:status history-load-state))
           (contains? loading-ids instrument-id))
      :loading

      (= :loaded-but-misaligned readiness-status)
      :shared-gap

      (= :rejected readiness-status)
      :rejected

      (= :stale readiness-status)
      :stale

      (= :aligned readiness-status)
      :sufficient

      (contains? eligible-ids instrument-id)
      :sufficient

      (and (= :insufficient readiness-status)
           (or load-validated? cached-history?))
      :insufficient

      (and (contains? insufficient-history-warning-codes warning-code)
           (or load-validated? cached-history?))
      :insufficient

      (and (= :missing readiness-status)
           load-validated?)
      :missing

      (and (contains? missing-history-warning-codes warning-code)
           load-validated?)
      :missing

      :else
      :pending)))

(defn selected-history-label
  [state readiness history-load-state history-status-by-id instrument]
  (get history-status-labels
       (selected-history-status state
                                readiness
                                history-load-state
                                history-status-by-id
                                instrument)
       "pending"))

(defn universe-section-model
  ([state draft]
   (universe-section-model state draft nil))
  ([state draft {:keys [readiness
                        history-load-state
                        history-status-by-id
                        candidate-options
                        include-blank-candidates?]}]
   (let [universe (vec (or (:universe draft) []))
         history-load-state* (or history-load-state
                                 (get-in state contracts/history-load-state-path)
                                 (optimizer-defaults/default-history-load-state))
         history-status-by-id* (or history-status-by-id
                                   (when readiness
                                     (setup-readiness/history-status-by-instrument
                                      readiness))
                                   {})
         search-query (or (get-in state contracts/ui-universe-search-query-path) "")
         searching? (boolean (seq (normalized-text search-query)))
         query-candidates? (or searching?
                               include-blank-candidates?)
         markets (if query-candidates?
                   (universe-candidates/candidate-markets state
                                                          universe
                                                          search-query
                                                          candidate-options)
                   [])
         active-index (universe-candidates/active-index state markets)
         market-keys (if query-candidates?
                       (mapv :key markets)
                       [])
         assumption-context {:assumptions (or (:history-assumptions draft) {})
                             :return-required?
                             (history-assumptions/return-required-for-objective?
                              (or (get-in readiness [:request :objective :kind])
                                  (get-in draft [:objective :kind])))}
         selected-rows (mapv #(selected-row-model state
                                                  readiness
                                                  history-load-state*
                                                  history-status-by-id*
                                                  %
                                                  assumption-context)
                             universe)
         candidate-rows (mapv #(candidate-row-model %1 %2 active-index)
                              markets
                              (range))]
     {:state state
      :draft draft
      :readiness readiness
      :history-load-state history-load-state*
      :history-status-by-id history-status-by-id*
      :universe universe
      :selected-rows selected-rows
      :search-query search-query
      :searching? searching?
      :markets markets
      :candidate-rows candidate-rows
      :active-index active-index
      :market-keys market-keys})))

(defn universe-panel-model
  [state draft]
  (universe-section-model state
                          draft
                          {:candidate-options {:ranking :asset-query}
                           :include-blank-candidates? true}))
