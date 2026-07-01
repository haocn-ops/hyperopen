(ns hyperopen.portfolio.optimizer.application.view-model.setup
  (:require [clojure.string :as str]
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
    :no-eligible-history "History starts loading as assets are included. Run Optimization retries anything still missing."
    :incomplete-history "History is incomplete for this universe. Run Optimization retries anything still missing."
    :missing-history-assumptions "Some assets need history assumptions before this universe can run."
    :history-loading "History is loading for the selected assets."
    :missing-black-litterman-views "Add a view before running Use my views."
    "Optimizer inputs are ready to run."))

(defn- history-load-copy
  [history-load-state readiness]
  (case (:status history-load-state)
    :loading "Loading optimizer history for the selected assets."
    :succeeded "Optimizer history is loaded for the selected assets."
    :failed "History load failed. Existing history, if any, is retained."
    (readiness-copy readiness)))

(defn readiness-panel-model
  [readiness history-load-state]
  (let [warnings (vec (or (seq (:blocking-warnings readiness))
                          (:warnings readiness)))]
    {:title "Readiness"
     :copy (history-load-copy history-load-state readiness)
     :error-message (get-in history-load-state [:error :message])
     :warnings (mapv (fn [warning]
                       {:message (warning-message readiness warning)
                        :code-label (warning-code-label warning)})
                     warnings)}))

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

(defn- active-preset
  [draft]
  (let [objective-kind (get-in draft [:objective :kind])
        return-kind (get-in draft [:return-model :kind])]
    (cond
      (= :black-litterman return-kind) :use-my-views
      (= :max-sharpe objective-kind) :risk-adjusted
      :else :conservative)))

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
         percent-label* #(apply-percent-label percent-label %)
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
       (summary-row "Constraints"
                    (str "gross "
                         (if (:gross-min constraints)
                           (str (:gross-min constraints) ".." (or (:gross-max constraints) "--"))
                           (str "<= " (or (:gross-max constraints) "--")))
                         " - cap <= " (percent-label* (:max-asset-weight constraints)))
                    "Constraints are enforced before the recommendation is accepted.")
       (summary-row "Horizon" "Annualized"
                    "Displayed return and volatility metrics use the optimizer annualization convention.")]})))

(defn setup-summary-card-model
  "Compact one-line scenario summary for the right column: preset, asset count, objective, return
  model, and the exposure/cap numbers. Derived output, not primary input — kept small so it does
  not compete with the center policy controls."
  ([draft] (setup-summary-card-model draft nil))
  ([draft {:keys [labelize]}]
   (let [constraints (:constraints draft)
         labelize* #(apply-labelize labelize %)]
     {:preset-label (labelize* (active-preset draft))
      :asset-count (count (:universe draft))
      :objective-label (labelize* (get-in draft [:objective :kind]))
      :return-label (labelize* (get-in draft [:return-model :kind]))
      :gross-min (:gross-min constraints)
      :gross-max (:gross-max constraints)
      :net-min (:net-min constraints)
      :net-max (:net-max constraints)
      :cap (:max-asset-weight constraints)})))

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
