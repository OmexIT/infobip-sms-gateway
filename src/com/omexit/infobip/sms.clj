(ns com.omexit.infobip.sms
  (:require [com.omexit.infobip.configutil :refer [*props-map*]]
            [com.omexit.infobip.rabbitmq :as rmq]
            [clojure.tools.logging :as log]))

(def send-sms (fn [& args]
                (log/infof "%s" args)))

;; Initialize Interfaces
(defn initialize-sms-interface
  "Initializes the available SMS interfaces. Currently the only available interface is RabbitMQ"
  []
  (let [{:keys [rabbitmq-host rabbitmq-port rabbitmq-username rabbitmq-password rabbitmq-vhost rabbitmq-queue-name
                rabbitmq-queue-exchange rabbitmq-queue-routing-key rabbitmq-retry-queue-name rabbitmq-retry-exchange
                rabbitmq-retry-routing-key rabbitmq-retry-delay rabbitmq-failed-queue-name rabbitmq-failed-exchange
                rabbitmq-failed-routing-key]} @*props-map*]
    (def do-send-sms (rmq/initialize-rabbitmq {
                                               ; connection info
                                               :host                     rabbitmq-host
                                               :port                     (Integer/parseInt rabbitmq-port)
                                               :username                 rabbitmq-username
                                               :password                 rabbitmq-password
                                               :vhost                    rabbitmq-vhost
                                               ; queue info
                                               :queue-name               rabbitmq-queue-name
                                               :queue-exchange           rabbitmq-queue-exchange
                                               :queue-routing-key        rabbitmq-queue-routing-key
                                               ; retry queue info
                                               :queue-retry-name         rabbitmq-retry-queue-name
                                               :queue-retry-exchange     rabbitmq-retry-exchange
                                               :queue-retry-routing-key  rabbitmq-retry-routing-key
                                               :queue-retry-delay        rabbitmq-retry-delay
                                               ; failed queue info
                                               :queue-failed-name        rabbitmq-failed-queue-name
                                               :queue-failed-exchange    rabbitmq-failed-exchange
                                               :queue-failed-routing-key rabbitmq-failed-routing-key}))
    (def send-sms (fn [args]
                    (try
                      (log/spyf "sendSMS(%s)" args)
                      (do-send-sms args)
                      (catch Exception ex
                        (log/errorf ex "!sendSMS(%s) -> %s" args (.getMessage ex))))))))

