(ns hyperopen.portfolio.optimizer.application.draft-enrichment
  "Read-time discovery decoration for draft instrument collections.

  A holdings-seeded draft can be stored BEFORE history discovery arrives, and
  the per-wallet autosave then persists the bare entries forever - so the
  stored draft cannot be trusted to carry :optimizer-history/* identity.
  Re-decorating at the request-building seam (the single path every
  readiness/run consumer goes through) keeps backend-keyed warnings
  translatable to draft instruments and keeps alignment's hard-warning
  exclusion working."
  (:require [hyperopen.portfolio.optimizer.application.history-loader.api-v2.discovery :as discovery]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defonce ^:private enrich-memo
  (volatile! nil))

(defn enrich-draft-instruments
  "Returns the draft with :universe and :proxy-reference-instruments decorated
  from the state's history discovery. Memoized on reference identity: streaming
  renders rebuild readiness constantly while the draft/discovery values are
  unchanged between them."
  [state draft]
  (let [discovery-state (get-in state contracts/history-discovery-path)]
    (if (empty? (:backend-id-by-local-id discovery-state))
      draft
      (let [cached @enrich-memo]
        (if (and cached
                 (identical? (:draft cached) draft)
                 (identical? (:discovery cached) discovery-state))
          (:value cached)
          (let [decorate #(discovery/with-discovery-metadata % discovery-state)
                value (cond-> draft
                        (seq (:universe draft))
                        (update :universe #(mapv decorate %))

                        (seq (:proxy-reference-instruments draft))
                        (update :proxy-reference-instruments #(mapv decorate %)))]
            (vreset! enrich-memo {:draft draft
                                  :discovery discovery-state
                                  :value value})
            value))))))
