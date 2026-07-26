(ns hyperopen.portfolio.optimizer.application.universe-candidates-cache-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.universe-candidates :as universe-candidates]))

(defn- market-keys
  [markets]
  (mapv :key markets))

(deftest candidate-markets-does-not-cache-vault-pool-without-explicit-cache-test
  (let [sigma-vault-address "0x1313131313131313131313131313131313131313"
        tau-vault-address "0x1414141414141414141414141414141414141414"
        rows [{:name "Sigma Yield"
               :vault-address sigma-vault-address
               :relationship {:type :normal}
               :tvl 900}
              {:name "Tau Carry"
               :vault-address tau-vault-address
               :relationship {:type :normal}
               :tvl 450}]
        state {:asset-selector {:markets []}
               :vaults {:merged-index-rows rows}}
        build-count (atom 0)
        original-builder universe-candidates/vault-row->candidate]
    (with-redefs [universe-candidates/vault-row->candidate
                  (fn [row]
                    (swap! build-count inc)
                    (original-builder row))]
      (let [sigma-candidates (universe-candidates/candidate-markets state [] "sigma")
            tau-candidates (universe-candidates/candidate-markets state [] "tau")]
        (is (= [(str "vault:" sigma-vault-address)]
               (market-keys sigma-candidates)))
        (is (= [(str "vault:" tau-vault-address)]
               (market-keys tau-candidates)))
        (is (= 4 @build-count))))))

(deftest candidate-markets-uses-explicit-vault-cache-without-sharing-between-caches-test
  (let [upsilon-vault-address "0x1515151515151515151515151515151515151515"
        phi-vault-address "0x1616161616161616161616161616161616161616"
        rows [{:name "Upsilon Yield"
               :vault-address upsilon-vault-address
               :relationship {:type :normal}
               :tvl 900}
              {:name "Phi Carry"
               :vault-address phi-vault-address
               :relationship {:type :normal}
               :tvl 450}]
        state {:asset-selector {:markets []}
               :vaults {:merged-index-rows rows}}
        cache-a (atom nil)
        cache-b (atom nil)
        build-count (atom 0)
        original-builder universe-candidates/vault-row->candidate]
    (with-redefs [universe-candidates/vault-row->candidate
                  (fn [row]
                    (swap! build-count inc)
                    (original-builder row))]
      (let [cache-a-upsilon (universe-candidates/candidate-markets
                             state
                             []
                             "upsilon"
                             {:vault-candidate-cache cache-a})
            cache-b-phi (universe-candidates/candidate-markets
                         state
                         []
                         "phi"
                         {:vault-candidate-cache cache-b})
            cache-a-phi (universe-candidates/candidate-markets
                         state
                         []
                         "phi"
                         {:vault-candidate-cache cache-a})]
        (is (= [(str "vault:" upsilon-vault-address)]
               (market-keys cache-a-upsilon)))
        (is (= [(str "vault:" phi-vault-address)]
               (market-keys cache-b-phi)))
        (is (= [(str "vault:" phi-vault-address)]
               (market-keys cache-a-phi)))
        (is (= 4 @build-count))))))

(deftest candidate-markets-reuses-vault-candidate-maps-for-the-same-row-vector-across-queries-test
  (let [gamma-vault-address "0x4444444444444444444444444444444444444444"
        delta-vault-address "0x5555555555555555555555555555555555555555"
        rows [{:name "Gamma Yield"
               :vault-address gamma-vault-address
               :relationship {:type :normal}
               :tvl 900}
              {:name "Delta Carry"
               :vault-address delta-vault-address
               :relationship {:type :normal}
               :tvl 450}]
        state {:asset-selector {:markets []}
               :vaults {:merged-index-rows rows}}
        cache (atom nil)
        build-count (atom 0)
        original-builder universe-candidates/vault-row->candidate]
    (with-redefs [universe-candidates/vault-row->candidate
                  (fn [row]
                    (swap! build-count inc)
                    (original-builder row))]
      (let [opts {:vault-candidate-cache cache}
            gamma-candidates (universe-candidates/candidate-markets state [] "gamma" opts)
            delta-candidates (universe-candidates/candidate-markets state [] "delta" opts)
            address-candidates (universe-candidates/candidate-markets
                                state
                                []
                                (subs delta-vault-address 0 10)
                                opts)]
        (is (= [(str "vault:" gamma-vault-address)]
               (market-keys gamma-candidates)))
        (is (= [(str "vault:" delta-vault-address)]
               (market-keys delta-candidates)))
        (is (= [(str "vault:" delta-vault-address)]
               (market-keys address-candidates)))
        (is (= 2 @build-count))))))

(deftest candidate-markets-reuses-cached-vault-pool-for-equal-cloned-merged-index-rows-test
  (let [lambda-vault-address "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        omega-vault-address "0xcccccccccccccccccccccccccccccccccccccccc"
        rows [{:name "Lambda Carry"
               :vault-address lambda-vault-address
               :relationship {:type :normal}
               :tvl 880}
              {:name "Omega Basis"
               :vault-address omega-vault-address
               :relationship {:type :normal}
               :tvl 610}]
        cloned-rows (mapv identity rows)
        state-a {:asset-selector {:markets []}
                 :vaults {:merged-index-rows rows}}
        state-b {:asset-selector {:markets []}
                 :vaults {:merged-index-rows cloned-rows}}
        cache (atom nil)
        build-count (atom 0)
        original-builder universe-candidates/vault-row->candidate]
    (is (= rows cloned-rows))
    (is (not (identical? rows cloned-rows)))
    (with-redefs [universe-candidates/vault-row->candidate
                  (fn [row]
                    (swap! build-count inc)
                    (original-builder row))]
      (let [opts {:vault-candidate-cache cache}
            lambda-candidates (universe-candidates/candidate-markets state-a [] "lambda" opts)
            omega-candidates (universe-candidates/candidate-markets
                              state-b
                              []
                              (subs omega-vault-address 0 10)
                              opts)]
        (is (= [(str "vault:" lambda-vault-address)]
               (market-keys lambda-candidates)))
        (is (= [(str "vault:" omega-vault-address)]
               (market-keys omega-candidates)))
        (is (= 2 @build-count))))))

(deftest candidate-markets-invalidates-vault-cache-when-merged-index-rows-change-test
  (let [epsilon-vault-address "0x6666666666666666666666666666666666666666"
        zeta-vault-address "0x7777777777777777777777777777777777777777"
        theta-vault-address "0x8888888888888888888888888888888888888888"
        rows-a [{:name "Epsilon Yield"
                 :vault-address epsilon-vault-address
                 :relationship {:type :normal}
                 :tvl 600}
                {:name "Zeta Carry"
                 :vault-address zeta-vault-address
                 :relationship {:type :normal}
                 :tvl 300}]
        rows-b [{:name "Epsilon Yield"
                 :vault-address epsilon-vault-address
                 :relationship {:type :normal}
                 :tvl 600}
                {:name "Zeta Carry"
                 :vault-address zeta-vault-address
                 :relationship {:type :normal}
                 :tvl 300}
                {:name "Theta Basis"
                 :vault-address theta-vault-address
                 :relationship {:type :normal}
                 :tvl 950}]
        state-a {:asset-selector {:markets []}
                 :vaults {:merged-index-rows rows-a}}
        state-b {:asset-selector {:markets []}
                 :vaults {:merged-index-rows rows-b}}
        cache (atom nil)
        build-count (atom 0)
        original-builder universe-candidates/vault-row->candidate]
    (with-redefs [universe-candidates/vault-row->candidate
                  (fn [row]
                    (swap! build-count inc)
                    (original-builder row))]
      (let [opts {:vault-candidate-cache cache}
            epsilon-candidates (universe-candidates/candidate-markets state-a [] "epsilon" opts)
            zeta-candidates (universe-candidates/candidate-markets state-a [] "zeta" opts)
            theta-candidates (universe-candidates/candidate-markets state-b [] "theta" opts)]
        (is (= [(str "vault:" epsilon-vault-address)]
               (market-keys epsilon-candidates)))
        (is (= [(str "vault:" zeta-vault-address)]
               (market-keys zeta-candidates)))
        (is (= [(str "vault:" theta-vault-address)]
               (market-keys theta-candidates)))
        (is (= 5 @build-count))))))

(deftest candidate-markets-invalidates-vault-cache-when-name-changes-at-the-same-row-count-test
  (let [rename-vault-address "0xdddddddddddddddddddddddddddddddddddddddd"
        rows-a [{:name "Legacy Delta"
                 :vault-address rename-vault-address
                 :relationship {:type :normal}
                 :tvl 410}]
        rows-b [{:name "Renamed Delta"
                 :vault-address rename-vault-address
                 :relationship {:type :normal}
                 :tvl 410}]
        state-a {:asset-selector {:markets []}
                 :vaults {:merged-index-rows rows-a}}
        state-b {:asset-selector {:markets []}
                 :vaults {:merged-index-rows rows-b}}
        cache (atom nil)
        build-count (atom 0)
        original-builder universe-candidates/vault-row->candidate]
    (with-redefs [universe-candidates/vault-row->candidate
                  (fn [row]
                    (swap! build-count inc)
                    (original-builder row))]
      (let [opts {:vault-candidate-cache cache}
            legacy-candidates (universe-candidates/candidate-markets state-a [] "legacy" opts)
            renamed-candidates (universe-candidates/candidate-markets state-b [] "renamed" opts)]
        (is (= [(str "vault:" rename-vault-address)]
               (market-keys legacy-candidates)))
        (is (= [(str "vault:" rename-vault-address)]
               (market-keys renamed-candidates)))
        (is (= "Renamed Delta" (:name (first renamed-candidates))))
        (is (= 2 @build-count))))))

(deftest candidate-markets-invalidates-vault-cache-when-tvl-changes-at-the-same-row-count-test
  (let [mu-vault-address "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        nu-vault-address "0xffffffffffffffffffffffffffffffffffffffff"
        rows-a [{:name "Mu Yield"
                 :vault-address mu-vault-address
                 :relationship {:type :normal}
                 :tvl 100}
                {:name "Nu Yield"
                 :vault-address nu-vault-address
                 :relationship {:type :normal}
                 :tvl 200}]
        rows-b [{:name "Mu Yield"
                 :vault-address mu-vault-address
                 :relationship {:type :normal}
                 :tvl 300}
                {:name "Nu Yield"
                 :vault-address nu-vault-address
                 :relationship {:type :normal}
                 :tvl 200}]
        state-a {:asset-selector {:markets []}
                 :vaults {:merged-index-rows rows-a}}
        state-b {:asset-selector {:markets []}
                 :vaults {:merged-index-rows rows-b}}
        cache (atom nil)
        build-count (atom 0)
        original-builder universe-candidates/vault-row->candidate]
    (with-redefs [universe-candidates/vault-row->candidate
                  (fn [row]
                    (swap! build-count inc)
                    (original-builder row))]
      (let [opts {:vault-candidate-cache cache}
            before-reorder (universe-candidates/candidate-markets state-a [] "yield" opts)
            after-reorder (universe-candidates/candidate-markets state-b [] "yield" opts)]
        (is (= [(str "vault:" nu-vault-address)
                (str "vault:" mu-vault-address)]
               (market-keys before-reorder)))
        (is (= [(str "vault:" mu-vault-address)
                (str "vault:" nu-vault-address)]
               (market-keys after-reorder)))
        (is (= 4 @build-count))))))

(deftest candidate-markets-invalidates-vault-cache-when-relationship-type-changes-at-the-same-row-count-test
  (let [rho-vault-address "0x1212121212121212121212121212121212121212"
        rows-a [{:name "Rho Carry"
                 :vault-address rho-vault-address
                 :relationship {:type :child}
                 :tvl 710}]
        rows-b [{:name "Rho Carry"
                 :vault-address rho-vault-address
                 :relationship {:type :normal}
                 :tvl 710}]
        state-a {:asset-selector {:markets []}
                 :vaults {:merged-index-rows rows-a}}
        state-b {:asset-selector {:markets []}
                 :vaults {:merged-index-rows rows-b}}
        cache (atom nil)
        build-count (atom 0)
        original-builder universe-candidates/vault-row->candidate]
    (with-redefs [universe-candidates/vault-row->candidate
                  (fn [row]
                    (swap! build-count inc)
                    (original-builder row))]
      (let [opts {:vault-candidate-cache cache}
            eligible-candidates (universe-candidates/candidate-markets state-a [] "rho" opts)
            ineligible-candidates (universe-candidates/candidate-markets state-b [] "rho" opts)]
        (is (= [] (market-keys eligible-candidates)))
        (is (= [(str "vault:" rho-vault-address)]
               (market-keys ineligible-candidates)))
        (is (= 1 @build-count))))))

(deftest candidate-markets-recomputes-selected-id-exclusion-from-the-passed-universe-test
  (let [iota-vault-address "0x9999999999999999999999999999999999999999"
        kappa-vault-address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        rows [{:name "Iota Yield"
               :vault-address iota-vault-address
               :relationship {:type :normal}
               :tvl 700}
              {:name "Kappa Carry"
               :vault-address kappa-vault-address
               :relationship {:type :normal}
               :tvl 500}]
        state {:asset-selector {:markets []}
               :vaults {:merged-index-rows rows}}
        cache (atom nil)
        build-count (atom 0)
        original-builder universe-candidates/vault-row->candidate]
    (with-redefs [universe-candidates/vault-row->candidate
                  (fn [row]
                    (swap! build-count inc)
                    (original-builder row))]
      (let [opts {:vault-candidate-cache cache}
            without-kappa (universe-candidates/candidate-markets
                           state
                           [{:instrument-id (str "vault:" kappa-vault-address)}]
                           "vault"
                           opts)
            without-iota (universe-candidates/candidate-markets
                          state
                          [{:instrument-id (str "vault:" iota-vault-address)}]
                          "vault"
                          opts)
            unfiltered (universe-candidates/candidate-markets state [] "vault" opts)]
        (is (= [(str "vault:" iota-vault-address)]
               (market-keys without-kappa)))
        (is (= [(str "vault:" kappa-vault-address)]
               (market-keys without-iota)))
        (is (= [(str "vault:" iota-vault-address)
                (str "vault:" kappa-vault-address)]
               (market-keys unfiltered)))
        (is (= 2 @build-count))))))
