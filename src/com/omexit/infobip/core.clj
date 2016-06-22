(ns com.omexit.infobip.core
  (:use ring.adapter.jetty)
  (:require [com.omexit.infobip.handler :as handler]
            [com.omexit.infobip.configutil :refer [*props-map*]])
  (:gen-class))




(defn -main
  "Main method."
  [& args]
  (handler/init-sms-app)
  (run-jetty #'handler/app (:service.port *props-map*)))
