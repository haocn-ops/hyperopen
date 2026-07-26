(ns hyperopen.views.portfolio.optimize.setup-history-assumption-recommendations
  "Backend-recommended setup surfaces for the proxy workflow (split from
  setup-history-assumptions to keep that namespace under its size cap): the
  per-card recommendation panel — one-click apply of the server's
  default-assumption block — and the section-level apply-all banner. Views
  render the view-model's :recommendation block and dispatch its carried
  action ids only."
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]))

(defn- recommendation-member-chip
  [card-id {:keys [instrument-id label role available?]}]
  [:span {:class ["inline-flex" "items-center" "gap-1" "border" "border-base-300"
                  "bg-base-200/40" "px-1.5" "py-0.5" "font-mono" "text-[0.6875rem]"
                  (if available? "text-trading-text" "text-trading-muted")]
          :title (when role (str "Role: " role))
          :data-role (str "portfolio-optimizer-history-assumption-recommendation-member-"
                          card-id "-" instrument-id)
          :data-available (if available? "true" "false")}
   label
   ;; The backend recommends this member but cannot serve its history yet -
   ;; apply holds it back, so the chip says why it won't ride along.
   (when-not available?
     [:span {:class ["text-[0.5625rem]" "uppercase" "tracking-[0.06em]"
                     "text-trading-muted"]}
      "awaiting data"])])

(defn recommendation-panel
  "Server-recommended setup for a mode-less card: the suggested approach,
  basket members (dimmed while the backend cannot serve their history yet),
  the stated relationship strength, and the backend's rationale. One click
  applies it as an acknowledged assumption; everything stays editable after."
  [card]
  (let [id (:instrument-id card)
        rec (:recommendation card)]
    [:div {:class ["border" "border-success/40" "bg-success/10" "p-2" "space-y-1.5"]
           :data-role (str "portfolio-optimizer-history-assumption-recommendation-" id)}
     [:div {:class ["flex" "items-center" "justify-between" "gap-2"]}
      [:span {:class controls/eyebrow-class} "Recommended setup"]
      (when (:relationship-label rec)
        [:span {:class ["font-mono" "text-[0.625rem]" "uppercase" "tracking-[0.08em]"
                        "text-trading-muted"]}
         (str (:relationship-label rec) " similarity")])]
     [:p {:class ["text-[0.75rem]" "font-semibold" "text-trading-text"]}
      (:approach-label rec)]
     (when (seq (:members rec))
       (into [:div {:class ["flex" "flex-wrap" "gap-1"]}]
             (map #(recommendation-member-chip id %))
             (:members rec)))
     (when (:rationale rec)
       [:p {:class ["text-[0.6875rem]" "leading-[1.45]" "text-trading-muted"]}
        (:rationale rec)])
     (if (:applicable? rec)
       [:button {:type "button"
                 :class ["border" "border-success/50" "bg-success/10" "px-3" "py-1.5"
                         "text-[0.75rem]" "font-semibold" "text-success"]
                 :data-role (str "portfolio-optimizer-history-assumption-recommendation-apply-"
                                 id)
                 :on {:click [[(get-in rec [:actions :apply-one]) id]]}}
        "Use recommended setup"]
       [:p {:class ["text-[0.6875rem]" "text-trading-muted"]
            :data-role (str "portfolio-optimizer-history-assumption-recommendation-unavailable-"
                            id)}
        "The recommended members are still waiting on backend history — configure manually or check back later."])]))

(defn recommended-banner
  "Section strip offering the one-click bulk apply when any card carries an
  applicable server recommendation."
  [{:keys [recommended-count recommended-actions]}]
  (when (pos? (or recommended-count 0))
    [:div {:class ["flex" "flex-wrap" "items-center" "justify-between" "gap-2"
                   "border" "border-success/40" "bg-success/10" "px-2" "py-1.5"]
           :data-role "portfolio-optimizer-history-assumptions-recommended-banner"}
     [:span {:class ["text-[0.6875rem]" "text-trading-text"]}
      (str recommended-count
           (if (= 1 recommended-count)
             " asset has a server-recommended setup"
             " assets have server-recommended setups"))]
     [:button {:type "button"
               :class ["border" "border-success/50" "bg-success/10" "px-2" "py-1"
                       "font-mono" "text-[0.6875rem]" "font-semibold" "uppercase"
                       "tracking-[0.08em]" "text-success"]
               :data-role "portfolio-optimizer-history-assumptions-apply-all-recommended"
               :on {:click [[(:apply-all recommended-actions)]]}}
      "Apply all recommended"]]))
