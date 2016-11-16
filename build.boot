;; -*- clojure -*-

(set-env!
 :resource-paths #{"src"}
 :dependencies '[[org.clojure/clojure "1.8.0"]
                 [riemann-clojure-client "0.4.1"]
                 [clj-time "0.11.0"]
                 [com.taoensso/timbre "4.2.1"]
                 [org.clojure/core.async "0.2.374"]
                 [mount "0.1.8"]])

(require 'boot.repl)
