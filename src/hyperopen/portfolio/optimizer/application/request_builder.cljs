(ns hyperopen.portfolio.optimizer.application.request-builder
  (:require [clojure.set :as set]
            [hyperopen.portfolio.optimizer.application.history-loader :as history-loader]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.infrastructure.prior-data :as prior-data]))

(def default-return-model
  {:kind :historical-mean})

(def default-risk-model
  {:kind :diagonal-shrink})

(def default-objective
  {:kind :minimum-variance})

(defn- draft-universe
  [draft]
  (vec (or (:universe draft) [])))

(def ^:private non-blank-text coercion/non-blank-text)

(def ^:private normalize-id-list coercion/normalize-id-list)

(defn- normalize-net-exposure
  [constraints]
  (let [net-min (:net-min constraints)
        net-max (:net-max constraints)]
    (if (or (some? net-min)
            (some? net-max))
      (cond-> {}
        (some? net-min) (assoc :min net-min)
        (some? net-max) (assoc :max net-max))
      (:net-exposure constraints))))

(def ^:private draft-only-constraint-keys
  #{:gross-max
    :net-min
    :net-max
    :asset-overrides
    :held-locks
    :perp-leverage})

(defn- normalize-constraints
  [constraints]
  (let [constraints* (or constraints {})
        allowlist (normalize-id-list (:allowlist constraints*))
        blocklist (normalize-id-list (:blocklist constraints*))
        held-locks (normalize-id-list (:held-locks constraints*))
        net-exposure (normalize-net-exposure constraints*)]
    (cond-> (apply dissoc constraints* draft-only-constraint-keys)
      true
      (assoc :blocklist blocklist)

      true
      (assoc :include-spot? (true? (:include-spot? constraints*)))

      (empty? allowlist)
      (dissoc :allowlist)

      (seq allowlist)
      (assoc :allowlist allowlist)

      (contains? constraints* :gross-max)
      (assoc :gross-leverage (:gross-max constraints*))

      (some? net-exposure)
      (assoc :net-exposure net-exposure)

      (contains? constraints* :asset-overrides)
      (assoc :per-asset-overrides (:asset-overrides constraints*))

      (contains? constraints* :held-locks)
      (assoc :held-position-locks held-locks)

      (contains? constraints* :perp-leverage)
      (assoc :per-perp-leverage-caps (:perp-leverage constraints*)))))

(defn- normalize-execution-assumptions
  [execution-assumptions]
  (let [assumptions* (or execution-assumptions {})
        fallback-slippage-bps (or (:fallback-slippage-bps assumptions*)
                                  (:slippage-fallback-bps assumptions*))]
    (cond-> (dissoc assumptions* :slippage-fallback-bps)
      (some? fallback-slippage-bps)
      (assoc :fallback-slippage-bps fallback-slippage-bps))))

(def ^:private finite-number? coercion/finite-number?)

(defn- non-zero-current-row?
  [row]
  (let [weight (:weight row)]
    (and (finite-number? weight)
         (not (zero? weight)))))

(defn- current-row-instrument
  [row]
  (select-keys row
               [:instrument-id
                :market-type
                :instrument-type
                :coin
                :dex
                :symbol
                :base
                :quote
                :name
                :vault-address
                :hip3?
                :optimizer-history/instrument-id
                :optimizer-history/display-symbol
                :optimizer-history/instrument-kind
                :optimizer-history/history-status
                :optimizer-history/quality-status
                :optimizer-history/proxy]))

(defn- universe-by-id
  [universe]
  (into {}
        (map (fn [instrument]
               [(:instrument-id instrument) instrument]))
        universe))

(defn- instrument-id-set
  [universe]
  (set (keep :instrument-id universe)))

(defn- current-portfolio-rows
  [current-portfolio]
  (let [exposures (seq (:exposures current-portfolio))]
    (if exposures
      exposures
      (vals (or (:by-instrument current-portfolio) {})))))

(defn- current-portfolio-universe
  [current-portfolio requested-universe]
  (let [requested-by-id (universe-by-id requested-universe)]
    (vec (vals (reduce (fn [acc row]
                         (let [instrument-id (:instrument-id row)]
                           (if (and instrument-id
                                    (non-zero-current-row? row))
                             (assoc acc
                                    instrument-id
                                    (merge (get requested-by-id instrument-id)
                                           (current-row-instrument row)))
                             acc)))
                       {}
                       (current-portfolio-rows current-portfolio))))))

(defn- current-history-source
  [history-data current-universe requested-universe]
  (let [current-ids (instrument-id-set current-universe)
        selected-ids (instrument-id-set requested-universe)]
    (cond
      (empty? current-ids)
      nil

      (set/subset? current-ids selected-ids)
      history-data

      :else
      (:current-portfolio-history-data history-data))))

(defn- confidence-variance
  [confidence]
  (let [confidence* (-> confidence
                        (max 0.0)
                        (min 1.0))]
    (max 0.000001 (- 1.0 confidence*))))

(defn- confidence-variance*
  [view*]
  (let [confidence (:confidence view*)]
    (cond
      (finite-number? (:confidence-variance view*))
      (:confidence-variance view*)

      (finite-number? confidence)
      (confidence-variance confidence)

      :else
      nil)))

(defn- invalid-black-litterman-view-warning
  [view]
  (cond-> {:code :invalid-black-litterman-view}
    (:id view) (assoc :view-id (:id view))))

(defn- black-litterman-view-outside-universe-warning
  [view instrument-ids]
  (cond-> {:code :black-litterman-view-outside-universe
           :instrument-ids (vec instrument-ids)}
    (:id view) (assoc :view-id (:id view))))

(defn- normalize-direction
  [direction]
  (case direction
    :underperform :underperform
    :outperform :outperform
    :outperform))

(defn- normalize-black-litterman-view
  [view]
  (let [view* (or view {})
        confidence (:confidence view*)
        instrument-id (non-blank-text (:instrument-id view*))
        comparator-id (non-blank-text (:comparator-instrument-id view*))
        long-id (non-blank-text (:long-instrument-id view*))
        short-id (non-blank-text (:short-instrument-id view*))
        direction (normalize-direction (:direction view*))
        confidence-variance* (confidence-variance* view*)]
    (cond
      (and (= :absolute (:kind view*))
           instrument-id
           (finite-number? (:return view*)))
      {:view (cond-> (assoc view* :weights (or (:weights view*)
                                               {instrument-id 1}))
               confidence-variance*
               (assoc :confidence-variance confidence-variance*))}

      (and (= :relative (:kind view*))
           instrument-id
           comparator-id
           (not= instrument-id comparator-id)
           (finite-number? (:return view*)))
      {:view (cond-> (assoc view*
                            :direction direction
                            :weights (case direction
                                       :underperform {instrument-id -1
                                                      comparator-id 1}
                                       {instrument-id 1
                                        comparator-id -1}))
               confidence-variance*
               (assoc :confidence-variance confidence-variance*))}

      (and (= :relative (:kind view*))
           long-id
           short-id
           (not= long-id short-id)
           (finite-number? (:return view*)))
      {:view (cond-> (assoc view* :weights (or (:weights view*)
                                               {long-id 1
                                                short-id -1}))
               confidence-variance*
               (assoc :confidence-variance confidence-variance*))}

      :else
      {:warning (invalid-black-litterman-view-warning view*)})))

(defn- normalize-return-model
  [return-model]
  (let [return-model* (or return-model default-return-model)]
    (if (= :black-litterman (:kind return-model*))
      (let [normalized (map normalize-black-litterman-view
                            (or (:views return-model*) []))]
        {:return-model (assoc return-model*
                              :views (vec (keep :view normalized)))
         :warnings (vec (keep :warning normalized))})
      {:return-model return-model*
       :warnings []})))

(defn- non-zero-weight-id?
  [[instrument-id weight]]
  (and (non-blank-text instrument-id)
       (finite-number? weight)
       (not (zero? weight))))

(defn- black-litterman-view-instrument-ids
  [view]
  (let [weights (->> (:weights view)
                     (filter non-zero-weight-id?)
                     (map first)
                     vec)]
    (case (:kind view)
      :relative
      (let [ids (or (seq (normalize-id-list [(:instrument-id view)
                                             (:comparator-instrument-id view)]))
                    (seq (normalize-id-list [(:long-instrument-id view)
                                             (:short-instrument-id view)]))
                    weights)]
        (vec ids))

      :absolute
      (let [ids (or (seq (normalize-id-list [(:instrument-id view)]))
                    weights)]
        (vec ids))

      weights)))

(defn- view-overlaps-eligible-universe?
  [eligible-ids view]
  (let [ids (black-litterman-view-instrument-ids view)]
    (case (:kind view)
      :relative
      (and (seq ids)
           (every? #(contains? eligible-ids %) ids))

      (boolean
       (some #(contains? eligible-ids %) ids)))))

(defn- filter-black-litterman-views-for-universe
  [return-model eligible-universe]
  (if-not (= :black-litterman (:kind return-model))
    {:return-model return-model
     :warnings []}
    (let [eligible-ids (set (keep :instrument-id eligible-universe))
          normalized (map (fn [view]
                            (if (view-overlaps-eligible-universe? eligible-ids view)
                              {:view view}
                              {:warning
                               (black-litterman-view-outside-universe-warning
                                view
                                (black-litterman-view-instrument-ids view))}))
                          (or (:views return-model) []))]
      {:return-model (assoc return-model
                            :views (vec (keep :view normalized)))
       :warnings (vec (keep :warning normalized))})))

(defn- black-litterman-return-model?
  [return-model]
  (= :black-litterman (:kind return-model)))

(defn- bl-prior
  [universe current-portfolio market-cap-by-coin return-model]
  (when (black-litterman-return-model? return-model)
    (prior-data/resolve-black-litterman-prior
     {:universe universe
      :market-cap-by-coin market-cap-by-coin
      :current-portfolio current-portfolio})))

(def ^:private align-history-memo-capacity
  ;; Each request aligns two universes (requested and currently held), so the
  ;; memo must hold more than one entry to survive a single build.
  4)

(defonce ^:private align-history-memo
  (volatile! {}))

(defn- align-history
  ;; Alignment walks every candle series and dominates request building, but
  ;; its inputs only change when the universe, loaded history, or as-of move -
  ;; not when draft constraints are edited. Unchanged state subtrees keep the
  ;; memo lookup cheap because equality short-circuits on identity.
  [{:keys [universe
           history-data
           as-of-ms
           stale-after-ms
           funding-periods-per-year]}]
  (let [inputs {:universe universe
                :api-v2-history (:api-v2-history history-data)
                :candle-history-by-coin (:candle-history-by-coin history-data)
                :funding-history-by-coin (:funding-history-by-coin history-data)
                :vault-details-by-address (:vault-details-by-address history-data)
                :as-of-ms as-of-ms
                :stale-after-ms stale-after-ms
                :funding-periods-per-year funding-periods-per-year}
        memo @align-history-memo]
    (if-let [entry (find memo inputs)]
      (val entry)
      (let [value (history-loader/align-history-inputs inputs)
            memo* (if (>= (count memo) align-history-memo-capacity) {} memo)]
        (vreset! align-history-memo (assoc memo* inputs value))
        value))))

(defn build-engine-request
  [{:keys [draft
           current-portfolio
           history-data
           market-cap-by-coin
           as-of-ms
           stale-after-ms
           funding-periods-per-year]}]
  (let [draft* (contracts/migrate-draft draft)
        requested-universe (draft-universe draft*)
        normalized-return-model (normalize-return-model (:return-model draft*))
        return-model (:return-model normalized-return-model)
        risk-model (or (:risk-model draft*) default-risk-model)
        objective (or (:objective draft*) default-objective)
        constraints (normalize-constraints (:constraints draft*))
        history (align-history
                 {:universe requested-universe
                  :history-data history-data
                  :as-of-ms as-of-ms
                  :stale-after-ms stale-after-ms
                  :funding-periods-per-year funding-periods-per-year})
        eligible-universe (:eligible-instruments history)
        current-universe (current-portfolio-universe current-portfolio
                                                     requested-universe)
        current-history-data (current-history-source history-data
                                                     current-universe
                                                     requested-universe)
        current-history (when (and (seq current-universe)
                                   current-history-data)
                          (align-history
                           {:universe current-universe
                            :history-data current-history-data
                            :as-of-ms as-of-ms
                            :stale-after-ms stale-after-ms
                            :funding-periods-per-year funding-periods-per-year}))
        universe-filtered-return-model (filter-black-litterman-views-for-universe
                                        return-model
                                        eligible-universe)
        return-model (:return-model universe-filtered-return-model)
        prior (bl-prior requested-universe
                        current-portfolio
                        market-cap-by-coin
                        return-model)
        warnings (vec (concat (:warnings normalized-return-model)
                              (:warnings universe-filtered-return-model)
                              (:warnings history)
                              (:warnings prior)))]
    (cond-> {:scenario-id (:id draft*)
             :universe eligible-universe
             :requested-universe requested-universe
             :current-portfolio-universe current-universe
             :current-portfolio current-portfolio
             :current-portfolio-history current-history
             :return-model return-model
             :risk-model risk-model
             :objective objective
             :constraints constraints
             :execution-assumptions (normalize-execution-assumptions
                                     (:execution-assumptions draft*))
             :history history
             :warnings warnings
             :as-of-ms as-of-ms}
      prior (assoc :black-litterman-prior prior))))
