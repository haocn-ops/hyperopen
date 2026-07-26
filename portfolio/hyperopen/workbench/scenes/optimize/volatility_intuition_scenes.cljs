(ns hyperopen.workbench.scenes.optimize.volatility-intuition-scenes
  "Workbench scenes for the volatility-intuition rail card and the
  under-chart ONE-YEAR MODELED LEVERAGE IMPACT panel (designer mockup,
  user-trimmed 2026-07-12). The extreme scene mirrors the mock's levered book
  (411.82% annualized σ) on the repo's 365-calendar-day scaling; the moderate
  scene proves the quiet path (no severity callout, no leverage panel); the
  very-high scene exercises the vol-only gate, the missing-current
  degradation (no Target/Current toggle), and the no-capital fallback where
  the panel — including its ending-wealth distribution markers — speaks in
  multiples of starting equity.

  The Target/Current toggle is DOM-radio + :has() state, so it works in these
  static scenes exactly as in the app — click the tabs."
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.views.portfolio.optimize.leverage-impact-panel
             :as leverage-impact-panel]
            [hyperopen.views.portfolio.optimize.volatility-intuition-card
             :as volatility-intuition-card]
            [hyperopen.workbench.support.layout :as layout]))

(portfolio/configure-scenes
  {:title "Volatility intuition"
   :collection :optimize})

;; The mock's levered book: gross 8.15x, extreme annualized σ. On the 365-day
;; basis the card must show ±21.56% / ±57.03% / ±118.1% (uncapped) with the
;; extreme callout and the −100% boundary note, and the leverage-impact panel
;; must show a target median that collapses despite the huge arithmetic mean —
;; with the distribution's median marker far left of its mean marker.
(def ^:private extreme-levered-result
  {:status :solved
   :as-of-ms 1752300000100
   :expected-return 18.6606
   :volatility 4.1182
   :current-expected-return 24.3160
   :current-volatility 3.1390
   :performance {:in-sample-sharpe 4.531}
   :current-performance {:in-sample-sharpe 7.746}
   :diagnostics {:gross-exposure 8.15
                 :net-exposure 8.15}
   :rebalance-preview {:capital-usd 100000}})

;; A defensive book: 40% target σ against a 30% current book. No severity
;; note, no boundary note, and the leverage-impact panel must NOT render
;; (0.9x gross, σ below the 100% gate) — the slot under the chart stays empty.
(def ^:private moderate-result
  {:status :solved
   :as-of-ms 1752300000200
   :expected-return 0.12
   :volatility 0.40
   :current-expected-return 0.10
   :current-volatility 0.30
   :diagnostics {:gross-exposure 0.9
                 :net-exposure 0.9}
   :rebalance-preview {:capital-usd 100000}})

;; Very high σ with no current baseline and no account equity: toggle absent,
;; vol-only gate surfaces the leverage-impact panel, and its rows and
;; distribution markers speak in multiples of starting equity instead of
;; dollars.
(def ^:private very-high-no-current-result
  {:status :solved
   :as-of-ms 1752300000300
   :expected-return 0.80
   :volatility 1.50
   :diagnostics {:gross-exposure 1.2
                 :net-exposure 1.2}})

(defn- recommendation-slice
  "Center column (frontier placeholder + leverage-impact panel) + the rail
  card at the app's rail width."
  [result]
  (layout/page-shell
   [:div {:class ["portfolio-optimizer" "mx-auto" "w-full"]
          :style {:max-width "1240px"}}
    [:div {:class ["grid" "gap-4" "p-4" "items-start"]
           :style {:grid-template-columns "minmax(0, 1fr) 340px"}}
     [:div {:class ["optimizer-results-center-panel" "space-y-4" "bg-base-100" "p-4"]}
      [:div {:class ["border" "border-base-300" "bg-base-200/30" "p-10"
                     "text-center" "text-xs" "text-trading-muted"]}
       "(efficient frontier chart)"]
      (leverage-impact-panel/leverage-impact-panel result)]
     [:div {:class ["optimizer-results-right-panel" "bg-base-100/95"]}
      (volatility-intuition-card/volatility-intuition-card result)]]]))

(portfolio/defscene extreme-levered-book
  []
  (recommendation-slice extreme-levered-result))

(portfolio/defscene moderate-book
  []
  (recommendation-slice moderate-result))

(portfolio/defscene very-high-vol-no-current
  []
  (recommendation-slice very-high-no-current-result))
