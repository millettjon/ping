;; -*- clojure -*-

(set-env!
 :resource-paths #{"src"}
 :dependencies '[;; [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojure "1.9.0-alpha14"]
                 [io.forward/yaml "1.0.5"]
                 [riemann-clojure-client "0.4.2"]
                 [http-kit "2.2.0"]
                 [com.taoensso/timbre "4.7.4"]
                 [org.clojure/core.async "0.2.395"]
                 [mount "0.1.10"]
                 [jarohen/chime "0.1.9"]
                 [spootnik/signal "0.2.0"]])

(require 'boot.repl)
