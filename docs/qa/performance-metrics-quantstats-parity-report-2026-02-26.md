# Performance Metrics Tab vs QuantStats Parity Report (2026-02-26)

## Scope
- Hyperopen metric source:
  - `/hyperopen/src/hyperopen/portfolio/metrics.cljs`
  - `/hyperopen/src/hyperopen/views/portfolio/vm.cljs`
- QuantStats source pinned to commit `fbd10daed0227aa0d10da6513f1b15e7e98d7fae`:
  - `quantstats/stats.py`
  - `quantstats/reports.py`
  - `quantstats/utils.py`
- Baseline rule for this audit: `periods_per_year=365` in Hyperopen vs QuantStats default `252` is treated as expected and not marked as a discrepancy by itself.

## Verification Method
1. Enumerated all rows in `performance-metric-groups` (`49` metrics total).
2. Mapped each row to Hyperopen implementation and QuantStats implementation.
3. Compared formula and runtime semantics (including report-layer behavior where applicable).
4. Executed test suite (`npm test`) to validate current parity tests; run passed (`1468` tests, `7375` assertions, `0` failures).

## Summary
- Total metrics audited: `49`
- `MATCH`: `36`
- `MATCH (CAVEAT)`: `2`
- `DISCREPANCY`: `11`

Primary discrepancy themes:
1. Annualization year basis: Hyperopen uses elapsed calendar span (`rows-span-years`) in `compute-performance-metrics`; QuantStats uses `len(returns) / periods`.
2. Window boundary handling: Hyperopen filters windows by `:day` at midnight; QuantStats filters by full timestamp index.
3. VaR/CVaR sign handling in report metrics: QuantStats forces negative sign with `-abs(...)`; Hyperopen currently uses raw values.

## Metric-by-Metric Audit

### Overview
| Metric | Hyperopen | QuantStats | Status | Notes |
|---|---|---|---|---|
| Time in Market | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:490` | `stats.py:462` + `reports.py:1302` | MATCH | Same ceil-to-2-decimals exposure ratio over non-zero returns. |
| Cumulative Return | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:481` | `stats.py:99` + `reports.py:1309` | MATCH | Same compounded product minus 1. |
| CAGR | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:518`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1274` | `stats.py:1507` + `reports.py:1314` | DISCREPANCY | Hyperopen passes `:years` from elapsed calendar span; QuantStats uses `len(returns)/periods`. |

### Risk-Adjusted Ratios
| Metric | Hyperopen | QuantStats | Status | Notes |
|---|---|---|---|---|
| Sharpe | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:595` | `stats.py:841` + `reports.py:1320` | MATCH | Same mean(excess)/std(excess), annualized by `sqrt(periods)`. |
| Prob. Sharpe Ratio | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:766` | `stats.py:1258` + `stats.py:1188` + `reports.py:1321` | MATCH | Function-level parity matches QuantStats PSR math. |
| Smart Sharpe | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:617` | `stats.py:901` | MATCH | Same Sharpe with autocorrelation penalty. |
| Sortino | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:623` | `stats.py:982` + `reports.py:1331` | MATCH | Same downside deviation definition and annualization. |
| Smart Sortino | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:651` | `stats.py:1053` + `reports.py:1334` | MATCH | Same Sortino with autocorrelation penalty. |
| Sortino/sqrt(2) | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1285` | `reports.py:1339` | MATCH | Same derived value from Sortino. |
| Smart Sortino/sqrt(2) | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1287` | `reports.py:1343` | MATCH | Same derived value from Smart Sortino. |
| Omega | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:799` | `stats.py:1394` + `reports.py:1355` | MATCH | Same threshold conversion and gain/loss deviation ratio. |

### Drawdown and Risk
| Metric | Hyperopen | QuantStats | Status | Notes |
|---|---|---|---|---|
| Max Drawdown | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:908` + `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1292` | `reports.py:2185` + `reports.py:2280` | MATCH | Same drawdown-details-driven max drawdown row semantics. |
| Max DD Date | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1293` | `reports.py:2281` | MATCH | Same valley date extraction from worst drawdown period. |
| Max DD Period Start | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1294` | `reports.py:2282` | MATCH | Same start date semantics. |
| Max DD Period End | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1295` | `reports.py:2283` | MATCH | Same end date semantics (last drawdown day, not recovery day). |
| Longest DD Days | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1296` | `reports.py:2284` | MATCH | Same longest period by `days`. |
| Volatility (ann.) | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:542`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1297` | `stats.py:683` + `reports.py:1378` | MATCH | Same sample stddev annualization. |
| R^2 | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1161`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1298` | `stats.py:2581` + `reports.py:1407` | MATCH (CAVEAT) | Formula is equivalent (`corr^2`), but Hyperopen aligns benchmark by shared day only; QuantStats benchmark prep can fill missing benchmark dates. |
| Information Ratio | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1166`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1300` | `stats.py:2635` + `reports.py:1410` | MATCH (CAVEAT) | Formula matches; same caveat as R^2 on alignment path. |
| Calmar | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:920`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1302` | `stats.py:1642` + `reports.py:1438` | DISCREPANCY | Uses CAGR value derived from elapsed span override rather than QuantStats `len/periods` CAGR basis. |
| Skew | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:672`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1304` | `stats.py:1588` + `reports.py:1439` | MATCH | Matches adjusted sample skew estimator used by pandas/QuantStats. |
| Kurtosis | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:691`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1305` | `stats.py:1615` + `reports.py:1440` | MATCH | Matches excess kurtosis estimator used by pandas/QuantStats. |

### Expectation and VaR
| Metric | Hyperopen | QuantStats | Status | Notes |
|---|---|---|---|---|
| Expected Daily | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:954`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1306` | `stats.py:198` + `reports.py:1462` | MATCH | Same geometric mean over daily grouped returns. |
| Expected Monthly | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:954`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1308` | `stats.py:198` + `reports.py:1466` | MATCH | Same geometric mean over month groups. |
| Expected Yearly | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:954`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1310` | `stats.py:198` + `reports.py:1472` | MATCH | Same geometric mean over year groups. |
| Kelly Criterion | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:995`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1312` | `stats.py:2553` + `reports.py:1479` | MATCH | Same Kelly formula using payoff ratio and win rate. |
| Risk of Ruin | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1005`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1313` | `stats.py:1815` + `reports.py:1482` | MATCH | Same gambler's ruin formula. |
| Daily Value-at-Risk | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1014`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1314` | `stats.py:1861` + `reports.py:1485` | DISCREPANCY | Hyperopen uses raw VaR; QuantStats metrics table forces negative with `-abs(var)`. |
| Expected Shortfall (cVaR) | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1030`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1315` | `stats.py:1921` + `stats.py:1990` + `reports.py:1488` | DISCREPANCY | Hyperopen uses raw ES/CVaR; QuantStats metrics table forces negative with `-abs(cvar)`. |

### Streaks and Pain
| Metric | Hyperopen | QuantStats | Status | Notes |
|---|---|---|---|---|
| Max Consecutive Wins | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1057`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1316` | `stats.py:390` + `reports.py:1497` | MATCH | Same longest positive-streak logic. |
| Max Consecutive Losses | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1061`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1317` | `stats.py:426` + `reports.py:1498` | MATCH | Same longest negative-streak logic. |
| Gain/Pain Ratio | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1065`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1318` | `stats.py:1466` + `reports.py:1501` | MATCH | Same sum(returns)/abs(sum(negative returns)) behavior. |
| Gain/Pain (1M) | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1065`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1319` | `stats.py:1466` + `reports.py:1502` | MATCH | Same monthly aggregate then gain/pain ratio. |

### Trade Shape
| Metric | Hyperopen | QuantStats | Status | Notes |
|---|---|---|---|---|
| Payoff Ratio | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:986`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1320` | `stats.py:2054` + `reports.py:1512` | MATCH | Same avg win divided by absolute avg loss. |
| Profit Factor | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1075`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1321` | `stats.py:2161` + `reports.py:1513` | MATCH | Same wins/losses sum behavior including infinite case. |
| Common Sense Ratio | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1109`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1322` | `stats.py:2228` + `reports.py:1514` | MATCH | Same `profit_factor * tail_ratio`. |
| CPC Index | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1117`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1323` | `stats.py:2201` + `reports.py:1515` | MATCH | Same `profit_factor * win_rate * win_loss_ratio`. |
| Tail Ratio | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1100`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1324` | `stats.py:2008` + `reports.py:1516` | MATCH | Same `abs(q95/q05)` quantile ratio semantics. |
| Outlier Win Ratio | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1129`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1325` | `stats.py:2254` + `reports.py:1517` | MATCH | Same `q99 / mean(returns>=0)`. |
| Outlier Loss Ratio | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1138`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1326` | `stats.py:2293` + `reports.py:1518` | MATCH | Same `q01 / mean(returns<0)`. |

### Period Returns
| Metric | Hyperopen | QuantStats | Status | Notes |
|---|---|---|---|---|
| MTD | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1327` | `reports.py:1531` | MATCH | Same month-start window and comp/sum behavior. |
| 3M | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1328`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1176` | `reports.py:1534` | DISCREPANCY | Hyperopen window filter compares parsed day-midnight against timestamp cutoff; QuantStats uses full timestamp index comparison. |
| 6M | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1329`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1176` | `reports.py:1535` | DISCREPANCY | Same boundary-day discrepancy as 3M. |
| YTD | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1330` | `reports.py:1536` | MATCH | Same year-start window logic. |
| 1Y | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1331`, `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1176` | `reports.py:1537` | DISCREPANCY | Same boundary-day discrepancy as 3M/6M. |
| 3Y (ann.) | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1332` | `reports.py:1548` + `stats.py:1507` | DISCREPANCY | Uses elapsed-span `:years` override plus day-midnight boundary filter; QuantStats uses `cagr(window, periods)` with timestamp cutoff. |
| 5Y (ann.) | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1335` | `reports.py:1553` + `stats.py:1507` | DISCREPANCY | Same issue pattern as 3Y (ann.). |
| 10Y (ann.) | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1338` | `reports.py:1558` + `stats.py:1507` | DISCREPANCY | Same issue pattern as 3Y (ann.). |
| All-time (ann.) | `/hyperopen/src/hyperopen/portfolio/metrics.cljs:1341` | `reports.py:1563` + `stats.py:1507` | DISCREPANCY | Uses elapsed calendar span (`rows-span-years`) instead of QuantStats `len/periods` annualization basis. |

## Discrepancy Detail

### D1: CAGR Year Basis Diverges from QuantStats
- Hyperopen: `compute-performance-metrics` injects `:years` from `rows-span-years` (`/hyperopen/src/hyperopen/portfolio/metrics.cljs:1267-1276`, `1332-1343`).
- QuantStats: `cagr` uses `years = len(returns) / periods` (`stats.py:1548`).
- Impacted metrics: `CAGR`, `Calmar`, `3Y (ann.)`, `5Y (ann.)`, `10Y (ann.)`, `All-time (ann.)`.

### D2: Rolling Window Boundary Uses Midnight Day Keys in Hyperopen
- Hyperopen: `rows-since-ms` parses `:day` to midnight before comparison (`/hyperopen/src/hyperopen/portfolio/metrics.cljs:1176-1183`).
- QuantStats: window filtering uses full datetime index cutoff (`reports.py:1534-1537`, `1548-1560`).
- Impacted metrics: `3M`, `6M`, `1Y` (and contributes to annualized multi-year window differences).

### D3: VaR/CVaR Sign Handling Differs from QuantStats Report Metrics
- Hyperopen: stores raw `value-at-risk` and raw `expected-shortfall` (`/hyperopen/src/hyperopen/portfolio/metrics.cljs:1314-1315`).
- QuantStats report metrics: forces negative sign for both via `-abs(...)` (`reports.py:1485-1490`).
- Impacted metrics: `Daily Value-at-Risk`, `Expected Shortfall (cVaR)`.

## Existing Parity Test Coverage
- Current parity tests in `/hyperopen/test/hyperopen/portfolio/metrics_test.cljs` validate many function-level formulas directly against QuantStats-derived expected values (`quantstats-ratio-parity-test`, `quantstats-risk-and-distribution-parity-test`, `quantstats-trade-shape-parity-test`, `quantstats-benchmark-parity-test`).
- Those tests are strong for function math parity but do not currently enforce report-layer semantics for:
  - annualization year basis in VM-level `compute-performance-metrics`
  - report-layer VaR/CVaR sign normalization
  - timestamp boundary inclusion semantics for rolling windows

