(ns hyperopen.portfolio.optimizer.application.setup-readiness
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.current-portfolio :as current-portfolio]
            [hyperopen.portfolio.optimizer.application.orderbook-loader :as orderbook-loader]
            [hyperopen.portfolio.optimizer.application.request-builder :as request-builder]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.domain.history-assumptions :as history-assumptions]
            [hyperopen.portfolio.optimizer.domain.risk :as risk]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def ^:private history-blocking-warning-codes
  #{:missing-history-coin
    :missing-candle-history
    :missing-return-history
    :insufficient-candle-history
    :insufficient-return-history
    :missing-vault-address
    :missing-vault-history
    :insufficient-vault-history
    :insufficient-common-history
    :missing-native-risk-history
    :identity-ambiguous
    :instrument-kind-mismatch
    :proxy-mapping-unapproved
    :proxy-validation-failed
    :validation-failed
    :history-assumption-required
    :history-assumption-incomplete})

(def ^:private history-assumption-warning-codes
  #{:history-assumption-required
    :history-assumption-incomplete})

(def ^:private missing-history-warning-codes
  #{:missing-history-coin
    :missing-candle-history
    :missing-return-history
    :missing-vault-address
    :missing-vault-history
    :missing-native-risk-history
    :identity-ambiguous})

(def ^:private insufficient-history-warning-codes
  #{:insufficient-candle-history
    :insufficient-return-history
    :insufficient-vault-history
    :insufficient-common-history})

(def ^:private stale-history-warning-codes
  #{:stale-history
    :source-fetch-failed})

(def ^:private rejected-history-warning-codes
  #{:instrument-kind-mismatch
    :proxy-mapping-unapproved
    :proxy-validation-failed
    :rejected
    :validation-failed})

(def ^:private fallback-as-of-bucket-ms
  ;; Quantize the wall-clock fallback so request building can memoize across
  ;; streaming renders. Staleness windows are minutes long, so a five second
  ;; as-of granularity is immaterial to readiness output.
  5000)

(defn- current-as-of-ms
  [state]
  (or (get-in state contracts/runtime-as-of-ms-path)
      (let [now-ms (.now js/Date)]
        (- now-ms (mod now-ms fallback-as-of-bucket-ms)))))

(def ^:private positive-number? coercion/positive-number?)

(def ^:private non-blank-text coercion/non-blank-text)

(defn- vault-ref?
  [value]
  (ids/vault-instrument-id? value))

(defn- with-manual-capital
  [snapshot draft]
  (let [manual-capital (get-in draft [:execution-assumptions :manual-capital-usdc])]
    (if (positive-number? manual-capital)
      (-> snapshot
          (assoc :capital-ready? true)
          (assoc-in [:capital :nav-usdc] manual-capital)
          (assoc-in [:capital :source] :manual)
          (assoc-in [:capital :manual-capital-usdc] manual-capital)
          (update :warnings
                  #(conj (vec %)
                         {:code :manual-capital-base
                          :message "Manual capital base is being used for preview sizing."})))
      snapshot)))

(defonce ^:private build-request-memo
  (volatile! nil))

(defn- build-request
  ;; Request building re-aligns the full instrument history, which is far too
  ;; expensive to redo on every streaming render. The snapshot is memoized on
  ;; its own inputs, so comparing these resolved inputs by value stays cheap.
  [state draft]
  (let [inputs {:draft draft
                :current-portfolio (with-manual-capital
                                     (current-portfolio/current-portfolio-snapshot state)
                                     draft)
                :history-data (get-in state contracts/history-data-path)
                :market-cap-by-coin (get-in state contracts/market-cap-by-coin-path)
                :as-of-ms (current-as-of-ms state)
                :stale-after-ms (get-in state contracts/runtime-stale-after-ms-path)
                :funding-periods-per-year
                (get-in state contracts/runtime-funding-periods-per-year-path)
                ;; A user-triggered refinement re-runs at a higher frontier density. The
                ;; budget is injected onto the solved objective only while a refinement is
                ;; active; it is stripped from the input-signature so it never reads as
                ;; stale relative to the draft (see contracts.signatures/stable-objective).
                :frontier-points-override
                (when (get-in state contracts/refinement-active-path)
                  (get-in state contracts/refinement-requested-points-path))}
        cached @build-request-memo]
    (if (and cached (= (:inputs cached) inputs))
      (:value cached)
      (let [value (request-builder/build-engine-request inputs)]
        (vreset! build-request-memo {:inputs inputs :value value})
        value))))

(defn- orderbook-cost-contexts
  [state request]
  (let [fallback-bps (get-in request [:execution-assumptions :fallback-slippage-bps])
        opts {:now-ms (:as-of-ms request)
              :fallback-bps fallback-bps
              :stale-after-ms (or (get-in state
                                          contracts/runtime-orderbook-stale-after-ms-path)
                                  orderbook-loader/default-stale-after-ms)}]
    (into {}
          (map (fn [{:keys [instrument-id coin]}]
                 [instrument-id
                  (dissoc (orderbook-loader/orderbook-cost-context state coin opts)
                          :coin)]))
          (:universe request))))

(defn- with-cost-contexts
  [state request]
  (let [generated-contexts (orderbook-cost-contexts state request)
        existing-contexts (get-in request [:execution-assumptions :cost-contexts-by-id])]
    (assoc-in request
              [:execution-assumptions :cost-contexts-by-id]
              (merge existing-contexts generated-contexts))))

(defn- native-mark-price
  "Live native mark for a market-catalog entry, preferring the precise :markRaw string over the
   display-rounded :mark. Returns nil when neither is a positive number."
  [entry]
  (let [px (or (coercion/parse-float-number (:markRaw entry))
               (coercion/parse-float-number (:mark entry)))]
    (when (coercion/positive-number? px) px)))

(defn- native-marks-by-id
  "Map of optimizer instrument-id -> live native mark, resolved from the global market catalog
   ([:asset-selector :market-by-key]). A HIP-3 perp prices its RETURNS off an equity proxy
   (e.g. xyz:BABA -> the Tiingo Alibaba ADR), so its last history close is the proxy level
   (~112) and not the native perp mark (~95); seeding the execution price from the native mark
   keeps the rebalance preview's order sizing and cost estimate honest. Each instrument is
   resolved by its OWN catalog key — its instrument-id, falling back to a market-type-qualified
   coin key (\"<market-type>:<coin>\") for doubled-prefix ids like \"perp:xyz:xyz:SILVER\" whose
   id does not equal the catalog key \"perp:xyz:SILVER\". Resolving per market-type means a spot
   and a perp sharing a base token never collide onto one another's price."
  [state universe]
  (let [market-by-key (get-in state [:asset-selector :market-by-key])]
    (into {}
          (keep (fn [{:keys [instrument-id coin market-type]}]
                  (when-let [entry (or (get market-by-key instrument-id)
                                       (when (and market-type (non-blank-text coin))
                                         (get market-by-key
                                              (str (name market-type) ":" coin))))]
                    (when-let [px (native-mark-price entry)]
                      [instrument-id px]))))
          universe)))

(defn- with-native-marks
  "Seed [:execution-assumptions :prices-by-id] with live native marks for the eligible universe
   so the rebalance preview prices execution against the native mark rather than an equity-proxy
   daily close. Any explicit price already present wins, and the solver is unaffected (only the
   preview reads :prices-by-id)."
  [state request]
  (let [marks (native-marks-by-id state (:universe request))]
    (cond-> request
      (seq marks)
      (update-in [:execution-assumptions :prices-by-id] #(merge marks %)))))

(defn- instrument-ids
  [instruments]
  (set (keep :instrument-id instruments)))

(defn- incomplete-history?
  [requested-universe request]
  (not= (instrument-ids requested-universe)
        (instrument-ids (:universe request))))

(defn- missing-black-litterman-views?
  [request]
  (and (= :black-litterman (get-in request [:return-model :kind]))
       (empty? (get-in request [:return-model :views]))))

(defn- requested-instrument-by-id
  [request]
  (into {}
        (mapcat (fn [instrument]
                  (cond-> [[(:instrument-id instrument) instrument]]
                    (:optimizer-history/instrument-id instrument)
                    (conj [(:optimizer-history/instrument-id instrument)
                           instrument]))))
        (:requested-universe request)))

(defn- warning-instrument
  [request warning]
  (get (requested-instrument-by-id request) (:instrument-id warning)))

(defn warning-asset-label
  [request warning]
  (let [instrument (warning-instrument request warning)
        coin (non-blank-text (:coin instrument))]
    (or (non-blank-text (:name instrument))
        (non-blank-text (:symbol instrument))
        (when-not (vault-ref? coin) coin)
        (non-blank-text (:vault-address instrument))
        (non-blank-text (:vault-address warning))
        (non-blank-text (:instrument-id warning))
        "Selected asset")))

(defn warning-code-summary
  "A code-level, count-aware sentence for a GROUP of same-code warnings (the grouped readiness
  panel shows this once instead of repeating a per-asset message N times)."
  [code count]
  (let [n count
        assets (str n (if (= 1 n) " asset" " assets"))]
    (cond
      (contains? stale-history-warning-codes code)
      (str assets (if (= 1 n) " uses" " use") " stale cached history")

      (contains? insufficient-history-warning-codes code)
      (str assets (if (= 1 n) " has" " have") " insufficient common history")

      :else
      (str assets " · " (str/replace (name code) "-" " ")))))

(defn- observation-count-message
  [warning noun]
  (if (and (some? (:observations warning))
           (some? (:required warning)))
    (str "only " (:observations warning) " usable " noun
         " observations; " (:required warning) " required.")
    (str "not enough usable " noun " observations.")))

(defn warning-display-message
  [request warning]
  (let [label (warning-asset-label request warning)]
    (case (:code warning)
      :missing-history-coin
      (str label ": missing coin metadata needed to fetch history.")

      :missing-candle-history
      (str label ": no candle history returned"
           (if-let [coin (non-blank-text (:coin warning))]
             (str " for " coin ".")
             "."))

      :missing-return-history
      (str label ": no optimizer return history returned.")

      :insufficient-candle-history
      (str label ": " (observation-count-message warning "candle"))

      :insufficient-return-history
      (str label ": " (observation-count-message warning "optimizer return"))

      :missing-vault-address
      (str label ": missing vault address needed to fetch vault details.")

      :missing-vault-history
      (str label ": vault details returned no usable return history.")

      :insufficient-vault-history
      (str label ": " (observation-count-message warning "vault return"))

      :insufficient-common-history
      (observation-count-message warning "shared return")

      :missing-native-risk-history
      (str label ": no native optimizer price history returned for mixed-frequency risk.")

      :identity-ambiguous
      (str label ": missing backend optimizer history identity.")

      :instrument-kind-mismatch
      (str label ": backend optimizer history identity does not match the selected asset type.")

      :proxy-mapping-unapproved
      (str label ": proxy history is not approved for optimizer use.")

      :proxy-validation-failed
      (str label ": proxy history failed backend validation.")

      :validation-failed
      (str label ": backend validation rejected optimizer history.")

      :rejected
      (str label ": backend rejected optimizer history.")

      :proxy-history-used
      (str label ": approved proxy history is included.")

      :vault-derived-history-used
      (str label ": row uses vault return-index history, not market candles.")

      :funding-history-missing
      (str label ": funding history is missing; price history availability is separate.")

      :stale-history
      (str label ": optimizer history may be stale.")

      :source-fetch-failed
      (str label ": source refresh failed; cached optimizer history may be stale.")

      :history-assumption-required
      (str label " needs a history assumption. Add a conservative assumption, or remove the asset.")

      :history-assumption-incomplete
      (str label
           (case (:missing warning)
             :volatility " needs an expected annual volatility."
             :expected-return " needs an expected annual return for this objective."
             :max-weight " needs a max weight cap."
             " needs more history-assumption details."))

      (or (some-> (:code warning) name)
          "Optimizer warning."))))

(defn- with-warning-messages
  [request warnings]
  (mapv (fn [warning]
          (assoc warning :message (warning-display-message request warning)))
        warnings))

(defn- first-missing-assumption-field
  [entry return-required?]
  (cond
    (not (positive-number? (:volatility entry)))
    :volatility

    (and return-required? (not (coercion/finite-number? (:expected-return entry))))
    :expected-return

    (not (positive-number? (:max-weight entry)))
    :max-weight

    :else nil))

(defn- history-assumption-warning
  [entry return-required? instrument-id]
  ;; A conservative assumption is chosen but required fields are still missing.
  {:code :history-assumption-incomplete
   :instrument-id instrument-id
   :behavior (:behavior entry)
   :missing (first-missing-assumption-field entry return-required?)})

(defn- history-assumption-warnings
  "Honest guidance for dropped assets the user has started configuring. Assets with
  no assumption draft are left to the existing incomplete-history path."
  [requested-universe request draft]
  (let [aligned-ids (instrument-ids (:universe request))
        return-required? (history-assumptions/return-required-for-objective?
                          (get-in request [:objective :kind]))
        assumptions (:history-assumptions draft)]
    (->> requested-universe
         (keep (fn [instrument]
                 (let [id (:instrument-id instrument)
                       entry (get assumptions id)]
                   (when (and id
                              (not (contains? aligned-ids id))
                              (some? (:behavior entry)))
                     (history-assumption-warning entry return-required? id)))))
         vec)))

(defn- blocking-history-warnings
  [requested-universe request]
  (let [missing-ids (set/difference (instrument-ids requested-universe)
                                    (instrument-ids (:universe request)))
        warnings (filter (fn [warning]
                           (or (contains? missing-ids (:instrument-id warning))
                               (contains? history-blocking-warning-codes
                                          (:code warning))))
                         (:warnings request))
        ;; Prefer actionable assumption guidance over the lower-level raw history
        ;; warnings for the same asset.
        assumption-ids (set (keep (fn [warning]
                                    (when (contains? history-assumption-warning-codes
                                                     (:code warning))
                                      (:instrument-id warning)))
                                  warnings))]
    (->> warnings
         (remove (fn [warning]
                   (and (contains? assumption-ids (:instrument-id warning))
                        (not (contains? history-assumption-warning-codes
                                        (:code warning))))))
         (with-warning-messages request))))

(defn- warning-history-status
  [warning]
  (cond
    (contains? missing-history-warning-codes (:code warning))
    :missing

    (contains? insufficient-history-warning-codes (:code warning))
    :insufficient

    (contains? stale-history-warning-codes (:code warning))
    :stale

    (contains? rejected-history-warning-codes (:code warning))
    :rejected

    :else
    nil))

(defn history-status-by-instrument
  [readiness]
  (let [request (:request readiness)
        requested-ids (mapv :instrument-id (:requested-universe request))
        aligned-ids (instrument-ids (:universe request))
        warnings (vec (concat (or (:warnings request) [])
                              (or (:warnings readiness) [])))
        common-gap? (boolean (some #(= :insufficient-common-history (:code %))
                                   warnings))
        warning-status-by-id (into {}
                                   (keep (fn [warning]
                                           (when-let [instrument-id (:instrument-id warning)]
                                             (when-let [status (warning-history-status warning)]
                                               [instrument-id status]))))
                                   warnings)]
    (into {}
          (map (fn [instrument-id]
                 [instrument-id
                  (or (get warning-status-by-id instrument-id)
                      (when (contains? aligned-ids instrument-id)
                        :aligned)
                      (when common-gap?
                        :loaded-but-misaligned)
                      :missing)]))
          requested-ids)))

(defn current-portfolio-history-load-needed?
  [readiness]
  (let [request (:request readiness)
        current-history (:current-portfolio-history request)
        current-ids (instrument-ids (:current-portfolio-universe request))
        selected-ids (instrument-ids (:requested-universe request))
        ;; A holding counts as covered when it aligned OR when the last
        ;; current-portfolio fetch already requested it; holdings with no
        ;; usable backend history must not force a reload on every run.
        covered-ids (set/union (instrument-ids
                                (:eligible-instruments current-history))
                               (set (:requested-instrument-ids current-history)))]
    (and (:runnable? readiness)
         (seq current-ids)
         (not (set/subset? current-ids selected-ids))
         (not (set/subset? current-ids covered-ids)))))

(defn readiness-error-message
  [readiness]
  (let [details (seq (distinct (keep :message (:blocking-warnings readiness))))
        with-details (fn [fallback prefix]
                       (if details
                         (str prefix ": " (str/join " " details))
                         fallback))]
    (case (:reason readiness)
      :missing-universe "Select a universe before running."
      :history-loading "Optimizer history is already loading."
      :missing-black-litterman-views "Add a view before running Use my views."
      :no-eligible-history
      (with-details "No eligible history was available for this universe."
                    "No eligible history was available")
      :incomplete-history
      (with-details "History is incomplete for this universe."
                    "History is incomplete")
      :missing-history-assumptions
      (with-details "Some assets need history assumptions before running."
                    "History assumptions needed")
      "Optimizer inputs are not ready to run.")))

(defn build-readiness
  [state]
  (let [draft (get-in state contracts/draft-path)
        requested-universe (vec (or (:universe draft) []))
        history-loading? (= :loading
                            (get-in state contracts/history-load-state-status-path))]
    (if (empty? requested-universe)
      {:status :blocked
       :reason :missing-universe
       :runnable? false
       :request nil
       :warnings []}
      (let [request (with-native-marks state
                      (with-cost-contexts state (build-request state draft)))
            risk-blocking-warnings (risk/missing-native-risk-history-warnings
                                    {:risk-model (:risk-model request)
                                     :history (:history request)})
            request (cond-> request
                      (seq risk-blocking-warnings)
                      (update :warnings into risk-blocking-warnings))
            assumption-warnings (history-assumption-warnings requested-universe
                                                            request
                                                            draft)
            request (cond-> request
                      (seq assumption-warnings)
                      (update :warnings into assumption-warnings))
            eligible? (boolean (seq (:universe request)))
            incomplete? (incomplete-history? requested-universe request)
            risk-history-incomplete? (boolean (seq risk-blocking-warnings))
            assumptions-flagged? (boolean (seq assumption-warnings))
            missing-bl-views? (missing-black-litterman-views? request)
            runnable? (and eligible?
                           (not incomplete?)
                           (not risk-history-incomplete?)
                           (not assumptions-flagged?)
                           (not history-loading?)
                           (not missing-bl-views?))
            blocking-warnings (if (or (not eligible?)
                                      incomplete?
                                      risk-history-incomplete?
                                      assumptions-flagged?)
                                (blocking-history-warnings requested-universe
                                                          request)
                                [])]
        {:status (if runnable? :ready :blocked)
         :reason (cond
                   history-loading? :history-loading
                   assumptions-flagged? :missing-history-assumptions
                   (not eligible?) :no-eligible-history
                   (or incomplete? risk-history-incomplete?) :incomplete-history
                   missing-bl-views? :missing-black-litterman-views
                   :else nil)
         :runnable? (boolean runnable?)
         :request request
         :blocking-warnings blocking-warnings
         :warnings (vec (:warnings request))}))))
