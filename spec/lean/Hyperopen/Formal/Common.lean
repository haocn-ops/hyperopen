namespace Hyperopen.Formal

inductive Surface where
  | vaultTransfer
  | orderRequestStandard
  | orderRequestAdvanced
  | effectOrderContract
  | portfolioReturnsEstimator
  | portfolioReturnsNormalization
  | tradingSubmitPolicy
  | orderFormOwnership
  deriving Repr, DecidableEq, Inhabited

inductive Command where
  | verify
  | sync
  deriving Repr, DecidableEq, Inhabited

structure Invocation where
  command : Command
  surface : Surface
  deriving Repr

inductive Clj where
  | nil
  | bool (value : Bool)
  | nat (value : Nat)
  | int (value : Int)
  | str (value : String)
  | keyword (value : String)
  | symbol (value : String)
  | vector (values : List Clj)
  | arrayMap (entries : List (Clj × Clj))
  deriving Repr, Inhabited

def surfaceId : Surface → String
  | .vaultTransfer => "vault-transfer"
  | .orderRequestStandard => "order-request-standard"
  | .orderRequestAdvanced => "order-request-advanced"
  | .effectOrderContract => "effect-order-contract"
  | .portfolioReturnsEstimator => "portfolio-returns-estimator"
  | .portfolioReturnsNormalization => "portfolio-returns-normalization"
  | .tradingSubmitPolicy => "trading-submit-policy"
  | .orderFormOwnership => "order-form-ownership"

def surfaceModuleName : Surface → String
  | .vaultTransfer => "Hyperopen.Formal.VaultTransfer"
  | .orderRequestStandard => "Hyperopen.Formal.OrderRequest.Standard"
  | .orderRequestAdvanced => "Hyperopen.Formal.OrderRequest.Advanced"
  | .effectOrderContract => "Hyperopen.Formal.EffectOrderContract"
  | .portfolioReturnsEstimator => "Hyperopen.Formal.PortfolioReturnsEstimator"
  | .portfolioReturnsNormalization => "Hyperopen.Formal.PortfolioReturnsNormalization"
  | .tradingSubmitPolicy => "Hyperopen.Formal.TradingSubmitPolicy"
  | .orderFormOwnership => "Hyperopen.Formal.OrderFormOwnership"

def surfaceStatus : Surface → String
  | .vaultTransfer => "modeled"
  | .orderRequestStandard => "modeled"
  | .orderRequestAdvanced => "modeled"
  | .effectOrderContract => "modeled"
  | .portfolioReturnsEstimator => "modeled"
  | .portfolioReturnsNormalization => "modeled"
  | .tradingSubmitPolicy => "modeled"
  | .orderFormOwnership => "modeled"

def surfaceManifestPath : Surface → String :=
  fun surface => "../../tools/formal/generated/" ++ surfaceId surface ++ ".edn"

def surfaceGeneratedSourcePath? : Surface → Option String
  | .vaultTransfer => some "../../target/formal/vault-transfer-vectors.cljs"
  | .orderRequestStandard => some "../../target/formal/order-request-standard-vectors.cljs"
  | .orderRequestAdvanced => some "../../target/formal/order-request-advanced-vectors.cljs"
  | .effectOrderContract => some "../../target/formal/effect-order-contract-vectors.cljs"
  | .portfolioReturnsEstimator => some "../../target/formal/portfolio-returns-estimator-vectors.cljs"
  | .portfolioReturnsNormalization => some "../../target/formal/portfolio-returns-normalization-vectors.cljs"
  | .tradingSubmitPolicy => some "../../target/formal/trading-submit-policy-vectors.cljs"
  | .orderFormOwnership => some "../../target/formal/order-form-ownership-vectors.cljs"

def surfaceManifest : Surface → String :=
  fun surface =>
    "{:surface \"" ++ surfaceId surface ++ "\" :module \"" ++ surfaceModuleName surface ++
      "\" :status \"" ++ surfaceStatus surface ++ "\"}\n"

def parseSurface? : String → Option Surface
  | "vault-transfer" => some .vaultTransfer
  | "order-request-standard" => some .orderRequestStandard
  | "order-request-advanced" => some .orderRequestAdvanced
  | "effect-order-contract" => some .effectOrderContract
  | "portfolio-returns-estimator" => some .portfolioReturnsEstimator
  | "portfolio-returns-normalization" => some .portfolioReturnsNormalization
  | "trading-submit-policy" => some .tradingSubmitPolicy
  | "order-form-ownership" => some .orderFormOwnership
  | _ => none

def commandId : Command → String
  | .verify => "verify"
  | .sync => "sync"

def parseCommand? : String → Option Command
  | "verify" => some .verify
  | "sync" => some .sync
  | _ => none

def usage : String :=
  "Usage: formal <verify|sync> --surface <vault-transfer|order-request-standard|order-request-advanced|effect-order-contract|portfolio-returns-estimator|portfolio-returns-normalization|trading-submit-policy|order-form-ownership>"

def parseInvocation : List String → Except String Invocation
  | [] => Except.error usage
  | "help" :: _ => Except.error usage
  | "--help" :: _ => Except.error usage
  | command :: tail =>
      match parseCommand? command with
      | none => Except.error (command ++ " is not a supported command.\n" ++ usage)
      | some command' =>
          match tail with
          | ["--surface", surface] =>
              match parseSurface? surface with
              | none => Except.error (surface ++ " is not a supported surface.\n" ++ usage)
              | some surface' => Except.ok {command := command', surface := surface'}
          | [] => Except.error ("Missing --surface.\n" ++ usage)
          | _ => Except.error ("Expected exactly one --surface argument.\n" ++ usage)

def writeManifest (surface : Surface) : IO Unit := do
  let path := surfaceManifestPath surface
  IO.FS.createDirAll "../../tools/formal/generated"
  IO.FS.writeFile path (surfaceManifest surface)

def verifyManifest (surface : Surface) : IO Unit := do
  let path := surfaceManifestPath surface
  try
    let actual ← IO.FS.readFile path
    let expected := surfaceManifest surface
    if actual = expected then
      pure ()
    else
      throw <| IO.userError s!"Stale generated manifest at {path}"
  catch _ =>
    throw <| IO.userError s!"Missing generated manifest at {path}"

def joinWithSpaces : List String → String
  | [] => ""
  | first :: rest => rest.foldl (fun acc part => acc ++ " " ++ part) first

def escapeString (value : String) : String :=
  String.join <|
    value.toList.map fun c =>
      if c = '\\' then "\\\\"
      else if c = '"' then "\\\""
      else if c = '\n' then "\\n"
      else if c = '\t' then "\\t"
      else if c = '\r' then "\\r"
      else String.ofList [c]

partial def renderClj : Clj → String
  | .nil => "nil"
  | .bool true => "true"
  | .bool false => "false"
  | .nat value => toString value
  | .int value => toString value
  | .str value => "\"" ++ escapeString value ++ "\""
  | .keyword value => ":" ++ value
  | .symbol value => value
  | .vector values => "[" ++ joinWithSpaces (values.map renderClj) ++ "]"
  | .arrayMap entries =>
      let renderedEntries :=
        entries.foldr
          (fun (key, value) acc => renderClj key :: renderClj value :: acc)
          []
      "(array-map" ++
        (if renderedEntries.isEmpty then
           ""
         else
           " " ++ joinWithSpaces renderedEntries) ++
        ")"

def renderDef (name : String) (value : Clj) : String :=
  "(def " ++ name ++ "\n  " ++ renderClj value ++ ")\n"

def renderNamespace (namespaceName : String) (defs : List (String × Clj)) : String :=
  let defBlocks := defs.map fun (name, value) => renderDef name value
  String.intercalate "\n" <|
    [";; GENERATED by `npm run formal:sync`. Do not edit by hand."
    , "(ns " ++ namespaceName ++ ")"
    , ""] ++ defBlocks

def writeGeneratedSource (surface : Surface) (content : String) : IO Unit := do
  match surfaceGeneratedSourcePath? surface with
  | none => pure ()
  | some path =>
      IO.FS.createDirAll "../../target/formal"
      IO.FS.writeFile path content

theorem parseSurface?_vaultTransfer :
    parseSurface? "vault-transfer" = some Surface.vaultTransfer := by
  rfl

theorem parseSurface?_standard :
    parseSurface? "order-request-standard" = some Surface.orderRequestStandard := by
  rfl

theorem parseSurface?_effectOrderContract :
    parseSurface? "effect-order-contract" = some Surface.effectOrderContract := by
  rfl

theorem parseSurface?_portfolioReturnsEstimator :
    parseSurface? "portfolio-returns-estimator" = some Surface.portfolioReturnsEstimator := by
  rfl

theorem parseSurface?_portfolioReturnsNormalization :
    parseSurface? "portfolio-returns-normalization" = some Surface.portfolioReturnsNormalization := by
  rfl

theorem parseCommand?_verify :
    parseCommand? "verify" = some Command.verify := by
  rfl

theorem parseSurface?_tradingSubmitPolicy :
    parseSurface? "trading-submit-policy" = some Surface.tradingSubmitPolicy := by
  rfl

theorem parseSurface?_orderFormOwnership :
    parseSurface? "order-form-ownership" = some Surface.orderFormOwnership := by
  rfl
