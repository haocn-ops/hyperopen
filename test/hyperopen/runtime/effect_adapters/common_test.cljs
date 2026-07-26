(ns hyperopen.runtime.effect-adapters.common-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.app-effects :as app-effects]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.effect-adapters.common :as common]))

(deftest facade-shared-adapters-delegate-to-common-module-test
  (is (identical? common/save effect-adapters/save))
  (is (identical? common/save-many effect-adapters/save-many))
  (is (identical? common/local-storage-set effect-adapters/local-storage-set))
  (is (identical? common/local-storage-set-json effect-adapters/local-storage-set-json))
  (is (identical? common/push-state effect-adapters/push-state))
  (is (identical? common/replace-state effect-adapters/replace-state))
  (is (identical? common/schedule-animation-frame! effect-adapters/schedule-animation-frame!)))

(deftest core-effect-handler-adapters-preserve-dispatch-signatures-test
  (let [calls (atom {:save nil
                     :save-many nil
                     :local-storage-set nil
                     :local-storage-set-json nil
                     :push-state nil
                     :replace-state nil})
        store (atom {})]
    (with-redefs [app-effects/save!
                  (fn [store* path value]
                    (swap! calls assoc :save [store* path value]))
                  app-effects/save-many!
                  (fn [store* path-values]
                    (swap! calls assoc :save-many [store* path-values]))
                  app-effects/local-storage-set!
                  (fn [key value]
                    (swap! calls assoc :local-storage-set [key value]))
                  app-effects/local-storage-set-json!
                  (fn [key value]
                    (swap! calls assoc :local-storage-set-json [key value]))
                  app-effects/push-state!
                  (fn [path]
                    (swap! calls assoc :push-state path))
                  app-effects/replace-state!
                  (fn [path]
                    (swap! calls assoc :replace-state path))]
      (effect-adapters/save :ctx store [:router :path] "/trade")
      (effect-adapters/save-many :ctx store [[[:router :path] "/wallet"]])
      (effect-adapters/local-storage-set :ctx store "active-asset" "ETH")
      (effect-adapters/local-storage-set-json :ctx store "asset-favorites" {:ETH true})
      (effect-adapters/push-state :ctx store "/trade")
      (effect-adapters/replace-state :ctx store "/wallet"))
    (is (= [store [:router :path] "/trade"]
           (:save @calls)))
    (is (= [store [[[:router :path] "/wallet"]]]
           (:save-many @calls)))
    (is (= ["active-asset" "ETH"]
           (:local-storage-set @calls)))
    (is (= ["asset-favorites" {:ETH true}]
           (:local-storage-set-json @calls)))
    (is (= "/trade" (:push-state @calls)))
    (is (= "/wallet" (:replace-state @calls)))))
