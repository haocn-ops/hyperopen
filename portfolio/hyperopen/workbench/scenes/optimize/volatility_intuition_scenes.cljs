(ns hyperopen.workbench.scenes.optimize.volatility-intuition-scenes
  "Workbench scenes for the volatility-intuition rail card, the under-chart
  insight strip, and the leverage-risk card (designer spec 2026-07-12,
  user-trimmed). The extreme scene mirrors the mock's levered book (411.82%
  annualized σ) on the repo's 365-calendar-day scaling; the moderate scene
  proves the quiet path (no severity callout, no insight strip, no leverage
  card); the very-high scene exercises the vol-only gate, the missing-current
  degradation (no Target/Current toggle), and the no-capital fallback where
  the leverage card speaks in multiples of starting equity.

  The Target/Current toggle is DOM-radio + :has() state, so it works in these
  static scenes exactly as in the app — click the tabs."
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.views.portfolio.optimize.leverage-risk-card
             :as leverage-risk-card]
            [hyperopen.views.portfolio.optimize.volatility-intuition-card
             :as volatility-intuition-card]
            [hyperopen.workbench.support.layout :as layout]))

(portfolio/configure-scenes
  {:title "Volatility intuition"
   :collection :optimize})

;; The mock's levered book: gross 8.15x, extreme annualized σ. On the 365-day
;; basis the card must show ±21.56% / ±57.03% / ±118.1% (uncapped) with the
;; extreme callout, the −100% boundary note, the insight strip, and a
;; leverage-risk card whose median target outcome collapses despite the huge
;; arithmetic mean.
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
;; note, no boundary note, empty insight slot, and the leverage-risk card
;; must NOT render (0.9x gross, σ below the 100% gate).
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
;; vol-only gate surfaces the leverage card, and its rows speak in multiples
;; of starting equity instead of dollars.
(def ^:private very-high-no-current-result
  {:status :solved
   :as-of-ms 1752300000300
   :expected-return 0.80
   :volatility 1.50
   :diagnostics {:gross-exposure 1.2
                 :net-exposure 1.2}})

(defn- recommendation-slice
  "Center-column strip + right-rail cards at the app's rail width."
  [result]
  (layout/page-shell
   [:div {:class ["portfolio-optimizer" "mx-auto" "w-full"]
          :style {:max-width "1080px"}}
    [:div {:class ["grid" "gap-4" "p-4" "items-start"]
           :style {:grid-template-columns "minmax(0, 1fr) 340px"}}
     [:div {:class ["optimizer-results-center-panel" "space-y-4" "bg-base-100" "p-4"]}
      [:div {:class ["border" "border-base-300" "bg-base-200/30" "p-10"
                     "text-center" "text-xs" "text-trading-muted"]}
       "(efficient frontier chart)"]
      (volatility-intuition-card/insight-strip result)]
     [:div {:class ["optimizer-results-right-panel" "bg-base-100/95"]}
      (volatility-intuition-card/volatility-intuition-card result)
      (leverage-risk-card/leverage-risk-card result)]]]))

(portfolio/defscene extreme-levered-book
  []
  (recommendation-slice extreme-levered-result))

(portfolio/defscene moderate-book
  []
  (recommendation-slice moderate-result))

(portfolio/defscene very-high-vol-no-current
  []
  (recommendation-slice very-high-no-current-result))
