(ns hyperopen.telemetry.console-warning-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.telemetry.console-warning :as warning]))

(defn- has-own?
  [obj key]
  (.call (.-hasOwnProperty js/Object.prototype) obj key))

(deftest emit-warning-logs-banner-and-warning-in-browser-context-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        orig-log (.-log js/console)
        calls (atom [])]
    (try
      (set! (.-document js/globalThis) (js-obj))
      (set! (.-log js/console) (fn [& args]
                                 (swap! calls conj (vec args))))
      (warning/emit-warning!)
      (is (>= (count @calls) 3))
      (is (some (fn [args]
                  (some #(and (string? %)
                              (>= (.indexOf % "Warning!") 0))
                        args))
                @calls))
      (finally
        (set! (.-log js/console) orig-log)
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest gradient-helpers-and-banner-rendering-handle-clamping-and-empty-lines-test
  (let [lerp @#'warning/lerp
        ease-out @#'warning/ease-out
        gradient-rgb @#'warning/gradient-rgb
        emit-banner! @#'warning/emit-banner!
        orig-log (.-log js/console)
        calls (atom [])]
    (is (= 7.5 (lerp 5 10 0.5)))
    (is (= 0.75 (ease-out 0.5)))
    (is (= [10 87 77] (gradient-rgb -0.5)))
    (is (= [4 166 135] (gradient-rgb 0.5)))
    (is (= [0 212 170] (gradient-rgb 1.5)))
    (try
      (set! (.-log js/console) (fn [& args]
                                 (swap! calls conj (vec args))))
      (with-redefs [str/split-lines (fn [_] ["top" "" "bottom"])]
        (emit-banner!))
      (is (= 1 (count @calls)))
      (let [[format-string style-a style-b] (first @calls)]
        (is (= "%ctop\n%cbottom" format-string))
        (is (.includes style-a "rgb(10,87,77)"))
        (is (.includes style-b "rgb(0,212,170)")))
      (finally
        (set! (.-log js/console) orig-log)))))

(deftest emit-warning-noops-without-browser-document-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        orig-log (.-log js/console)
        calls (atom [])]
    (try
      (js-delete js/globalThis "document")
      (set! (.-log js/console) (fn [& args]
                                 (swap! calls conj args)))
      (warning/emit-warning!)
      (is (empty? @calls))
      (finally
        (set! (.-log js/console) orig-log)
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))

(deftest emit-warning-noops-without-console-log-test
  (let [orig-document (.-document js/globalThis)
        had-document? (has-own? js/globalThis "document")
        orig-log (.-log js/console)]
    (try
      (set! (.-document js/globalThis) (js-obj))
      (set! (.-log js/console) nil)
      (is (nil? (warning/emit-warning!)))
      (finally
        (set! (.-log js/console) orig-log)
        (if had-document?
          (set! (.-document js/globalThis) orig-document)
          (js-delete js/globalThis "document"))))))
