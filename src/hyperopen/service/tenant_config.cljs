(ns hyperopen.service.tenant-config
  "Pure public tenant configuration boundary for white-label deployments.

   Tenant configuration is deliberately small and public. Unknown or malformed
   input resolves to the safe default atomically; no caller can accidentally
   enable a route or persist signing material through this boundary."
  (:require [clojure.string :as str]
            [hyperopen.ui.theme :as ui-theme]))

(def ^:private known-themes
  (into #{} (map :id) ui-theme/themes))
(def ^:private known-features #{:terminal :analytics :affiliate})
(def ^:private affiliate-statuses #{:configured :enabled :disabled :unavailable})
(def ^:private builder-fee-statuses #{:configured :disabled})
(def ^:private builder-fee-fields
  #{:status :builder-address :fee-tenths-bp :disclosure :max-fee-rate})
(def ^:private secret-key-pattern
  #"(?i)(private[-_ ]?key|seed[-_ ]?phrase|api[-_ ]?secret|access[-_ ]?token|raw[-_ ]?signature|mnemonic)")
(def ^:private secret-value-pattern
  #"(?i)(sk_(?:live|test)_[A-Za-z0-9_-]+|0x[0-9a-f]{32,}|(?:seed|private)[-_ ]?(?:phrase|key)|access[-_ ]?token)")
(def ^:private url-pattern #"^https://[^\s]+$")
(def ^:private max-affiliate-endpoint-length 2048)

(def ^:private builder-address-pattern #"^0x[0-9a-f]{40}$")

(def default-tenant-raw
  {:tenant/id "hyperopen-default"
   :brand/name "Hyperopen"
   :brand/logo-url ""
   :theme/id "dark"
   :features {:terminal true :analytics true :affiliate false}
   :venue {:id :hyperliquid
           :label "Hyperliquid"
           :url "https://app.hyperliquid.xyz"}
   :affiliate {:provider nil
               :id nil
               :status :unavailable
               :referral-url ""
               :event-endpoint ""
               :disclosure "官方 affiliate 服务当前不可用；交易不受影响。"}
   :builder-fee {:status :disabled
                 :builder-address nil
                 :fee-tenths-bp nil
                 :disclosure "No DEXHelm builder fee is active in this release."}})

(defn- key-name
  [key]
  (cond
    (keyword? key) (str key)
    (string? key) key
    :else (str key)))

(defn contains-secret?
  "Returns true when a public config/value contains secret-shaped keys or values."
  [value]
  (cond
    (map? value)
    (or (some #(re-find secret-key-pattern (key-name %)) (keys value))
        (some (fn [[key nested]]
                (and (not (and (or (= :builder-address key)
                                   (= "builder-address" (key-name key)))
                               (string? nested)
                               (re-matches builder-address-pattern nested)))
                     (contains-secret? nested)))
              value))

    (sequential? value)
    (boolean (some contains-secret? value))

    (string? value)
    (boolean (re-find secret-value-pattern value))

    :else false))

(defn- non-empty-string?
  [value]
  (and (string? value) (seq (str/trim value))))

(defn- public-url?
  [value]
  (or (= "" value)
      (and (string? value) (re-matches url-pattern value))))

(defn normalize-affiliate-event-endpoint
  [value]
  (let [endpoint (some-> value str str/trim)]
    (when (and (seq endpoint)
               (<= (count endpoint) max-affiliate-endpoint-length))
      (try
        (let [parsed (js/URL. endpoint)
              port (.-port parsed)]
          (when (and (= "https:" (.-protocol parsed))
                     (seq (.-hostname parsed))
                     (empty? (.-username parsed))
                     (empty? (.-password parsed))
                     (empty? (.-hash parsed))
                     (or (empty? port) (= "443" port)))
            (.-href parsed)))
        (catch :default _
          nil)))))

(defn valid-affiliate-event-endpoint?
  [value]
  (boolean (normalize-affiliate-event-endpoint value)))

(defn max-fee-rate
  [fee-tenths-bp]
  (when (and (integer? fee-tenths-bp)
             (<= 1 fee-tenths-bp 100))
    (let [fraction (-> (str "000" fee-tenths-bp)
                       (subs (- (count (str "000" fee-tenths-bp)) 3))
                       (str/replace #"0+$" ""))]
      (str "0." fraction "%"))))

(defn- valid-builder-fee?
  [builder-fee]
  (let [{:keys [status builder-address fee-tenths-bp disclosure]} builder-fee
        max-fee-rate-value (:max-fee-rate builder-fee)]
    (and (map? builder-fee)
         (every? builder-fee-fields (keys builder-fee))
         (contains? builder-fee-statuses status)
         (non-empty-string? disclosure)
         (if (= :disabled status)
           (and (nil? builder-address)
                (nil? fee-tenths-bp)
                (nil? max-fee-rate-value))
           (and (string? builder-address)
                (re-matches builder-address-pattern builder-address)
                (integer? fee-tenths-bp)
                (<= 1 fee-tenths-bp 100)
                (= (max-fee-rate fee-tenths-bp) max-fee-rate-value))))))

(defn- normalize-builder-fee
  [raw]
  (let [builder-fee (if (map? raw) raw {})
        status (get builder-fee :status)
        disclosure (get builder-fee :disclosure)
        builder-address (get builder-fee :builder-address)
        fee-tenths-bp (get builder-fee :fee-tenths-bp)]
    (cond
      (= status :disabled)
      {:status :disabled
       :builder-address nil
       :fee-tenths-bp nil
       :disclosure disclosure}

      (= status :configured)
      (when (and (string? builder-address)
                 (re-matches builder-address-pattern builder-address)
                 (integer? fee-tenths-bp)
                 (<= 1 fee-tenths-bp 100))
        {:status :configured
         :builder-address builder-address
         :fee-tenths-bp fee-tenths-bp
         :disclosure disclosure
         :max-fee-rate (max-fee-rate fee-tenths-bp)})

      :else nil)))

(defn normalize-tenant-theme-id
  "Normalize a tenant theme id against the UI theme catalog.

   `default` is retained as a legacy deployment alias for the current UI
   default; unknown values return nil so the whole tenant override falls back."
  [value]
  (let [candidate (cond
                    (= :default value) ui-theme/default-theme-id
                    (= "default" value) ui-theme/default-theme-id
                    (keyword? value) (name value)
                    (string? value) (str/trim value)
                    :else nil)]
    (when (ui-theme/valid-theme-id? candidate)
      (ui-theme/normalize-theme-id candidate))))

(defn valid-tenant-config?
  "Validates an already-normalized tenant configuration."
  [tenant]
  (and (map? tenant)
       (non-empty-string? (:tenant/id tenant))
       (non-empty-string? (:brand/name tenant))
       (public-url? (:brand/logo-url tenant))
       (contains? known-themes (:theme/id tenant))
       (= known-features (set (keys (:features tenant))))
       (every? boolean? (vals (:features tenant)))
       (or (true? (get-in tenant [:features :terminal]))
           (true? (get-in tenant [:features :analytics])))
       (= :hyperliquid (get-in tenant [:venue :id]))
       (non-empty-string? (get-in tenant [:venue :label]))
       (public-url? (get-in tenant [:venue :url]))
       (contains? affiliate-statuses (get-in tenant [:affiliate :status]))
       (let [endpoint (or (get-in tenant [:affiliate :event-endpoint]) "")
             enabled? (= :enabled (get-in tenant [:affiliate :status]))]
         (if (seq endpoint)
           (and enabled?
                (true? (get-in tenant [:features :affiliate]))
                (valid-affiliate-event-endpoint? endpoint))
           (not enabled?)))
       (if (contains? #{:configured :enabled} (get-in tenant [:affiliate :status]))
         (and (= :hyperliquid (get-in tenant [:affiliate :provider]))
              (non-empty-string? (get-in tenant [:affiliate :id]))
              (public-url? (get-in tenant [:affiliate :referral-url])))
         (and (nil? (get-in tenant [:affiliate :provider]))
              (nil? (get-in tenant [:affiliate :id]))
              (= "" (get-in tenant [:affiliate :referral-url]))))
       (non-empty-string? (get-in tenant [:affiliate :disclosure]))
       (valid-builder-fee? (:builder-fee tenant))
       (not (contains-secret? tenant))))

(defn- normalize-features
  [features]
  (let [features* (if (map? features) features {})]
    (into {}
          (map (fn [feature]
                 [feature (true? (get features* feature))]))
          known-features)))

(defn- normalize-tenant
  [raw]
  (let [raw* (if (map? raw) raw {})
        tenant {:tenant/id (:tenant/id raw*)
                :brand/name (:brand/name raw*)
                :brand/logo-url (or (:brand/logo-url raw*) "")
                :theme/id (normalize-tenant-theme-id (:theme/id raw*))
                :features (normalize-features (:features raw*))
                :venue {:id (get-in raw* [:venue :id])
                        :label (get-in raw* [:venue :label])
                        :url (get-in raw* [:venue :url])}
                :affiliate {:provider (get-in raw* [:affiliate :provider])
                            :id (get-in raw* [:affiliate :id])
                            :status (or (get-in raw* [:affiliate :status]) :unavailable)
                            :referral-url (get-in raw* [:affiliate :referral-url])
                            :event-endpoint
                            (let [endpoint (or (get-in raw* [:affiliate :event-endpoint]) "")]
                                  (or (normalize-affiliate-event-endpoint endpoint)
                                  endpoint))
                            :disclosure (get-in raw* [:affiliate :disclosure])}
                :builder-fee (normalize-builder-fee
                              (or (:builder-fee raw*)
                                  (:builder-fee default-tenant-raw)))}]
    (when (and (contains? known-themes (:theme/id tenant))
               (valid-tenant-config? tenant))
      tenant)))

(defn normalize-tenant-config
  "Normalize public tenant data, falling back atomically to the default tenant."
  [raw]
  (or (normalize-tenant raw)
      (normalize-tenant default-tenant-raw)))

(defn build-time-tenant-config
  "Normalize a build-time tenant override, falling back atomically to default."
  [override]
  (normalize-tenant-config override))

(defn active-tenant-config
  "Select the validated override when present, otherwise the default tenant."
  [state]
  (normalize-tenant-config (or (:tenant/override (or state {}))
                               default-tenant-raw)))

(defn active-builder-fee-config
  "Select a strictly normalized builder-fee subconfig from runtime state."
  [state]
  (let [builder-fee (get-in state [:tenant/override :builder-fee])]
    (if (and (map? builder-fee)
             (every? builder-fee-fields (keys builder-fee))
             (not (contains-secret? builder-fee)))
      (:builder-fee
       (normalize-tenant-config
        (assoc default-tenant-raw :builder-fee builder-fee)))
      (:builder-fee (active-tenant-config state)))))

(defn canonical-serialize
  "Stable, insertion-order independent serialization for public config."
  [value]
  (letfn [(canonical [item]
            (cond
              (map? item)
              (->> item
                   (map (fn [[key val]] [key (canonical val)]))
                   (sort-by (fn [[key _]] [(if (keyword? key) 0 1) (key-name key)]))
                   vec)

              (set? item)
              (vec (sort-by str (map canonical item)))

              (sequential? item)
              (mapv canonical item)

              :else item))]
    (pr-str (canonical value))))

(defn enabled-routes
  "Canonical public routes enabled by the product feature flags."
  [tenant]
  (let [tenant* (normalize-tenant-config tenant)]
    (cond-> []
      (true? (get-in tenant* [:features :terminal])) (conj "/trade")
      (true? (get-in tenant* [:features :analytics])) (conj "/portfolio"))))
