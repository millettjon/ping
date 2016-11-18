(ns eway.riemann.client
  (:require [clojure.core.async :as async]
            [riemann.client :as rm]
            [taoensso.timbre :as timbre]
            [mount.core :as mount :refer [defstate]]))

;; Note: Riemann uses service, host, and time as the composite primary key for indexing.
;; Note: Custom metrics must be strings.
;; TODO: ? How to handle backpressure?
;;    - ? wait for response?
;;    - ? setup core.async bounded queue? (sliding-buffer)
;;    - ? how to tell when the queue is overloaded?
;; TODO: send events in batches

#_ (let [c (rm/tcp-client {:host "riemann"})]
     (-> c (rm/send-event {:service "http" :state "ok" :tags ["haproxy2"] :ttl 60 :http-request "/foo/bar/baz"})
         (deref 5000 ::timeout)))


(defn batch-reader
  [batch-size batch-time-ms]
  (fn [event-channel]
    (let [batch-size-1 (dec batch-size)
          timeout-channel (async/timeout batch-time-ms)]
      (loop [batch []]
        (let [[val port] (async/alts!! [event-channel timeout-channel])]
          (cond
            ;; the timeout expired
            (= port timeout-channel) batch

            ;; the channel is closed
            (nil? val) (if (seq batch) batch)

            ;; a full batch was received
            (== batch-size-1 (count batch)) (conj batch val)

            ;; a value was received
            :default (recur (conj batch val))
            ))))))

(defn new-riemann-client
  [event-channel]
  (let [client (rm/tcp-client {:host "riemann"})
        read-batch (batch-reader 1000 2000)]
    (fn []
      ;; read-batch returns nil to signal exit
      (while (when-let [batch (read-batch event-channel)]
               (try
                 ;; (timbre/debug "read batch: " (count batch))
                 (if (seq batch)
                   (->> batch
                        ;; (random-sample 0.01)
                        (rm/send-events client)
                        deref) ;; block to apply backpressure
                   :retry)
                 (catch Exception ex
                   (timbre/error ex "GOT AN EX")
                   #_ :retry
                   nil
                   )))))))

(defstate event-channel
  :start (async/chan (async/sliding-buffer 1000))
  :stop (async/close! event-channel))

(defstate riemann-client-thread
  :start (-> (new-riemann-client event-channel) Thread. .start))
