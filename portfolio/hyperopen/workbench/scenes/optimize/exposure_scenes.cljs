(ns hyperopen.workbench.scenes.optimize.exposure-scenes
  "Workbench scenes for the 2D exposure-map Positioning control
  (views.portfolio.optimize.setup-exposure-map). Each scene builds the exposure-map view-model
  from a seeded constraint map + current exposure so every state — default (Balanced), a seeded
  gross floor, a long-bias band, an off-policy current dot, and a saved-default profile — can be
  eyeballed in isolation inside the .portfolio-optimizer scope."
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.portfolio.optimizer.application.view-model.exposure :as exposure-vm]
            [hyperopen.views.portfolio.optimize.setup-exposure-map :as exposure-map]))

(portfolio/configure-scenes
  {:title "Exposure Map"
   :collection :optimize})

(defn- shell
  [content]
  (layout/page-shell
   (layout/desktop-shell
    [:div {:class ["portfolio-optimizer" "w-full" "p-6"]}
     [:div {:style {:max-width "360px"}} content]])))

(defn- render
  [opts]
  (shell (exposure-map/exposure-map (exposure-vm/exposure-map-model opts))))

(portfolio/defscene balanced-default
  []
  (render {:constraints {:gross-max 2.0 :net-min 1.0 :net-max 1.0 :max-asset-weight 0.5}
           :current-exposure {:gross 1.8 :net 1.0}
           :highlighted-controls #{}}))

(portfolio/defscene seeded-gross-floor
  []
  ;; The screenshot case: a tight gross band (floor + ceiling) and a wide-ish long net band.
  (render {:constraints {:gross-min 1.91 :gross-max 1.92 :net-min 1.31 :net-max 1.42
                         :max-asset-weight 0.5}
           :current-exposure {:gross 1.88 :net 1.28}
           :highlighted-controls #{}}))

(portfolio/defscene long-bias
  []
  (render {:constraints {:gross-max 2.0 :net-min 1.25 :net-max 1.75 :max-asset-weight 0.5}
           :current-exposure {:gross 1.6 :net 1.5}
           :highlighted-controls #{}}))

(portfolio/defscene high-leverage
  []
  ;; A 6x gross ceiling grows the Y axis past the 3x floor to the 10x nice step, so the handle is
  ;; never clipped and the trader is not capped at 3x.
  (render {:constraints {:gross-min 5.0 :gross-max 6.0 :net-min 1.5 :net-max 2.5
                         :max-asset-weight 0.5}
           :current-exposure {:gross 5.4 :net 1.9}
           :highlighted-controls #{}}))

(portfolio/defscene off-policy-current
  []
  ;; Current portfolio sits outside the target band on both axes → off-policy verdict + warn dot.
  (render {:constraints {:gross-max 1.0 :net-min 0.0 :net-max 0.0 :max-asset-weight 0.25}
           :current-exposure {:gross 2.4 :net 1.6}
           :highlighted-controls #{}}))

(portfolio/defscene infeasible-highlight
  []
  ;; A run flagged gross + net infeasible → the pad border + axis read warn.
  (render {:constraints {:gross-min 2.5 :gross-max 3.0 :net-min 1.8 :net-max 2.0
                         :max-asset-weight 0.5}
           :current-exposure {:gross 1.2 :net 0.8}
           :highlighted-controls #{:gross-max :net-min}}))

(portfolio/defscene saved-default
  []
  ;; The Profile row shows "Use saved" when a default exists for the universe.
  (render {:constraints {:gross-max 2.0 :net-min 1.0 :net-max 1.0 :max-asset-weight 0.5}
           :current-exposure {:gross 1.8 :net 1.0}
           :highlighted-controls #{}
           :has-saved-default? true}))
