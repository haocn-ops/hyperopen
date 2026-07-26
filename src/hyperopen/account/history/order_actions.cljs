(ns hyperopen.account.history.order-actions
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.account.history.shared :as history-shared]
            [hyperopen.platform :as platform]))

(def ^:private order-history-freshness-ttl-ms
  60000)

(def ^:private trade-history-direction-filter-options
  #{:all :long :short})

(def ^:private order-history-status-options
  #{:all :long :short})

(defn default-order-history-state []
  {:sort {:column "Time" :direction :desc}
   :status-filter :all
   :coin-search ""
   :filter-open? false
   :page-size history-shared/default-order-history-page-size
   :page 1
   :page-input "1"
   :loading? false
   :error nil
   :request-id 0
   :loaded-at-ms nil
   :loaded-for-address nil})

(defn default-trade-history-state []
  {:sort {:column "Time" :direction :desc}
   :direction-filter :all
   :coin-search ""
   :filter-open? false
   :page-size history-shared/default-order-history-page-size
   :page 1
   :page-input "1"})

(defn restore-order-history-pagination-settings! [store]
  (let [page-size (history-shared/normalize-order-history-page-size
                   (platform/local-storage-get "order-history-page-size"))]
    (swap! store update-in [:account-info :order-history] merge
           {:page-size page-size
            :page 1
            :page-input "1"})))

(defn restore-trade-history-pagination-settings! [store]
  (let [page-size (history-shared/normalize-order-history-page-size
                   (platform/local-storage-get "trade-history-page-size"))]
    (swap! store update-in [:account-info :trade-history] merge
           {:page-size page-size
            :page 1
            :page-input "1"})))

(defn order-history-request-id
  [state]
  (get-in state [:account-info :order-history :request-id] 0))

(defn- normalize-address
  [address]
  (let [text (some-> address str str/trim str/lower-case)]
    (when (seq text)
      text)))

(defn- order-history-fresh?
  [state now-ms]
  (let [loaded-at-ms (get-in state [:account-info :order-history :loaded-at-ms])
        loaded-for-address (normalize-address
                            (get-in state [:account-info :order-history :loaded-for-address]))
        effective-address (normalize-address (account-context/effective-account-address state))
        error (get-in state [:account-info :order-history :error])]
    (and (number? loaded-at-ms)
         (number? now-ms)
         (some? effective-address)
         (= effective-address loaded-for-address)
         (nil? error)
         (let [age-ms (- now-ms loaded-at-ms)]
           (<= 0 age-ms order-history-freshness-ttl-ms)))))

(defn- normalize-order-history-status-filter
  [status]
  (let [status* (cond
                  (keyword? status) status
                  (string? status) (keyword (str/lower-case status))
                  :else :all)]
    (if (contains? order-history-status-options status*)
      status*
      :all)))

(defn- normalize-trade-history-direction-filter
  [direction-filter]
  (let [direction* (cond
                     (keyword? direction-filter) direction-filter
                     (string? direction-filter) (keyword (str/lower-case direction-filter))
                     :else :all)]
    (if (contains? trade-history-direction-filter-options direction*)
      direction*
      :all)))

(defn select-order-history-tab [state]
  (let [order-history-loading? (true? (get-in state [:account-info :order-history :loading?]))
        should-fetch? (and (not order-history-loading?)
                           (not (order-history-fresh? state (platform/now-ms))))]
    (if should-fetch?
      (let [request-id (inc (order-history-request-id state))]
        [[:effects/save-many [[[:account-info :selected-tab] :order-history]
                              [[:account-info :order-history :loading?] true]
                              [[:account-info :order-history :error] nil]
                              [[:account-info :order-history :request-id] request-id]]]
         [:effects/api-fetch-historical-orders request-id]])
      [[:effects/save [:account-info :selected-tab] :order-history]])))

(defn set-trade-history-page-size [state page-size]
  (let [locale (get-in state [:ui :locale])
        page-size* (history-shared/normalize-order-history-page-size page-size locale)]
    [[:effects/save-many [[[:account-info :trade-history :page-size] page-size*]
                          [[:account-info :trade-history :page] 1]
                          [[:account-info :trade-history :page-input] "1"]]]
     [:effects/local-storage-set "trade-history-page-size" (str page-size*)]]))

(defn set-trade-history-page [state page max-page]
  (let [locale (get-in state [:ui :locale])
        page* (history-shared/normalize-order-history-page page max-page locale)]
    [[:effects/save-many [[[:account-info :trade-history :page] page*]
                          [[:account-info :trade-history :page-input] (str page*)]]]]))

(defn next-trade-history-page [state max-page]
  (let [current-page (get-in state [:account-info :trade-history :page] 1)]
    (set-trade-history-page state (inc current-page) max-page)))

(defn prev-trade-history-page [state max-page]
  (let [current-page (get-in state [:account-info :trade-history :page] 1)]
    (set-trade-history-page state (dec current-page) max-page)))

(defn set-trade-history-page-input [_state input-value]
  [[:effects/save [:account-info :trade-history :page-input]
    (if (string? input-value)
      input-value
      (str (or input-value "")))]] )

(defn apply-trade-history-page-input [state max-page]
  (let [raw-value (get-in state [:account-info :trade-history :page-input] "")
        locale (get-in state [:ui :locale])
        page* (history-shared/normalize-order-history-page raw-value max-page locale)]
    [[:effects/save-many [[[:account-info :trade-history :page] page*]
                          [[:account-info :trade-history :page-input] (str page*)]]]]))

(defn handle-trade-history-page-input-keydown [state key max-page]
  (if (= key "Enter")
    (apply-trade-history-page-input state max-page)
    []))

(defn sort-trade-history [state column]
  (let [current-sort (get-in state
                             [:account-info :trade-history :sort]
                             {:column "Time" :direction :desc})
        current-column (:column current-sort)
        current-direction (:direction current-sort)
        desc-columns #{"Time" "Price" "Size" "Trade Value" "Fee" "Closed PNL"}
        new-direction (if (= current-column column)
                        (if (= current-direction :asc) :desc :asc)
                        (if (contains? desc-columns column)
                          :desc
                          :asc))]
    [[:effects/save-many [[[:account-info :trade-history :sort]
                           {:column column :direction new-direction}]
                          [[:account-info :trade-history :page] 1]
                          [[:account-info :trade-history :page-input] "1"]]]]))

(defn toggle-trade-history-direction-filter-open [state]
  (let [open? (boolean (get-in state [:account-info :trade-history :filter-open?]))]
    [[:effects/save [:account-info :trade-history :filter-open?] (not open?)]]))

(defn set-trade-history-direction-filter [_state direction-filter]
  (let [direction* (normalize-trade-history-direction-filter direction-filter)]
    [[:effects/save-many [[[:account-info :trade-history :direction-filter] direction*]
                          [[:account-info :trade-history :filter-open?] false]
                          [[:account-info :trade-history :page] 1]
                          [[:account-info :trade-history :page-input] "1"]]]]))

(defn sort-order-history [state column]
  (let [current-sort (get-in state
                             [:account-info :order-history :sort]
                             {:column "Time" :direction :desc})
        current-column (:column current-sort)
        current-direction (:direction current-sort)
        desc-columns #{"Time" "Size" "Filled Size" "Order Value" "Price" "Order ID"}
        new-direction (if (= current-column column)
                        (if (= current-direction :asc) :desc :asc)
                        (if (contains? desc-columns column)
                          :desc
                          :asc))]
    [[:effects/save-many [[[:account-info :order-history :sort]
                           {:column column :direction new-direction}]
                          [[:account-info :order-history :page] 1]
                          [[:account-info :order-history :page-input] "1"]]]]))

(defn toggle-order-history-filter-open [state]
  (let [open? (boolean (get-in state [:account-info :order-history :filter-open?]))]
    [[:effects/save [:account-info :order-history :filter-open?] (not open?)]]))

(defn set-order-history-status-filter [_state status-filter]
  (let [status* (normalize-order-history-status-filter status-filter)]
    [[:effects/save-many [[[:account-info :order-history :status-filter] status*]
                          [[:account-info :order-history :filter-open?] false]
                          [[:account-info :order-history :page] 1]
                          [[:account-info :order-history :page-input] "1"]]]]))

(defn set-order-history-page-size [state page-size]
  (let [locale (get-in state [:ui :locale])
        page-size* (history-shared/normalize-order-history-page-size page-size locale)]
    [[:effects/save-many [[[:account-info :order-history :page-size] page-size*]
                          [[:account-info :order-history :page] 1]
                          [[:account-info :order-history :page-input] "1"]]]
     [:effects/local-storage-set "order-history-page-size" (str page-size*)]]))

(defn set-order-history-page [state page max-page]
  (let [locale (get-in state [:ui :locale])
        page* (history-shared/normalize-order-history-page page max-page locale)]
    [[:effects/save-many [[[:account-info :order-history :page] page*]
                          [[:account-info :order-history :page-input] (str page*)]]]]))

(defn next-order-history-page [state max-page]
  (let [current-page (get-in state [:account-info :order-history :page] 1)]
    (set-order-history-page state (inc current-page) max-page)))

(defn prev-order-history-page [state max-page]
  (let [current-page (get-in state [:account-info :order-history :page] 1)]
    (set-order-history-page state (dec current-page) max-page)))

(defn set-order-history-page-input [_state input-value]
  [[:effects/save [:account-info :order-history :page-input]
    (if (string? input-value)
      input-value
      (str (or input-value "")))]] )

(defn apply-order-history-page-input [state max-page]
  (let [raw-value (get-in state [:account-info :order-history :page-input] "")
        locale (get-in state [:ui :locale])
        page* (history-shared/normalize-order-history-page raw-value max-page locale)]
    [[:effects/save-many [[[:account-info :order-history :page] page*]
                          [[:account-info :order-history :page-input] (str page*)]]]]))

(defn handle-order-history-page-input-keydown [state key max-page]
  (if (= key "Enter")
    (apply-order-history-page-input state max-page)
    []))

(defn refresh-order-history [state]
  (let [request-id (inc (order-history-request-id state))
        selected? (= :order-history (get-in state [:account-info :selected-tab]))]
    (cond-> [[:effects/save-many [[[:account-info :order-history :request-id] request-id]
                                  [[:account-info :order-history :loading?] selected?]
                                  [[:account-info :order-history :error] nil]]]]
      selected?
      (conj [:effects/api-fetch-historical-orders request-id]))))
