(ns hyperopen.portfolio.optimizer.application.execution-cloid
  "Client-order-id (\"cloid\") identity for optimizer orders, so a run can recognize its
  OWN resting orders on the live book with no server-side or in-memory state — the key
  to cleaning up overlapping orders across a page reload.

  A cloid is a 16-byte value Hyperliquid stores with an order (`0x` + 32 hex chars) and
  echoes back on the `frontendOpenOrders` query. Every optimizer order is stamped with a
  cloid whose leading 4 bytes are a fixed magic tag, so any order carrying that tag is
  recognizably ours regardless of which browser session placed it.

  Recognition MUST read the `frontendOpenOrders` snapshot ([:orders :open-orders-snapshot]);
  the flat websocket `openOrders` feed ([:orders :open-orders]) strips the cloid."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.execution :as execution]))

(def optimizer-cloid-prefix
  "Magic tag every optimizer cloid starts with (0x + 8 hex = 4 bytes). The remaining 12
  bytes carry per-order uniqueness. \"0770c0de\" reads as \"opt code\"."
  "0x0770c0de")

(defn- normalize-cloid
  [v]
  (when (some? v)
    (let [s (str/lower-case (str/trim (str v)))]
      (when (seq s) s))))

(defn optimizer-cloid?
  "True when `v` is a cloid bearing the optimizer magic tag."
  [v]
  (boolean (some-> (normalize-cloid v)
                   (str/starts-with? optimizer-cloid-prefix))))

(defn make-cloid
  "Full optimizer cloid = magic prefix + a 24-hex-char uniqueness suffix (12 bytes),
  yielding the 34-char `0x`+32-hex form Hyperliquid expects. The suffix is supplied by
  the caller (crypto-random in the effect, a fixed string in tests) so this stays pure."
  [suffix-hex]
  (let [hex (->> (str suffix-hex)
                 str/lower-case
                 (re-seq #"[0-9a-f]")
                 (apply str))
        padded (-> (str hex (apply str (repeat 24 "0")))
                   (subs 0 24))]
    (str optimizer-cloid-prefix padded)))

(def ^:private snapshot-path [:orders :open-orders-snapshot])

(defn snapshot-open-orders
  "Normalized rows from the cloid-bearing `frontendOpenOrders` snapshot:
  {:oid :coin :side :cloid :sz :limit-px}. Empty when the snapshot has not loaded."
  [state]
  (into []
        (keep (fn [o]
                (when (map? o)
                  (let [oid (or (:oid o) (:o o))
                        coin (some-> (:coin o) str str/trim)]
                    (when (and (some? oid) (seq (or coin "")))
                      {:oid oid
                       :coin coin
                       :side (:side o)
                       :cloid (normalize-cloid (:cloid o))
                       :sz (:sz o)
                       :limit-px (:limitPx o)})))))
        (get-in state snapshot-path)))

(defn classify-overlap
  "Splits the live open-order snapshot against a plan's ready rows:
   {:optimizer-owned   [...]  ; carry our cloid tag -> safe to auto-cancel
    :untagged-overlap  [...]} ; on a coin a ready row trades but NOT our cloid ->
                              ; user must decide (could be a manually placed order).
  Rows that are neither ours nor on a traded coin are ignored. Coin matching uses the
  plan row's bare token (execution/coin-for-row), exact-string against the snapshot
  :coin — precise for perps, best-effort for namespaced/spot symbols."
  [snapshot-rows ready-rows]
  (let [traded-coins (into #{}
                           (keep #(some-> (execution/coin-for-row %) str/trim))
                           ready-rows)]
    (reduce (fn [acc row]
              (cond
                (optimizer-cloid? (:cloid row))
                (update acc :optimizer-owned conj row)

                (contains? traded-coins (:coin row))
                (update acc :untagged-overlap conj row)

                :else acc))
            {:optimizer-owned [] :untagged-overlap []}
            snapshot-rows)))
