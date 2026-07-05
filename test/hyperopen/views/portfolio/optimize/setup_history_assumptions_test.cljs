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
                        :message "NEW needs an expected annual volatility."}])
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
    (is (some #{"NEW needs an expected annual volatility."}
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
    (is (some? proxy-mode) "Proxy behavior is offered.")
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
        add-select (ts/node-by-role node "portfolio-optimizer-history-assumption-proxy-add-perp:NEW")
        relationship-high (ts/node-by-role node "portfolio-optimizer-history-assumption-relationship-perp:NEW-high")
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
    (is (some? add-select))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-proxy-asset
            "perp:NEW" [:event.target/value] true]
           (first (ts/change-actions add-select)))
        "The add dropdown selects a proxy.")
    (is (some? relationship-high))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-relationship-strength
            "perp:NEW" :high]
           (first (ts/click-actions relationship-high))))
    (is (some? basket) "The system-created prior basket is previewed.")
    (is (some? diagnostics))
    (is (some #{"R² used for confidence, not weights"} (ts/collect-strings diagnostics)))
    (is (some #{"Final model: proxy basket + shrinkage + specific risk + cap"}
              (ts/collect-strings node)))
    (is (= [:actions/apply-portfolio-optimizer-history-assumption "perp:NEW"]
           (first (ts/click-actions apply-button))))
    (is (= [:actions/reset-portfolio-optimizer-history-assumption "perp:NEW"]
           (first (ts/click-actions reset-button))))
    (is (some #{"Configured"} (ts/collect-strings status))
        "A complete proxy entry reads Configured.")))

(deftest history-assumptions-section-proxy-add-select-renders-real-option-siblings-test
  ;; Regression (caught only by live browser QA, not the hiccup-tree assertions
  ;; above): `into` must target the [:select ...] hiccup vector itself so each
  ;; [:option ...] lands as a direct sibling child. Targeting a bare
  ;; vector-of-options nests them as ONE child value; `find-by-data-role` still
  ;; locates the :select node either way (it walks arbitrarily nested
  ;; structures), so that alone can't tell the shapes apart - a real Replicant
  ;; DOM render turned the nested shape into one stringified literal instead of
  ;; actual <option> elements. Assert the DIRECT children explicitly instead.
  (let [node (proxy-section :minimum-variance)
        select (ts/node-by-role node "portfolio-optimizer-history-assumption-proxy-add-perp:NEW")
        children (subvec select 2)]
    (is (= 2 (count children))
        "The placeholder plus the one addable proxy (ETH; BTC is already selected) are direct children.")
    (is (every? #(= :option (first %)) children)
        "Each child is its own [:option ...] node, never a nested vector-of-options.")
    (is (= ["" "perp:ETH"] (mapv #(get-in % [1 :value]) children)))))

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
