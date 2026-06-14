(ns hyperopen.portfolio.optimizer.contracts.signatures)

(def request-signature-schema-version 1)

(def optimizer-input-keys
  [:requested-universe
   :universe
   :current-portfolio
   :return-model
   :risk-model
   :objective
   :constraints
   :execution-assumptions
   :history
   :black-litterman-prior])

(defn- stable-execution-assumptions
  [execution-assumptions]
  (when (map? execution-assumptions)
    (dissoc execution-assumptions :cost-contexts-by-id)))

(defn- stable-history
  [history]
  (when (map? history)
    (dissoc history :freshness)))

(defn- stable-objective
  [objective]
  ;; :frontier-points is a sampling-density knob injected by refinement, not a
  ;; problem-defining input. Stripping it keeps a draft result and a refined result of
  ;; the same scenario sharing one input-signature (no false staleness / no dup-suppression).
  (when (map? objective)
    (dissoc objective :frontier-points)))

(defn optimizer-input-signature
  [request]
  (when (map? request)
    (-> (select-keys request optimizer-input-keys)
        (update :execution-assumptions stable-execution-assumptions)
        (update :history stable-history)
        (update :objective stable-objective))))

(defn build-request-signature
  [request]
  {:schema-version request-signature-schema-version
   :scenario-id (:scenario-id request)
   :as-of-ms (:as-of-ms request)
   :request request
   :input-signature (optimizer-input-signature request)})
