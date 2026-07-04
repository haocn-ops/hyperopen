(ns hyperopen.portfolio.optimizer.application.execution-carryover
  "Resting carryover: stale open orders left on the book by PREVIOUS optimizer
  execution runs.

  A \"carryover\" entry records one resting order a previous run left on the book, so a
  later run can cancel it before submitting — otherwise the stale order fills on top of
  the new orders and over-allocates the account (the new plan's deltas are computed from
  settled positions only, never from open orders). Entries live at
  contracts/execution-resting-carryover-path — OUTSIDE execution-path, which is replaced
  wholesale on staging / discard / restage / ledger-apply, exactly the moments the
  carryover must survive."
  (:require [hyperopen.portfolio.optimizer.application.execution :as execution]))

(defn norm-oid
  "Exchange order id normalized to a non-empty string, for set membership across the
   number/string shapes the feeds and responses use. nil when absent/blank."
  [v]
  (when (some? v)
    (let [s (str v)]
      (when (seq s) s))))

(defn order-oid
  "oid of a live open-order row, mirroring the trading screen's resolver (oid may be
   top-level or nested under :order, as a number or string)."
  [o]
  (norm-oid (or (:oid o) (:o o)
                (get-in o [:order :oid]) (get-in o [:order :o]))))

(defn open-oids
  "Set of oids currently live on the own-account book, or nil when the book has not
   hydrated yet (so callers treat 'absent from book' as 'unknown', never as gone)."
  [state]
  (when (true? (get-in state [:orders :open-orders-hydrated?]))
    (into #{} (keep order-oid) (get-in state [:orders :open-orders]))))

(defn- parse-int-value
  [value]
  (let [num (cond
              (number? value) value
              (string? value) (js/parseInt value 10)
              :else js/NaN)]
    (when (and (number? num)
               (not (js/isNaN num)))
      (js/Math.floor num))))

(defn- request-asset-idx
  [row]
  (get-in row [:request :action :orders 0 :a]))

(defn carryover-entry
  "Carryover record for a ledger row that rested on the book, capturing everything a
   later run needs to CANCEL it: the exchange oid (from the settled response) and the
   wire asset index (from the row's own frozen order request, so no market-metadata
   resolution is needed at cancel time). nil for non-resting rows."
  [attempt-id row]
  (when-let [oid (execution/resting-oid row)]
    (cond-> {:oid oid
             :coin (execution/coin-for-row row)
             :instrument-id (:instrument-id row)
             :side (:side row)
             :quantity (:quantity row)
             :attempt-id attempt-id}
      (some? (request-asset-idx row)) (assoc :asset-id (request-asset-idx row)))))

(defn merge-resting-carryover
  "Adds `entries` into the carryover vector, keyed by normalized oid (a newer entry for
   an oid already present replaces the old one). Entries without a usable oid are dropped."
  [carryover entries]
  (let [entries* (filterv #(some? (norm-oid (:oid %))) (or entries []))
        new-oids (into #{} (map #(norm-oid (:oid %))) entries*)]
    (-> (vec (remove #(contains? new-oids (norm-oid (:oid %))) (or carryover [])))
        (into entries*))))

(defn prune-resting-carryover
  "Removes the entries whose oid is in `oids` (any number/string shape) — used after a
   pre-run cancellation the exchange accepted."
  [carryover oids]
  (let [gone (into #{} (keep norm-oid) (or oids []))]
    (if (seq gone)
      (vec (remove #(contains? gone (norm-oid (:oid %))) (or carryover [])))
      (vec (or carryover [])))))

(defn record-resting-carryover
  "Folds a settled ledger into the carryover: prunes the oids a successful pre-run
   cancellation removed from the book, then adds the ledger's own resting rows."
  [carryover ledger]
  (-> carryover
      (cond->
       (= :ok (get-in ledger [:cancellations :status]))
        (prune-resting-carryover (get-in ledger [:cancellations :oids])))
      (merge-resting-carryover
       (keep #(carryover-entry (:attempt-id ledger) %) (:rows ledger)))))

(defn live-resting-carryover
  "Carryover entries still (as far as we can tell) open on the live book. Once the
   own-account open-orders feed has hydrated, entries whose oid is absent are dropped
   (they filled or were cancelled elsewhere). Before hydration every entry is kept —
   attempting to cancel an already-gone order is a tolerated per-status error, whereas
   skipping a live one recreates the double-allocation bug the carryover exists to
   prevent."
  [state carryover]
  (let [entries (filterv #(some? (norm-oid (:oid %))) (or carryover []))
        book (open-oids state)]
    (if (set? book)
      (filterv #(contains? book (norm-oid (:oid %))) entries)
      entries)))

(defn carryover-cancels
  "Wire cancel entries {:a <asset-idx> :o <oid>} for the given carryover entries, split
   into {:cancels [...] :unresolved [...]}. An entry missing its asset index or a
   parseable oid cannot be cancelled and must halt the run — submitting new orders while
   a stale one may still fill is the over-allocation bug this cancellation prevents."
  [entries]
  (reduce (fn [acc {:keys [asset-id oid] :as entry}]
            (let [a (parse-int-value asset-id)
                  o (parse-int-value oid)]
              (if (and (some? a) (some? o))
                (update acc :cancels conj {:a a :o o})
                (update acc :unresolved conj entry))))
          {:cancels [] :unresolved []}
          (or entries [])))
