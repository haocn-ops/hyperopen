(ns hyperopen.views.portfolio.optimize.return-views-io-panel-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.views.portfolio.optimize.return-views-panel :as panel]
            [hyperopen.views.portfolio.optimize.test-support :as support]))

(def ^:private now-ms 1751400000000)

(def ^:private container-role
  "portfolio-optimizer-objective-menu-use-my-views-editor")

(defn- bl-draft
  [universe]
  {:universe universe
   :objective {:kind :max-sharpe}
   :return-model {:kind :black-litterman :views []}})

(defn- render
  [{:keys [draft state]}]
  (panel/return-views-panel
   {:draft draft
    :state (or state {})
    :readiness {}
    :now-ms now-ms}))

(deftest panel-renders-export-import-toolbar-test
  (let [view-node (render {:draft (bl-draft [{:instrument-id "perp:BTC" :coin "BTC"}])})
        toolbar (support/node-by-role view-node (str container-role "-io"))
        export-button (support/node-by-role toolbar (str container-role "-export"))
        import-button (support/node-by-role toolbar (str container-role "-import"))]
    (is (some? toolbar))
    (is (= [[:actions/export-portfolio-optimizer-return-views]]
           (support/click-actions export-button)))
    (is (= [[:actions/import-portfolio-optimizer-return-views]]
           (support/click-actions import-button)))
    (is (not (support/node-attr export-button :disabled)))))

(deftest panel-disables-export-on-empty-universe-test
  (let [view-node (render {:draft (bl-draft [])})
        export-button (support/node-by-role view-node (str container-role "-export"))]
    (is (some? export-button))
    (is (true? (support/node-attr export-button :disabled)))
    (is (nil? (support/click-actions export-button)))))

(deftest panel-hides-toolbar-when-views-are-off-test
  (let [view-node (render {:draft {:universe [{:instrument-id "perp:BTC"}]
                                   :objective {:kind :max-sharpe}
                                   :return-model {:kind :historical-mean}}})]
    (is (nil? (support/node-by-role view-node (str container-role "-io"))))))

(deftest panel-renders-io-note-with-dismiss-test
  (let [state (assoc-in {} contracts/ui-return-views-io-note-path
                        {:kind :error :message "Import failed: the file is not valid return-views JSON."})
        view-node (render {:draft (bl-draft [{:instrument-id "perp:BTC"}])
                           :state state})
        note (support/node-by-role view-node (str container-role "-io-note"))
        dismiss (support/node-by-role note (str container-role "-io-note-dismiss"))]
    (is (some? note))
    (is (= "error" (support/node-attr note :data-kind)))
    (is (some #(= "Import failed: the file is not valid return-views JSON." %)
              (support/collect-strings note)))
    (is (= [[:actions/dismiss-portfolio-optimizer-return-views-io-note]]
           (support/click-actions dismiss)))))

(deftest panel-omits-io-note-when-absent-test
  (let [view-node (render {:draft (bl-draft [{:instrument-id "perp:BTC"}])})]
    (is (nil? (support/node-by-role view-node (str container-role "-io-note"))))))
