(ns hyperopen.portfolio.optimizer.application.black-litterman-preview-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.black-litterman-preview]))

(def ^:private build-preview
  (resolve 'hyperopen.portfolio.optimizer.application.black-litterman-preview/build-preview))

(defn- near?
  [expected actual]
  (< (js/Math.abs (- expected actual)) 0.0000001))

(defn- ready-request
  [views]
  {:status :ready
   :request {:universe [{:instrument-id "A"}
                        {:instrument-id "B"}]
             :return-model {:kind :black-litterman
                            :views views}
             :risk-model {:kind :sample-covariance}
             :periods-per-year 10
             :history {:return-series-by-instrument {"A" [0.01 0.03 0.02]
                                                     "B" [0.04 0.01 0.04]}}
             :black-litterman-prior {:source :market-cap
                                     :weights-by-instrument {"A" 0.6
                                                             "B" 0.4}}}})

(deftest build-preview-returns-unavailable-when-no-eligible-request-exists-test
  (is (some? build-preview))
  (when build-preview
    (is (= {:status :unavailable
            :reason :no-eligible-request}
           (select-keys (build-preview {:status :blocked
                                        :reason :incomplete-history})
                        [:status :reason])))))

(deftest build-preview-returns-empty-when-no-views-exist-test
  (is (some? build-preview))
  (when build-preview
    (let [preview (build-preview (ready-request []))]
      (is (= {:status :empty
              :view-count 0}
             (select-keys preview [:status :view-count])))
      (is (= ["A" "B"] (mapv :instrument-id (:rows preview))))
      (is (near? 0.2 (get-in preview [:rows 0 :prior-return])))
      (is (near? (get-in preview [:rows 0 :prior-return])
                 (get-in preview [:rows 0 :posterior-return]))))))

(deftest build-preview-returns-prior-and-posterior-rows-when-readiness-request-is-available-test
  (is (some? build-preview))
  (when build-preview
    (let [preview (build-preview
                   (ready-request
                    [{:id "view-1"
                      :kind :relative
                      :instrument-id "A"
                      :comparator-instrument-id "B"
                      :direction :outperform
                      :return 0.1
                      :confidence 0.5
                      :confidence-variance 1
                      :weights {"A" 1
                                "B" -1}}]))]
      (is (= :ready (:status preview)))
      (is (= ["A" "B"] (mapv :instrument-id (:rows preview))))
      (is (near? 0.2 (get-in preview [:rows 0 :prior-return]))
          "Preview should label baseline expected returns as priors, not prior weights.")
      (is (near? 0.3 (get-in preview [:rows 1 :prior-return])))
      (is (> (get-in preview [:rows 0 :posterior-return])
             (get-in preview [:rows 0 :prior-return]))
          "A relative A-over-B view should raise A when baseline starts below B.")
      (is (< (get-in preview [:rows 1 :posterior-return])
             (get-in preview [:rows 1 :prior-return]))
          "A relative A-over-B view should lower B when baseline starts above A."))))

(deftest build-preview-labels-vault-rows-from-universe-metadata-test
  (is (some? build-preview))
  (when build-preview
    (let [vault-address "0x3333333333333333333333333333333333333333"
          vault-id (str "vault:" vault-address)
          preview (build-preview
                   {:status :ready
                    :request {:universe [{:instrument-id "perp:BTC"
                                          :market-type :perp
                                          :coin "BTC"}
                                         {:instrument-id vault-id
                                          :market-type :vault
                                          :coin vault-id
                                          :vault-address vault-address
                                          :name "Alpha Yield"}]
                              :return-model {:kind :black-litterman
                                             :views [{:id "view-1"
                                                      :kind :absolute
                                                      :instrument-id vault-id
                                                      :return 0.04
                                                      :confidence 0.8
                                                      :weights {vault-id 1}}]}
                              :risk-model {:kind :sample-covariance}
                              :periods-per-year 10
                              :history {:return-series-by-instrument
                                        {"perp:BTC" [0.01 0.02 -0.01 0.03]
                                         vault-id [0.02 -0.01 0.04 0.01]}}
                              :black-litterman-prior
                              {:source :market-cap
                               :weights-by-instrument {"perp:BTC" 0.5
                                                       vault-id 0.5}}}})
          vault-row (second (:rows preview))]
      (is (= vault-id (:instrument-id vault-row)))
      (is (= "Alpha Yield" (:label vault-row))))))
