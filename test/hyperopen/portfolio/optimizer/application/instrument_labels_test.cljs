(ns hyperopen.portfolio.optimizer.application.instrument-labels-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.instrument-labels :as instrument-labels]))

(deftest labels-by-instrument-prefers-human-spot-display-labels-test
  (let [labels (instrument-labels/labels-by-instrument
                [{:instrument-id "spot:@107"
                  :market-type :spot
                  :coin "@107"
                  :symbol "HYPE/USDC"
                  :base "HYPE"
                  :quote "USDC"}
                 {:instrument-id "perp:HYPE"
                  :market-type :perp
                  :coin "HYPE"}]
                ["spot:@107" "perp:HYPE"])]
    (is (= {"spot:@107" "HYPE"
            "perp:HYPE" "HYPE"}
           labels))))

(deftest labels-by-instrument-matches-backend-perp-to-local-history-id-test
  (let [labels (instrument-labels/labels-by-instrument
                [{:instrument-id "hl:perp:ETH"
                  :market-type :perp
                  :coin "ETH"}]
                ["perp:ETH"])]
    (is (= {"perp:ETH" "ETH"}
           labels))))
