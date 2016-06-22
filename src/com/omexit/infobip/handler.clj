(ns com.omexit.infobip.handler
  (:use com.omexit.infobip.constants)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [com.omexit.infobip.service :as service]
            [cheshire.core :as json]
            [compojure.handler :as handler]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.json :as ring-json]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clj-time.format :as f]
            [cheshire.core :refer :all]
            [com.omexit.infobip.sms :as sms]
            [cheshire.generate :refer [add-encoder encode-str remove-encoder]]
            [com.omexit.infobip.smsService :as smsService]))

(defn wrap-status [params]
  (prn params)
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/generate-string "Hey")})

(defroutes app-routes
           (context "/sms" []
             (context "/campaigns" []
               (GET "/" {:keys [params] :as request} (service/find-all-campaign params))
               (GET "/:id" {:keys [params] :as request} (service/find-one-campaign params))
               (POST "/" {:keys [body] :as request} (service/create-campaign (slurp body))) ;;;Create SMS campaign
               (GET "/:campaign-id/messages" {:keys [headers params]} (service/fetch-campaign-messages params)) ;;Get campaign messages by campaignid
               (GET "/test/:id" {:keys [headers params body]} (wrap-status params)))
             (context "/logs" []
               (GET "/messages" {:keys [headers params]} (service/fetch-sent-messages params)) ;;Get campaign messages)
               (context "/cron" []
                 (GET "/" {:keys [params] :as request} (service/handle-cron-commands params))
                 (GET "/scheduled" {:keys [params] :as request} (service/fetch-scheduled-campaign params))))
             (route/not-found "Not Found")))

           (defn wrap-custom-response
             [handler]
             (fn [request]
               (try
                 (let [response (handler request)]
                   (assoc-in response [:status] 200)
                   {:status  200
                    :headers {"Content-Type" "application/json"}
                    :body    (json/generate-string response)}
                   )
                 (catch Exception e
                   {:status 400 :body "Invalid data"}))))

           ;(def app
           ;  (-> (wrap-defaults app-routes (-> site-defaults
           ;                                    (assoc-in [:security :anti-forgery] false)
           ;                                    (dissoc :session)))
           ;      wrap-custom-response))


           (def app
             (wrap-cors
               (-> (handler/api app-routes))
               :access-control-allow-origin [#".*"]
               :access-control-allow-methods [:get :put :post :delete]))


           (defn create-thread-executor []
             (reify
               java.util.concurrent.Executor
               (execute [_ task]
                 (let [f #(try
                           (task)
                           ;; return value is ignored by Thread
                           (catch Throwable e
                             ;; not much we can do here
                             (.printStackTrace e *out*)))]
                   (doto (Thread. f)
                     (.start))))))

           (def exe (create-thread-executor))


           (defn init-sms-app
             "Init app pre-start information"
             []
             ;;Add org.joda.time.DateTime Json encoder
             (add-encoder org.joda.time.DateTime
                          (fn [c jsonGenerator]
                            (.writeString jsonGenerator (f/unparse cheshire-formatter c))))
             ;;initialize SMS Queue handler
             (sms/initialize-sms-interface)
             (.execute exe smsService/init-sms-status-reactor))