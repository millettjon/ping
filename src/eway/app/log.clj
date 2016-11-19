(ns eway.app.log
  (:require [taoensso.timbre :as timbre]
            [mount.core :as mount :refer [defstate]]))

(defn init-logging!
  []
  ;; Disable stack trace colors.
  (timbre/merge-config!
   {:output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})})
  (timbre/set-level! :debug #_ :info))

(defstate log
  :start (init-logging!))

#_ timbre/*config* ; Inspect logging config.

