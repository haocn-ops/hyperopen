import Hyperopen.Formal.Common

namespace Hyperopen.Formal.OrderFormOwnership

open Hyperopen.Formal

def surface : Surface := .orderFormOwnership

def kw (value : String) : Clj :=
  .keyword value

def str (value : String) : Clj :=
  .str value

def bool (value : Bool) : Clj :=
  .bool value

def nat (value : Nat) : Clj :=
  .nat value

def nilClj : Clj :=
  .nil

def kv (key : String) (value : Clj) : Clj × Clj :=
  (.keyword key, value)

def mapClj (entries : List (Clj × Clj)) : Clj :=
  .arrayMap entries

def vec (values : List Clj) : Clj :=
  .vector values

def leg (enabled : Bool) (trigger : String) : Clj :=
  mapClj
    [kv "enabled?" (bool enabled)
    ,kv "trigger" (str trigger)
    ,kv "is-market" (bool true)
    ,kv "limit" (str "")]

def legWithOffset (enabled : Bool) (trigger offsetInput : String) : Clj :=
  mapClj
    [kv "enabled?" (bool enabled)
    ,kv "trigger" (str trigger)
    ,kv "offset-input" (str offsetInput)
    ,kv "is-market" (bool true)
    ,kv "limit" (str "")]

def defaultScale : Clj :=
  mapClj
    [kv "start" (str "")
    ,kv "end" (str "")
    ,kv "count" (nat 5)
    ,kv "skew" (str "1.00")]

def defaultTwap : Clj :=
  mapClj
    [kv "hours" (nat 0)
    ,kv "minutes" (nat 30)
    ,kv "randomize" (bool false)]

def defaultTpsl : Clj :=
  mapClj [kv "unit" (kw "usd")]

def baseOrderForm : Clj :=
  mapClj
    [kv "type" (kw "limit")
    ,kv "side" (kw "buy")
    ,kv "size-percent" (nat 0)
    ,kv "size" (str "1")
    ,kv "price" (str "100")
    ,kv "trigger-px" (str "")
    ,kv "reduce-only" (bool false)
    ,kv "post-only" (bool false)
    ,kv "tif" (kw "gtc")
    ,kv "slippage" (str "0.5")
    ,kv "scale" defaultScale
    ,kv "twap" defaultTwap
    ,kv "tpsl" defaultTpsl
    ,kv "tp" (leg false "")
    ,kv "sl" (leg false "")]

def uiOwnedInputForm : Clj :=
  mapClj
    [kv "type" (kw "limit")
    ,kv "side" (kw "buy")
    ,kv "size-percent" (nat 0)
    ,kv "size" (str "1")
    ,kv "price" (str "100")
    ,kv "trigger-px" (str "")
    ,kv "reduce-only" (bool false)
    ,kv "post-only" (bool false)
    ,kv "tif" (kw "gtc")
    ,kv "slippage" (str "0.5")
    ,kv "scale" defaultScale
    ,kv "twap" defaultTwap
    ,kv "tpsl" defaultTpsl
    ,kv "tp" (leg false "")
    ,kv "sl" (leg false "")
    ,kv "entry-mode" (kw "pro")
    ,kv "ui-leverage" (nat 7)
    ,kv "margin-mode" (kw "cross")
    ,kv "size-input-mode" (kw "base")
    ,kv "size-input-source" (kw "percent")
    ,kv "size-display" (str "1.0")
    ,kv "pro-order-type-dropdown-open?" (bool true)
    ,kv "margin-mode-dropdown-open?" (bool true)
    ,kv "leverage-popover-open?" (bool true)
    ,kv "leverage-draft" (nat 11)
    ,kv "size-unit-dropdown-open?" (bool true)
    ,kv "tpsl-unit-dropdown-open?" (bool true)
    ,kv "tif-dropdown-open?" (bool true)
    ,kv "outcome-option-dropdown-open?" (bool true)
    ,kv "outcome-option-query" (str "fra")
    ,kv "price-input-focused?" (bool true)
    ,kv "tpsl-panel-open?" (bool true)
    ,kv "submitting?" (bool true)
    ,kv "error" (str "boom")]

def blockedWriteRuntime : Clj :=
  mapClj
    [kv "submitting?" (bool false)
    ,kv "error" nilClj]

def rawUiOpen : Clj :=
  mapClj
    [kv "pro-order-type-dropdown-open?" (bool true)
    ,kv "margin-mode-dropdown-open?" (bool true)
    ,kv "leverage-popover-open?" (bool true)
    ,kv "size-unit-dropdown-open?" (bool true)
    ,kv "tpsl-unit-dropdown-open?" (bool true)
    ,kv "tif-dropdown-open?" (bool true)
    ,kv "outcome-option-dropdown-open?" (bool false)
    ,kv "outcome-option-query" (str "")
    ,kv "tpsl-panel-open?" (bool true)
    ,kv "price-input-focused?" (bool true)
    ,kv "entry-mode" (kw "pro")
    ,kv "ui-leverage" (nat 20)
    ,kv "leverage-draft" (nat 20)
    ,kv "margin-mode" (kw "cross")
    ,kv "size-input-mode" (kw "quote")
    ,kv "size-input-source" (kw "manual")
    ,kv "size-display" (str "1.25")]

def effectiveMarketUi : Clj :=
  mapClj
    [kv "pro-order-type-dropdown-open?" (bool true)
    ,kv "margin-mode-dropdown-open?" (bool true)
    ,kv "leverage-popover-open?" (bool true)
    ,kv "size-unit-dropdown-open?" (bool true)
    ,kv "tpsl-unit-dropdown-open?" (bool true)
    ,kv "tif-dropdown-open?" (bool false)
    ,kv "outcome-option-dropdown-open?" (bool false)
    ,kv "outcome-option-query" (str "")
    ,kv "tpsl-panel-open?" (bool true)
    ,kv "price-input-focused?" (bool false)
    ,kv "entry-mode" (kw "market")
    ,kv "ui-leverage" (nat 20)
    ,kv "leverage-draft" (nat 20)
    ,kv "margin-mode" (kw "cross")
    ,kv "size-input-mode" (kw "quote")
    ,kv "size-input-source" (kw "manual")
    ,kv "size-display" (str "1.25")]

def effectiveScaleUi : Clj :=
  mapClj
    [kv "pro-order-type-dropdown-open?" (bool true)
    ,kv "margin-mode-dropdown-open?" (bool true)
    ,kv "leverage-popover-open?" (bool true)
    ,kv "size-unit-dropdown-open?" (bool true)
    ,kv "tpsl-unit-dropdown-open?" (bool false)
    ,kv "tif-dropdown-open?" (bool false)
    ,kv "outcome-option-dropdown-open?" (bool false)
    ,kv "outcome-option-query" (str "")
    ,kv "tpsl-panel-open?" (bool false)
    ,kv "price-input-focused?" (bool false)
    ,kv "entry-mode" (kw "pro")
    ,kv "ui-leverage" (nat 20)
    ,kv "leverage-draft" (nat 20)
    ,kv "margin-mode" (kw "cross")
    ,kv "size-input-mode" (kw "quote")
    ,kv "size-input-source" (kw "manual")
    ,kv "size-display" (str "1.25")]

def crossMarginCollapsedUi : Clj :=
  mapClj
    [kv "pro-order-type-dropdown-open?" (bool false)
    ,kv "margin-mode-dropdown-open?" (bool false)
    ,kv "leverage-popover-open?" (bool false)
    ,kv "size-unit-dropdown-open?" (bool false)
    ,kv "tpsl-unit-dropdown-open?" (bool false)
    ,kv "tif-dropdown-open?" (bool false)
    ,kv "outcome-option-dropdown-open?" (bool false)
    ,kv "outcome-option-query" (str "")
    ,kv "tpsl-panel-open?" (bool false)
    ,kv "price-input-focused?" (bool false)
    ,kv "entry-mode" (kw "limit")
    ,kv "ui-leverage" (nat 18)
    ,kv "leverage-draft" (nat 18)
    ,kv "margin-mode" (kw "isolated")
    ,kv "size-input-mode" (kw "quote")
    ,kv "size-input-source" (kw "manual")
    ,kv "size-display" (str "2.0")]

def reduceOnlyTransition : Clj :=
  mapClj
    [kv "order-form"
       (mapClj
         [kv "type" (kw "limit")
         ,kv "side" (kw "buy")
         ,kv "size-percent" (nat 0)
         ,kv "size" (str "1")
         ,kv "price" (str "100")
         ,kv "trigger-px" (str "")
         ,kv "reduce-only" (bool true)
         ,kv "post-only" (bool false)
         ,kv "tif" (kw "gtc")
         ,kv "slippage" (str "0.5")
         ,kv "scale" defaultScale
         ,kv "twap" defaultTwap
         ,kv "tpsl" defaultTpsl
         ,kv "tp" (leg false "110")
         ,kv "sl" (leg false "90")])
    ,kv "order-form-ui"
       (mapClj
         [kv "pro-order-type-dropdown-open?" (bool false)
         ,kv "margin-mode-dropdown-open?" (bool false)
         ,kv "leverage-popover-open?" (bool false)
         ,kv "size-unit-dropdown-open?" (bool false)
         ,kv "tpsl-unit-dropdown-open?" (bool false)
         ,kv "tif-dropdown-open?" (bool false)
         ,kv "outcome-option-dropdown-open?" (bool false)
         ,kv "outcome-option-query" (str "")
         ,kv "tpsl-panel-open?" (bool false)
         ,kv "price-input-focused?" (bool false)
         ,kv "entry-mode" (kw "limit")
         ,kv "ui-leverage" (nat 20)
         ,kv "leverage-draft" (nat 20)
         ,kv "margin-mode" (kw "cross")
         ,kv "size-input-mode" (kw "quote")
         ,kv "size-input-source" (kw "manual")
         ,kv "size-display" (str "1")])
    ,kv "order-form-runtime" blockedWriteRuntime]

def scaleTransition : Clj :=
  mapClj
    [kv "order-form"
       (mapClj
         [kv "type" (kw "scale")
         ,kv "side" (kw "buy")
         ,kv "size-percent" (nat 0)
         ,kv "size" (str "1")
         ,kv "price" (str "100")
         ,kv "trigger-px" (str "")
         ,kv "reduce-only" (bool false)
         ,kv "post-only" (bool false)
         ,kv "tif" (kw "gtc")
         ,kv "slippage" (str "0.5")
         ,kv "scale" defaultScale
         ,kv "twap" defaultTwap
         ,kv "tpsl" defaultTpsl
         ,kv "tp" (leg false "")
         ,kv "sl" (leg false "")])
    ,kv "order-form-ui"
       (mapClj
         [kv "pro-order-type-dropdown-open?" (bool false)
         ,kv "margin-mode-dropdown-open?" (bool false)
         ,kv "leverage-popover-open?" (bool false)
         ,kv "size-unit-dropdown-open?" (bool false)
         ,kv "tpsl-unit-dropdown-open?" (bool false)
         ,kv "tif-dropdown-open?" (bool false)
         ,kv "outcome-option-dropdown-open?" (bool false)
         ,kv "outcome-option-query" (str "")
         ,kv "tpsl-panel-open?" (bool false)
         ,kv "price-input-focused?" (bool false)
         ,kv "entry-mode" (kw "pro")
         ,kv "ui-leverage" (nat 20)
         ,kv "leverage-draft" (nat 20)
         ,kv "margin-mode" (kw "cross")
         ,kv "size-input-mode" (kw "quote")
         ,kv "size-input-source" (kw "manual")
         ,kv "size-display" (str "1")])
    ,kv "order-form-runtime" blockedWriteRuntime]

def leverageClampLow : Clj :=
  mapClj
    [kv "order-form"
       (mapClj [])
    ,kv "order-form-ui"
       (mapClj
         [kv "pro-order-type-dropdown-open?" (bool false)
         ,kv "margin-mode-dropdown-open?" (bool false)
         ,kv "leverage-popover-open?" (bool false)
         ,kv "size-unit-dropdown-open?" (bool false)
         ,kv "tpsl-unit-dropdown-open?" (bool false)
         ,kv "tif-dropdown-open?" (bool false)
         ,kv "outcome-option-dropdown-open?" (bool false)
         ,kv "outcome-option-query" (str "")
         ,kv "tpsl-panel-open?" (bool false)
         ,kv "price-input-focused?" (bool false)
         ,kv "entry-mode" (kw "limit")
         ,kv "ui-leverage" (nat 1)
         ,kv "leverage-draft" (nat 1)
         ,kv "margin-mode" (kw "cross")
         ,kv "size-input-mode" (kw "quote")
         ,kv "size-input-source" (kw "manual")
         ,kv "size-display" (str "1")])
    ,kv "order-form-runtime" blockedWriteRuntime]

def leverageClampHigh : Clj :=
  mapClj
    [kv "order-form"
       (mapClj [])
    ,kv "order-form-ui"
       (mapClj
         [kv "pro-order-type-dropdown-open?" (bool false)
         ,kv "margin-mode-dropdown-open?" (bool false)
         ,kv "leverage-popover-open?" (bool false)
         ,kv "size-unit-dropdown-open?" (bool false)
         ,kv "tpsl-unit-dropdown-open?" (bool false)
         ,kv "tif-dropdown-open?" (bool false)
         ,kv "outcome-option-dropdown-open?" (bool false)
         ,kv "outcome-option-query" (str "")
         ,kv "tpsl-panel-open?" (bool false)
         ,kv "price-input-focused?" (bool false)
         ,kv "entry-mode" (kw "limit")
         ,kv "ui-leverage" (nat 12)
         ,kv "leverage-draft" (nat 12)
         ,kv "margin-mode" (kw "cross")
         ,kv "size-input-mode" (kw "quote")
         ,kv "size-input-source" (kw "manual")
         ,kv "size-display" (str "1")])
    ,kv "order-form-runtime" blockedWriteRuntime]

def tpOffsetCleared : Clj :=
  mapClj
    [kv "order-form"
       (mapClj
         [kv "tp" (legWithOffset true "110" "")
         ,kv "sl" (legWithOffset true "90" "20")])
    ,kv "order-form-runtime" blockedWriteRuntime]

def slOffsetCleared : Clj :=
  mapClj
    [kv "order-form"
       (mapClj
         [kv "tp" (legWithOffset true "110" "20")
         ,kv "sl" (legWithOffset true "90" "")])
    ,kv "order-form-runtime" blockedWriteRuntime]

def unitOffsetCleared : Clj :=
  mapClj
    [kv "order-form"
       (mapClj
         [kv "tpsl" defaultTpsl
         ,kv "tp" (legWithOffset true "110" "")
         ,kv "sl" (legWithOffset true "90" "")])
    ,kv "order-form-runtime" blockedWriteRuntime]

def caseMap (id : String) (input expected : Clj) : Clj :=
  mapClj
    [kv "id" (kw id)
    ,kv "input" input
    ,kv "expected" expected]

def persistedFormVectors : Clj :=
  vec
    [caseMap "ui-owned-fields-stripped" uiOwnedInputForm baseOrderForm
    ,caseMap "already-canonical-form-stays-unchanged"
       baseOrderForm
       baseOrderForm]

def blockedWriteVectors : Clj :=
  vec
    [caseMap "blocked-canonical-write-path"
       (mapClj [kv "path" (vec [kw "entry-mode"]), kv "value" (kw "market")])
       (mapClj [kv "order-form-runtime" blockedWriteRuntime])
    ,caseMap "blocked-runtime-write-path"
       (mapClj [kv "path" (vec [kw "submitting?"]), kv "value" (bool true)])
       (mapClj [kv "order-form-runtime" blockedWriteRuntime])]

def effectiveUiVectors : Clj :=
  vec
    [caseMap "market-closes-price-and-tif-controls"
       (mapClj
         [kv "order-form" (mapClj [kv "type" (kw "market"), kv "ui-leverage" (nat 20)])
         ,kv "order-form-ui" rawUiOpen])
       effectiveMarketUi
    ,caseMap "scale-closes-price-tif-and-tpsl"
       (mapClj
         [kv "order-form" (mapClj [kv "type" (kw "scale"), kv "ui-leverage" (nat 20)])
         ,kv "order-form-ui" rawUiOpen])
       effectiveScaleUi]

def orderFormUiStateVectors : Clj :=
  vec
    [caseMap "cross-margin-disallowed-collapses-to-isolated"
       (mapClj
         [kv "cross-margin-allowed?" (bool false)
         ,kv "order-form" (mapClj [kv "type" (kw "limit"), kv "ui-leverage" (nat 18)])
         ,kv "order-form-ui" (mapClj [kv "margin-mode" (kw "cross"), kv "margin-mode-dropdown-open?" (bool true)])])
       crossMarginCollapsedUi]

def reduceOnlyVectors : Clj :=
  vec
    [caseMap "reduce-only-closes-tpsl-and-disables-legs"
       (mapClj
         [kv "order-form"
            (mapClj
              [kv "type" (kw "limit")
              ,kv "reduce-only" (bool false)
              ,kv "tp" (legWithOffset true "110" "20")
              ,kv "sl" (legWithOffset true "90" "20")])
         ,kv "order-form-ui" (mapClj [kv "tpsl-panel-open?" (bool true)])])
       reduceOnlyTransition]

def scaleVectors : Clj :=
  vec
    [caseMap "scale-forces-tpsl-panel-closed"
       (mapClj
         [kv "order-form"
            (mapClj
              [kv "type" (kw "scale")
              ,kv "reduce-only" (bool false)
              ,kv "tp" (leg false "")
              ,kv "sl" (leg false "")])
         ,kv "order-form-ui" (mapClj [kv "tpsl-panel-open?" (bool true)])])
       scaleTransition]

def leverageVectors : Clj :=
  vec
    [caseMap "leverage-clamps-low-to-one"
       (mapClj
         [kv "market-max-leverage" (nat 12)
         ,kv "order-form" (mapClj [kv "ui-leverage" (nat 0), kv "leverage-draft" (nat 0)])
         ,kv "order-form-ui" (mapClj [kv "leverage-draft" (nat 0)])])
       leverageClampLow
    ,caseMap "leverage-clamps-high-to-market-max"
       (mapClj
         [kv "market-max-leverage" (nat 12)
         ,kv "order-form" (mapClj [kv "ui-leverage" (nat 27), kv "leverage-draft" (nat 27)])
         ,kv "order-form-ui" (mapClj [kv "leverage-draft" (nat 27)])])
       leverageClampHigh]

def offsetCacheVectors : Clj :=
  vec
    [caseMap "tp-trigger-edit-clears-tp-offset-cache"
       (mapClj
         [kv "path" (vec [kw "tp", kw "trigger"])
         ,kv "value" (str "120")
         ,kv "order-form"
            (mapClj
              [kv "tp" (legWithOffset true "110" "20")
              ,kv "sl" (legWithOffset true "90" "20")])])
       tpOffsetCleared
    ,caseMap "sl-trigger-edit-clears-sl-offset-cache"
       (mapClj
         [kv "path" (vec [kw "sl", kw "trigger"])
         ,kv "value" (str "80")
         ,kv "order-form"
            (mapClj
              [kv "tp" (legWithOffset true "110" "20")
              ,kv "sl" (legWithOffset true "90" "20")])])
       slOffsetCleared
    ,caseMap "tpsl-unit-change-clears-both-offset-caches"
       (mapClj
         [kv "path" (vec [kw "tpsl", kw "unit"])
         ,kv "value" (kw "percent")
         ,kv "order-form"
            (mapClj
              [kv "tpsl" defaultTpsl
              ,kv "tp" (legWithOffset true "110" "20")
              ,kv "sl" (legWithOffset true "90" "20")])])
       unitOffsetCleared]

def generatedSource : String :=
  renderNamespace "hyperopen.formal.order-form-ownership-vectors"
    [("base-order-form", baseOrderForm)
    ,("persisted-form-vectors", persistedFormVectors)
    ,("blocked-write-vectors", blockedWriteVectors)
    ,("effective-ui-vectors", effectiveUiVectors)
    ,("order-form-ui-state-vectors", orderFormUiStateVectors)
    ,("reduce-only-vectors", reduceOnlyVectors)
    ,("scale-vectors", scaleVectors)
    ,("leverage-vectors", leverageVectors)
    ,("offset-cache-vectors", offsetCacheVectors)]

theorem surface_id :
    surfaceId surface = "order-form-ownership" := by
  rfl

theorem manifest_spec :
    surfaceManifest surface =
      "{:surface \"order-form-ownership\" :module \"Hyperopen.Formal.OrderFormOwnership\" :status \"modeled\"}\n" := by
  rfl

def verify : IO Unit := do
  writeGeneratedSource surface generatedSource

def sync : IO Unit := do
  writeGeneratedSource surface generatedSource

end Hyperopen.Formal.OrderFormOwnership
