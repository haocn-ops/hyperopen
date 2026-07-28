(ns hyperopen.service.product-context
  "Read-only product and tenant context shared by shell surfaces."
  (:require [hyperopen.portfolio.routes :as portfolio-routes]
            [hyperopen.router :as router]
            [hyperopen.service.tenant-config :as tenant-config]))

(def ^:private configured-affiliate-statuses #{:configured :enabled})

(defn- tenant-source
  [source]
  (if (and (map? source) (contains? source :tenant/override))
    (tenant-config/active-tenant-config source)
    (tenant-config/normalize-tenant-config source)))

(defn build-product-context-view-model
  "Builds a serializable, read-only VM for product chrome and inline banners."
  [source]
  (let [tenant (tenant-source source)
        brand-name (or (:brand/name tenant) "Hyperopen")
        venue-label (or (get-in tenant [:venue :label]) "Hyperliquid")
        affiliate-status (or (get-in tenant [:affiliate :status]) :unavailable)
        attribution-status (when (map? source)
                             (get-in source [:attribution :status]))
        affiliate-enabled? (and (true? (get-in tenant [:features :affiliate]))
                                (contains? configured-affiliate-statuses affiliate-status)
                                (string? (get-in tenant [:affiliate :id])))]
    {:tenant/id (:tenant/id tenant)
     :features (:features tenant)
     :brand {:name brand-name
             :logo-url (or (:brand/logo-url tenant) "")
             :theme-id (or (:theme/id tenant) :default)}
     :venue {:id (get-in tenant [:venue :id])
             :label venue-label}
     :affiliate {:status affiliate-status
                 :attribution-status attribution-status
                 :enabled? affiliate-enabled?
                 :id (get-in tenant [:affiliate :id])
                 :disclosure (or (get-in tenant [:affiliate :disclosure]) "")}
     :brand-label brand-name
     :venue-label venue-label
     :affiliate-disclosure (or (get-in tenant [:affiliate :disclosure]) "")}))

(defn feature-enabled?
  [context feature]
  (true? (get-in context [:features feature])))

(defn route-enabled?
  "Return false only for tenant-controlled product routes that are disabled."
  [context route]
  (cond
    (router/trade-route? route)
    (feature-enabled? context :terminal)

    (portfolio-routes/portfolio-route? route)
    (feature-enabled? context :analytics)

    :else
    true))

(defn safe-route
  "Resolve disabled product routes without changing unrelated route behavior."
  [context route]
  (if (route-enabled? context route)
    route
    (cond
      (feature-enabled? context :terminal) "/trade"
      (feature-enabled? context :analytics) "/portfolio"
      ;; Tenant validation requires at least one product surface to be enabled.
      :else "/trade")))
