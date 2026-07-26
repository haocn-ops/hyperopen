(ns hyperopen.portfolio.optimizer.application.view-model.frontier
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def modes [:standalone :contribution :none])

(def ^:private non-blank-text coercion/non-blank-text)
(def ^:private finite-number? coercion/finite-number?)

(defn normalize-mode
  [overlay-mode]
  (if (some #{overlay-mode} modes)
    overlay-mode
    :standalone))

(defn- point-visible?
  [point]
  (and (finite-number? (:volatility point))
       (finite-number? (:expected-return point))))

(defn visible-points
  [result overlay-mode]
  (let [mode (normalize-mode overlay-mode)]
    (if (contains? #{:standalone :contribution} mode)
      (->> (get-in result [:frontier-overlays mode])
           (filter point-visible?)
           vec)
      [])))

(defn all-points
  [result]
  (->> [:standalone :contribution]
       (mapcat #(get-in result [:frontier-overlays %]))
       (filter point-visible?)
       vec))

(defn copy
  [overlay-mode]
  (case (normalize-mode overlay-mode)
    :contribution
    {:subtitle "Risk vs return — annualized frontier with contribution overlays"
     :x-axis-prefix "Vol"
     :y-axis-prefix "Ret"
     :reading-text "Frontier points stay on the same risk / return scale. Overlay markers show signed volatility contribution on x and return contribution on y for each selected asset."
     :legend-label "Signed contribution"}

    :standalone
    {:subtitle "Risk vs return — annualized frontier with standalone asset overlays"
     :x-axis-prefix "Vol"
     :y-axis-prefix "Ret"
     :reading-text "Frontier points are feasible portfolios. Overlay markers show each selected asset as its own standalone risk / return point."
     :legend-label "Standalone assets"}

    {:subtitle "Risk vs return — annualized"
     :x-axis-prefix "Vol"
     :y-axis-prefix "Ret"
     :reading-text "Each point is a feasible portfolio."
     :legend-label nil}))

(defn overlay-label
  [point]
  (or (:label point) (:instrument-id point)))

(defn vault-point?
  [point]
  (or (= :vault (ids/normalize-market-type (:market-type point)))
      (ids/vault-instrument-id? (:instrument-id point))))

(defn- base-symbol
  [value]
  (some-> value
          non-blank-text
          (str/replace #"^.*:" "")
          (str/split #"/|-" 2)
          first
          non-blank-text))

(defn point-market
  [point]
  (let [source (or (:icon-market point) point)
        instrument-id (non-blank-text (or (:instrument-id source)
                                          (:instrument-id point)))
        label (non-blank-text (:label point))
        [kind raw-coin] (when instrument-id
                          (str/split instrument-id #":" 2))
        market-type (or (ids/normalize-market-type (:market-type source))
                        (case kind
                          "spot" :spot
                          "perp" :perp
                          nil))
        coin (or (non-blank-text (:coin source))
                 (when market-type
                   (non-blank-text raw-coin))
                 instrument-id
                 label)
        base (or (non-blank-text (:base source))
                 (base-symbol coin)
                 (base-symbol label))]
    (cond-> {:key instrument-id
             :coin coin
             :symbol (or (non-blank-text (:symbol source))
                         (when (= :spot market-type) coin)
                         base)
             :base base
             :market-type market-type}
      (non-blank-text (:dex source))
      (assoc :dex (non-blank-text (:dex source)))
      (contains? source :hip3?)
      (assoc :hip3? (boolean (:hip3? source))))))
