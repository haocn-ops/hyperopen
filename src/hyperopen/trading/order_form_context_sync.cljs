(ns hyperopen.trading.order-form-context-sync
  "Keep the active order form's committed size coherent with live market context.

   The order form's canonical `:size` (base units) and displayed `:size-display`
   (the quote/notional string the user sees) are inert stored fields — they are
   only re-derived inside explicit user-driven transitions (price/side/leverage
   edits, percent taps, manual size entry). An order-book tick is NOT a user
   transition, so a committed size stays frozen while spot affordability
   validation recomputes order value from the live best-ask on every render.

   When the best-ask moves up after a spot buy size was committed (or when the
   size was first derived from the mark before the book loaded), `:size` stays
   frozen and `:size * live-ask` exceeds available USDC — producing a false
   \"Not enough USDC\" reject even though the displayed Size notional still looks
   affordable against the displayed Available.

   This seam re-projects the active order form on a non-user context change (an
   order-book tick on the active market) by reusing the EXACT same cross-field
   synchronization that user price/side edits apply, so what the user sees is
   what gets validated."
  (:require [hyperopen.state.trading :as trading]
            [hyperopen.trading.order-form-ownership :as ownership]
            [hyperopen.trading.order-form-transitions :as transitions]))

(defn- size-coherence-changed?
  "True when re-projecting moved the canonical size or the displayed notional.
   Percent-only drift (the cosmetic % indicator on a manual-base size) is ignored
   so hot-path order-book ticks do not churn the store needlessly."
  [form next-form]
  (or (not= (:size form) (:size next-form))
      (not= (str (or (:size-display form) ""))
            (str (or (:size-display next-form) "")))))

(defn reconcile-active-order-form
  "Re-derive the active order form's `:size` / `:size-display` against the current
   market context and return `state` with the order-form (and its UI-owned size
   fields) updated. Returns `state` unchanged when the derived size and displayed
   notional do not move, so callers on a hot path (every order-book tick) can
   avoid redundant store writes."
  [state]
  (let [form (trading/order-form-draft state)
        next-form (transitions/reconcile-size-after-context-change state form)]
    (if (size-coherence-changed? form next-form)
      (let [transition (ownership/enforce-field-ownership state {:order-form next-form})]
        (cond-> state
          (contains? transition :order-form)
          (assoc :order-form (:order-form transition))

          (contains? transition :order-form-ui)
          (assoc :order-form-ui (:order-form-ui transition))))
      state)))
