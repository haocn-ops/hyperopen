(ns hyperopen.wallet.agent-runtime
  (:require [hyperopen.wallet.agent-runtime.approval :as approval]
            [hyperopen.wallet.agent-runtime.enable :as enable]
            [hyperopen.wallet.agent-runtime.errors :as errors]
            [hyperopen.wallet.agent-runtime.protection-mode :as protection-mode]
            [hyperopen.wallet.agent-runtime.storage-mode :as storage-mode]
            [hyperopen.wallet.agent-runtime.unlock :as unlock]))

(defn exchange-response-error
  [resp]
  (errors/exchange-response-error resp))

(defn runtime-error-message
  [err]
  (errors/runtime-error-message err))

(defn set-agent-storage-mode!
  [opts]
  (storage-mode/set-agent-storage-mode! opts))

(defn set-agent-local-protection-mode!
  [opts]
  (protection-mode/set-agent-local-protection-mode! opts))

(defn approve-agent-request!
  [opts]
  (approval/approve-agent-request! opts))

(defn enable-agent-trading!
  [opts]
  (enable/enable-agent-trading! opts))

(defn unlock-agent-trading!
  [opts]
  (unlock/unlock-agent-trading! opts))

(defn lock-agent-trading!
  [opts]
  (unlock/lock-agent-trading! opts))
