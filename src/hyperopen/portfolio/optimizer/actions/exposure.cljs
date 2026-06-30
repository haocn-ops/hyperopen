(ns hyperopen.portfolio.optimizer.actions.exposure
  "Pure action handlers for the 2D exposure-map Positioning control. Each handler reads the
  current draft constraints, asks the pure `exposure-policy` namespace for the next FULL
  constraint map, and persists it whole via `common/save-draft-path-values` (one save-many that
  also flips the dirty flag). Writing the whole map is deliberate: a zero gross band must DISSOC
  :gross-min, and the save-many effect can only assoc path values, so per-key writes could not
  clear the floor.

  These handlers only ever write the existing canonical constraint keys
  (:gross-min/:gross-max/:net-min/:net-max/:max-asset-weight), so the request builder and specs
  are unchanged."
  (:require [hyperopen.portfolio.optimizer.actions.common :as common]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as defaults]
            [hyperopen.portfolio.optimizer.domain.exposure-policy :as policy]))

(defn- current-constraints
  [state]
  (or (get-in state contracts/draft-constraints-path) {}))

(defn- write-constraints
  [constraints]
  (common/save-draft-path-values
   [[contracts/draft-constraints-path constraints]]))

(defn set-portfolio-optimizer-exposure-point
  "Drag/click on the pad. `client-x`/`client-y` are pointer coordinates, `bounds` the pad's
  bounding rect (resolved by :event.currentTarget/bounds), `buttons` the pressed-button bitmask
  (resolved by :event/pointer-buttons). A hover (no pressed button) or degenerate bounds is a
  no-op so passive pointer moves don't rewrite the draft."
  [state client-x client-y bounds buttons]
  (if-let [targets (policy/point->targets {:client-x client-x
                                           :client-y client-y
                                           :bounds bounds
                                           :buttons buttons})]
    (write-constraints (policy/apply-point (current-constraints state) targets))
    []))

(defn set-portfolio-optimizer-exposure-band
  "Set the gross or net band (half-width) from a slider. `axis` is :gross|:net, `value` the
  band magnitude (clamped in the policy layer)."
  [state axis value]
  (let [axis* (common/normalize-keyword-like axis)
        value* (common/parse-number-value value)]
    (if (and (contains? #{:gross :net} axis*) (some? value*))
      (write-constraints (policy/apply-band (current-constraints state) axis* value*))
      [])))

(defn apply-portfolio-optimizer-exposure-preset
  "Apply a named positioning preset (:conservative|:balanced|:high-gross|:long-bias)."
  [state preset]
  (let [preset* (common/normalize-keyword-like preset)]
    (if (contains? policy/presets preset*)
      (write-constraints (policy/apply-preset (current-constraints state) preset*))
      [])))

(defn reset-portfolio-optimizer-constraints-to-system
  "Replace the draft constraints with the system defaults."
  [_state]
  (write-constraints (:constraints (defaults/default-draft))))
