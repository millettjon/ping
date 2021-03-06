(ns eway.ping.core
  (:require
   ;; mount component dependencies
   [eway.app.log]
   [eway.app.config :refer [config]]
   [eway.ping.lb :as lb]
   [eway.ping.cdn :as cdn]
   [eway.app.riemann :as rm]

   [taoensso.timbre :as l]
   [eway.util.string :as u]
   [clojure.core.async :as async]
   [clojure.string :as s]
   [signal.handler :as sig]
   [chime :refer [chime-at]]
   [clj-time.core :as t]
   [clj-time.periodic :refer [periodic-seq]]
   [org.httpkit.client :as http]
   [mount.core :as mount :refer [defstate]]))

;; ----- WEB CHECK -----
(defn now [] (System/currentTimeMillis))


(defn log-event [event]
  (let [event (merge event {:tags ["ping"]
                            :ttl (/ (config :interval) 1000)})]
    (async/>!! rm/event-channel event)))

(defn check-error [event error]
  (if error
    (assoc event
           :state "critical"
           :status (-> error .getClass .getName)
           :description (println-str (:description event) error))
    event))

(defn check-body [event body]
  (let [{:keys [response-string]} config]
    (if (or (not= 200 (event :status)) (s/includes? body response-string))
      event
      (assoc event
             :state "critical"
             :status "bad response"
             :description (println-str (:description event) "Failed to find string" (pr-str response-string) "in response body.\n" (-> body (u/truncate 1000) prn-str))))))

(defn check-status [frontend]
  "Checks the status of a frontend."
  (doseq [proto (:protocols frontend)]
    (let [host (:name frontend)
          path (:request-uri config)
          url (str proto "://" host path)
          options {:timeout (:timeout config)
                   :user-agent "eway.ping"
                   :start-time (now)}]
       (http/get url options
                (fn [{:keys [status headers body error opts]}] ;; asynchronous response handling
                  (let [elapsed-ms (- (now) (:start-time opts))
                        event {:host host
                               :service (str host " " proto)
                               :protocol proto
                               :metric elapsed-ms
                               :status status
                               :state (if (= status 200) "ok" "critical")
                               :description (println-str "url:" url)}]
                    (-> event
                        (check-error error)
                        (check-body body)
                        log-event)))))))
#_ (check-status {:protocols ["http"] :name "bogus.ixs1.net"})
#_ (->> lb/config
        (take 10)
        (map normalize-frontend)
        (map check-status)
        dorun)

;; ----- SCHEDULER -----
(defn normalize-frontend [[_name [type data]]]
  (let [frontend (if (= type :short)
                   {:protocols data}    ; expand short form
                   data)]
    ;; Coerce frontend name to a string.
    (assoc frontend :name (name _name))))

(defn start-scheduler []
  "Schedules jobs by staggering start times so that they are evenly distributed over each repeat interval."
  (let [jobs (atom [])
        num-frontends (count lb/config)
        interval (:interval config)
        interval-ms (t/millis interval)
        step-ms (long (/ interval num-frontends))
        start-time (t/now)
        error-fn (fn [e] (prn e))
        make-job (fn [i frontend]
                   (let [frontend (normalize-frontend frontend)
                         job-time (t/plus start-time (t/millis (* i step-ms)))
                         callback-fn (fn [time] (check-status frontend) #_ (println time frontend))
                         cancel-fn (chime-at (periodic-seq job-time interval-ms) callback-fn {:error-handler error-fn})]
                     (swap! jobs conj cancel-fn)))]
    (->> lb/config
         (map-indexed make-job)
         dorun)
    jobs))

(defstate scheduled-jobs
  :start (start-scheduler)
  :stop (doseq [cancel-fn @scheduled-jobs] (cancel-fn)))

;; ----- COMMAND LINE -----
;; Command line entry point.
(def done (promise))
(defn -main [& _]
  (mount/start)
  @done
  (l/info "Exiting.")
  (mount/stop))

(defn check-cdn [& _]
  (mount/start-without #'eway.ping.core/scheduled-jobs)
  (prn "Checking " (count cdn/config) "sites.")
  (->> cdn/config
       (map check-status)
       dorun)

  (prn "Sleeping a bit so riemann can flush events.")
  (Thread/sleep 5000)
  (mount/stop))

#_ (check-cdn)

(defn reset []
  (mount/stop)
  (mount/start))
#_ (reset)
#_ (mount/start-without #'eway.ping.core/scheduled-jobs)
#_ (mount/stop #'eway.ping.core/scheduled-jobs)

;; ----- SIGNALS -----
(sig/with-handler :hup
  (l/info "Caught signal HUP")
  (reset))

(sig/with-handler :term
  (l/info "Caught signal TERM")
  (deliver done true))

(sig/with-handler :int
  (l/info "Caught signal INT")
  (deliver done true))
