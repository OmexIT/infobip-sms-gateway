(ns com.omexit.infobip.mifos
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [com.omexit.infobip.configutil :refer [*props-map*]]))

(defn fetch-mifos-active-clients
  [tenant-id]
  (let [options {:scheme       :get
                 :uri          "clients"
                 :query-params {"pretty" false
                                "fields" "displayName,mobileNo,lastname,displayName,middlename,accountNo,firstname,externalId,active"}}
        {:keys [scheme uri data query-params]} options
        mifos-api-url (str (:mifos-api-base @*props-map*) "/" uri)
        request-params (conj
                         {:headers        {"Content-Type"              "application/json"
                                           "Fineract-Platform-TenantId" tenant-id}
                          :basic-auth     [(:mifos-api-user @*props-map*) (:mifos-api-password @*props-map*)]
                          :insecure?      true
                          :socket-timeout (Integer/parseInt (:mifos-api-read-timeout @*props-map*))
                          :conn-timeout   (Integer/parseInt (:mifos-api-connect-timeout @*props-map*))}
                         (if query-params {:query-params query-params} {})
                         (if data {:body data} {}))
        {:keys [status request-time body error]}
        (condp = scheme
          :post (client/post mifos-api-url request-params)
          :get (client/get mifos-api-url request-params)
          :put (client/put mifos-api-url request-params)
          :delete (client/delete mifos-api-url request-params)
          (client/get mifos-api-url request-params))]
    (if (= status 200)
      (json/parse-string body true))))

