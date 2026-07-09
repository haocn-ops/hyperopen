(ns hyperopen.portfolio.optimizer.application.view-model.setup
  "Setup-page view models: readiness/data-health projections and the policy
  summary. The history-assumption card and rail projections live in
  view-model.setup-history-assumption-cards / -rail (split 2026-07-05)."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.history-warning-policy :as warning-policy]
            [hyperopen.portfolio.optimizer.application.return-views :as return-views]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.application.view-model.universe :as universe]))

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

(def ^:private info-warning-codes
  ;; By-design notes, not problems: the run is unaffected, the user only needs
  ;; the disclosure. Rendered muted and folded behind a collapsed "data notes"
  ;; disclosure, below cautions.
  ;;
  ;; :stale-history is a NOTE, not a caution (owner review 2026-07-04): a
  ;; refresh adds at most the newest day of data, which does not move a
  ;; covariance estimate or the allocation — only an actual fetch error
  ;; (:source-fetch-failed) is worth the user's attention.
  ;; :insufficient-common-history is likewise not user-fixable here (the
  ;; provider window is what it is), so it must not read as an action item.
  ;; :excluded-from-alignment is a disclosure that an asset was left out of the
  ;; shared estimate; the ACTIONABLE follow-up (configure a proxy/conservative
  ;; assumption) already surfaces as its own caution, so this stays a note.
  #{:proxy-history-used
    :vault-derived-history-used
    :funding-history-missing
    :manual-capital-base
    :missing-market-cap-prior
    :missing-current-portfolio-prior
    :stale-history
    :insufficient-common-history
    :excluded-from-alignment})

(defn- warning-severity
  "Rank a warning group so the panel can render blocking issues, cautions, and
  informational notes distinctly instead of one undifferentiated amber wall."
  [blocking? code group]
  (cond
    blocking? :blocking
    ;; Serve-time staleness is normally an immaterial note, but once it escalates
    ;; to an incident (>= 7 days, or the backend tagged it severity error) it
    ;; signals a failing refresh pipeline and earns the user's attention.
    (and (contains? warning-policy/stale-history-warning-codes code)
         (some warning-policy/stale-incident? group))
    :caution
    (contains? info-warning-codes code) :info
    :else :caution))

(def ^:private warning-code-details
  ;; One human sentence of context per code — what the condition means for the
  ;; run — so the headline can stay short. The stale-history line is honest
  ;; about the stakes: a refresh adds at most the newest data and rarely
  ;; changes the allocation.
  {:proxy-history-used "Used when direct history is limited."
   :stale-history "Cached history is used; refreshing rarely changes the result."
   :source-fetch-failed "Refresh retries the history provider."
   :missing-market-cap-prior "The optimizer could not load market-cap baseline data for some assets."})

(defn- provider-limit-code?
  [code]
  (= :insufficient-common-history code))

(defn- warning-group-detail
  "Secondary explanation line for a group. Provider-limit warnings demote their
  raw provider message here so the headline stays human-readable."
  [readiness code group]
  (or (get warning-code-details code)
      (when (provider-limit-code? code)
        (warning-message readiness (first group)))))

(defn- warning-group-message
  [readiness code group cnt]
  (cond
    ;; The provider message ("CoinGecko Demo provider history window is capped
    ;; by provider tier.") is vendor telemetry; lead with the user's problem.
    (provider-limit-code? code)
    "History source is limited — not enough shared history across the selected assets."

    (= 1 cnt)
    (warning-message readiness (first group))

    :else
    (setup-readiness/warning-code-summary code cnt)))

(defn group-readiness-warnings
  "Group the chosen readiness warnings by :code (first-seen order) so the panel shows each KIND
  once with a count and an expandable affected-asset list, instead of repeating one row per asset
  (a 13-stale-history universe used to render 13 near-identical rows). Pure projection — it does
  not touch the raw readiness lists that history-status/assumption-cards depend on."
  [readiness]
  (let [request (:request readiness)
        blocking? (boolean (seq (:blocking-warnings readiness)))
        warnings (vec (or (seq (:blocking-warnings readiness))
                          (:warnings readiness)))
        order (distinct (map :code warnings))
        by-code (group-by :code warnings)]
    (->> order
         (mapv (fn [code]
                 (let [group (get by-code code)
                       cnt (count group)]
                   (cond-> {:code code
                            :code-label (some-> code name)
                            :count cnt
                            :severity (warning-severity blocking? code group)
                            :message (warning-group-message readiness code group cnt)
                            :assets (mapv (fn [warning]
                                            {:instrument-id (:instrument-id warning)
                                             :label (setup-readiness/warning-asset-label request warning)})
                                          group)}
                     (warning-group-detail readiness code group)
                     (assoc :detail (warning-group-detail readiness code group))
                     (warning-group-action code)
                     (assoc :action (warning-group-action code))))))
         ;; Rank by severity, then actionability: a warning the user can fix
         ;; here (Refresh history) outranks one they cannot (provider limit).
         ;; Stable sort, so first-seen order is otherwise preserved.
         (sort-by (fn [{:keys [severity action]}]
                    [(get {:blocking 0 :caution 1 :info 2} severity 1)
                     (if action 0 1)]))
         vec)))

(defn- readiness-status
  "Overall verdict for the Data health header: can the user run, run with
  cautions, or must they fix something first. :issue-count is the number of
  warning groups, so the verdict line can read \"Ready with cautions · 3
  issues\" and stay meaningful even when the cards sit below other panels."
  [readiness history-load-state warnings]
  ;; Only blocking/caution groups count as issues — informational notes are
  ;; folded away and must not inflate the verdict into looking actionable.
  (let [issue-count (count (remove #(= :info (:severity %)) warnings))]
    (cond
      (or (contains? #{:history-loading :holdings-loading} (:reason readiness))
          (= :loading (:status history-load-state)))
      {:level :loading :label "Loading…" :issue-count issue-count}

      (= :blocked (:status readiness))
      {:level :blocked :label "Action needed" :issue-count issue-count}

      (some #(= :caution (:severity %)) warnings)
      {:level :caution :label "Ready with cautions" :issue-count issue-count}

      :else
      {:level :ready :label "Ready to run" :issue-count issue-count})))

(defn readiness-panel-model
  [readiness history-load-state]
  (let [warnings (group-readiness-warnings readiness)]
    {:title "Data health"
     :status (readiness-status readiness history-load-state warnings)
     :copy (history-load-copy history-load-state readiness)
     :error-message (get-in history-load-state [:error :message])
     :warnings warnings}))

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

(def objective-display-names
  "The ONE user-facing vocabulary for objectives. \"Minimum risk\" is the
  product term everywhere the user reads; \"minimum variance\" is the method
  and stays in secondary/technical copy only."
  {:minimum-variance "Minimum risk"
   :max-sharpe "Maximum Sharpe"
   :target-volatility "Target volatility"
   :target-return "Target return"})

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
         preset (active-preset draft)
         objective-kind (get-in draft [:objective :kind])
         min-variance? (= :minimum-variance objective-kind)
         returns-label (or (returns-source-label draft)
                           (labelize* (get-in draft [:return-model :kind])))
         strip (fn [label prefix]
                 (when label (str/replace-first label prefix "")))]
     {:preset-label (get preset-display-names preset (labelize* preset))
      :asset-count (count (:universe draft))
      :universe-source-kind (get-in draft [:metadata :universe-source :kind])
      :objective-label (get objective-display-names objective-kind
                            (labelize* objective-kind))
      :returns-label returns-label
      ;; Minimum variance does not consume expected returns at all: saying
      ;; "Historical mean" under Returns implies the forecast drives the result.
      :return-forecast-label (if min-variance? "Not used" returns-label)
      :views-active? (= :black-litterman (get-in draft [:return-model :kind]))
      :min-variance? min-variance?
      :exposure-rows
      (filterv (fn [[_ value]] (some? value))
               [["Gross" (strip (gross-range-label constraints) "Gross ")]
                ["Net" (strip (net-range-label constraints) "Net ")]
                ["Max asset" (strip (cap-label (:max-asset-weight constraints)) "Max asset ")]
                ["Rebalance" (strip (band-label (:rebalance-tolerance constraints)) "Rebalance ")]])
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
