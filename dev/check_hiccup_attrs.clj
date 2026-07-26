#!/usr/bin/env bb

(ns dev.check-hiccup-attrs
  (:require [dev.hiccup-lint :as lint]))

(defn -main
  [& _args]
  (System/exit (lint/check-hiccup-attrs!)))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
