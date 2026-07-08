(ns hyperopen.portfolio.optimizer.contracts.spec-registry
  (:require [clojure.spec.alpha :as s]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.contracts.specs :as specs]))

(s/def ::contracts/draft ::specs/draft)
(s/def ::contracts/engine-request ::specs/engine-request)
(s/def ::contracts/request-signature ::specs/request-signature)
(s/def ::contracts/scenario-record ::specs/scenario-record)
(s/def ::contracts/tracking-snapshot ::specs/tracking-snapshot)
(s/def ::contracts/tracking-record ::specs/tracking-record)
(s/def ::contracts/result-payload ::specs/result-payload)
(s/def ::contracts/worker-envelope ::specs/worker-envelope)
