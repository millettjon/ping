(ns eway.app.config
  (:require
   [clojure.edn :as edn]
   [taoensso.timbre :as l]
   [mount.core :as mount :refer [defstate]]))

(def etc-dir
  (str (System/getProperty "user.dir") "/etc"))

(defn load-config []
  "Loads configuration from disk."
  (let [path (str etc-dir "/config.edn")]
    (l/info "Loading configuration from: " path)
    (try
      (let [config (-> path slurp edn/read-string)]
        (l/info "Loaded configuration:" config)
        config)
      (catch Exception e
        (l/error (str e))))))

(defstate config
  :start (load-config))

#_ (mount/start #'eway.app.config/config)
#_ (mount/stop #'eway.app.config/config)
