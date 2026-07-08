(ns hyperopen.portfolio.optimizer.contracts.constants)

(def result-payload-schema-version 1)

(def draft-statuses
  #{:draft :saved :archived :tracking})

(def scenario-record-statuses
  #{:saved :archived :executed :partially-executed :tracking :failed})

(def tracking-snapshot-statuses
  #{:tracked :not-trackable})

(def result-payload-statuses
  #{:solved :infeasible :error :failed})

(def objective-kinds
  #{:minimum-variance :max-sharpe :target-return :target-volatility})

(def return-model-kinds
  #{:historical-mean :ew-mean :black-litterman})

(def risk-model-kinds
  #{:diagonal-shrink
    :ledoit-wolf
    :ledoit-wolf-dense
    :sample-covariance
    :mixed-frequency})

(def history-assumption-behaviors
  #{:conservative :proxy})
