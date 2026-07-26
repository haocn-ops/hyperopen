(ns hyperopen.views.portfolio.optimize.setup-use-my-views-cards
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(def ^:private step-class
  ["views-trust-panel__eyebrow"])

(def ^:private title-class
  ["views-trust-panel__headline"])

(def ^:private body-class
  ["views-trust-panel__description"])

(def ^:private row-list-class
  ["views-trust-panel__body"])

(defn preview
  [readiness]
  (optimizer-view-model/black-litterman-preview-model readiness))

(defn- pct
  [value]
  (opt-format/format-pct value {:minimum-fraction-digits 1
                                :maximum-fraction-digits 1}))

(defn- signed-view-pct
  [value]
  (opt-format/format-pct-delta value {:minimum-fraction-digits 0
                                      :maximum-fraction-digits 0
                                      :suffix "%"}))

(defn- signed-delta
  [value]
  (opt-format/format-pct-delta value {:minimum-fraction-digits 1
                                      :maximum-fraction-digits 1
                                      :suffix ""}))

(defn- card-formatters
  []
  {:pct pct
   :signed-view-pct signed-view-pct
   :signed-delta signed-delta})

(defn- step-id
  [step]
  (str "views-trust-" step))

(defn- card-shell
  [{:keys [role step label title copy accent? dim?]} rows]
  [:section (cond-> {:class (cond-> ["optimizer-use-my-views-card"
                                     "views-trust-panel__box" "flex" "min-h-[15.5rem]" "flex-col"
                                     "border" "border-base-300"]
                              accent? (conj "optimizer-use-my-views-card--accent")
                              dim? (conj "opacity-50"))
                     :aria-labelledby (step-id step)
                     :data-role role}
              accent? (assoc :aria-current "step"))
   [:h3 {:id (step-id step)
         :class (cond-> step-class
                  accent? (conj "views-trust-panel__eyebrow--accent"))}
    [:span step]
    [:span {:class ["mx-2" "text-trading-muted/50"]} "·"]
    label]
   [:p {:class title-class} title]
   (when copy
     [:p {:class body-class} copy])
   (into [:div {:class row-list-class}]
         rows)])

(defn- empty-row
  [role copy]
  [:div {:class ["optimizer-use-my-views-empty" "views-trust-panel__empty"]
         :data-role role}
   copy])

(defn- value-row
  [role label value value-class]
  [:div {:class ["views-trust-panel__row"]
         :data-role role}
   [:span {:class ["min-w-0" "truncate" "text-trading-muted"]} label]
   [:span {:class (into ["num" "text-right"] value-class)} value]])

(defn- output-note
  [role copy]
  [:aside {:class ["optimizer-use-my-views-note" "views-trust-panel__callout"]
           :role "note"
           :data-role role}
   copy])

(defn- view-row
  [{:keys [role label return-label confidence-label]}]
  [:div {:class ["views-trust-panel__row"]
         :data-role role}
   [:span {:class ["min-w-0" "truncate" "text-trading-muted"]}
    label]
   [:span {:class ["num" "text-right" "whitespace-nowrap"]}
    [:span {:class ["views-trust-panel__value--view"]} return-label]
    [:span {:class ["text-trading-muted/50"]} " · "]
    [:span {:class ["views-trust-panel__confidence"]} confidence-label]]])

(defn- combined-output-row
  [{:keys [role label prior-label posterior-label delta-label]}]
  [:div {:class ["views-trust-panel__row"]
         :data-role role}
   [:span {:class ["min-w-0" "truncate" "text-trading-muted"]}
    label]
   [:span {:class ["num" "text-right" "whitespace-nowrap"]}
    [:span {:class ["text-trading-muted/70"]} prior-label]
    [:span {:class ["px-1.5" "text-trading-muted/50"]} "→"]
    [:span {:class ["views-trust-panel__value--combined"]} posterior-label]
    [:span {:class ["ml-1.5" "text-trading-muted/70"]}
     (str "(" delta-label ")")]]])

(defn- render-row
  [row]
  (case (:kind row)
    :empty
    (empty-row (:role row) (:copy row))

    :value
    (value-row (:role row)
               (:label row)
               (:value-label row)
               (case (:value-tone row)
                 :prior ["views-trust-panel__value--prior"]
                 :combined ["views-trust-panel__value--combined"]
                 []))

    :view
    (view-row row)

    :combined-output
    (combined-output-row row)

    :note
    (output-note (:role row) (:copy row))

    nil))

(defn- render-card
  [card]
  (card-shell card (keep render-row (:rows card))))

(defn cards
  [draft readiness preview]
  (let [model (optimizer-view-model/black-litterman-cards-model
               draft
               readiness
               preview
               (card-formatters))]
    (into [:div {:class ["optimizer-use-my-views-insight-cards"
                         "grid" "grid-cols-1" "gap-3" "xl:grid-cols-3"]
                 :data-role "portfolio-optimizer-setup-use-my-views-insight-cards"}]
          (map render-card (:cards model)))))
