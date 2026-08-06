(ns hyperopen.views.header-view
  (:require [hyperopen.views.header.navigation :as navigation]
            [hyperopen.views.header.settings :as settings]
            [hyperopen.views.header.spectate :as spectate]
            [hyperopen.views.header.vm :as vm]
            [hyperopen.views.header.wallet :as wallet]))

(def brand-mark-classes
  ["brand-mark"
   "text-primary"
   "font-black"
   "tracking-normal"
   "italic"
   "select-none"])

(def brand-wordmark-classes
  ["brand-wordmark"
   "text-primary"
   "font-black"
   "tracking-normal"
   "italic"
   "select-none"])

(defn- brand-logo
  [product-context brand image-role fallback-role class]
  (let [logo-url (get-in product-context [:brand :logo-url])
        brand-name (or (get-in product-context [:brand :name]) "Hyperopen")
        initial (subs brand-name 0 1)]
    (when (and (string? logo-url)
               (seq logo-url)
               (not (true? (:logo-failed? brand))))
      [:span {:class ["relative" "inline-flex" "shrink-0" "items-center" "justify-center"]
              :data-role (str image-role "-shell")}
       [:span {:class ["absolute" "inset-0" "inline-flex" "items-center" "justify-center"
                       "rounded-md" "bg-base-300" "font-black" "text-primary"]
               :data-role fallback-role}
        initial]
       [:img {:src logo-url
              :alt (str brand-name " logo")
              :class (into ["relative"] class)
              :on {:error [[:actions/mark-brand-logo-failed logo-url]]}
              :data-role image-role}]])))

(defn- brand-fallback
  [brand role class]
  [:span {:class (into ["inline-flex" "items-center" "justify-center"] class)
          :data-role role}
   (:mark brand)])

(defn- active-subaccount-banner
  [{:keys [name]}]
  (when (seq name)
    [:div {:class ["w-full"
                   "bg-[#56dcca]"
                   "px-3"
                   "py-2"
                   "text-xs"
                   "font-medium"
                   "text-black"
                   "sm:px-4"]
           :data-role "header-subaccount-active-banner"}
     (str "IMPORTANT: You are trading on behalf of your sub-account " name)]))

(defn header-view
  [state]
  (let [{:keys [account-selector brand desktop-nav-items mobile-menu-open? mobile-nav more-nav settings spectate wallet product-context]}
        (vm/header-vm state)]
    [:div {:class ["contents"]
           :data-role "header-shell"}
     [:header.bg-base-200.border-b.border-base-300.w-full
      {:data-parity-id "header"}
      [:div {:class ["w-full" "app-shell-gutter" "py-2" "md:py-3"]}
       [:div {:class ["flex" "items-center" "gap-2" "md:gap-4"]}
        [:div {:class ["flex" "items-center" "gap-2.5" "md:gap-3" "min-w-0"]}
         (navigation/render-mobile-menu mobile-nav spectate mobile-menu-open?)
         [:div {:class ["flex" "min-w-0" "items-center" "gap-2"]}
          [:button {:type "button"
                    :class ["lg:hidden" "inline-flex" "shrink-0" "items-center" "rounded-lg" "px-1" "py-0.5"]
                    :on {:click [[:actions/navigate "/trade"]]}
                    :data-role "mobile-brand"}
           (or (brand-logo product-context
                           brand
                           "mobile-brand-logo"
                           "mobile-brand-fallback"
                           ["h-6" "w-6" "rounded-md" "object-contain"])
               (brand-fallback brand "mobile-brand-fallback"
                               (into ["text-lg" "leading-none"] brand-mark-classes)))]
          [:div {:class ["hidden" "lg:flex" "items-center" "gap-2"]}
           (or (brand-logo product-context
                           brand
                           "header-brand-logo"
                           "header-brand-fallback"
                           ["h-7" "w-7" "rounded-md" "object-contain"])
               (brand-fallback brand "header-brand-fallback"
                               (into ["h-7" "w-7" "text-lg"]
                                     brand-wordmark-classes)))]
          [:div {:class ["flex" "w-[7rem]" "min-w-[7rem]" "shrink-0" "flex-col"
                         "justify-center" "sm:w-[8.5rem]" "sm:min-w-[8.5rem]"
                         "md:w-[8rem]" "md:min-w-[8rem]"
                         "lg:w-auto" "lg:min-w-0"]}
           [:span {:class (into ["w-full" "truncate" "text-sm" "leading-none"
                                 "sm:text-base" "md:text-base" "lg:text-2xl"]
                                brand-wordmark-classes)
                   :data-role "header-brand-name"}
            (:wordmark brand)]
           (when (seq (:tagline brand))
             [:span {:class ["hidden" "md:inline" "text-xs" "leading-tight"
                             "text-ho-text-dim" "italic" "whitespace-nowrap"]
                     :data-role "header-brand-tagline"}
              (:tagline brand)])]]
        (navigation/render-desktop-nav desktop-nav-items more-nav)
        [:div {:class ["ml-auto" "flex" "items-center" "gap-1.5" "sm:gap-2.5" "lg:gap-4"]
               :data-parity-id "header-wallet-control"}
         [:div {:class ["inline-flex" "md:hidden" "lg:inline-flex"]}
          (spectate/render-trigger spectate)]
         (wallet/render (assoc wallet :account-selector account-selector))
         [:div {:class ["relative" "flex" "items-center" "gap-1.5" "sm:gap-2"]
                :data-role "header-settings-toolbar"}
          (settings/render-trigger settings)
          (settings/render-shell settings)]]]]]]
     (active-subaccount-banner (:active-subaccount account-selector))]))
