;;  RabbitMQ Wrapper Lirary
;;  It uses Langohr, a Clojure client for RabbitMQ
;;
;;  @author  Nahashon Kibet
;;

(ns com.omexit.infobip.rabbitmq
  (:require [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.exchange :as le]
            [langohr.consumers :as lc]
            [langohr.basic :as lb]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [com.omexit.infobip.smsService :as smsService]))

(declare initialize-publisher initialize-consumer create-consumer-handler)

(def rabbitmq-conn (atom nil))
(def enqueue (atom nil))
(def enqueue-retry (atom nil))
(def enqueue-failed (atom nil))

;;
(defn initialize-rabbitmq
  [args]
  (try
    (log/info "RabbitMQ - Initializing..")
    (let [{:keys [host port username password vhost]
           :or   {host     "127.0.0.1"
                  port     5672
                  username "guest"
                  password "guest"
                  vhost    "/"}} args
          {:keys [queue-name queue-exchange queue-routing-key
                  queue-retry-name queue-retry-exchange queue-retry-routing-key queue-retry-delay
                  queue-failed-name queue-failed-exchange queue-failed-routing-key]} args
          rabbitmq-conn-settings {:host     host
                                  :port     port
                                  :username username
                                  :password password
                                  :vhost    vhost}]
      (log/infof "rabbitMQ(%s)" rabbitmq-conn-settings)
      (reset! rabbitmq-conn (rmq/connect rabbitmq-conn-settings))

      ; main queue publisher
      (reset! enqueue (initialize-publisher queue-name
                                            queue-exchange
                                            queue-routing-key
                                            {:durable     true
                                             :auto-delete false}))
      ; retry publisher
      (reset! enqueue-retry (initialize-publisher queue-retry-name
                                                  queue-retry-exchange
                                                  queue-retry-routing-key
                                                  {:durable     true
                                                   :auto-delete true
                                                   :arguments   {"x-dead-letter-exchange"    queue-exchange
                                                                 "x-dead-letter-routing-key" queue-routing-key
                                                                 "x-message-ttl"             queue-retry-delay}}))
      ; failed publisher
      (reset! enqueue-failed (initialize-publisher queue-failed-name
                                                   queue-failed-exchange
                                                   queue-failed-routing-key
                                                   {:durable     true
                                                    :auto-delete true}))
      (initialize-consumer queue-name queue-exchange queue-routing-key queue-failed-exchange smsService/send-multi-text-fn {:durable     true
                                                                                                                         :auto-delete false})


      (log/info "RabbitMQ - Initializing done..")
      (when args
        (let [arg (first args)]))
      @enqueue)
    (catch Exception e (log/error e (.getMessage e)))))
;;
(defn initialize-publisher
  "Returns a publish function"
  [rmq-queue-name rmq-exchange rmq-routing-key options]
  (try
    (log/infof "RabbitMQ - Initializing \"%s\" Publisher" rmq-queue-name)
    (let [fn-enqueue (fn [args]
                       (log/infof "args --- %s" args)
                       (let [msg (json/generate-string args)
                             ; open a channel
                             channel (lch/open @rabbitmq-conn)]
                         ; declare exchange
                         (le/declare channel rmq-exchange "direct" {:durable true :auto-delete false})
                         (log/infof ":payload %s" msg)
                         (let [; declare queue
                               queue-name (:queue (lq/declare channel rmq-queue-name options))]
                           ; bind queue to exchange
                           (lq/bind channel queue-name rmq-exchange {:routing-key rmq-routing-key})
                           ; publish
                           (lb/publish
                             channel
                             rmq-exchange
                             rmq-routing-key
                             msg
                             {"Content-type" "text/json"}))

                         (rmq/close channel)))]
      fn-enqueue)
    (catch Exception e (log/error e (.getMessage e)))))

;;
(defn initialize-consumer
  "Consumer function"
  [rmq-queue-name rmq-exchange rmq-routing-key rmq-failed-exchange fn-process-payload options]
  (try (log/infof "RabbitMQ - Initialize \"%s\" Consumer" rmq-queue-name)
       (let [channel (lch/open @rabbitmq-conn)
             queue-name (:queue (lq/declare channel rmq-queue-name options))]
         ; declare exchange
         (le/declare channel rmq-exchange "direct" {:durable true :auto-delete false})
         (lq/bind channel queue-name rmq-exchange {:routing-key rmq-routing-key})
         (when fn-process-payload
           (do
             (lc/subscribe channel queue-name (create-consumer-handler channel rmq-routing-key fn-process-payload) {:auto-ack true}))))
       (catch Exception exception (log/error exception (.getMessage exception)))))

;;
(defn create-consumer-handler
  "Returns the consumer function"
  [channel routing-key fn-process-payload]
  (fn [channel {:keys [delivery-tag] :as delivery-tag} ^bytes payload]
    (try
      (let [notification (json/parse-string (String. payload "UTF-8") true)]
        (if (empty? notification)
          (log/errorf "!invalidNotification -> %s" (into [] notification))
          (do
            (log/infof "payload -> %s " (into [] notification))
            (fn-process-payload notification))))
      (catch Exception e (log/error e (.getMessage e))))))

(defn requeue
  "Requeue failed messages"
  [channel delivery-tag]
  (log/infof "Requeueing %s " delivery-tag)
  (lb/nack channel delivery-tag true true))

(defn ack
  "Acknowledge message receipt"
  [channel delivery-tag]
  (log/debugf "ack(%s)" delivery-tag)
  (lb/ack channel delivery-tag))

(defn shut-down []
  (try
    (rmq/close @rabbitmq-conn)
    (catch Exception e (log/error e (.getMessage e)))))
