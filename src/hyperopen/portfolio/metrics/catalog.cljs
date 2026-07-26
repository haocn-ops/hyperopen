(ns hyperopen.portfolio.metrics.catalog)

(def ^:private metric-descriptions
  {:time-in-market "Share of days in the selected window with active exposure."
   :cumulative-return "Total compounded return over the selected window."
   :cagr "Annualized compounded growth rate over the selected window."
   :sharpe "Risk-adjusted return using excess return divided by volatility."
   :prob-sharpe-ratio "Probability that the observed Sharpe ratio is greater than zero."
   :smart-sharpe "Sharpe ratio adjusted for autocorrelation in returns."
   :sortino "Excess return divided by downside volatility only."
   :smart-sortino "Sortino ratio adjusted for autocorrelation in returns."
   :sortino-sqrt2 "Sortino ratio scaled by sqrt(2) for alternate reporting conventions."
   :smart-sortino-sqrt2 "Autocorrelation-adjusted Sortino ratio scaled by sqrt(2)."
   :omega "Ratio of gains above the target return to losses below it."
   :max-drawdown "Largest peak-to-trough loss in the selected window."
   :max-dd-date "Date the maximum drawdown reached its trough."
   :max-dd-period-start "Date the maximum drawdown began at its prior peak."
   :max-dd-period-end "Date the maximum drawdown recovered, or the latest observed point."
   :longest-dd-days "Longest drawdown duration in calendar days."
   :avg-drawdown "Average maximum drawdown across observed drawdown periods."
   :avg-drawdown-days "Average duration of observed drawdown periods."
   :recovery-factor "Absolute total return divided by absolute maximum drawdown."
   :ulcer-index "Root mean square of drawdowns, measuring drawdown depth and duration."
   :serenity-index "Return quality ratio combining total return, ulcer index, and drawdown tail risk."
   :volatility-ann "Annualized standard deviation of returns."
   :r2 "How closely returns moved with the benchmark, on a 0 to 1 scale."
   :information-ratio "Active return divided by tracking error versus the benchmark."
   :beta "Sensitivity of portfolio returns to the selected benchmark."
   :alpha "Annualized excess return after adjusting for benchmark sensitivity."
   :correlation "Linear correlation between portfolio returns and the selected benchmark."
   :treynor-ratio "Compounded excess return divided by benchmark beta."
   :calmar "Annualized return divided by maximum drawdown."
   :skew "Asymmetry of the return distribution."
   :kurtosis "Fat-tailedness of the return distribution relative to normal."
   :expected-daily "Average expected one-day return from the observed history."
   :expected-monthly "Average expected one-month return implied by the observed history."
   :expected-yearly "Average expected one-year return implied by the observed history."
   :kelly-criterion "Suggested capital fraction from the observed win and loss profile."
   :risk-of-ruin "Estimated chance of compounding down to a near-zero capital threshold."
   :daily-var "Expected one-day loss threshold at the configured confidence level."
   :expected-shortfall "Average loss on days worse than Value-at-Risk."
   :max-consecutive-wins "Longest streak of positive-return days."
   :max-consecutive-losses "Longest streak of negative-return days."
   :gain-pain-ratio "Total gains divided by total losses."
   :gain-pain-1m "One-month gain-to-pain ratio using rolling monthly results."
   :payoff-ratio "Average winning return divided by average losing return."
   :profit-factor "Gross gains divided by gross losses."
   :common-sense-ratio "Tail-aware return quality ratio combining gain and loss efficiency."
   :cpc-index "Composite ratio summarizing payoff quality and consistency."
   :tail-ratio "Magnitude of right-tail gains relative to left-tail losses."
   :outlier-win-ratio "Contribution of extreme winning days relative to typical wins."
   :outlier-loss-ratio "Contribution of extreme losing days relative to typical losses."
   :mtd "Return since the start of the current month."
   :m3 "Return over the last three months."
   :m6 "Return over the last six months."
   :ytd "Return since the start of the current calendar year."
   :y1 "Return over the last year."
   :y3-ann "Annualized return over the last three years."
   :y5-ann "Annualized return over the last five years."
   :y10-ann "Annualized return over the last ten years."
   :all-time-ann "Annualized return over the full available history."
   :best-day "Best single-day return in the selected window."
   :worst-day "Worst single-day return in the selected window."
   :best-month "Best compounded monthly return in the selected window."
   :worst-month "Worst compounded monthly return in the selected window."
   :best-year "Best compounded yearly return in the selected window."
   :worst-year "Worst compounded yearly return in the selected window."
   :avg-up-month "Average positive compounded monthly return."
   :avg-down-month "Average negative compounded monthly return."
   :win-days "Share of non-zero daily returns that are positive."
   :win-month "Share of non-zero monthly returns that are positive."
   :win-quarter "Share of non-zero quarterly returns that are positive."
   :win-year "Share of non-zero yearly returns that are positive."})

(def ^:private performance-metric-groups
  [{:id :overview
    :rows [{:key :time-in-market
            :label "Time in Market"
            :kind :percent}
           {:key :cumulative-return
            :label "Cumulative Return"
            :kind :percent}
           {:key :cagr
            :label "CAGR"
            :kind :percent}]}
   {:id :risk-adjusted
    :rows [{:key :sharpe
            :label "Sharpe"
            :kind :ratio}
           {:key :prob-sharpe-ratio
            :label "Prob. Sharpe Ratio"
            :kind :ratio}
           {:key :smart-sharpe
            :label "Smart Sharpe"
            :kind :ratio}
           {:key :sortino
            :label "Sortino"
            :kind :ratio}
           {:key :smart-sortino
            :label "Smart Sortino"
            :kind :ratio}
           {:key :sortino-sqrt2
            :label "Sortino/sqrt(2)"
            :kind :ratio}
           {:key :smart-sortino-sqrt2
            :label "Smart Sortino/sqrt(2)"
            :kind :ratio}
           {:key :omega
            :label "Omega"
            :kind :ratio}]}
   {:id :drawdown-and-risk
    :rows [{:key :max-drawdown
            :label "Max Drawdown"
            :kind :percent}
           {:key :max-dd-date
            :label "Max DD Date"
            :kind :date}
           {:key :max-dd-period-start
            :label "Max DD Period Start"
            :kind :date}
           {:key :max-dd-period-end
            :label "Max DD Period End"
            :kind :date}
	           {:key :longest-dd-days
	            :label "Longest DD Days"
	            :kind :integer}
	           {:key :avg-drawdown
	            :label "Avg. Drawdown"
	            :kind :percent}
	           {:key :avg-drawdown-days
	            :label "Avg. Drawdown Days"
	            :kind :integer}
	           {:key :recovery-factor
	            :label "Recovery Factor"
	            :kind :ratio}
	           {:key :ulcer-index
	            :label "Ulcer Index"
	            :kind :ratio}
	           {:key :serenity-index
	            :label "Serenity Index"
	            :kind :ratio}
	           {:key :volatility-ann
	            :label "Volatility (ann.)"
	            :kind :percent}
           {:key :r2
            :label "R^2"
            :kind :ratio}
           {:key :information-ratio
            :label "Information Ratio"
            :kind :ratio}
           {:key :calmar
            :label "Calmar"
            :kind :ratio}
           {:key :skew
            :label "Skew"
            :kind :ratio}
           {:key :kurtosis
            :label "Kurtosis"
            :kind :ratio}]}
   {:id :expectation-and-var
    :rows [{:key :expected-daily
            :label "Expected Daily"
            :kind :percent}
           {:key :expected-monthly
            :label "Expected Monthly"
            :kind :percent}
           {:key :expected-yearly
            :label "Expected Yearly"
            :kind :percent}
           {:key :kelly-criterion
            :label "Kelly Criterion"
            :kind :percent}
           {:key :risk-of-ruin
            :label "Risk of Ruin"
            :kind :ratio}
           {:key :daily-var
            :label "Daily Value-at-Risk"
            :kind :percent}
           {:key :expected-shortfall
            :label "Expected Shortfall (cVaR)"
            :kind :percent}]}
   {:id :streaks-and-pain
    :rows [{:key :max-consecutive-wins
            :label "Max Consecutive Wins"
            :kind :integer}
           {:key :max-consecutive-losses
            :label "Max Consecutive Losses"
            :kind :integer}
           {:key :gain-pain-ratio
            :label "Gain/Pain Ratio"
            :kind :ratio}
           {:key :gain-pain-1m
            :label "Gain/Pain (1M)"
            :kind :ratio}]}
   {:id :trade-shape
    :rows [{:key :payoff-ratio
            :label "Payoff Ratio"
            :kind :ratio}
           {:key :profit-factor
            :label "Profit Factor"
            :kind :ratio}
           {:key :common-sense-ratio
            :label "Common Sense Ratio"
            :kind :ratio}
           {:key :cpc-index
            :label "CPC Index"
            :kind :ratio}
           {:key :tail-ratio
            :label "Tail Ratio"
            :kind :ratio}
           {:key :outlier-win-ratio
            :label "Outlier Win Ratio"
            :kind :ratio}
           {:key :outlier-loss-ratio
            :label "Outlier Loss Ratio"
            :kind :ratio}]}
   {:id :period-returns
    :rows [{:key :mtd
            :label "MTD"
            :kind :percent}
           {:key :m3
            :label "3M"
            :kind :percent}
           {:key :m6
            :label "6M"
            :kind :percent}
           {:key :ytd
            :label "YTD"
            :kind :percent}
           {:key :y1
            :label "1Y"
            :kind :percent}
           {:key :y3-ann
            :label "3Y (ann.)"
            :kind :percent}
           {:key :y5-ann
            :label "5Y (ann.)"
            :kind :percent}
           {:key :y10-ann
            :label "10Y (ann.)"
            :kind :percent}
	           {:key :all-time-ann
	            :label "All-time (ann.)"
	            :kind :percent}]}
	   {:id :period-extremes
	    :rows [{:key :best-day
	            :label "Best Day"
	            :kind :percent}
	           {:key :worst-day
	            :label "Worst Day"
	            :kind :percent}
	           {:key :best-month
	            :label "Best Month"
	            :kind :percent}
	           {:key :worst-month
	            :label "Worst Month"
	            :kind :percent}
	           {:key :best-year
	            :label "Best Year"
	            :kind :percent}
	           {:key :worst-year
	            :label "Worst Year"
	            :kind :percent}]}
	   {:id :win-rates
	    :rows [{:key :avg-up-month
	            :label "Avg. Up Month"
	            :kind :percent}
	           {:key :avg-down-month
	            :label "Avg. Down Month"
	            :kind :percent}
	           {:key :win-days
	            :label "Win Days"
	            :kind :percent}
	           {:key :win-month
	            :label "Win Month"
	            :kind :percent}
	           {:key :win-quarter
	            :label "Win Quarter"
	            :kind :percent}
	           {:key :win-year
	            :label "Win Year"
	            :kind :percent}]}
	   {:id :benchmark-relative
	    :rows [{:key :beta
	            :label "Beta"
	            :kind :ratio}
	           {:key :alpha
	            :label "Alpha"
	            :kind :ratio}
	           {:key :correlation
	            :label "Correlation"
	            :kind :percent}
	           {:key :treynor-ratio
	            :label "Treynor Ratio"
	            :kind :percent}]}])

(defn metric-rows
  [metric-values]
  (let [metric-status (or (:metric-status metric-values)
                          {})
        metric-reason (or (:metric-reason metric-values)
                          {})]
    (mapv (fn [{:keys [rows] :as group}]
            (assoc group
                   :rows (mapv (fn [{:keys [key] :as row}]
                                 (assoc row
                                        :description (get metric-descriptions key)
                                        :value (get metric-values key)
                                        :status (get metric-status key)
                                        :reason (get metric-reason key)))
                               rows)))
          performance-metric-groups)))
