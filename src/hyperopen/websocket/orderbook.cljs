(ns hyperopen.websocket.orderbook
  (:require [hyperopen.telemetry :as telemetry]
            [hyperopen.trading.order-form-context-sync :as order-form-context-sync]
            [hyperopen.websocket.client :as ws-client]
            [hyperopen.websocket.market-projection-runtime :as market-projection-runtime]
            [hyperopen.websocket.orderbook-policy :as policy]))

(defn- send-subscribe! [subscription]
  (ws-client/send-message! {:method "subscribe"
                            :subscription subscription}))

(defn- send-unsubscribe! [subscription]
  (ws-client/send-message! {:method "unsubscribe"
                            :subscription subscription}))

;; Order book state
(defonce orderbook-state (atom {:subscriptions {}
                                :books {}})) ; coin -> subscription map, and coin -> book data

;; Subscribe to order book for a symbol with optional aggregation config
(defn subscribe-orderbook!
  ([symbol] (subscribe-orderbook! symbol nil))
  ([symbol aggregation-config]
   (when symbol
     (let [desired-subscription (policy/build-subscription symbol aggregation-config)
           current-subscription (get-in @orderbook-state [:subscriptions symbol])]
       (if (= current-subscription desired-subscription)
         (telemetry/log! "Order book subscription unchanged for:" symbol desired-subscription)
         (do
           (when current-subscription
             (send-unsubscribe! current-subscription))
           (send-subscribe! desired-subscription)
           (swap! orderbook-state assoc-in [:subscriptions symbol] desired-subscription)
           (telemetry/log! "Subscribed to order book for:" symbol desired-subscription)))))))

;; Unsubscribe from order book for a symbol
(defn unsubscribe-orderbook! [symbol]
  (let [subscription (or (get-in @orderbook-state [:subscriptions symbol])
                         (policy/build-subscription symbol nil))]
    (when symbol
      (send-unsubscribe! subscription)
      (telemetry/log! "Unsubscribed from order book for:" symbol))
    (swap! orderbook-state
           (fn [state]
             (-> state
                 (update :subscriptions dissoc symbol)
                 (update :books dissoc symbol))))))

;; Create a handler function that has access to the store
(defn create-orderbook-data-handler [store]
  (fn [data]
    (when (and (map? data) (= (:channel data) "l2Book"))
      (let [book-data (:data data)
            coin (:coin book-data)
            levels (:levels book-data)]
        (when (and coin levels (>= (count levels) 2))
          (let [bids (first levels)
                asks (second levels)
                next-book (assoc (policy/build-book bids asks)
                                 :timestamp (:time book-data))
                previous-book (get-in @orderbook-state [:books coin])
                render-changed? (not (policy/same-render-book? previous-book next-book))]
            ;; Update local state
            (swap! orderbook-state assoc-in [:books coin] next-book)
            ;; Keep duplicate visual snapshots out of the app store so the
            ;; trade route does not rerender on timestamp-only book refreshes.
            (when (and store render-changed?)
              (market-projection-runtime/queue-market-projection!
               {:store store
                :coalesce-key [:orderbook coin]
                :apply-update-fn (fn [state]
                                   (let [next-state (assoc-in state [:orderbooks coin] next-book)]
                                     ;; Re-project the active order form against the new book so
                                     ;; the committed size the user sees stays coherent with the
                                     ;; live best-ask that affordability validation uses (avoids
                                     ;; false "Not enough USDC" rejects when the ask moves up).
                                     (if (= coin (:active-asset next-state))
                                       (order-form-context-sync/reconcile-active-order-form next-state)
                                       next-state)))}))))))))

;; Get current subscriptions
(defn get-subscriptions []
  (:subscriptions @orderbook-state))

;; Get order book for a specific symbol
(defn get-orderbook [symbol]
  (get-in @orderbook-state [:books symbol]))

;; Get all order books
(defn get-all-orderbooks []
  (:books @orderbook-state))

;; Get best bid and ask for a symbol
(defn get-best-bid-ask [symbol]
  (when-let [book (get-orderbook symbol)]
    {:best-bid (or (get-in book [:render :best-bid])
                   (first (:bids book)))
     :best-ask (or (get-in book [:render :best-ask])
                   (first (:asks book)))}))

;; Clear order book data for a specific symbol
(defn clear-orderbook! [symbol]
  (swap! orderbook-state update :books dissoc symbol))

;; Clear all order book data
(defn clear-all-orderbooks! []
  (swap! orderbook-state assoc :books {}))

;; Initialize order book module
(defn init! [store]
  (telemetry/log! "Order book subscription module initialized")
  ;; Register handler for l2Book channel with store access
  (ws-client/register-handler! "l2Book" (create-orderbook-data-handler store)))
