(ns hyperopen.workbench.support.state
  (:require [hyperopen.state.app-defaults :as app-defaults]
            [hyperopen.state.trading :as trading]))

(defn deep-merge
  [& maps]
  (apply merge-with
         (fn [left right]
           (if (and (map? left) (map? right))
             (deep-merge left right)
             right))
         maps))

(defn create-store
  [scene-id state]
  (atom state :meta {:hyperopen.workbench/scene-id scene-id}))

(defn base-state
  []
  {:ui (app-defaults/default-ui-state)
   :router {:path "/trade"}
   :wallet {:connected? true
            :address "0x4b20993bc481177ec7e8f571cecae8a9e22c02db"
            :connecting? false
            :agent {:status :ready
                    :enabled? true
                    :storage-mode :local}}
   :header-ui (app-defaults/default-header-ui-state)
   :asset-selector (app-defaults/default-asset-selector-state)
   :chart-options (app-defaults/default-chart-options-state)
   :orderbook-ui (app-defaults/default-orderbook-ui-state)
   :orders (app-defaults/default-orders-state)
   :websocket (app-defaults/default-websocket-state nil)
   :websocket-ui (app-defaults/default-websocket-ui-state)
   :vaults-ui (app-defaults/default-vaults-ui-state)
   :vaults (app-defaults/default-vaults-state)
   :order-form (trading/default-order-form)
   :order-form-ui (trading/default-order-form-ui)
   :order-form-runtime (trading/default-order-form-runtime)})

(defn build-state
  [& xs]
  (apply deep-merge (base-state) xs))

(defn toggle-in
  [state path]
  (update-in state path not))

(defn set-in
  [state path value]
  (assoc-in state path value))

(defn update-sort-state
  [sort-state column]
  (let [active? (= column (:column sort-state))]
    {:column column
     :direction (if active?
                  (if (= :asc (:direction sort-state))
                    :desc
                    :asc)
                  :desc)}))

(defn update-sort-in
  [state path column]
  (update-in state path #(update-sort-state (or % {:direction :desc}) column)))
