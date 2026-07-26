(ns hyperopen.workbench.scenes.account.overlays-scenes
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.fixtures :as fixtures]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.views.account-info.position-margin-modal :as position-margin-modal]
            [hyperopen.views.account-info.position-reduce-popover :as position-reduce-popover]
            [hyperopen.views.account-info.position-tpsl-modal :as position-tpsl-modal]))

(portfolio/configure-scenes
  {:title "Overlays"
   :collection :account})

(portfolio/defscene position-tpsl-modal
  []
  (layout/page-shell
   (position-tpsl-modal/position-tpsl-modal-view (fixtures/position-tpsl-modal))))

(portfolio/defscene position-reduce-popover
  []
  (layout/page-shell
   (layout/desktop-shell
    (layout/panel-shell {:class ["h-[720px]" "relative"]}
     (position-reduce-popover/position-reduce-popover-view (fixtures/position-reduce-popover))))))

(portfolio/defscene position-margin-modal
  []
  (layout/page-shell
   (layout/desktop-shell
    (layout/panel-shell {:class ["h-[720px]" "relative"]}
     (position-margin-modal/position-margin-modal-view (fixtures/position-margin-modal))))))
