(ns hyperopen.views.footer.mobile-nav)

(defn- mobile-nav-icon
  [kind active?]
  (let [icon-classes (into ["h-[18px]" "w-[18px]"]
                           (if active?
                             ["text-[#61e6cf]"]
                             ["text-trading-text-secondary"]))]
    (case kind
      :markets
      [:svg {:viewBox "0 0 20 20" :fill "currentColor" :class icon-classes}
       [:path {:d "M3 11a1 1 0 011-1h1a1 1 0 011 1v5H3v-5z"}]
       [:path {:d "M8 7a1 1 0 011-1h1a1 1 0 011 1v9H8V7z"}]
       [:path {:d "M13 4a1 1 0 011-1h1a1 1 0 011 1v12h-3V4z"}]]

      :trade
      [:svg {:viewBox "0 0 20 20" :fill "currentColor" :class icon-classes}
       [:path {:fill-rule "evenodd"
               :clip-rule "evenodd"
               :d "M10 3a7 7 0 100 14 7 7 0 000-14zm1 3a1 1 0 10-2 0v4c0 .265.105.52.293.707l2.5 2.5a1 1 0 001.414-1.414L11 9.586V6z"}]]

      :account
      [:svg {:viewBox "0 0 20 20" :fill "currentColor" :class icon-classes}
       [:path {:fill-rule "evenodd"
               :clip-rule "evenodd"
               :d "M10 9a3 3 0 100-6 3 3 0 000 6zm-6 8a6 6 0 1112 0H4z"}]]

      nil)))

(defn- mobile-nav-button
  [{:keys [label active? click-action icon-kind data-role]}]
  [:button {:type "button"
            :class (into ["inline-flex"
                          "h-10"
                          "items-center"
                          "justify-center"
                          "gap-1.5"
                          "whitespace-nowrap"
                          "px-1"
                          "py-1"
                          "text-sm"
                          "font-medium"
                          "transition-colors"]
                         (if active?
                           ["text-[#61e6cf]"]
                           ["text-trading-text-secondary" "hover:text-trading-text"]))
            :on {:click click-action}
            :data-role data-role}
   (mobile-nav-icon icon-kind active?)
   [:span label]])

(defn render
  [mobile-nav]
  [:div {:class ["lg:hidden"
                 "w-full"
                 "bg-[#07161b]/95"
                 "backdrop-blur"
                 "app-shell-gutter"
                 "pt-1"
                 "pb-[calc(0.25rem+env(safe-area-inset-bottom))]"]
         :data-role "mobile-bottom-nav"}
   [:div {:class ["grid" "grid-cols-3" "gap-2"]}
    (for [item (:items mobile-nav)]
      ^{:key (:data-role item)}
      (mobile-nav-button item))]])
