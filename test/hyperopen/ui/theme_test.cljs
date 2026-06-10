(ns hyperopen.ui.theme-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.ui.theme :as theme]))

(defn- has-own?
  [obj key]
  (.call (.-hasOwnProperty js/Object.prototype) obj key))

(deftest normalize-theme-id-accepts-catalog-ids-test
  (doseq [{:keys [id]} theme/themes]
    (is (= id (theme/normalize-theme-id id))))
  (is (= "institutional" (theme/normalize-theme-id " Institutional "))))

(deftest normalize-theme-id-falls-back-to-default-test
  (is (= theme/default-theme-id (theme/normalize-theme-id nil)))
  (is (= theme/default-theme-id (theme/normalize-theme-id "")))
  (is (= theme/default-theme-id (theme/normalize-theme-id "neon-pony")))
  (is (= theme/default-theme-id (theme/normalize-theme-id 42))))

(deftest catalog-shape-test
  (is (pos? (count theme/themes)))
  (is (= theme/default-theme-id (:id (first theme/themes))))
  (doseq [{:keys [id label]} theme/themes]
    (is (string? id))
    (is (seq label))))

(deftest apply-theme-attribute-sets-dataset-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        html-el (js-obj "dataset" (js-obj))]
    (try
      (set! (.-document js/globalThis) (js-obj "documentElement" html-el))
      (is (= "hyperdumb" (theme/apply-theme-attribute! "hyperdumb")))
      (is (= "hyperdumb" (.. js/document -documentElement -dataset -theme)))
      (is (= theme/default-theme-id (theme/apply-theme-attribute! "bogus")))
      (is (= theme/default-theme-id
             (.. js/document -documentElement -dataset -theme)))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest active-theme-id-reads-state-test
  (is (= "institutional" (theme/active-theme-id {:ui {:theme "institutional"}})))
  (is (= theme/default-theme-id (theme/active-theme-id {})))
  (is (= theme/default-theme-id (theme/active-theme-id {:ui {:theme "junk"}}))))
