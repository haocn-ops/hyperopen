(ns hyperopen.runtime.effect-adapters.portfolio-optimizer-io
  "File-transfer effect wrappers for the optimizer's history-assumptions agent
  IO (JSON template download + completed-file pick). Split from the main
  optimizer effect-adapter facade, which sits at its namespace-size cap; the
  mechanics mirror the return-views / spectate-watchlist adapters. The pick
  path strips markdown code fences before parsing — agents fence JSON no
  matter what the embedded instructions say."
  (:require [nexus.registry :as nxr]
            [hyperopen.portfolio.optimizer.application.history-assumptions-io :as history-assumptions-io]))

(defn download-portfolio-optimizer-history-assumptions-file-effect
  "Download the history-assumptions export document as a JSON file (Blob +
  anchor, mirroring the return-views download)."
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

(defn- read-history-assumptions-file-as-json!
  [file on-data]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [_]
            (let [text (history-assumptions-io/strip-code-fences (.-result reader))
                  data (try
                         (js->clj (js/JSON.parse text))
                         (catch :default _ ::invalid))]
              (on-data (when (not= data ::invalid) data)))))
    (set! (.-onerror reader)
          (fn [_] (on-data nil)))
    (.readAsText reader file)))

(defn pick-portfolio-optimizer-history-assumptions-file-effect
  "Open a file picker for a completed history-assumptions JSON file and hand
  the parsed data to the apply action (nil data → the action reports an
  invalid-file note)."
  [_ store]
  (when (exists? js/document)
    (let [input (.createElement js/document "input")]
      (set! (.-type input) "file")
      (set! (.-accept input) ".json,application/json")
      (set! (.-onchange input)
            (fn [_]
              (when-let [file (some-> input .-files (aget 0))]
                (read-history-assumptions-file-as-json!
                 file
                 (fn [data]
                   (nxr/dispatch store nil
                                 [[:actions/apply-imported-portfolio-optimizer-history-assumptions
                                   data]]))))))
      (.click input))))
