(ns hyperopen.views.notifications-view-trade-confirmation-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.notifications-view :as notifications-view]))

(defn- fill-prop
  ([id side symbol qty price ts]
   (fill-prop id side symbol qty price ts "limit"))
  ([id side symbol qty price ts order-type]
  {:id id
   :side side
   :symbol symbol
   :qty qty
   :price price
   :orderType order-type
   :ts ts}))

(defn- find-by-class
  [node class-name]
  (hiccup/find-first-node node #(contains? (hiccup/node-class-set %) class-name)))

(deftest notifications-view-renders-order-submitted-toast-with-confirmation-styling-test
  (let [view-node (notifications-view/notifications-view
                   {:ui {:toasts [{:id "submit"
                                   :kind :success
                                   :toast-surface :order-submitted
                                   :headline "Order submitted"
                                   :subline "Awaiting fill confirmation"
                                   :message "Order submitted."}]}})
        toast-node (hiccup/find-by-data-role view-node "global-toast")
        toast-classes (hiccup/node-class-set toast-node)
        dismiss-node (hiccup/find-by-data-role toast-node "global-toast-dismiss")]
    (is (contains? toast-classes "o-toast"))
    (is (contains? toast-classes "pointer-events-auto"))
    (is (not (contains? toast-classes "global-toast-surface")))
    (is (some? (find-by-class toast-node "check")))
    (is (some? (find-by-class toast-node "msg")))
    (is (= "Order submitted"
           (hiccup/node-text (find-by-class toast-node "line1"))))
    (is (= "Awaiting fill confirmation"
           (hiccup/node-text (find-by-class toast-node "line2"))))
    (is (contains? (hiccup/node-class-set dismiss-node) "close"))
    (is (= [[:actions/dismiss-order-feedback-toast "submit"]]
           (get-in dismiss-node [1 :on :click])))))

(deftest notifications-view-renders-order-canceled-toast-with-confirmation-styling-test
  (let [view-node (notifications-view/notifications-view
                   {:ui {:toasts [{:id "cancel"
                                   :kind :success
                                   :toast-surface :order-canceled
                                   :headline "Order canceled"
                                   :subline "Open orders updated"
                                   :message "Order canceled."}]}})
        toast-node (hiccup/find-by-data-role view-node "global-toast")
        toast-classes (hiccup/node-class-set toast-node)
        dismiss-node (hiccup/find-by-data-role toast-node "global-toast-dismiss")]
    (is (contains? toast-classes "o-toast"))
    (is (contains? toast-classes "pointer-events-auto"))
    (is (not (contains? toast-classes "global-toast-surface")))
    (is (some? (find-by-class toast-node "check")))
    (is (some? (find-by-class toast-node "msg")))
    (is (= "Order canceled"
           (hiccup/node-text (find-by-class toast-node "line1"))))
    (is (= "Open orders updated"
           (hiccup/node-text (find-by-class toast-node "line2"))))
    (is (contains? (hiccup/node-class-set dismiss-node) "close"))
    (is (= [[:actions/dismiss-order-feedback-toast "cancel"]]
           (get-in dismiss-node [1 :on :click])))))

(deftest notifications-view-renders-readable-generic-error-detail-test
  (let [view-node (notifications-view/notifications-view
                   {:ui {:toasts [{:id "reject"
                                   :kind :error
                                   :headline "Order not placed"
                                   :subline "The exchange rejected this order."
                                   :detail "Order could not be closed because there is insufficient liquidity."
                                   :message "Order not placed: Order could not be closed because there is insufficient liquidity."}]}})
        toast-node (hiccup/find-by-data-role view-node "global-toast")
        headline-node (hiccup/find-by-data-role toast-node "global-toast-headline")
        subline-node (hiccup/find-by-data-role toast-node "global-toast-subline")
        detail-node (hiccup/find-by-data-role toast-node "global-toast-detail")]
    (is (some? toast-node))
    (is (= "error" (get-in toast-node [1 :data-toast-kind])))
    (is (= "Order not placed" (hiccup/node-text headline-node)))
    (is (= "The exchange rejected this order." (hiccup/node-text subline-node)))
    (is (= "Order could not be closed because there is insufficient liquidity."
           (hiccup/node-text detail-node)))
    (is (not (contains? (hiccup/node-class-set detail-node) "truncate")))
    (is (contains? (hiccup/node-class-set detail-node) "whitespace-normal"))
    (is (contains? (hiccup/node-class-set detail-node) "break-words"))))

(deftest notifications-view-renders-trade-confirmation-toast-variants-test
  (let [fills [(fill-prop "fill-1" :buy "HYPE" 0.25 44.20 1800000000000)
               (fill-prop "fill-2" :buy "HYPE" 0.30 44.30 1800000003300)
               (fill-prop "fill-3" :sell "SOL" 1.00 198.10 1800000006600)
               (fill-prop "fill-4" :buy "BTC" 0.01 65124.00 1800000009900)]
        view-node (notifications-view/notifications-view
                   {:ui {:toasts [{:id "pill"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :pill
                                   :fills [(first fills)]}
                                  {:id "detailed"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :detailed
                                   :fills [(assoc (first fills)
                                                 :qty 4.23
                                                 :orderType "market"
                                                 :slippagePct -0.02)]}
                                  {:id "stack"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :stack
                                   :fills fills}
                                  {:id "consolidated"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :consolidated
                                   :fills (mapv #(assoc % :side :buy :symbol "HYPE") fills)}]}})
        region (hiccup/find-by-data-role view-node "global-toast-region")
        pill-node (hiccup/find-by-data-role view-node "PillToast")
        detailed-node (hiccup/find-by-data-role view-node "DetailedToast")
        stack-node (hiccup/find-by-data-role view-node "ToastStack")
        consolidated-node (hiccup/find-by-data-role view-node "ConsolidatedToast")
        expand-buttons (hiccup/find-all-nodes
                        view-node
                        #(= "trade-toast-expand" (get-in % [1 :data-role])))
        close-buttons (hiccup/find-all-nodes
                       view-node
                       #(= "trade-toast-dismiss" (get-in % [1 :data-role])))]
    (is (= "status" (:role (second region))))
    (is (= "polite" (:aria-live (second region))))
    (is (contains? (hiccup/node-class-set pill-node) "o-toast"))
    (is (contains? (hiccup/node-class-set detailed-node) "detailed"))
    (is (contains? (hiccup/node-class-set stack-node) "o-stack"))
    (is (contains? (hiccup/node-class-set consolidated-node) "o-consol"))
    (is (contains? (hiccup/node-class-set pill-node) "pointer-events-auto"))
    (is (contains? (hiccup/node-class-set detailed-node) "pointer-events-auto"))
    (is (contains? (hiccup/node-class-set stack-node) "pointer-events-auto"))
    (is (contains? (hiccup/node-class-set consolidated-node) "pointer-events-auto"))
    (is (contains? (set (hiccup/collect-strings detailed-node)) "Avg Price"))
    (is (contains? (set (hiccup/collect-strings view-node)) "+1 more fills · collapse into blotter"))
    (is (= #{:button} (set (map first close-buttons))))
    (is (= #{:button} (set (map first expand-buttons))))
    (is (= [[:actions/expand-order-feedback-toast "stack"]]
           (get-in (first expand-buttons) [1 :on :click])))))

(deftest notifications-view-renders-expanded-trade-confirmation-blotter-test
  (let [fills [(fill-prop "fill-1" :buy "HYPE" 0.25 44.20 1800000000000)
               (fill-prop "fill-2" :buy "HYPE" 0.30 44.30 1800000003300)
               (fill-prop "fill-3" :sell "SOL" 1.00 198.10 1800000006600)
               (fill-prop "fill-4" :sell "SOL" 2.00 198.20 1800000009900)]
        view-node (notifications-view/notifications-view
                   {:ui {:toasts [{:id "blotter"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :stack
                                   :expanded? true
                                   :fills fills}]}})
        blotter-node (hiccup/find-by-data-role view-node "BlotterCard")
        collapse-button (hiccup/find-by-data-role view-node "trade-toast-collapse")
        history-link (hiccup/find-by-data-role view-node "trade-toast-view-full-history")
        rendered-strings (set (hiccup/collect-strings blotter-node))
        rendered-text (str/join " " (hiccup/collect-strings blotter-node))]
    (is (some? blotter-node))
    (is (contains? (hiccup/node-class-set blotter-node) "o-blotter"))
    (is (contains? (hiccup/node-class-set blotter-node) "pointer-events-auto"))
    (is (= "true"
           (get-in blotter-node [1 :data-trade-blotter-surface])))
    (is (contains? rendered-strings "Activity · 4 fills"))
    (is (re-find #"Bought\s+0\.55\s+HYPE" rendered-text))
    (is (re-find #"Sold\s+3\s+SOL" rendered-text))
    (is (= :button (first collapse-button)))
    (is (= [[:actions/collapse-order-feedback-toast "blotter"]]
           (get-in collapse-button [1 :on :click])))
    (is (= :a (first history-link)))
    (is (= "/portfolio?tab=order-history"
           (get-in history-link [1 :href])))))

(deftest expanded-trade-confirmation-blotter-renders-net-flow-as-signed-usd-test
  (let [fills [(fill-prop "fill-1" :buy "UPUMP" 1000000 0.0005 1800000000000)
               (fill-prop "fill-2" :sell "SOL" 10 25.125 1800000003300)
               (fill-prop "fill-3" :buy "MOON" 100 1.2342 1800000006600)]
        view-node (notifications-view/notifications-view
                   {:ui {:toasts [{:id "blotter"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :stack
                                   :expanded? true
                                   :fills fills}]}})
        blotter-node (hiccup/find-by-data-role view-node "BlotterCard")
        rendered-text (str/join " " (hiccup/collect-strings blotter-node))]
    (is (re-find #"Net Flow\s+\+\$372\.17\s+Notional\s+\$874\.67" rendered-text))
    (is (not (re-find #"Net Flow\s+\+\s+1,000,090" rendered-text)))))

(deftest expanded-trade-confirmation-blotter-explains-summary-money-terms-test
  (let [fills [(fill-prop "fill-1" :buy "UPUMP" 1000000 0.0005 1800000000000)
               (fill-prop "fill-2" :sell "SOL" 10 25.125 1800000003300)
               (fill-prop "fill-3" :buy "MOON" 100 1.2342 1800000006600)]
        view-node (notifications-view/notifications-view
                   {:ui {:toasts [{:id "blotter"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :stack
                                   :expanded? true
                                   :fills fills}]}})
        blotter-node (hiccup/find-by-data-role view-node "BlotterCard")
        net-flow-node (hiccup/find-first-node
                       blotter-node
                       #(= "Signed USD value of buys minus sells."
                           (:title (hiccup/node-attrs %))))
        notional-node (hiccup/find-first-node
                       blotter-node
                       #(= "Gross USD value of all fills."
                           (:title (hiccup/node-attrs %))))]
    (is (some? net-flow-node))
    (is (some? notional-node))))

(deftest expanded-trade-confirmation-blotter-footer-describes-grouped-fill-rate-test
  (let [fills [(fill-prop "fill-1" :buy "HYPE" 0.25 44.20 1800000000000)
               (fill-prop "fill-2" :buy "HYPE" 0.30 44.30 1800000003300)
               (fill-prop "fill-3" :sell "SOL" 1.00 198.10 1800000006600)
               (fill-prop "fill-4" :sell "SOL" 2.00 198.20 1800000009900)]
        view-node (notifications-view/notifications-view
                   {:ui {:toasts [{:id "blotter"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :stack
                                   :expanded? true
                                   :fills fills}]}})
        blotter-node (hiccup/find-by-data-role view-node "BlotterCard")
        rendered-strings (set (hiccup/collect-strings blotter-node))]
    (is (contains? rendered-strings "Grouped fills · avg 0.3 fills/sec"))
    (is (not (contains? rendered-strings "TWAP · avg 1.2 fills/sec")))))

(deftest expanded-trade-confirmation-blotter-footer-describes-twap-fill-rate-test
  (let [fills [(fill-prop "fill-1" :buy "HYPE" 0.25 44.20 1800000000000 "twap")
               (fill-prop "fill-2" :buy "HYPE" 0.30 44.30 1800000001000 "twap")
               (fill-prop "fill-3" :buy "HYPE" 0.40 44.40 1800000002000 "twap")
               (fill-prop "fill-4" :buy "HYPE" 0.50 44.50 1800000003000 "twap")]
        view-node (notifications-view/notifications-view
                   {:ui {:toasts [{:id "blotter"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :consolidated
                                   :expanded? true
                                   :fills fills}]}})
        blotter-node (hiccup/find-by-data-role view-node "BlotterCard")
        rendered-strings (set (hiccup/collect-strings blotter-node))]
    (is (contains? rendered-strings "TWAP · avg 1.0 fills/sec"))))

(deftest expanded-trade-confirmation-blotter-history-link-preserves-spectate-address-test
  (let [spectate-address "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
        view-node (notifications-view/notifications-view
                   {:account-context {:spectate-mode {:active? true
                                                       :address spectate-address}}
                    :ui {:toasts [{:id "blotter"
                                   :kind :success
                                   :toast-surface :trade-confirmation
                                   :variant :consolidated
                                   :expanded? true
                                   :fills [(fill-prop "fill-1" :buy "HYPE" 0.25 44.20 1800000000000)
                                           (fill-prop "fill-2" :buy "HYPE" 0.30 44.30 1800000003300)
                                           (fill-prop "fill-3" :buy "HYPE" 0.40 44.40 1800000006600)
                                           (fill-prop "fill-4" :buy "HYPE" 0.50 44.50 1800000009900)]}]}})
        history-link (hiccup/find-by-data-role view-node "trade-toast-view-full-history")]
    (is (= "/portfolio?spectate=0xabcdefabcdefabcdefabcdefabcdefabcdefabcd&tab=order-history"
           (get-in history-link [1 :href])))))
