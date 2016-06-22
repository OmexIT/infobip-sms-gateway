(ns com.omexit.infobip.infobipHelper
  (:use [slingshot.slingshot :only [throw+ try+]]
        [com.omexit.infobip.constants])
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [com.omexit.infobip.configutil :refer [*props-map*]]
            [com.omexit.infobip.data :as data]))


(defn call-infobip
  [params]
  (log/infof "call-infobip -> %s" params)
  (let [{:keys [scheme uri data query-params]} params
        infobip-api-url (str (:infobip-endpoint @*props-map*) "/" uri)
        request-params (conj
                         {:headers        {"Content-Type" "application/json"
                                           "accept",      "application/json"}
                          :basic-auth     [(:infobip-api-user @*props-map*) (:infobip-api-password @*props-map*)]
                          :insecure?      true
                          :socket-timeout (Integer/parseInt (:infobip-api-read-timeout @*props-map*))
                          :conn-timeout   (Integer/parseInt (:infobip-api-connect-timeout @*props-map*))}
                         (if query-params {:query-params query-params} {})
                         (if data {:body data} {}))
        _ (log/infof "request-params: --- %s" request-params)
        {:keys [status request-time body error]}
        (condp = scheme
          :post (client/post infobip-api-url request-params)
          :get (client/get infobip-api-url request-params)
          :put (client/put infobip-api-url request-params)
          :delete (client/delete infobip-api-url request-params)
          (client/get infobip-api-url request-params))]
    (if-not error
      (json/parse-string body true)
      ;(throw+ {:type http-error :message "Error calling infobip" :developer-message (String/format ":method %s :endpoint %s" scheme infobip-api-url)})
      (log/errorf "%s" error))))




