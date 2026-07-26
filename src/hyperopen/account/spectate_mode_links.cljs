(ns hyperopen.account.spectate-mode-links
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.portfolio.routes :as portfolio-routes]
            [hyperopen.router :as router]))

(def ^:private spectate-query-param
  "spectate")

(defn- path-without-query
  [path]
  (first (str/split (or path "/") #"\?" 2)))

(defn spectate-address-from-search
  [search]
  (let [params (js/URLSearchParams. (or search ""))]
    (some-> (.get params spectate-query-param)
            account-context/normalize-address)))

(defn spectate-url-path
  [path address]
  (let [path* (router/normalize-path (path-without-query path))
        address* (account-context/normalize-address address)]
    (if address*
      (str path* "?" spectate-query-param "=" address*)
      path*)))

(defn spectate-navigation-path
  [path address]
  (let [path* (router/normalize-path path)
        address* (account-context/normalize-address address)]
    (spectate-url-path
     path*
     (when (and address*
                (not (portfolio-routes/trader-portfolio-route? path*)))
       address*))))

(defn internal-route-href
  [state path]
  (spectate-navigation-path
   path
   (when (account-context/spectate-mode-active? state)
     (account-context/spectate-address state))))

(defn- location-origin
  [location]
  (cond
    (string? location)
    location

    (map? location)
    (or (:origin location)
        (get location "origin"))

    :else
    (some-> location .-origin)))

(defn spectate-url
  ([path address]
   (spectate-url (some-> js/globalThis .-location) path address))
  ([location path address]
   (let [path* (spectate-url-path path address)
         origin* (location-origin location)]
     (if (seq origin*)
       (str origin* path*)
       path*))))
