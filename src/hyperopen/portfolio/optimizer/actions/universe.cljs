(ns hyperopen.portfolio.optimizer.actions.universe
  (:require [hyperopen.portfolio.optimizer.actions.common :as common]
            [hyperopen.portfolio.optimizer.actions.run :as run-actions]
            [hyperopen.portfolio.optimizer.application.current-portfolio :as current-portfolio]
            [hyperopen.portfolio.optimizer.application.history-loader.api-v2 :as history-api-v2]
            [hyperopen.portfolio.optimizer.application.history-prefetch :as history-prefetch]
            [hyperopen.portfolio.optimizer.application.universe-candidates :as universe-candidates]
            [hyperopen.portfolio.optimizer.black-litterman-actions.views :as black-litterman-views]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.ids :as ids]
            [hyperopen.portfolio.optimizer.universe-keyboard :as universe-keyboard]))

(defn set-portfolio-optimizer-universe-search-query
  [_state query]
  [[:effects/save-many
    [[contracts/ui-universe-search-query-path
      (or (some-> query str) "")]
     [contracts/ui-universe-search-active-index-path
      0]]]])

(defn- draft-add-asset-closed-path-value
  []
  [contracts/ui-draft-add-asset-open-path false])

(defn- reset-search-path-values
  []
  [[contracts/ui-universe-search-query-path ""]
   [contracts/ui-universe-search-active-index-path 0]])

(defn set-portfolio-optimizer-draft-add-asset-open
  [_state open?]
  [[:effects/save-many
    (into [[contracts/ui-draft-add-asset-open-path (boolean open?)]]
          (reset-search-path-values))]])

(declare add-portfolio-optimizer-universe-instrument)

(defn handle-portfolio-optimizer-universe-search-keydown
  [state key market-keys]
  (universe-keyboard/handle-keydown add-portfolio-optimizer-universe-instrument state key market-keys))

(declare add-portfolio-optimizer-universe-instrument-and-run)

(defn handle-portfolio-optimizer-draft-add-asset-keydown
  [state key market-keys]
  (universe-keyboard/handle-keydown
   add-portfolio-optimizer-universe-instrument-and-run
   state
   key
   market-keys))

(defn- with-prefetch-effect
  [effects prefetch-plan]
  (cond-> effects
    (:start? prefetch-plan)
    (conj history-prefetch/selection-prefetch-effect)))

(defn- with-prefetch-path-value
  [path-values prefetch-plan]
  (cond-> path-values
    (:changed? prefetch-plan)
    (conj [contracts/history-prefetch-path
           (:state prefetch-plan)])))

(defn- with-history-discovery
  [state market]
  (if (map? market)
    (history-api-v2/with-discovery-metadata
     market
     (get-in state contracts/history-discovery-path))
    market))

(def ^:private from-current-universe-cap
  25)

(def ^:private unusable-history-statuses
  #{:missing :rejected :unavailable :unsupported :disabled})

(def ^:private unusable-quality-statuses
  #{:failed :rejected :missing})

(defn- exposure-abs-notional-usdc
  [exposure]
  (or (some-> (:abs-notional-usdc exposure)
              common/parse-number-value
              js/Math.abs)
      (some-> (:signed-notional-usdc exposure)
              common/parse-number-value
              js/Math.abs)
      0))

(defn- optimizer-history-status
  [instrument key]
  (common/normalize-keyword-like (get instrument key)))

(defn- known-unusable-history?
  [instrument]
  (or (contains? unusable-history-statuses
                 (optimizer-history-status instrument
                                           :optimizer-history/history-status))
      (contains? unusable-quality-statuses
                 (optimizer-history-status instrument
                                           :optimizer-history/quality-status))))

(defn- known-usable-history?
  [instrument]
  (and (:optimizer-history/instrument-id instrument)
       (not (known-unusable-history? instrument))))

(defn- from-current-candidate
  [state idx exposure]
  (when-let [instrument (common/exposure->universe-instrument exposure)]
    {:idx idx
     :abs-notional-usdc (exposure-abs-notional-usdc exposure)
     :instrument (with-history-discovery state instrument)}))

(defn- from-current-sort-key
  [{:keys [instrument abs-notional-usdc idx]}]
  [(if (known-usable-history? instrument) 0 1)
   (- abs-notional-usdc)
   idx])

(defn- usable-universe-from-current-exposures
  [state exposures]
  (->> exposures
       (map-indexed #(from-current-candidate state %1 %2))
       (keep identity)
       (remove #(known-unusable-history? (:instrument %)))
       (sort-by from-current-sort-key)
       (map :instrument)
       common/dedupe-instruments
       (take from-current-universe-cap)
       vec))

(defn add-portfolio-optimizer-universe-instrument
  [state market-key]
  (let [market-key* (common/non-blank-text market-key)
        universe (common/draft-universe state)
        market (or (get-in state [:asset-selector :market-by-key market-key*])
                   (when-let [vault-address (ids/vault-address-from-instrument-id
                                             market-key*)]
                     (some (fn [row]
                             (when (= vault-address
                                      (ids/normalize-vault-address
                                       (:vault-address row)))
                               (universe-candidates/vault-row->candidate row)))
                           (get-in state [:vaults :merged-index-rows]))))
        instrument (common/market->universe-instrument
                    (with-history-discovery state market))
        instrument-id (:instrument-id instrument)]
    (if (and instrument
             (not (common/instrument-present? universe instrument-id)))
      (let [prefetch-plan (history-prefetch/enqueue-missing-instruments
                           state
                           [instrument])
            path-values (with-prefetch-path-value
                          [[contracts/draft-universe-path (conj universe instrument)]
                           [contracts/ui-universe-search-query-path ""]
                           [contracts/ui-universe-search-active-index-path 0]]
                          prefetch-plan)]
        (with-prefetch-effect
          (common/save-draft-path-values path-values)
          prefetch-plan))
      [])))

(defn- draft-add-run-save-effect
  [effect]
  (if (= :effects/save-many (first effect))
    (update effect 1
            (fn [path-values]
              (let [path-values* (vec (remove #(or (= contracts/ui-draft-add-asset-open-path
                                                     (first %))
                                                  (= contracts/history-prefetch-path
                                                     (first %)))
                                             path-values))
                    dirty-path-value (peek path-values*)]
                (if (= contracts/draft-dirty-path (first dirty-path-value))
                  (conj (pop path-values*)
                        (draft-add-asset-closed-path-value)
                        dirty-path-value)
                  (conj path-values*
                        (draft-add-asset-closed-path-value))))))
    effect))

(defn- selection-prefetch-effect?
  [effect]
  (and (= :effects/load-portfolio-optimizer-history (first effect))
       (= :selection-prefetch (:source (second effect)))))

(defn- state-after-save-effect
  [state effect]
  (case (first effect)
    :effects/save
    (assoc-in state (second effect) (nth effect 2))

    :effects/save-many
    (reduce (fn [state* [path value]]
              (assoc-in state* path value))
            state
            (second effect))

    state))

(defn- projected-state-after-save-effects
  [state effects]
  (reduce state-after-save-effect state effects))

(defn- instrument-shortable?
  [instrument]
  (cond
    (contains? instrument :shortable?)
    (true? (:shortable? instrument))

    (= :perp (ids/normalize-market-type
              (or (:market-type instrument)
                  (:instrument-type instrument))))
    true

    :else false))

(defn- position-side-for-instrument
  [instrument side]
  (common/selectable-position-side (instrument-shortable? instrument)
                                   side))

(defn- side-updated-universe
  [universe instrument-id side]
  (reduce (fn [{:keys [changed?] :as acc} instrument]
            (if (common/instrument-matches-id? instrument instrument-id)
              (let [side* (position-side-for-instrument instrument side)
                    current-side (common/normalize-position-side
                                  (:position-side instrument))]
                (-> acc
                    (update :universe conj (assoc instrument
                                                  :position-side side*))
                    (assoc :changed? (or changed?
                                         (not= current-side side*)))))
              (update acc :universe conj instrument)))
          {:changed? false
           :universe []}
          universe))

(defn set-portfolio-optimizer-universe-instrument-side
  [state instrument-id side]
  (let [instrument-id* (common/non-blank-text instrument-id)
        universe (common/draft-universe state)]
    (if instrument-id*
      (let [{:keys [changed? universe]} (side-updated-universe universe
                                                               instrument-id*
                                                               side)]
        (if changed?
          (common/save-draft-path-values
           [[contracts/draft-universe-path universe]])
          []))
      [])))

(defn set-portfolio-optimizer-universe-instrument-side-and-run
  [state instrument-id side]
  (let [effects (set-portfolio-optimizer-universe-instrument-side state
                                                                  instrument-id
                                                                  side)]
    (if (seq effects)
      (into effects
            (run-actions/run-portfolio-optimizer-from-draft
             (projected-state-after-save-effects state effects)))
      [])))

(defn add-portfolio-optimizer-universe-instrument-and-run
  [state market-key]
  (let [effects (add-portfolio-optimizer-universe-instrument state market-key)]
    (if (seq effects)
      (let [effects* (vec (remove selection-prefetch-effect?
                                  (update effects 0 draft-add-run-save-effect)))]
        (into effects*
              (run-actions/run-portfolio-optimizer-from-draft
               (projected-state-after-save-effects state effects*))))
      [])))

(defn toggle-portfolio-optimizer-universe-instrument-exclusion-and-run
  [state instrument-id]
  (let [instrument-id* (common/non-blank-text instrument-id)
        universe (common/draft-universe state)
        instrument (common/find-instrument universe instrument-id*)]
    (if (and instrument-id* instrument)
      (let [blocklist (common/constraint-list state :blocklist)
            instrument-ids (set (ids/instrument-id-candidates instrument))
            excluded? (boolean (some instrument-ids blocklist))
            blocklist* (vec (remove instrument-ids blocklist))
            effects (common/save-draft-path-values
                     [[(conj contracts/draft-constraints-path :blocklist)
                       (if excluded?
                         blocklist*
                         (common/set-membership blocklist*
                                                instrument-id*
                                                true))]])
            state* (projected-state-after-save-effects state effects)]
        (into effects
              (run-actions/run-portfolio-optimizer-from-draft state*)))
      [])))

(defn- black-litterman-universe-path-values
  [state universe*]
  (let [ids (set (keep :instrument-id universe*))
        return-model (get-in state contracts/draft-return-model-path)
        views (vec (:views return-model))
        views* (vec (filter (fn [view]
                              (every? ids
                                      (black-litterman-views/view-instrument-ids view)))
                            views))
        draft-path (fn [kind field]
                     (conj contracts/ui-black-litterman-editor-path
                           :drafts
                           kind
                           field))
        clear-if-missing (fn [kind field]
                           (let [value (get-in state (draft-path kind field))]
                             (when (and value (not (contains? ids value)))
                               [(draft-path kind field) nil])))]
    (cond-> []
      (= :black-litterman (:kind return-model))
      (conj [contracts/draft-return-model-views-path views*])

      :always
      (into (keep identity
                  [(clear-if-missing :absolute :instrument-id)
                   (clear-if-missing :relative :instrument-id)
                   (clear-if-missing :relative :comparator-instrument-id)])))))

(defn remove-portfolio-optimizer-universe-instrument
  [state instrument-id]
  (let [instrument-id* (common/non-blank-text instrument-id)
        universe (common/draft-universe state)
        universe* (vec (remove #(= instrument-id* (:instrument-id %)) universe))
        constraints (get-in state contracts/draft-constraints-path)]
    (if (and instrument-id*
             (not= universe universe*))
      (let [prefetch-state (history-prefetch/remove-instrument state instrument-id*)
            prefetch-changed? (not= (history-prefetch/prefetch-state state)
                                    prefetch-state)
            path-values (cond-> (into [[contracts/draft-universe-path universe*]
                                        [(conj contracts/draft-constraints-path :allowlist)
                                         (common/set-membership (vec (:allowlist constraints)) instrument-id* false)]
                                        [(conj contracts/draft-constraints-path :blocklist)
                                         (common/set-membership (vec (:blocklist constraints)) instrument-id* false)]
                                        [(conj contracts/draft-constraints-path :held-locks)
                                         (common/set-membership (vec (:held-locks constraints)) instrument-id* false)]
                                        [(conj contracts/draft-constraints-path :asset-overrides)
                                         (dissoc (or (:asset-overrides constraints) {}) instrument-id*)]
                                        [(conj contracts/draft-constraints-path :perp-leverage)
                                         (dissoc (or (:perp-leverage constraints) {}) instrument-id*)]]
                                       (black-litterman-universe-path-values state universe*))
                          prefetch-changed?
                          (conj [contracts/history-prefetch-path prefetch-state]))]
        (common/save-draft-path-values path-values))
      [])))

(defn set-portfolio-optimizer-universe-from-current
  [state]
  (let [snapshot (current-portfolio/current-portfolio-snapshot state)
        universe (usable-universe-from-current-exposures
                  state
                  (:exposures snapshot))
        current-derived-constraints
        (when-let [constraints (get-in state contracts/draft-constraints-path)]
          (current-portfolio/current-derived-constraints snapshot constraints))]
    (if (seq universe)
      (let [prefetch-state (history-prefetch/cleanup-to-instrument-ids
                            (history-prefetch/prefetch-state state)
                            (keep :instrument-id universe))
            prefetch-base-state (assoc-in state
                                          contracts/history-prefetch-path
                                          prefetch-state)
            prefetch-plan (history-prefetch/enqueue-missing-instruments
                           prefetch-base-state
                           universe)
            prefetch-changed? (or (not= (history-prefetch/prefetch-state state)
                                        prefetch-state)
                                  (:changed? prefetch-plan))
            path-values (cond-> (into [[contracts/draft-universe-path universe]]
                                      (black-litterman-universe-path-values state universe))
                          current-derived-constraints
                          (conj [contracts/draft-constraints-path
                                 current-derived-constraints])

                          prefetch-changed?
                          (conj [contracts/history-prefetch-path
                                 (:state prefetch-plan)]))]
        (with-prefetch-effect
          (common/save-draft-path-values path-values)
          prefetch-plan))
      [])))
