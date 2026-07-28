(ns hyperopen.views.product-context
  "Small, non-interactive tenant context banner shared by route surfaces."
  (:require [hyperopen.service.product-context :as product-context]))

(defn- affiliate-status-label
  [status enabled?]
  (cond
    enabled? "Affiliate configured"
    (= :disabled status) "Affiliate disabled"
    :else "Affiliate unavailable"))

(defn- attribution-status-label
  [status]
  (case status
    :observed "Attribution observed"
    :pending "Attribution pending"
    :accepted "Attribution accepted"
    :unavailable "Attribution unavailable"
    :settled "Attribution provider-confirmed"
    nil))

(defn render-inline-banner
  [context data-role]
  (let [vm (or context (product-context/build-product-context-view-model {}))
        role (or data-role "product-tenant-context-banner")]
    [:div {:class ["flex" "flex-wrap" "items-center" "gap-x-3" "gap-y-1"
                   "border-b" "border-base-300" "bg-base-200/70" "px-3" "py-2"
                   "text-xs" "text-ho-text-dim"]
           :data-role role}
     [:span {:class ["font-semibold" "text-ho-text"]
             :data-role (str role "-brand")}
      (:brand-label vm)]
     [:span {:class ["text-ho-text-dim"]
             :data-role (str role "-venue")}
      (str "Venue: " (:venue-label vm))]
     [:span {:class ["text-ho-text-dim"]
             :data-role (str role "-affiliate-status")}
      (affiliate-status-label (get-in vm [:affiliate :status])
                              (get-in vm [:affiliate :enabled?]))]
     (when-let [status-label (attribution-status-label
                              (get-in vm [:affiliate :attribution-status]))]
       [:span {:class ["text-ho-text-dim"]
               :data-role (str role "-attribution-status")}
        status-label])
     (when (seq (:affiliate-disclosure vm))
       [:span {:class ["basis-full" "text-xs" "text-ho-text-dim"]
               :data-role (str role "-affiliate-disclosure")}
        (:affiliate-disclosure vm)])]))
