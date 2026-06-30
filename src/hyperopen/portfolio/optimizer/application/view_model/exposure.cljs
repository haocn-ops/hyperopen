(ns hyperopen.portfolio.optimizer.application.view-model.exposure
  "Pure view-model for the 2D exposure-map Positioning control. Turns the canonical draft
  constraints (plus the current portfolio exposure and the infeasible-control highlight set)
  into a flat display map the view renders: the policy targets/bands, the pad marker fractions,
  the band rectangle, the current-portfolio dot, the preset chips, the read-only generated-
  constraints echo numbers, and per-axis warning flags. All numbers come from the pure
  `exposure-policy` namespace so the view, actions, and tests stay in agreement."
  (:require [hyperopen.portfolio.optimizer.domain.exposure-policy :as policy]))

(defn snapshot->current-exposure
  "Reduce a current-portfolio snapshot to {:gross r :net r} exposure ratios (multiples of
  capital), or nil when capital is not loaded. The pad plots this as the faint 'current' dot."
  [snapshot]
  (let [nav (get-in snapshot [:capital :nav-usdc])
        gross (get-in snapshot [:capital :gross-exposure-usdc])
        net (get-in snapshot [:capital :net-exposure-usdc])]
    (when (and (number? nav) (pos? nav) (number? gross) (number? net))
      {:gross (/ gross nav)
       :net (/ net nav)})))

(defn- gross-highlighted?
  [highlighted-controls]
  (boolean (some highlighted-controls [:gross-min :gross-max])))

(defn- net-highlighted?
  [highlighted-controls]
  (boolean (some highlighted-controls [:net-min :net-max])))

(defn exposure-map-model
  "Build the exposure-map display model. `current-exposure` is `{:gross r :net r}` (ratios of
  capital) or nil when the current portfolio is not loaded; `highlighted-controls` is the set of
  constraint keys the last run flagged infeasible."
  [{:keys [constraints current-exposure highlighted-controls]}]
  (let [constraints* (or constraints {})
        policy* (policy/constraints->policy constraints*)
        active (policy/active-preset constraints*)
        gross-min (:gross-min constraints*)
        gross-max (:gross-max constraints*)
        net-min (:net-min constraints*)
        net-max (:net-max constraints*)]
    {:policy policy*
     :target-marker (policy/target-marker policy*)
     :band-rect (policy/band-rect policy*)
     :current-marker (policy/current-exposure-marker current-exposure)
     :current-exposure current-exposure
     :gross-band (:gross-band policy*)
     :net-band (:net-band policy*)
     :max-band policy/max-band
     :axis {:gross-max policy/gross-axis-max
            :net-extent policy/net-axis-extent}
     :echo {:gross-min gross-min
            :gross-max gross-max
            :gross-floored? (some? gross-min)
            :net-min net-min
            :net-max net-max}
     :active-preset active
     :presets (mapv (fn [k]
                      {:key k
                       :label (get policy/preset-labels k)
                       :active? (= k active)})
                    policy/preset-keys)
     :highlighted {:gross (gross-highlighted? highlighted-controls)
                   :net (net-highlighted? highlighted-controls)}}))
