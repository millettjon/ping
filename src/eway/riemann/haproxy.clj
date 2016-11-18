(ns eway.lb.haproxy
  (:require [clojure.string :as str]
            [net.cgrand.regex :as re]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [taoensso.timbre :as timbre]))

;; Define regular expression for parsing haproxy log lines.
;;
;; References:
;; - haproxy log format: http://cbonte.github.io/haproxy-dconv/configuration-1.5.html#8.2.4
;;   log-format %ci:%cp\ [%t]\ %ft\ %b/%s\ %Tq/%Tw/%Tc/%Tr/%Tt\ %ST\ %B\ %CC\ \
;;              %CS\ %tsc\ %ac/%fc/%bc/%sc/%rc\ %sq/%bq\ %hr\ %hs\ %{+Q}r

(let [d re/digit
      d+ (re/+ d)
      w re/wordchar
      w+ (re/+ w)
      ip4-octet #{[\2 \5 {\0 \5}]
                  [\2 {\0 \4} d]
                  [(re/? {\0 \1}) d (re/? d)]}
      ip4 [ip4-octet \. ip4-octet \. ip4-octet \. ip4-octet]

      ;; All proxy names must be formed from upper and lower case letters, digits,
      ;; '-' (dash), '_' (underscore) , '.' (dot) and ':' (colon)
      name (re/+ #{w \- \. \:})

      ;; time stat
      t #{"-1" d+}

      syslog-prefix [
                     w w w " " #{" " d} d        ; date
                     " " d d \: d d \: d d       ; time
                     " " w+                      ; hostname
                     " " w+                      ; process
                     "[" d+ "]:"                 ; pid
                     ]

      http-prefix [[ip4 :as :client-ip]      ; %ci
                   ":" [d+ :as :client-port] ; %cp
                   " " \[ [d d \/ w w w \/ d d d d \: d d \: d d \: d d \. d d d :as :timestamp] \] ; %t
                   ]]

  (def http-pattern
    (re/regex
     syslog-prefix " " http-prefix

     ;; HTTP FIELDS
     ;; 4.6s -> 13.4
     " " [name :as :frontend-name] ; %ft
     [(re/? \~) :as :ssl]

     ;; 5.4 -> 16.4
     " " [name :as :backend-name] \/ #{[name :as :server-name] "<NOSRV>"}

     ;; 6.3 -> 20.0
     ;; http timing fields
     ;; See: http://cbonte.github.io/haproxy-dconv/configuration-1.5.html#8.4
     " " [t :as :client-request-time]   ; %Tq
     \/ [t :as :queue-wait-time]        ; %Tw
     \/ [t :as :server-connect-time]    ; %Tc
     \/ [t :as :server-response-time]   ; %Tr
     \/ [t :as :total-time]             ; %Tt

     ;; 7.0 -> 20.6
     " " #{[{\2 \5} d d :as :status-code] "-1"} ; %ST

     ;; 7.3 -> 21.1
     " " [d+ :as :bytes-read]           ; %B

     ;; 7.2 -> 21.9
     " " [\- :as :captured-request-cookie]  ; %CC
     " " [\- :as :captured-response-cookie] ; %CS

     ;; 7.4 -> 26.2
     " " [#{\C \S \P \L \R \I \D \U \K \c \s \-}
          #{\R \Q \C \H \D \L \T \-} \- \- :as :termination-state] ; %tsc

     ;; 7.8 -> 26.3
     " " [d+ :as :actconn]                        ; %ac active connections?
     \/ [d+ :as :frontend-concurrent-connections] ; %fc
     \/ [d+ :as :backend-concurrent-connections]  ; %bc
     \/ [d+ :as :server-concurrent-connections]   ; %sc
     \/ [d+ :as :retries]                         ; %rc

     ;; 7.6 -> 27.1
     " " [d+ :as :server-queue]         ; %sq
     \/ [d+ :as :backend-queue]         ; %bq

     ;; 8.4 -> 41
     ;; For requests that are truncated, the terminating double quote " may be missing.
     " " \" #{[[#{"GET" "HEAD" "POST"} :as :method] " " [(re/+ (re/- #{\"})) :as :url]] "<BADREQ>"} (re/? \") ; %{+Q}r

     ;;(re/repeat re/any)
     ))

  ;; Log format for SSL connection errors.
  ;; Ref: http://cbonte.github.io/haproxy-dconv/configuration-1.5.html#8.2.5
  (def ssl-handshake-failure-pattern
    (re/regex
     syslog-prefix " " http-prefix
     " " [name :as :frontend-name] "/2: SSL handshake failure"))

  (def proxy-status-pattern
    (re/regex
     syslog-prefix
     " Proxy " [name :as :proxy-name] " " [#{"started" "stopped"} :as :status] (re/+ re/any)))

  (def stopping-pattern
    (re/regex
     syslog-prefix " "
     [#{"Stopping"} :as :status] " " [#{"frontend" "backend" "proxy"} :as :proxy-type] " " [name :as :proxy-name] " " (re/+ re/any)))

  (def server-status-pattern
    (re/regex
     syslog-prefix
     " Server " [name :as :frontend-name] "/" [name :as :server-name] " is " [#{"UP" "DOWN"} :as :state] (re/+ re/any))))

(defn parse-line
  "Parses an haproxy log line into a map."
  [line]
  (let [m (fn [pattern type]
                  (some-> pattern
                          (re/exec line)
                          (assoc :type type)))]
    (or (m http-pattern :http-request)
        (m ssl-handshake-failure-pattern :ssl-error)
        (m proxy-status-pattern :lifecycle)
        (m server-status-pattern :server-status)
        (m stopping-pattern :stopping)
        (timbre/warn "Failed to match line: " line)
;;        (throw (Exception. "invalid line"))
        )))

(defn each-line
  [file f]
  (with-open [rdr (clojure.java.io/reader file)]
    (doseq [line (line-seq rdr)]
      (try
        (f line)
        (catch NullPointerException x
          (timbre/warn x line)
          ;; (throw x)
          )))))

(def haproxy-timestamp-formatter (f/formatter "dd/MMM/yyyy:HH:mm:ss.SSS"))

(defn parse-timestamp
  "Takes an haproxy timestamp string and returns the number of seconds since the unix epoch."
  [timestamp]
  (let [ms (->> timestamp
                (f/parse haproxy-timestamp-formatter)
                .getMillis)]
    (-> ms (/ 1000) (+ 0.5) long)))

(def servlet-pattern
  (re/regex "/s/" [(re/+ #{re/wordchar "/"}) :as :servlet] (re/* re/any)))
#_ (re/exec servlet-pattern "/s/om/showStats")

(defn resource
  "Takes an url and returns the type of resource."
  [url]
  (or
   (if (nil? url) "<BADREQ>")
   (if-let [m (re/exec servlet-pattern url)] (:servlet m))
   (if (str/includes? url ".jsp") "jsp")
   "static"))

(defn riemann-http-event
  "Converts an haproxy log event to a riemann event."
  [{:keys [:timestamp :type :url] :as event}]
  (when (= :http-request type)
    (try
      (-> event
          (select-keys [:ssl :termination-state :client-ip :frontend-name :backend-name :status-code :total-time :bytes-read :server-name :server-response-time :actconn])
          (assoc
           :resource (resource url)
           :time (parse-timestamp timestamp)
           :state "ok"
           :tags ["haproxy2"]
           :ttl 30.0
           ))
      (catch Exception ex
        (timbre/error ex "failed to convert event to riemann format: " event)
        (throw ex)
        )
      )))

;; PERFORMANCE
;; 733k lines
;; identity: 0.721
#_ (let [el (partial each-line "var/log/haproxy.log-20160105-1452007802")]
     (doseq [f [identity parse-line]]
       (prn f)
       (time (el f))))
