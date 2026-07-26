(ns hyperopen.portfolio.optimizer.application.view-model.rebalance
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def ^:private finite-number? coercion/finite-number?)
(def ^:private vault-instrument? ids/vault-instrument-id?)

(defn- signed-label
  [value]
  (cond
    (and (finite-number? value) (neg? value)) "short"
    (and (finite-number? value) (pos? value)) "long"
    :else "flat"))

(defn- position-side
  [value]
  (case (coercion/normalize-keyword-like value)
    :short :short
    :long))

(defn- instrument-group-key
  [labels-by-instrument instrument-id]
  (let [value (or (get labels-by-instrument instrument-id)
                  (str instrument-id))
        unprefixed (last (str/split value #":"))
        base (first (str/split unprefixed #"[/-]"))]
    (if (seq base) base value)))

(defn- instrument-label
  [labels-by-instrument instrument-id]
  (or (get labels-by-instrument instrument-id)
      (str instrument-id)))

(defn- base-symbol
  [value]
  (some-> value
          str
          str/trim
          (str/replace #"^.*:" "")
          (str/split #"/|-" 2)
          first
          str/trim
          not-empty))

(defn- instrument-market
  [labels-by-instrument instrument-id draft-instrument]
  (let [instrument-id* (str instrument-id)
        label (instrument-label labels-by-instrument instrument-id)
        source-id (or (coercion/non-blank-text (:instrument-id draft-instrument))
                      instrument-id*)
        [kind raw-coin] (str/split source-id #":" 2)
        market-type (or (ids/normalize-market-type (:market-type draft-instrument))
                        (case kind
                          "spot" :spot
                          "perp" :perp
                          nil))
        coin (or (coercion/non-blank-text (:coin draft-instrument))
                 (when market-type
                   (not-empty raw-coin))
                 (not-empty instrument-id*)
                 (base-symbol label))
        base (or (coercion/non-blank-text (:base draft-instrument))
                 (base-symbol coin)
                 (base-symbol label))
        symbol (or (coercion/non-blank-text (:symbol draft-instrument))
                   (when (= :spot market-type)
                     (when base
                       (str base "/USDC")))
                   base
                   label)]
    (cond-> {:key source-id
             :coin coin
             :symbol symbol
             :base base
             :market-type market-type}
      (coercion/non-blank-text (:dex draft-instrument))
      (assoc :dex (coercion/non-blank-text (:dex draft-instrument)))
      (and (map? draft-instrument)
           (contains? draft-instrument :hip3?))
      (assoc :hip3? (boolean (:hip3? draft-instrument))))))

(defn- leg-label
  [labels-by-instrument instrument-id current-weight target-weight]
  (let [value (str instrument-id)
        market-type (first (str/split value #":"))]
    (case market-type
      "spot" "spot"
      "perp" (cond
               (neg? (or target-weight 0)) "perp short"
               (pos? (or target-weight 0)) "perp long"
               (neg? (or current-weight 0)) "perp short"
               :else "perp long")
      "vault" (instrument-label labels-by-instrument instrument-id)
      value)))

(defn- universe-by-candidate-id
  [universe]
  (into {}
        (mapcat (fn [instrument]
                  (map (fn [instrument-id]
                         [instrument-id instrument])
                       (ids/instrument-id-candidates instrument))))
        universe))

(defn- excluded-instrument?
  [excluded-ids instrument]
  (boolean
   (and instrument
        (some excluded-ids (ids/instrument-id-candidates instrument)))))

(defn- preferred-row-id
  [result-ids instrument]
  (let [candidates (ids/instrument-id-candidates instrument)]
    (or (some result-ids candidates)
        (some #(when-not (str/starts-with? % "hl:") %)
              (reverse candidates))
        (first candidates))))

(defn- short-selectable?
  [instrument instrument-id]
  (cond
    (contains? instrument :shortable?)
    (true? (:shortable? instrument))

    (= :perp (ids/normalize-market-type
              (or (:market-type instrument)
                  (:instrument-type instrument))))
    true

    (str/starts-with? (str instrument-id) "perp:")
    true

    :else false))

(defn- row-position-side
  [draft-instrument target-weight]
  (or (when draft-instrument
        (position-side (:position-side draft-instrument)))
      (if (neg? (or target-weight 0))
        :short
        :long)))

(defn- binding-kind-for
  "A binding bound of 0 pins the target at zero — usually a side setting
  blocking the direction the solver wanted — which reads very differently
  from sitting on a max-weight cap, so the badge must not conflate them."
  [entries]
  (when (seq entries)
    (if (some (fn [{:keys [bound]}]
                (and (number? bound)
                     (<= (js/Math.abs bound) 1e-10)))
              entries)
      :floored
      :capped)))

(defn- row-model
  [idx labels-by-instrument binding-by-instrument excluded-ids draft-by-id capital-usd
   [instrument-id current-weight target-weight]]
  (let [current-weight* (or current-weight 0)
        excluded? (or (contains? excluded-ids instrument-id)
                      (excluded-instrument? excluded-ids
                                            (get draft-by-id instrument-id)))
        target-weight* (if excluded?
                         0
                         (or target-weight 0))
        current-notional (* (or capital-usd 0) current-weight*)
        target-notional (* (or capital-usd 0) target-weight*)
        delta (- target-weight* current-weight*)
        binding-entries (get binding-by-instrument instrument-id)
        binding? (boolean (seq binding-entries))
        draft-instrument (get draft-by-id instrument-id)]
    {:idx idx
     :asset (instrument-group-key labels-by-instrument instrument-id)
     :instrument-id instrument-id
     :current-weight current-weight*
     :target-weight target-weight*
     :current-notional current-notional
     :target-notional target-notional
     :delta delta
     :delta-notional (- target-notional current-notional)
     :binding? binding?
     :binding-kind (binding-kind-for binding-entries)
     :excluded? excluded?
     :status-label (when excluded? "sell to 0")
     :current-sign (signed-label current-weight*)
     :target-sign (signed-label target-weight*)
     :position-side (row-position-side draft-instrument target-weight*)
     :short-selectable? (short-selectable? draft-instrument instrument-id)
     :leg-label (leg-label labels-by-instrument
                           instrument-id
                           current-weight*
                           target-weight*)
     :market (instrument-market labels-by-instrument instrument-id draft-instrument)}))

(defn- grouped-rows
  [rows]
  (reduce (fn [{:keys [order] :as acc} {:keys [asset] :as row}]
            (-> acc
                (update :order #(if (some #{asset} %) % (conj (or % []) asset)))
                (update-in [:by-asset asset] (fnil conj []) row)))
          {:order []
           :by-asset {}}
          rows))

(defn- group-icon-model
  [rows]
  (let [representative (or (some #(when-not (vault-instrument? (:instrument-id %)) %) rows)
                           (first rows))
        vault? (vault-instrument? (:instrument-id representative))]
    {:icon-kind (if vault? :vault :market)
     :market (when-not vault? (:market representative))}))

(defn- group-model
  [asset rows]
  (let [current-weight (reduce + 0 (map :current-weight rows))
        target-weight (reduce + 0 (map :target-weight rows))
        delta (- target-weight current-weight)
        binding? (boolean (some :binding? rows))
        binding-kind (cond
                       (some #(= :capped (:binding-kind %)) rows) :capped
                       (some #(= :floored (:binding-kind %)) rows) :floored)
        excluded? (boolean (some :excluded? rows))
        expandable? (> (count rows) 1)
        rows* (mapv #(assoc % :hidden? (not expandable?)) rows)]
    (merge
     {:asset asset
      :instrument-id (when-not expandable?
                       (:instrument-id (first rows*)))
      :position-side (if (some #(= :short (:position-side %)) rows*)
                       :short
                       :long)
      :short-selectable? (boolean (some :short-selectable? rows*))
      :current-weight current-weight
      :target-weight target-weight
      :delta delta
      :delta-notional (reduce + 0 (map :delta-notional rows))
      :binding? binding?
      :binding-kind binding-kind
      :excluded? excluded?
      :status-label (when excluded? "sell to 0")
      :expandable? expandable?
      :target-sign (signed-label target-weight)
      :rows rows*}
     (group-icon-model rows*))))

(defn target-exposure-table-model
  ([result]
   (target-exposure-table-model result nil))
  ([result {:keys [draft]}]
  (let [capital-usd (get-in result [:rebalance-preview :capital-usd])
        instrument-ids (vec (:instrument-ids result))
        target-by-id (merge (zipmap instrument-ids (:target-weights result))
                            (:target-weights-by-instrument result))
        current-by-id (merge (zipmap instrument-ids (:current-weights result))
                             (:current-weights-by-instrument result)
                             (:current-portfolio-weights-by-instrument result))
        labels-by-instrument (or (:labels-by-instrument result) {})
        draft-universe (vec (:universe draft))
        excluded-ids (set (or (get-in draft [:constraints :blocklist]) []))
        draft-by-id (universe-by-candidate-id draft-universe)
        result-ids (set instrument-ids)
        excluded-row-ids (keep (fn [instrument]
                                 (when (excluded-instrument? excluded-ids
                                                             instrument)
                                   (preferred-row-id result-ids instrument)))
                               draft-universe)
        row-ids (vec (distinct (concat instrument-ids
                                       excluded-row-ids)))
        binding-by-instrument (dissoc (group-by :instrument-id
                                                (get-in result [:diagnostics :binding-constraints]))
                                      nil)
        binding-instrument-ids (set (keys binding-by-instrument))
        rows (mapv (fn [idx row]
                     (row-model idx
                                labels-by-instrument
                                binding-by-instrument
                                excluded-ids
                                draft-by-id
                                capital-usd
                                row))
                   (range)
                   (map (fn [instrument-id]
                          [instrument-id
                           (get current-by-id instrument-id 0)
                           (get target-by-id instrument-id 0)])
                        row-ids))
        {:keys [order by-asset]} (grouped-rows rows)
        groups (mapv #(group-model % (get by-asset %)) order)]
    {:capital-usd capital-usd
     :labels-by-instrument labels-by-instrument
     :binding-instrument-ids binding-instrument-ids
     :rows rows
     :groups groups})))
