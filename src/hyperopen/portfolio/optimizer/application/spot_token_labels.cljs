(ns hyperopen.portfolio.optimizer.application.spot-token-labels
  "Resolve human spot token display names (e.g. \"PURR\") from Hyperliquid
   spotMeta, independent of the live market catalog.

   Dust/illiquid spot pairs carry a bare \"@113\" pair reference as their balance
   coin and can be missing — or name-degraded to that same \"@113\" — in the
   market catalog (the catalog drops pairs that lack an asset-context). spotMeta
   still names them, mirroring the account balances projection, so callers can
   stamp a real :base/:symbol on a spot exposure even when the catalog can't."
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def ^:private non-blank-text coercion/non-blank-text)
(def ^:private parse-number coercion/parse-float-number)
(def ^:private finite-number? coercion/finite-number?)

(defn at-reference?
  "True when a spot identifier is a bare pair reference like \"@113\" rather than
   a human token symbol."
  [value]
  (boolean (some-> value str str/trim (str/starts-with? "@"))))

(defn- token-name-by-index
  "{token-index -> token name} from spotMeta :tokens."
  [meta]
  (into {}
        (keep (fn [{:keys [index name]}]
                (when-let [token-name (non-blank-text name)]
                  (when (some? index)
                    [index token-name]))))
        (:tokens meta)))

(defn- base-index-by-coin
  "{pair coin (e.g. \"@113\") -> base token index} from spotMeta :universe. Keyed
   by the entry :name because the @<n> coin is the universe entry name, not its
   numeric :index (which is decoupled from the number in @<n>)."
  [meta]
  (into {}
        (keep (fn [{:keys [name tokens]}]
                (when-let [pair-coin (non-blank-text name)]
                  (when (sequential? tokens)
                    [pair-coin (first tokens)]))))
        (:universe meta)))

(defn- parse-token-index
  "Coerce a spot balance :token field (number, \"113\", or \"@113\") to a token
   index."
  [token]
  (cond
    (number? token)
    (js/Math.floor token)

    (string? token)
    (let [stripped (str/replace (str/trim token) #"^@" "")]
      (when (seq stripped)
        (let [parsed (parse-number stripped)]
          (when (finite-number? parsed)
            (js/Math.floor parsed)))))

    :else nil))

(defn resolver
  "Build a reusable spotMeta token resolver from app state's [:spot :meta]."
  [meta]
  {:name-by-index (token-name-by-index meta)
   :base-index-by-coin (base-index-by-coin meta)})

(defn display-fields
  "Resolve {:base BASE :symbol \"BASE/USDC\"} for a spot balance from spotMeta.
   Prefers the balance's own :token index, then maps the @<n> coin through its
   universe entry's base token. Returns nil when spotMeta cannot name the token,
   so callers keep their market-derived behavior."
  [{:keys [name-by-index base-index-by-coin]} coin token]
  (when-let [base (or (get name-by-index (parse-token-index token))
                      (some->> (get base-index-by-coin coin)
                               (get name-by-index)))]
    {:base base
     :symbol (str base "/USDC")}))
