(ns hyperopen.ui.preferences-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.ui.preferences :as preferences]))

(defn- has-own?
  [obj key]
  (.call (.-hasOwnProperty js/Object.prototype) obj key))

(deftest restore-ui-font-preference-uses-stored-valid-value-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        html-el (js-obj "dataset" (js-obj))]
    (try
      (set! (.-document js/globalThis) (js-obj "documentElement" html-el))
      (with-redefs [hyperopen.platform/local-storage-get (fn [_] " Inter ")]
        (preferences/restore-ui-font-preference!)
        (is (= "inter" (.. js/document -documentElement -dataset -uiFont))))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest restore-ui-font-preference-falls-back-to-system-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        html-el (js-obj "dataset" (js-obj))]
    (try
      (set! (.-document js/globalThis) (js-obj "documentElement" html-el))
      (with-redefs [hyperopen.platform/local-storage-get (fn [_] "not-supported")]
        (preferences/restore-ui-font-preference!)
        (is (= "system" (.. js/document -documentElement -dataset -uiFont))))
      (with-redefs [hyperopen.platform/local-storage-get (fn [_]
                                                            (throw (js/Error. "boom")))]
        (preferences/restore-ui-font-preference!)
        (is (= "system" (.. js/document -documentElement -dataset -uiFont))))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest restore-ui-theme-preference-uses-stored-valid-value-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        html-el (js-obj "dataset" (js-obj))
        store (atom {})]
    (try
      (set! (.-document js/globalThis) (js-obj "documentElement" html-el))
      (with-redefs [hyperopen.platform/local-storage-get (fn [_] " HyperDegen ")]
        (is (= "hyperdegen" (preferences/restore-ui-theme-preference! store)))
        (is (= "hyperdegen" (.. js/document -documentElement -dataset -theme)))
        (is (= "hyperdegen" (get-in @store [:ui :theme]))))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest restore-ui-theme-preference-prioritizes-stored-then-tenant-then-default-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        html-el (js-obj "dataset" (js-obj))
        tenant-state {:ui {:theme "dark"}
                      :tenant/override {:theme/id "institutional"}}
        cases [{:label "valid stored preference overrides tenant theme"
                :stored "hyperdegen"
                :state tenant-state
                :expected "hyperdegen"}
               {:label "missing stored preference uses tenant theme"
                :stored nil
                :state tenant-state
                :expected "institutional"}
               {:label "invalid stored preference uses tenant theme"
                :stored "not-a-theme"
                :state tenant-state
                :expected "institutional"}
               {:label "missing stored and tenant preferences use dark default"
                :stored nil
                :state {}
                :expected "dark"}]]
    (try
      (set! (.-document js/globalThis) (js-obj "documentElement" html-el))
      (doseq [{:keys [label stored state expected]} cases]
        (let [store (atom state)]
          (with-redefs [hyperopen.platform/local-storage-get (fn [_] stored)]
            (is (= expected
                   (preferences/restore-ui-theme-preference! store))
                label)
            (is (= expected
                   (.. js/document -documentElement -dataset -theme))
                label)
            (is (= expected (get-in @store [:ui :theme]))
                label))))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest restore-ui-theme-preference-falls-back-to-default-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        html-el (js-obj "dataset" (js-obj))
        store (atom {})]
    (try
      (set! (.-document js/globalThis) (js-obj "documentElement" html-el))
      (with-redefs [hyperopen.platform/local-storage-get (fn [_] "not-a-theme")]
        (preferences/restore-ui-theme-preference! store)
        (is (= "dark" (.. js/document -documentElement -dataset -theme)))
        (is (= "dark" (get-in @store [:ui :theme]))))
      (with-redefs [hyperopen.platform/local-storage-get (fn [_]
                                                           (throw (js/Error. "boom")))]
        (preferences/restore-ui-theme-preference! store)
        (is (= "dark" (.. js/document -documentElement -dataset -theme))))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest restore-ui-theme-preference-no-document-noop-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        calls (atom 0)]
    (try
      (js-delete js/globalThis "document")
      (with-redefs [hyperopen.platform/local-storage-get (fn [_]
                                                           (swap! calls inc)
                                                           "hyperdegen")]
        (preferences/restore-ui-theme-preference! (atom {}))
        (is (= 0 @calls)))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest restore-ui-font-preference-no-document-noop-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        calls (atom 0)]
    (try
      (js-delete js/globalThis "document")
      (with-redefs [hyperopen.platform/local-storage-get (fn [_]
                                                            (swap! calls inc)
                                                            "inter")]
        (preferences/restore-ui-font-preference!)
        (is (= 0 @calls)))
      (finally
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))
