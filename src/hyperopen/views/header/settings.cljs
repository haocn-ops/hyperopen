(ns hyperopen.views.header.settings
  (:require [hyperopen.views.header.dom :as dom]
            [hyperopen.views.header.icons :as icons]
            [hyperopen.views.ui.toggle :as toggle]))

(defn- confirmation-strip
  [{:keys [body cancel-action confirm-action confirm-label title]}]
  (when title
    [:div {:class ["ts-confirmation"]
           :data-role "trading-settings-storage-mode-confirmation"}
     [:div {:class ["ts-confirmation-text"]}
      [:div {:class ["ts-confirmation-title"]} title]
      [:p {:class ["ts-confirmation-body"]} body]]
     [:div {:class ["ts-confirmation-actions"]}
      [:button {:type "button"
                :class ["ts-confirmation-btn"]
                :on {:click cancel-action}}
       "Cancel"]
      [:button {:type "button"
                :class ["ts-confirmation-btn" "primary"]
                :on {:click confirm-action}}
       confirm-label]]]))

(defn- row-icon
  [{:keys [checked? data-role disabled? icon-kind]}]
  [:div {:class ["ts-row-icon" (when disabled? "is-disabled")]
         :data-role (str data-role "-icon")}
   (icons/trading-settings-row-icon icon-kind checked?)])

(defn- choice-option
  [row-data-role {:keys [action active? label tooltip value]}]
  (let [tooltip-id (str row-data-role "-option-" value "-tooltip")]
    [:span {:class ["ts-choice-option-wrap"]
            :replicant/key (str row-data-role ":" value)}
     [:button {:type "button"
               :class ["ts-choice-option" (when active? "is-active")]
               :data-role (str row-data-role "-option-" value)
               :aria-describedby (when tooltip tooltip-id)
               :aria-pressed (boolean active?)
               :on {:click action}}
      label]
     (when tooltip
       [:span {:id tooltip-id
               :role "tooltip"
               :class ["ts-choice-tooltip"]
               :data-role tooltip-id}
        tooltip])]))

(defn- choice-row
  [{:keys [data-role hint options title] :as row}]
  [:div {:class ["ts-row" "ts-row-choice"]
         :data-role data-role}
   (row-icon row)
   [:div {:class ["ts-row-text"]}
    [:div {:class ["ts-row-label"]} title]
    [:div {:class ["ts-row-hint"]} hint]]
   (into
    [:div {:class ["ts-choice"]
           :role "group"
           :aria-label title}]
    (map (partial choice-option data-role) options))])

(defn- toggle-row
  [{:keys [checked? confirmation data-role disabled? hint on-change title] :as row}]
  [:div {:class ["ts-row" (when disabled? "is-disabled")]
         :data-role data-role}
   (row-icon row)
   [:div {:class ["ts-row-text"]}
    [:div {:class ["ts-row-label"]} title]
    [:div {:class ["ts-row-hint"]} hint]]
   (toggle/toggle {:on? (boolean checked?)
                   :aria-label title
                   :data-role (str data-role "-toggle")
                   :disabled? (boolean disabled?)
                   :on-change on-change})
   (confirmation-strip confirmation)])

(defn- setting-row
  [{:keys [kind] :as row}]
  (if (= :choice kind)
    (choice-row row)
    (toggle-row row)))

(defn- settings-section
  [{:keys [data-role hint rows title]}]
  [:section {:class ["ts-group"]
             :data-role data-role}
   [:div {:class ["ts-group-head"]}
    [:span {:class ["ts-group-label"]} title]
    [:span {:class ["ts-group-hint"]} hint]]
   (into
    [:div {:class ["ts-group-card"]}]
    (map setting-row rows))])

(defn render-trigger
  [{:keys [open? return-focus? trigger-action trigger-key]}]
  [:button {:type "button"
            :class ["ts-trigger"]
            :on {:click trigger-action}
            :replicant/key trigger-key
            :replicant/on-render (when return-focus?
                                   dom/focus-visible-node!)
            :aria-haspopup "dialog"
            :aria-expanded open?
            :title "Trading settings"
            :aria-label "Trading settings"
            :data-role "header-settings-button"}
   (icons/gear-line-icon {:class ["h-3.5" "w-3.5"]})])

(defn- popover
  [{:keys [close-actions footer-note sections title]}]
  [:section {:class ["ts-pop"
                     "fixed"
                     "right-4"
                     "top-[56px]"
                     "z-[285]"
                     "w-[min(400px,calc(100vw-32px))]"]
             :role "dialog"
             :aria-label "Trading settings"
             :tabindex "0"
             :data-role "trading-settings-panel"
             :style {:transition "transform 180ms cubic-bezier(0.2,0.9,0.3,1), opacity 180ms ease"
                     :transform "translateY(0) scale(1)"
                     :transform-origin "top right"
                     :opacity 1}
             :replicant/mounting {:style {:transform "translateY(8px) scale(0.98)"
                                          :opacity 0}}
             :replicant/unmounting {:style {:transform "translateY(8px) scale(0.98)"
                                            :opacity 0}}
             :replicant/on-render dom/focus-visible-node!}
   [:div {:class ["ts-pop-head"]}
    [:div {:class ["ts-pop-head-l"]}
     [:div {:class ["ts-pop-icon"]}
      (icons/gear-line-icon {:class ["h-3.5" "w-3.5"]})]
     [:div
      [:div {:class ["ts-pop-title"]
             :data-role "trading-settings-title"}
       title]]]
    [:button {:type "button"
              :class ["ts-close"]
              :aria-label "Close trading settings"
              :data-role "trading-settings-close"
              :on {:click close-actions}}
     (icons/close-icon {:class ["h-3.5" "w-3.5"]})]]
   [:div {:class ["ts-pop-body"]}
    (for [{:keys [id] :as section} sections]
      ^{:key (str "settings-section:" (name id))}
      (settings-section section))]
   [:div {:class ["ts-pop-foot"]
          :data-role "trading-settings-footer-note"}
    (icons/device-icon {:class ["h-3" "w-3"]})
    [:span footer-note]]])

(defn render-shell
  [{:keys [close-actions open?] :as settings}]
  (when open?
    (list
     [:button {:type "button"
               :class ["ts-dim"]
               :aria-label "Dismiss trading settings"
               :data-role "trading-settings-backdrop"
               :on {:click close-actions}}]
     (popover settings))))
