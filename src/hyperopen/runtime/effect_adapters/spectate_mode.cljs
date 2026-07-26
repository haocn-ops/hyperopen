(ns hyperopen.runtime.effect-adapters.spectate-mode
  "Browser-side effects for spectate-mode watchlist import and export: download a
  JSON file, open the file picker and parse a chosen file, and surface a
  success/error message in the modal feedback toast. The pure import/export
  decisions live in `hyperopen.account.spectate-watchlist-io`; only DOM and
  dispatch side effects live here."
  (:require [nexus.registry :as nxr]
            [hyperopen.platform :as platform]
            [hyperopen.runtime.state :as runtime-state]
            [hyperopen.wallet.copy-feedback-runtime :as copy-feedback-runtime]))

(defn- show-feedback!
  [store kind message]
  (let [runtime runtime-state/runtime]
    (copy-feedback-runtime/clear-wallet-copy-feedback-timeout-in-runtime!
     runtime
     platform/clear-timeout!)
    (copy-feedback-runtime/set-wallet-copy-feedback! store kind message)
    (copy-feedback-runtime/schedule-wallet-copy-feedback-clear!
     {:store store
      :runtime runtime
      :clear-wallet-copy-feedback! copy-feedback-runtime/clear-wallet-copy-feedback!
      :clear-wallet-copy-feedback-timeout!
      #(copy-feedback-runtime/clear-wallet-copy-feedback-timeout-in-runtime!
        runtime
        platform/clear-timeout!)
      :wallet-copy-feedback-duration-ms runtime-state/wallet-copy-feedback-duration-ms
      :set-timeout-fn platform/set-timeout!})))

(defn spectate-watchlist-feedback
  [_ store kind message]
  (show-feedback! store kind message))

(defn download-spectate-watchlist-file
  [_ _ {:keys [filename] doc :document}]
  (when (and (exists? js/document)
             (exists? js/URL))
    (let [json (js/JSON.stringify (clj->js doc) nil 2)
          blob (js/Blob. #js [json] #js {:type "application/json;charset=utf-8"})
          url (.createObjectURL js/URL blob)
          link (.createElement js/document "a")]
      (set! (.-href link) url)
      (set! (.-download link) filename)
      (.appendChild (.-body js/document) link)
      (.click link)
      (.removeChild (.-body js/document) link)
      (.revokeObjectURL js/URL url))))

(defn- dispatch-apply!
  [store data]
  (nxr/dispatch store nil [[:actions/apply-imported-spectate-watchlist data]]))

(defn- read-file-as-json!
  [file on-data]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [_]
            (let [text (.-result reader)
                  data (try
                         (js->clj (js/JSON.parse text))
                         (catch :default _ ::invalid))]
              (on-data (when (not= data ::invalid) data)))))
    (set! (.-onerror reader)
          (fn [_] (on-data nil)))
    (.readAsText reader file)))

(defn pick-spectate-watchlist-file
  [_ store]
  (when (exists? js/document)
    (let [input (.createElement js/document "input")]
      (set! (.-type input) "file")
      (set! (.-accept input) ".json,application/json")
      (set! (.-onchange input)
            (fn [_]
              (when-let [file (some-> input .-files (aget 0))]
                (read-file-as-json! file #(dispatch-apply! store %)))))
      (.click input))))
