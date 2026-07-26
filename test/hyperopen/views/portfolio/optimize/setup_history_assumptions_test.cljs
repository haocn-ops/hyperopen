(ns hyperopen.views.portfolio.optimize.setup-history-assumptions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.optimize.setup-history-assumptions :as setup-history-assumptions]
            [hyperopen.views.portfolio.optimize.test-support :as ts]))

(def ^:private btc {:instrument-id "perp:BTC" :market-type :perp :coin "BTC"})
(def ^:private new-perp {:instrument-id "perp:NEW" :market-type :perp :coin "NEW"})

(defn- section
  [assumption blocking-warnings]
  (setup-history-assumptions/history-assumptions-section
   {:state {}
    :draft {:universe [btc new-perp]
            :objective {:kind :minimum-variance}
            :history-assumptions {"perp:NEW" assumption}}
    :readiness {:request {:requested-universe [btc new-perp]
                          :universe [btc]
                          :objective {:kind :minimum-variance}}
                :blocking-warnings blocking-warnings}
    ;; history has loaded (it requested perp:NEW), so the no-history card shows.
    :history-load-state {:status :succeeded
                         :request-signature {:universe [btc new-perp]}}}))

(deftest history-assumptions-section-renders-card-and-dispatches-actions-test
  (let [node (section {:behavior :conservative
                       :expected-return nil
                       :volatility nil
                       :max-weight 0.03
                       :correlation-floor 0.75}
                      [{:code :history-assumption-incomplete
                        :instrument-id "perp:NEW"
                        :missing :volatility
                        :message "NEW needs a modeled annual volatility."}])
        card (ts/node-by-role node "portfolio-optimizer-history-assumption-card-perp:NEW")
        volatility-input (ts/node-by-role node "portfolio-optimizer-history-assumption-volatility-perp:NEW")
        cap-input (ts/node-by-role node "portfolio-optimizer-history-assumption-max-weight-perp:NEW")
        clear-button (ts/node-by-role node "portfolio-optimizer-history-assumption-clear-perp:NEW")]
    (is (some? (ts/node-by-role node "portfolio-optimizer-history-assumptions-section")))
    (is (some? card))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-expected-volatility
            "perp:NEW" [:event.target/value]]
           (first (ts/input-actions volatility-input))))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-max-weight-cap
            "perp:NEW" [:event.target/value]]
           (first (ts/input-actions cap-input))))
    (is (= [:actions/clear-portfolio-optimizer-history-assumption "perp:NEW"]
           (first (ts/click-actions clear-button))))
    (is (some #{"NEW needs a modeled annual volatility."}
              (ts/collect-strings card))
        "Field-level errors are surfaced on the card.")))

(deftest history-assumptions-section-unconfigured-asset-offers-both-modes-test
  ;; A thin-history asset with no entry yet offers both behaviors as mode tabs;
  ;; it stays excluded until the user picks one.
  (let [node (setup-history-assumptions/history-assumptions-section
              {:state {}
               :draft {:universe [btc new-perp]
                       :objective {:kind :minimum-variance}
                       :history-assumptions {}}
               :readiness {:request {:requested-universe [btc new-perp]
                                     :universe [btc]
                                     :objective {:kind :minimum-variance}}
                           :blocking-warnings []}
               :history-load-state {:status :succeeded
                                    :request-signature {:universe [btc new-perp]}}})
        proxy-mode (ts/node-by-role node "portfolio-optimizer-history-assumption-mode-perp:NEW-proxy")
        conservative-mode (ts/node-by-role node "portfolio-optimizer-history-assumption-mode-perp:NEW-conservative")]
    (is (some? proxy-mode) "The model-from-similar-assets mode is offered.")
    (is (some #{"Model from similar assets"} (ts/collect-strings proxy-mode))
        "The proxy mode button names what it does, not 'proxy behavior'.")
    (is (= [:actions/set-portfolio-optimizer-history-assumption-mode "perp:NEW" :proxy]
           (first (ts/click-actions proxy-mode))))
    (is (some? conservative-mode))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-mode "perp:NEW" :conservative]
           (first (ts/click-actions conservative-mode))))))

(def ^:private eth {:instrument-id "perp:ETH" :market-type :perp :coin "ETH"})

(def ^:private proxy-entry
  {:behavior :proxy
   :expected-return 0.0
   :volatility 0.8
   :max-weight 0.05
   :proxy {:instrument-ids ["perp:BTC"]
           :relationship-strength :medium
           :prior-weights nil}})

(defn- proxy-section
  [objective-kind]
  (setup-history-assumptions/history-assumptions-section
   {:state {}
    :draft {:universe [btc eth new-perp]
            :objective {:kind objective-kind}
            :constraints {:max-asset-weight 0.5}
            :history-assumptions {"perp:NEW" proxy-entry}}
    :readiness {:request {:requested-universe [btc eth new-perp]
                          :universe [btc eth]
                          :objective {:kind objective-kind}
                          :history {:eligible-instruments [btc eth]}}
                :blocking-warnings []}
    :history-load-state {:status :succeeded
                         :request-signature {:universe [btc eth new-perp]}}}))

(deftest history-assumptions-section-proxy-card-renders-workflow-controls-test
  (let [node (proxy-section :minimum-variance)
        remove-chip (ts/node-by-role node "portfolio-optimizer-history-assumption-proxy-remove-perp:NEW-perp:BTC")
        search-input (ts/node-by-role node "portfolio-optimizer-history-assumption-proxy-search-perp:NEW")
        relationship-high (ts/node-by-role node "portfolio-optimizer-history-assumption-relationship-perp:NEW-high")
        guardrails (ts/node-by-role node "portfolio-optimizer-history-assumption-guardrails-perp:NEW")
        volatility-input (ts/node-by-role node "portfolio-optimizer-history-assumption-volatility-perp:NEW")
        cap-input (ts/node-by-role node "portfolio-optimizer-history-assumption-max-weight-perp:NEW")
        basket (ts/node-by-role node "portfolio-optimizer-history-assumption-prior-basket-perp:NEW")
        diagnostics (ts/node-by-role node "portfolio-optimizer-history-assumption-diagnostics-perp:NEW")
        apply-button (ts/node-by-role node "portfolio-optimizer-history-assumption-apply-perp:NEW")
        reset-button (ts/node-by-role node "portfolio-optimizer-history-assumption-reset-perp:NEW")
        status (ts/node-by-role node "portfolio-optimizer-history-assumption-status-perp:NEW")]
    (is (some? (ts/node-by-role node "portfolio-optimizer-history-assumptions-section")))
    (is (some? remove-chip))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-proxy-asset
            "perp:NEW" "perp:BTC" false]
           (first (ts/click-actions remove-chip)))
        "Chip x removes the proxy.")
    (is (some? search-input) "The proxy picker is a catalog search input.")
    (is (= [:actions/set-portfolio-optimizer-history-assumption-proxy-search
            "perp:NEW" [:event.target/value]]
           (first (ts/input-actions search-input)))
        "Typing updates the per-card proxy search query.")
    (is (some? relationship-high))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-relationship-strength
            "perp:NEW" :high]
           (first (ts/click-actions relationship-high))))
    (is (some? guardrails) "Volatility + cap live in a risk-guardrails drawer.")
    (is (= :details (first guardrails))
        "The guardrails are a collapsed disclosure, not primary inputs.")
    (is (nil? (ts/node-attr guardrails :open))
        "The drawer starts collapsed and is never forced open from state.")
    (is (some #{"80% vol · 5% max"} (ts/collect-strings guardrails))
        "Collapsed, the drawer summarizes the auto-set values.")
    (is (some #{"Auto-set"} (ts/collect-strings guardrails))
        "Seed values are labeled auto-set.")
    (is (some #{"Modeled annual volatility"} (ts/collect-strings guardrails))
        "The volatility input names the model's use, not a user forecast.")
    (is (some #{"Max allocation cap"} (ts/collect-strings guardrails)))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-expected-volatility
            "perp:NEW" [:event.target/value]]
           (first (ts/input-actions volatility-input)))
        "The volatility input still commits edits from inside the drawer.")
    (is (= [:actions/set-portfolio-optimizer-history-assumption-max-weight-cap
            "perp:NEW" [:event.target/value]]
           (first (ts/input-actions cap-input))))
    (is (some? basket) "The prior basket panel is previewed.")
    (is (some #{"Source: Equal-weight fallback"} (ts/collect-strings basket))
        "The equal prior is labeled a fallback, never model output.")
    (is (some? (ts/node-by-role node "portfolio-optimizer-history-assumption-regression-perp:NEW"))
        "The regression estimate gets its own panel.")
    (is (some #{"No return overlap with the proxies yet. Using the prior only."}
              (ts/collect-strings node))
        "Without overlap the regression panel says so instead of faking weights.")
    (is (some? (ts/node-by-role node "portfolio-optimizer-history-assumption-final-basket-perp:NEW"))
        "The final modeled basket is a separate, emphasized panel.")
    (is (some #{"Confidence q 0% — controls how much the regression can move the prior"}
              (ts/collect-strings node)))
    (is (some? diagnostics))
    (is (some #{"R² used for confidence, not weights"} (ts/collect-strings diagnostics)))
    (is (some #{"Final model: BTC 100% + specific risk + 5% cap"}
              (ts/collect-strings node))
        "The summary strip names the final basket.")
    (is (= [:actions/apply-portfolio-optimizer-history-assumption "perp:NEW"]
           (first (ts/click-actions apply-button))))
    (is (= [:actions/reset-portfolio-optimizer-history-assumption "perp:NEW"]
           (first (ts/click-actions reset-button))))
    (is (some #{"Configured"} (ts/collect-strings status))
        "A complete proxy entry reads Configured.")))

(deftest history-assumptions-section-proxy-search-results-click-adds-and-clears-test
  ;; Full-catalog typeahead: a matching catalog asset (SOL, not in the universe)
  ;; shows as a result; clicking it adds the proxy and clears the search buffer.
  (let [sol {:key "perp:SOL" :market-type :perp :coin "SOL" :symbol "SOL-USDC" :volume24h 999}
        node (setup-history-assumptions/history-assumptions-section
              {:state {:asset-selector {:markets [sol]}
                       :portfolio-ui {:optimizer {:proxy-search-queries {"perp:NEW" "SOL"}}}}
               :draft {:universe [btc eth new-perp]
                       :objective {:kind :minimum-variance}
                       :constraints {:max-asset-weight 0.5}
                       :history-assumptions {"perp:NEW" proxy-entry}}
               :readiness {:request {:requested-universe [btc eth new-perp]
                                     :universe [btc eth]
                                     :objective {:kind :minimum-variance}
                                     :history {:eligible-instruments [btc eth]}}
                           :blocking-warnings []}
               :history-load-state {:status :succeeded
                                    :request-signature {:universe [btc eth new-perp]}}})
        option (ts/node-by-role node "portfolio-optimizer-history-assumption-proxy-option-perp:NEW-perp:SOL")]
    (is (some? option) "The out-of-universe catalog match (SOL) is a selectable result.")
    (is (= [[:actions/set-portfolio-optimizer-history-assumption-proxy-asset
             "perp:NEW" "perp:SOL" true]
            [:actions/set-portfolio-optimizer-history-assumption-proxy-search "perp:NEW" ""]]
           (ts/click-actions option))
        "Clicking adds the proxy, then clears the search buffer.")))

(deftest history-assumptions-section-proxy-return-input-only-for-return-seeking-test
  (is (nil? (ts/node-by-role (proxy-section :minimum-variance)
                             "portfolio-optimizer-history-assumption-return-perp:NEW"))
      "Minimum variance does not ask for an expected return on a proxy card.")
  (is (some? (ts/node-by-role (proxy-section :max-sharpe)
                              "portfolio-optimizer-history-assumption-return-perp:NEW"))
      "Return-seeking objectives do."))

(deftest history-assumptions-section-offers-manual-entry-when-no-cards-test
  ;; Even when every selected asset has adequate history the workflow stays
  ;; reachable: the user may judge an asset statistically unsound on their own
  ;; (user feedback 2026-07-05 - "how would I factor load SOPH?") and start it
  ;; by hand. Choosing an asset from the dropdown seeds proxy mode.
  (let [node (setup-history-assumptions/history-assumptions-section
              {:draft {:universe [btc eth]
                       :objective {:kind :minimum-variance}
                       :history-assumptions {}}
               :readiness {:request {:requested-universe [btc eth]
                                     :universe [btc eth]
                                     :objective {:kind :minimum-variance}}
                           :blocking-warnings []}})
        add-select (ts/node-by-role node "portfolio-optimizer-history-assumption-workflow-add")]
    (is (some? (ts/node-by-role node "portfolio-optimizer-history-assumptions-section"))
        "The section renders in compact form so the manual entry point exists.")
    (is (some? (ts/node-by-role node "portfolio-optimizer-history-assumptions-empty")))
    (is (some? add-select))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-mode
            [:event.target/value] :proxy]
           (first (ts/change-actions add-select)))
        "Choosing an asset seeds proxy mode for it.")
    (is (= ["" "perp:BTC" "perp:ETH"]
           (mapv #(get-in % [1 :value]) (subvec add-select 2)))
        "Every selected asset is offered (placeholder first), as real option siblings.")
    (is (nil? (ts/node-by-role node "portfolio-optimizer-history-assumptions-count"))
        "The workflow count is hidden while no asset is in the workflow.")))

(deftest history-assumptions-section-add-dropdown-excludes-carded-assets-test
  (let [node (proxy-section :minimum-variance)
        add-select (ts/node-by-role node "portfolio-optimizer-history-assumption-workflow-add")]
    (is (some? add-select))
    (is (= ["" "perp:BTC" "perp:ETH"]
           (mapv #(get-in % [1 :value]) (subvec add-select 2)))
        "perp:NEW already has a card, so only the remaining assets are addable.")))

(deftest history-assumptions-section-add-dropdown-shows-day-counts-ascending-test
  ;; The dropdown ranks assets by native return-day count ASCENDING with the
  ;; count in the label, so the user can proxy out the most limiting assets
  ;; (the ones capping the shared covariance window) first instead of blind.
  (let [node (setup-history-assumptions/history-assumptions-section
              {:state {}
               :draft {:universe [btc eth]
                       :objective {:kind :minimum-variance}
                       :history-assumptions {}}
               :readiness {:request {:requested-universe [btc eth]
                                     :universe [btc eth]
                                     :objective {:kind :minimum-variance}
                                     :history {:eligible-instruments [btc eth]
                                               :raw-price-series-by-instrument
                                               {"perp:BTC" (vec (repeat 1079 {:close 1}))
                                                "perp:ETH" (vec (repeat 403 {:close 1}))}}}
                           :blocking-warnings []}
               :history-load-state {:status :succeeded
                                    :request-signature {:universe [btc eth]}}})
        add-select (ts/node-by-role node "portfolio-optimizer-history-assumption-workflow-add")
        options (subvec add-select 2)]
    (is (= ["" "perp:ETH" "perp:BTC"]
           (mapv #(get-in % [1 :value]) options))
        "Fewest days of returns first (ETH 403 < BTC 1079), not universe order.")
    (is (= ["ETH (403 days)" "BTC (1079 days)"]
           (mapv #(nth % 2) (rest options)))
        "Each option shows the asset's day count in parentheses.")))

(def ^:private acknowledged-proxy-entry
  (assoc proxy-entry :metadata {:source :user :acknowledged? true}))

(defn- acknowledged-section
  [state]
  (setup-history-assumptions/history-assumptions-section
   {:state state
    :draft {:universe [btc eth new-perp]
            :objective {:kind :minimum-variance}
            :constraints {:max-asset-weight 0.5}
            :history-assumptions {"perp:NEW" acknowledged-proxy-entry}}
    :readiness {:request {:requested-universe [btc eth new-perp]
                          :universe [btc eth]
                          :objective {:kind :minimum-variance}
                          :history {:eligible-instruments [btc eth]}}
                :blocking-warnings []}
    :history-load-state {:status :succeeded
                         :request-signature {:universe [btc eth new-perp]}}}))

(deftest history-assumptions-section-configured-card-collapses-to-summary-test
  ;; A configured (complete + acknowledged) card rests as a one-line summary so
  ;; several configured assets stay a glance, not a scroll. Editors are gone;
  ;; label, status, summary, and an Edit control remain.
  (let [node (acknowledged-section {})
        card (ts/node-by-role node "portfolio-optimizer-history-assumption-card-perp:NEW")
        expand (ts/node-by-role node "portfolio-optimizer-history-assumption-expand-perp:NEW")
        status (ts/node-by-role node "portfolio-optimizer-history-assumption-status-perp:NEW")]
    (is (some? card))
    (is (= "true" (ts/node-attr card :data-collapsed)))
    (is (nil? (ts/node-by-role node "portfolio-optimizer-history-assumption-volatility-perp:NEW"))
        "Collapsed, the editors are not rendered.")
    (is (nil? (ts/node-by-role node "portfolio-optimizer-history-assumption-apply-perp:NEW")))
    (is (some #{"Configured"} (ts/collect-strings status)))
    (is (some? (ts/node-by-role node "portfolio-optimizer-history-assumption-summary-perp:NEW"))
        "The one-line summary stays visible.")
    (is (= [:actions/set-portfolio-optimizer-history-assumption-card-collapsed
            "perp:NEW" false]
           (first (ts/click-actions expand)))
        "Edit expands the card.")))

(deftest history-assumptions-section-collapse-override-and-control-test
  ;; An explicit expand override reopens a configured card (full editors back),
  ;; and every mode-carrying card offers a Collapse control.
  (let [expanded (acknowledged-section
                  {:portfolio-ui {:optimizer {:assumption-cards-collapsed
                                              {"perp:NEW" false}}}})
        card (ts/node-by-role expanded "portfolio-optimizer-history-assumption-card-perp:NEW")
        collapse (ts/node-by-role expanded "portfolio-optimizer-history-assumption-collapse-perp:NEW")]
    (is (some? card))
    (is (nil? (ts/node-attr card :data-collapsed)))
    (is (some? (ts/node-by-role expanded "portfolio-optimizer-history-assumption-volatility-perp:NEW"))
        "Expanded again, the editors are back.")
    (is (= [:actions/set-portfolio-optimizer-history-assumption-card-collapsed
            "perp:NEW" true]
           (first (ts/click-actions collapse)))
        "The Collapse control writes the explicit collapse override."))
  (let [unfinished (proxy-section :minimum-variance)]
    (is (some? (ts/node-by-role unfinished "portfolio-optimizer-history-assumption-collapse-perp:NEW"))
        "An unacknowledged (expanded-by-default) card can still be collapsed by hand.")))

(defn- loading-proxy-section
  "Same proxy card, but rendered while the proxy's history fetch is still in
  flight (aggregate load :loading + a non-idle prefetch queue)."
  []
  (setup-history-assumptions/history-assumptions-section
   {:state {:optimizer {:history-prefetch
                        {:queue []
                         :active-instrument-id "perp:BTC"
                         :by-instrument-id {"perp:BTC" {:status :loading}}}}}
    :draft {:universe [btc eth new-perp]
            :objective {:kind :minimum-variance}
            :constraints {:max-asset-weight 0.5}
            :history-assumptions {"perp:NEW" proxy-entry}}
    :readiness {:request {:requested-universe [btc eth new-perp]
                          :universe []
                          :objective {:kind :minimum-variance}}
                :blocking-warnings []}
    :history-load-state {:status :loading}}))

(deftest history-assumptions-section-surfaces-in-flight-history-loading-test
  (let [node (loading-proxy-section)
        banner (ts/node-by-role node "portfolio-optimizer-history-assumptions-loading-banner")
        status (ts/node-by-role node "portfolio-optimizer-history-assumption-status-perp:NEW")
        apply-button (ts/node-by-role node "portfolio-optimizer-history-assumption-apply-perp:NEW")
        apply-note (ts/node-by-role node "portfolio-optimizer-history-assumption-apply-loading-perp:NEW")]
    (is (some? banner) "The section carries an aggregate loading banner.")
    (is (some #(and (string? %) (re-find #"Loading proxy history for 1 asset" %))
              (ts/collect-strings banner)))
    (is (= "true" (ts/node-attr status :data-loading)))
    (is (some #{"Loading history…"} (ts/collect-strings status))
        "The status chip says loading instead of a mid-flight verdict.")
    (is (true? (ts/node-attr apply-button :disabled))
        "Apply is held while history is fetching.")
    (is (some? apply-note) "The hold explains itself as waiting, not broken.")))

(deftest history-assumptions-section-settled-load-shows-no-loading-ui-test
  (let [node (proxy-section :minimum-variance)]
    (is (nil? (ts/node-by-role node "portfolio-optimizer-history-assumptions-loading-banner")))
    (is (nil? (ts/node-attr
               (ts/node-by-role node "portfolio-optimizer-history-assumption-status-perp:NEW")
               :data-loading)))
    (is (nil? (ts/node-by-role node "portfolio-optimizer-history-assumption-apply-loading-perp:NEW")))))
