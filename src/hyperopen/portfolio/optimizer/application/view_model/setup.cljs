(ns hyperopen.portfolio.optimizer.application.view-model.setup
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.return-views :as return-views]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.application.view-model.universe :as universe]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.domain.history-assumptions :as history-assumptions]))

(defn- warning-code-label
  [warning]
  (some-> (:code warning) name))

(defn- warning-message
  [readiness warning]
  (or (:message warning)
      (setup-readiness/warning-display-message (:request readiness) warning)
      (warning-code-label warning)))

(defn- readiness-copy
  [readiness]
  (case (:reason readiness)
    :missing-universe "Select a universe before running."
    :holdings-loading "Waiting for your holdings snapshot — the universe fills itself when account data arrives."
    :no-eligible-history "History starts loading as assets are included. Run Optimization retries anything still missing."
    :incomplete-history "History is incomplete for this universe. Run Optimization retries anything still missing."
    :missing-history-assumptions "Some assets need history assumptions before this universe can run."
    :history-loading "History is loading for the selected assets."
    "Optimizer inputs are ready to run."))

(defn- history-load-copy
  [history-load-state readiness]
  (case (:status history-load-state)
    :loading "Loading optimizer history for the selected assets."
    :succeeded "Optimizer history is loaded for the selected assets."
    :failed "History load failed. Existing history, if any, is retained."
    (readiness-copy readiness)))

(defn- warning-group-action
  "Remediation for a warning group, so warnings tell the user what to do next
  instead of only describing the problem. Stale/failed-fetch history is fixable
  in-app: a full history load refetches the bundle at a fresh as-of."
  [code]
  (when (contains? setup-readiness/stale-history-warning-codes code)
    {:label "Refresh history"
     :actions [[:actions/load-portfolio-optimizer-history-from-draft]]}))

(defn group-readiness-warnings
  "Group the chosen readiness warnings by :code (first-seen order) so the panel shows each KIND
  once with a count and an expandable affected-asset list, instead of repeating one row per asset
  (a 13-stale-history universe used to render 13 near-identical rows). Pure projection — it does
  not touch the raw readiness lists that history-status/assumption-cards depend on."
  [readiness]
  (let [request (:request readiness)
        warnings (vec (or (seq (:blocking-warnings readiness))
                          (:warnings readiness)))
        order (distinct (map :code warnings))
        by-code (group-by :code warnings)]
    (mapv (fn [code]
            (let [group (get by-code code)
                  cnt (count group)]
              (cond-> {:code code
                       :code-label (some-> code name)
                       :count cnt
                       :message (if (= 1 cnt)
                                  (warning-message readiness (first group))
                                  (setup-readiness/warning-code-summary code cnt))
                       :assets (mapv (fn [warning]
                                       {:instrument-id (:instrument-id warning)
                                        :label (setup-readiness/warning-asset-label request warning)})
                                     group)}
                (warning-group-action code)
                (assoc :action (warning-group-action code)))))
          order)))

(defn readiness-panel-model
  [readiness history-load-state]
  {:title "Readiness"
   :copy (history-load-copy history-load-state readiness)
   :error-message (get-in history-load-state [:error :message])
   :warnings (group-readiness-warnings readiness)})

(defn- title-case-token
  [token]
  (if (seq token)
    (str (str/upper-case (subs token 0 1))
         (subs token 1))
    token))

(defn- default-labelize
  [value]
  (cond
    (keyword? value)
    (->> (str/split (name value) #"-")
         (map title-case-token)
         (str/join " "))

    (some? value)
    (str value)

    :else
    "--"))

(defn- apply-labelize
  [formatter value]
  (cond
    (nil? value)
    "--"

    (fn? formatter)
    (formatter value)

    (map? formatter)
    (get formatter value (default-labelize value))

    :else
    (default-labelize value)))

(defn- apply-percent-label
  [formatter value]
  (if (fn? formatter)
    (formatter value)
    (str value)))

(declare constraints-summary-line)

(defn- active-preset
  ;; Two presets: a views-aware (Black-Litterman) return model belongs to
  ;; Maximum Sharpe — views are an input policy, not a strategy.
  [draft]
  (let [objective-kind (get-in draft [:objective :kind])
        return-kind (get-in draft [:return-model :kind])]
    (if (or (= :black-litterman return-kind)
            (= :max-sharpe objective-kind))
      :max-sharpe
      :conservative)))

(def ^:private preset-display-names
  {:conservative "Conservative"
   :max-sharpe "Maximum Sharpe"})

(defn- universe-label
  [instrument]
  (if (= :vault (:market-type instrument))
    (universe/instrument-primary-label instrument)
    (or (:coin instrument)
        (universe/instrument-primary-label instrument))))

(defn- universe-summary
  [draft]
  (let [universe (vec (:universe draft))
        labels (->> universe
                    (keep universe-label)
                    (take 5)
                    (str/join ", "))]
    (str (count universe) " assets"
         (when (seq labels) (str " - " labels)))))

(defn- summary-row
  [label title copy]
  {:label label
   :title title
   :copy copy})

(defn setup-summary-model
  ([draft]
   (setup-summary-model draft nil))
  ([draft {:keys [labelize percent-label]}]
   (let [preset (active-preset draft)
         objective-kind (get-in draft [:objective :kind])
         return-kind (get-in draft [:return-model :kind])
         constraints (:constraints draft)
         labelize* #(apply-labelize labelize %)
         bl? (= :black-litterman return-kind)]
     {:active-preset preset
      :black-litterman? bl?
      :summary-rows
      [(summary-row "Preset" (labelize* preset)
                    "You can deviate from the preset below without changing the universe.")
       (summary-row "Universe" (universe-summary draft)
                    "Selected instruments are optimized as one cross-margin book.")
       (summary-row "Expected Returns" (labelize* return-kind)
                    "Funding-adjusted return assumptions are kept separate from covariance.")
       (summary-row "Objective" (labelize* objective-kind)
                    "Objective remains separate from return model selection.")
       (summary-row "Portfolio exposure"
                    (constraints-summary-line constraints)
                    "Exposure limits are enforced before the recommendation is accepted.")
       (summary-row "Horizon" "Annualized"
                    "Displayed return and volatility metrics use the optimizer annualization convention.")]})))

(defn- fmt-mult
  [x]
  (when (number? x)
    (str (.toFixed x 2) "×")))

(defn- fmt-abs-mult
  [x]
  (when (number? x)
    (str (.toFixed (js/Math.abs x) 2) "×")))

(defn- fmt-signed-mult
  [x]
  (when (number? x)
    (str (cond
           (pos? x) "+"
           (neg? x) "-"
           :else "")
         (.toFixed (js/Math.abs x) 2)
         "×")))

(defn gross-range-label
  [{:keys [gross-min gross-max]}]
  (if (number? gross-min)
    (str "Gross " (fmt-mult gross-min) "–" (fmt-mult gross-max))
    (str "Gross ≤ " (or (fmt-mult gross-max) "--"))))

(defn- net-range-direction
  [net-min net-max]
  (cond
    (and (zero? net-min) (zero? net-max)) :neutral
    (and (not (neg? net-min)) (pos? net-max)) :long
    (and (neg? net-min) (not (pos? net-max))) :short
    :else :neutral-range))

(defn- net-direction-copy
  [direction]
  (case direction
    :long "long"
    :short "short"
    :neutral "neutral"
    :neutral-range "neutral range"
    nil))

(defn- net-range-copy
  [net-min net-max]
  (let [direction (net-range-direction net-min net-max)
        direction-copy (net-direction-copy direction)]
    (cond
      (= net-min net-max)
      (str (fmt-signed-mult net-min) " " direction-copy)

      (= direction :long)
      (str (fmt-signed-mult net-min) "–" (fmt-abs-mult net-max) " " direction-copy)

      (= direction :short)
      (str (fmt-signed-mult net-min) "–" (fmt-abs-mult net-max) " " direction-copy)

      :else
      (str (fmt-signed-mult net-min) "–" (fmt-signed-mult net-max) " " direction-copy))))

(defn net-range-label
  [{:keys [net-min net-max]}]
  (cond
    (and (number? net-min) (number? net-max))
    (str "Net " (net-range-copy net-min net-max))

    (number? net-max) (str "Net ≤ " (fmt-signed-mult net-max))
    (number? net-min) (str "Net ≥ " (fmt-signed-mult net-min))
    :else nil))

(defn- cap-label
  [cap]
  (when (number? cap)
    (str "Max asset " (js/Math.round (* 100 cap)) "%")))

(defn- band-label
  [tolerance]
  (when (number? tolerance)
    (str "Rebalance " (.toFixed (* 100 tolerance) 1) " pp")))

(defn constraints-summary-line
  "One scannable line of the live exposure-policy numbers (\"Gross 1.90–1.91× ·
  Net +1.30×–1.41× long · Max asset 50% · Rebalance 3.0 pp\") shared by the
  Portfolio exposure header and the scenario-contract card."
  [constraints]
  (->> [(gross-range-label constraints)
        (net-range-label constraints)
        (cap-label (:max-asset-weight constraints))
        (band-label (:rebalance-tolerance constraints))]
       (keep identity)
       (str/join " · ")))

(def return-model-display-names
  "Human names for return-model kinds, shared by the model-section header and the
  scenario-contract card so both say \"Historical mean\", not \"Historical-mean\"."
  {:historical-mean "Historical mean"
   :ew-mean "EW mean"
   :black-litterman "Views + implied baseline"})

(def risk-model-display-names
  {:diagonal-shrink "Stabilized covariance"
   :ledoit-wolf-dense "Ledoit-Wolf"
   :mixed-frequency "Mixed frequency"
   :sample-covariance "Sample covariance"})

(defn- returns-source-label
  "Honest returns-source line for the scenario contract. For the views-aware
  model it reports the live provenance split (\"2 your views · 12 implied\");
  the estimator-only models name the estimate."
  [draft]
  (let [return-kind (get-in draft [:return-model :kind])]
    (if (= :black-litterman return-kind)
      (return-views/returns-contract-label
       (return-views/summary
        (return-views/rows {:universe (:universe draft)
                            :views (get-in draft [:return-model :views])})))
      (get return-model-display-names return-kind))))

(defn setup-summary-card-model
  "Scenario-contract card for the right column: universe (count + source),
  objective, returns source, risk model, and the live constraint numbers — the
  exact policy the solver receives. Derived output, not primary input."
  ([draft] (setup-summary-card-model draft nil))
  ([draft {:keys [labelize]}]
   (let [constraints (:constraints draft)
         labelize* #(apply-labelize labelize %)
         preset (active-preset draft)]
     {:preset-label (get preset-display-names preset (labelize* preset))
      :asset-count (count (:universe draft))
      :universe-source-kind (get-in draft [:metadata :universe-source :kind])
      :objective-label (labelize* (get-in draft [:objective :kind]))
      :returns-label (or (returns-source-label draft)
                         (labelize* (get-in draft [:return-model :kind])))
      :return-label (or (get return-model-display-names
                             (get-in draft [:return-model :kind]))
                        (labelize* (get-in draft [:return-model :kind])))
      :risk-label (or (get risk-model-display-names
                           (get-in draft [:risk-model :kind]))
                      (labelize* (get-in draft [:risk-model :kind])))
      :gross-min (:gross-min constraints)
      :gross-max (:gross-max constraints)
      :net-min (:net-min constraints)
      :net-max (:net-max constraints)
      :cap (:max-asset-weight constraints)
      :rebalance-tolerance (:rebalance-tolerance constraints)
      :constraints-line (constraints-summary-line constraints)})))

;; --- History-assumption cards ------------------------------------------------
;;
;; Views render these rows and dispatch the carried action ids; they never touch
;; the raw draft or raw history. A card is shown for any selected asset that is
;; missing/short on history or that the user has already started configuring.

(def ^:private mode-options
  [{:value :conservative :label "Use a conservative assumption"}])

(def ^:private mode-labels
  {:conservative "Conservative assumption"})

(def ^:private assumption-action-ids
  {:set-mode :actions/set-portfolio-optimizer-history-assumption-mode
   :set-expected-return :actions/set-portfolio-optimizer-history-assumption-expected-return
   :set-expected-volatility :actions/set-portfolio-optimizer-history-assumption-expected-volatility
   :set-max-weight-cap :actions/set-portfolio-optimizer-history-assumption-max-weight-cap
   :clear :actions/clear-portfolio-optimizer-history-assumption})

(def ^:private card-needing-adequacy
  ;; universe/history-adequacy values that warrant a card: no usable history, or
  ;; thin-but-present history below the user-facing short threshold. :pending (still
  ;; loading) and :ok (enough history) are excluded.
  #{:none :short})

(defn- percent-field
  [percent-label* value]
  {:value value
   :percent-label (when (some? value) (percent-label* value))
   ;; editable percent text (decimal 0.25 -> "25"); the action parses it back.
   :input-text (when (some? value) (coercion/decimal->percent-text value))})

(defn- card-status
  [entry complete?]
  (cond
    (nil? (:behavior entry)) :needs-assumptions
    complete? :complete
    :else :incomplete))

(def ^:private card-status-labels
  {:needs-assumptions "Excluded - needs assumption"
   :incomplete "Needs assumptions"
   :complete "Conservative"})

(defn- card-summary
  [entry percent-label*]
  (let [vol (some-> (:volatility entry) percent-label*)
        cap (some-> (:max-weight entry) percent-label*)]
    (when (= :conservative (:behavior entry))
      (str "conservative" (when vol (str " - " vol " vol"))
           (when cap (str " - " cap " cap"))))))

(defn- history-assumption-card
  [{:keys [instrument label entry complete? errors note percent-label*]}]
  (let [id (:instrument-id instrument)
        behavior (:behavior entry)
        status (card-status entry complete?)]
    {:instrument-id id
     :label label
     :role (str "portfolio-optimizer-history-assumption-card-" id)
     :status status
     :status-label (get card-status-labels status "Needs assumptions")
     :mode behavior
     :mode-label (get mode-labels behavior)
     :mode-options mode-options
     :expected-return (percent-field percent-label* (:expected-return entry))
     :volatility (percent-field percent-label* (:volatility entry))
     :max-weight (percent-field percent-label* (:max-weight entry))
     :errors (vec errors)
     :note note
     :engine-applied? (boolean complete?)
     :summary (when complete? (card-summary entry percent-label*))
     :actions assumption-action-ids}))

(defn history-assumption-cards
  ([state draft readiness history-load-state]
   (history-assumption-cards state draft readiness history-load-state nil))
  ([state draft readiness history-load-state {:keys [percent-label]}]
   (let [universe (vec (or (get-in readiness [:request :requested-universe])
                           (:universe draft)
                           []))
         assumptions (or (:history-assumptions draft) {})
         history-status-by-id (if readiness
                                (setup-readiness/history-status-by-instrument readiness)
                                {})
         load-state (or history-load-state {})
         objective-kind (or (get-in readiness [:request :objective :kind])
                            (get-in draft [:objective :kind]))
         return-required? (history-assumptions/return-required-for-objective? objective-kind)
         percent-label* #(apply-percent-label percent-label %)
         warnings-by-id (group-by :instrument-id (:blocking-warnings readiness))
         cards (->> universe
                    (keep (fn [instrument]
                            (let [id (:instrument-id instrument)
                                  entry (get assumptions id)
                                  ;; Same adequacy signal as the universe-row badge:
                                  ;; :pending until history loads (so a historied asset
                                  ;; never flashes a card), and :short for thin-but-
                                  ;; present history below the user-facing threshold.
                                  status (universe/selected-history-status
                                          state readiness load-state
                                          history-status-by-id instrument)
                                  adequacy (universe/history-adequacy status state instrument)]
                              (when (and id
                                         (or (some? entry)
                                             (contains? card-needing-adequacy adequacy)))
                                (let [complete? (and entry
                                                     (history-assumptions/assumption-complete?
                                                      entry return-required?))
                                      errors (keep :message (get warnings-by-id id))]
                                  (history-assumption-card
                                   {:instrument instrument
                                    :label (universe/instrument-primary-label instrument)
                                    :entry entry
                                    :complete? complete?
                                    :errors errors
                                    :note nil
                                    :percent-label* percent-label*}))))))
                    vec)]
     {:cards cards
      :applicable? (boolean (seq cards))})))
