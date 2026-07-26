(ns hyperopen.account.history.funding-actions
  (:require [clojure.string :as str]
            [hyperopen.account.history.shared :as history-shared]
            [hyperopen.domain.funding-history :as funding-history]
            [hyperopen.platform :as platform]))

(defn- default-funding-history-filters []
  (let [now (platform/now-ms)]
    (funding-history/normalize-funding-history-filters
     {:start-time-ms 0
      :end-time-ms now}
     now)))

(defn default-funding-history-state []
  (let [filters (default-funding-history-filters)]
    {:filters filters
     :draft-filters filters
     :sort {:column "Time" :direction :desc}
     :filter-open? false
     :coin-search ""
     :coin-suggestions-open? false
     :page-size history-shared/default-order-history-page-size
     :page 1
     :page-input "1"
     :loading? false
     :error nil
     :request-id 0}))

(defn restore-funding-history-pagination-settings! [store]
  (let [page-size (history-shared/normalize-order-history-page-size
                   (platform/local-storage-get "funding-history-page-size"))]
    (swap! store update-in [:account-info :funding-history] merge
           {:page-size page-size
            :page 1
            :page-input "1"})))

(defn- parse-datetime-local-ms
  [value]
  (let [text (str/trim (str (or value "")))]
    (when (seq text)
      (let [parsed (.parse js/Date text)]
        (when (and (number? parsed)
                   (not (js/isNaN parsed)))
          (js/Math.floor parsed))))))

(defn funding-history-filters
  [state]
  (funding-history/normalize-funding-history-filters
   (get-in state [:account-info :funding-history :filters])
   (platform/now-ms)))

(defn- funding-history-draft-filters
  [state]
  (funding-history/normalize-funding-history-filters
   (or (get-in state [:account-info :funding-history :draft-filters])
       (funding-history-filters state))
   (platform/now-ms)))

(defn funding-history-request-id
  [state]
  (get-in state [:account-info :funding-history :request-id] 0))

(defn- filtered-funding-rows
  [state filters]
  (funding-history/filter-funding-history-rows
   (get-in state [:orders :fundings-raw] [])
   filters))

(defn select-funding-history-tab [state]
  (let [filters (funding-history-filters state)
        request-id (inc (funding-history-request-id state))
        projected (filtered-funding-rows state filters)]
    [[:effects/save-many [[[:account-info :selected-tab] :funding-history]
                          [[:account-info :funding-history :filters] filters]
                          [[:account-info :funding-history :draft-filters] filters]
                          [[:account-info :funding-history :coin-search] ""]
                          [[:account-info :funding-history :coin-suggestions-open?] false]
                          [[:account-info :funding-history :loading?] true]
                          [[:account-info :funding-history :error] nil]
                          [[:account-info :funding-history :request-id] request-id]
                          [[:orders :fundings] projected]]]
     [:effects/api-fetch-user-funding-history request-id]]))

(defn set-funding-history-filters [_state path value]
  (let [path* (if (vector? path) path [path])
        full-path (into [:account-info :funding-history] path*)
        value* (case path*
                 [:draft-filters :start-time-ms] (parse-datetime-local-ms value)
                 [:draft-filters :end-time-ms] (parse-datetime-local-ms value)
                 [:filters :start-time-ms] (parse-datetime-local-ms value)
                 [:filters :end-time-ms] (parse-datetime-local-ms value)
                 [:coin-search] (history-shared/normalize-coin-search-value value)
                 [:coin-suggestions-open?] (boolean value)
                 value)]
    [[:effects/save full-path value*]]))

(defn toggle-funding-history-filter-open [state]
  (let [open? (boolean (get-in state [:account-info :funding-history :filter-open?]))
        filters (funding-history-filters state)
        draft-filters (if open?
                        (funding-history-draft-filters state)
                        filters)]
    [[:effects/save-many [[[:account-info :funding-history :filter-open?] (not open?)]
                          [[:account-info :funding-history :draft-filters] draft-filters]
                          [[:account-info :funding-history :coin-search] ""]
                          [[:account-info :funding-history :coin-suggestions-open?] false]]]]))

(defn- normalize-funding-history-filter-coin
  [coin]
  (let [coin* (some-> coin str str/trim)]
    (when (seq coin*)
      coin*)))

(defn toggle-funding-history-filter-coin [state coin]
  (if-let [coin* (normalize-funding-history-filter-coin coin)]
    (let [draft-filters (funding-history-draft-filters state)
          current-set (or (:coin-set draft-filters) #{})
          next-set (if (contains? current-set coin*)
                     (disj current-set coin*)
                     (conj current-set coin*))]
      [[:effects/save [:account-info :funding-history :draft-filters]
        (assoc draft-filters :coin-set next-set)]])
    []))

(defn add-funding-history-filter-coin [state coin]
  (if-let [coin* (normalize-funding-history-filter-coin coin)]
    (let [draft-filters (funding-history-draft-filters state)
          current-set (or (:coin-set draft-filters) #{})
          next-set (conj current-set coin*)]
      [[:effects/save-many [[[:account-info :funding-history :draft-filters]
                             (assoc draft-filters :coin-set next-set)]
                            [[:account-info :funding-history :coin-search] ""]
                            [[:account-info :funding-history :coin-suggestions-open?] true]]]])
    []))

(defn handle-funding-history-coin-search-keydown [state key top-coin]
  (cond
    (= key "Enter")
    (add-funding-history-filter-coin state top-coin)

    (= key "Escape")
    [[:effects/save [:account-info :funding-history :coin-suggestions-open?] false]]

    :else
    []))

(defn reset-funding-history-filter-draft [state]
  (let [filters (funding-history-filters state)]
    [[:effects/save-many [[[:account-info :funding-history :draft-filters] filters]
                          [[:account-info :funding-history :filter-open?] false]
                          [[:account-info :funding-history :coin-search] ""]
                          [[:account-info :funding-history :coin-suggestions-open?] false]]]]))

(defn apply-funding-history-filters [state]
  (let [current-filters (funding-history-filters state)
        draft-filters (funding-history-draft-filters state)
        time-range-changed?
        (not= (select-keys current-filters [:start-time-ms :end-time-ms])
              (select-keys draft-filters [:start-time-ms :end-time-ms]))
        projected (filtered-funding-rows state draft-filters)
        request-id (inc (funding-history-request-id state))
        base-effects [[:effects/save-many [[[:account-info :funding-history :filters] draft-filters]
                                           [[:account-info :funding-history :draft-filters] draft-filters]
                                           [[:account-info :funding-history :filter-open?] false]
                                           [[:account-info :funding-history :coin-search] ""]
                                           [[:account-info :funding-history :coin-suggestions-open?] false]
                                           [[:account-info :funding-history :page] 1]
                                           [[:account-info :funding-history :page-input] "1"]
                                           [[:orders :fundings] projected]]]]]
    (if time-range-changed?
      (into base-effects
            [[:effects/save-many [[[:account-info :funding-history :loading?] true]
                                  [[:account-info :funding-history :error] nil]
                                  [[:account-info :funding-history :request-id] request-id]]]
             [:effects/api-fetch-user-funding-history request-id]])
      base-effects)))

(defn view-all-funding-history [state]
  (let [current-filters (funding-history-filters state)
        next-filters (assoc current-filters
                            :start-time-ms 0
                            :end-time-ms (platform/now-ms))
        projected (filtered-funding-rows state next-filters)
        request-id (inc (funding-history-request-id state))]
    [[:effects/save-many [[[:account-info :funding-history :filters] next-filters]
                          [[:account-info :funding-history :draft-filters] next-filters]
                          [[:account-info :funding-history :filter-open?] false]
                          [[:account-info :funding-history :coin-search] ""]
                          [[:account-info :funding-history :coin-suggestions-open?] false]
                          [[:account-info :funding-history :page] 1]
                          [[:account-info :funding-history :page-input] "1"]
                          [[:account-info :funding-history :loading?] true]
                          [[:account-info :funding-history :error] nil]
                          [[:account-info :funding-history :request-id] request-id]
                          [[:orders :fundings] projected]]]
     [:effects/api-fetch-user-funding-history request-id]]))

(defn export-funding-history-csv [state]
  (let [filters (funding-history-filters state)
        rows (filtered-funding-rows state filters)]
    [[:effects/export-funding-history-csv rows]]))

(defn sort-funding-history [state column]
  (let [current-sort (get-in state
                             [:account-info :funding-history :sort]
                             {:column "Time" :direction :desc})
        current-column (:column current-sort)
        current-direction (:direction current-sort)
        new-direction (if (= current-column column)
                        (if (= current-direction :asc) :desc :asc)
                        (if (contains? #{"Time" "Size" "Payment" "Rate"} column)
                          :desc
                          :asc))]
    [[:effects/save-many [[[:account-info :funding-history :sort]
                           {:column column :direction new-direction}]
                          [[:account-info :funding-history :page] 1]
                          [[:account-info :funding-history :page-input] "1"]]]]))

(defn set-funding-history-page-size [state page-size]
  (let [locale (get-in state [:ui :locale])
        page-size* (history-shared/normalize-order-history-page-size page-size locale)]
    [[:effects/save-many [[[:account-info :funding-history :page-size] page-size*]
                          [[:account-info :funding-history :page] 1]
                          [[:account-info :funding-history :page-input] "1"]]]
     [:effects/local-storage-set "funding-history-page-size" (str page-size*)]]))

(defn set-funding-history-page [state page max-page]
  (let [locale (get-in state [:ui :locale])
        page* (history-shared/normalize-order-history-page page max-page locale)]
    [[:effects/save-many [[[:account-info :funding-history :page] page*]
                          [[:account-info :funding-history :page-input] (str page*)]]]]))

(defn next-funding-history-page [state max-page]
  (let [current-page (get-in state [:account-info :funding-history :page] 1)]
    (set-funding-history-page state (inc current-page) max-page)))

(defn prev-funding-history-page [state max-page]
  (let [current-page (get-in state [:account-info :funding-history :page] 1)]
    (set-funding-history-page state (dec current-page) max-page)))

(defn set-funding-history-page-input [_state input-value]
  [[:effects/save [:account-info :funding-history :page-input]
    (if (string? input-value)
      input-value
      (str (or input-value "")))]] )

(defn apply-funding-history-page-input [state max-page]
  (let [raw-value (get-in state [:account-info :funding-history :page-input] "")
        locale (get-in state [:ui :locale])
        page* (history-shared/normalize-order-history-page raw-value max-page locale)]
    [[:effects/save-many [[[:account-info :funding-history :page] page*]
                          [[:account-info :funding-history :page-input] (str page*)]]]]))

(defn handle-funding-history-page-input-keydown [state key max-page]
  (if (= key "Enter")
    (apply-funding-history-page-input state max-page)
    []))
