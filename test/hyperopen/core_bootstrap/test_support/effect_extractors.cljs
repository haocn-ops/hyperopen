(ns hyperopen.core-bootstrap.test-support.effect-extractors)

(def ^:private projection-effect-ids
  #{:effects/save
    :effects/save-many})

(def ^:private persistence-effect-ids
  #{:effects/local-storage-set
    :effects/local-storage-set-json})

(defn effect-phase
  [effect heavy-effect-ids]
  (let [effect-id (first effect)]
    (cond
      (contains? projection-effect-ids effect-id) :projection
      (contains? persistence-effect-ids effect-id) :persistence
      (contains? heavy-effect-ids effect-id) :heavy-io
      :else :other)))

(defn projection-before-heavy?
  [effects heavy-effect-ids]
  (let [projection-index (first (keep-indexed
                                 (fn [idx effect]
                                   (when (= :projection (effect-phase effect heavy-effect-ids))
                                     idx))
                                 effects))
        heavy-index (first (keep-indexed
                            (fn [idx effect]
                              (when (= :heavy-io (effect-phase effect heavy-effect-ids))
                                idx))
                            effects))]
    (or (nil? heavy-index)
        (and (some? projection-index)
             (< projection-index heavy-index)))))

(defn duplicate-heavy-effect-ids
  [effects heavy-effect-ids]
  (->> effects
       (map first)
       (filter heavy-effect-ids)
       frequencies
       (keep (fn [[effect-id count]]
               (when (> count 1)
                 effect-id)))
       set))

(defn phase-order-valid?
  [effects heavy-effect-ids]
  (let [phase-rank {:projection 0
                    :persistence 1
                    :heavy-io 2}]
    (loop [remaining-effects (seq effects)
           highest-rank -1]
      (if-let [effect (first remaining-effects)]
        (let [phase (effect-phase effect heavy-effect-ids)
              rank (get phase-rank phase)]
          (if (nil? rank)
            (recur (next remaining-effects) highest-rank)
            (and (>= rank highest-rank)
                 (recur (next remaining-effects) rank))))
        true))))

(defn extract-saved-order-form [effects]
  (or (some (fn [effect]
              (when (= :effects/save-many (first effect))
                (some (fn [[path value]]
                        (when (= [:order-form] path) value))
                      (second effect))))
            effects)
      (some (fn [effect]
              (when (and (= :effects/save (first effect))
                         (= [:order-form] (second effect)))
                (nth effect 2)))
            effects)))

(defn extract-saved-order-form-ui [effects]
  (or (some (fn [effect]
              (when (= :effects/save-many (first effect))
                (some (fn [[path value]]
                        (when (= [:order-form-ui] path) value))
                      (second effect))))
            effects)
      (some (fn [effect]
              (when (and (= :effects/save (first effect))
                         (= [:order-form-ui] (second effect)))
                (nth effect 2)))
            effects)))
