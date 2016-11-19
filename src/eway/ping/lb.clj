(ns eway.ping.lb
  (:require
   [yaml.core :as yaml]
   [clojure.spec :as s]
   [taoensso.timbre :as timbre]
   [mount.core :as mount :refer [defstate]]))

;; Expected schema for link clusters in LBAdmin.yaml.
;; Simple values.
(s/def :dns/name keyword?)
(s/def :haproxy/backend string?)

;; Compound values.
(s/def :eway.ping.link/protocols
  (s/coll-of #{"http" "https"} :kind vector? :distinct true :min-count 1 :max-count 2))
(s/def :eway.ping.link/cluster
  (s/or :long (s/keys :req-un [:eway.ping.link/protocols] :opt-un [:haproxy/backend])
        :short :eway.ping.link/protocols))
(s/def :eway.ping.link/clusters (s/map-of :dns/name :eway.ping.link/cluster))

(def etc-dir
  (str (System/getProperty "user.dir") "/etc"))

(defn load-config []
  "Loads the load balancer configuration from yaml."
  (let [        
        ;; We use yaml here instead of edn since LBAdmin.yaml is pulled form the "lb" project.
        ;; Note: yaml/from-file returns keys as strings so use yaml/parse-string instead.
        lb-conf (-> (str etc-dir "/LBAdmin.yaml")
                    slurp
                    yaml/parse-string)

        link-frontends (get-in lb-conf [:profiles :lb2 :frontends :link])]
    
    (s/conform :eway.ping.link/clusters link-frontends)))

(defstate config
  :start (load-config))

#_ (mount/start #'eway.ping.lb/config)
