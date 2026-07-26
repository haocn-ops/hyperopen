import Hyperopen.Formal.Common
import Hyperopen.Formal.EffectOrderContract
import Hyperopen.Formal.PortfolioReturnsEstimator
import Hyperopen.Formal.PortfolioReturnsNormalization
import Hyperopen.Formal.VaultTransfer
import Hyperopen.Formal.OrderRequest.Standard
import Hyperopen.Formal.OrderRequest.Advanced
import Hyperopen.Formal.TradingSubmitPolicy
import Hyperopen.Formal.OrderFormOwnership

namespace Hyperopen.Formal

def runVerify : Surface → IO Unit
  | .vaultTransfer => VaultTransfer.verify
  | .orderRequestStandard => OrderRequest.Standard.verify
  | .orderRequestAdvanced => OrderRequest.Advanced.verify
  | .effectOrderContract => EffectOrderContract.verify
  | .portfolioReturnsEstimator => PortfolioReturnsEstimator.verify
  | .portfolioReturnsNormalization => PortfolioReturnsNormalization.verify
  | .tradingSubmitPolicy => TradingSubmitPolicy.verify
  | .orderFormOwnership => OrderFormOwnership.verify

def runSync : Surface → IO Unit
  | .vaultTransfer => VaultTransfer.sync
  | .orderRequestStandard => OrderRequest.Standard.sync
  | .orderRequestAdvanced => OrderRequest.Advanced.sync
  | .effectOrderContract => EffectOrderContract.sync
  | .portfolioReturnsEstimator => PortfolioReturnsEstimator.sync
  | .portfolioReturnsNormalization => PortfolioReturnsNormalization.sync
  | .tradingSubmitPolicy => TradingSubmitPolicy.sync
  | .orderFormOwnership => OrderFormOwnership.sync

def runInvocation : Invocation → IO Unit
  | {command := .verify, surface} => runVerify surface
  | {command := .sync, surface} => runSync surface
