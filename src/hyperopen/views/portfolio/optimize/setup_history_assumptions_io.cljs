(ns hyperopen.views.portfolio.optimize.setup-history-assumptions-io
  "Export/Import file toolbar + outcome note for the proxy workflow's agent IO:
  download a JSON template listing every asset that needs assumptions, let a
  desktop AI agent fill in proxy baskets offline, and import the completed file
  to author them in bulk. Split from setup-history-assumptions to keep that
  namespace under its size cap; mirrors the Return views io-toolbar idiom."
  (:require [hyperopen.views.portfolio.optimize.setup-controls :as controls]))

(def ^:private container-role
  "portfolio-optimizer-history-assumptions")

(def ^:private button-class
  ["border" "border-base-300" "bg-base-200/30" "px-2" "py-1" "font-mono"
   "text-[0.6875rem]" "font-semibold" "uppercase" "tracking-[0.08em]"
   "text-trading-muted" "hover:text-trading-text"
   "disabled:cursor-not-allowed" "disabled:opacity-40"
   "focus:outline-none" "focus:ring-0" "focus:ring-offset-0"])

(defn- export-button
  [{:keys [role-suffix label title scope disabled?]}]
  [:button (cond-> {:type "button"
                    :class button-class
                    :data-role (str container-role "-" role-suffix)
                    :title title
                    :on (when-not disabled?
                          {:click [[:actions/export-portfolio-optimizer-history-assumptions
                                    scope]]})}
             disabled? (assoc :disabled true))
   label])

(defn io-toolbar
  "Two export scopes — just the workflow's flagged assets, or the whole
  included universe — both drawing proxy candidates from the user's own
  selections only (never the exchange catalog). Each disables when its scope
  is empty: an empty template has nothing for an agent to fill."
  [{:keys [asset-count universe-count]}]
  [:div {:class ["flex" "flex-wrap" "items-center" "gap-1.5"]
         :data-role (str container-role "-io")}
   [:span {:class controls/eyebrow-class} "Agent file"]
   (export-button
    {:role-suffix "export-workflow"
     :label "Export proxy assets"
     :scope :proxy-workflow
     :disabled? (zero? (or asset-count 0))
     :title (str "Download a JSON template of just the assets in this proxy"
                 " workflow — hand it to an AI agent to pick proxy baskets,"
                 " then import the completed file here")})
   (export-button
    {:role-suffix "export-universe"
     :label "Export universe"
     :scope :universe
     :disabled? (zero? (or universe-count 0))
     :title (str "Download a JSON template of every included universe asset —"
                 " the agent reviews them all and can model any it judges"
                 " untrustworthy")})
   [:button {:type "button"
             :class button-class
             :data-role (str container-role "-import")
             :title (str "Import a completed history-assumptions JSON file —"
                         " each filled asset becomes a configured assumption")
             :on {:click [[:actions/import-portfolio-optimizer-history-assumptions]]}}
    "Import JSON"]])

(defn io-note
  "Outcome of the last export/import (\"Configured 3 assets · 1 unknown proxy
  dropped\"), dismissable, overwritten by the next file action."
  [{:keys [kind message]}]
  (when (seq (str (or message "")))
    [:div {:class (cond-> ["flex" "items-start" "justify-between" "gap-2"
                           "border" "px-2" "py-1.5" "text-[0.6875rem]"
                           "leading-[1.4]"]
                    (= :error kind) (into ["border-error/50" "bg-error/10"
                                           "text-error"])
                    (not= :error kind) (into ["border-base-300" "bg-base-200/20"
                                              "text-trading-muted"]))
           :data-role (str container-role "-io-note")
           :data-kind (name (or kind :success))}
     [:span message]
     [:button {:type "button"
               :class ["border-0" "bg-transparent" "px-1" "py-0" "font-mono"
                       "text-[0.6875rem]" "text-trading-muted"
                       "focus:outline-none" "focus:ring-0" "focus:ring-offset-0"]
               :data-role (str container-role "-io-note-dismiss")
               :aria-label "Dismiss file import/export message"
               :on {:click [[:actions/dismiss-portfolio-optimizer-history-assumptions-io-note]]}}
      "×"]]))
