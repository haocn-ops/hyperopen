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
        proxy-mode-button (ts/node-by-role node "portfolio-optimizer-history-assumption-mode-perp:NEW-proxy")
        volatility-input (ts/node-by-role node "portfolio-optimizer-history-assumption-volatility-perp:NEW")
        cap-input (ts/node-by-role node "portfolio-optimizer-history-assumption-max-weight-perp:NEW")
        clear-button (ts/node-by-role node "portfolio-optimizer-history-assumption-clear-perp:NEW")]
    (is (some? (ts/node-by-role node "portfolio-optimizer-history-assumptions-section")))
    (is (some? card))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-mode "perp:NEW" :proxy]
           (first (ts/click-actions proxy-mode-button))))
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

(deftest history-assumptions-section-renders-proxy-controls-test
  (let [node (section {:behavior :proxy
                       :expected-return 0.25
                       :volatility 0.9
                       :proxy-instrument-id "perp:BTC"
                       :relationship :medium
                       :max-weight 0.05}
                      [{:code :history-assumption-proxy-not-applied
                        :instrument-id "perp:NEW"
                        :message "NEW's proxy assumption is saved but isn't applied to the risk model yet, so it is excluded from this optimization."}])
        proxy-select (ts/node-by-role node "portfolio-optimizer-history-assumption-proxy-perp:NEW")
        relationship-high (ts/node-by-role node "portfolio-optimizer-history-assumption-relationship-perp:NEW-high")]
    (is (some? proxy-select))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-proxy-instrument
            "perp:NEW" [:event.target/value]]
           (first (ts/change-actions proxy-select))))
    (is (= [:actions/set-portfolio-optimizer-history-assumption-proxy-relationship
            "perp:NEW" :high]
           (first (ts/click-actions relationship-high))))
    (is (some #(re-find #"saved but isn't applied" %)
              (ts/collect-strings node))
        "Proxy mode shows the honest not-yet-applied note.")))

(deftest history-assumptions-section-absent-when-no-cards-test
  (let [node (setup-history-assumptions/history-assumptions-section
              {:draft {:universe [btc]
                       :objective {:kind :minimum-variance}
                       :history-assumptions {}}
               :readiness {:request {:requested-universe [btc]
                                     :universe [btc]
                                     :objective {:kind :minimum-variance}}
                           :blocking-warnings []}})]
    (is (nil? node)
        "No section renders when every selected asset has adequate history.")))
