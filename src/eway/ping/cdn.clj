(ns eway.ping.cdn
  (:require
   [yaml.core :as yaml]
   [taoensso.timbre :as timbre]
   [mount.core :as mount :refer [defstate]]))

(def etc-dir
  (str (System/getProperty "user.dir") "/etc"))

(defn load-config []
  "Loads the cdn configuration from yaml."
  (let [        
        ;; We use yaml here instead of edn since cdn-data.yaml is pulled form the cdn-data project.
        ;; Note: yaml/from-file returns keys as strings so use yaml/parse-string instead.
        cdn-conf (-> (str etc-dir "/cdn-data.yaml")
                    slurp
                    yaml/parse-string
                    (get-in [:cdn-data :sites]))]
    
    (map (fn [el] {:protocols ["http"] :name (-> el first name)}) cdn-conf)
))

#_ (load-config)

(defstate config
  :start (load-config))

#_ (mount/stop #'eway.ping.cdn/config)
#_ (mount/start #'eway.ping.cdn/config)
