(ns hyperopen.views.vaults.detail.activity.table-chrome)

(defn- sort-header-button
  [tab {:keys [id label]} sort-state]
  (let [active? (= id (:column sort-state))
        direction (:direction sort-state)
        icon (when active?
               (if (= :asc direction) "↑" "↓"))]
    [:button {:type "button"
              :class (into ["group"
                            "inline-flex"
                            "items-center"
                            "gap-1"
                            "text-xs"
                            "font-medium"
                            "text-[#949e9c]"
                            "transition-colors"
                            "hover:text-ho-text"]
                           (when active?
                             ["text-ho-text"]))
              :on {:click [[:actions/sort-vault-detail-activity tab id]]}}
     [:span label]
     (when icon
       [:span {:class ["text-xs" "opacity-70"]}
        icon])]))

(defn table-header [tab columns sort-state]
  [:thead
   [:tr {:class ["border-b" "border-[#1b3237]" "bg-transparent" "text-xs" "font-medium" "text-[#949e9c]"]}
    (for [{:keys [id] :as column} columns]
      ^{:key (str "activity-header-" (name id))}
      [:th {:class ["px-4" "py-2" "text-left" "whitespace-nowrap" "font-medium"]}
       (sort-header-button tab column sort-state)])]])

(defn empty-table-row [col-span message]
  [:tr
   [:td {:col-span col-span
         :class ["px-4" "py-6" "text-left" "text-sm" "text-[#8f9ea5]"]}
    message]])

(defn error-table-row [col-span message]
  [:tr
   [:td {:col-span col-span
         :class ["px-4" "py-6" "text-left" "text-sm" "text-red-300"]}
    message]])

(defn position-pnl-class [pnl]
  (cond
    (and (number? pnl) (pos? pnl)) "text-[#1fa67d]"
    (and (number? pnl) (neg? pnl)) "text-ho-sell-hi"
    :else "text-trading-text"))

(defn side-tone-class
  [side-key]
  (case side-key
    :long "text-[#1fa67d]"
    :short "text-ho-sell-hi"
    "text-trading-text"))

(defn side-coin-tone-class
  [side-key]
  (case side-key
    :long "text-ho-accent-bright"
    :short "text-[#eaafb8]"
    "text-trading-text"))

(defn side-coin-cell-style
  [side-key]
  (case side-key
    :long {:background "linear-gradient(90deg,rgb(31,166,125) 0px,rgb(31,166,125) 4px,rgba(11,50,38,0.92) 4px,transparent 100%)"
           :padding-left "12px"}
    :short {:background "linear-gradient(90deg,rgb(237,112,136) 0px,rgb(237,112,136) 4px,rgba(52,36,46,0.92) 4px,transparent 100%)"
            :padding-left "12px"}
    nil))

(defn interactive-value-class
  []
  ["underline" "decoration-dotted" "underline-offset-2"])

(defn status-tone-class
  [status-key]
  (case status-key
    :positive "text-[#1fa67d]"
    :negative "text-ho-sell-hi"
    :neutral "text-[#9aa7ad]"
    "text-trading-text"))

(defn ledger-type-tone-class
  [type-key]
  (case type-key
    :deposit "text-[#1fa67d]"
    :withdraw "text-ho-sell-hi"
    "text-trading-text"))

(def activity-row-class
  ["border-b"
   "border-[#1b3237]"
   "text-sm"
   "text-ho-text"
   "transition-colors"
   "hover:bg-[#0d2028]/40"])

(def activity-cell-class
  ["px-4" "py-2.5"])

(def activity-cell-num-class
  ["px-4" "py-2.5" "num"])
