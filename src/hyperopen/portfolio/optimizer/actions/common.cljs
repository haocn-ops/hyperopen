(ns hyperopen.portfolio.optimizer.actions.common
  (:require [hyperopen.portfolio.optimizer.application.run-identity :as run-identity]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def supported-universe-market-types
  #{:perp :spot :vault})

(def normalize-keyword-like coercion/normalize-keyword-like)

(defn save-draft-path-values
  [path-values]
  [[:effects/save-many
    (conj (vec path-values)
          [contracts/draft-dirty-path true])]])

(def non-blank-text coercion/non-blank-text)

(defn normalize-position-side
  [side]
  (let [side* (normalize-keyword-like side)]
    (if (contains? #{:long :short} side*)
      side*
      :long)))

(defn selectable-position-side
  [shortable? side]
  (let [side* (normalize-position-side side)]
    (if (and (= :short side*)
             (not shortable?))
      :long
      side*)))

(defn- default-position-side
  [source shortable?]
  (selectable-position-side shortable?
                            (or (:position-side source)
                                (:side source))))

(def ^:private optimizer-history-keys
  [:optimizer-history/instrument-id
   :optimizer-history/display-symbol
   :optimizer-history/instrument-kind
   :optimizer-history/history-status
   :optimizer-history/proxy
   :optimizer-history/quality-status])

(defn- with-optimizer-history-metadata
  [instrument source]
  (merge instrument (select-keys source optimizer-history-keys)))

(defn exposure->universe-instrument
  [exposure]
  (let [instrument-id (non-blank-text (:instrument-id exposure))
        coin (non-blank-text (:coin exposure))
        market-type (:market-type exposure)
        shortable? (= :perp market-type)]
    (when (and instrument-id
               coin
               (keyword? market-type))
      (with-optimizer-history-metadata
        (cond-> {:instrument-id instrument-id
                 :market-type market-type
                 :coin coin
                 :shortable? shortable?
                 :position-side (default-position-side exposure shortable?)}
          (non-blank-text (:dex exposure))
          (assoc :dex (non-blank-text (:dex exposure)))
          (non-blank-text (:symbol exposure)) (assoc :symbol (non-blank-text (:symbol exposure)))
          (non-blank-text (:base exposure)) (assoc :base (non-blank-text (:base exposure)))
          (non-blank-text (:quote exposure)) (assoc :quote (non-blank-text (:quote exposure)))
          (contains? exposure :hip3?) (assoc :hip3? (boolean (:hip3? exposure))))
        exposure))))

(defn market->universe-instrument
  [market]
  (let [instrument-id (non-blank-text (:key market))
        coin (non-blank-text (:coin market))
        market-type (normalize-keyword-like (:market-type market))
        vault-address (ids/normalize-vault-address (:vault-address market))
        shortable? (= :perp market-type)]
    (when (and instrument-id
               coin
               (contains? supported-universe-market-types market-type)
               (or (not= :vault market-type)
                   vault-address))
      (with-optimizer-history-metadata
        (cond-> {:instrument-id instrument-id
                 :market-type market-type
                 :coin coin
                 :shortable? shortable?
                 :position-side (default-position-side market shortable?)}
          (= :vault market-type)
          (assoc :vault-address vault-address)
          (non-blank-text (:dex market))
          (assoc :dex (non-blank-text (:dex market)))
          (non-blank-text (:symbol market)) (assoc :symbol (non-blank-text (:symbol market)))
          (non-blank-text (:name market)) (assoc :name (non-blank-text (:name market)))
          (non-blank-text (:base market)) (assoc :base (non-blank-text (:base market)))
          (non-blank-text (:quote market)) (assoc :quote (non-blank-text (:quote market)))
          (contains? market :tvl) (assoc :tvl (:tvl market))
          (contains? market :hip3?) (assoc :hip3? (boolean (:hip3? market))))
        market))))

(defn dedupe-instruments
  [instruments]
  (:items
   (reduce (fn [{:keys [seen] :as acc} instrument]
             (let [instrument-id (:instrument-id instrument)]
               (if (contains? seen instrument-id)
                 acc
                 (-> acc
                     (update :seen conj instrument-id)
                     (update :items conj instrument)))))
           {:seen #{}
            :items []}
           instruments)))

(def parse-number-value coercion/parse-number)

(def parse-boolean-value coercion/parse-boolean-value)

(defn constraint-list
  [state constraint-key]
  (vec (or (get-in state (conj contracts/draft-constraints-path constraint-key))
           [])))

(defn draft-universe
  [state]
  (vec (or (get-in state contracts/draft-universe-path)
           [])))

(defn instrument-matches-id?
  [instrument instrument-id]
  (let [instrument-id* (non-blank-text instrument-id)]
    (boolean
     (and instrument-id*
          (some #(= instrument-id* %)
                (ids/instrument-id-candidates instrument))))))

(defn find-instrument
  [universe instrument-id]
  (some #(when (instrument-matches-id? % instrument-id) %)
        universe))

(defn instrument-present?
  [universe instrument-id]
  (boolean (find-instrument universe instrument-id)))

(defn instrument-market-type
  [state instrument-id]
  (some-> (find-instrument (get-in state contracts/draft-universe-path)
                           instrument-id)
          :market-type))

(defn set-membership
  [items item enabled?]
  (let [items* (vec (remove #(= item %) items))]
    (if enabled?
      (conj items* item)
      items*)))

(defn build-request-signature
  [request]
  (run-identity/build-request-signature request))

(defn current-scenario-id
  [state]
  (or (non-blank-text (get-in state contracts/active-scenario-loaded-id-path))
      (non-blank-text (get-in state contracts/draft-id-path))))

(defn vault-list-metadata-fetch-effects
  [state]
  (if (seq (get-in state [:vaults :merged-index-rows]))
    []
    [[:effects/api-fetch-vault-index-with-cache]
     [:effects/api-fetch-vault-summaries]]))

(defn scenario-id-effect
  [effect-id scenario-id]
  (if-let [scenario-id* (non-blank-text scenario-id)]
    [[effect-id scenario-id*]]
    []))
