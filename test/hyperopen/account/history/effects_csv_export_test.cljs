(ns hyperopen.account.history.effects-csv-export-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is]]
            [goog.object :as gobj]
            [hyperopen.account.history.effects :as history-effects]))

(defn- has-own?
  [obj key]
  (.call (.-hasOwnProperty js/Object.prototype) obj key))

(deftest export-funding-history-csv-effect-builds-and-downloads-csv-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        orig-url (.-URL js/globalThis)
        had-url? (has-own? js/globalThis "URL")
        orig-blob (.-Blob js/globalThis)
        had-blob? (has-own? js/globalThis "Blob")
        csv-text (atom nil)
        append-count (atom 0)
        remove-count (atom 0)
        click-count (atom 0)
        revoked-url (atom nil)
        link (js-obj)]
    (try
      (set! (.-click link) (fn [] (swap! click-count inc)))
      (set! (.-Blob js/globalThis)
            (fn [parts _opts]
              (js-obj "parts" parts)))
      (set! (.-URL js/globalThis)
            (js-obj
             "createObjectURL" (fn [blob]
                                 (reset! csv-text (aget (gobj/get blob "parts") 0))
                                 "blob://funding-history")
             "revokeObjectURL" (fn [url]
                                 (reset! revoked-url url))))
      (set! (.-document js/globalThis)
            (js-obj
             "createElement" (fn [_tag] link)
             "body" (js-obj
                     "appendChild" (fn [el]
                                     (is (identical? link el))
                                     (swap! append-count inc))
                     "removeChild" (fn [el]
                                     (is (identical? link el))
                                     (swap! remove-count inc)))))
      (history-effects/export-funding-history-csv-effect
       nil nil
       [{:time-ms 1700000000000
         :coin "BTC, \"perp\"\nX"
         :size-raw 1234.5
         :position-side :mystery
         :payment-usdc-raw 1.23456
         :funding-rate-raw 0.00012}])
      (is (= 1 @append-count))
      (is (= 1 @remove-count))
      (is (= 1 @click-count))
      (is (= "blob://funding-history" @revoked-url))
      (is (string? @csv-text))
      (is (str/includes? @csv-text "Time,Coin,Size,Position Side,Payment,Rate"))
      (is (str/includes? @csv-text "\"BTC, \"\"perp\"\"\nX\""))
      (is (str/includes? @csv-text ",Flat,"))
      (is (str/includes? @csv-text "USDC"))
      (is (str/includes? @csv-text "%"))
      (is (str/starts-with? (.-download link) "funding-history-"))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))
        (if had-url?
          (set! (.-URL js/globalThis) orig-url)
          (js-delete js/globalThis "URL"))
        (if had-blob?
          (set! (.-Blob js/globalThis) orig-blob)
          (js-delete js/globalThis "Blob"))))))

(deftest export-funding-history-csv-effect-without-document-noops-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")]
    (try
      (js-delete js/globalThis "document")
      (is (nil? (history-effects/export-funding-history-csv-effect nil nil [])))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest export-funding-history-csv-effect-covers-side-labels-and-fallback-formatting-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        orig-url (.-URL js/globalThis)
        had-url? (has-own? js/globalThis "URL")
        orig-blob (.-Blob js/globalThis)
        had-blob? (has-own? js/globalThis "Blob")
        csv-text (atom nil)]
    (try
      (set! (.-Blob js/globalThis)
            (fn [parts _opts]
              (js-obj "parts" parts)))
      (set! (.-URL js/globalThis)
            (js-obj
             "createObjectURL" (fn [blob]
                                 (reset! csv-text (aget (gobj/get blob "parts") 0))
                                 "blob://funding-history-extra")
             "revokeObjectURL" (fn [_] nil)))
      (set! (.-document js/globalThis)
            (js-obj
             "createElement" (fn [_tag]
                               (js-obj "click" (fn [] nil)))
             "body" (js-obj
                     "appendChild" (fn [_] nil)
                     "removeChild" (fn [_] nil))))
      (history-effects/export-funding-history-csv-effect
       nil nil
       [{:time-ms 1700000000000
         :coin nil
         :size-raw "oops"
         :position-side :long
         :payment-usdc-raw "oops"
         :funding-rate-raw "oops"}
        {:time-ms 1700000001000
         :coin "ETH"
         :size-raw 1.25
         :position-side :short
         :payment-usdc-raw 0.45
         :funding-rate-raw 0.00034}])
      (is (str/includes? @csv-text ",Long,"))
      (is (str/includes? @csv-text ",Short,"))
      (is (str/includes? @csv-text ",,0.000 -,Long,0.0000 USDC,0.0000%"))
      (is (str/includes? @csv-text ",ETH,1.250 ETH,Short,0.4500 USDC,0.0340%"))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))
        (if had-url?
          (set! (.-URL js/globalThis) orig-url)
          (js-delete js/globalThis "URL"))
        (if had-blob?
          (set! (.-Blob js/globalThis) orig-blob)
          (js-delete js/globalThis "Blob"))))))

(deftest export-funding-history-csv-effect-without-url-noops-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        orig-url (.-URL js/globalThis)
        had-url? (has-own? js/globalThis "URL")
        create-count (atom 0)]
    (try
      (set! (.-document js/globalThis)
            (js-obj
             "createElement" (fn [_tag]
                               (swap! create-count inc)
                               (js-obj "click" (fn [] nil)))
             "body" (js-obj
                     "appendChild" (fn [_] nil)
                     "removeChild" (fn [_] nil))))
      (js-delete js/globalThis "URL")
      (is (nil? (history-effects/export-funding-history-csv-effect nil nil [])))
      (is (= 0 @create-count))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))
        (if had-url?
          (set! (.-URL js/globalThis) orig-url)
          (js-delete js/globalThis "URL"))))))
