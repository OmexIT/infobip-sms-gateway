(ns com.omexit.infobip.smsService
  (:use [com.omexit.infobip.constants])
  (:require [com.omexit.infobip.infobipHelper :as infobip]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [com.omexit.infobip.configutil :refer [*props-map*]]
            [com.omexit.infobip.phoneNumberUtil :as phoneUtil]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [com.omexit.infobip.data :as data]
            [clojurewerkz.meltdown.reactor :as mr]
            [clojurewerkz.meltdown.selectors :refer [$]]))

(defn send-single-text
  ""
  [request]
  (let [{:keys [destinations text]} request
        args {:scheme :post
              :uri    "sms/1/text/single"
              :data   (json/generate-string {:from (:infobip-message-sender @*props-map*),
                                             :to   destinations,
                                             :text text})}
        ret (infobip/call-infobip args)
        {:keys [status]} ret]))

(def send-multi-text-fn
  (fn [request]
    (try
      (log/infof "doSendMultiText(args=%s)" (into [] request))
      (let [infobip-payload {:messages (into [] (map (fn [req]
                                                       (let [{:keys [sms_to sms_from message]} req
                                                             formated-req {:from sms_from
                                                                           :to   (phoneUtil/e164-format sms_to (:sms-region @*props-map*))
                                                                           :text message}]
                                                         formated-req)) request))}
            ;_ (log/infof "infobip-payload = %s" (into [] infobip-payload))
            args {:scheme :post
                  :uri    "sms/1/text/multi"
                  :data   (json/generate-string infobip-payload)}
            ret (infobip/call-infobip args)
            {:keys [bulkId messages]} ret
            rec-idx (atom 0)
            dat (into [] (doall
                           (map (fn [req-response]
                                  (let [request-message (nth request @rec-idx)
                                        _ (swap! rec-idx inc)
                                        {:keys [campaign_id sms_to sms_from message]} request-message
                                        {:keys [status smsCount messageId]} req-response
                                        {:keys [groupName name description]} status
                                        messge-dat {:campaign_id    campaign_id
                                                    :message_id     messageId
                                                    :bulk_id        bulkId
                                                    :sms_to         sms_to
                                                    :sms_from       sms_from
                                                    :message        message
                                                    :request_status name
                                                    :status_message description
                                                    :sms_count      smsCount
                                                    :date_modified  (t/now)
                                                    :group_name     groupName}] messge-dat)) messages)))]
        (data/create-campaign-message-log dat))
      (catch Exception e
        (.printStackTrace e)))))

(defn update-sent-status
  ""
  [bulk-id]
  (format "doSendMultiText(args=%s)" bulk-id)
  (let [args {:scheme       :get
              :uri          "/sms/1/logs"
              :query-params {"bulkId" bulk-id}}
        ret (infobip/call-infobip args)
        {:keys [results]} ret]
    (doall
      (map (fn [x]
             (let [{:keys [bulkId messageId sentAt doneAt price status]} x
                   {:keys [pricePerMessage currency]} price
                   {:keys [groupName name description]} status
                   update-data {:bulk_id bulkId
                                :message_id messageId
                                :sent_at (f/parse infobip-formatter sentAt)
                                :done_at (f/parse infobip-formatter doneAt)
                                :price_per_message pricePerMessage
                                :currency currency
                                :group_name groupName
                                :request_status name
                                :status_message description}]
               (data/update-message-status update-data))) results))))



(def reactor (mr/create))


(defn init-sms-status-reactor []
  (try
    (do
      ;;Init the configurations
      ;;Register reactor for empty list
      (mr/on reactor ($ "data-list-is-empty")
             (fn [event]
               (try
                 ;;;fetch data to process
                 (let [data-to-process (atom (data/fetch-pending-direct-campaigns))]
                   (log/infof "Fetched %s" (count @data-to-process))
                   (doseq [rec @data-to-process]
                     (log/infof "rec: %s" rec))
                   ;;;If no data sleep 5 seconds before trying
                   (while (empty? @data-to-process)
                     (Thread/sleep 5000)
                     (reset! data-to-process (data/fetch-pending-direct-campaigns)))
                   (mr/notify reactor "new-data-list-created" {:files (take 5000 @data-to-process)}))
                 (catch Exception e
                   (log/errorf e "Error in the reactor")))))

      ;;Register reactor for new list created
      (mr/on reactor ($ "new-data-list-created")
             (fn [event]
               (try
                 (let [kyc-data-list (atom (-> event :data :files))]
                   (log/infof "New reactor job->number of files (%s)" (count @kyc-data-list))
                   ;(log/infof "%s" @kyc-data-list)
                   ;(doseq [kyc-data @kyc-data-list]
                   ;  (data/call-unbarring-service kyc-data))

                   (let [resp (doall
                                (pmap (fn [c]
                                        (let [{:keys [bulk_id]} c]
                                          (update-sent-status bulk_id))) @kyc-data-list))]
                     (log/infof "Records sent->(%s), Records processed->(%s)" (count @kyc-data-list) (count resp)))

                   ;;Reset cdr list so that we can start new fetch
                   (reset! kyc-data-list (list))

                   ;;Rest a little bit for 10 seconds the app needs some energy to perform next task
                   (Thread/sleep 10000)

                   ;;Check if the cdr list then we trigger new fetch
                   (if (empty? @kyc-data-list)
                     (mr/notify reactor "data-list-is-empty" {:my "payload"})))
                 (catch Exception e
                   (log/errorf e "Error in the reactor")))))

      (mr/notify reactor "data-list-is-empty" {:my "payload"}))
    (catch Exception e
      (log/errorf e "Error on reactor"))))