(ns hyperopen.views.spectate-mode-modal.import-export)

(defn- download-icon
  []
  [:svg {:viewBox "0 0 20 20"
         :class ["h-3.5" "w-3.5"]
         :fill "none"
         :stroke "currentColor"
         :stroke-width "1.7"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :aria-hidden "true"}
   [:path {:d "M10 3v9"}]
   [:path {:d "M6.5 8.5 10 12l3.5-3.5"}]
   [:path {:d "M4 14.5V16h12v-1.5"}]])

(defn- upload-icon
  []
  [:svg {:viewBox "0 0 20 20"
         :class ["h-3.5" "w-3.5"]
         :fill "none"
         :stroke "currentColor"
         :stroke-width "1.7"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :aria-hidden "true"}
   [:path {:d "M10 12V3"}]
   [:path {:d "M6.5 6.5 10 3l3.5 3.5"}]
   [:path {:d "M4 14.5V16h12v-1.5"}]])

(defn- toolbar-button
  [{:keys [label aria-label title on-click data-role disabled?]} icon]
  [:button {:type "button"
            :class (cond-> ["inline-flex"
                            "items-center"
                            "gap-1.5"
                            "rounded-lg"
                            "border"
                            "border-base-300"
                            "bg-base-200"
                            "px-2.5"
                            "py-1.5"
                            "text-xs"
                            "font-medium"
                            "leading-none"
                            "text-gray-200"]
                     (not disabled?) (into ["hover:bg-base-300"
                                            "hover:text-gray-100"])
                     disabled? (into ["cursor-not-allowed"
                                      "opacity-45"]))
            :on (when-not disabled? {:click on-click})
            :aria-label aria-label
            :title title
            :disabled disabled?
            :data-role data-role}
   icon
   [:span label]])

(defn import-export-toolbar
  [{:keys [has-saved-watchlist?]}]
  [:div {:class ["flex"
                 "items-center"
                 "justify-between"
                 "gap-2"
                 "px-4"
                 "pt-1"
                 "pb-2"]
         :data-role "spectate-mode-watchlist-toolbar"}
   [:span {:class ["text-xs" "font-medium" "text-gray-400"]}
    "Saved addresses"]
   [:div {:class ["flex" "items-center" "gap-1.5"]}
    (toolbar-button
     {:label "Import"
      :aria-label "Import watchlist from file"
      :title "Import watchlist from a JSON file"
      :on-click [[:actions/import-spectate-mode-watchlist]]
      :data-role "spectate-mode-watchlist-import"}
     (upload-icon))
    (toolbar-button
     {:label "Export"
      :aria-label "Export watchlist to file"
      :title "Export watchlist to a JSON file"
      :on-click [[:actions/export-spectate-mode-watchlist]]
      :data-role "spectate-mode-watchlist-export"
      :disabled? (not has-saved-watchlist?)}
     (download-icon))]])
