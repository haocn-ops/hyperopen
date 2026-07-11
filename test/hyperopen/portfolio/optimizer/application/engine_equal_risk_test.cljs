(ns hyperopen.portfolio.optimizer.application.engine-equal-risk-test
  "End-to-end engine coverage for the :equal-risk objective: request-builder
  -> context -> sequential solve -> payload, on the real quadprog adapter."
  (:require [cljs.spec.alpha :as s]
            [cljs.test :refer-macros [async deftest is testing]]
            [hyperopen.portfolio.optimizer.application.engine :as engine]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.contracts.spec-registry]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]
            [hyperopen.portfolio.optimizer.infrastructure.solver-adapter :as solver-adapter]))

(defn- near?
  ([expected actual] (near? expected actual 1e-6))
  ([expected actual tolerance]
   (and (number? actual)
        (< (js/Math.abs (- expected actual)) tolerance))))

(def ^:private history-data
  {:candle-history-by-coin
   {"BTC" [{:time-ms 0 :close "100"}
           {:time-ms 100 :close "101"}
           {:time-ms 200 :close "103.02"}
           {:time-ms 300 :close "106.1106"}
           {:time-ms 400 :close "104"}]
    "ETH" [{:time-ms 0 :close "100"}
           {:time-ms 100 :close "102"}
           {:time-ms 200 :close "103.02"}
           {:time-ms 300 :close "103.02"}
           {:time-ms 400 :close "107"}]
    "SOL" [{:time-ms 0 :close "100"}
           {:time-ms 100 :close "99"}
           {:time-ms 200 :close "104"}
           {:time-ms 300 :close "101"}
           {:time-ms 400 :close "105"}]}
   :funding-history-by-coin
   {"BTC" [{:time-ms 0 :funding-rate-raw 0.00001}]
    "ETH" [{:time-ms 0 :funding-rate-raw -0.00001}]
    "SOL" [{:time-ms 0 :funding-rate-raw 0.00001}]}})

(defn- equal-risk-request
  [& [draft-overrides]]
  (fixtures/sample-engine-request
   {:draft (fixtures/sample-draft
            (merge
             {:id "equal-risk-scenario"
              :universe [{:instrument-id "perp:BTC"
                          :market-type :perp
                          :coin "BTC"
                          :shortable? true
                          :position-side :long}
                         {:instrument-id "perp:ETH"
                          :market-type :perp
                          :coin "ETH"
                          :shortable? true
                          :position-side :long}]
              :return-model {:kind :historical-mean}
              :risk-model {:kind :sample-covariance}
              :objective {:kind :equal-risk}
              ;; Draft-side keys: renamed by the request builder. Zero band =>
              ;; targets G=1, N=1 (single fully-invested long book). The
              ;; fixture deep-merges constraints over a long-only base, so
              ;; :long-only? is stated explicitly.
              :constraints {:long-only? false
                            :gross-max 1.0
                            :net-min 1.0
                            :net-max 1.0
                            :max-asset-weight 0.9
                            :rebalance-tolerance 0.001}
              :execution-assumptions {:fallback-slippage-bps 20
                                      :prices-by-id {"perp:BTC" 100
                                                     "perp:ETH" 50}}}
             draft-overrides))
    :current-portfolio (fixtures/sample-current-portfolio
                        {:capital {:nav-usdc 10000}
                         :by-instrument {"perp:BTC" {:weight 0.6}
                                         "perp:ETH" {:weight 0.4}}})
    :history-data history-data
    :market-cap-by-coin {}
    :as-of-ms 1000}))

(deftest equal-risk-request-carries-canonical-exposure-targets-test
  (let [request (equal-risk-request)]
    (is (= :equal-risk (get-in request [:objective :kind])))
    ;; Engine-side keys after the rename; the targets derive from these inside
    ;; the constraint encoder (never from the raw band edges in views).
    (is (= 1.0 (get-in request [:constraints :gross-leverage])))
    (is (= {:min 1.0 :max 1.0} (get-in request [:constraints :net-exposure])))))

(deftest equal-risk-solves-end-to-end-with-contributions-payload-test
  (let [problems (atom [])
        solve-problem (fn [problem]
                        (swap! problems conj problem)
                        (solver-adapter/solve-with-quadprog problem))
        result (engine/run-optimization (equal-risk-request)
                                        {:solve-problem solve-problem})]
    (is (= :solved (:status result)))
    (is (= :sequential-equal-risk (get-in result [:solver :strategy])))
    (is (= :equal-risk (get-in result [:solver :objective-kind])))
    (testing "no frontier sweep: every solver problem is an equal-risk subproblem"
      (is (pos? (count @problems)))
      (is (every? #(= :equal-risk (:objective-kind %)) @problems))
      (is (every? #(nil? (:return-tilt %)) @problems)))
    (testing "the frontier is the single selected point from the target solve"
      (is (= 1 (count (:frontier result))))
      (is (= :target-solve (get-in result [:frontier-summary :source])))
      (is (every? #(= :target-solve (:source %))
                  (vals (:frontier-summaries result)))))
    (testing "published weights satisfy the exact book targets with no dust drops"
      (is (near? 1.0 (reduce + 0 (:target-weights result))))
      (is (every? #(>= % 0) (:target-weights result)))
      (is (= [] (:dropped-weights result))))
    (testing "risk contributions are computed from the published weights"
      (let [contributions (:risk-contributions result)]
        (is (= :signed-euler-volatility (:method contributions)))
        (is (contains? #{:exact :approximate :not-converged} (:quality contributions)))
        (is (near? 1.0 (:sum-relative-contributions contributions) 1e-9))
        (is (= [0.5 0.5] (:target-relative-contributions contributions)))
        (is (contains? (:relative-contributions-by-instrument contributions) "perp:BTC"))
        (is (contains? (:target-relative-contributions-by-instrument contributions) "perp:ETH"))
        (is (number? (:rms-error contributions)))
        (is (number? (:max-absolute-error contributions)))
        (is (number? (:negative-contribution-count contributions)))))
    (testing "solver metadata is present and truthful"
      (let [solver (:equal-risk-solver result)]
        (is (= :sequential-equal-risk (:strategy solver)))
        (is (boolean? (:converged? solver)))
        (is (keyword? (:termination-reason solver)))
        (is (pos? (:initialization-count solver)))
        (is (keyword? (:selected-initialization solver)))
        (is (number? (:objective-value solver)))
        (is (number? (:exactness-tolerance solver)))))
    (testing "the solved payload passes the canonical result specs"
      (is (s/valid? :hyperopen.portfolio.optimizer.contracts/result-payload result)
          (s/explain-str :hyperopen.portfolio.optimizer.contracts/result-payload result)))))

(deftest equal-risk-sync-and-async-engine-paths-agree-test
  (async done
    (let [request (equal-risk-request)
          sync-result (engine/run-optimization
                       request
                       {:solve-problem solver-adapter/solve-with-quadprog})
          progress-events (atom [])]
      (-> (engine/run-optimization-async
           request
           {:solve-problem solver-adapter/solve-with-quadprog
            :on-progress (fn [payload] (swap! progress-events conj payload))})
          (.then (fn [async-result]
                   (is (= :solved (:status sync-result) (:status async-result)))
                   (is (= (:target-weights sync-result)
                          (:target-weights async-result)))
                   (is (= (:risk-contributions sync-result)
                          (:risk-contributions async-result)))
                   (is (= (:equal-risk-solver sync-result)
                          (:equal-risk-solver async-result)))
                   (testing "iterative solve progress is reported"
                     (is (some #(and (= :solve (:step %))
                                     (re-find #"init" (str (:detail %))))
                               @progress-events)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "async equal-risk engine run failed: " err))
                    (done)))))))

(deftest equal-risk-proceeds-when-return-model-is-invalid-test
  ;; A Black-Litterman view naming an instrument outside the universe makes
  ;; the return model :invalid. Return-dependent objectives stay blocked
  ;; (existing behavior, re-asserted here); Equal Risk must still solve, with
  ;; return-based display metrics omitted rather than fabricated.
  (let [invalid-views {:kind :black-litterman
                       :views [{:id "sol-view"
                                :kind :absolute
                                :instrument-id "perp:SOL"
                                :weights {"perp:SOL" 1}
                                :return 0.2
                                :confidence 0.75}]}
        equal-risk-result (engine/run-optimization
                           (assoc (equal-risk-request) :return-model invalid-views)
                           {:solve-problem solver-adapter/solve-with-quadprog})
        max-sharpe-result (engine/run-optimization
                           (assoc (equal-risk-request {:objective {:kind :max-sharpe}})
                                  :return-model invalid-views)
                           {:solve-problem solver-adapter/solve-with-quadprog})]
    (is (= :solved (:status equal-risk-result)))
    (is (nil? (:expected-return equal-risk-result)))
    (is (nil? (get-in equal-risk-result [:performance :in-sample-sharpe])))
    (is (some #(= :return-model-unavailable-for-display (:code %))
              (:warnings equal-risk-result)))
    (is (some? (:risk-contributions equal-risk-result)))
    (is (= :infeasible (:status max-sharpe-result)))
    (is (= :invalid-return-model (:reason max-sharpe-result)))))

(deftest equal-risk-presolve-failure-surfaces-specific-violations-test
  ;; Default-style exposure (gross 2, net +1) with an all-long universe
  ;; implies 0.5x short gross; the run must fail in presolve with the
  ;; specific short-book reason, before any solver call.
  (let [called? (atom false)
        result (engine/run-optimization
                (equal-risk-request {:constraints {:long-only? false
                                                   :gross-max 2.0
                                                   :net-min 1.0
                                                   :net-max 1.0
                                                   :max-asset-weight 2.0
                                                   :rebalance-tolerance 0.001}})
                {:solve-problem (fn [_]
                                  (reset! called? true)
                                  {:status :solved :weights [0.5 0.5]})})]
    (is (= :infeasible (:status result)))
    (is (= :equal-risk-presolve (:reason result)))
    (is (false? @called?))
    (is (some #(= :equal-risk-short-book-empty (:code %))
              (get-in result [:details :violations])))
    (is (some (fn [violation]
                (and (= :equal-risk-short-book-empty (:code violation))
                     (string? (:message violation))))
              (get-in result [:details :violations])))))

(deftest equal-risk-solves-mixed-books-through-request-builder-test
  ;; A short side survives the draft migration (shortable perp) and the books
  ;; land exactly on the G=2, N=0 targets.
  (let [result (engine/run-optimization
                (equal-risk-request
                 {:universe [{:instrument-id "perp:BTC"
                              :market-type :perp
                              :coin "BTC"
                              :shortable? true
                              :position-side :long}
                             {:instrument-id "perp:ETH"
                              :market-type :perp
                              :coin "ETH"
                              :shortable? true
                              :position-side :short}]
                  ;; The fixture's per-perp caps (0.75/0.7) and 0.5 turnover
                  ;; deep-merge in and would (correctly) fail against the 1.0x
                  ;; book targets from the [0.6, 0.4] current book, so this
                  ;; case raises both explicitly: caps to 1.5 and the one-sided
                  ;; turnover budget to 1.0 (the move needs sum|delta| = 1.8).
                  :constraints {:long-only? false
                                :gross-max 2.0
                                :net-min 0.0
                                :net-max 0.0
                                :max-asset-weight 1.5
                                :max-turnover 1.0
                                :perp-leverage {"perp:BTC" {:max-weight 1.5}
                                                "perp:ETH" {:max-weight 1.5}}
                                :rebalance-tolerance 0.001}})
                {:solve-problem solver-adapter/solve-with-quadprog})
        weights (:target-weights result)]
    (is (= :solved (:status result))
        (pr-str (select-keys result [:status :reason :details :message])))
    (is (near? 1.0 (nth weights 0)))
    (is (near? -1.0 (nth weights 1)))
    (is (near? 2.0 (get-in result [:diagnostics :gross-exposure]) 1e-6))
    (is (near? 0.0 (get-in result [:diagnostics :net-exposure]) 1e-6))))

(deftest equal-risk-lopsided-books-warn-on-the-solved-result-test
  ;; Feasible-but-degenerate targets (G=2, N=1.9 => 0.05x short budget shared
  ;; by TWO shorts) must solve AND carry the non-blocking lopsided-books
  ;; warning through to the published payload; the balanced mixed-books run
  ;; above must NOT carry it.
  (let [result (engine/run-optimization
                (equal-risk-request
                 {:universe [{:instrument-id "perp:BTC"
                              :market-type :perp
                              :coin "BTC"
                              :shortable? true
                              :position-side :long}
                             {:instrument-id "perp:ETH"
                              :market-type :perp
                              :coin "ETH"
                              :shortable? true
                              :position-side :short}
                             {:instrument-id "perp:SOL"
                              :market-type :perp
                              :coin "SOL"
                              :shortable? true
                              :position-side :short}]
                  :constraints {:long-only? false
                                :gross-max 2.0
                                :net-min 1.9
                                :net-max 1.9
                                :max-asset-weight 2.0
                                :max-turnover nil
                                :perp-leverage {"perp:BTC" {:max-weight 2.0}
                                                "perp:ETH" {:max-weight 2.0}}
                                :rebalance-tolerance 0.001}})
                {:solve-problem solver-adapter/solve-with-quadprog})
        lopsided (filter #(= :equal-risk-lopsided-books (:code %))
                         (:warnings result))]
    (is (= :solved (:status result))
        (pr-str (select-keys result [:status :reason :details :message])))
    (is (= 1 (count lopsided)))
    (let [warning (first lopsided)]
      (is (= :short (:book warning)))
      (is (= 2 (:asset-count warning)))
      (is (string? (:message warning))))))
