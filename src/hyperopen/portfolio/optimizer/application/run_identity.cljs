(ns hyperopen.portfolio.optimizer.application.run-identity
  (:require [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn build-request-signature
  [request]
  (contracts/build-request-signature request))

(defn optimizer-input-signature
  [request]
  (contracts/optimizer-input-signature request))

(defn matching-request?
  [request last-successful-run]
  (let [run-request (get-in last-successful-run [:request-signature :request])
        current-signature (optimizer-input-signature request)
        run-signature (optimizer-input-signature run-request)]
    (and (some? current-signature)
         (some? run-signature)
         (= current-signature run-signature))))

(defn solved-run?
  [last-successful-run]
  (= :solved (get-in last-successful-run [:result :status])))

(defn- request-signature-input
  [request-signature]
  (or (:input-signature request-signature)
      (optimizer-input-signature (:request request-signature))))

(defn- matching-completed-request?
  [run-state last-successful-run]
  (let [run-signature (:request-signature run-state)
        last-run-signature (:request-signature last-successful-run)
        run-input (request-signature-input run-signature)
        last-run-input (request-signature-input last-run-signature)]
    (and (some? run-signature)
         (some? last-run-signature)
         (= (:scenario-id run-signature)
            (:scenario-id last-run-signature))
         (some? run-input)
         (some? last-run-input)
         (= run-input last-run-input))))

(defn completed-run?
  [{:keys [draft run-state running? last-successful-run]}]
  (and (solved-run? last-successful-run)
       (= :succeeded (:status run-state))
       (not running?)
       (not (true? (get-in draft [:metadata :dirty?])))
       (matching-completed-request? run-state last-successful-run)))

(defn current-solved-run?
  [{:keys [draft readiness running? last-successful-run] :as ctx}]
  (and (solved-run? last-successful-run)
       (not running?)
       (not (true? (get-in draft [:metadata :dirty?])))
       (or (completed-run? ctx)
           (matching-request? (:request readiness) last-successful-run))))

(defn stale-run?
  [{:keys [draft readiness last-successful-run] :as ctx}]
  (and (some? last-successful-run)
       (not (completed-run? ctx))
       (or (true? (get-in draft [:metadata :dirty?]))
           (not (matching-request? (:request readiness) last-successful-run)))))
