(ns hyperopen.views.active-asset.outcome-option-sort-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.active-asset.row :as row]
            [hyperopen.views.active-asset.test-support :as support]))

(defn- outcome-option-handlers-fixture []
  {:on-select-outcome-option (fn [outcome-id]
                               [[:actions/close-outcome-option-dropdown]
                                [:actions/update-order-form [:outcome-option-id] outcome-id]])
   :on-toggle-option-dropdown [[:actions/toggle-outcome-option-dropdown]]
   :on-option-dropdown-keydown [[:actions/handle-outcome-option-dropdown-keydown [:event/key]]]
   :on-change-option-query [[:actions/set-outcome-option-query [:event.target/value]]]
   :on-sort-option-column (fn [column]
                            [[:actions/set-outcome-option-sort column]])})

(defn- find-all-nodes
  [node pred]
  (letfn [(walk [n]
            (cond
              (vector? n)
              (let [children (if (map? (second n))
                               (drop 2 n)
                               (drop 1 n))
                    child-results (mapcat walk children)]
                (if (pred n)
                  (cons n child-results)
                  child-results))

              (seq? n)
              (mapcat walk n)

              :else []))]
    (vec (walk node))))

(defn- world-cup-row-vm
  [option-ui]
  {:is-outcome true
   :icon-market {:coin "#1890"
                 :symbol "2026 World Cup Champion"
                 :market-type :outcome}
   :dropdown-visible? false
   :missing-icons #{}
   :loaded-icons #{}
   :outcome-options [{:outcome-id 173
                      :label "Argentina"
                      :mark 0.14
                      :volume24h 2461
                      :openInterest 35592}
                     {:outcome-id 189
                      :label "France"
                      :mark 0.18
                      :volume24h 5776
                      :openInterest 41448}
                     {:outcome-id 212
                      :label "Spain"
                      :mark 0.17
                      :volume24h 1791
                      :openInterest 27056}]
   :outcome-option-id 189
   :outcome-option-ui option-ui
   :outcome-chance-label "18%"
   :countdown-text "10h 8m"
   :mark 0.17514
   :markRaw "0.17514"
   :change24h 0.01413
   :change24hPct 8.78
   :volume24h 5776
   :open-interest-usd 41448})

(deftest desktop-outcome-row-sorts-searchable-option-menu-from-header-test
  (support/with-viewport-width
    1280
    (fn []
      (let [view-node (row/active-asset-row-from-vm
                       (world-cup-row-vm {:open? true
                                          :query ""
                                          :sort-by :chance
                                          :sort-direction :desc})
                       {:outcome-handlers (outcome-option-handlers-fixture)})
            header (support/find-node
                    #(and (vector? %)
                          (= :button (first %))
                          (= "Sort outcomes by chance"
                             (get-in % [1 :aria-label])))
                    view-node)
            option-labels (->> (find-all-nodes
                                view-node
                                #(= "outcome-option-select-row"
                                    (get-in % [1 :data-role])))
                               (mapv #(first (support/collect-strings %))))]
        (is (some? header))
        (is (= [[:actions/set-outcome-option-sort :chance]]
               (get-in header [1 :on :click])))
        (is (= ["France" "Spain" "Argentina"] option-labels))))))
