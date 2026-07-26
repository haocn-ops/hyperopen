(ns hyperopen.ui.fonts
  (:require [clojure.string :as str]))

(def default-ui-system-font-family
  "system-ui, -apple-system, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, \"Noto Sans\", \"Liberation Sans\", sans-serif")

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn- expand-ui-system-var
  [font-family system-font-family]
  (some-> (or font-family "")
          (str/replace "var(--font-ui-system)" system-font-family)
          non-blank-text))

(defn resolve-ui-font-family []
  (if (and (exists? js/window) (exists? js/document))
    (try
      (let [root (.-documentElement js/document)
            computed-style (.getComputedStyle js/window root)
            system-font-family (or (non-blank-text (.getPropertyValue computed-style "--font-ui-system"))
                                   default-ui-system-font-family)
            configured-font-family (non-blank-text (.getPropertyValue computed-style "--font-ui"))]
        (or (expand-ui-system-var configured-font-family system-font-family)
            system-font-family))
      (catch :default _
        default-ui-system-font-family))
    default-ui-system-font-family))

(defn canvas-font
  ([size-px]
   (canvas-font size-px 400))
  ([size-px weight]
   (str weight " " size-px "px " (resolve-ui-font-family))))
