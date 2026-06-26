(ns hyperopen.portfolio.optimizer.actions.draft-options)

(def objective-models
  {:minimum-variance {:kind :minimum-variance}
   :max-sharpe {:kind :max-sharpe}
   :target-volatility {:kind :target-volatility
                       :target-volatility 0.2}
   :target-return {:kind :target-return
                   :target-return 0.15}})

(def return-models
  {:historical-mean {:kind :historical-mean}
   :ew-mean {:kind :ew-mean
             :alpha 0.015159678336035098}
   :black-litterman {:kind :black-litterman
                     :views []}})

(def risk-models
  {:diagonal-shrink {:kind :diagonal-shrink}
   :ledoit-wolf {:kind :diagonal-shrink}
   :ledoit-wolf-dense {:kind :ledoit-wolf-dense}
   :sample-covariance {:kind :sample-covariance}
   :mixed-frequency {:kind :mixed-frequency}})

(def setup-presets
  {:conservative {:objective {:kind :minimum-variance}
                  :return-model {:kind :historical-mean}}
   :risk-adjusted {:objective {:kind :max-sharpe}
                   :return-model {:kind :historical-mean}}
   :use-my-views {:objective {:kind :max-sharpe}
                  :return-model {:kind :black-litterman
                                 :views []}}})

(def objective-menu-options
  {:minimum-volatility {:objective {:kind :minimum-variance}}
   :max-sharpe {:objective {:kind :max-sharpe}}
   :target-volatility {:objective {:kind :target-volatility
                                   :target-volatility 0.12}}
   :maximum-return {:objective {:kind :target-return
                                :target-return 0.3}}
   :use-my-views {:objective {:kind :max-sharpe}
                  :return-model-kind :black-litterman}})

(def numeric-constraint-keys
  #{:max-asset-weight
    :gross-min
    :gross-max
    :net-min
    :net-max
    :dust-usdc
    :max-turnover
    :rebalance-tolerance})

(def clearable-numeric-constraint-keys
  #{:gross-min
    :max-turnover})

(def boolean-constraint-keys
  #{:long-only?
    :include-spot?})

(def numeric-objective-parameter-keys
  #{:target-return
    :target-volatility})

(def numeric-execution-assumption-keys
  #{:fallback-slippage-bps
    :manual-capital-usdc})

(def keyword-execution-assumption-keys
  #{:default-order-type
    :fee-mode})

(def instrument-filter-keys
  #{:allowlist
    :blocklist})

(def numeric-asset-override-keys
  #{:max-weight
    :perp-max-weight})

(def boolean-asset-override-keys
  #{:held-lock?})
