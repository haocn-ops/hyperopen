(ns hyperopen.workbench.runner
  (:require [portfolio.runner]
            [portfolio.ui :as portfolio.ui]
            [portfolio.ui.search.memsearch-index :as memsearch-index]
            [hyperopen.workbench.collections :as collections]
            [hyperopen.workbench.support.dispatch :as dispatch]))

(defn ^:export start
  []
  (collections/register!)
  (dispatch/install-global-dispatch!)
  (portfolio.ui/start!
   {:config (portfolio.runner/get-compiler-portfolio-config)
    :index (memsearch-index/create-index)}))

(start)
