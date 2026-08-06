(ns hyperopen.views.trade.order-form-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trade.order-form.test-support :refer [base-state
                                                                  collect-strings
                                                                  button-node-by-label
                                                                  find-first-node
                                                                  find-all-nodes
                                                                  first-index]]
            [hyperopen.views.trade.order-form-view :as view]))

(deftest order-form-parity-controls-render-test
  (let [view-node (view/order-form-view (base-state))
        strings (set (collect-strings view-node))]
    (is (contains? strings "Cross"))
    (is (contains? strings "20x"))
    (is (contains? strings "Classic"))
    (is (contains? strings "Market"))
    (is (contains? strings "Limit"))
    (is (contains? strings "Pro"))
    (is (contains? strings "Buy / Long"))
    (is (contains? strings "Sell / Short"))))

(deftest order-form-renders-raw-portfolio-margin-mode-and-scale-post-only-test
  (doseq [[raw-abstraction expected-label]
          [[" portfolioMargin " "PM"]
           ["unifiedAccount" "Classic"]
           ["default" "Classic"]
           ["disabled" "Classic"]
           ["dexAbstraction" "Classic"]
           ["unknown" "Classic"]
           [nil "Classic"]]]
    (let [view-node (view/order-form-view
                     (assoc (base-state {:type :limit})
                            :account {:mode :unified
                                      :abstraction-raw raw-abstraction}))]
      (is (contains? (set (collect-strings view-node)) expected-label)
          (str "raw abstraction " (pr-str raw-abstraction)))))
  (let [view-node (view/order-form-view
                   (base-state {:type :scale :post-only true}))
        post-only-input (find-first-node view-node
                                         #(= "trade-toggle-post-only"
                                             (get-in % [1 :id])))]
    (is (some? post-only-input))
    (is (true? (get-in post-only-input [1 :checked])))))

(deftest order-form-renders-outcome-side-selector-test
  (let [state (assoc (base-state {:type :limit})
                     :active-asset "outcome:0"
                     :active-market {:coin "outcome:0"
                                     :quote "USDH"
                                     :market-type :outcome
                                     :szDecimals 0
                                     :outcome-sides [{:side-index 0
                                                      :side-name "Yes"
                                                      :coin "#0"
                                                      :asset-id 100000000}
                                                     {:side-index 1
                                                      :side-name "No"
                                                      :coin "#1"
                                                      :asset-id 100000001}]})
        view-node (view/order-form-view state)
        strings (set (collect-strings view-node))
        no-button (find-first-node view-node
                                   (fn [node]
                                     (= [[:actions/update-order-form [:outcome-side] 1]]
                                        (get-in node [1 :on :click]))))
        buy-tab (button-node-by-label view-node "Buy")
        sell-tab (button-node-by-label view-node "Sell")
        buy-yes-classes (set (get-in (button-node-by-label view-node "Buy Yes") [1 :class]))
        buy-no-classes (set (get-in (button-node-by-label view-node "Buy No") [1 :class]))]
    (is (contains? strings "Buy Yes"))
    (is (contains? strings "Buy No"))
    (is (contains? strings "Buy"))
    (is (contains? strings "Sell"))
    (is (contains? strings "USDH"))
    (is (contains? strings "Yes"))
    (is (not (contains? strings "BTC")))
    (is (not (contains? strings "Outcome")))
    (is (not (contains? strings "Buy / Long")))
    (is (not (contains? strings "Sell / Short")))
    (is (not (contains? strings "Cross")))
    (is (not (contains? strings "20x")))
    (is (not (contains? strings "Classic")))
    (is (contains? buy-yes-classes "bg-ho-accent"))
    (is (contains? buy-no-classes "bg-ho-surface-raised"))
    (is (= [[:actions/update-order-form [:side] :buy]]
           (get-in buy-tab [1 :on :click])))
    (is (= [[:actions/update-order-form [:side] :sell]]
           (get-in sell-tab [1 :on :click])))
    (is (some? no-button))))

(deftest order-form-renders-selected-outcome-no-with-sell-color-test
  (let [state (assoc (base-state {:type :limit
                                  :outcome-side 1})
                     :active-asset "outcome:0"
                     :active-market {:coin "outcome:0"
                                     :quote "USDH"
                                     :market-type :outcome
                                     :szDecimals 0
                                     :outcome-sides [{:side-index 0
                                                      :side-name "Yes"
                                                      :coin "#0"
                                                      :asset-id 100000000}
                                                     {:side-index 1
                                                      :side-name "No"
                                                      :coin "#1"
                                                      :asset-id 100000001}]})
        view-node (view/order-form-view state)
        buy-no-classes (set (get-in (button-node-by-label view-node "Buy No") [1 :class]))]
    (is (contains? buy-no-classes "bg-ho-sell-hi"))
    (is (contains? buy-no-classes "text-ho-text"))))

(defn- grouped-world-cup-state
  [ui-overrides]
  (assoc (base-state {:type :limit
                      :outcome-option-id 189
                      :outcome-side 0}
                     ui-overrides)
         :active-asset "#1890"
         :active-market {:coin "#1890"
                         :quote "USDH"
                         :market-type :outcome
                         :outcome-kind :question
                         :question-id 32
                         :szDecimals 0
                         :question-options [{:outcome-id 172
                                             :label "Algeria"
                                             :mark 0.01
                                             :volume24h 12
                                             :openInterest 41
                                             :sides [{:side-index 0 :side-label "Yes" :coin "#1720"}
                                                     {:side-index 1 :side-label "No" :coin "#1721"}]}
                                            {:outcome-id 173
                                             :label "Argentina"
                                             :mark 0.14
                                             :volume24h 2461
                                             :openInterest 35592
                                             :sides [{:side-index 0 :side-label "Yes" :coin "#1730"}
                                                     {:side-index 1 :side-label "No" :coin "#1731"}]}
                                            {:outcome-id 189
                                             :label "France"
                                             :mark 0.18
                                             :volume24h 5776
                                             :openInterest 41448
                                             :sides [{:side-index 0 :side-label "Yes" :coin "#1890"}
                                                     {:side-index 1 :side-label "No" :coin "#1891"}]}
                                            {:outcome-id 212
                                             :label "Spain"
                                             :mark 0.17
                                             :volume24h 1791
                                             :openInterest 27056
                                             :sides [{:side-index 0 :side-label "Yes" :coin "#2120"}
                                                     {:side-index 1 :side-label "No" :coin "#2121"}]}]
                         :outcome-sides [{:side-index 0
                                          :side-label "Yes"
                                          :coin "#1890"}
                                         {:side-index 1
                                          :side-label "No"
                                          :coin "#1891"}]}))

(deftest order-form-omits-multi-outcome-option-selector-from-body-test
  (let [view-node (view/order-form-view (grouped-world-cup-state {}))
        trigger (find-first-node view-node
                                 (fn [node]
                                   (= "outcome-option-select-trigger"
                                      (get-in node [1 :data-role]))))
        menu (find-first-node view-node
                              (fn [node]
                                (= "outcome-option-select-menu"
                                   (get-in node [1 :data-role]))))
        option-buttons (find-all-nodes view-node
                                       (fn [node]
                                         (= "outcome-option-select-row"
                                            (get-in node [1 :data-role]))))
        strings (set (collect-strings view-node))]
    (is (nil? trigger))
    (is (contains? strings "Buy Yes"))
    (is (contains? strings "Buy No"))
    (is (not (contains? strings "Live Outcomes")))
    (is (nil? menu))
    (is (= [] option-buttons))))

(deftest order-form-open-multi-outcome-dropdown-state-does-not-render-body-menu-test
  (let [view-node (view/order-form-view
                   (grouped-world-cup-state {:outcome-option-dropdown-open? true
                                             :outcome-option-query "sp"}))
        menu (find-first-node view-node
                              (fn [node]
                                (= "outcome-option-select-menu"
                                   (get-in node [1 :data-role]))))
        search-input (find-first-node view-node
                                      (fn [node]
                                        (= "Search outcome options"
                                           (get-in node [1 :aria-label]))))
        option-buttons (find-all-nodes view-node
                                       (fn [node]
                                         (= "outcome-option-select-row"
                                            (get-in node [1 :data-role]))))
        strings (set (collect-strings view-node))]
    (is (nil? menu))
    (is (nil? search-input))
    (is (not (contains? strings "Live Outcomes")))
    (is (not (contains? strings "Spain")))
    (is (= [] option-buttons))))

(deftest order-form-keeps-binary-outcome-options-as-two-button-selector-test
  (let [state (assoc (base-state {:type :limit
                                  :outcome-option-id 142
                                  :outcome-side 0})
                     :active-asset "#1420"
                     :active-market {:coin "#1420"
                                     :quote "USDH"
                                     :market-type :outcome
                                     :outcome-kind :question
                                     :question-id 31
                                     :szDecimals 0
                                     :question-options [{:outcome-id 141
                                                         :label "San Antonio"
                                                         :sides [{:side-index 0 :side-label "Yes" :coin "#1410"}
                                                                 {:side-index 1 :side-label "No" :coin "#1411"}]}
                                                        {:outcome-id 142
                                                         :label "New York"
                                                         :sides [{:side-index 0 :side-label "Yes" :coin "#1420"}
                                                                 {:side-index 1 :side-label "No" :coin "#1421"}]}]
                                     :outcome-sides [{:side-index 0
                                                      :side-label "Yes"
                                                      :coin "#1420"}
                                                     {:side-index 1
                                                      :side-label "No"
                                                      :coin "#1421"}]})
        view-node (view/order-form-view state)
        trigger (find-first-node view-node
                                 (fn [node]
                                   (= "outcome-option-select-trigger"
                                      (get-in node [1 :data-role]))))
        strings (set (collect-strings view-node))]
    (is (nil? trigger))
    (is (contains? strings "Buy Yes"))
    (is (contains? strings "Buy No"))
    (is (not (contains? strings "Live Outcomes")))))

(deftest leverage-row-renders-isolated-margin-label-when-selected-test
  (let [view-node (view/order-form-view (base-state {:margin-mode :isolated}))
        trigger (find-first-node view-node
                                 (fn [node]
                                   (let [attrs (when (map? (second node)) (second node))]
                                     (re-find #"^Margin mode:" (or (:aria-label attrs) "")))))
        trigger-strings (set (collect-strings trigger))]
    (is (contains? trigger-strings "Isolated"))
    (is (not (contains? trigger-strings "Cross")))))

(deftest leverage-row-forces-isolated-label-when-market-disallows-cross-test
  (let [state (assoc (base-state {:margin-mode :cross})
                     :active-market {:coin "xyz:NATGAS"
                                     :quote "USDC"
                                     :market-type :perp
                                     :marginMode "noCross"
                                     :onlyIsolated true})
        view-node (view/order-form-view state)
        strings (set (collect-strings view-node))]
    (is (contains? strings "Isolated"))
    (is (not (contains? strings "Cross")))))

(deftest leverage-popover-renders-adjust-controls-when-open-test
  (let [view-node (view/order-form-view (base-state {:type :limit}
                                                     {:leverage-popover-open? true
                                                      :leverage-draft 18}))
        strings (set (collect-strings view-node))]
    (is (contains? strings "Adjust Leverage"))
    (is (contains? strings "Maximum leverage"))
    (is (contains? strings "Max position size"))
    (is (contains? strings "Confirm"))))

(deftest submit-button-renders-before-liquidation-metrics-test
  (let [view-node (view/order-form-view (base-state {:type :limit :size "1" :price "100"}))
        tokens (vec (collect-strings view-node))
        submit-index (first-index tokens "Place Order")
        liquidation-index (first-index tokens "Liquidation Price")]
    (is (number? submit-index))
    (is (number? liquidation-index))
    (is (< submit-index liquidation-index))))

(deftest scale-preview-renders-in-footer-after-submit-test
  (let [view-node (view/order-form-view (base-state {:type :scale
                                                     :size "1000"
                                                     :scale {:start "84"
                                                             :end "79"
                                                             :count 5
                                                             :skew "1.00"}}))
        tokens (vec (collect-strings view-node))
        submit-index (first-index tokens "Place Order")
        start-index (first-index tokens "Start")
        end-index (first-index tokens "End")
        order-value-index (first-index tokens "Order Value")
        margin-index (first-index tokens "Margin Required")
        fees-index (first-index tokens "Fees")]
    (is (number? submit-index))
    (is (number? start-index))
    (is (number? end-index))
    (is (number? order-value-index))
    (is (number? margin-index))
    (is (number? fees-index))
    (is (< submit-index start-index))
    (is (< start-index end-index))
    (is (< end-index order-value-index))
    (is (< order-value-index margin-index))
    (is (< margin-index fees-index))))

(deftest submit-button-uses-compact-height-test
  (let [view-node (view/order-form-view (base-state))
        submit-button (find-first-node view-node
                                       (fn [node]
                                         (let [attrs (when (map? (second node)) (second node))]
                                           (= "trade-submit-order-button"
                                              (:data-parity-id attrs)))))
        classes (set (get-in submit-button [1 :class]))]
    (is (some? submit-button))
    (is (contains? classes "h-[33px]"))
    (is (not (contains? classes "h-10")))))

(deftest order-form-panel-does-not-force-legacy-min-height-test
  (let [view-node (view/order-form-view (base-state))
        panel (find-first-node view-node
                               (fn [node]
                                 (let [attrs (when (map? (second node)) (second node))]
                                   (= "order-form" (:data-parity-id attrs)))))
        classes (set (get-in panel [1 :class]))]
    (is (some? panel))
    (is (not (contains? classes "min-h-[500px]")))
    (is (not (contains? classes "lg:min-h-[560px]")))
    (is (not (contains? classes "xl:min-h-[640px]")))))
