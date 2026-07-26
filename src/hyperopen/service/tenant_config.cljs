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
(def ^:private secret-key-pattern
  #"(?i)(private[-_ ]?key|seed[-_ ]?phrase|api[-_ ]?secret|access[-_ ]?token|raw[-_ ]?signature|mnemonic)")
(def ^:private secret-value-pattern
  #"(?i)(sk_(?:live|test)_[A-Za-z0-9_-]+|0x[0-9a-f]{32,}|(?:seed|private)[-_ ]?(?:phrase|key)|access[-_ ]?token)")
(def ^:private url-pattern #"^https://[^\s]+$")

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
               :disclosure "官方 affiliate 服务当前不可用；交易不受影响。"}})

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
        (some contains-secret? (vals value)))

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
       (public-url? (or (get-in tenant [:affiliate :event-endpoint]) ""))
       (if (contains? #{:configured :enabled} (get-in tenant [:affiliate :status]))
         (and (= :hyperliquid (get-in tenant [:affiliate :provider]))
              (non-empty-string? (get-in tenant [:affiliate :id]))
              (public-url? (get-in tenant [:affiliate :referral-url])))
         (and (nil? (get-in tenant [:affiliate :provider]))
              (nil? (get-in tenant [:affiliate :id]))
              (= "" (get-in tenant [:affiliate :referral-url]))))
       (non-empty-string? (get-in tenant [:affiliate :disclosure]))
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
                            :event-endpoint (or (get-in raw* [:affiliate :event-endpoint]) "")
                            :disclosure (get-in raw* [:affiliate :disclosure])}}]
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
