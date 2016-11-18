(ns eway.riemann.ping
  (:require [clojure.edn :as edn]
            [yaml.core :as yaml]
            [clojure.spec :as s]
            [clojure.core.async :as async]
            [eway.riemann.client :as rm]
            [taoensso.timbre :as timbre]
            [org.httpkit.client :as http]
            [mount.core :as mount :refer [defstate]]))

;; ----- LOGGING -----
(defn init-logging!
  []
  ;; Disable stack trace colors.
  (timbre/merge-config!
   {:output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})})
  (timbre/set-level! :debug #_ :info))

(defstate log
  :start (init-logging!))

#_ timbre/*config* ; Inspect logging config.

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

(let [host "link.ixs1.net"
      proto "http"
      path "/s/om?showStats"
      url (str proto "://" host path)
      options {:timeout 200             ; ms
               :user-agent "riemann-ping"}]
  ;; @(http/get url)
  
  (http/get url options
            (fn [{:keys [status headers body error]}] ;; asynchronous response handling
              (if error
                (println "Failed, exception is " error)
                (println "Async HTTP GET: " status)))))

;; TODO: Time requests.
;; TODO: Check response.
;; TODO: Create unit tests.
;; TODO: Forward response to riemman.
;; TODO: Retry every 60? 120? 300? seconds?
;; TODO: Does the request rate need to be limited or randomized?
;;       - otherwise will add 250 hits in 1 second or something
;;       -? use timer per service?
;;       -? use ms delay between each request? (total checks/window)
;; TODO: Should it back off if service is down?
;; TODO: Add edn configuration file.
;; TODO: Read list of services to test from configuration file.


;; Command line entry point.
(defn -main [& _]
  (mount/start))

(defn reset []
  (mount/stop)
  (mount/start))
#_ (reset)
