(ns hyperopen.workbench.scenes.optimize.equal-risk-correlation-scenes
  "Workbench scenes for the Equal Risk CORRELATION DRIVERS / DIVERSIFICATION
  tabs (designer specs 2026-07-11: correlation view + per-asset breakdown) and the
  Allocation table's row selection + P&L-correlation line. `designer-parity`
  mirrors the mock's 3-long/2-short book with an INTERACTIVE selection store:
  clicking an Allocation row OR picking from the breakdown tab's Change-asset
  select re-targets the per-asset contribution panel exactly as the app does
  (the correlation tab is the full-width heatmap alone). The other scenes
  stress the states the mock doesn't draw: a 28-asset book whose heatmap caps
  at 24 with the honest remainder line (while the Change-asset select still
  offers all 28), and a degenerate zero-variance asset whose correlations
  render as em-dashes.

  Every fixture's :risk-contributions AND :risk-structure sections are
  computed by the REAL domain math over an explicit covariance, so the scene
  numbers can never violate the identities the views assume (net = own variance
  + cross covariance; position corr = sides × underlying corr). Absent
  :risk-structure (every scene in equal-risk-scenes) is the degradation case:
  the card keeps its original two tabs."
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.portfolio.optimizer.domain.risk-contributions
             :as risk-contributions]
            [hyperopen.portfolio.optimizer.domain.risk-structure :as risk-structure]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.workbench.support.state :as ws]
            [hyperopen.views.portfolio.optimize.results-summary :as results-summary]
            [hyperopen.views.portfolio.optimize.risk-contributions-card
             :as risk-contributions-card]
            [hyperopen.views.portfolio.optimize.target-exposure-table
             :as target-exposure-table]))

(portfolio/configure-scenes
  {:title "Equal Risk correlation"
   :collection :optimize})

(defn- instrument-id
  [symbol]
  (str "perp:" symbol))

(defn- rho-at
  [rho fallback i j]
  (if (= i j)
    1.0
    (or (get rho [i j]) (get rho [j i]) fallback)))

(defn- covariance-from
  "Sigma = D rho D for per-asset vols and a sparse upper-triangle correlation
  map {[i j] rho}; unlisted pairs use `fallback`."
  [vols rho fallback]
  (let [n (count vols)]
    (mapv (fn [i]
            (mapv (fn [j]
                    (* (nth vols i) (nth vols j) (rho-at rho fallback i j)))
                  (range n)))
          (range n))))

(defn- structured-result
  "Solved-result fixture whose contribution + structure sections come from the
  real domain namespaces. `assets` are [symbol weight vol] rows; `rho` is the
  sparse underlying-correlation map."
  [{:keys [assets rho rho-fallback correlation-cap current-weights]
    :or {rho-fallback 0.25}}]
  (let [symbols (mapv first assets)
        ids (mapv (comp instrument-id first) assets)
        weights (mapv second assets)
        current-weights* (or current-weights
                             (mapv (fn [idx weight]
                                     (* weight
                                        (+ 0.45 (* 0.1 (mod idx 4)))))
                                   (range)
                                   weights))
        vols (mapv #(nth % 2) assets)
        n (count ids)
        targets (vec (repeat n (/ 1 n)))
        covariance (covariance-from vols rho rho-fallback)
        contributions (risk-contributions/contribution-summary
                       {:instrument-ids ids
                        :covariance covariance
                        :weights weights
                        :targets targets})
        structure (risk-structure/structure-summary
                   (cond-> {:instrument-ids ids
                            :covariance covariance
                            :weights weights}
                     correlation-cap (assoc :correlation-cap correlation-cap)))
        target-diversification (risk-structure/portfolio-diversification-summary
                                covariance weights)
        current-diversification (risk-structure/portfolio-diversification-summary
                                 covariance current-weights*)
        structure* (cond-> (dissoc structure :status)
                     (= :ok (:status target-diversification))
                     (assoc :target-diversification
                            (dissoc target-diversification :status))
                     (= :ok (:status current-diversification))
                     (assoc :current-diversification
                            (dissoc current-diversification :status)))]
    {:status :solved
     :as-of-ms 1752300000000
     :solver {:strategy :sequential-equal-risk
              :objective-kind :equal-risk}
     :instrument-ids ids
     :labels-by-instrument (zipmap ids symbols)
     :target-weights-by-instrument (zipmap ids weights)
     :current-weights-by-instrument (zipmap ids current-weights*)
     :expected-return 0.11
     :volatility (:portfolio-volatility structure)
     :performance {:in-sample-sharpe 0.52}
     :frontier-overlays {:standalone (map (fn [symbol vol]
                                            {:label symbol
                                             :volatility vol
                                             :expected-return (* 0.15 vol)})
                                          symbols
                                          vols)}
     :risk-contributions (-> contributions
                             (dissoc :status)
                             (assoc :quality :approximate))
     :risk-structure structure*
     :equal-risk-solver {:strategy :sequential-equal-risk
                         :converged? true
                         :termination-reason :step-tolerance
                         :iterations 14
                         :initialization-count 4
                         :selected-initialization :inverse-volatility
                         :allocation-freedom {:status :limited
                                              :free-degrees 3
                                              :binding-count 2
                                              :books {:long 3 :short 2}}
                         :initializations [{:seed-kind :inverse-volatility
                                            :status :completed
                                            :objective 2.1e-4
                                            :converged? true}]}
     :diagnostics {:gross-exposure (reduce + 0 (map js/Math.abs weights))
                   :net-exposure (reduce + 0 weights)
                   :long-exposure (reduce + 0 (filter pos? weights))
                   :short-exposure (- (reduce + 0 (filter neg? weights)))
                   :binding-constraints []}}))

;; The mock's book: BTC/ETH/SP500 long, MSTR/SOL short, underlying
;; correlations chosen so the POSITION P&L view reproduces the mock's signs
;; (long × short pairs flip negative, short × short stays positive). The
;; weights sit a nudge off the true equal-risk solution for this covariance
;; (tuned numerically), so the real math yields the mock's near-balanced
;; APPROXIMATE state — RMS 1.8 pts, max 2.7 pts — and the MSTR short tells
;; the decomposition story exactly: standalone 32.6%, diversification
;; −10.0%, net +22.7%.
(def ^:private designer-parity-result
  (structured-result
   {:assets [["BTC" 0.157 0.60]
             ["ETH" 0.124 0.65]
             ["SP500" 0.452 0.18]
             ["MSTR" -0.157 0.85]
             ["SOL" -0.11 0.90]]
    ;; The current book is smaller but concentrated in the highly correlated
    ;; BTC/ETH pair. The recommendation adds gross stress while its long/short
    ;; offsets lower modeled volatility, preserving the intended tradeoff with
    ;; summaries computed by the real domain math.
    :current-weights [0.25 0.25 0.0 0.0 0.0]
    :rho {[0 1] 0.68 [0 2] 0.42 [0 3] 0.28 [0 4] 0.21
          [1 2] 0.51 [1 3] 0.35 [1 4] 0.18
          [2 3] 0.22 [2 4] 0.05
          [3 4] 0.32}}))

;; 28 held assets: the persisted matrix caps at 24 (largest |net share|) and
;; the heatmap must say how many it dropped — also the full-width legibility
;; stress (24 columns of cells in the card-wide grid).
(def ^:private capped-twenty-eight-result
  (structured-result
   {:assets (vec
             (map-indexed (fn [idx symbol]
                            [symbol
                             (* (if (< idx 12) 1 -1)
                                (+ 0.04 (* 0.011 idx)))
                             (+ 0.3 (* 0.045 idx))])
                          ["BTC" "ETH" "SOL" "AVAX" "LINK" "ARB" "OP" "DOGE"
                           "AAVE" "UNI" "INJ" "TIA" "MKR" "LDO" "NEAR" "SUI"
                           "APT" "SEI" "JUP" "WLD" "PYTH" "ENA" "ONDO" "STX"
                           "RUNE" "ATOM" "DOT" "FIL"]))
    :rho {}
    :rho-fallback 0.35}))

;; A zero-variance asset (a depegged-flat stable proxy): its correlation
;; column/row must render as em-dashes, never fabricated numbers, while the
;; rest of the book stays fully populated.
(def ^:private degenerate-column-result
  (structured-result
   {:assets [["BTC" 0.45 0.60]
             ["ETH" 0.35 0.65]
             ["FLAT" 0.20 0.0]]
    :rho {[0 1] 0.72}}))

;; --- Interactive selection ------------------------------------------------------

(def ^:private selection-reducers
  {:actions/set-portfolio-optimizer-selected-risk-instrument
   (fn [state _dispatch-data instrument-id]
     (assoc state :selected-risk-instrument instrument-id))})

(defonce ^:private designer-parity-store
  (ws/create-store ::designer-parity {:selected-risk-instrument "perp:MSTR"}))

(defn- results-scene
  "Allocation table + risk card side by side, selection flowing from the
  scene store exactly as it flows from app state in the real page. The right
  column carries the center-panel class so the why-card's active-tab
  highlight works here too."
  [store result]
  (let [selected (:selected-risk-instrument @store)]
    (layout/page-shell
     (layout/interactive-shell
      store
      selection-reducers
      [:div {:class ["portfolio-optimizer" "mx-auto" "w-full"]
             :style {:max-width "1500px"}}
       [:div {:class ["grid" "grid-cols-1" "gap-4" "p-4" "items-start"
                      "lg:grid-cols-[minmax(320px,430px)_minmax(0,1fr)]"]}
        (target-exposure-table/target-exposure-table
         result
         {:selected-risk-instrument selected})
        [:div {:class ["optimizer-results-center-panel" "space-y-4"]}
         (risk-contributions-card/risk-contributions-card
          result
          {:selected-risk-instrument selected})
         (results-summary/equal-risk-context-card result)]]]))))

(defn- static-card-scene
  [result]
  (layout/page-shell
   [:div {:class ["portfolio-optimizer" "mx-auto" "w-full"]
          :style {:max-width "1040px"}}
    [:div {:class ["optimizer-results-center-panel" "space-y-4" "p-4"]}
     (risk-contributions-card/risk-contributions-card result)
     (results-summary/equal-risk-context-card result)]]))

(portfolio/defscene correlation-designer-parity
  :params designer-parity-store
  [store]
  (results-scene store designer-parity-result))

(portfolio/defscene correlation-capped-twenty-eight-assets
  []
  (static-card-scene capped-twenty-eight-result))

(portfolio/defscene correlation-degenerate-column
  []
  (static-card-scene degenerate-column-result))
