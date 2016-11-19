(ns eway.riemann.config
  (:require
   [clojure.edn :as edn]
   [yaml.core :as yaml]
   [clojure.spec :as s]
   [taoensso.timbre :as timbre]
   [mount.core :as mount :refer [defstate]]))

;; ----- CONFIGURATION -----

(def etc-dir
  (str (System/getProperty "user.dir") "/etc"))

(defn load-config []
  "Loads configuration from disk."
  (-> (str etc-dir "/config.edn")
      slurp
      edn/read-string))

;; Expected schema for link clusters in LBAdmin.yaml.
;; Simple values.
(s/def :eway.riemann.ping/dns-name keyword?)
(s/def :eway.riemann.ping/backend string?)

;; Compound values.
(s/def :eway.riemann.ping.link/protocols
  (s/coll-of #{"http" "https"} :kind vector? :distinct true :min-count 1 :max-count 2))
(s/def :eway.riemann.ping.link/cluster
  (s/or :long (s/keys :req-un [:eway.riemann.ping.link/protocols] :opt-un [:eway.riemann.ping/backend])
        :short :eway.riemann.ping.link/protocols))
(s/def :eway.riemann.ping.link/clusters (s/map-of :eway.riemann.ping/dns-name :eway.riemann.ping.link/cluster))

(defn load-lb-config []
  "Loads the load balancer configuration from yaml."
  (let [        
        ;; We use yaml here instead of edn since LBAdmin.yaml is pulled form the "lb" project.
        ;; Note: yaml/from-file returns keys as strings so use yaml/parse-string instead.
        lb-conf (-> (str etc-dir "/LBAdmin.yaml")
                    slurp
                    yaml/parse-string)

        link-frontends (get-in lb-conf [:profiles :lb2 :frontends :link])]
    
    (s/conform :eway.riemann.ping.link/clusters link-frontends)))

(defstate config
  :start (assoc (load-config) :lb (load-lb-config)))
