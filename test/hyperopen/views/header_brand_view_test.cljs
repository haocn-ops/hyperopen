(ns hyperopen.views.header-brand-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.service.tenant-config :as tenant-config]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.header-view :as header-view]))

(deftest header-renders-one-responsive-tenant-brand-name-test
  (let [tenant (-> tenant-config/default-tenant-raw
                   (assoc :tenant/id "enterprise-example"
                          :brand/name "Enterprise Desk"))
        view (header-view/header-view {:tenant/override tenant
                                       :wallet {:connected? false}})
        brand-names (hiccup/find-all-nodes
                     view
                     #(= "header-brand-name" (get-in % [1 :data-role])))
        brand-name (first brand-names)
        brand-container (hiccup/find-first-node
                         view
                         #(contains? (hiccup/node-class-set %) "md:w-[8rem]"))]
    (is (= 1 (count brand-names)))
    (is (= ["Enterprise Desk"] (vec (hiccup/collect-strings brand-name))))
    (is (not (contains? (hiccup/node-class-set brand-name) "hidden")))
    (is (contains? (hiccup/node-class-set brand-name) "truncate"))
    (is (contains? (hiccup/node-class-set brand-name) "md:text-base"))
    (is (contains? (hiccup/node-class-set brand-name) "lg:text-2xl"))
    (is (contains? (hiccup/node-class-set brand-container) "shrink-0"))
    (is (contains? (hiccup/node-class-set brand-container) "md:min-w-[8rem]"))))

(deftest header-keeps-the-mobile-brand-logo-outside-the-wordmark-test
  (let [tenant (-> tenant-config/default-tenant-raw
                   (assoc :tenant/id "dexhelm"
                          :brand/name "DEXHelm"))
        view (header-view/header-view {:tenant/override tenant
                                       :wallet {:connected? false}})
        mobile-brand (hiccup/find-by-data-role view "mobile-brand")
        brand-container (hiccup/find-first-node
                         view
                         #(contains? (hiccup/node-class-set %) "md:w-[8rem]"))]
    (is (contains? (hiccup/node-class-set mobile-brand) "shrink-0"))
    (is (contains? (hiccup/node-class-set brand-container) "w-[7rem]"))
    (is (contains? (hiccup/node-class-set brand-container) "min-w-[7rem]"))
    (is (contains? (hiccup/node-class-set brand-container) "sm:w-[8.5rem]"))
    (is (contains? (hiccup/node-class-set brand-container) "sm:min-w-[8.5rem]"))))
