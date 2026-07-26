(ns hyperopen.portfolio.optimizer.infrastructure.persistence-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [async deftest is]]
            [hyperopen.core-bootstrap.test-support.browser-mocks :as browser-mocks]
            [hyperopen.platform.indexed-db :as indexed-db]
            [hyperopen.portfolio.optimizer.infrastructure.persistence :as persistence]
            [hyperopen.test-support.async :as async-support]))

(def ^:private wallet-address
  "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

(deftest persistence-key-helpers-are-stable-and-scoped-test
  (is (= (str "scenario-index::" wallet-address)
         (persistence/scenario-index-key (str/upper-case wallet-address))))
  (is (= (str "draft::" wallet-address)
         (persistence/draft-key wallet-address)))
  (is (= "scenario::scn_01"
         (persistence/scenario-key " scn_01 ")))
  (is (= "tracking::scn_01"
         (persistence/tracking-key "scn_01")))
  (is (nil? (persistence/scenario-index-key "not-an-address")))
  (is (nil? (persistence/scenario-key "   "))))

(deftest portfolio-optimizer-store-roundtrips-scenarios-drafts-and-tracking-test
  (async done
    (browser-mocks/with-test-indexed-db
      (fn []
        (let [scenario {:id "scn_01"
                        :status :saved
                        :config {:objective {:kind :max-sharpe}
                                 :return-model {:kind :black-litterman
                                                :views [{:id "view-1"
                                                         :kind :relative
                                                         :long-instrument-id "perp:BTC"
                                                         :short-instrument-id "perp:ETH"
                                                         :return 0.04
                                                         :confidence 0.8
                                                         :confidence-variance 0.2
                                                         :weights {"perp:BTC" 1
                                                                   "perp:ETH" -1}}]}}}
              scenario-index {:ordered-ids ["scn_01"]
                              :by-id {"scn_01" {:name "Core run"}}}
              draft {:name "Draft"
                     :objective {:kind :minimum-variance}
                     :return-model {:kind :black-litterman
                                    :views [{:id "view-2"
                                             :kind :absolute
                                             :instrument-id "perp:SOL"
                                             :return 0.12
                                             :confidence 0.7
                                             :confidence-variance 0.3
                                             :weights {"perp:SOL" 1}}]}}
              tracking {:scenario-id "scn_01"
                        :snapshots [{:weight-drift-rms 0.02}]}
              fail! (async-support/unexpected-error done)]
          (-> (js/Promise.all
               #js [(persistence/save-scenario-index! wallet-address scenario-index)
                    (persistence/save-scenario! "scn_01" scenario)
                    (persistence/save-draft! wallet-address draft)
                    (persistence/save-tracking! "scn_01" tracking)])
              (.then (fn [results]
                       (is (= [true true true true]
                              (vec (array-seq results))))
                       (js/Promise.all
                        #js [(persistence/load-scenario-index! wallet-address)
                             (persistence/load-scenario! "scn_01")
                             (persistence/load-draft! wallet-address)
                             (persistence/load-tracking! "scn_01")])))
              (.then (fn [records]
                       (let [[loaded-index loaded-scenario loaded-draft loaded-tracking]
                             (vec (array-seq records))]
                         (is (= scenario-index loaded-index))
                         (is (= scenario loaded-scenario))
                         (is (= draft loaded-draft))
                         (is (= tracking loaded-tracking))
                         (persistence/delete-draft! wallet-address))))
              (.then (fn [deleted?]
                       (is (true? deleted?))
                       (persistence/load-draft! wallet-address)))
              (.then (fn [missing-draft]
                       (is (nil? missing-draft))
                       (done)))
              (.catch fail!)))))))

(deftest load-scenario-index-recovers-from-saved-scenario-records-when-index-is-missing-test
  (async done
    (browser-mocks/with-test-indexed-db
      (fn []
        (let [other-address "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
              scenario-a {:schema-version 1
                          :id "scn_a"
                          :name "Recovered A"
                          :address wallet-address
                          :status :saved
                          :config {:objective {:kind :max-sharpe}
                                   :return-model {:kind :historical-mean}
                                   :risk-model {:kind :diagonal-shrink}}
                          :saved-run {:result {:expected-return 0.11
                                               :volatility 0.22}}
                          :updated-at-ms 3000}
              scenario-b {:schema-version 1
                          :id "scn_b"
                          :name "Recovered B"
                          :address wallet-address
                          :status :executed
                          :config {:objective {:kind :minimum-variance}
                                   :return-model {:kind :black-litterman}
                                   :risk-model {:kind :stabilized-covariance}}
                          :saved-run {:result {:expected-return 0.09
                                               :volatility 0.18}}
                          :updated-at-ms 5000}
              other-scenario (assoc scenario-a
                                    :id "scn_other"
                                    :address other-address
                                    :updated-at-ms 7000)
              fail! (async-support/unexpected-error done)]
          (-> (js/Promise.all
               #js [(persistence/save-scenario! "scn_a" scenario-a)
                    (persistence/save-scenario! "scn_b" scenario-b)
                    (persistence/save-scenario! "scn_other" other-scenario)])
              (.then (fn [_]
                       (persistence/load-scenario-index! wallet-address)))
              (.then (fn [loaded-index]
                       (is (= ["scn_b" "scn_a"] (:ordered-ids loaded-index)))
                       (is (= "Recovered B"
                              (get-in loaded-index [:by-id "scn_b" :name])))
                       (is (= :executed
                              (get-in loaded-index [:by-id "scn_b" :status])))
                       (is (nil? (get-in loaded-index [:by-id "scn_other"])))
                       (done)))
              (.catch fail!)))))))

(deftest load-scenario-index-recovers-from-saved-scenario-records-when-index-is-incomplete-test
  (async done
    (browser-mocks/with-test-indexed-db
      (fn []
        (let [scenario {:schema-version 1
                        :id "scn_incomplete"
                        :name "Recovered From Incomplete"
                        :address wallet-address
                        :status :saved
                        :config {:objective {:kind :max-sharpe}
                                 :return-model {:kind :historical-mean}
                                 :risk-model {:kind :diagonal-shrink}}
                        :saved-run {:result {:expected-return 0.13
                                             :volatility 0.21}}
                        :updated-at-ms 6000}
              incomplete-index {:ordered-ids ["scn_incomplete"]
                                :by-id {}}
              fail! (async-support/unexpected-error done)]
          (-> (js/Promise.all
               #js [(persistence/save-scenario-index! wallet-address incomplete-index)
                    (persistence/save-scenario! "scn_incomplete" scenario)])
              (.then (fn [_]
                       (persistence/load-scenario-index! wallet-address)))
              (.then (fn [loaded-index]
                       (is (= ["scn_incomplete"] (:ordered-ids loaded-index)))
                       (is (= "Recovered From Incomplete"
                              (get-in loaded-index
                                      [:by-id "scn_incomplete" :name])))
                       (done)))
              (.catch fail!)))))))

(deftest indexed-db-app-store-list-includes-portfolio-optimizer-store-test
  (async done
    (browser-mocks/with-test-indexed-db
      (fn []
        (let [fail! (async-support/unexpected-error done)]
          (-> (indexed-db/put-json! indexed-db/portfolio-optimizer-store
                                    "scenario::probe"
                                    {:ok true})
              (.then (fn [persisted?]
                       (is (true? persisted?))
                       (indexed-db/get-json! indexed-db/portfolio-optimizer-store
                                             "scenario::probe")))
              (.then (fn [record]
                       (is (= {:ok true} record))
                       (done)))
              (.catch fail!)))))))
