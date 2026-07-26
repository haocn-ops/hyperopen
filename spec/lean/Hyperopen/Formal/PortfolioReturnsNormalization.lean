import Hyperopen.Formal.Common

namespace Hyperopen.Formal.PortfolioReturnsNormalization

open Hyperopen.Formal

def surface : Surface := .portfolioReturnsNormalization

structure Ratio where
  numerator : Int
  denominator : Nat
  deriving Repr, DecidableEq, Inhabited

structure Summary where
  accountValueHistory : List (Nat × Int) := []
  pnlHistory : List (Nat × Int) := []
  deriving Repr, DecidableEq, Inhabited

structure HistoryPoint where
  timeMs : Nat
  value : Int
  deriving Repr, DecidableEq, Inhabited

structure ObservedPoint where
  timeMs : Nat
  accountValue : Int
  pnlValue : Int
  deriving Repr, DecidableEq, Inhabited

structure NormalizedCumulativeRow where
  timeMs : Nat
  percent : Int
  factor : Ratio
  deriving Repr, DecidableEq, Inhabited

def mkRatio (numerator : Int) (denominator : Nat) : Ratio :=
  let denominator' := if denominator = 0 then 1 else denominator
  let divisor := Nat.gcd numerator.natAbs denominator'
  let divisor' := if divisor = 0 then 1 else divisor
  let normalizedDenominator := denominator' / divisor'
  let normalizedAbsNumerator := numerator.natAbs / divisor'
  let normalizedNumerator : Int :=
    if numerator < 0 then
      -(Int.ofNat normalizedAbsNumerator)
    else
      Int.ofNat normalizedAbsNumerator
  { numerator := normalizedNumerator
    denominator := normalizedDenominator }

def insertHistoryRow (row : Nat × Int) : List (Nat × Int) → List (Nat × Int)
  | [] => [row]
  | current :: rest =>
      if row.fst < current.fst then
        row :: current :: rest
      else
        current :: insertHistoryRow row rest

def sortHistoryRows (rows : List (Nat × Int)) : List (Nat × Int) :=
  rows.foldl (fun acc row => insertHistoryRow row acc) []

def dedupeHistoryRowsByTime : List (Nat × Int) → List (Nat × Int)
  | [] => []
  | row :: rest =>
      match dedupeHistoryRowsByTime rest with
      | [] => [row]
      | next :: tail =>
          if row.fst = next.fst then
            next :: tail
          else
            row :: next :: tail

def historyPoints (rows : List (Nat × Int)) : List HistoryPoint :=
  (dedupeHistoryRowsByTime (sortHistoryRows rows)).map fun (timeMs, value) =>
    { timeMs := timeMs
      value := value }


def canonicalHistoryRows (rows : List (Nat × Int)) : List (Nat × Int) :=
  (historyPoints rows).map fun point => (point.timeMs, point.value)


def normalizeSummary (summary : Summary) : Summary :=
  { accountValueHistory := canonicalHistoryRows summary.accountValueHistory
    pnlHistory := canonicalHistoryRows summary.pnlHistory }


def lookupHistoryValue : Nat → List (Nat × Int) → Option Int
  | _, [] => none
  | timeMs, (candidateTimeMs, value) :: rest =>
      if candidateTimeMs = timeMs then
        some value
      else
        lookupHistoryValue timeMs rest


def alignedAccountPnlPoints (summary : Summary) : List ObservedPoint :=
  let normalized := normalizeSummary summary
  normalized.accountValueHistory.filterMap fun (timeMs, accountValue) =>
    match lookupHistoryValue timeMs normalized.pnlHistory with
    | some pnlValue =>
        some { timeMs := timeMs
               accountValue := accountValue
               pnlValue := pnlValue }
    | none => none


def anchoredAccountPnlPoints (summary : Summary) : List ObservedPoint :=
  let rec dropLeading : List ObservedPoint → List ObservedPoint
    | [] => []
    | point :: rest =>
        if point.accountValue > 0 then
          point :: rest
        else
          dropLeading rest
  dropLeading (alignedAccountPnlPoints summary)


def insertPercentRow (row : Nat × Int) : List (Nat × Int) → List (Nat × Int)
  | [] => [row]
  | current :: rest =>
      if row.fst < current.fst then
        row :: current :: rest
      else
        current :: insertPercentRow row rest


def sortPercentRows (rows : List (Nat × Int)) : List (Nat × Int) :=
  rows.foldl (fun acc row => insertPercentRow row acc) []


def dedupePercentRowsByTime : List (Nat × Int) → List (Nat × Int)
  | [] => []
  | row :: rest =>
      match dedupePercentRowsByTime rest with
      | [] => [row]
      | next :: tail =>
          if row.fst = next.fst then
            next :: tail
          else
            row :: next :: tail


def factorFromPercent? (percent : Int) : Option Ratio :=
  if percent + 100 ≥ 0 then
    some (mkRatio (percent + 100) 100)
  else
    none


def normalizeCumulativeRows (rows : List (Nat × Int)) : List NormalizedCumulativeRow :=
  (dedupePercentRowsByTime (sortPercentRows rows)).filterMap fun (timeMs, percent) =>
    match factorFromPercent? percent with
    | some factor =>
        some { timeMs := timeMs
               percent := percent
               factor := factor }
    | none => none


def unsortedDuplicateHistoryRows : List (Nat × Int) :=
  [(3000, 30), (1000, 10), (2000, 20), (2000, 25), (3000, 30)]


def canonicalHistoryRowsInput : List (Nat × Int) :=
  [(1000, 10), (2000, 25), (3000, 30)]


def duplicateJoinSummary : Summary :=
  { accountValueHistory := [(3000, 140), (1000, 100), (2000, 120), (2000, 130)]
    pnlHistory := [(3000, 25), (1000, 0), (2000, 10), (2000, 15), (2500, 99)] }


def exactJoinSummary : Summary :=
  { accountValueHistory := [(1000, 100), (2000, 120), (3000, 140)]
    pnlHistory := [(1000, 0), (2500, 10), (3000, 20)] }


def anchoredSummary : Summary :=
  { accountValueHistory := [(1000, 0), (2000, -10), (3000, 100), (4000, 110)]
    pnlHistory := [(1000, 0), (2000, 0), (3000, 0), (4000, 10)] }


def noPositiveAnchorSummary : Summary :=
  { accountValueHistory := [(1000, 0), (2000, -10)]
    pnlHistory := [(1000, 0), (2000, 5)] }


def duplicateAndLossFilterRows : List (Nat × Int) :=
  [(4000, 33), (3000, -100), (1000, 0), (2000, 10), (2000, 21)]


def negativeRecoveryRows : List (Nat × Int) :=
  [(1000, 0), (2000, -50), (3000, -25)]


theorem surface_id :
    surfaceId surface = "portfolio-returns-normalization" := by
  rfl


theorem manifest_spec :
    surfaceManifest surface =
      "{:surface \"portfolio-returns-normalization\" :module \"Hyperopen.Formal.PortfolioReturnsNormalization\" :status \"modeled\"}\n" := by
  native_decide


theorem history_points_sort_and_last_write_wins :
    historyPoints unsortedDuplicateHistoryRows =
      [{ timeMs := 1000, value := 10 }
      ,{ timeMs := 2000, value := 25 }
      ,{ timeMs := 3000, value := 30 }] := by
  native_decide


theorem canonical_history_rows_are_idempotent_on_canonical_input :
    canonicalHistoryRows canonicalHistoryRowsInput = canonicalHistoryRowsInput := by
  native_decide


theorem aligned_join_uses_exact_timestamps_after_dedupe :
    alignedAccountPnlPoints duplicateJoinSummary =
      [{ timeMs := 1000, accountValue := 100, pnlValue := 0 }
      ,{ timeMs := 2000, accountValue := 130, pnlValue := 15 }
      ,{ timeMs := 3000, accountValue := 140, pnlValue := 25 }] := by
  native_decide


theorem anchor_drops_leading_nonpositive_account_values :
    anchoredAccountPnlPoints anchoredSummary =
      [{ timeMs := 3000, accountValue := 100, pnlValue := 0 }
      ,{ timeMs := 4000, accountValue := 110, pnlValue := 10 }] := by
  native_decide


theorem no_positive_anchor_yields_empty_points :
    anchoredAccountPnlPoints noPositiveAnchorSummary = [] := by
  native_decide


theorem cumulative_normalization_sorts_dedupes_and_keeps_zero_factor :
    normalizeCumulativeRows duplicateAndLossFilterRows =
      [{ timeMs := 1000, percent := 0, factor := mkRatio 1 1 }
      ,{ timeMs := 2000, percent := 21, factor := mkRatio 121 100 }
      ,{ timeMs := 3000, percent := -100, factor := mkRatio 0 1 }
      ,{ timeMs := 4000, percent := 33, factor := mkRatio 133 100 }] := by
  native_decide


def kv (key : String) (value : Clj) : Clj × Clj :=
  (.keyword key, value)


def mapClj (entries : List (Clj × Clj)) : Clj :=
  .arrayMap entries


def vecClj (values : List Clj) : Clj :=
  .vector values


def ratioToClj (value : Ratio) : Clj :=
  mapClj
    [kv "num" (.int value.numerator)
    ,kv "den" (.nat value.denominator)]


def historyRowToClj (row : Nat × Int) : Clj :=
  vecClj [.nat row.fst, .int row.snd]


def summaryToClj (summary : Summary) : Clj :=
  mapClj
    [kv "accountValueHistory" (vecClj (summary.accountValueHistory.map historyRowToClj))
    ,kv "pnlHistory" (vecClj (summary.pnlHistory.map historyRowToClj))]


def historyPointToClj (point : HistoryPoint) : Clj :=
  mapClj
    [kv "time-ms" (.nat point.timeMs)
    ,kv "value" (.int point.value)]


def observedPointToClj (point : ObservedPoint) : Clj :=
  mapClj
    [kv "time-ms" (.nat point.timeMs)
    ,kv "account-value" (.int point.accountValue)
    ,kv "pnl-value" (.int point.pnlValue)]


def normalizedCumulativeRowToClj (row : NormalizedCumulativeRow) : Clj :=
  mapClj
    [kv "time-ms" (.nat row.timeMs)
    ,kv "percent" (.int row.percent)
    ,kv "factor" (ratioToClj row.factor)]


def historyPointVectorToClj (id : String) (rows : List (Nat × Int)) : Clj :=
  mapClj
    [kv "id" (.keyword id)
    ,kv "rows" (vecClj (rows.map historyRowToClj))
    ,kv "expected" (vecClj ((historyPoints rows).map historyPointToClj))]


def alignedSummaryVectorToClj (id : String) (summary : Summary) : Clj :=
  mapClj
    [kv "id" (.keyword id)
    ,kv "summary" (summaryToClj summary)
    ,kv "expected" (vecClj ((alignedAccountPnlPoints summary).map observedPointToClj))]


def anchoredSummaryVectorToClj (id : String) (summary : Summary) : Clj :=
  mapClj
    [kv "id" (.keyword id)
    ,kv "summary" (summaryToClj summary)
    ,kv "expected" (vecClj ((anchoredAccountPnlPoints summary).map observedPointToClj))]


def cumulativeNormalizationVectorToClj (id : String) (rows : List (Nat × Int)) : Clj :=
  mapClj
    [kv "id" (.keyword id)
    ,kv "rows" (vecClj (rows.map historyRowToClj))
    ,kv "expected" (vecClj ((normalizeCumulativeRows rows).map normalizedCumulativeRowToClj))]


def historyPointVectors : Clj :=
  vecClj
    [historyPointVectorToClj "unsorted-duplicates" unsortedDuplicateHistoryRows
    ,historyPointVectorToClj "already-canonical" canonicalHistoryRowsInput]


def alignedSummaryVectors : Clj :=
  vecClj
    [alignedSummaryVectorToClj "duplicate-join" duplicateJoinSummary
    ,alignedSummaryVectorToClj "exact-timestamp-join" exactJoinSummary]


def anchoredSummaryVectors : Clj :=
  vecClj
    [anchoredSummaryVectorToClj "skip-leading-nonpositive-anchor" anchoredSummary
    ,anchoredSummaryVectorToClj "no-positive-anchor" noPositiveAnchorSummary]


def cumulativeNormalizationVectors : Clj :=
  vecClj
    [cumulativeNormalizationVectorToClj "duplicate-and-loss-filter" duplicateAndLossFilterRows
    ,cumulativeNormalizationVectorToClj "negative-recovery" negativeRecoveryRows]


def generatedSource : String :=
  renderNamespace "hyperopen.formal.portfolio-returns-normalization-vectors"
    [("history-point-vectors", historyPointVectors)
    ,("aligned-summary-vectors", alignedSummaryVectors)
    ,("anchored-summary-vectors", anchoredSummaryVectors)
    ,("cumulative-normalization-vectors", cumulativeNormalizationVectors)]


def verify : IO Unit := do
  writeGeneratedSource surface generatedSource


def sync : IO Unit := do
  writeGeneratedSource surface generatedSource

end Hyperopen.Formal.PortfolioReturnsNormalization
