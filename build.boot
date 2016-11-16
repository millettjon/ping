;; -*- clojure -*-

(set-env!
 :resource-paths #{"src"}
 :dependencies '[[org.clojure/clojure "1.8.0"]
                 [riemann-clojure-client "0.4.2"]
                 [com.taoensso/timbre "4.7.4"]
                 [org.clojure/core.async "0.2.395"]
                 [mount "0.1.10"]])

(require 'boot.repl)
