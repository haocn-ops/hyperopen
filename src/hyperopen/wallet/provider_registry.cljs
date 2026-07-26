(ns hyperopen.wallet.provider-registry
  (:require [clojure.string :as str]))

(declare sync-provider-list!)

(defonce ^:private provider-override
  (atom nil))

(defonce ^:private selected-provider-id
  (atom nil))

(defonce ^:private eip6963-providers
  (atom {}))

(defonce ^:private discovery-state
  (atom {:installed? false
         :store nil
         :handler nil}))

(defn- js-get
  [obj prop]
  (when (some? obj)
    (aget obj prop)))

(defn- provider-like?
  [provider]
  (fn? (js-get provider "request")))

(defn- window-object
  []
  (js-get js/globalThis "window"))

(defn- singleton-provider
  []
  (or (some-> (window-object) (js-get "ethereum"))
      (js-get js/globalThis "ethereum")))

(defn- truthy-flag?
  [provider prop]
  (true? (js-get provider prop)))

(defn- lower-text
  [value]
  (some-> value str str/lower-case))

(defn- infer-provider-kind
  [provider]
  (cond
    (truthy-flag? provider "isCoinbaseWallet") :coinbase
    (truthy-flag? provider "isCoinbaseBrowser") :coinbase
    (truthy-flag? provider "isMetaMask") :metamask
    (truthy-flag? provider "isRabby") :rabby
    (truthy-flag? provider "isBraveWallet") :brave
    (truthy-flag? provider "isTrust") :trust
    :else :browser))

(defn- legacy-base-id
  [provider]
  (case (infer-provider-kind provider)
    :coinbase "legacy:coinbase"
    :metamask "legacy:metamask"
    :rabby "legacy:rabby"
    :brave "legacy:brave"
    :trust "legacy:trust"
    "legacy:browser"))

(defn- legacy-name
  [provider]
  (or (some-> (js-get provider "name") str not-empty)
      (case (infer-provider-kind provider)
        :coinbase "Coinbase Wallet"
        :metamask "MetaMask"
        :rabby "Rabby"
        :brave "Brave Wallet"
        :trust "Trust Wallet"
        "Browser Wallet")))

(defn- legacy-rdns
  [provider]
  (case (infer-provider-kind provider)
    :coinbase "com.coinbase.wallet"
    :metamask "io.metamask"
    :rabby "io.rabby"
    :brave "com.brave.wallet"
    :trust "com.trustwallet"
    nil))

(defn- unique-id
  [base counts]
  (let [seen-count (get counts base 0)
        next-counts (update counts base (fnil inc 0))]
    [(if (zero? seen-count)
       base
       (str base "-" (inc seen-count)))
     next-counts]))

(defn- append-provider
  [providers provider]
  (if (or (nil? provider)
          (some #(identical? provider %) providers))
    providers
    (conj providers provider)))

(defn- legacy-provider-candidates
  []
  (let [singleton (singleton-provider)
        providers-value (js-get singleton "providers")
        provider-list (if (array? providers-value)
                        (vec (array-seq providers-value))
                        [])]
    (if (seq provider-list)
      (append-provider provider-list singleton)
      (cond-> []
        (some? singleton) (conj singleton)))))

(defn- legacy-provider-records
  []
  (loop [remaining (legacy-provider-candidates)
         counts {}
         records []]
    (if-let [provider (first remaining)]
      (if-not (provider-like? provider)
        (recur (rest remaining) counts records)
        (let [[id next-counts] (unique-id (legacy-base-id provider) counts)]
          (recur (rest remaining)
                 next-counts
                 (conj records {:id id
                                :name (legacy-name provider)
                                :rdns (legacy-rdns provider)
                                :source :legacy
                                :provider provider}))))
      records)))

(defn- provider-detail-info
  [detail]
  (let [info (js-get detail "info")]
    {:uuid (some-> (js-get info "uuid") str not-empty)
     :name (some-> (js-get info "name") str not-empty)
     :rdns (some-> (js-get info "rdns") str not-empty)
     :icon (some-> (js-get info "icon") str not-empty)}))

(defn- eip6963-id
  [{:keys [uuid rdns name]}]
  (str "eip6963:"
       (or uuid
           rdns
           (some-> name lower-text (str/replace #"\s+" "-"))
           "unknown")))

(defn- eip6963-record-from-detail
  [detail]
  (let [provider (js-get detail "provider")
        {:keys [name rdns] :as info} (provider-detail-info detail)]
    (when (provider-like? provider)
      (assoc info
             :id (eip6963-id info)
             :name (or name "Browser Wallet")
             :rdns rdns
             :source :eip6963
             :provider provider))))

(defn- provider-already-seen?
  [records provider]
  (some #(identical? provider (:provider %)) records))

(defn provider-records
  []
  (if-let [override @provider-override]
    [{:id "override"
      :name "Wallet Simulator"
      :rdns nil
      :source :override
      :provider override}]
    (let [announced (vec (vals @eip6963-providers))]
      (reduce
       (fn [records record]
         (if (provider-already-seen? records (:provider record))
           records
           (conj records record)))
       announced
       (legacy-provider-records)))))

(defn provider-metadata
  []
  (->> (provider-records)
       (mapv #(select-keys % [:id :name :rdns :source]))))

(defn select-provider!
  [provider-id]
  (let [records (provider-records)
        record (if (seq provider-id)
                 (some #(when (= provider-id (:id %)) %) records)
                 (or (some #(when (= @selected-provider-id (:id %)) %) records)
                     (first records)))]
    (reset! selected-provider-id (:id record))
    record))

(defn selected-provider-id-value
  []
  @selected-provider-id)

(defn provider
  []
  (some-> (select-provider! nil) :provider))

(defn set-provider-override!
  [provider]
  (reset! provider-override provider)
  (reset! selected-provider-id (when provider "override"))
  provider)

(defn clear-provider-override!
  []
  (reset! provider-override nil)
  (when (= "override" @selected-provider-id)
    (reset! selected-provider-id nil))
  true)

(defn sync-provider-list!
  [store]
  (when (some? store)
    (let [selected-id @selected-provider-id]
      (swap! store update-in [:wallet]
             (fn [wallet-state]
               (merge wallet-state
                      {:providers (provider-metadata)
                       :selected-provider-id selected-id})))))
  true)

(defn- handle-eip6963-announce!
  [event]
  (when-let [record (eip6963-record-from-detail (js-get event "detail"))]
    (swap! eip6963-providers assoc (:id record) record)
    (when-let [store (:store @discovery-state)]
      (sync-provider-list! store))))

(defn- request-eip6963-providers!
  [window*]
  (when (and window*
             (fn? (js-get window* "dispatchEvent"))
             (exists? js/Event))
    (try
      (.dispatchEvent window* (js/Event. "eip6963:requestProvider"))
      (catch :default _
        nil))))

(defn install-discovery!
  ([] (install-discovery! nil))
  ([store]
   (let [window* (window-object)]
     (swap! discovery-state assoc :store store)
     (when (and window*
                (fn? (js-get window* "addEventListener"))
                (not (:installed? @discovery-state)))
       (let [handler handle-eip6963-announce!]
         (.addEventListener window* "eip6963:announceProvider" handler)
         (swap! discovery-state assoc
                :installed? true
                :handler handler)))
     (request-eip6963-providers! window*)
     (sync-provider-list! store)
     true)))

(defn reset-provider-registry!
  []
  (let [{:keys [handler]} @discovery-state
        window* (window-object)]
    (when (and window*
               handler
               (fn? (js-get window* "removeEventListener")))
      (try
        (.removeEventListener window* "eip6963:announceProvider" handler)
        (catch :default _
          nil))))
  (reset! provider-override nil)
  (reset! selected-provider-id nil)
  (reset! eip6963-providers {})
  (reset! discovery-state {:installed? false
                           :store nil
                           :handler nil})
  true)
