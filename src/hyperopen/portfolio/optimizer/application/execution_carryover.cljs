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
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.execution :as execution]))

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

(defn- rows-from-open-orders-value
  "Order rows from one open-orders state value. The websocket `openOrders` channel
   stores the WHOLE payload map {:dex \"\" :user 0x… :orders [...]} at
   [:orders :open-orders] (verified live 2026-07-04) — treating that map as a row seq
   yields MapEntries and zero oids, which silently emptied the oid set and broke the
   pre-run cancellation filter. Mirrors the account tab's open-orders-seq."
  [v]
  (cond
    (nil? v) []
    (sequential? v) v
    (map? v) (let [nested (or (:orders v) (:openOrders v) (:data v))]
               (cond
                 (sequential? nested) nested
                 (map? (:order v)) [v]
                 :else []))
    :else []))

(defn- row-coin
  [o]
  (let [coin (or (:coin o) (get-in o [:order :coin]))]
    (when (some? coin)
      (let [s (str coin)]
        (when (seq s) s)))))

(defn- attach-dex-coin
  "Namespaces a bare per-dex snapshot coin with its dex (\"ORCL\" on dex \"xyz\" →
   \"xyz:ORCL\") so it matches plan instrument coins, which carry the dex prefix."
  [o dex]
  (let [coin (row-coin o)
        dex* (when (some? dex) (let [s (str dex)] (when (seq s) s)))]
    (if (and (map? o) coin dex*
             (not (str/includes? coin ":")))
      (assoc o :coin (str dex* ":" coin))
      o)))

(defn open-order-rows
  "Every open order the app currently knows about, merged across ALL live sources —
   no single source is complete:
   - [:orders :open-orders]            websocket feed (default dex only, payload-map shape)
   - [:orders :open-orders-snapshot]   frontendOpenOrders REST snapshot (carries cloid)
   - [:orders :open-orders-snapshot-by-dex]  per-dex snapshots (carry cloid; bare coins
                                              namespaced with their dex)
   - [:webdata2 :openOrders]           webData2 stream
   Deduped by oid (first source wins)."
  [state]
  (let [flat (rows-from-open-orders-value (get-in state [:orders :open-orders]))
        snapshot (rows-from-open-orders-value (get-in state [:orders :open-orders-snapshot]))
        by-dex (mapcat (fn [[dex rows]]
                         (map #(attach-dex-coin % dex)
                              (rows-from-open-orders-value rows)))
                       (get-in state [:orders :open-orders-snapshot-by-dex]))
        wd2 (rows-from-open-orders-value (get-in state [:webdata2 :openOrders]))]
    (:out (reduce (fn [{:keys [seen out] :as acc} row]
                    (let [oid (order-oid row)]
                      (cond
                        (nil? oid) acc
                        (contains? seen oid) acc
                        :else {:seen (conj seen oid) :out (conj out row)})))
                  {:seen #{} :out []}
                  (concat flat snapshot by-dex wd2)))))

(defn open-oids
  "Set of oids currently live on the own-account book (across every source), or nil
   when the book has not hydrated yet (so callers treat 'absent from book' as
   'unknown', never as gone)."
  [state]
  (when (or (true? (get-in state [:orders :open-orders-hydrated?]))
            (seq (open-order-rows state)))
    (into #{} (keep order-oid) (open-order-rows state))))

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
