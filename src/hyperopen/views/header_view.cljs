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
   "tracking-[-0.12em]"
   "italic"
   "select-none"])

(def brand-wordmark-classes
  ["brand-wordmark"
   "text-primary"
   "font-black"
   "tracking-[-0.06em]"
   "italic"
   "select-none"])

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
  (let [{:keys [account-selector brand desktop-nav-items mobile-menu-open? mobile-nav more-nav settings spectate wallet]}
        (vm/header-vm state)]
    [:div {:class ["contents"]
           :data-role "header-shell"}
     [:header.bg-base-200.border-b.border-base-300.w-full
      {:data-parity-id "header"}
      [:div {:class ["w-full" "app-shell-gutter" "py-2" "md:py-3"]}
       [:div {:class ["flex" "items-center" "gap-2" "md:gap-4"]}
        [:div {:class ["flex" "items-center" "gap-2.5" "md:gap-3" "min-w-0"]}
         (navigation/render-mobile-menu mobile-nav spectate mobile-menu-open?)
         [:button {:type "button"
                   :class ["md:hidden" "inline-flex" "items-center" "rounded-lg" "px-1" "py-0.5"]
                   :on {:click [[:actions/navigate "/trade"]]}
                   :data-role "mobile-brand"}
          [:span {:class (into ["text-lg" "leading-none"]
                               brand-mark-classes)}
           (:mark brand)]]
         [:div {:class ["hidden" "md:flex" "flex-col" "justify-center"]}
          [:span {:class (into ["text-xl" "leading-none" "sm:text-3xl"]
                               brand-wordmark-classes)}
           (:wordmark brand)]
          (when (seq (:tagline brand))
            [:span {:class ["text-xs" "leading-tight" "text-ho-text-dim"
                            "italic" "whitespace-nowrap"]
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
          (settings/render-shell settings)]]]]]
     (active-subaccount-banner (:active-subaccount account-selector))]))
