(ns hyperopen.websocket.diagnostics.schema
  (:require [cljs.spec.alpha :as s]
            [hyperopen.websocket.diagnostics.catalog :as catalog]))

(s/def ::status
  (set (keys catalog/status-catalog)))

(s/def ::group
  (set (keys catalog/group-catalog)))

(s/def ::generated-at-ms
  (s/nilable number?))

(s/def ::state
  keyword?)

(s/def ::freshness
  keyword?)

(s/def ::last-recv-at-ms
  (s/nilable number?))

(s/def ::expected-traffic?
  boolean?)

(s/def ::attempt
  (s/nilable int?))

(s/def ::last-close
  (s/nilable map?))

(s/def ::transport
  (s/keys :opt-un [::state
                   ::freshness
                   ::last-recv-at-ms
                   ::expected-traffic?
                   ::attempt
                   ::last-close]))

(s/def ::worst-status
  keyword?)

(s/def ::gap-detected?
  boolean?)

(s/def ::group-health
  (s/keys :opt-un [::worst-status
                   ::gap-detected?]))

(s/def ::groups
  (s/map-of keyword? ::group-health))

(s/def ::topic
  (s/nilable string?))

(s/def ::subscribed?
  boolean?)

(s/def ::status-raw
  keyword?)

(s/def ::age-ms
  (s/nilable number?))

(s/def ::stale-threshold-ms
  (s/nilable number?))

(s/def ::descriptor
  any?)

(s/def ::stream
  (s/keys :opt-un [::group
                   ::topic
                   ::subscribed?
                   ::status-raw
                   ::last-recv-at-ms
                   ::age-ms
                   ::stale-threshold-ms
                   ::descriptor]))

(s/def ::streams
  (s/map-of any? map?))

(s/def ::market-projection
  map?)

(s/def ::health
  (s/keys :opt-un [::generated-at-ms
                   ::transport
                   ::groups
                   ::streams
                   ::market-projection]))

(s/def ::label
  string?)

(s/def ::href
  string?)

(s/def ::active-bars
  int?)

(s/def ::bar-count
  int?)

(s/def ::tone
  keyword?)

(s/def ::tooltip
  string?)

(s/def ::score
  int?)

(s/def ::source
  keyword?)

(s/def ::connection-meter
  (s/keys :req-un [::label
                   ::status
                   ::active-bars
                   ::bar-count
                   ::tone
                   ::tooltip
                   ::score
                   ::source]))

(s/def ::footer-link
  (s/keys :req-un [::label ::href]))

(s/def ::footer-links
  (s/coll-of ::footer-link :kind vector?))

(s/def ::mobile-nav-item
  (s/keys :req-un [::label]
          :opt-un [::href]))

(s/def ::mobile-nav
  map?)

(s/def ::diagnostics
  map?)

(s/def ::banner
  map?)

(s/def ::footer-vm
  (s/keys :req-un [::connection-meter
                   ::footer-links
                   ::mobile-nav]
          :opt-un [::banner
                   ::diagnostics]))
