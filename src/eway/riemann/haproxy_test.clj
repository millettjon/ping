(ns eway.lb.haproxy-test
  (:require [eway.lb.haproxy :as ha])
  (:use clojure.test))

(def http-requests
  ["Jan  5 10:00:09 lb2a haproxy[14043]: 209.151.244.108:55207 [05/Jan/2016:10:00:08.907] link.emailping.onlinedialog.com emailping/link8 0/0/1/240/241 200 327 - - ---- 351/1/1/1/0 0/0 \"GET /s/link/ep?rc=cp&rti=a10425&b=y&acq_ctrlid=8988&acq_source=32018100&email=tbeatty97@yahoo.com HTTP/1.1\""])

(def samples
  [
   ;; HTTP request
   "Jan  5 10:00:09 lb2a haproxy[14043]: 209.151.244.108:55207 [05/Jan/2016:10:00:08.907] link.emailping.onlinedialog.com emailping/link8 0/0/1/240/241 200 327 - - ---- 351/1/1/1/0 0/0 \"GET /s/link/ep?rc=cp&rti=a10425&b=y&acq_ctrlid=8988&acq_source=32018100&email=tbeatty97@yahoo.com HTTP/1.1\""

   ;; SSL error
   "Jan  5 10:00:20 lb2a haproxy[14046]: 38.81.65.42:45881 [05/Jan/2016:10:00:20.651] link.llifi.net/2: SSL handshake failure"

   ;; Server up/down
   "Jan 12 16:10:01 lb2a haproxy[28175]: Server link/link2 is DOWN, reason: Layer7 wrong status, code: 0, info: \"via agent : stopped\", check duration: 17ms. 3 active and 0 backup servers left. 0 sessions active, 0 requeued, 0 remaining in queue."
   "Jan 12 17:01:07 lb2a haproxy[12928]: Server emailping/link7 is UP, reason: Layer7 check passed, code: 0, info: \"via agent : up\", check duration: 1ms. 2 active and 0 backup servers online. 0 sessions requeued, 0 total in queue."

   ;; Proxy start/stop
   "Jan 12 16:18:16 lb2a haproxy[6371]: Proxy link.lancome.securedmi.com started."
   "Jan 12 16:57:35 lb2a haproxy[6375]: Proxy link.abcmouse.csacq2.com stopped (FE: 0 conns, BE: 0 conns)."

   ;; Backend start/stop.
   "Jan  5 18:21:22 lb2a haproxy[13203]: Stopping backend link in 0 ms."
   "Jan  5 18:21:22 lb2a haproxy[13203]: Stopping frontend link-lb2.ixs1.net in 0 ms."
   "Jan  5 18:21:22 lb2a haproxy[13201]: Stopping proxy stats in 0 ms."

   ])

(deftest parse-line
  (doseq [line samples]
    (ha/parse-line line)))

(deftest resource
  (are [path resource] (= resource (ha/resource path))
    "/s/om" "om"
    "/s/foo/bar" "foo/bar"
    "/foo.jsp" "jsp"
    "/foo.jsp?xyz" "jsp"
    "/foo.img" "static"
    ))

(deftest riemann-http-event
  (doseq [line http-requests]
    (prn (-> line
             ha/parse-line
             ha/riemann-http-event))))
