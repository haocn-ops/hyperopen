(ns hyperopen.portfolio.optimizer.application.request-builder-refinement-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.request-builder :as request-builder]))

(def ^:private base
  {:draft {:id "draft-1"
           :universe [{:instrument-id "perp:BTC" :market-type :perp :coin "BTC"}]
           :objective {:kind :max-sharpe}}
   :history-data {:candle-history-by-coin {"BTC" [{:time 1000 :close "100"}
                                                  {:time 2000 :close "110"}]}}
   :market-cap-by-coin {"BTC" 900}
   :as-of-ms 2500})

(deftest build-engine-request-applies-frontier-points-override-test
  (is (nil? (get-in (request-builder/build-engine-request base)
                    [:objective :frontier-points]))
      "no override => objective carries no frontier-points")
  (is (= 72 (get-in (request-builder/build-engine-request
                     (assoc base :frontier-points-override 72))
                    [:objective :frontier-points]))
      "override is injected onto the objective for the solve"))
