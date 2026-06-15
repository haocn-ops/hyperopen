import Hyperopen.Formal.OrderRequest.Standard

namespace Hyperopen.Formal.OrderRequest.Advanced

open Hyperopen.Formal

abbrev Market := Standard.Market
abbrev Side := Standard.Side
abbrev MarginMode := Standard.MarginMode
abbrev NumericInput := Standard.NumericInput
abbrev Contract := Standard.Contract
abbrev WireOrder := Standard.WireOrder
abbrev UpdateLeverageAction := Standard.UpdateLeverageAction

def surface : Surface := .orderRequestAdvanced

def twapMinRuntimeMinutes : Nat := 5
def twapMaxRuntimeMinutes : Nat := 1440
def twapFrequencySeconds : Nat := 30

inductive AdvancedOrderType where
  | scale
  | twap
  deriving Repr, DecidableEq, Inhabited

inductive SkewInput where
  | text (value : String)
  | front
  | even
  | back
  deriving Repr, DecidableEq, Inhabited

structure ScaleSpec where
  start : String := ""
  finish : String := ""
  count : Nat := 0
  skew : SkewInput := .text "1.00"
  deriving Repr, DecidableEq, Inhabited

structure TwapSpec where
  hours : Option NumericInput := none
  minutes : NumericInput := .nat 0
  randomize : Bool := false
  deriving Repr, DecidableEq, Inhabited

structure Context where
  activeAsset : Option String := none
  assetIdx : Option Nat := none
  market : Market := default
  deriving Repr, DecidableEq, Inhabited

structure Form where
  orderType : AdvancedOrderType := .scale
  side : Side := .buy
  size : String := ""
  reduceOnly : Bool := false
  postOnly : Bool := false
  scale : ScaleSpec := default
  twap : TwapSpec := default
  uiLeverage : Option NumericInput := none
  marginMode : MarginMode := .cross
  deriving Repr, DecidableEq, Inhabited

structure TwapAction where
  asset : Nat
  isBuy : Bool
  size : String
  reduceOnly : Bool
  minutes : Nat
  randomize : Bool
  deriving Repr, DecidableEq, Inhabited

inductive AdvancedRequestPayload where
  | scale (orders : List WireOrder)
  | twap (action : TwapAction)
  deriving Repr, DecidableEq, Inhabited

structure AdvancedRequest where
  payload : AdvancedRequestPayload
  assetIdx : Nat
  preActions : List UpdateLeverageAction := []
  deriving Repr, DecidableEq, Inhabited

structure AdvancedVector where
  id : String
  contract : Contract
  context : Context
  form : Form
  expected : Option AdvancedRequest
  deriving Repr, DecidableEq, Inhabited

structure Ratio where
  numerator : Int
  denominator : Nat
  deriving Repr, DecidableEq, Inhabited

structure ScaleLeg where
  price : Ratio
  size : Ratio
  deriving Repr, DecidableEq, Inhabited

def btcPerpContext : Context :=
  { activeAsset := some "BTC"
    assetIdx := some 5
    market := { marketType := some Standard.MarketType.perp
                szDecimals := 4 } }

def missingActiveAssetContext : Context :=
  { activeAsset := none
    assetIdx := some 5
    market := { marketType := some Standard.MarketType.perp
                szDecimals := 4 } }

def btcPerpTwoDpContext : Context :=
  { activeAsset := some "BTC"
    assetIdx := some 5
    market := { marketType := some Standard.MarketType.perp
                szDecimals := 2 } }

def spotPurrContext : Context :=
  { activeAsset := some "PURR/USDC"
    -- Spot wire asset id = 10000 + spotMeta pair index (PURR/USDC pair 0).
    -- Mirror real spot markets, which carry the encoded id on :asset-id.
    assetIdx := some 10000
    market := { marketType := some Standard.MarketType.spot
                coin := some "PURR/USDC"
                szDecimals := 0
                assetId := some 10000 } }

def mkRatio (numerator : Int) (denominator : Nat) : Ratio :=
  { numerator := numerator
    denominator := if denominator = 0 then 1 else denominator }

def ratioZero : Ratio := mkRatio 0 1

def ratioOne : Ratio := mkRatio 1 1

def ratioOfNat (value : Nat) : Ratio :=
  mkRatio (Int.ofNat value) 1

def ratioAdd (left right : Ratio) : Ratio :=
  mkRatio
    (left.numerator * Int.ofNat right.denominator +
      right.numerator * Int.ofNat left.denominator)
    (left.denominator * right.denominator)

def ratioSub (left right : Ratio) : Ratio :=
  mkRatio
    (left.numerator * Int.ofNat right.denominator -
      right.numerator * Int.ofNat left.denominator)
    (left.denominator * right.denominator)

def ratioMul (left right : Ratio) : Ratio :=
  mkRatio
    (left.numerator * right.numerator)
    (left.denominator * right.denominator)

def ratioDiv? (left right : Ratio) : Option Ratio :=
  if right.numerator = 0 then
    none
  else
    let sign : Int := if right.numerator < 0 then -1 else 1
    some <|
      mkRatio
        (left.numerator * Int.ofNat right.denominator * sign)
        (left.denominator * right.numerator.natAbs)

def ratioDivNat? (value : Ratio) (divisor : Nat) : Option Ratio :=
  if divisor = 0 then
    none
  else
    some <| mkRatio value.numerator (value.denominator * divisor)

def ratioLe (left right : Ratio) : Bool :=
  left.numerator * Int.ofNat right.denominator ≤
    right.numerator * Int.ofNat left.denominator

def ratioLt (left right : Ratio) : Bool :=
  left.numerator * Int.ofNat right.denominator <
    right.numerator * Int.ofNat left.denominator

def ratioPositive (value : Ratio) : Bool :=
  ratioLt ratioZero value

def pow10 : Nat → Nat
  | 0 => 1
  | n + 1 => 10 * pow10 n

def ratioFromDecimal (value : Standard.Decimal) : Ratio :=
  let wholeValue := (Standard.parseNatDigits? value.wholeDigits).getD 0
  let fractionValue := (Standard.parseNatDigits? value.fractionDigits).getD 0
  let denominator := pow10 value.fractionDigits.length
  mkRatio
    (Int.ofNat (wholeValue * denominator + fractionValue))
    denominator

def parsePositiveRatio? (value : String) : Option Ratio := do
  let decimal ← Standard.parsePositiveDecimal? value
  pure (ratioFromDecimal decimal)

def normalizeScaleOrderCount (count : Nat) : Nat :=
  max 2 (min 100 count)

def validScaleSkew? (skew : SkewInput) : Option Ratio :=
  match skew with
  | .front => some (mkRatio 1 2)
  | .even => some ratioOne
  | .back => some (mkRatio 2 1)
  | .text text =>
      match Standard.parsePositiveDecimal? text with
      | none => none
      | some decimal =>
          let ratio := ratioFromDecimal decimal
          if ratioLe ratio (ratioOfNat 100) then
            some ratio
          else
            none

def ratioFloorToDecimals? (value : Ratio) (decimals : Nat) : Option Ratio :=
  if value.numerator < 0 then
    none
  else
    let factor := pow10 decimals
    let numerator := Int.toNat value.numerator
    let floored := (numerator * factor) / value.denominator
    some <| mkRatio (Int.ofNat floored) factor

def digitChar (digit : Nat) : Char :=
  Char.ofNat ('0'.toNat + digit)

def natToDigits (value : Nat) : List Char :=
  (toString value).toList

def fractionDigitsLoop (denominator remainder digitsLeft : Nat) (accRev : List Char) :
    Option (List Char) :=
  if remainder = 0 then
    some accRev.reverse
  else
    match digitsLeft with
    | 0 => none
    | digitsLeft' + 1 =>
        let remainderTimesTen := remainder * 10
        let digit := remainderTimesTen / denominator
        let nextRemainder := remainderTimesTen % denominator
        fractionDigitsLoop denominator nextRemainder digitsLeft' (digitChar digit :: accRev)

def positiveRatioToDecimalString? (value : Ratio) (maxDigits : Nat := 16) : Option String :=
  if value.numerator < 0 then
    none
  else
    let numerator := Int.toNat value.numerator
    let whole := numerator / value.denominator
    let remainder := numerator % value.denominator
    let fractionDigits? := fractionDigitsLoop value.denominator remainder maxDigits []
    match fractionDigits? with
    | none => none
    | some fractionDigits =>
        some <|
          Standard.cleanDecimalString
            { wholeDigits := natToDigits whole
              fractionDigits := fractionDigits }

def roundedRatioToDecimalString? (value : Ratio) (decimals : Nat) : Option String :=
  if value.numerator < 0 then
    none
  else
    let factor := pow10 decimals
    let numerator := Int.toNat value.numerator
    let scaled := numerator * factor
    let quotient := scaled / value.denominator
    let remainder := scaled % value.denominator
    let rounded :=
      if remainder * 2 < value.denominator then
        quotient
      else
        quotient + 1
    let whole := rounded / factor
    let fractional := rounded % factor
    let fractionalDigits :=
      let digits := natToDigits fractional
      let padding := List.replicate (decimals - digits.length) '0'
      if decimals = 0 then [] else padding ++ digits
    some <|
      Standard.cleanDecimalString
        { wholeDigits := natToDigits whole
          fractionDigits := fractionalDigits }

def fallbackRoundedPriceDecimals (value : Ratio) : Nat :=
  let whole := (Int.toNat value.numerator) / value.denominator
  let significantWholeDigits := if whole = 0 then 0 else (natToDigits whole).length
  max 1 (16 - significantWholeDigits)

def positiveRatioToWireString? (value : Ratio) : Option String := do
  match positiveRatioToDecimalString? value with
  | some exact => some exact
  | none => roundedRatioToDecimalString? value (fallbackRoundedPriceDecimals value)

def parseNonnegativeInt? : NumericInput → Option Nat
  | .nat value => some value
  | .text value =>
      match Standard.parseDecimal? value with
      | none => none
      | some decimal =>
          Standard.parseNatDigits? (Standard.trimLeadingZeroDigits decimal.wholeDigits)

def twapTotalMinutes? (twap : TwapSpec) : Option Nat := do
  let minutes ← parseNonnegativeInt? twap.minutes
  match twap.hours with
  | some hoursInput =>
      let hours ← parseNonnegativeInt? hoursInput
      some (hours * 60 + minutes)
  | none => some minutes

def validTwapRuntime (minutes : Nat) : Bool :=
  twapMinRuntimeMinutes ≤ minutes && minutes ≤ twapMaxRuntimeMinutes

def twapSuborderCount (minutes : Nat) : Nat :=
  1 + ((60 * minutes) / twapFrequencySeconds)

def twapSuborderNotional? (size : String) (minutes : Nat) (referencePrice : String) : Option Ratio := do
  let sizeRatio ← parsePositiveRatio? size
  let referencePriceRatio ← parsePositiveRatio? referencePrice
  let count := twapSuborderCount minutes
  let suborderSize ← ratioDivNat? sizeRatio count
  pure (ratioMul suborderSize referencePriceRatio)

def leverageContext (context : Context) : Standard.Context :=
  { activeAsset := context.activeAsset.getD ((context.market.coin).getD "")
    assetIdx := context.assetIdx
    market := context.market }

def buildUpdateLeverageAction (context : Context) (form : Form) : Option UpdateLeverageAction :=
  Standard.buildUpdateLeverageAction
    (leverageContext context)
    { uiLeverage := form.uiLeverage
      marginMode := form.marginMode }

def buildScaleLegs? (context : Context) (form : Form) : Option (List ScaleLeg) := do
  let totalSize ← parsePositiveRatio? form.size
  let startPrice ← parsePositiveRatio? form.scale.start
  let endPrice ← parsePositiveRatio? form.scale.finish
  let skew ← validScaleSkew? form.scale.skew
  if form.scale.count ≤ 1 then
    none
  else
    let orderCount := normalizeScaleOrderCount form.scale.count
    let n := ratioOfNat orderCount
    let startWeightDenominator := ratioMul n (ratioAdd ratioOne skew)
    let startWeight ← ratioDiv? (ratioOfNat 2) startWeightDenominator
    let weightStep ← ratioDivNat? (ratioMul startWeight (ratioSub skew ratioOne)) (orderCount - 1)
    let priceStep ← ratioDivNat? (ratioSub endPrice startPrice) (orderCount - 1)
    let szDecimals := context.market.szDecimals
    let buildLeg (index : Nat) : Option ScaleLeg := do
      let weight := ratioAdd startWeight (ratioMul weightStep (ratioOfNat index))
      let rawSize := ratioMul totalSize weight
      let flooredSize ← ratioFloorToDecimals? rawSize szDecimals
      let price := ratioAdd startPrice (ratioMul priceStep (ratioOfNat index))
      if ratioPositive price then
        some { price := price, size := flooredSize }
      else
        none
    (List.range orderCount).mapM buildLeg

def spotMarket (context : Context) : Bool :=
  match context.market.marketType with
  | some Standard.MarketType.spot => true
  | some Standard.MarketType.perp => false
  | none =>
      match context.market.coin with
      | some coin => Standard.containsSlash coin
      | none =>
          match context.activeAsset with
          | some asset => Standard.containsSlash asset
          | none => false

-- Reduce-only is perp-only; spot markets force it false (mirrors the CLJS
-- builders' spot gate).
def effectiveReduceOnly (context : Context) (form : Form) : Bool :=
  if spotMarket context then false else form.reduceOnly

def scaleOrders? (context : Context) (form : Form) : Option (List WireOrder) := do
  let assetIdx ← context.assetIdx
  let legs ← buildScaleLegs? context form
  let tif := if form.postOnly then "Alo" else "Gtc"
  let reduceOnly := Standard.reduceOnlyFlag (effectiveReduceOnly context form)
  let toOrder (leg : ScaleLeg) : Option WireOrder := do
    let priceText ← positiveRatioToWireString? leg.price
    let sizeText ← positiveRatioToWireString? leg.size
    some
      { asset := assetIdx
        isBuy := Standard.sideIsBuy form.side
        price := priceText
        size := sizeText
        reduceOnly := reduceOnly
        terms := Standard.OrderTerms.limit tif }
  legs.mapM toOrder

def buildScaleRequest (context : Context) (form : Form) : Option AdvancedRequest := do
  let assetIdx ← context.assetIdx
  let orders ← scaleOrders? context form
  some { payload := .scale orders
         assetIdx := assetIdx }

def buildTwapRequest (context : Context) (form : Form) : Option AdvancedRequest := do
  let activeAsset ← context.activeAsset
  let _ : Unit := if activeAsset.isEmpty then () else ()
  let assetIdx ← context.assetIdx
  let sizeText ← Standard.normalizedSizeText? form.size
  let minutes ← twapTotalMinutes? form.twap
  if validTwapRuntime minutes then
    some
      { payload :=
          .twap
            { asset := assetIdx
              isBuy := Standard.sideIsBuy form.side
              size := sizeText
              reduceOnly := effectiveReduceOnly context form
              minutes := minutes
              randomize := form.twap.randomize }
        assetIdx := assetIdx }
  else
    none

def buildAdvancedRequest (context : Context) (form : Form) : Option AdvancedRequest :=
  let baseRequest :=
    match form.orderType with
    | .scale => buildScaleRequest context form
    | .twap => buildTwapRequest context form
  match baseRequest with
  | none => none
  | some request =>
      match buildUpdateLeverageAction context form with
      | some preAction => some { request with preActions := [preAction] }
      | none => some request

def scaleOrderRequestForm : Form :=
  { orderType := .scale
    side := Standard.Side.sell
    size := "9"
    reduceOnly := true
    postOnly := true
    scale := { start := "100"
               finish := "90"
               count := 3
               skew := .text "1.00" } }

def twapOrderRequestForm : Form :=
  { orderType := .twap
    side := Standard.Side.sell
    size := "3"
    reduceOnly := true
    twap := { hours := some (.nat 1)
              minutes := .nat 30
              randomize := false } }

def legacyTwapMinutesForm : Form :=
  { orderType := .twap
    side := Standard.Side.buy
    size := "2"
    reduceOnly := false
    twap := { hours := none
              minutes := .text "15"
              randomize := true } }

def scaleFlooringForm : Form :=
  { orderType := .scale
    side := Standard.Side.buy
    size := "1"
    reduceOnly := false
    postOnly := false
    scale := { start := "100"
               finish := "90"
               count := 3
               skew := .text "1.00" } }

def scaleRepeatingPriceStepForm : Form :=
  { orderType := .scale
    side := Standard.Side.buy
    size := "12"
    reduceOnly := false
    postOnly := false
    scale := { start := "100"
               finish := "99"
               count := 4
               skew := .text "1.00" } }

def twapSuborderTooSmallBuilderForm : Form :=
  { orderType := .twap
    side := Standard.Side.buy
    size := "1"
    reduceOnly := false
    twap := { hours := some (.nat 0)
              minutes := .nat 30
              randomize := false } }

def scaleInvalidSizeForm : Form :=
  { orderType := .scale
    side := Standard.Side.buy
    size := "0"
    scale := { start := "100"
               finish := "90"
               count := 3
               skew := .text "1.00" } }

def scaleCountTooSmallForm : Form :=
  { orderType := .scale
    side := Standard.Side.buy
    size := "1"
    scale := { start := "100"
               finish := "90"
               count := 1
               skew := .text "1.00" } }

def twapInvalidRuntimeForm : Form :=
  { orderType := .twap
    side := Standard.Side.buy
    size := "1"
    twap := { hours := some (.nat 0)
              minutes := .nat 4
              randomize := true } }

def twapMissingActiveAssetForm : Form :=
  { orderType := .twap
    side := Standard.Side.buy
    size := "1"
    twap := { hours := some (.nat 0)
              minutes := .nat 15
              randomize := true } }

def spotScaleForm : Form :=
  -- reduceOnly := true must be forced false for a spot scale ladder.
  { orderType := .scale
    side := Standard.Side.buy
    size := "9"
    reduceOnly := true
    postOnly := false
    scale := { start := "100"
               finish := "90"
               count := 3
               skew := .text "1.00" } }

def spotTwapForm : Form :=
  -- reduceOnly := true must be forced false for a spot TWAP.
  { orderType := .twap
    side := Standard.Side.buy
    size := "3"
    reduceOnly := true
    twap := { hours := some (.nat 1)
              minutes := .nat 30
              randomize := false } }

def advancedVectors : List AdvancedVector :=
  [ { id := "scale-order-request"
      contract := .submitReady
      context := btcPerpContext
      form := scaleOrderRequestForm
      expected := buildAdvancedRequest btcPerpContext scaleOrderRequestForm }
  , { id := "twap-order-request"
      contract := .submitReady
      context := btcPerpContext
      form := twapOrderRequestForm
      expected := buildAdvancedRequest btcPerpContext twapOrderRequestForm }
  , { id := "legacy-twap-minutes-request"
      contract := .submitReady
      context := btcPerpContext
      form := legacyTwapMinutesForm
      expected := buildAdvancedRequest btcPerpContext legacyTwapMinutesForm }
  , { id := "scale-flooring-drops-remainder"
      contract := .submitReady
      context := btcPerpContext
      form := scaleFlooringForm
      expected := buildAdvancedRequest btcPerpContext scaleFlooringForm }
  , { id := "scale-repeating-price-step-request"
      contract := .submitReady
      context := btcPerpContext
      form := scaleRepeatingPriceStepForm
      expected := buildAdvancedRequest btcPerpContext scaleRepeatingPriceStepForm }
  , { id := "twap-suborder-too-small-still-builds"
      contract := .rawBuilder
      context := btcPerpContext
      form := twapSuborderTooSmallBuilderForm
      expected := buildAdvancedRequest btcPerpContext twapSuborderTooSmallBuilderForm }
  , { id := "scale-invalid-fails-closed"
      contract := .rawBuilder
      context := btcPerpContext
      form := scaleInvalidSizeForm
      expected := buildAdvancedRequest btcPerpContext scaleInvalidSizeForm }
  , { id := "scale-count-too-small-fails-closed"
      contract := .rawBuilder
      context := btcPerpContext
      form := scaleCountTooSmallForm
      expected := buildAdvancedRequest btcPerpContext scaleCountTooSmallForm }
  , { id := "twap-invalid-runtime-fails-closed"
      contract := .rawBuilder
      context := btcPerpContext
      form := twapInvalidRuntimeForm
      expected := buildAdvancedRequest btcPerpContext twapInvalidRuntimeForm }
  , { id := "twap-missing-active-asset-fails-closed"
      contract := .rawBuilder
      context := missingActiveAssetContext
      form := twapMissingActiveAssetForm
      expected := buildAdvancedRequest missingActiveAssetContext twapMissingActiveAssetForm }
  , { id := "spot-scale-encoded-asset-id-suppresses-reduce-only"
      contract := .submitReady
      context := spotPurrContext
      form := spotScaleForm
      expected := buildAdvancedRequest spotPurrContext spotScaleForm }
  , { id := "spot-twap-encoded-asset-id-suppresses-reduce-only"
      contract := .submitReady
      context := spotPurrContext
      form := spotTwapForm
      expected := buildAdvancedRequest spotPurrContext spotTwapForm } ]

def advancedOrderTypeKeyword : AdvancedOrderType → String
  | .scale => "scale"
  | .twap => "twap"

def skewToClj : SkewInput → Clj
  | .text value => .str value
  | .front => .keyword "front"
  | .even => .keyword "even"
  | .back => .keyword "back"

def contextToClj (context : Context) : Clj :=
  .arrayMap <|
    [(.keyword "active-asset",
      match context.activeAsset with
      | some activeAsset => .str activeAsset
      | none => .nil)] ++
      (match context.assetIdx with
       | some assetIdx => [(.keyword "asset-idx", .nat assetIdx)]
       | none => []) ++
      [(.keyword "market", Standard.marketToClj context.market)]

def scaleSpecToClj (scale : ScaleSpec) : Clj :=
  .arrayMap
    [(.keyword "start", .str scale.start)
    ,(.keyword "end", .str scale.finish)
    ,(.keyword "count", .nat scale.count)
    ,(.keyword "skew", skewToClj scale.skew)]

def twapSpecToClj (twap : TwapSpec) : Clj :=
  .arrayMap <|
    (match twap.hours with
     | some hours => [(.keyword "hours", Standard.numericInputToClj hours)]
     | none => []) ++
      [(.keyword "minutes", Standard.numericInputToClj twap.minutes)
      ,(.keyword "randomize", .bool twap.randomize)]

def formToClj (form : Form) : Clj :=
  .arrayMap <|
    [(.keyword "type", .keyword (advancedOrderTypeKeyword form.orderType))
    ,(.keyword "side", .keyword (Standard.sideKeyword form.side))
    ,(.keyword "size", .str form.size)] ++
      (if form.reduceOnly then [(.keyword "reduce-only", .bool true)] else []) ++
      (if form.postOnly then [(.keyword "post-only", .bool true)] else []) ++
      (match form.orderType with
       | .scale => [(.keyword "scale", scaleSpecToClj form.scale)]
       | .twap => [(.keyword "twap", twapSpecToClj form.twap)]) ++
      (match form.uiLeverage with
       | some leverage => [(.keyword "ui-leverage", Standard.numericInputToClj leverage)]
       | none => []) ++
      (if form.uiLeverage.isSome || form.marginMode ≠ Standard.MarginMode.cross then
         [(.keyword "margin-mode", .keyword (Standard.marginModeKeyword form.marginMode))]
       else
         [])

def twapActionToClj (action : TwapAction) : Clj :=
  .arrayMap
    [(.keyword "type", .str "twapOrder")
    ,(.keyword "twap",
      .arrayMap
        [(.keyword "a", .nat action.asset)
        ,(.keyword "b", .bool action.isBuy)
        ,(.keyword "s", .str action.size)
        ,(.keyword "r", .bool action.reduceOnly)
        ,(.keyword "m", .nat action.minutes)
        ,(.keyword "t", .bool action.randomize)])]

def requestToClj (request : AdvancedRequest) : Clj :=
  match request.payload with
  | .scale orders =>
      .arrayMap <|
        [(.keyword "action",
          .arrayMap
            [(.keyword "type", .str "order")
            ,(.keyword "orders", .vector (orders.map Standard.wireOrderToClj))
            ,(.keyword "grouping", .str "na")])
        ,(.keyword "asset-idx", .nat request.assetIdx)
        ,(.keyword "orders", .vector (orders.map Standard.wireOrderToClj))] ++
          (if request.preActions.isEmpty then
             []
           else
             [(.keyword "pre-actions",
               .vector (request.preActions.map Standard.updateLeverageActionToClj))])
  | .twap action =>
      .arrayMap <|
        [(.keyword "action", twapActionToClj action)
        ,(.keyword "asset-idx", .nat request.assetIdx)] ++
          (if request.preActions.isEmpty then
             []
           else
             [(.keyword "pre-actions",
               .vector (request.preActions.map Standard.updateLeverageActionToClj))])

def expectedToClj : Option AdvancedRequest → Clj
  | some request => requestToClj request
  | none => .nil

def vectorEntryToClj (vector : AdvancedVector) : Clj :=
  .arrayMap
    [(.keyword "id", .keyword vector.id)
    ,(.keyword "contract", .keyword (Standard.contractKeyword vector.contract))
    ,(.keyword "context", contextToClj vector.context)
    ,(.keyword "form", formToClj vector.form)
    ,(.keyword "expected", expectedToClj vector.expected)]

def scaleFlooringExampleRequest : Option AdvancedRequest :=
  buildAdvancedRequest btcPerpContext scaleFlooringForm

def twapSuborderTooSmallRequest : Option AdvancedRequest :=
  buildAdvancedRequest btcPerpContext twapSuborderTooSmallBuilderForm

theorem surface_id :
    surfaceId surface = "order-request-advanced" := by
  rfl

theorem manifest_spec :
    surfaceManifest surface =
      "{:surface \"order-request-advanced\" :module \"Hyperopen.Formal.OrderRequest.Advanced\" :status \"modeled\"}\n" := by
  rfl

theorem scale_order_request_matches_expected_shape :
    buildAdvancedRequest btcPerpContext scaleOrderRequestForm =
      some { payload :=
               .scale
                 [{ asset := 5
                    isBuy := false
                    price := "100"
                    size := "3"
                    reduceOnly := some true
                    terms := Standard.OrderTerms.limit "Alo" }
                 ,{ asset := 5
                    isBuy := false
                    price := "95"
                    size := "3"
                    reduceOnly := some true
                    terms := Standard.OrderTerms.limit "Alo" }
                 ,{ asset := 5
                    isBuy := false
                    price := "90"
                    size := "3"
                    reduceOnly := some true
                    terms := Standard.OrderTerms.limit "Alo" }]
             assetIdx := 5
             preActions := [] } := by
  native_decide

theorem scale_flooring_drops_remainder :
    buildAdvancedRequest btcPerpContext scaleFlooringForm =
      some { payload :=
               .scale
                 [{ asset := 5
                    isBuy := true
                    price := "100"
                    size := "0.3333"
                    reduceOnly := none
                    terms := Standard.OrderTerms.limit "Gtc" }
                 ,{ asset := 5
                    isBuy := true
                    price := "95"
                    size := "0.3333"
                    reduceOnly := none
                    terms := Standard.OrderTerms.limit "Gtc" }
                 ,{ asset := 5
                    isBuy := true
                    price := "90"
                    size := "0.3333"
                    reduceOnly := none
                    terms := Standard.OrderTerms.limit "Gtc" }]
             assetIdx := 5
             preActions := [] } := by
  native_decide

theorem scale_repeating_price_step_uses_float_boundary_rendering :
    buildAdvancedRequest btcPerpContext scaleRepeatingPriceStepForm =
      some { payload :=
               .scale
                 [{ asset := 5
                    isBuy := true
                    price := "100"
                    size := "3"
                    reduceOnly := none
                    terms := Standard.OrderTerms.limit "Gtc" }
                 ,{ asset := 5
                    isBuy := true
                    price := "99.66666666666667"
                    size := "3"
                    reduceOnly := none
                    terms := Standard.OrderTerms.limit "Gtc" }
                 ,{ asset := 5
                    isBuy := true
                    price := "99.33333333333333"
                    size := "3"
                    reduceOnly := none
                    terms := Standard.OrderTerms.limit "Gtc" }
                 ,{ asset := 5
                    isBuy := true
                    price := "99"
                    size := "3"
                    reduceOnly := none
                    terms := Standard.OrderTerms.limit "Gtc" }]
             assetIdx := 5
             preActions := [] } := by
  native_decide

theorem twap_total_minutes_and_suborder_formula :
    twapTotalMinutes? twapOrderRequestForm.twap = some 90 ∧
      twapSuborderCount 90 = 181 := by
  native_decide

theorem twap_suborder_too_small_builder_still_emits_request :
    buildAdvancedRequest btcPerpContext twapSuborderTooSmallBuilderForm =
      some { payload :=
               .twap
                 { asset := 5
                   isBuy := true
                   size := "1"
                   reduceOnly := false
                   minutes := 30
                   randomize := false }
             assetIdx := 5
             preActions := [] } := by
  native_decide

theorem twap_missing_active_asset_fails_closed :
    buildAdvancedRequest missingActiveAssetContext twapMissingActiveAssetForm = none := by
  native_decide

def generatedSource : String :=
  renderNamespace "hyperopen.formal.order-request-advanced-vectors"
    [("btc-perp-context", contextToClj btcPerpContext)
    ,("spot-purr-context", contextToClj spotPurrContext)
    ,("order-request-advanced-vectors", .vector (advancedVectors.map vectorEntryToClj))]

def verify : IO Unit := do
  writeGeneratedSource surface generatedSource

def sync : IO Unit := do
  writeGeneratedSource surface generatedSource

end Hyperopen.Formal.OrderRequest.Advanced
