(ns hyperopen.portfolio.optimizer.actions.view-library
  "Gap-fill hydration for the per-wallet return-view library. The inline
  view-row edit actions (authoring, sync) live in `actions.draft`; this
  namespace holds the watcher-dispatched hydrate that restores remembered
  views when a universe instrument lacks one — universe add, draft restore,
  or the library mirror arriving from IndexedDB."
  (:require [hyperopen.portfolio.optimizer.application.view-library :as view-library]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn hydrate-portfolio-optimizer-view-library
  "Gap-fill remembered return views from the wallet's view library into the
  draft: every universe instrument WITHOUT an authored absolute view takes its
  library entry; existing draft views are never touched, so this is idempotent
  and can never clobber live edits. Only meaningful under the views-aware
  return model — other estimators ignore views, and the preset/model-switch
  actions hydrate on entry. Deliberately does NOT mark the draft dirty —
  applying remembered input is machine work, not a user edit."
  [state]
  (let [return-model (get-in state contracts/draft-return-model-path)]
    (if (= :black-litterman (:kind return-model))
      (let [views (vec (or (:views return-model) []))
            views* (view-library/hydrate-views
                    views
                    (get-in state contracts/view-library-path)
                    (get-in state contracts/draft-universe-path))]
        (if (= views views*)
          []
          [[:effects/save-many
            [[contracts/draft-return-model-views-path views*]]]]))
      [])))
