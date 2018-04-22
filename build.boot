;; -*- clojure -*-

(set-env!
 :resource-paths #{"src"}
 :dependencies '[[org.clojure/clojure "1.9.0"]
                 ;; [org.clojure/core.specs.alpha "0.1.24"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [io.forward/yaml "1.0.7"]
                 [riemann-clojure-client "0.4.5"]
                 [http-kit "2.3.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.8"]
                 [org.clojure/core.async "0.4.474"]
                 [mount "0.1.12"]
                 [jarohen/chime "0.2.2"]
                 [spootnik/signal "0.2.2"]])

(require 'boot.repl)

(require 'eway.ping.core)

(deftask run
  []
  (#'eway.ping.core/-main))

(deftask check-cdn
  []
  (#'eway.ping.core/check-cdn))
